import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.telegram.messenger.plugins.PluginSettingsFile;

public final class PluginSettingsCheck {
    private static final String VALID = "{\"format\":1,\"plugin_id\":\"test.plugin\",\"scopes\":{\"schema.json\":{\"version\":1},\"global.json\":{\"text\":\"hello\"}}}";
    private static int rejected;
    private static void reject(byte[] bytes) throws Exception {
        try { PluginSettingsFile.parse(bytes, "test.plugin"); }
        catch (IOException expected) { rejected++; return; }
        throw new AssertionError("Accepted malformed settings");
    }
    private static void reject(String text) throws Exception { reject(text.getBytes(StandardCharsets.UTF_8)); }
    public static void main(String[] args) throws Exception {
        assert PluginSettingsFile.parse(VALID.getBytes(StandardCharsets.UTF_8), "test.plugin").getAsJsonObject("global.json").get("text").getAsString().equals("hello");
        reject(VALID.replace("test.plugin", "other.plugin"));
        reject(VALID.replace("\"format\":1", "\"format\":1,\"format\":1"));
        reject(VALID.replace("\"text\":\"hello\"", "\"text\":1,\"text\":2"));
        reject(VALID.replace("\"version\":1", "\"version\":0"));
        reject(VALID.replace("\"version\":1", "\"version\":1.5"));
        reject(VALID.replace("\"version\":1", "\"version\":2147483648"));
        reject(VALID.replace("global.json", "../global.json"));
        reject(VALID.replace("global.json", "registry.json"));
        reject(VALID.replace("\"format\":1", "\"format\":\"1\""));
        reject(VALID.replace("\"text\":\"hello\"", "\"text\":" + "[".repeat(20000) + "0" + "]".repeat(20000)));
        reject(VALID.replace("\"hello\"", "NaN"));
        reject(VALID + " {}");
        reject(new byte[]{(byte) 0xff});
        reject(new byte[PluginSettingsFile.MAX_BYTES + 1]);
        System.out.println("PASS: settings exchange round-trip, " + rejected + " rejected cases including duplicate keys, depth, size and cross-plugin import");
    }
}
