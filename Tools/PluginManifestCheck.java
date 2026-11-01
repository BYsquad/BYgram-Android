import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.telegram.messenger.plugins.PluginManifest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PluginManifestCheck {
    private static int checks;

    private static PluginManifest parse(String json) {
        return PluginManifest.parse(json.getBytes(StandardCharsets.UTF_8), 1, 72379, 7237);
    }

    private static void reject(String json, String code) {
        try {
            parse(json);
            throw new AssertionError("Accepted invalid manifest");
        } catch (PluginManifest.ValidationException e) {
            if (!e.code.equals(code)) throw new AssertionError(e.code + " != " + code);
            checks++;
        }
    }

    public static void main(String[] args) throws Exception {
        JsonObject base = JsonParser.parseString(new String(Files.readAllBytes(
                Paths.get("Tools/fixtures/plugins/hello-manifest.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        PluginManifest valid = parse(base.toString());
        assert valid.id().equals("dev.bygram.hello");
        assert valid.version().equals("1.0.0");
        assert valid.entrypoint().equals("main.py:HelloPlugin");
        valid.metadata().addProperty("id", "mutated");
        assert valid.id().equals("dev.bygram.hello");
        for (String field : base.keySet()) {
            JsonObject copy = base.deepCopy();
            copy.remove(field);
            reject(copy.toString(), "InvalidManifest");
            copy = base.deepCopy();
            copy.add(field, new JsonObject());
            reject(copy.toString(), "InvalidManifest");
        }
        String[][] invalid = {
            {"id", "Dev.hello"}, {"id", "single"}, {"id", "a..b"}, {"id", "a.1b"},
            {"id", "../hello"}, {"name", " "}, {"name", "line\nfeed"}, {"name", "\u0000"},
            {"version", "01.0.0"}, {"version", "1.0"}, {"version", "1.0.0-beta"},
            {"entrypoint", "../main.py:Hello"}, {"entrypoint", "/main.py:Hello"},
            {"entrypoint", "C:/main.py:Hello"}, {"entrypoint", "x\\main.py:Hello"},
            {"entrypoint", "x//main.py:Hello"}, {"entrypoint", "./main.py:Hello"},
            {"entrypoint", "main.py:class"}, {"entrypoint", "main.py:Hello()"},
            {"entrypoint", "main.pyc:Hello"}, {"entrypoint", "main.py:Hello:Other"},
            {"account_scope", "all"}, {"icon", "../icon.png"}, {"icon", "https://host/a.png"},
            {"icon", "a.svg"}
        };
        for (String[] item : invalid) {
            JsonObject copy = base.deepCopy();
            copy.addProperty(item[0], item[1]);
            reject(copy.toString(), "InvalidManifest");
        }
        for (String number : new String[]{"0", "-1", "1.1", "2147483648", "true", "\"1\"", "null"}) {
            reject(base.toString().replace("\"settings_schema\":1", "\"settings_schema\":" + number), "InvalidManifest");
        }
        reject(base.toString().replace("\"manifest_version\":1", "\"manifest_version\":2"), "InvalidManifest");
        reject(base.toString().replace("\"ui\"", "\"unknown\""), "InvalidManifest");
        reject(base.toString().replace("[\"ui\"]", "[\"ui\",\"ui\"]"), "InvalidManifest");
        reject(base.toString().replace("\"3.11\"", "\"3.12\""), "IncompatiblePython");
        reject(base.toString().replace("\"min\":1", "\"min\":2").replace("\"max\":1", "\"max\":2"), "IncompatibleSdk");
        reject(base.toString().replace("\"min\":1", "\"min\":2"), "InvalidManifest");
        reject(base.toString().replace("\"sdk\":{", "\"sdk\":{\"typo\":1,"), "InvalidManifest");
        reject(base.toString().replace("{", "{\"typo\":1,"), "InvalidManifest");
        reject(base.toString().replace("{", "{\"id\":\"a.b\","), "InvalidManifest");
        reject(base.toString().replace("{", "{/*comment*/"), "InvalidManifest");
        reject(base.toString() + "{}", "InvalidManifest");
        reject(base.toString().replace("1.0.0", "1".repeat(81) + ".0.0"), "InvalidManifest");
        reject(base.toString().replace("Привет BYgram", "\\ud800"), "InvalidManifest");
        reject("[]", "InvalidManifest");
        reject("", "InvalidManifest");
        reject("{\"deep\":" + "[".repeat(10) + "1" + "]".repeat(10) + "}", "PackageLimitExceeded");
        reject(" ".repeat(PluginManifest.MAX_BYTES + 1), "PackageLimitExceeded");
        try {
            PluginManifest.parse(new byte[]{(byte) 0xc0, (byte) 0xaf}, 1, 1, 1);
            throw new AssertionError("Accepted malformed UTF-8");
        } catch (PluginManifest.ValidationException e) { assert e.field.equals("utf8"); checks++; }

        JsonObject raw = base.deepCopy();
        raw.getAsJsonArray("capabilities").add("hooks.raw");
        reject(raw.toString(), "InvalidManifest");
        raw.addProperty("requires_hook_backend", true);
        JsonObject builds = new JsonObject();
        builds.addProperty("min_build", 1);
        builds.addProperty("max_build", 100000);
        raw.add("bygram", builds.deepCopy());
        raw.add("telegram", builds.deepCopy());
        JsonObject target = new JsonObject();
        target.addProperty("class", "org.telegram.HookProbe");
        target.addProperty("method", "label");
        JsonArray arguments = new JsonArray();
        arguments.add("java.lang.String[]");
        arguments.add("int");
        target.add("args", arguments);
        JsonArray targets = new JsonArray();
        targets.add(target);
        raw.add("hook_targets", targets);
        parse(raw.toString());
        target.remove("method");
        target.addProperty("constructor", true);
        parse(raw.toString());
        target.addProperty("method", "label");
        reject(raw.toString(), "InvalidManifest");
        target.remove("constructor");
        arguments.add("void");
        reject(raw.toString(), "InvalidManifest");
        arguments.remove(arguments.size() - 1);
        arguments.add("a.".repeat(10000) + "B");
        reject(raw.toString(), "InvalidManifest");
        arguments.remove(arguments.size() - 1);
        raw.getAsJsonObject("bygram").addProperty("max_build", 2);
        reject(raw.toString(), "IncompatibleBuild");
        System.out.println("PASS: manifest validation, " + checks + " rejected cases; metadata and hook declarations");
    }
}
