package org.telegram.messenger.plugins;

import com.google.gson.*;
import java.util.*;

/** Validates a complete native page before registering any of its setting fields. */
final class PluginPageSpec {
    static Map<String, JsonObject> validate(JsonObject page, boolean accounts, Set<String> callbacks) {
        Map<String, JsonObject> fields = new HashMap<>();
        require(string(page.get("title"), 120) && page.has("items") && page.get("items").isJsonArray()
                && page.getAsJsonArray("items").size() <= 100, "InvalidPage");
        for (JsonElement element : page.getAsJsonArray("items")) {
            require(element.isJsonObject(), "InvalidPage");
            JsonObject field = element.getAsJsonObject();
            require(string(field.get("type"), 32) && string(field.get("title"), 1000), "InvalidPage");
            String type = field.get("type").getAsString();
            require(Arrays.asList("Header", "Text", "Switch", "TextInput", "Select", "Slider", "Action").contains(type), "InvalidPage");
            if (type.equals("Action")) {
                require(string(field.get("callback"), 120) && callbacks.contains(field.get("callback").getAsString()), "UnknownCallback");
            }
            if (Arrays.asList("Header", "Text", "Action").contains(type)) continue;
            String scope = "global";
            if (field.has("scope")) {
                require(string(field.get("scope"), 7), "InvalidSettingScope");
                scope = field.get("scope").getAsString();
            }
            require(scope.equals("global") || scope.equals("account") && accounts, "InvalidSettingScope");
            field.addProperty("scope", scope);
            require(string(field.get("key"), 120) && field.get("key").getAsString().matches("[A-Za-z_][A-Za-z0-9_.-]{0,119}"), "InvalidSettingKey");
            String key = scope + ":" + field.get("key").getAsString();
            require(!fields.containsKey(key), "InvalidSettingKey");
            if (type.equals("Select")) {
                require(field.has("options") && field.get("options").isJsonArray()
                        && field.getAsJsonArray("options").size() > 0 && field.getAsJsonArray("options").size() <= 100, "InvalidSetting");
                Set<JsonElement> values = new HashSet<>();
                for (JsonElement option : field.getAsJsonArray("options")) {
                    require(option.isJsonObject(), "InvalidSetting");
                    JsonObject item = option.getAsJsonObject();
                    require(string(item.get("title"), 120) && scalar(item.get("value")) && values.add(item.get("value")), "InvalidSetting");
                }
                if (!field.has("default") || field.get("default").isJsonNull())
                    field.add("default", field.getAsJsonArray("options").get(0).getAsJsonObject().get("value").deepCopy());
            }
            validateValue(field, field.get("default"));
            fields.put(key, field.deepCopy());
        }
        return fields;
    }
    static void validateValue(JsonObject field, JsonElement value) {
        boolean valid = false;
        try {
            if (value != null && !value.isJsonNull()) switch (field.get("type").getAsString()) {
                case "Switch": valid = value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(); break;
                case "TextInput":
                    JsonElement length = field.get("max_length");
                    int max = length.getAsBigDecimal().intValueExact();
                    valid = length.isJsonPrimitive() && length.getAsJsonPrimitive().isNumber()
                            && max >= 0 && max <= 10000 && string(value, max); break;
                case "Select":
                    if (!scalar(value)) break;
                    for (JsonElement option : field.getAsJsonArray("options"))
                        if (option.getAsJsonObject().get("value").equals(value)) valid = true;
                    break;
                case "Slider":
                    if (!number(value) || !number(field.get("min")) || !number(field.get("max")) || !number(field.get("step"))) break;
                    double n = value.getAsDouble(), min = field.get("min").getAsDouble(), upper = field.get("max").getAsDouble(), step = field.get("step").getAsDouble();
                    valid = step > 0 && min <= upper && Double.isFinite((upper - min) / step) && n >= min && n <= upper
                            && Math.abs((n - min) / step - Math.rint((n - min) / step)) < 0.000001;
                    break;
            }
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("InvalidSetting", invalid); }
        require(valid, "InvalidSetting");
    }
    private static boolean string(JsonElement value, int limit) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().length() <= limit;
    }
    private static boolean number(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && Double.isFinite(value.getAsDouble());
    }
    private static boolean scalar(JsonElement value) {
        return value != null && value.isJsonPrimitive() && (!value.getAsJsonPrimitive().isNumber() || number(value));
    }
    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException(code);
    }
}
