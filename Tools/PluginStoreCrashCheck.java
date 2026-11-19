package org.telegram.messenger.plugins;

import android.util.AtomicFile;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

/** Simulated process termination at every AtomicFile commit boundary; uses production store code. */
public final class PluginStoreCrashCheck {
    private static final String ID = "test.crash";
    private static final String MANIFEST;
    static {
        try { MANIFEST = new String(Files.readAllBytes(Paths.get("Tools/fixtures/plugins/hello-manifest.json")), StandardCharsets.UTF_8)
                .replace("dev.bygram.hello", ID); }
        catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }
    private static PluginPackage stage(PluginStore store, String version) throws Exception {
        return stage(store, version, MANIFEST, version);
    }
    private static PluginPackage stage(PluginStore store, String version, String manifest, String token) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(entry("manifest.json"));
            zip.write(manifest.replace("1.0.0", version).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(entry("main.py")); zip.write(("# " + version + "\n").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return PluginPackage.stage(new ByteArrayInputStream(bytes.toByteArray()), new File(store.staging, token), 1, 72379, 7237);
    }
    private static ZipEntry entry(String name) {
        ZipEntry entry = new ZipEntry(name);
        // Revocation matches the archive hash, including ZIP timestamps.
        entry.setTimeLocal(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        return entry;
    }
    private static JsonObject values(int value) { JsonObject object = new JsonObject(); object.addProperty("value", value); return object; }
    private static void writeValue(PluginStore store, int value) throws IOException {
        PluginStore.write(new AtomicFile(new File(store.data, ID + "/global.json")), values(value));
    }
    private static int value(PluginStore store) throws IOException {
        return store.settingsSnapshot(ID).getAsJsonObject("global.json").get("value").getAsInt();
    }
    private static void checkUpdateGuards() throws Exception {
        File root = Files.createTempDirectory("bygram-plugin-guards-").toFile();
        try {
            PluginStore store = new PluginStore(root);
            store.install(stage(store, "2.0.0"));
            writeValue(store, 7);
            try { store.install(stage(store, "1.0.0")); throw new AssertionError("downgrade accepted"); }
            catch (IOException expected) { assert expected.getMessage().equals("PluginVersionNotNewer"); }

            String oldHostOnly = MANIFEST.trim().replaceFirst("}$", ",\"bygram\":{\"min_build\":72379,\"max_build\":72379}}");
            PluginPackage next = stage(store, "10.0.0", oldHostOnly, "old-host");
            store.install(next);
            store = new PluginStore(root);
            store.applyPending(72380, 7237);
            JsonObject installed = store.plugins().getAsJsonObject(ID);
            assert installed.getAsJsonObject("manifest").get("version").getAsString().equals("2.0.0");
            assert !installed.has("pending") && installed.get("update_error").getAsString().startsWith("IncompatibleBuild");
            assert value(store) == 7;

            next = stage(store, "10.0.0", MANIFEST, "revoked");
            store.install(next);
            store.rememberRevoked(java.util.Collections.singleton(next.sha256));
            store = new PluginStore(root);
            store.rememberRevoked(java.util.Collections.emptySet());
            store.applyPending(72379, 7237);
            installed = store.plugins().getAsJsonObject(ID);
            assert installed.getAsJsonObject("manifest").get("version").getAsString().equals("2.0.0");
            assert !installed.has("pending") && installed.get("update_error").getAsString().equals("PluginRevoked");
            assert value(store) == 7;
            PluginPackage retry = stage(store, "10.0.0");
            assert retry.sha256.equals(next.sha256);
            try { store.install(retry); throw new AssertionError("revoked package reinstalled"); }
            catch (IOException expected) { assert expected.getMessage().equals("PluginRevoked"); }
        } finally { PluginPackage.delete(root); }
        System.out.println("PASS: numeric update order, host upgrade preserves current package, and revocations survive restart");
    }

    public static void main(String[] args) throws Exception {
        checkUpdateGuards();
        checkDeveloperReload();
        int deaths = 0;
        for (String operation : new String[]{"install", "pending", "cancel", "rollback", "restore", "remove"}) {
            for (boolean committed : new boolean[]{false, true}) {
                boolean finished = false;
                for (int boundary = 1; boundary <= 20; boundary++) {
                    File root = Files.createTempDirectory("bygram-plugin-crash-").toFile();
                    try {
                        PluginStore store = new PluginStore(root); store.install(stage(store, "1.0.0")); writeValue(store, 1);
                        PluginPackage next = stage(store, "2.0.0");
                        if (operation.equals("pending") || operation.equals("rollback") || operation.equals("cancel")) store.install(next);
                        if (operation.equals("rollback")) { store.applyPending(72379, 7237); writeValue(store, 2); }
                        JsonObject replacement = new JsonObject(); replacement.add("global.json", values(9));
                        replacement.add("account-0-123.json", values(9));
                        AtomicFile.dieAfterWrites = boundary; AtomicFile.dieAfterCommit = committed;
                        boolean died = false;
                        try {
                            switch (operation) {
                                case "install": store.install(next); break;
                                case "pending": store.applyPending(72379, 7237); break;
                                case "cancel": store.cancelPending(ID); break;
                                case "rollback": store.rollback(ID); break;
                                case "restore": store.restoreSettings(ID, replacement); break;
                                case "remove": store.remove(ID, false); break;
                                default: throw new AssertionError(operation);
                            }
                        } catch (AtomicFile.ProcessDeath expected) { died = true; deaths++; }
                        finally { AtomicFile.dieAfterWrites = -1; }
                        PluginStore restored = new PluginStore(root);
                        PluginStore.recoverMigration(new File(restored.data, ID));
                        restored.applyRollbacks();
                        if (operation.equals("install") || operation.equals("pending")) restored.applyPending(72379, 7237);
                        if (restored.plugins().has(ID)) {
                            JsonObject record = restored.plugins().getAsJsonObject(ID);
                            String version = record.getAsJsonObject("manifest").get("version").getAsString();
                            String code = new String(PluginPackage.read(new File(restored.packageDir(record), "main.py")), StandardCharsets.UTF_8);
                            assert code.contains(version) : "mixed package/registry: " + operation + " " + boundary;
                            assert !record.get("desired").getAsBoolean() : "crash enabled code";
                            if (operation.equals("rollback")) assert value(restored) == (version.equals("1.0.0") ? 1 : 2) : "mixed rollback settings";
                        } else assert operation.equals("remove");
                        if (operation.equals("restore")) {
                            JsonObject scopes = restored.settingsSnapshot(ID);
                            assert value(restored) == 1 || value(restored) == 9;
                            assert scopes.has("account-0-123.json") == (value(restored) == 9) : "mixed import scopes";
                        } else if (!operation.equals("rollback")) assert value(restored) == 1 : "unrelated data changed";
                        if (!died) { finished = true; break; }
                    } finally { AtomicFile.dieAfterWrites = -1; PluginPackage.delete(root); }
                }
                assert finished : "matrix bound too small: " + operation;
            }
        }
        System.out.println("PASS: " + deaths + " process-death boundaries before/after commit for install, pending, cancel, rollback, settings restore and remove");
    }

    private static void checkDeveloperReload() throws Exception {
        File root = Files.createTempDirectory("bygram-developer-reload-").toFile();
        try {
            PluginStore store = new PluginStore(root);
            PluginPackage initial = stage(store, "1.0.0");
            store.install(initial, true);
            assert PluginStore.isDeveloper(store.plugins().getAsJsonObject(ID));
            String updatedManifest = MANIFEST.replace("Привет BYgram", "Developer update");
            PluginPackage update = stage(store, "1.0.0", updatedManifest, "dev-update");
            assert !initial.sha256.equals(update.sha256);
            store.install(update, true);
            store.applyPending(72379, 7237, "missing.id");
            assert store.plugins().getAsJsonObject(ID).get("hash").getAsString().equals(initial.sha256);
            store.applyPending(72379, 7237, ID);
            JsonObject current = store.plugins().getAsJsonObject(ID);
            assert current.get("hash").getAsString().equals(update.sha256);
            assert !current.get("desired").getAsBoolean();

            String ordinary = MANIFEST.replace(ID, "test.ordinary");
            store.install(stage(store, "1.0.0", ordinary, "ordinary"));
            try {
                store.install(stage(store, "2.0.0", ordinary, "ordinary-dev"), true);
                throw new AssertionError("Developer deployment replaced an ordinary plugin");
            } catch (IOException expected) {
                assert expected.getMessage().equals("DeveloperPackageConflict");
            }
        } finally { PluginPackage.delete(root); }
        System.out.println("PASS: developer reload isolates package ownership and applies same-version changes");
    }
}
