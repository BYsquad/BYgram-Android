package org.telegram.messenger.plugins;

import android.util.AtomicFile;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** All access is serialized on PluginManager's queue. Packages never live in backup storage. */
final class PluginStore {
    final File root, packages, staging, data;
    private final AtomicFile registry;
    private final AtomicFile registryBackup;
    private JsonObject saved;
    JsonObject state;

    PluginStore(File root) throws Exception {
        this.root = root;
        packages = new File(root, "packages"); staging = new File(root, "staging"); data = new File(root, "data");
        PluginPackage.check(packages.mkdirs() || packages.isDirectory(), "StorageUnavailable");
        PluginPackage.check(staging.mkdirs() || staging.isDirectory(), "StorageUnavailable");
        PluginPackage.check(data.mkdirs() || data.isDirectory(), "StorageUnavailable");
        registry = new AtomicFile(new File(root, "registry.json"));
        registryBackup = new AtomicFile(new File(root, "registry.previous.json"));
        boolean recovered = false;
        try {
            state = readRegistry(registry);
            if (state.size() == 0) { state = readRegistry(registryBackup); recovered = state.size() > 0; }
        }
        catch (IOException corrupt) {
            state = readRegistry(registryBackup);
            PluginPackage.check(state.size() > 0, "RegistryCorrupt");
            recovered = true;
        }
        if (recovered) {
            state.addProperty("engine", false); state.addProperty("recovered", true);
            for (JsonElement record : state.getAsJsonObject("plugins").asMap().values()) revokeApproval(record.getAsJsonObject());
            write(registry, state);
        }
        saved = state.size() == 0 ? null : state.deepCopy();
        if (state.size() == 0) {
            state.addProperty("schema", 1); state.addProperty("engine", false); state.add("plugins", new JsonObject());
            save();
        }
        // Staging is never referenced by a committed registry entry.
        File[] abandoned = staging.listFiles();
        if (abandoned != null) for (File file : abandoned) PluginPackage.delete(file);
    }

