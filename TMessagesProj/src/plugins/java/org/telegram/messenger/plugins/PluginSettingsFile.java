package org.telegram.messenger.plugins;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Data-only settings exchange. No packages, execution grants or account grants. */
public final class PluginSettingsFile {
    public static final int MAX_BYTES = 8 * 1024 * 1024;

    public static JsonObject parse(byte[] bytes, String expectedId) throws IOException {
        try {
            JsonObject root = parseJson(bytes, MAX_BYTES, 12, 100000);
            PluginPackage.check(root != null && root.size() == 3 && root.has("format") && root.has("plugin_id") && root.has("scopes")
                    && root.get("format").isJsonPrimitive() && root.getAsJsonPrimitive("format").isNumber()
                    && root.get("format").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0
                    && root.get("plugin_id").isJsonPrimitive() && root.getAsJsonPrimitive("plugin_id").isString()
                    && root.get("plugin_id").getAsString().equals(expectedId) && root.get("scopes").isJsonObject(), "InvalidPluginSettings");
            JsonObject scopes = root.getAsJsonObject("scopes");
            PluginPackage.check(scopes.size() <= 128 && scopes.has("schema.json"), "InvalidPluginSettings");
            for (String name : scopes.keySet()) {
                PluginPackage.check(settingFile(name) && scopes.get(name).isJsonObject(), "InvalidPluginSettings");
                validateValue(scopes.get(name), 0);
                PluginPackage.check(scopes.get(name).toString().getBytes(StandardCharsets.UTF_8).length <= 1024 * 1024, "SettingsLimitExceeded");
            }
            JsonElement version = scopes.getAsJsonObject("schema.json").get("version");
            PluginPackage.check(version != null && version.isJsonPrimitive() && version.getAsJsonPrimitive().isNumber()
                    && version.getAsBigDecimal().intValueExact() >= 1, "InvalidPluginSettings");
            return scopes;
        } catch (RuntimeException e) { throw new IOException("InvalidPluginSettings", e); }
    }

    static JsonObject parseJson(byte[] bytes, int maxBytes, int maxDepth, int maxNodes) throws IOException {
        PluginPackage.check(bytes.length <= maxBytes, "SettingsLimitExceeded");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            // Bound depth and node count before building a Gson tree.
            try (JsonReader reader = new JsonReader(new StringReader(text))) {
                reader.setStrictness(Strictness.STRICT);
                ArrayDeque<Set<String>> levels = new ArrayDeque<>(); int nodes = 0;
                while (reader.peek() != JsonToken.END_DOCUMENT) {
                    PluginPackage.check(++nodes <= maxNodes, "SettingsLimitExceeded");
                    switch (reader.peek()) {
                        case BEGIN_OBJECT: reader.beginObject(); levels.push(new HashSet<>()); break;
                        case BEGIN_ARRAY: reader.beginArray(); levels.push(new HashSet<>()); break;
                        case END_OBJECT: reader.endObject(); levels.pop(); break;
                        case END_ARRAY: reader.endArray(); levels.pop(); break;
                        case NAME:
                            String key = reader.nextName();
                            PluginPackage.check(!levels.isEmpty() && levels.peek().add(key), "InvalidPluginSettings"); break;
                        case STRING: PluginPackage.check(reader.nextString().length() <= 1024 * 1024, "SettingsLimitExceeded"); break;
                        case NUMBER: reader.nextString(); break;
                        case BOOLEAN: reader.nextBoolean(); break;
                        case NULL: reader.nextNull(); break;
                        default: throw new IOException("InvalidPluginSettings");
                    }
                    PluginPackage.check(levels.size() <= maxDepth, "SettingsLimitExceeded");
                }
                PluginPackage.check(levels.isEmpty(), "InvalidPluginSettings");
            }
            JsonObject root = new GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(text, JsonObject.class);
            PluginPackage.check(root != null, "InvalidPluginSettings");
            return root;
        } catch (RuntimeException e) { throw new IOException("InvalidPluginSettings", e); }
    }

    static boolean settingFile(String name) {
        return name.equals("global.json") || name.equals("schema.json") || name.matches("account-[0-9]+-[0-9]+(-[a-f0-9-]{36})?\\.json");
    }
    static void validateValue(JsonElement value, int depth) {
        if (depth > 8) throw new IllegalArgumentException("SettingsLimitExceeded");
        if (value.isJsonArray()) {
            if (value.getAsJsonArray().size() > 512) throw new IllegalArgumentException("SettingsLimitExceeded");
            for (JsonElement child : value.getAsJsonArray()) validateValue(child, depth + 1);
        } else if (value.isJsonObject()) {
            if (value.getAsJsonObject().size() > 256) throw new IllegalArgumentException("SettingsLimitExceeded");
            for (JsonElement child : value.getAsJsonObject().asMap().values()) validateValue(child, depth + 1);
        }
    }
}
