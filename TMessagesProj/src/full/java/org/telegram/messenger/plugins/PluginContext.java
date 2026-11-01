package org.telegram.messenger.plugins;

import android.util.AtomicFile;
import com.chaquo.python.PyObject;
import com.google.gson.*;
import org.telegram.messenger.*;
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

    public synchronized String settingsGet(String key, String account) throws IOException {
        check(); JsonObject values = settings(account);
        return values.has(key) ? values.get(key).toString() : "null";
    }
    public synchronized boolean settingsHas(String key, String account) throws IOException { check(); return settings(account).has(key); }
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
    private void dispatchUiCallback(PyObject callback, Object... args) throws Exception {
        uiCallbacks.dispatch(scope, () -> call(callback, args), () -> {
            scope.invalidate(); manager.callbackFailed(scope, new IOException("PluginRestartRequired"));
        });
    }
    public synchronized void failed(Exception error) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - errorWindow >= 60000) { errorWindow = now; errors = 0; }
        manager.log(id(), "PluginCallbackFailed: " + android.util.Log.getStackTraceString(error));
        if (++errors >= 3) { scope.invalidate(); manager.callbackFailed(scope, error); }
    }
    public void bulletin(String text) {
        capability("ui");
        if (text.length() > 1000) throw new IllegalArgumentException("TextLimitExceeded");
        PluginHost.bulletin(this, text);
    }
    public AutoCloseable menu(String location, String localId, String title, PyObject callback) throws Exception {
        capability("ui"); return scope.own(PluginHost.menu(this, location, localId, title, callback));
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
            if (previous != null) for (String property : new String[]{"type", "scope", "max_length", "min", "max", "step", "options"})
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
                JsonElement callback = item.getAsJsonObject().get("callback");
                if (callback != null && callback.isJsonPrimitive()) releaseCallback(callback.getAsString());
            }
        }
    }
    public void callNamed(String key) {
        PyObject callback;
        synchronized (callbacks) { callback = callbacks.get(key); }
        if (callback == null) throw new IllegalArgumentException("UnknownCallback");
        call(callback);
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
                if (value.getAsString().equals("messages.observe") || value.getAsString().equals("messages.send")) return true;
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
