package org.telegram.messenger.plugins;

import android.net.Uri;
import com.chaquo.python.PyObject;
import com.google.gson.*;
import org.telegram.messenger.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class PluginManager {
    public interface Result<T> { void done(T value, String error); }
    private static final PluginManager INSTANCE = new PluginManager();
    public static PluginManager get() { return INSTANCE; }
    final ExecutorService queue = Executors.newSingleThreadExecutor(r -> new Thread(r, "BYgramPlugins"));
    private final ThreadPoolExecutor downloads = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
    private final Map<String, Running> running = new HashMap<>();
    private final Set<String> restartRequired = new HashSet<>();
    private final Map<String, PluginPackage> staged = new HashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final PluginRuntime runtime = new PluginRuntime();
    private PluginStore store;
    private volatile JsonObject snapshot = new JsonObject();
    private volatile Map<String, Set<String>> accountGrants = Collections.emptyMap();
    private boolean initialized;
    private volatile boolean recoveryRequired;
    private long generation;
    private long nextRevocationCheck;
    private volatile String failure = "";
    private final java.util.concurrent.atomic.AtomicInteger pendingLogs = new java.util.concurrent.atomic.AtomicInteger();
    private long logBytes = -1;

    private static final class Running {
        PluginScope scope; PyObject plugin; PluginContext context;
        Set<String> dependencies = Collections.emptySet();
        boolean enabling;
        Running(PluginScope scope) { this.scope = scope; }
    }
    private PluginManager() { }
    public JsonObject snapshot() { return snapshot.deepCopy(); }
    public String failure() { return failure; }
    boolean accountAllowed(String id, String identity) {
        Set<String> grants = accountGrants.get(id); return grants != null && grants.contains(identity);
    }
    String[] allowedAccounts(String id) {
        Set<String> grants = accountGrants.get(id); return grants == null ? new String[0] : grants.toArray(new String[0]);
    }
    public void listen(Runnable listener) { listeners.add(listener); }
    public void unlisten(Runnable listener) { listeners.remove(listener); }
    public void refresh() { submit(() -> { init(); publish(); }, null); }
    public void resetRegistry(Result<Void> callback) {
        submit(() -> {
            PluginRecovery.setSafe(ApplicationLoader.applicationContext, true);
            stopAll();
            PluginStore.resetRegistry(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "plugins"));
            initialized = false; failure = ""; init(); publish();
        }, callback);
    }

    private void init() throws Exception {
        if (initialized) return;
        try {
            store = new PluginStore(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "plugins"));
        } catch (Exception unavailable) {
            enterRecovery();
            throw unavailable;
        }
        if (store.recoveredFromBackup || hasMarker("activation") || hasMarker("startup")) enterRecovery();
        else if (safe()) PluginRecovery.showNotification(ApplicationLoader.applicationContext);
        store.state.remove("recovery_error");
        try {
            for (String id : store.plugins().keySet()) PluginContext.recoverMigration(new File(store.data, id));
            if (hasMarker("activation")) store.recoverActivation(PluginStore.read(new android.util.AtomicFile(new File(store.root, "activation"))));
            store.applyRollbacks();
            if (!store.plugins().isEmpty()) loadStartupRevocations();
            store.applyPending(hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
            quarantineRevoked();
            store.collectPackages();
        } catch (Exception recoveryError) {
            enterRecovery();
            store.state.addProperty("engine", false);
            store.state.addProperty("recovery_error", error(recoveryError));
            log("host", "Recovery failed: " + android.util.Log.getStackTraceString(recoveryError));
        }
        for (String id : store.plugins().keySet()) {
            JsonObject record = store.plugins().getAsJsonObject(id);
            if (record.has("error") && record.get("error").getAsString().equals("PluginRestartRequired")) {
                record.remove("error"); record.addProperty("desired", false);
            }
        }
        try { store.save(); }
        catch (IOException unavailable) { enterRecovery(); throw unavailable; }
        initialized = true;
        publish();
        if (engine() && !safe()) {
            boolean starting = false;
            for (String id : new ArrayList<>(store.plugins().keySet())) {
                JsonObject record = store.plugins().getAsJsonObject(id);
                if (record.get("desired").getAsBoolean() && record.has("approved")
                        && record.get("hash").equals(record.get("approved"))) {
                    if (!starting) {
                        marker("startup", true);
                        starting = true;
                    }
                    try { enable(id); } catch (Exception e) {
                        if (restartRequired.contains(id)) enterRecovery();
                        fail(id, e);
                        if (safe()) {
                            stopAll();
                            break;
                        }
                    }
                }
            }
            if (starting) AndroidUtilities.runOnUIThread(() -> queue.execute(() -> {
                try { marker("startup", false); } catch (IOException e) { failure = "RecoveryMarkerFailed"; }
            }), 10000);
        }
        publish();
    }

    public void onResume() {
        submit(() -> {
            init();
            if (safe()) stopAll();
            else emitAppEvent("RESUME");
            refreshRevocations();
            publish();
        }, null);
    }
    public void appEvent(String event) {
        submit(() -> { if (initialized) emitAppEvent(event); }, null);
    }
    private void emitAppEvent(String event) {
        for (Running instance : running.values()) {
            if (instance.context != null && instance.plugin != null)
                instance.context.appEvent(instance.plugin, event);
        }
    }
    private void loadStartupRevocations() throws IOException {
        nextRevocationCheck = android.os.SystemClock.elapsedRealtime() + 60000;
        PluginCatalog.Request request = new PluginCatalog.Request();
        Future<Set<String>> pending;
        try { pending = downloads.submit(() -> PluginCatalog.fetchRevocations(request)); }
        catch (RejectedExecutionException busy) { return; }
        Set<String> revoked;
        try { revoked = pending.get(2500, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) {
            request.close(); pending.cancel(true); Thread.currentThread().interrupt();
            return;
        } catch (TimeoutException | ExecutionException unavailable) {
            request.close(); pending.cancel(true);
            return;
        }
        store.rememberRevoked(revoked);
    }

    private void refreshRevocations() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now < nextRevocationCheck) return;
        nextRevocationCheck = now + 60000;
        catalogWork(() -> {
            Set<String> revoked;
            try { revoked = PluginCatalog.fetchRevocations(new PluginCatalog.Request()); }
            catch (Exception unavailable) { return; }
            queue.execute(() -> {
                try {
                    store.rememberRevoked(revoked);
                    if (quarantineRevoked()) publish();
                } catch (IOException error) {
                    enterRecovery();
                    stopAll();
                    log("host", "Revocation check: " + android.util.Log.getStackTraceString(error));
                    publish();
                }
            });
        }, (value, error) -> { });
    }

    private boolean quarantineRevoked() throws IOException {
        boolean changed = false;
        for (String id : store.plugins().keySet()) {
            JsonObject record = record(id);
            if (!store.isRevoked(record.get("hash").getAsString())) continue;
            if (running.containsKey(id)) {
                stop(id);
                record = record(id);
                changed = true;
            }
            if (record.get("desired").getAsBoolean() || record.has("approved")
                    || !record.has("error") || !"PluginRevoked".equals(record.get("error").getAsString())) {
                record.remove("approved");
                record.addProperty("desired", false);
                record.addProperty("error", "PluginRevoked");
                changed = true;
            }
        }
        if (changed) store.save();
        return changed;
    }

    private void enterRecovery() {
        recoveryRequired = true;
        try { PluginRecovery.setSafe(ApplicationLoader.applicationContext, true); }
        catch (IOException error) { FileLog.e(error); }
        PluginRecovery.showNotification(ApplicationLoader.applicationContext);
    }

    public boolean safe() { return recoveryRequired || PluginRecovery.isSafe(ApplicationLoader.applicationContext); }
    private boolean engine() { return store.state.get("engine").getAsBoolean(); }

    public void setEngine(boolean enabled, Result<Void> callback) {
        submit(() -> {
            init();
            if (enabled && store.state.has("recovery_error")) throw new IOException("PluginRestartRequired");
            if (enabled) store.state.remove("recovered");
            store.state.addProperty("engine", enabled); store.save();
            if (!enabled) stopAll();
            else if (!safe()) for (String id : new ArrayList<>(store.plugins().keySet())) {
                JsonObject record = store.plugins().getAsJsonObject(id);
                if (record.get("desired").getAsBoolean()) try { enable(id); } catch (Exception e) { fail(id, e); }
            }
            publish();
        }, callback);
    }
    public void safeMode(boolean enabled, Result<Void> callback) {
        submit(() -> {
            init();
            if (enabled) {
                PluginRecovery.setSafe(ApplicationLoader.applicationContext, true);
                stopAll();
            }
            // Leaving recovery never enables code implicitly.
            else {
                store.state.addProperty("engine", false);
                for (String id : store.plugins().keySet()) store.plugins().getAsJsonObject(id).addProperty("desired", false);
                store.save(); marker("startup", false); marker("activation", false);
                PluginRecovery.setSafe(ApplicationLoader.applicationContext, false);
                recoveryRequired = false;
                PluginRecovery.clearNotification(ApplicationLoader.applicationContext);
            }
            publish();
        }, callback);
    }
    private PluginFiles.Request readFile(Uri uri, int limit, PluginFiles.Result callback) {
        android.os.CancellationSignal signal = new android.os.CancellationSignal();
        return PluginFiles.read(() -> {
            android.content.res.AssetFileDescriptor file = ApplicationLoader.applicationContext.getContentResolver().openAssetFileDescriptor(uri, "r", signal);
            if (file == null) throw new IOException("FileUnavailable");
            try { return file.createInputStream(); } catch (Exception error) { file.close(); throw error; }
        }, signal::cancel, limit, callback);
    }
    public AutoCloseable stage(Uri uri, Result<String> callback) {
        return readFile(uri, PluginPackage.MAX_ZIP, (request, bytes, readError) -> {
            if (readError != null) { AndroidUtilities.runOnUIThread(() -> callback.done(null, readError)); return; }
            boolean archive = bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' &&
                    (bytes[2] == 3 && bytes[3] == 4 || bytes[2] == 5 && bytes[3] == 6 || bytes[2] == 7 && bytes[3] == 8);
            if (archive) stageFileBytes(bytes, request, true, callback);
            else convertFile(bytes, false, request, callback);
        });
    }
    private void convertFile(byte[] bytes, boolean archive, PluginFiles.Request request, Result<String> callback) {
        catalogWork(() -> {
            try {
                request.check();
                byte[] converted = runtime.convertExtera(bytes, archive, null, null);
                request.check();
                stageFileBytes(converted, request, false, callback);
            } catch (Exception failure) {
                String reason = error(failure);
                AndroidUtilities.runOnUIThread(() -> callback.done(null, reason));
            }
        }, callback);
    }
    private void stageFileBytes(byte[] bytes, PluginFiles.Request request, boolean tryExtera, Result<String> callback) {
        queue.execute(() -> {
            String token = UUID.randomUUID().toString();
            File dir = null;
            try {
                request.check();
                init(); dir = new File(store.staging, token);
                PluginPackage pkg;
                try (InputStream input = new ByteArrayInputStream(bytes)) {
                    pkg = PluginPackage.stage(input, dir, 1, hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
                }
                validateIcon(pkg);
                request.check();
                staged.put(token, pkg);
                AndroidUtilities.runOnUIThread(() -> callback.done(token, null));
            } catch (Exception e) {
                if (dir != null) try { PluginPackage.delete(dir); } catch (IOException ignored) { }
                if (tryExtera && "ManifestMissing".equals(e.getMessage())) {
                    convertFile(bytes, true, request, callback);
                    return;
                }
                String error = error(e); AndroidUtilities.runOnUIThread(() -> callback.done(null, error));
            }
        });
    }
    private static void validateIcon(PluginPackage pkg) throws IOException {
        if (pkg.manifest.metadata().has("icon")) {
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(new File(pkg.directory, pkg.manifest.metadata().get("icon").getAsString()).getAbsolutePath(), bounds);
            PluginPackage.check(bounds.outWidth > 0 && bounds.outWidth <= 256 && bounds.outHeight > 0 && bounds.outHeight <= 256, "InvalidIcon");
        }
    }
    public PluginCatalog.Request catalog(String repository, int account, Result<JsonArray> callback) {
        PluginCatalog.Request request = new PluginCatalog.Request();
        catalogWork(() -> {
            try {
                String channel = PluginCatalog.channelName(repository);
                if (channel != null) {
                    JsonArray entries = PluginChannelCatalog.list(channel, account, request, runtime, hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
                    AndroidUtilities.runOnUIThread(() -> callback.done(entries, null));
                    return;
                }
                byte[] bytes = repository == null ? PluginCatalog.fetch("index.json", 1024 * 1024, request)
                        : PluginCatalog.fetchRepository(repository, null, 1024 * 1024, request);
                JsonArray entries = repository == null ? PluginCatalog.parse(bytes, hostBuild(), BuildConfig.BYGRAM_BASE_BUILD)
                        : PluginCatalog.parseRepository(bytes, repository, hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
                if (repository == null) try {
                    Set<String> ids = new HashSet<>();
                    for (JsonElement value : entries) ids.add(value.getAsJsonObject().getAsJsonObject("manifest").get("id").getAsString());
                    for (JsonElement value : PluginCatalog.fetchPaidEntries(request, hostBuild(), BuildConfig.BYGRAM_BASE_BUILD))
                        if (ids.add(value.getAsJsonObject().getAsJsonObject("manifest").get("id").getAsString())) entries.add(value);
                } catch (Exception unavailable) { request.check(); }
                AndroidUtilities.runOnUIThread(() -> callback.done(entries, null));
            } catch (Exception e) { String error = error(e); AndroidUtilities.runOnUIThread(() -> callback.done(null, error)); }
        }, callback);
        return request;
    }
    private <T> void catalogWork(Runnable task, Result<T> callback) {
        try { downloads.execute(task); }
        catch (RejectedExecutionException busy) { AndroidUtilities.runOnUIThread(() -> callback.done(null, "CatalogBusy")); }
    }
    public JsonArray purchasedEntries(String library) throws Exception {
        return PluginCatalog.parsePaidEntries(JsonParser.parseString(library).getAsJsonObject(), hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
    }
    public PluginCatalog.Request stageCatalog(String repository, int account, JsonObject entry, Result<String> callback) {
        return stageCatalog(repository, account, entry, null, callback);
    }
    public PluginCatalog.Request stageCatalog(String repository, int account, JsonObject entry, String paidArchive, Result<String> callback) {
        PluginCatalog.Request request = new PluginCatalog.Request();
        catalogWork(() -> {
            final byte[] bytes;
            boolean extera = repository != null && entry.has("extera_id");
            String channel = PluginCatalog.channelName(repository);
            try {
                if (entry.has("purchase")) {
                    PluginPackage.check(repository == null && paidArchive != null && paidArchive.length() <= 14_000_000, "InvalidCatalog");
                    bytes = android.util.Base64.decode(paidArchive, android.util.Base64.NO_WRAP);
                    PluginPackage.check(bytes.length <= PluginPackage.MAX_ZIP, "CatalogLimitExceeded");
                } else if (channel != null) {
                    byte[] original = PluginChannelCatalog.download(channel, account, entry, request);
                    String format = entry.get("format").getAsString();
                    request.check();
                    bytes = format.equals("bygram") ? original
                            : runtime.convertExtera(original, format.equals("archive"), null, null);
                    request.check();
                } else if (extera) {
                    byte[] original = PluginCatalog.fetchExtera(entry, request);
                    PluginCatalog.verifyExtera(entry, original);
                    request.check();
                    bytes = runtime.convertExtera(original, entry.get("archive").getAsBoolean(),
                            entry.get("extera_id").getAsString(), entry.getAsJsonObject("manifest").get("version").getAsString());
                    request.check();
                } else {
                    String file = entry.get("file").getAsString();
                    bytes = repository == null ? PluginCatalog.fetch(file, PluginPackage.MAX_ZIP, request)
                            : PluginCatalog.fetchRepository(repository, file, PluginPackage.MAX_ZIP, request);
                }
            }
            catch (Exception e) {
                String error = error(e); AndroidUtilities.runOnUIThread(() -> callback.done(null, error)); return;
            }
            queue.execute(() -> {
                String token = UUID.randomUUID().toString(); File dir = null;
                try {
                    request.check();
                    init(); dir = new File(store.staging, token);
                    PluginPackage pkg = extera || channel != null
                            ? PluginPackage.stage(new ByteArrayInputStream(bytes), dir, 1, hostBuild(), BuildConfig.BYGRAM_BASE_BUILD)
                            : PluginCatalog.stage(entry, bytes, dir, hostBuild(), BuildConfig.BYGRAM_BASE_BUILD, repository == null);
                    validateIcon(pkg);
                    staged.put(token, pkg);
                    AndroidUtilities.runOnUIThread(() -> callback.done(token, null));
                } catch (Exception e) {
                    if (dir != null) try { PluginPackage.delete(dir); } catch (IOException ignored) { }
                    String error = error(e); AndroidUtilities.runOnUIThread(() -> callback.done(null, error));
                }
            });
        }, callback);
        return request;
    }
    public void preview(String token, Result<JsonObject> callback) {
        queue.execute(() -> {
            PluginPackage pkg = staged.get(token);
            JsonObject result = pkg == null ? null : pkg.manifest.metadata();
            if (result != null) result.addProperty("sha256", pkg.sha256);
            if (result != null) result.addProperty("source", pkg.source);
            if (result != null && pkg.signer != null) result.addProperty("author_key", pkg.signer);
            if (result != null && pkg.catalogRelease != null) result.add("catalog_release", pkg.catalogRelease.deepCopy());
            AndroidUtilities.runOnUIThread(() -> callback.done(result, result == null ? "StageExpired" : null));
        });
    }
    public void commit(String token, boolean accept, Result<Void> callback) {
        submit(() -> {
            init(); PluginPackage pkg = staged.remove(token);
            if (pkg == null) throw new IOException("StageExpired");
            try { if (accept) store.install(pkg); }
            finally { PluginPackage.delete(pkg.directory.getParentFile()); }
            publish();
        }, callback);
    }
    Future<JsonObject> deployDeveloper(byte[] bytes) {
        return queue.submit(() -> deployDeveloperInternal(bytes));
    }
    Future<JsonObject> deployDeveloperSource(String id, byte[] bytes, boolean archive) {
        return queue.submit(() -> {
            requireDeveloperMode();
            PluginPackage.check(id != null && id.matches("[A-Za-z0-9_-]{2,32}"), "InvalidPluginId");
            return deployDeveloperInternal(runtime.convertExtera(bytes, archive, id, null, true));
        });
    }
    Future<JsonObject> elyxDeveloper(String command, JsonObject payload) {
        return queue.submit(() -> {
            requireDeveloperMode();
            init();
            String id = resolveDeveloperId(payload.get("plugin_id").getAsString());
            JsonObject record = developerRecord(id);
            File root = store.packageDir(record);
            PluginPackage.check(new File(root, "_bygram_elyx.json").isFile(), "InvalidElyxProject");
            if ("elyx_compare_folder".equals(command)) {
                JsonObject result = runtime.compareElyx(root, payload);
                result.add("base_hash", record.get("hash"));
                return result;
            }
            if (payload.has("base_hash"))
                PluginPackage.check(payload.get("base_hash").equals(record.get("hash")), "DeveloperBaseChanged");
            byte[] archive = runtime.patchElyx(root, payload);
            JsonObject result = deployDeveloperInternal(runtime.convertExtera(archive, true, null, null, true), id);
            result.addProperty("success", true);
            result.add("errors", new JsonArray());
            return result;
        });
    }
    Future<JsonObject> patchDeveloper(String id, String baseHash, JsonObject changes, JsonArray removed) {
        return queue.submit(() -> {
            requireDeveloperMode();
            init();
            JsonObject installed = developerRecord(id);
            PluginPackage.check(baseHash.equals(installed.get("hash").getAsString()), "DeveloperBaseChanged");
            File root = store.packageDir(installed);
            Set<String> names = new TreeSet<>(PluginPackageAccess.files(root));
            for (JsonElement name : removed) names.remove(name.getAsString());
            names.addAll(changes.keySet());
            PluginPackage.check(names.size() <= 512, "PackageLimitExceeded");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int total = 0;
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
                for (String name : names) {
                    byte[] content = changes.has(name)
                            ? android.util.Base64.decode(changes.get(name).getAsString(), android.util.Base64.DEFAULT)
                            : PluginPackage.read(new File(root, name), PluginPackage.MAX_FILE);
                    total += content.length;
                    PluginPackage.check(content.length <= PluginPackage.MAX_FILE && total <= PluginPackage.MAX_TOTAL, "PackageLimitExceeded");
                    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(name);
                    entry.setTime(0);
                    zip.putNextEntry(entry);
                    zip.write(content);
                    zip.closeEntry();
                }
            }
            PluginPackage.check(bytes.size() <= PluginPackage.MAX_ZIP, "PackageLimitExceeded");
            return deployDeveloperInternal(bytes.toByteArray(), id);
        });
    }
    private JsonObject deployDeveloperInternal(byte[] bytes) throws Exception {
        return deployDeveloperInternal(bytes, null);
    }
    private JsonObject deployDeveloperInternal(byte[] bytes, String expectedId) throws Exception {
            requireDeveloperMode();
            init();
            if (!engine() || safe()) throw new IOException("EngineDisabled");
            File directory = new File(store.staging, UUID.randomUUID().toString());
            try {
                int host = hostBuild();
                PluginPackage pkg = PluginPackage.stage(new ByteArrayInputStream(bytes), directory, 1, host, BuildConfig.BYGRAM_BASE_BUILD);
                validateIcon(pkg);
                String id = pkg.manifest.id();
                PluginPackage.check(expectedId == null || expectedId.equals(id), "DeveloperPackageConflict");
                JsonObject existing = store.plugins().getAsJsonObject(id);
                PluginPackage.check(existing == null || PluginStore.isDeveloper(existing) && !existing.has("pending"), "DeveloperPackageConflict");
                store.checkNotRevoked(pkg.sha256);
                if (existing != null) stopForMutation(id);
                if (existing == null || !existing.get("hash").getAsString().equals(pkg.sha256)) {
                    store.install(pkg, true);
                    store.applyPending(host, BuildConfig.BYGRAM_BASE_BUILD, id);
                }
                JsonObject installed = record(id);
                PluginPackage.check(pkg.sha256.equals(installed.get("hash").getAsString()), "DeveloperUpdateFailed");
                activateDeveloper(id, installed);
                JsonObject result = new JsonObject();
                result.addProperty("plugin_id", id);
                result.addProperty("sha256", pkg.sha256);
                result.addProperty("path", store.packageDir(installed).getAbsolutePath());
                return result;
            } finally {
                PluginPackage.delete(directory);
                publish();
            }
    }
    Future<JsonObject> developerCommand(String command, String id) {
        return queue.submit(() -> {
            requireDeveloperMode();
            init();
            JsonObject result = new JsonObject();
            if (command.equals("get_plugins") || command.equals("get_elyx_plugins")) {
                JsonArray entries = new JsonArray();
                for (Map.Entry<String, JsonElement> entry : store.plugins().entrySet()) {
                    JsonObject value = entry.getValue().getAsJsonObject();
                    if (!PluginStore.isDeveloper(value)) continue;
                    if (command.equals("get_elyx_plugins") && !new File(store.packageDir(value), "_bygram_elyx.json").isFile()) continue;
                    JsonObject status = new JsonObject();
                    status.addProperty("plugin_id", entry.getKey());
                    status.add("manifest", value.get("manifest").deepCopy());
                    status.addProperty("active", running.containsKey(entry.getKey()));
                    entries.add(status);
                }
                result.add("plugins", entries);
                return result;
            }
            String resolved = resolveDeveloperId(id);
            JsonObject installed = developerRecord(resolved);
            try {
                switch (command) {
                    case "enable_plugin": activateDeveloper(resolved, installed); break;
                    case "disable_plugin": stopForMutation(resolved); break;
                    case "reload_plugin": stopForMutation(resolved); activateDeveloper(resolved, installed); break;
                    case "remove_plugin": stopForMutation(resolved); store.remove(resolved, false); break;
                    default: throw new IOException("UnknownDeveloperCommand");
                }
                result.addProperty("plugin_id", resolved);
                return result;
            } finally { publish(); }
        });
    }
    private String resolveDeveloperId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{2,32}")) return id;
        return runtime.exteraId(id);
    }
    private JsonObject developerRecord(String id) throws IOException {
        JsonObject record = record(id);
        PluginPackage.check(PluginStore.isDeveloper(record), "DeveloperPackageConflict");
        return record;
    }
    private void activateDeveloper(String id, JsonObject installed) throws Exception {
        if (!engine() || safe()) throw new IOException("EngineDisabled");
        checkRestart(id);
        installed.add("approved", installed.get("hash"));
        installed.addProperty("desired", false);
        installed.remove("error");
        store.save();
        try { enable(id); }
        catch (Exception failure) { fail(id, failure); throw failure; }
    }
    private static void requireDeveloperMode() throws IOException {
        PluginPackage.check(BuildConfig.DEBUG_VERSION || PluginDevServer.running(), "DeveloperModeUnavailable");
    }
    void ideSession(String platform, int port, boolean start) throws Exception {
        requireDeveloperMode();
        String adapter;
        switch (platform) {
            case "vscode": adapter = "debugpy"; break;
            case "pycharm": adapter = "pycharm"; break;
            default: throw new IOException("InvalidIdeDebugRequest");
        }
        runtime.ideSession(adapter, port, start);
    }
    void debugDeveloper(String id, String method, PluginDevServer.Terminal terminal, String adapter, int port) throws Exception {
        requireDeveloperMode();
        Running target = queue.submit(() -> {
            init();
            String resolved = resolveDeveloperId(id);
            developerRecord(resolved);
            Running instance = running.get(resolved);
            if (instance == null || !instance.context.interceptorsReady) throw new IOException("PluginDisabled");
            return instance;
        }).get(15, TimeUnit.SECONDS);
        if (!target.scope.beginCallback()) throw new IOException("PluginDisabled");
        try { runtime.debug(target.plugin, method, terminal, adapter, port); }
        finally { target.scope.endCallback(); }
    }
    public void setEnabled(String id, boolean enabled, Result<Void> callback) {
        submit(() -> {
            init(); JsonObject record = record(id);
            if (enabled) {
                checkRestart(id);
                store.checkNotRevoked(record.get("hash").getAsString());
                if (!engine() || safe()) throw new IOException("EngineDisabled");
                record.addProperty("approved", record.get("hash").getAsString());
                record.addProperty("desired", true); record.remove("error"); store.save();
                try { enable(id); } catch (Exception e) { fail(id, e); throw e; }
            } else { record.addProperty("desired", false); store.save(); stop(id); }
            publish();
        }, callback);
    }
    private void enable(String id) throws Exception {
        enable(id, new HashSet<>());
    }
    private void enable(String id, Set<String> stack) throws Exception {
        if (running.containsKey(id)) return;
        if (!stack.add(id)) throw new IOException("PluginDependencyCycle: " + id);
        if (!engine() || safe()) throw new IOException("EngineDisabled");
        JsonObject record = record(id);
        checkRestart(id);
        store.checkNotRevoked(record.get("hash").getAsString());
        if (!record.has("approved") || !record.get("approved").equals(record.get("hash"))) throw new IOException("ApprovalRequired");
        File directory = store.packageDir(record);
        String signer = PluginSignature.verify(directory);
        PluginPackage.check(Objects.equals(record.has("author_key") ? record.get("author_key").getAsString() : null, signer), "InvalidAuthorSignature");
        PluginManifest manifest = PluginManifest.parse(PluginPackage.read(new File(directory, "manifest.json")),
                1, hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
        JsonObject dependencies = runtime.dependencies(directory);
        for (String dependency : dependencies.keySet()) {
            JsonObject requirement = dependencies.getAsJsonObject(dependency);
            JsonObject required = store.plugins().getAsJsonObject(dependency);
            if (required == null || !running.containsKey(dependency) && !required.get("desired").getAsBoolean())
                throw dependencyError("PluginDependencyUnavailable", dependency, requirement);
            String version = required.getAsJsonObject("manifest").get("version").getAsString();
            if (PluginStore.compareVersions(version, requirement.get("version").getAsString()) < 0)
                throw dependencyError("PluginDependencyOutdated", dependency, requirement);
            enable(dependency, stack);
            if (!running.get(dependency).scope.isActive()) throw new IOException("PluginDependencyUnavailable: " + dependency);
        }
        stack.remove(id);
        if (manifest.metadata().has("requires_hook_backend") && manifest.metadata().get("requires_hook_backend").getAsBoolean()
                && !HookBridge.available()) throw new IOException("HookBackendUnavailable");
        activation(id, "import");
        Running instance = new Running(new PluginScope(id, ++generation));
        instance.dependencies = dependencies.keySet();
        running.put(id, instance);
        try {
            if (manifest.metadata().has("requires_hook_backend") && manifest.metadata().get("requires_hook_backend").getAsBoolean()) {
                if (manifest.metadata().has("hook_targets")) HookBridge.verifyTargets(manifest);
                activation(id, "backend"); PluginBackendProbe.ensure(ApplicationLoader.applicationContext);
                PluginTasks.call(instance.scope, () -> { HookBridge.verify(); return null; });
            }
            PluginContext context = new PluginContext(instance.scope, manifest, directory, new File(store.data, id), this);
            instance.context = context;
            instance.plugin = PluginTasks.call(instance.scope, () -> runtime.load(directory, manifest, context));
            activation(id, "migration");
            PluginTasks.call(instance.scope, () -> { context.migrate(instance.plugin); return null; });
            activation(id, "on_load");
            PluginTasks.call(instance.scope, () -> instance.plugin.callAttr("on_load"));
            context.configureSettings(PluginTasks.call(instance.scope, () -> instance.plugin.callAttr("_settings_json").toString()));
            activation(id, "on_enable"); instance.enabling = true;
            PluginTasks.call(instance.scope, () -> instance.plugin.callAttr("on_enable"));
            instance.scope.check();
            record.addProperty("activated", true); store.save();
            marker("activation", false);
            context.interceptorsReady = true;
            context.appEvent(instance.plugin, "START");
        } catch (Exception e) {
            if ("PluginRestartRequired".equals(e.getMessage())) restartRequired.add(id);
            stop(id); marker("activation", false); throw e;
        }
    }
    private static IOException dependencyError(String code, String id, JsonObject requirement) {
        String url = requirement.get("url").getAsString();
        try {
            java.net.URI uri = new java.net.URI(url);
            if (url.length() <= 1024 && "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null)
                return new IOException(code + ": " + id + "\n" + uri.toASCIIString());
        } catch (java.net.URISyntaxException ignored) { }
        return new IOException(code + ": " + id);
    }
    private void stop(String id) {
        for (Map.Entry<String, Running> entry : new ArrayList<>(running.entrySet())) {
            if (entry.getValue().dependencies.contains(id)) stop(entry.getKey());
        }
        Running instance = detach(id);
        if (instance != null) cleanup(id, instance);
    }
    private void stopAll() {
        for (Running instance : running.values()) instance.scope.invalidate();
        Map<String, Running> detached = new LinkedHashMap<>();
        for (String id : new ArrayList<>(running.keySet())) {
            Running instance = detach(id);
            if (instance != null) detached.put(id, instance);
        }
        for (Map.Entry<String, Running> entry : detached.entrySet()) cleanup(entry.getKey(), entry.getValue());
    }
    private Running detach(String id) {
        Running instance = running.remove(id);
        if (instance == null) return null;
        instance.scope.invalidate();
        try {
            if (!instance.scope.awaitIdle(2000)) {
                restartRequired.add(id);
                instance.scope.close();
                record(id).addProperty("error", "PluginRestartRequired"); store.save();
                return null;
            }
        } catch (Exception e) {
            restartRequired.add(id);
            instance.scope.close(); failure = "PluginRestartRequired";
            try { record(id).addProperty("error", "PluginRestartRequired"); store.save(); } catch (Exception ignored) { }
            return null;
        }
        instance.scope.close();
        return instance;
    }
    private void cleanup(String id, Running instance) {
        try { if (instance.plugin != null && instance.enabling) PluginTasks.call(instance.scope, () -> instance.plugin.callAttr("on_disable")); }
        catch (Exception e) {
            if ("PluginRestartRequired".equals(e.getMessage())) restartRequired.add(id);
            log(id, "on_disable: " + android.util.Log.getStackTraceString(e));
        }
        finally {
            try {
                if (!instance.scope.awaitIdle(0)) restartRequired.add(id);
                else PluginTasks.call(instance.scope, () -> { runtime.unload(instance.plugin, id); return null; });
            }
            catch (Exception e) {
                if ("PluginRestartRequired".equals(e.getMessage())) restartRequired.add(id);
                log(id, "on_unload: " + android.util.Log.getStackTraceString(e));
            }
            if (restartRequired.contains(id)) try { record(id).addProperty("error", "PluginRestartRequired"); store.save(); } catch (Exception ignored) { }
        }
    }
    public void remove(String id, boolean data, Result<Void> callback) {
        submit(() -> { init(); stopForMutation(id); store.remove(id, data); publish(); }, callback);
    }
    public void rollback(String id, Result<Void> callback) {
        submit(() -> {
            init();
            JsonObject previous = record(id).getAsJsonObject("rollback");
            if (previous != null) store.checkNotRevoked(previous.get("hash").getAsString());
            stopForMutation(id); store.rollback(id); publish();
        }, callback);
    }
    public void cancelUpdate(String id, Result<Void> callback) {
        submit(() -> { init(); store.cancelPending(id); publish(); }, callback);
    }
    private void stopForMutation(String id) throws Exception {
        record(id).addProperty("desired", false); store.save();
        stop(id); checkRestart(id);
    }
    public void accounts(String id, String[] identities, Result<Void> callback) {
        submit(() -> {
            init(); JsonObject record = record(id);
            JsonArray allowed = new JsonArray();
            for (String identity : identities) {
                String[] parts = identity.split(":");
                int slot = Integer.parseInt(parts[0]);
                if (slot < 0 || slot >= UserConfig.MAX_ACCOUNT_COUNT || !UserConfig.getInstance(slot).isClientActivated()
                        || !identity.equals(PluginContext.currentIdentity(slot))) throw new IOException("AccountUnavailable");
                allowed.add(identity);
            }
            stopForMutation(id); record = record(id); record.add("accounts", allowed); store.save(); publish();
        }, callback);
    }
    public void settings(String id, Result<Void> callback) {
        submit(() -> {
            init(); Running instance = running.get(id);
            if (instance == null) throw new IOException("PluginDisabled");
            try { instance.context.openSettings(PluginTasks.call(instance.scope, () -> instance.plugin.callAttr("_settings_json").toString())); }
            catch (Exception e) { fail(id, e); throw e; }
        }, callback);
    }
    public void settingsLink(String id, String path, Result<Void> callback) {
        submit(() -> {
            init();
            Running instance = running.get(id);
            if (instance == null || !instance.scope.isActive()) throw new IOException("PluginDisabled");
            String page = PluginTasks.call(instance.scope, () -> instance.plugin.callAttr("_settings_link_json", path).toString());
            instance.context.openSettings(page);
        }, callback);
    }
    public void reloadSettings(PluginContext owner, String path, Result<JsonObject> callback) {
        queue.execute(() -> {
            try {
                Running instance = running.get(owner.id());
                if (instance == null || instance.context != owner || !owner.active()) return;
                JsonObject page = PluginTasks.call(instance.scope, () -> owner.prepareSettings(
                        instance.plugin.callAttr("_reload_settings_json", path).toString()));
                AndroidUtilities.runOnUIThread(() -> callback.done(page, null));
            } catch (Exception failure) {
                owner.failed(failure);
            }
        });
    }
    public void pin(String id, Result<Void> callback) {
        submit(() -> {
            init();
            JsonObject record = record(id);
            record.addProperty("pinned", !record.has("pinned") || !record.get("pinned").getAsBoolean());
            store.save();
            publish();
        }, callback);
    }

    public void exportPackage(String id, Result<Uri> callback) {
        readPackage(id, null, directory -> {
            File cache = new File(ApplicationLoader.applicationContext.getCacheDir(), "bygram-plugin-share/" + directory.getName());
            PluginPackage.check(cache.isDirectory() || cache.mkdirs(), "StorageUnavailable");
            File destination = new File(cache, id + ".byplugin");
            File original = new File(directory.getParentFile().getCanonicalFile(), directory.getName() + ".byplugin");
            PluginPackageAccess.export(directory, original, destination);
            return androidx.core.content.FileProvider.getUriForFile(ApplicationLoader.applicationContext,
                    ApplicationLoader.getApplicationId() + ".provider", destination);
        }, callback);
    }

    public void sourceFiles(String id, String token, Result<List<String>> callback) {
        readPackage(id, token, directory -> {
            List<String> files = PluginPackageAccess.files(directory);
            files.removeIf(name -> !PluginPackageAccess.isPreviewable(name));
            return files;
        }, callback);
    }

    public void importedStickerIcon(String id, String token, Result<String> callback) {
        readPackage(id, token, directory -> {
            File file = new File(directory, "_bygram_elyx.json");
            if (!file.isFile()) return null;
            JsonElement value = JsonParser.parseString(new String(PluginPackage.read(file, 1024 * 1024),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("metainfo").get("icon");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
            String icon = value.getAsString();
            return PluginManifest.isStickerIcon(icon) ? icon : null;
        }, callback);
    }

    public void sourceFile(String id, String token, String path, String truncated, Result<String> callback) {
        readPackage(id, token, directory -> PluginPackageAccess.source(directory, path, truncated), callback);
    }

    private interface PackageReader<T> { T read(File directory) throws IOException; }

    private <T> void readPackage(String id, String token, PackageReader<T> reader, Result<T> callback) {
        queue.execute(() -> {
            try {
                init();
                PluginPackage pending = token == null ? null : staged.get(token);
                if (token != null && pending == null) throw new IOException("StageExpired");
                T result = reader.read(pending == null ? store.packageDir(record(id)) : pending.directory);
                AndroidUtilities.runOnUIThread(() -> callback.done(result, null));
            } catch (Exception failure) {
                String reason = error(failure);
                AndroidUtilities.runOnUIThread(() -> callback.done(null, reason));
            }
        });
    }
    public AutoCloseable exportSettings(String id, Uri uri, Result<Void> callback) {
        android.os.CancellationSignal signal = new android.os.CancellationSignal();
        return PluginFiles.write(() -> {
            android.content.res.AssetFileDescriptor file = ApplicationLoader.applicationContext.getContentResolver()
                    .openAssetFileDescriptor(uri, "wt", signal);
            if (file == null) throw new IOException("FileUnavailable");
            try { return file.createOutputStream(); } catch (Exception error) { file.close(); throw error; }
        }, () -> {
            Future<byte[]> content = queue.submit(() -> {
                init(); JsonObject record = record(id), exported = new JsonObject();
                JsonObject scopes = store.settingsSnapshot(id);
                if (!scopes.has("schema.json")) {
                    JsonObject schema = new JsonObject(); schema.add("version", record.getAsJsonObject("manifest").get("settings_schema"));
                    scopes.add("schema.json", schema);
                }
                exported.addProperty("format", 1); exported.addProperty("plugin_id", id); exported.add("scopes", scopes);
                byte[] bytes = exported.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                PluginSettingsFile.parse(bytes, id);
                return bytes;
            });
            try { return content.get(); }
            catch (InterruptedException cancelled) { content.cancel(false); throw cancelled; }
        }, signal::cancel, (request, bytes, error) -> AndroidUtilities.runOnUIThread(() -> callback.done(null, error)), 30000);
    }
    public AutoCloseable importSettings(String id, Uri uri, Result<Void> callback) {
        return readFile(uri, PluginSettingsFile.MAX_BYTES, (request, bytes, readError) -> {
            if (readError != null) { AndroidUtilities.runOnUIThread(() -> callback.done(null, readError)); return; }
            submit(() -> {
                request.check();
                init(); record(id);
                JsonObject scopes = PluginSettingsFile.parse(bytes, id);
                request.check();
                stopForMutation(id);
                request.check();
                store.restoreSettings(id, scopes); publish();
            }, callback);
        });
    }
    public void resetData(String id, Result<Void> callback) {
        submit(() -> {
            init(); stopForMutation(id); JsonObject record = record(id);
            if (record.has("rollback")) record.getAsJsonObject("rollback").remove("settings_snapshot");
            store.save(); PluginPackage.delete(new File(store.data, id)); store.collectPackages(); publish();
        }, callback);
    }
    public void journal(String id, boolean clear, Result<String> callback) {
        queue.execute(() -> {
            try {
                init(); record(id);
                File log = new File(new File(store.root, "logs"), id + ".log");
                File previous = new File(log.getParentFile(), id + ".previous.log");
                if (clear) { PluginPackage.delete(log); PluginPackage.delete(previous); logBytes = -1; }
                String text = previous.exists() ? new String(PluginPackage.read(previous), java.nio.charset.StandardCharsets.UTF_8) : "";
                if (log.exists()) text += new String(PluginPackage.read(log), java.nio.charset.StandardCharsets.UTF_8);
                final String journal = text;
                AndroidUtilities.runOnUIThread(() -> callback.done(journal, null));
            } catch (Exception e) { String error = error(e); AndroidUtilities.runOnUIThread(() -> callback.done(null, error)); }
        });
    }
    private JsonObject record(String id) throws IOException {
        JsonObject record = store.plugins().getAsJsonObject(id);
        if (record == null) throw new IOException("PluginMissing");
        return record;
    }
    private void checkRestart(String id) throws IOException {
        JsonObject record = record(id);
        if (restartRequired.contains(id)) throw new IOException("PluginRestartRequired");
        if (record.has("rollback_pending") && !(safe() && store.state.has("recovery_error"))) throw new IOException("PluginRestartRequired");
        if (record.has("error") && record.get("error").getAsString().equals("PluginRestartRequired")) throw new IOException("PluginRestartRequired");
    }
    private void fail(String id, Exception e) throws Exception {
        if ("PluginRestartRequired".equals(e.getMessage())) restartRequired.add(id);
        stop(id); JsonObject record = record(id);
        if (restartRequired.contains(id) || record.has("error") && record.get("error").getAsString().equals("PluginRestartRequired")) {
            record.addProperty("desired", false); record.addProperty("error", "PluginRestartRequired"); store.save(); return;
        }
        if (record.has("rollback") && !record.has("activated")) { store.rollback(id); record = record(id); }
        record.addProperty("desired", false); record.addProperty("error", error(e)); store.save();
        log(id, "PluginCallbackFailed: " + android.util.Log.getStackTraceString(e));
    }
    void callbackFailed(PluginScope scope, Exception e) {
        queue.execute(() -> {
            Running current = running.get(scope.id);
            if (current == null || current.scope != scope) return;
            try { fail(scope.id, e); publish(); } catch (Exception failure) { this.failure = error(failure); }
        });
    }
    private void publish() {
        Map<String, Set<String>> grants = new HashMap<>();
        for (String id : store.plugins().keySet()) {
            JsonObject record = store.plugins().getAsJsonObject(id);
            Set<String> allowed = new HashSet<>();
            if (record.has("accounts")) for (JsonElement identity : record.getAsJsonArray("accounts")) allowed.add(identity.getAsString());
            grants.put(id, allowed);
        }
        accountGrants = grants;
        JsonObject view = store.state.deepCopy();
        view.addProperty("safe", safe());
        for (String id : view.getAsJsonObject("plugins").keySet())
            view.getAsJsonObject("plugins").getAsJsonObject(id).addProperty("active", running.containsKey(id));
        snapshot = view;
        AndroidUtilities.runOnUIThread(() -> { for (Runnable listener : listeners) listener.run(); });
    }
    private interface Operation { void run() throws Exception; }
    public static int hostBuild() throws android.content.pm.PackageManager.NameNotFoundException {
        return ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(
                ApplicationLoader.applicationContext.getPackageName(), 0).versionCode;
    }
    private void submit(Operation operation, Result<Void> callback) {
        queue.execute(() -> {
            String result = null;
            try { operation.run(); }
            catch (Exception e) { result = error(e); failure = result; if (initialized) publish(); }
            String error = result;
            if (callback != null) AndroidUtilities.runOnUIThread(() -> callback.done(null, error));
        });
    }
    private static String error(Exception e) {
        if (e instanceof PluginManifest.ValidationException) return e.getMessage();
        String message = e.getMessage();
        if (message != null && message.matches("(?s)(?:PluginDependency(?:Unavailable|Outdated|Cycle)|HookTargetUnavailable|UnsupportedHookTarget): .*"))
            return message;
        if (message != null) for (String code : new String[]{"ContributionConflict", "HookConflict", "HookBackendUnavailable",
                "CapabilityDenied", "AccountRequired", "AccountUnavailable", "InvalidSetting", "SettingsMigrationFailed", "HookProbeFailed", "HookLimitExceeded"})
            if (message.contains(code)) return code;
        if (e instanceof IOException && e.getMessage() != null && e.getMessage().matches("[A-Za-z]+")) return e.getMessage();
        return "PluginOperationFailed (" + e.getClass().getSimpleName() + ")";
    }
    private void marker(String name, boolean enabled) throws IOException {
        android.util.AtomicFile file = new android.util.AtomicFile(new File(store.root, name));
        if (!enabled) { file.delete(); return; }
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(1); file.finishWrite(out); }
        catch (IOException e) { if (out != null) file.failWrite(out); throw e; }
    }
    private void activation(String id, String stage) throws IOException {
        JsonObject record = record(id), status = new JsonObject();
        status.addProperty("plugin_id", id); status.addProperty("stage", stage);
        status.add("version", record.getAsJsonObject("manifest").get("version")); status.add("hash", record.get("hash"));
        PluginStore.write(new android.util.AtomicFile(new File(store.root, "activation")), status);
        PluginStore.write(new android.util.AtomicFile(new File(store.root, "last-stage.json")), status);
    }
    private boolean hasMarker(String name) {
        File file = new File(store.root, name);
        return file.exists() || new File(file + ".new").exists() || new File(file + ".bak").exists();
    }
    void log(String id, String text) {
        if (pendingLogs.incrementAndGet() > 128) { pendingLogs.decrementAndGet(); return; }
        String bounded = text.substring(0, Math.min(4000, text.length()));
        queue.execute(() -> { try { writeLog(id, bounded); } finally { pendingLogs.decrementAndGet(); } });
    }
    private void writeLog(String id, String text) {
        if (store == null) return;
        try {
            File dir = new File(store.root, "logs");
            if (!dir.isDirectory() && !dir.mkdir()) return;
            File file = new File(dir, id + ".log");
            boolean prune = !file.exists() || file.length() > 512 * 1024;
            if (file.length() > 512 * 1024) { File previous = new File(dir, id + ".previous.log"); if (previous.exists()) previous.delete(); file.renameTo(previous); }
            byte[] line = (System.currentTimeMillis() + " " + text + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(line);
            }
            if (logBytes >= 0) logBytes += line.length;
            if (prune || logBytes < 0 || logBytes > 10 * 1024 * 1024) {
                File[] logs = dir.listFiles();
                if (logs != null) {
                    Arrays.sort(logs, Comparator.comparingLong(File::lastModified));
                    long total = 0; for (File entry : logs) total += entry.length();
                    for (File entry : logs) {
                        if (total <= 10 * 1024 * 1024) break;
                        long length = entry.length(); if (entry.delete()) total -= length;
                    }
                    logBytes = total;
                }
            }
        } catch (IOException ignored) { }
    }
}
