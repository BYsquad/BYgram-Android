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
    private long generation;
    private volatile String failure = "";
    private final java.util.concurrent.atomic.AtomicInteger pendingLogs = new java.util.concurrent.atomic.AtomicInteger();
    private long logBytes = -1;

    private static final class Running {
        PluginScope scope; PyObject plugin; PluginContext context;
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
        store = new PluginStore(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "plugins"));
        if (hasMarker("activation") || hasMarker("startup"))
            PluginRecovery.setSafe(ApplicationLoader.applicationContext, true);
        store.state.remove("recovery_error");
        try {
            for (String id : store.plugins().keySet()) PluginContext.recoverMigration(new File(store.data, id));
            if (hasMarker("activation")) store.recoverActivation(PluginStore.read(new android.util.AtomicFile(new File(store.root, "activation"))));
            store.applyRollbacks();
            store.applyPending();
            store.collectPackages();
        } catch (Exception recoveryError) {
            PluginRecovery.setSafe(ApplicationLoader.applicationContext, true);
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
        store.save();
        initialized = true;
        publish();
        if (engine() && !safe()) {
            marker("startup", true);
            for (String id : new ArrayList<>(store.plugins().keySet())) {
                JsonObject record = store.plugins().getAsJsonObject(id);
                if (record.get("desired").getAsBoolean() && record.has("approved")
                        && record.get("hash").equals(record.get("approved"))) {
                    try { enable(id); } catch (Exception e) { fail(id, e); }
                }
            }
            AndroidUtilities.runOnUIThread(() -> queue.execute(() -> {
                try { marker("startup", false); } catch (IOException e) { failure = "RecoveryMarkerFailed"; }
            }), 10000);
        }
        publish();
    }

    public void onResume() {
        submit(() -> {
            init();
            if (safe()) stopAll();
            publish();
        }, null);
    }
    public boolean safe() { return PluginRecovery.isSafe(ApplicationLoader.applicationContext); }
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
            init(); PluginRecovery.setSafe(ApplicationLoader.applicationContext, enabled);
            if (enabled) stopAll();
            // Leaving recovery never enables code implicitly.
            else {
                store.state.addProperty("engine", false);
                for (String id : store.plugins().keySet()) store.plugins().getAsJsonObject(id).addProperty("desired", false);
                store.save(); marker("startup", false); marker("activation", false);
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
                String error = error(e); AndroidUtilities.runOnUIThread(() -> callback.done(null, error));
            }
            });
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
    public PluginCatalog.Request catalog(Result<JsonArray> callback) {
        PluginCatalog.Request request = new PluginCatalog.Request();
        catalogWork(() -> {
            try {
                JsonArray entries = PluginCatalog.parse(PluginCatalog.fetch("index.json", 1024 * 1024, request), hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
                AndroidUtilities.runOnUIThread(() -> callback.done(entries, null));
            } catch (Exception e) { String error = error(e); AndroidUtilities.runOnUIThread(() -> callback.done(null, error)); }
        }, callback);
        return request;
    }
    private <T> void catalogWork(Runnable task, Result<T> callback) {
        try { downloads.execute(task); }
        catch (RejectedExecutionException busy) { AndroidUtilities.runOnUIThread(() -> callback.done(null, "CatalogBusy")); }
    }
    public PluginCatalog.Request stageCatalog(JsonObject entry, Result<String> callback) {
        PluginCatalog.Request request = new PluginCatalog.Request();
        catalogWork(() -> {
            final byte[] bytes;
            try { bytes = PluginCatalog.fetch(entry.get("file").getAsString(), PluginPackage.MAX_ZIP, request); }
            catch (Exception e) {
                String error = error(e); AndroidUtilities.runOnUIThread(() -> callback.done(null, error)); return;
            }
            queue.execute(() -> {
                String token = UUID.randomUUID().toString(); File dir = null;
                try {
                    request.check();
                    init(); dir = new File(store.staging, token);
                    PluginPackage pkg = PluginCatalog.stage(entry, bytes, dir, hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
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
    public void setEnabled(String id, boolean enabled, Result<Void> callback) {
        submit(() -> {
            init(); JsonObject record = record(id);
            if (enabled) {
                checkRestart(id);
                if (!engine() || safe()) throw new IOException("EngineDisabled");
                record.addProperty("approved", record.get("hash").getAsString());
                record.addProperty("desired", true); record.remove("error"); store.save();
                try { enable(id); } catch (Exception e) { fail(id, e); throw e; }
            } else { record.addProperty("desired", false); store.save(); stop(id); }
            publish();
        }, callback);
    }
    private void enable(String id) throws Exception {
        if (running.containsKey(id)) return;
        if (!engine() || safe()) throw new IOException("EngineDisabled");
        JsonObject record = record(id);
        checkRestart(id);
        if (!record.has("approved") || !record.get("approved").equals(record.get("hash"))) throw new IOException("ApprovalRequired");
        File directory = store.packageDir(record);
        String signer = PluginSignature.verify(directory);
        PluginPackage.check(Objects.equals(record.has("author_key") ? record.get("author_key").getAsString() : null, signer), "InvalidAuthorSignature");
        PluginManifest manifest = PluginManifest.parse(PluginPackage.read(new File(directory, "manifest.json")),
                1, hostBuild(), BuildConfig.BYGRAM_BASE_BUILD);
        if (manifest.metadata().has("requires_hook_backend") && manifest.metadata().get("requires_hook_backend").getAsBoolean()
                && !HookBridge.available()) throw new IOException("HookBackendUnavailable");
        activation(id, "import");
        Running instance = new Running(new PluginScope(id, ++generation));
        running.put(id, instance);
        try {
            if (manifest.metadata().has("requires_hook_backend") && manifest.metadata().get("requires_hook_backend").getAsBoolean()) {
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
        } catch (Exception e) {
            if ("PluginRestartRequired".equals(e.getMessage())) restartRequired.add(id);
            stop(id); marker("activation", false); throw e;
        }
    }
    private void stop(String id) {
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
        submit(() -> { init(); stopForMutation(id); store.rollback(id); publish(); }, callback);
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
    public void exportSettings(String id, Uri uri, Result<Void> callback) {
        submit(() -> {
            init(); JsonObject record = record(id), exported = new JsonObject();
            JsonObject scopes = store.settingsSnapshot(id);
            if (!scopes.has("schema.json")) {
                JsonObject schema = new JsonObject(); schema.add("version", record.getAsJsonObject("manifest").get("settings_schema"));
                scopes.add("schema.json", schema);
            }
            exported.addProperty("format", 1); exported.addProperty("plugin_id", id); exported.add("scopes", scopes);
            byte[] bytes = exported.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            PluginSettingsFile.parse(bytes, id);
            try (OutputStream out = ApplicationLoader.applicationContext.getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("FileUnavailable"); out.write(bytes);
            }
        }, callback);
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
