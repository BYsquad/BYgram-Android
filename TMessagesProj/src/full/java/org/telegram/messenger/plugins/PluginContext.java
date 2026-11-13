package org.telegram.messenger.plugins;

import android.util.AtomicFile;
import com.chaquo.python.PyObject;
import com.google.gson.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.PluginHost;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Future;

/** The SDK boundary. This is not a sandbox for code which can use the Java bridge directly. */
public final class PluginContext {
    public final PluginScope scope;
    public final PluginManifest manifest;
    private final File packageDirectory, dataDirectory;
    private final PluginManager manager;
    private final PluginWork work = new PluginWork();
    private final PluginCallbacks uiCallbacks = new PluginCallbacks();
    private final Map<String, JsonObject> settings = new HashMap<>();
    private final Map<String, PyObject> callbacks = new HashMap<>();
    private final Map<String, JsonObject> fields = new HashMap<>();
    private volatile boolean migrating;
    volatile boolean interceptorsReady;
    private long errorWindow;
    private int errors;
    public String migrationScope = "";

    PluginContext(PluginScope scope, PluginManifest manifest, File packageDirectory, File dataDirectory,
                  PluginManager manager) throws IOException {
        this.scope = scope; this.manifest = manifest;
        this.packageDirectory = packageDirectory; this.dataDirectory = dataDirectory; this.manager = manager;
        PluginPackage.check(dataDirectory.mkdirs() || dataDirectory.isDirectory(), "StorageUnavailable");
        try {
            scope.own(() -> { synchronized (callbacks) { callbacks.clear(); } });
            scope.own(work);
            scope.own(uiCallbacks);
        }
        catch (Exception e) { throw new IOException("PluginDisabled", e); }
    }
    public String id() { return scope.id; }
    public long generation() { return scope.generation; }
    public String version() { return manifest.version(); }
    public boolean active() { return scope.isActive(); }
    public void check() {
        scope.check();
        if (migrating) throw new IllegalStateException("SettingsMigrationInProgress");
    }
    void migrate(PyObject plugin) throws Exception {
        AtomicFile versionFile = new AtomicFile(new File(dataDirectory, "schema.json"));
        JsonObject metadata = PluginStore.read(versionFile);
        int next = manifest.metadata().get("settings_schema").getAsInt();
        int previous = metadata.has("version") ? metadata.get("version").getAsInt() : next;
        if (previous != next) {
            JsonObject backups = new JsonObject(), replacements = new JsonObject();
            for (String name : PluginStore.settingNames(dataDirectory)) {
                if (name.equals("schema.json")) continue;
                File file = new File(dataDirectory, name);
                JsonObject values = PluginStore.read(new AtomicFile(file)); backups.add(file.getName(), values);
                migrating = true; migrationScope = file.getName().replace(".json", "");
                try {
                    String migrated = plugin.callAttr("_migrate_json", previous, next, values.toString()).toString();
                    scope.check();
                    PluginPackage.check(migrated.getBytes(StandardCharsets.UTF_8).length <= 1024 * 1024, "SettingsLimitExceeded");
                    JsonObject valuesNext = JsonParser.parseString(migrated).getAsJsonObject();
                    validateJson(valuesNext, 0); replacements.add(file.getName(), valuesNext);
                } finally { migrating = false; migrationScope = ""; }
            }
            backups.add("schema.json", metadata);
            AtomicFile journal = new AtomicFile(new File(dataDirectory, "migration-backup.json"));
            scope.check();
            PluginStore.write(journal, backups, PluginPackage.MAX_TOTAL);
            try {
                for (String name : replacements.keySet()) { scope.check(); PluginStore.write(new AtomicFile(new File(dataDirectory, name)), replacements.getAsJsonObject(name)); }
                scope.check();
                metadata.addProperty("version", next); PluginStore.write(versionFile, metadata);
                journal.delete();
            } catch (Exception e) { recoverMigration(dataDirectory); throw new IOException("SettingsMigrationFailed", e); }
        } else { scope.check(); metadata.addProperty("version", next); PluginStore.write(versionFile, metadata); }
        synchronized (this) { settings.clear(); }
    }
    static void recoverMigration(File directory) throws IOException { PluginStore.recoverMigration(directory); }
    public void capability(String name) {
        check();
        for (JsonElement value : manifest.metadata().getAsJsonArray("capabilities")) if (value.getAsString().equals(name)) return;
        throw new IllegalStateException("CapabilityDenied");
    }
    public void networkAllowed() {
        capability("network");
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) throw new IllegalStateException("NetworkOnMainThread");
    }
    public void log(String level, String text) { check(); manager.log(id(), level + ": " + text); }

    public static void setReflectedField(java.lang.reflect.Field field, Object object, PyObject value)
            throws IllegalAccessException {
        field.set(object, value == null ? null : value.toJava(field.getType()));
    }

    public String filesPath() {
        check();
        return new File(dataDirectory, "files").getAbsolutePath();
    }

    public synchronized String settingsGet(String key, String account) throws IOException {
        check(); JsonObject values = settings(account);
        return values.has(key) ? values.get(key).toString() : "null";
    }
    public synchronized boolean settingsHas(String key, String account) throws IOException { check(); return settings(account).has(key); }
    public synchronized String settingsExport() throws IOException {
        check();
        return settings("").toString();
    }
    public synchronized void settingsImport(String json) throws IOException {
        check();
        PluginPackage.check(json.length() <= 64 * 1024, "SettingsLimitExceeded");
        JsonObject values = JsonParser.parseString(json).getAsJsonObject();
        validateJson(values, 0);
        for (String key : values.keySet()) {
            PluginPackage.check(key.matches("[A-Za-z_][A-Za-z0-9_.-]{0,119}"), "InvalidSettingKey");
            JsonObject field = fields.get("global:" + key);
            if (field != null) PluginPageSpec.validateValue(field, values.get(key));
        }
        check();
        PluginStore.write(settingsFile(""), values);
        settings.put("", values);
        PluginHost.settingsChanged();
    }
    public synchronized void settingsSet(String key, String json, String account) throws IOException {
        check(); PluginPackage.check(key != null && key.matches("[A-Za-z_][A-Za-z0-9_.-]{0,119}"), "InvalidSettingKey");
        PluginPackage.check(json.length() <= 64 * 1024, "SettingsLimitExceeded");
        JsonElement value = JsonParser.parseString(json);
        validateJson(value, 0);
        String schemaKey = (account.isEmpty() ? "global:" : "account:") + key;
        if (fields.containsKey(schemaKey)) {
            JsonObject field = fields.get(schemaKey);
            boolean accountField = field.has("scope") && field.get("scope").getAsString().equals("account");
            if (accountField == account.isEmpty()) throw new IllegalArgumentException("InvalidSettingScope");
            PluginPageSpec.validateValue(field, value);
        }
        JsonObject values = settings(account).deepCopy(); values.add(key, value);
        validateJson(values, 0);
        AtomicFile file = settingsFile(account);
        check(); PluginStore.write(file, values); settings.put(account, values);
        PluginHost.settingsChanged();
    }
    static void validateJson(JsonElement value, int depth) {
        PluginSettingsFile.validateValue(value, depth);
    }
    private JsonObject settings(String account) throws IOException {
        if (!account.isEmpty()) account(Integer.parseInt(account.split(":")[0]), account);
        if (!settings.containsKey(account)) settings.put(account, PluginStore.read(settingsFile(account)));
        return settings.get(account);
    }
    private AtomicFile settingsFile(String account) throws IOException {
        String name = "global";
        if (!account.isEmpty()) { account(Integer.parseInt(account.split(":")[0]), account); name = "account-" + account.replace(':', '-'); }
        return new AtomicFile(new File(dataDirectory, name + ".json"));
    }
    public String accountIdentity(int slot) {
        check();
        return currentIdentity(slot);
    }
    public static String currentIdentity(int slot) {
        if (slot < 0 || slot >= UserConfig.MAX_ACCOUNT_COUNT) throw new IllegalArgumentException("AccountUnavailable");
        UserConfig config = UserConfig.getInstance(slot);
        if (!config.isClientActivated()) throw new IllegalStateException("AccountUnavailable");
        if (!config.bygramPluginAuthGeneration.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) throw new IllegalStateException("AccountUnavailable");
        return slot + ":" + config.getClientUserId() + ":" + config.bygramPluginAuthGeneration;
    }
    public void account(int slot, String identity) {
        check();
        if (!manifest.metadata().get("account_scope").getAsString().equals("selected_accounts")) throw new IllegalStateException("AccountRequired");
        if (!accountIdentity(slot).equals(identity)) throw new IllegalStateException("AccountUnavailable");
        if (manager.accountAllowed(id(), identity)) return;
        throw new IllegalStateException("AccountUnavailable");
    }
    public String[] allowedAccounts() {
        check(); ArrayList<String> allowed = new ArrayList<>();
        if (!manifest.metadata().get("account_scope").getAsString().equals("selected_accounts")) return new String[0];
        for (String identity : manager.allowedAccounts(id())) {
            try { if (currentIdentity(Integer.parseInt(identity.split(":")[0])).equals(identity)) allowed.add(identity); }
            catch (IllegalStateException | IllegalArgumentException unavailable) { }
        }
        return allowed.toArray(new String[0]);
    }
    public boolean accountEventAllowed(String identity) {
        return active() && manifest.metadata().get("account_scope").getAsString().equals("selected_accounts")
                && manager.accountAllowed(id(), identity);
    }
    public boolean accountAvailable(int slot, String identity) {
        try { account(slot, identity); return true; }
        catch (IllegalStateException | IllegalArgumentException unavailable) { return false; }
    }
    public void runUi(PyObject callback) throws Exception {
        check();
        dispatchUiCallback(callback);
    }
    public void runUiDelayed(PyObject callback, long delay) throws Exception {
        check();
        if (delay < 0) throw new IllegalArgumentException("InvalidDelay");
        class Task implements Runnable {
            AutoCloseable registration;
            @Override public void run() {
                try {
                    registration.close();
                    if (!scope.beginCallback()) return;
                    try { callback.call(); } finally { scope.endCallback(); }
                } catch (Exception error) { failed(error); }
            }
        }
        Task task = new Task();
        task.registration = scope.own(() -> AndroidUtilities.cancelRunOnUIThread(task));
        AndroidUtilities.runOnUIThread(task, delay);
        if (!scope.isActive()) AndroidUtilities.cancelRunOnUIThread(task);
    }
    public AutoCloseable submit(PyObject callback) throws Exception {
        check();
        return work.submit(scope, () -> call(callback));
    }
    public void call(PyObject callback, Object... args) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            try { dispatchUiCallback(callback, args); } catch (Exception error) { failed(error); }
            return;
        }
        if (!scope.beginCallback()) return;
        long started = android.os.SystemClock.elapsedRealtime();
        try { callback.call(args); }
        catch (Exception e) { failed(e); }
        finally {
            scope.endCallback();
            long elapsed = android.os.SystemClock.elapsedRealtime() - started;
            if (elapsed > 16) manager.log(id(), "SlowCallback " + elapsed + "ms");
        }
    }
    void appEvent(PyObject plugin, String event) {
        PyObject callback = plugin.get("_compat_app_event");
        if (callback == null) return;
        try { dispatchUiCallback(callback, event); }
        catch (Exception error) { failed(error); }
    }
    private void dispatchUiCallback(PyObject callback, Object... args) throws Exception {
        dispatchUiCallback(() -> call(callback, args));
    }
    private void dispatchUiCallback(Runnable callback) throws Exception {
        uiCallbacks.dispatch(scope, callback, () -> {
            scope.invalidate(); manager.callbackFailed(scope, new IOException("PluginRestartRequired"));
        });
    }
    public void callMessageMenu(PyObject callback, int account, String identity, long peer, Object... event) {
        try {
            dispatchUiCallback(() -> {
                if (accountAvailable(account, identity) && dialogAvailable(account, peer)) call(callback, event);
            });
        } catch (Exception error) { failed(error); }
    }
    public synchronized void failed(Exception error) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - errorWindow >= 60000) { errorWindow = now; errors = 0; }
        manager.log(id(), "PluginCallbackFailed: " + android.util.Log.getStackTraceString(error));
        if (++errors >= 3) { scope.invalidate(); manager.callbackFailed(scope, error); }
    }
    public void bulletin(String text) {
        bulletin(text, "info");
    }
    public void bulletin(String text, String type) {
        capability("ui");
        if (text.length() > 1000 || !Arrays.asList("info", "error", "success").contains(type))
            throw new IllegalArgumentException("InvalidBulletin");
        PluginHost.bulletin(this, text, type);
    }
    public org.telegram.ui.ActionBar.BaseFragment currentFragment() {
        capability("ui");
        return PluginHost.currentFragment();
    }
    public PluginHost.PluginAlert alert(String json, android.view.View view, PyObject callback) throws Exception {
        capability("ui");
        if (json.length() > 64 * 1024) throw new IllegalArgumentException("PageLimitExceeded");
        return PluginHost.alert(this, JsonParser.parseString(json).getAsJsonObject(), view, callback);
    }
    public AutoCloseable menu(String location, String localId, String title, PyObject callback) throws Exception {
        capability("ui"); return scope.own(PluginHost.menu(this, location, localId, title, callback));
    }
    public AutoCloseable compatMenu(String location, String id, String title, String icon, String subtext,
                                    int priority, PyObject callback, PyObject condition) throws Exception {
        capability("ui");
        if ("message.context".equals(location)) capability("messages.observe");
        return scope.own(PluginHost.menu(this, location, id, title, icon, subtext, priority, callback, condition));
    }
    public boolean menuVisible(PyObject condition, Object... args) {
        if (condition == null) return active();
        if (!scope.beginCallback()) return false;
        try { return condition.call(args).toBoolean(); }
        catch (Exception error) { failed(error); return false; }
        finally { scope.endCallback(); }
    }
    public AutoCloseable intentHandler(boolean before, int priority, PyObject callback) throws Exception {
        capability("hooks.telegram");
        return scope.own(PluginHost.intentHandler(this, before, priority, callback));
    }
    public AutoCloseable intercept(String kind, String filter, boolean substring, int priority, PyObject callback) throws Exception {
        capability("hooks.telegram");
        if (!Arrays.asList("request", "response", "updates", "update", "send").contains(kind)
                || filter == null || !filter.matches("[A-Za-z0-9_$]{0,128}") || callback == null) {
            throw new IllegalArgumentException("InvalidTelegramHook");
        }
        return scope.own(PluginInterceptors.add(id(), kind, filter, substring, priority, (slot, name, value, detail) -> {
            if (!interceptorsReady || !scope.beginCallback()) return null;
            long started = android.os.SystemClock.elapsedRealtime();
            try {
                String identity;
                try { identity = currentIdentity(slot); }
                catch (IllegalStateException | IllegalArgumentException unavailable) { return null; }
                if (!accountAvailable(slot, identity)) return null;
                PyObject result = callback.call(name, slot, value, detail);
                return accountAvailable(slot, identity) && result != null
                        ? result.toJava(PluginInterceptors.Result.class) : null;
            } finally {
                scope.endCallback();
                long elapsed = android.os.SystemClock.elapsedRealtime() - started;
                if (elapsed > 16) manager.log(id(), "SlowTelegramHook " + elapsed + "ms");
            }
        }, this::failed));
    }
    public void openPage(String json) {
        capability("ui");
        openSettings(json);
    }
    void configureSettings(String json) {
        check();
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64 * 1024) throw new IllegalArgumentException("PageLimitExceeded");
        JsonObject page = JsonParser.parseString(json).getAsJsonObject();
        try { validatePage(page); } finally { releasePage(page); }
    }
    void openSettings(String json) {
        check();
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64 * 1024) throw new IllegalArgumentException("PageLimitExceeded");
        JsonObject page = JsonParser.parseString(json).getAsJsonObject();
        try { validatePage(page); PluginHost.openPage(this, page.toString()); }
        catch (RuntimeException error) { releasePage(page); throw error; }
    }
    private synchronized void validatePage(JsonObject page) {
        Set<String> registered;
        synchronized (callbacks) { registered = new HashSet<>(callbacks.keySet()); }
        Map<String, JsonObject> validated = PluginPageSpec.validate(page,
                manifest.metadata().get("account_scope").getAsString().equals("selected_accounts"), registered);
        Set<String> keys = new HashSet<>(fields.keySet()); keys.addAll(validated.keySet());
        if (keys.size() > 512) throw new IllegalArgumentException("SettingsLimitExceeded");
        for (String key : validated.keySet()) {
            JsonObject previous = fields.get(key), next = validated.get(key);
            if (previous != null) for (String property : new String[]{"type", "scope", "max_length", "mask", "min", "max", "step", "options"})
                if (!Objects.equals(previous.get(property), next.get(property))) throw new IllegalArgumentException("SettingSchemaConflict");
        }
        fields.putAll(validated);
    }
    public synchronized String validSetting(String key, String fallback, String account) throws IOException {
        check();
        JsonObject field = fields.get((account.isEmpty() ? "global:" : "account:") + key);
        JsonElement value = settings(account).get(key);
        if (value == null) return fallback;
        try { if (field != null) PluginPageSpec.validateValue(field, value); return value.toString(); }
        catch (IllegalArgumentException e) { return fallback; }
    }
    public String registerCallback(PyObject callback) {
        check();
        synchronized (callbacks) {
            if (callbacks.size() >= 512) throw new IllegalStateException("CallbackLimitExceeded");
            String key = java.util.UUID.randomUUID().toString(); callbacks.put(key, callback); return key;
        }
    }
    public void releaseCallback(String key) {
        synchronized (callbacks) { callbacks.remove(key); }
    }
    public void releasePage(JsonObject page) {
        if (!page.has("items") || !page.get("items").isJsonArray()) return;
        for (JsonElement item : page.getAsJsonArray("items")) {
            if (item.isJsonObject()) {
                for (String property : new String[]{"callback", "change_callback", "long_callback", "row_factory"}) {
                    JsonElement callback = item.getAsJsonObject().get(property);
                    if (callback != null && callback.isJsonPrimitive()) releaseCallback(callback.getAsString());
                }
            }
        }
    }
    public void callNamed(String key, Object... args) {
        PyObject callback;
        synchronized (callbacks) { callback = callbacks.get(key); }
        if (callback == null) throw new IllegalArgumentException("UnknownCallback");
        call(callback, args);
    }
    public Object createSettingsRow(String key, android.content.Context context) {
        PyObject callback;
        synchronized (callbacks) { callback = callbacks.get(key); }
        if (callback == null || !scope.beginCallback()) return null;
        try {
            PyObject result = callback.call(context);
            return result == null ? null : result.toJava(Object.class);
        } catch (Exception error) { failed(error); return null; }
        finally { scope.endCallback(); }
    }
    public void sendText(int slot, String identity, long peer, String text) {
        capability("messages.send"); account(slot, identity);
        if (peer == 0 || text == null || text.isEmpty() || text.length() > 4096) throw new IllegalArgumentException("InvalidMessage");
        AndroidUtilities.runOnUIThread(() -> {
            if (!scope.isActive()) return;
            try {
                account(slot, identity);
                if (BygramChatPrivacy.isHidden(slot, peer) || BygramChatPrivacy.isProtected(slot, peer)) throw new IllegalStateException("PrivateDialogUnavailable");
                SendMessagesHelper.getInstance(slot).sendMessage(SendMessagesHelper.SendMessageParams.of(text, peer));
            } catch (Exception e) { failed(e); }
        });
    }
    public int sendRequest(int slot, String identity, TLObject request, PyObject callback) {
        capability("telegram.requests");
        account(slot, identity);
        if (request == null || callback == null) throw new IllegalArgumentException("InvalidTelegramRequest");
        return ConnectionsManager.getInstance(slot).sendPluginRequest(request, (response, error) -> {
            if (accountAvailable(slot, identity)) call(callback, response, error);
        }, () -> accountAvailable(slot, identity));
    }
    private File mediaFile(String path) throws IOException {
        File source = new File(path);
        if (!source.isAbsolute()) {
            source = contained(dataDirectory, path);
            if (!source.isFile()) source = contained(packageDirectory, path);
        }
        File file = source.getCanonicalFile();
        String absolute = file.getPath();
        if ((!absolute.startsWith(dataDirectory.getCanonicalPath() + File.separator)
                && !absolute.startsWith(packageDirectory.getCanonicalPath() + File.separator)) || !file.isFile() || file.length() == 0)
            throw new IOException("InvalidStoragePath");
        return file;
    }
    private java.util.function.LongPredicate sendGuard(int slot, String identity, long peer) {
        return actualPeer -> actualPeer == peer && accountAvailable(slot, identity)
                && !BygramChatPrivacy.isHidden(slot, peer) && !BygramChatPrivacy.isProtected(slot, peer);
    }
    private SendMessageChatArguments sendArguments(java.util.function.LongPredicate allowed) {
        SendMessageChatArguments.Builder builder = new SendMessageChatArguments.Builder();
        builder.setBygramPluginSendAllowed(allowed);
        return builder.build();
    }
    public void sendPrepared(int slot, String identity, SendMessagesHelper.SendMessageParams params) throws IOException {
        boolean media = params.photo != null || params.document != null;
        capability(media ? "messages.media" : "messages.send");
        account(slot, identity);
        long peer = params.peer;
        if (peer == 0 || !media && (params.message == null || params.message.isEmpty() || params.message.length() > 4096)
                || params.caption != null && params.caption.length() > 1024
                || params.replyToMsg != null && (params.replyToMsg.currentAccount != slot || params.replyToMsg.getDialogId() != peer))
            throw new IllegalArgumentException("InvalidMessage");
        if (params.path != null) params.path = mediaFile(params.path).getAbsolutePath();
        java.util.function.LongPredicate allowed = sendGuard(slot, identity, peer);
        params.sendMessageChatArguments = sendArguments(allowed);
        AndroidUtilities.runOnUIThread(() -> {
            if (!allowed.test(params.peer)) return;
            try { SendMessagesHelper.getInstance(slot).sendMessage(params); }
            catch (Exception error) { failed(error); }
        });
    }
    public void setSendParameter(SendMessagesHelper.SendMessageParams params, String key, PyObject value)
            throws ReflectiveOperationException {
        check();
        if (!Arrays.asList("peer", "message", "caption", "photo", "document", "path", "replyToMsg",
                "replyMarkup", "entities", "params", "notify", "scheduleDate", "ttl",
                "hasMediaSpoilers", "sendingHighQuality", "searchLinks").contains(key))
            throw new IllegalArgumentException("InvalidMessageParameter");
        setReflectedField(SendMessagesHelper.SendMessageParams.class.getField(key), params, value);
    }
    public void sendMedia(int slot, String identity, long peer, String kind, String path, String caption) throws IOException {
        mediaPrepared(slot, identity, peer, kind, path, caption, null, null, true, 0, 0, false, false, null);
    }
    public void mediaPrepared(int slot, String identity, long peer, String kind, String path, String caption,
                              ArrayList<org.telegram.tgnet.TLRPC.MessageEntity> entities, MessageObject reply, boolean notify,
                              int schedule, int ttl, boolean spoiler, boolean quality, MessageObject edit) throws IOException {
        capability("messages.media");
        account(slot, identity);
        if (peer == 0 || caption == null || caption.length() > 1024
                || !Arrays.asList("photo", "document", "video", "audio").contains(kind)
                || reply != null && (reply.currentAccount != slot || reply.getDialogId() != peer)
                || edit != null && (edit.currentAccount != slot || edit.getDialogId() != peer))
            throw new IllegalArgumentException("InvalidPluginMedia");
        File file = mediaFile(path);
        String absolute = file.getAbsolutePath();
        java.util.function.LongPredicate allowed = sendGuard(slot, identity, peer);
        AndroidUtilities.runOnUIThread(() -> {
            if (!allowed.test(peer)) return;
            try {
                SendMessageChatArguments arguments = sendArguments(allowed);
                AccountInstance instance = AccountInstance.getInstance(slot);
                SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                info.path = absolute; info.caption = caption; info.entities = entities; info.ttl = ttl;
                info.isVideo = kind.equals("video"); info.hasMediaSpoilers = spoiler; info.highQuality = quality;
                boolean document = kind.equals("document") || kind.equals("audio");
                SendMessagesHelper.prepareSendingMedia(instance, new ArrayList<>(Collections.singletonList(info)),
                        peer, reply, null, null, null, document, false, edit, notify, schedule, 0, 0,
                        false, null, arguments, 0, false, 0, 0, null);
            } catch (Exception failure) { failed(failure); }
        });
    }
    public void editText(int slot, String identity, MessageObject message, String text) {
        editPrepared(slot, identity, message, text, null);
    }
    public void editPrepared(int slot, String identity, MessageObject message, String text,
                             ArrayList<org.telegram.tgnet.TLRPC.MessageEntity> entities) {
        capability("messages.media");
        account(slot, identity);
        if (message == null || message.currentAccount != slot || text == null || text.isEmpty() || text.length() > 4096)
            throw new IllegalArgumentException("InvalidMessage");
        long peer = message.getDialogId();
        AndroidUtilities.runOnUIThread(() -> {
            if (!accountAvailable(slot, identity) || BygramChatPrivacy.isHidden(slot, peer)
                    || BygramChatPrivacy.isProtected(slot, peer)) return;
            try { SendMessagesHelper.getInstance(slot).editMessage(message, text, false, PluginHost.currentFragment(), entities, 0, 0); }
            catch (Exception failure) { failed(failure); }
        });
    }
    public String draftText(int slot, String identity, long peer) {
        capability("ui"); capability("messages.observe"); account(slot, identity);
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return PluginHost.draftText(this, slot, identity, peer);
        java.util.concurrent.FutureTask<String> read = new java.util.concurrent.FutureTask<>(() -> {
            check(); return PluginHost.draftText(this, slot, identity, peer);
        });
        AndroidUtilities.runOnUIThread(read);
        try { return read.get(1000, java.util.concurrent.TimeUnit.MILLISECONDS); }
        catch (java.util.concurrent.ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("ChatUnavailable", cause);
        } catch (InterruptedException | java.util.concurrent.TimeoutException error) {
            read.cancel(false); AndroidUtilities.cancelRunOnUIThread(read);
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("ChatUnavailable", error);
        }
    }
    public void previewText(int slot, String identity, long peer, String text) {
        capability("ui"); capability("messages.send"); account(slot, identity);
        if (peer == 0 || text == null || text.length() > 4096) throw new IllegalArgumentException("InvalidMessage");
        AndroidUtilities.runOnUIThread(() -> {
            if (!active()) return;
            try { PluginHost.previewText(this, slot, identity, peer, text); }
            catch (Exception error) { failed(error); }
        });
    }
    public boolean dialogAvailable(int slot, long peer) {
        try {
            account(slot, currentIdentity(slot));
            if (BygramChatPrivacy.isHidden(slot, peer) || BygramChatPrivacy.isProtected(slot, peer)) return false;
            for (JsonElement value : manifest.metadata().getAsJsonArray("capabilities"))
                if (Arrays.asList("messages.observe", "messages.send", "messages.media").contains(value.getAsString())) return true;
        } catch (IllegalStateException | IllegalArgumentException unavailable) { }
        return false;
    }
    public AutoCloseable subscribe(String name, PyObject callback) throws Exception {
        check(); if ("network.completed".equals(name)) capability("network");
        if (("account.added".equals(name) || "account.logged_out".equals(name))
                && !manifest.metadata().get("account_scope").getAsString().equals("selected_accounts")) throw new IllegalStateException("AccountRequired");
        return scope.own(PluginHost.subscribe(this, name, callback));
    }
    public AutoCloseable observeMessages(int slot, String identity, PyObject callback) throws Exception {
        capability("messages.observe"); account(slot, identity);
        NotificationCenter.NotificationCenterDelegate observer = (event, account, args) -> {
            if (!active()) return;
            try {
                account(slot, identity);
                long dialog = (Long) args[0];
                if (BygramChatPrivacy.isHidden(slot, dialog) || BygramChatPrivacy.isProtected(slot, dialog)) return;
                java.util.List<?> messages = (java.util.List<?>) args[1];
                JsonObject dto = new JsonObject(); dto.addProperty("account", slot);
                dto.addProperty("user_id", UserConfig.getInstance(slot).getClientUserId()); dto.addProperty("dialog_id", dialog);
                JsonArray ids = new JsonArray();
                for (int i = 0; i < Math.min(50, messages.size()); i++) ids.add(((MessageObject) messages.get(i)).getId());
                dto.add("message_ids", ids); dto.addProperty("dropped", Math.max(0, messages.size() - 50));
                dto.addProperty("scheduled", args.length > 2 && Boolean.TRUE.equals(args[2]));
                call(callback, dto.toString());
            } catch (IllegalStateException unavailable) { /* Logout invalidates identities before delivery. */ }
        };
        AndroidUtilities.runOnUIThread(() -> { if (active()) NotificationCenter.getInstance(slot).addObserver(observer, NotificationCenter.didReceiveNewMessages); });
        return scope.own(() -> AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(slot).removeObserver(observer, NotificationCenter.didReceiveNewMessages)));
    }
    public void networkCompleted(int size) {
        capability("network");
        PluginHost.emit(this, "network.completed", "{\"plugin_id\":\"" + id() + "\",\"bytes\":" + size + "}");
    }
    public AutoCloseable theme(String key, int color) throws Exception {
        capability("theme"); return scope.own(PluginHost.theme(this, key, color));
    }
    public String readText(String path, boolean asset) throws IOException {
        check(); File root = asset ? packageDirectory : dataDirectory;
        File file = contained(root, path);
        return new String(PluginPackage.read(file), StandardCharsets.UTF_8);
    }
    public void writeText(String path, String text) throws IOException {
        check(); File file = contained(dataDirectory, path);
        if (file.getParentFile().getCanonicalFile().equals(dataDirectory.getCanonicalFile())) {
            String name = file.getName().replaceFirst("\\.(bak|new)$", "");
            PluginPackage.check(!PluginStore.settingFile(name) && !name.equals("migration-backup.json"), "ReservedStoragePath");
        }
        PluginPackage.check(file.getParentFile().mkdirs() || file.getParentFile().isDirectory(), "StorageUnavailable");
        PluginPackage.check(text.getBytes(StandardCharsets.UTF_8).length <= 1024 * 1024, "StorageLimitExceeded");
        AtomicFile atomic = new AtomicFile(file); FileOutputStream out = null;
        try { out = atomic.startWrite(); out.write(text.getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(out); }
        catch (IOException e) { if (out != null) atomic.failWrite(out); throw e; }
    }
    private static File contained(File root, String relative) throws IOException {
        PluginPackage.check(relative != null && !relative.startsWith("/") && !relative.contains("\\") && !relative.contains(":"), "InvalidStoragePath");
        File file = new File(root, relative);
        PluginPackage.check(file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator), "InvalidStoragePath");
        return file;
    }
}
