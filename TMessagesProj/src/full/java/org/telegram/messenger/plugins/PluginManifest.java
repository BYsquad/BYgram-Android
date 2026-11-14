package org.telegram.messenger.plugins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Metadata validation only. Never imports or executes a plugin. */
public final class PluginManifest {
    public static final int MAX_BYTES = 64 * 1024;
    private final JsonObject metadata;

    private PluginManifest(JsonObject metadata) {
        this.metadata = metadata;
    }

    public String id() { return metadata.get("id").getAsString(); }
    public String version() { return metadata.get("version").getAsString(); }
    public String entrypoint() { return metadata.get("entrypoint").getAsString(); }
    public JsonObject metadata() { return metadata.deepCopy(); }

    public static final class ValidationException extends IllegalArgumentException {
        public final String code;
        public final String field;

        private ValidationException(String code, String field) {
            super(code + ": " + field);
            this.code = code;
            this.field = field;
        }
    }

    private static void require(boolean condition, String code, String field) {
        if (!condition) throw new ValidationException(code, field);
    }

    public static PluginManifest parse(byte[] utf8, int sdk, int bygramBuild, int telegramBuild) {
        require(utf8 != null, "InvalidManifest", "manifest");
        require(utf8.length <= MAX_BYTES, "PackageLimitExceeded", "manifest");
        JsonObject root;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(utf8)).toString();
            try (JsonReader reader = new JsonReader(new StringReader(text))) {
                reader.setStrictness(Strictness.STRICT);
                JsonElement value = read(reader, 0);
                require(reader.peek() == JsonToken.END_DOCUMENT && value.isJsonObject(),
                        "InvalidManifest", "manifest");
                root = value.getAsJsonObject();
            }
        } catch (CharacterCodingException e) {
            throw new ValidationException("InvalidManifest", "utf8");
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw new ValidationException("InvalidManifest", "json");
        }
        fields(root, "manifest_version", "id", "name", "description", "version", "author",
                "entrypoint", "sdk", "python", "capabilities", "account_scope", "settings_schema",
                "icon", "sticker_icon", "bygram", "telegram", "requires_hook_backend", "hook_targets");
        require(integer(root, "manifest_version") == 1, "InvalidManifest", "manifest_version");
        String id = string(root, "id", 120, true);
        require(id.length() >= 3 && id.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"),
                "InvalidManifest", "id");
        string(root, "name", 80, true);
        string(root, "description", 1000, false);
        string(root, "author", 120, false);
        require(string(root, "version", 80, true).matches(
                "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"), "InvalidManifest", "version");
        String entry = string(root, "entrypoint", MAX_BYTES, true);
        int colon = entry.indexOf(':');
        require(colon > 0 && colon == entry.lastIndexOf(':'), "InvalidManifest", "entrypoint");
        path(entry.substring(0, colon), "entrypoint");
        require((entry.substring(0, colon).endsWith(".py") || entry.endsWith(".pyc:_ElyxEntry")) &&
                entry.substring(colon + 1).matches("[a-zA-Z_][a-zA-Z0-9_]*") &&
                !PYTHON_KEYWORDS.contains(entry.substring(colon + 1)), "InvalidManifest", "entrypoint");
        range(object(root, "sdk"), "min", "max", sdk, "IncompatibleSdk");
        require("3.11".equals(string(root, "python", 16, true)), "IncompatiblePython", "python");
        Set<String> capabilities = new HashSet<>();
        for (JsonElement item : array(root, "capabilities")) {
            require(item.isJsonPrimitive() && item.getAsJsonPrimitive().isString(), "InvalidManifest", "capabilities");
            String capability = item.getAsString();
            require(CAPABILITIES.contains(capability), "InvalidManifest", "capabilities");
            require(capabilities.add(capability), "InvalidManifest", "capabilities");
        }
        String scope = string(root, "account_scope", 32, true);
        require(scope.equals("global") || scope.equals("selected_accounts"), "InvalidManifest", "account_scope");
        require(!capabilities.contains("hooks.telegram") || scope.equals("selected_accounts"), "InvalidManifest", "hooks.telegram");
        require(!capabilities.contains("telegram.requests") || scope.equals("selected_accounts"), "InvalidManifest", "telegram.requests");
        require(!capabilities.contains("messages.media") || scope.equals("selected_accounts"), "InvalidManifest", "messages.media");
        integer(root, "settings_schema");
        if (root.has("icon")) {
            String icon = string(root, "icon", MAX_BYTES, true);
            path(icon, "icon");
            require(icon.endsWith(".png") || icon.endsWith(".webp"), "InvalidManifest", "icon");
        }
        if (root.has("sticker_icon")) {
            require(isStickerIcon(string(root, "sticker_icon", 74, true)), "InvalidManifest", "sticker_icon");
        }
        if (root.has("bygram")) range(object(root, "bygram"), "min_build", "max_build", bygramBuild, "IncompatibleBuild");
        if (root.has("telegram")) range(object(root, "telegram"), "min_build", "max_build", telegramBuild, "IncompatibleBuild");
        boolean backend = false;
        if (root.has("requires_hook_backend")) {
            JsonElement flag = root.get("requires_hook_backend");
            require(flag.isJsonPrimitive() && flag.getAsJsonPrimitive().isBoolean(), "InvalidManifest", "requires_hook_backend");
            backend = flag.getAsBoolean();
        }
        if (capabilities.contains("hooks.raw")) {
            require(backend && root.has("bygram") && root.has("telegram") && root.has("hook_targets"),
                    "InvalidManifest", "hooks.raw");
        }
        if (root.has("hook_targets")) {
            JsonArray targets = array(root, "hook_targets");
            require(capabilities.contains("hooks.raw") && targets.size() > 0 && targets.size() <= 256, "InvalidManifest", "hook_targets");
            Set<String> seenTargets = new HashSet<>();
            for (JsonElement target : targets) {
                require(target.isJsonObject(), "InvalidManifest", "hook_targets");
                JsonObject t = target.getAsJsonObject();
                fields(t, "class", "method", "constructor", "args");
                require(string(t, "class", 512, true).matches(JAVA_CLASS), "InvalidManifest", "class");
                require(t.has("method") != t.has("constructor"), "InvalidManifest", "hook_targets");
                if (t.has("method")) require(string(t, "method", 256, true).matches("[A-Za-z_$][A-Za-z0-9_$]*"), "InvalidManifest", "method");
                else require(t.get("constructor").isJsonPrimitive() && t.getAsJsonPrimitive("constructor").isBoolean()
                        && t.get("constructor").getAsBoolean(), "InvalidManifest", "constructor");
                JsonArray args = array(t, "args");
                require(args.size() <= 64, "InvalidManifest", "args");
                for (JsonElement arg : args) {
                    require(arg.isJsonPrimitive() && arg.getAsJsonPrimitive().isString()
                            && arg.getAsString().length() <= 512
                            && arg.getAsString().matches("(" + JAVA_CLASS + "|boolean|byte|char|short|int|long|float|double)(\\[\\])*"),
                            "InvalidManifest", "args");
                    require(!arg.getAsString().replace("[]", "").equals("void"), "InvalidManifest", "args");
                }
                require(seenTargets.add(t.get("class").getAsString() + "#"
                                + (t.has("method") ? t.get("method").getAsString() : "<init>") + args),
                        "InvalidManifest", "hook_targets");
            }
        }
        return new PluginManifest(root);
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        require(depth <= 8, "PackageLimitExceeded", "json_depth");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    require(!object.has(key), "InvalidManifest", key);
                    object.add(key, read(reader, depth + 1));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1));
                reader.endArray();
                return array;
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER: return new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            default: throw new ValidationException("InvalidManifest", reader.getPath());
        }
    }

    private static void fields(JsonObject object, String... allowed) {
        Set<String> names = new HashSet<>(Arrays.asList(allowed));
        for (String key : object.keySet()) require(names.contains(key), "InvalidManifest", key);
    }

    private static String string(JsonObject object, String key, int max, boolean nonempty) {
        JsonElement value = object.get(key);
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), "InvalidManifest", key);
        String text = value.getAsString();
        require(text.codePointCount(0, text.length()) <= max && (!nonempty || !text.trim().isEmpty()), "InvalidManifest", key);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            require(!Character.isISOControl(c), "InvalidManifest", key);
            if (Character.isHighSurrogate(c)) {
                require(i + 1 < text.length() && Character.isLowSurrogate(text.charAt(++i)), "InvalidManifest", key);
            } else require(!Character.isLowSurrogate(c), "InvalidManifest", key);
        }
        return text;
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), "InvalidManifest", key);
        try {
            int number = value.getAsBigDecimal().intValueExact();
            require(number >= 1, "InvalidManifest", key);
            return number;
        } catch (ArithmeticException e) { throw new ValidationException("InvalidManifest", key); }
    }

    private static JsonObject object(JsonObject parent, String key) {
        require(parent.has(key) && parent.get(key).isJsonObject(), "InvalidManifest", key);
        return parent.getAsJsonObject(key);
    }

    private static JsonArray array(JsonObject parent, String key) {
        require(parent.has(key) && parent.get(key).isJsonArray(), "InvalidManifest", key);
        return parent.getAsJsonArray(key);
    }

    private static void range(JsonObject range, String min, String max, int current, String error) {
        fields(range, min, max);
        int low = integer(range, min), high = integer(range, max);
        require(low <= high, "InvalidManifest", min);
        require(current >= low && current <= high, error, min);
    }

    private static void path(String path, String field) {
        String[] parts = path.split("/", -1);
        require(parts.length <= 12 && !path.contains("\\") && !path.contains(":"), "InvalidManifest", field);
        for (String part : parts) require(!part.isEmpty() && !part.equals(".") && !part.equals(".."), "InvalidManifest", field);
    }

    public static boolean isStickerIcon(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{1,64}/[0-9]{1,9}");
    }

    private static final String JAVA_CLASS = "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*";
    private static final Set<String> CAPABILITIES = new HashSet<>(Arrays.asList(
            "ui", "theme", "messages.observe", "messages.send", "messages.media", "network", "hooks.raw", "hooks.telegram", "telegram.requests"));
    private static final Set<String> PYTHON_KEYWORDS = new HashSet<>(Arrays.asList(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
            "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
            "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"));
}
