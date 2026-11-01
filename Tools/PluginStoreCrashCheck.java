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
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(MANIFEST.replace("1.0.0", version).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("main.py")); zip.write(("# " + version + "\n").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return PluginPackage.stage(new ByteArrayInputStream(bytes.toByteArray()), new File(store.staging, version), 1, 72379, 7237);
    }
    private static JsonObject values(int value) { JsonObject object = new JsonObject(); object.addProperty("value", value); return object; }
    private static void writeValue(PluginStore store, int value) throws IOException {
        PluginStore.write(new AtomicFile(new File(store.data, ID + "/global.json")), values(value));
    }
    private static int value(PluginStore store) throws IOException {
        return store.settingsSnapshot(ID).getAsJsonObject("global.json").get("value").getAsInt();
    }
    public static void main(String[] args) throws Exception {
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
                        if (operation.equals("rollback")) { store.applyPending(); writeValue(store, 2); }
                        JsonObject replacement = new JsonObject(); replacement.add("global.json", values(9));
                        replacement.add("account-0-123.json", values(9));
                        AtomicFile.dieAfterWrites = boundary; AtomicFile.dieAfterCommit = committed;
                        boolean died = false;
                        try {
                            switch (operation) {
                                case "install": store.install(next); break;
                                case "pending": store.applyPending(); break;
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
                        if (operation.equals("install") || operation.equals("pending")) restored.applyPending();
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
}