    JsonObject plugins() { return state.getAsJsonObject("plugins"); }
    static void resetRegistry(File root) throws IOException {
        JsonObject clean = new JsonObject(); clean.addProperty("schema", 1);
        clean.addProperty("engine", false); clean.add("plugins", new JsonObject());
        write(new AtomicFile(new File(root, "registry.previous.json")), clean);
        write(new AtomicFile(new File(root, "registry.json")), clean);
    }
    private static void revokeApproval(JsonObject record) {
        record.addProperty("desired", false); record.remove("approved"); record.remove("accounts");
        for (String key : new String[]{"pending", "rollback", "rollback_pending"})
            if (record.has(key)) revokeApproval(record.getAsJsonObject(key));
    }
    private static JsonObject readRegistry(AtomicFile file) throws IOException {
        JsonObject value = read(file);
        if (value.size() == 0 && !file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return value;
        try {
            PluginPackage.check(value.has("schema") && value.get("schema").isJsonPrimitive() && value.getAsJsonPrimitive("schema").isNumber()
                    && value.get("schema").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0
                    && value.has("engine") && value.get("engine").isJsonPrimitive() && value.getAsJsonPrimitive("engine").isBoolean()
                    && value.has("plugins") && value.get("plugins").isJsonObject(), "RegistryCorrupt");
            for (String id : value.getAsJsonObject("plugins").keySet()) {
                PluginPackage.check(id.length() <= 120 && id.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"), "RegistryCorrupt");
                validateRecord(id, value.getAsJsonObject("plugins").getAsJsonObject(id), 0);
            }
            return value;
        } catch (RuntimeException error) { throw new IOException("RegistryCorrupt", error); }
    }
    private static void validateRecord(String id, JsonObject record, int depth) throws IOException {
        if (record.has("author_key")) PluginPackage.check(record.get("author_key").isJsonPrimitive()
                && record.getAsJsonPrimitive("author_key").isString() && record.get("author_key").getAsString().matches("[0-9a-f]{64}"), "RegistryCorrupt");
        PluginPackage.check(depth <= 3 && record.get("hash").getAsString().matches("[a-f0-9]{64}")
                && record.get("desired").isJsonPrimitive() && record.getAsJsonPrimitive("desired").isBoolean()
                && record.getAsJsonObject("manifest").get("id").getAsString().equals(id), "RegistryCorrupt");
        JsonObject metadata = record.getAsJsonObject("manifest");
        PluginManifest.parse(metadata.toString().getBytes(StandardCharsets.UTF_8), metadata.getAsJsonObject("sdk").get("min").getAsInt(),
                metadata.has("bygram") ? metadata.getAsJsonObject("bygram").get("min_build").getAsInt() : 1,
                metadata.has("telegram") ? metadata.getAsJsonObject("telegram").get("min_build").getAsInt() : 1);
        PluginPackage.check(record.get("installed").isJsonPrimitive() && record.getAsJsonPrimitive("installed").isNumber()
                && record.get("installed").getAsBigDecimal().longValueExact() >= 0, "RegistryCorrupt");
        for (String key : new String[]{"pending", "rollback", "rollback_pending"})
            if (record.has(key)) validateRecord(id, record.getAsJsonObject(key), depth + 1);
    }
    void save() throws IOException {
        try {
            if (saved != null) write(registryBackup, saved);
            write(registry, state); saved = state.deepCopy();
        } catch (IOException error) { if (saved != null) state = saved.deepCopy(); throw error; }
    }

    File packageDir(JsonObject record) throws IOException {
        String hash = record.get("hash").getAsString();
        PluginPackage.check(hash.matches("[a-f0-9]{64}"), "RegistryCorrupt");
        File dir = new File(packages, hash);
        PluginPackage.check(dir.isDirectory(), "PackageMissing");
        return dir;
    }

    void install(PluginPackage pkg) throws Exception {
        JsonObject oldState = state.deepCopy();
        JsonObject existing = plugins().getAsJsonObject(pkg.manifest.id());
        if (existing != null && existing.has("author_key"))
            PluginPackage.check(existing.get("author_key").getAsString().equals(pkg.signer), "AuthorKeyChanged");
        if (existing != null && (pkg.sha256.equals(existing.get("hash").getAsString())
                || existing.has("pending") && pkg.sha256.equals(existing.getAsJsonObject("pending").get("hash").getAsString()))) {
            throw new IOException("AlreadyInstalled");
        }
        File destination = new File(packages, pkg.sha256);
        if (!destination.exists()) PluginPackage.check(pkg.directory.renameTo(destination), "InstallCommitFailed");
        JsonObject record = new JsonObject();
        record.add("manifest", pkg.manifest.metadata()); record.addProperty("hash", pkg.sha256);
        record.addProperty("desired", false); record.addProperty("installed", System.currentTimeMillis());
        record.addProperty("source", pkg.source);
        if (pkg.signer != null) record.addProperty("author_key", pkg.signer);
        if (pkg.catalogRelease != null) record.add("catalog_release", pkg.catalogRelease.deepCopy());
        if (existing == null) plugins().add(pkg.manifest.id(), record);
        else existing.add("pending", record);
        try { save(); } catch (Exception e) { state = oldState; throw e; }
    }

    void applyPending() throws Exception {
        JsonObject old = state.deepCopy();
        try {
        for (String id : new java.util.ArrayList<>(plugins().keySet())) {
            JsonObject record = plugins().getAsJsonObject(id);
            if (!record.has("pending")) continue;
            JsonObject next = record.getAsJsonObject("pending").deepCopy();
            JsonObject previous = record.deepCopy();
            previous.remove("pending"); previous.remove("rollback");
            String snapshot = java.util.UUID.randomUUID().toString();
            write(new AtomicFile(snapshotFile(snapshot)), settingsSnapshot(id), PluginPackage.MAX_TOTAL);
            previous.addProperty("settings_snapshot", snapshot);
            next.add("rollback", previous);
            // Updating never transfers approval to a different executable hash.
            next.addProperty("desired", false);
            plugins().add(id, next);
        }
        save();
        } catch (Exception e) { state = old; throw e; }
    }
    void cancelPending(String id) throws IOException {
        JsonObject record = plugins().getAsJsonObject(id);
        PluginPackage.check(record != null && record.has("pending"), "PendingUpdateMissing");
        record.remove("pending"); save(); collectPackages();
    }

    void rollback(String id) throws Exception {
        JsonObject old = state.deepCopy();
        JsonObject record = plugins().getAsJsonObject(id);
        PluginPackage.check(record.has("rollback"), "RollbackUnavailable");
        JsonObject previous = record.getAsJsonObject("rollback").deepCopy();
        if (previous.has("settings_snapshot")) readSnapshot(previous.get("settings_snapshot"));
        previous.addProperty("desired", false);
        record.add("rollback_pending", previous);
        try { save(); } catch (Exception e) { state = old; throw e; }
        applyRollbacks();
    }

    void recoverActivation(JsonObject activation) throws Exception {
        if (!activation.has("plugin_id") || !activation.has("hash")) return;
        JsonObject record = plugins().getAsJsonObject(activation.get("plugin_id").getAsString());
        if (record != null && record.has("rollback") && !record.has("activated")
                && record.get("hash").equals(activation.get("hash")))
            rollback(activation.get("plugin_id").getAsString());
    }

    void applyRollbacks() throws Exception {
        for (String id : new java.util.ArrayList<>(plugins().keySet())) {
            JsonObject record = plugins().getAsJsonObject(id);
            if (!record.has("rollback_pending")) continue;
            JsonObject previous = record.getAsJsonObject("rollback_pending").deepCopy();
            if (previous.has("settings_snapshot")) {
                restoreSettings(id, readSnapshot(previous.get("settings_snapshot")));
                previous.remove("settings_snapshot");
            }
            plugins().add(id, previous); save();
        }
    }
    static boolean settingFile(String name) {
        return PluginSettingsFile.settingFile(name);
    }
    JsonObject settingsSnapshot(String id) throws IOException {
        JsonObject snapshot = new JsonObject();
        File directory = new File(data, id);
        for (String name : settingNames(directory)) snapshot.add(name, read(new AtomicFile(new File(directory, name))));
        return snapshot;
    }
    static java.util.Set<String> settingNames(File directory) {
        java.util.Set<String> names = new java.util.HashSet<>();
        String[] files = directory.list();
        if (files != null) for (String name : files) {
            if (name.endsWith(".bak")) name = name.substring(0, name.length() - 4);
            if (settingFile(name)) names.add(name);
        }
        return names;
    }
    private File snapshotFile(String reference) throws IOException {
        PluginPackage.check(reference.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"), "RegistryCorrupt");
        return new File(new File(root, "snapshots"), reference + ".json");
    }
    private JsonObject readSnapshot(JsonElement reference) throws IOException {
        return reference.isJsonObject() ? reference.getAsJsonObject() : read(new AtomicFile(snapshotFile(reference.getAsString())), PluginPackage.MAX_TOTAL);
    }
    void restoreSettings(String id, JsonObject replacement) throws IOException {
        File directory = new File(data, id);
        PluginPackage.check(directory.mkdirs() || directory.isDirectory(), "StorageUnavailable");
        JsonObject before = settingsSnapshot(id);
        for (String name : replacement.keySet()) {
            PluginPackage.check(settingFile(name), "InvalidPluginSettings");
            if (!before.has(name)) before.add(name, JsonNull.INSTANCE);
        }
        AtomicFile journal = new AtomicFile(new File(directory, "migration-backup.json"));
        write(journal, before, PluginPackage.MAX_TOTAL);
        try {
            for (String name : settingNames(directory)) if (!replacement.has(name)) new AtomicFile(new File(directory, name)).delete();
            for (String name : replacement.keySet()) write(new AtomicFile(new File(directory, name)), replacement.getAsJsonObject(name));
            journal.delete();
        } catch (Exception error) { recoverMigration(directory); throw new IOException("SettingsRestoreFailed", error); }
    }

    static void recoverMigration(File directory) throws IOException {
        AtomicFile journal = new AtomicFile(new File(directory, "migration-backup.json"));
        if (!journal.getBaseFile().exists() && !new File(journal.getBaseFile() + ".bak").exists()) return;
        JsonObject values = read(journal, PluginPackage.MAX_TOTAL);
        for (String name : values.keySet()) {
            PluginPackage.check(settingFile(name), "SettingsMigrationFailed");
            PluginPackage.check(values.get(name).isJsonObject() || values.get(name).isJsonNull(), "SettingsMigrationFailed");
        }
        for (String name : values.keySet()) {
            AtomicFile target = new AtomicFile(new File(directory, name));
            if (values.get(name).isJsonNull()) target.delete();
            else write(target, values.getAsJsonObject(name));
        }
        journal.delete();
    }
    void remove(String id, boolean removeData) throws Exception {
        JsonObject old = state.deepCopy();
        plugins().remove(id);
        try { save(); } catch (Exception e) { state = old; throw e; }
        if (removeData) PluginPackage.delete(new File(data, id));
        collectPackages();
    }
    void collectPackages() throws IOException {
        java.util.Set<String> retained = new java.util.HashSet<>();
        collectReferences(plugins(), retained, "hash");
        JsonObject backup;
        try { backup = readRegistry(registryBackup); }
        catch (IOException corrupt) { return; }
        if (backup.has("plugins")) collectReferences(backup.getAsJsonObject("plugins"), retained, "hash");
        File[] directories = packages.listFiles();
        if (directories != null) for (File directory : directories)
            if (directory.getName().matches("[a-f0-9]{64}") && !retained.contains(directory.getName())) PluginPackage.delete(directory);
        java.util.Set<String> snapshots = new java.util.HashSet<>();
        collectReferences(plugins(), snapshots, "settings_snapshot");
        if (backup.has("plugins")) collectReferences(backup.getAsJsonObject("plugins"), snapshots, "settings_snapshot");
        File[] files = new File(root, "snapshots").listFiles();
        if (files != null) for (File file : files) {
            String name = file.getName();
            if (name.matches("[a-f0-9-]{36}\\.json(\\.(new|bak))?") && !snapshots.contains(name.substring(0, 36)))
                new AtomicFile(new File(file.getParentFile(), name.substring(0, 36) + ".json")).delete();
        }
    }
    private static void collectReferences(JsonObject object, java.util.Set<String> retained, String property) {
        java.util.ArrayDeque<JsonObject> records = new java.util.ArrayDeque<>();
        for (JsonElement value : object.asMap().values()) records.add(value.getAsJsonObject());
        while (!records.isEmpty()) {
            JsonObject record = records.removeFirst();
            if (record.has(property) && record.get(property).isJsonPrimitive()) retained.add(record.get(property).getAsString());
            for (String key : new String[]{"pending", "rollback", "rollback_pending"}) if (record.has(key)) records.add(record.getAsJsonObject(key));
        }
    }

    static JsonObject read(AtomicFile file) throws IOException {
        return read(file, PluginPackage.MAX_FILE);
    }
    static JsonObject read(AtomicFile file, int limit) throws IOException {
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return new JsonObject();
        try (InputStream in = file.openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) {
                PluginPackage.check(out.size() + count <= limit, "StorageLimitExceeded");
                out.write(buffer, 0, count);
            }
            return PluginSettingsFile.parseJson(out.toByteArray(), limit, 16, limit);
        } catch (RuntimeException e) { throw new IOException("RegistryCorrupt", e); }
    }

    static void write(AtomicFile file, JsonObject value) throws IOException {
        write(file, value, 1024 * 1024);
    }
    static void write(AtomicFile file, JsonObject value, int limit) throws IOException {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        PluginPackage.check(bytes.length <= limit, "StorageLimitExceeded");
        FileOutputStream out = null;
        try {
            out = file.startWrite(); out.write(bytes); file.finishWrite(out);
        } catch (IOException e) { if (out != null) file.failWrite(out); throw e; }
    }
}
