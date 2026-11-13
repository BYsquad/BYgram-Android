package org.telegram.messenger.plugins;

import com.google.gson.*;
import java.util.*;

/** Validates a complete native page before registering any of its setting fields. */
final class PluginPageSpec {
    static Map<String, JsonObject> validate(JsonObject page, boolean accounts, Set<String> callbacks) {
        Map<String, JsonObject> fields = new HashMap<>();
        Set<String> aliases = new HashSet<>();
        require(string(page.get("title"), 120) && page.has("items") && page.get("items").isJsonArray()
                && page.getAsJsonArray("items").size() <= 100, "InvalidPage");
        JsonElement path = page.get("link_path");
        if (path != null && !path.isJsonNull()) {
            require(path.isJsonArray() && path.getAsJsonArray().size() < 12, "InvalidPluginSettingsLink");
            for (JsonElement part : path.getAsJsonArray()) require(alias(part), "InvalidPluginSettingsLink");
        }
        for (JsonElement element : page.getAsJsonArray("items")) {
            require(element.isJsonObject(), "InvalidPage");
            JsonObject field = element.getAsJsonObject();
            require(string(field.get("type"), 32) && string(field.get("title"), 1000), "InvalidPage");
            String type = field.get("type").getAsString();
            if (field.has("link_alias")) {
                require(alias(field.get("link_alias"))
                        && aliases.add(field.get("link_alias").getAsString()), "InvalidPluginSettingsLink");
            }
            if (field.has("mask")) {
                require(type.equals("TextInput") && string(field.get("mask"), 512), "InvalidSettingMask");
                java.util.regex.Pattern.compile(field.get("mask").getAsString());
            }
            require(Arrays.asList("Header", "Text", "Switch", "TextInput", "Select", "Slider", "Action", "Divider", "Custom").contains(type), "InvalidPage");
            for (String property : new String[]{"callback", "change_callback", "long_callback", "row_factory"}) {
                if (field.has(property) || property.equals("callback") && type.equals("Action")
                        || property.equals("row_factory") && type.equals("Custom")) {
                    require(string(field.get(property), 120) && callbacks.contains(field.get(property).getAsString()), "UnknownCallback");
                }
            }
            if (Arrays.asList("Header", "Text", "Action", "Divider", "Custom").contains(type)) continue;
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
            if (field.has("mask") && string(field.get("default"), 0)) {
                JsonObject initial = field.deepCopy();
                initial.remove("mask");
                validateValue(initial, field.get("default"));
            } else {
                validateValue(field, field.get("default"));
            }
            fields.put(key, field.deepCopy());
        }
        if (page.has("focus_alias"))
            require(alias(page.get("focus_alias")) && aliases.contains(page.get("focus_alias").getAsString()), "InvalidPluginSettingsLink");
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
                            && max >= 0 && max <= 10000 && string(value, max)
                            && (!field.has("mask") || value.getAsString().matches(field.get("mask").getAsString())); break;
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
    private static boolean alias(JsonElement value) {
        return string(value, 64) && value.getAsString().matches("[A-Za-z0-9_.-]{1,64}");
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
