package org.telegram.messenger.plugins;

import android.util.AtomicFile;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class PluginStoreCheck {
    private static final String ID = "test.plugin";
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void installed(PluginStore store, String hash) throws Exception {
        JsonObject record = json("{\"desired\":false,\"installed\":1}");
        record.add("manifest", JsonParser.parseString(new String(Files.readAllBytes(Paths.get("Tools/fixtures/plugins/hello-manifest.json")), StandardCharsets.UTF_8)));
        record.getAsJsonObject("manifest").addProperty("id", ID);
        record.addProperty("hash", hash); store.plugins().add(ID, record);
        Files.createDirectories(new File(store.packages, hash).toPath()); store.save();
    }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("bygram-store-check-").toFile();
        try {
            PluginStore store = new PluginStore(root);
            String first = new String(new char[64]).replace('\0', 'a'), second = first.replace('a', 'b');
            installed(store, first);
            store.state.addProperty("engine", true);
            store.plugins().getAsJsonObject(ID).addProperty("desired", true);
            store.plugins().getAsJsonObject(ID).addProperty("approved", first);
            store.plugins().getAsJsonObject(ID).add("accounts", JsonParser.parseString("[\"0:123:old-grant\"]")); store.save();
            installed(store, second);
            store.collectPackages();
            assert new File(store.packages, first).isDirectory() : "previous registry package was collected";
            Files.write(new File(root, "registry.json").toPath(), "{\"schema\":1,\"engine\":false,\"plugins\":[]}".getBytes(StandardCharsets.UTF_8));
            PluginStore recovered = new PluginStore(root);
            assert recovered.plugins().getAsJsonObject(ID).get("hash").getAsString().equals(first) : "structurally corrupt registry did not recover";
            assert !recovered.state.get("engine").getAsBoolean();
            assert !recovered.plugins().getAsJsonObject(ID).get("desired").getAsBoolean();
            assert !recovered.plugins().getAsJsonObject(ID).has("approved") && !recovered.plugins().getAsJsonObject(ID).has("accounts") : "recovery restored stale execution/account grants";
            JsonObject invalidMetadata = store.state.deepCopy();
            invalidMetadata.getAsJsonObject("plugins").getAsJsonObject(ID).getAsJsonObject("manifest").remove("name");
            Files.write(new File(root, "registry.json").toPath(), invalidMetadata.toString().getBytes(StandardCharsets.UTF_8));
            assert new PluginStore(root).state.get("recovered").getAsBoolean() : "malformed display metadata passed registry validation";
            File malformed = new File(root, "invalid.json");
            for (byte[] bytes : new byte[][] {"{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8), new byte[]{(byte) 0xff},
                    ("{\"a\":" + "[".repeat(20000) + "0" + "]".repeat(20000) + "}").getBytes(StandardCharsets.UTF_8)}) {
                Files.write(malformed.toPath(), bytes);
                try { PluginStore.read(new AtomicFile(malformed)); throw new AssertionError("malformed persisted JSON accepted"); }
                catch (IOException expected) { }
            }
            File data = new File(store.data, ID); Files.createDirectories(data.toPath());
            Files.write(new File(data, "global.json.bak").toPath(), "{\"value\":1}".getBytes(StandardCharsets.UTF_8));
            assert store.settingsSnapshot(ID).getAsJsonObject("global.json").get("value").getAsInt() == 1 : "backup-only settings lost";
            JsonObject replacement = json("{\"schema.json\":{\"version\":2},\"global.json\":{\"value\":2},\"account-0-123.json\":{\"value\":3}}");
            AtomicFile.failWriteName = "schema.json";
            try { store.restoreSettings(ID, replacement); throw new AssertionError("injected failure was swallowed"); }
            catch (IOException expected) { }
            assert store.settingsSnapshot(ID).size() == 1 : "failed restore left newly created scopes";
            assert store.settingsSnapshot(ID).getAsJsonObject("global.json").get("value").getAsInt() == 1;
            store.restoreSettings(ID, replacement);
            assert store.settingsSnapshot(ID).getAsJsonObject("schema.json").get("version").getAsInt() == 2;
            JsonObject before = json("{\"schema.json\":{\"version\":1},\"global.json\":{\"value\":1},\"account-0-123.json\":null}");
            PluginStore.write(new AtomicFile(new File(data, "migration-backup.json")), before);
            PluginStore.recoverMigration(data);
            assert store.settingsSnapshot(ID).size() == 2;
            assert store.settingsSnapshot(ID).getAsJsonObject("schema.json").get("version").getAsInt() == 1;
            Files.move(new File(data, "global.json").toPath(), new File(data, "global.json.bak").toPath());
            store.restoreSettings(ID, json("{\"schema.json\":{\"version\":1}}"));
            assert !new File(data, "global.json.bak").exists() : "removed scope can resurrect from backup";
            JsonObject previous = store.plugins().getAsJsonObject(ID).deepCopy();
            previous.addProperty("desired", true);
            JsonObject updated = previous.deepCopy(); updated.addProperty("hash", second);
            updated.add("rollback", previous); updated.addProperty("activated", true);
            store.plugins().add(ID, updated); store.save();
            JsonObject activation = new JsonObject(); activation.addProperty("plugin_id", ID); activation.addProperty("hash", second);
            store.recoverActivation(activation);
            assert store.plugins().getAsJsonObject(ID).has("rollback") : "successful activation rolled back";
            updated.remove("activated"); activation.addProperty("hash", first);
            store.recoverActivation(activation);
            assert store.plugins().getAsJsonObject(ID).has("rollback") : "stale marker rolled back unrelated package";
            activation.addProperty("hash", second);
            store.recoverActivation(activation);
            assert !store.plugins().getAsJsonObject(ID).has("rollback") : "interrupted first activation did not roll back";
            assert !store.plugins().getAsJsonObject(ID).get("desired").getAsBoolean() : "recovery auto-enabled previous version";
            JsonObject settingsBefore = store.settingsSnapshot(ID);
            JsonObject corruptJournal = json("{\"schema.json\":{\"version\":99},\"not-settings.json\":{}}");
            PluginStore.write(new AtomicFile(new File(data, "migration-backup.json")), corruptJournal);
            try { PluginStore.recoverMigration(data); throw new AssertionError("corrupt journal accepted"); }
            catch (IOException expected) { }
            assert store.settingsSnapshot(ID).equals(settingsBefore) : "corrupt journal partially overwrote settings";
            new AtomicFile(new File(data, "migration-backup.json")).delete();
            File orphan = new File(root, "snapshots/00000000-0000-0000-0000-000000000000.json.new");
            Files.createDirectories(orphan.getParentFile().toPath()); Files.write(orphan.toPath(), new byte[]{1});
            store.collectPackages(); assert !orphan.exists() : "uncommitted snapshot leaked";
            String currentHash = store.plugins().getAsJsonObject(ID).get("hash").getAsString();
            JsonObject pending = store.plugins().getAsJsonObject(ID).deepCopy(); pending.addProperty("hash", first);
            store.plugins().getAsJsonObject(ID).add("pending", pending); store.save();
            AtomicFile.failWriteName = "registry.json";
            try { store.cancelPending(ID); throw new AssertionError("cancel write failure swallowed"); }
            catch (IOException expected) { }
            assert store.plugins().getAsJsonObject(ID).has("pending") : "failed cancellation lost update";
            store.cancelPending(ID);
            assert !store.plugins().getAsJsonObject(ID).has("pending");
            assert store.plugins().getAsJsonObject(ID).get("hash").getAsString().equals(currentHash);
            assert store.settingsSnapshot(ID).equals(settingsBefore) : "cancellation changed settings";
            Files.write(new File(root, "registry.json").toPath(), "corrupt".getBytes(StandardCharsets.UTF_8));
            Files.write(new File(root, "registry.previous.json").toPath(), "corrupt".getBytes(StandardCharsets.UTF_8));
            try { new PluginStore(root); throw new AssertionError("two corrupt registries accepted"); }
            catch (IOException expected) { }
            PluginStore.resetRegistry(root);
            PluginStore reset = new PluginStore(root);
            assert reset.plugins().size() == 0 && !reset.state.get("engine").getAsBoolean();
            assert reset.settingsSnapshot(ID).equals(settingsBefore) : "registry reset destroyed settings";
            System.out.println("PASS: registry structure recovery, previous package retention, backup-only scopes, interrupted restore and migration recovery");
        } finally { PluginPackage.delete(root); }
    }
}
