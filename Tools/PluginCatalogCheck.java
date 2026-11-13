package org.telegram.messenger.plugins;

import java.nio.file.*;
import com.google.gson.*;
import org.telegram.messenger.plugins.PluginCatalog;
public final class PluginCatalogCheck {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(args[0]));
        JsonArray entries = PluginCatalog.parse(bytes, 72379, 7237);
        assert entries.size() == 6;
        checkExtera();
        assert PluginCatalog.repository("https://example.org/repository/index.json").getHost().equals("example.org");
        for (String url : new String[]{"http://example.org/index.json", "https://user@example.org/index.json",
                "https://example.org/index.json?token=one", "https://example.org/index.json#fragment", "https://example.org/repository"}) {
            try { PluginCatalog.repository(url); throw new AssertionError("Accepted " + url); }
            catch (java.io.IOException expected) { }
        }
        JsonObject dense = new JsonObject(); dense.addProperty("format", 1);
        JsonArray denseEntries = new JsonArray(); dense.add("plugins", denseEntries);
        for (int index = 0; index < 4; index++) {
            JsonObject item = entries.get(0).getAsJsonObject().deepCopy();
            JsonObject manifest = item.getAsJsonObject("manifest"); manifest.addProperty("id", "test.dense.plugin" + index);
            manifest.add("capabilities", JsonParser.parseString("[\"hooks.raw\"]")); manifest.addProperty("requires_hook_backend", true);
            manifest.add("bygram", JsonParser.parseString("{\"min_build\":1,\"max_build\":2147483647}"));
            manifest.add("telegram", JsonParser.parseString("{\"min_build\":1,\"max_build\":2147483647}"));
            JsonArray targets = new JsonArray(); manifest.add("hook_targets", targets);
            for (int target = 0; target < 1000; target++) targets.add(JsonParser.parseString("{\"class\":\"x\",\"method\":\"m\",\"args\":[]}"));
            denseEntries.add(item);
        }
        assert PluginCatalog.parse(dense.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), 72379, 7237).size() == 4 : "valid dense index rejected";
        JsonObject invalidManifestIndex = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        invalidManifestIndex.getAsJsonArray("plugins").get(0).getAsJsonObject().getAsJsonObject("manifest").addProperty("entrypoint", "main.py:class");
        assert PluginCatalog.parse(invalidManifestIndex.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), 72379, 7237).size() == 5 : "bad manifest hid healthy plugins";
        PluginCatalog.Request cancelled = new PluginCatalog.Request(); cancelled.close(); cancelled.close();
        try { cancelled.check(); throw new AssertionError("cancel not honored"); }
        catch (java.io.IOException expected) { assert expected.getMessage().equals("CatalogCancelled"); }
        JsonObject provenanceIndex = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject provenance = new JsonObject(); provenance.addProperty("repository", "https://github.com/bygram/example");
        provenance.addProperty("repository_id", 1); provenance.addProperty("release_id", 2); provenance.addProperty("asset_id", 3);
        provenance.addProperty("tag", "v" + entries.get(0).getAsJsonObject().getAsJsonObject("manifest").get("version").getAsString());
        provenanceIndex.getAsJsonArray("plugins").get(0).getAsJsonObject().add("release", provenance);
        assert PluginCatalog.parse(provenanceIndex.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), 72379, 7237).size() == 6;
        provenance.addProperty("tag", "v999.0.0");
        try { PluginCatalog.parse(provenanceIndex.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), 72379, 7237); throw new AssertionError("release version mismatch accepted"); }
        catch (java.io.IOException expected) { }
        java.io.File temporary = Files.createTempDirectory("catalog-install-").toFile();
        try {
            int index = 0;
            for (JsonElement value : entries) {
                JsonObject entry = value.getAsJsonObject();
                byte[] data = Files.readAllBytes(Paths.get(args[0]).getParent().resolve(entry.get("file").getAsString()));
                PluginPackage pkg = PluginCatalog.stage(entry, data, new java.io.File(temporary, "valid" + index++), 72379, 7237, true);
                assert pkg.source.equals("catalog");
                if (index == 1) {
                    JsonObject externalEntry = entry.deepCopy();
                    externalEntry.add("release", provenance);
                    PluginPackage external = PluginCatalog.stage(externalEntry, data, new java.io.File(temporary, "external"),
                            72379, 7237, false);
                    assert external.source.equals("local_file") && external.catalogRelease == null;
                }
                PluginStore store = new PluginStore(new java.io.File(temporary, "store" + index)); store.install(pkg);
                assert !store.plugins().getAsJsonObject(pkg.manifest.id()).get("desired").getAsBoolean();
                assert new PluginStore(store.root).plugins().getAsJsonObject(pkg.manifest.id()).get("source").getAsString().equals("catalog");
                for (String field : new String[]{"sha256", "size", "manifest"}) {
                    JsonObject changed = entry.deepCopy();
                    if (field.equals("sha256")) changed.addProperty(field, "0".repeat(64));
                    else if (field.equals("size")) changed.addProperty(field, data.length + 1);
                    else changed.getAsJsonObject("manifest").addProperty("name", "Changed");
                    try { PluginCatalog.stage(changed, data, new java.io.File(temporary, "bad" + index++), 72379, 7237, true); throw new AssertionError(field); }
                    catch (java.io.IOException expected) { }
                }
            }
        } finally { PluginPackage.delete(temporary); }
        for (String field : new String[]{"file", "sha256", "size"}) {
            JsonObject root = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject entry = root.getAsJsonArray("plugins").get(0).getAsJsonObject();
            if (field.equals("size")) entry.addProperty(field, 999999999);
            else entry.addProperty(field, "../escape");
            try { PluginCatalog.parse(root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), 72379, 7237); throw new AssertionError(field); }
            catch (java.io.IOException expected) { }
        }
        for (String json : new String[]{"{\"format\":1,\"format\":1,\"plugins\":[]}", "{\"format\":1.5,\"plugins\":[]}", "{\"format\":1,\"plugins\":[],\"unknown\":true}", "[".repeat(20000)}) {
            try { PluginCatalog.parse(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), 72379, 7237); throw new AssertionError("invalid JSON accepted"); }
            catch (java.io.IOException expected) { }
        }
        System.out.println("PASS: catalog manifests, immutable paths, hash and size validation");
    }

    private static void checkExtera() throws Exception {
        String hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        String json = "{\"schema\":1,\"name\":\"Example\",\"plugins\":[{\"id\":\"example\",\"name\":\"Example\","
                + "\"version\":\"1.2\",\"file\":\"packages/example.elyx\",\"sha256\":\"" + hash + "\",\"size\":3}]}";
        String url = "https://example.org/plugins/extera-plugins.json";
        JsonArray parsed = PluginCatalog.parseRepository(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), url, 72379, 7237);
        JsonObject entry = parsed.get(0).getAsJsonObject();
        assert entry.get("file").getAsString().equals("https://example.org/plugins/packages/example.elyx");
        assert entry.get("archive").getAsBoolean();
        PluginCatalog.verifyExtera(entry, new byte[]{'a', 'b', 'c'});
        try {
            PluginCatalog.verifyExtera(entry, new byte[]{'x', 'y', 'z'});
            throw new AssertionError("External archive mismatch accepted");
        } catch (java.io.IOException expected) { }
        try {
            PluginCatalog.parseRepository(json.replace("packages/example.elyx", "http://example.org/source.elyx")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8), url, 72379, 7237);
            throw new AssertionError("HTTP external package accepted");
        } catch (java.io.IOException expected) { }
        assert "SomeChannel".equals(PluginCatalog.channelName("https://t.me/SomeChannel"));
        assert "SomeChannel".equals(PluginCatalog.channelName("https://t.me/exteraPluginRepo?c=SomeChannel"));
        assert PluginCatalog.channelName("https://t.me/SomeChannel/1") == null;
        assert PluginCatalog.channelName("https://t.me/+private") == null;
        assert PluginCatalog.repository("https://github.com/example/plugins").toString().equals(
                "https://raw.githubusercontent.com/example/plugins/HEAD/extera-plugins.json");
        assert PluginCatalog.repository("https://github.com/example/plugins/tree/v1/dir").toString().equals(
                "https://raw.githubusercontent.com/example/plugins/v1/dir/extera-plugins.json");
        assert PluginCatalog.sourceAddress("https://t.me/exteraPluginRepo?git=https%3A%2F%2Fgithub.com%2Fexample%2Fplugins")
                .equals("https://raw.githubusercontent.com/example/plugins/HEAD/extera-plugins.json");
        System.out.println("PASS: native extera index, package integrity and Telegram channel URLs");
    }
}
