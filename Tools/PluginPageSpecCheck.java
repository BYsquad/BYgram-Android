package org.telegram.messenger.plugins;

import com.google.gson.*;
import java.util.*;

public final class PluginPageSpecCheck {
    private static int rejected;
    private static JsonObject page(String items) { return JsonParser.parseString("{\"title\":\"Settings\",\"items\":[" + items + "]}").getAsJsonObject(); }
    private static void reject(String items) {
        try { PluginPageSpec.validate(page(items), false, Collections.emptySet()); }
        catch (IllegalArgumentException expected) { rejected++; return; }
        throw new AssertionError("Accepted invalid descriptor: " + items);
    }
    public static void main(String[] args) {
        String text = "{\"type\":\"TextInput\",\"key\":\"label\",\"title\":\"Label\",\"default\":\"ok\",\"max_length\":4}";
        Map<String, JsonObject> fields = PluginPageSpec.validate(page(text), false, Collections.emptySet());
        assert fields.size() == 1;
        PluginPageSpec.validateValue(fields.get("global:label"), new JsonPrimitive("four"));
        assert PluginPageSpec.validate(page(text + "," + text.replace("\"Label\"", "\"Account\"").replace("\"key\"", "\"scope\":\"account\",\"key\"")), true, Collections.emptySet()).size() == 2;
        reject(text + "," + text);
        reject(text.replace("\"Label\"", "true"));
        reject(text.replace("\"max_length\":4", "\"max_length\":4.1"));
        reject(text.replace("\"max_length\":4", "\"max_length\":\"4\""));
        reject(text.replace("\"ok\"", "true"));
        reject(text.replace("\"ok\"", "\"too long\""));
        reject(text.replace("\"key\"", "\"scope\":\"account\",\"key\""));
        reject(text.replace("\"key\"", "\"scope\":\"unknown\",\"key\""));
        reject("{\"type\":\"Action\",\"title\":\"Run\",\"callback\":\"unknown\"}");
        reject("{\"type\":\"Text\",\"title\":12}");
        reject("null");
        String select = "{\"type\":\"Select\",\"key\":\"choice\",\"title\":\"Pick\",\"options\":[{\"title\":\"One\",\"value\":1}]}";
        JsonObject selected = PluginPageSpec.validate(page(select), false, Collections.emptySet()).get("global:choice");
        assert selected.get("default").getAsInt() == 1;
        reject(select.replace("[{\"title\":\"One\",\"value\":1}]", "[]"));
        reject(select.replace("\"value\":1", "\"value\":{}"));
        reject(select.replace("\"value\":1", "\"value\":null"));
        reject(select.replace("\"title\":\"One\"", "\"title\":true"));
        reject(select.replace("\"value\":1}", "\"value\":1},{\"title\":\"Duplicate\",\"value\":1}"));
        String slider = "{\"type\":\"Slider\",\"key\":\"size\",\"title\":\"Size\",\"min\":-2,\"max\":2,\"step\":0.5,\"default\":-1.5}";
        PluginPageSpec.validate(page(slider), false, Collections.emptySet());
        reject(slider.replace("-1.5", "-1.1"));
        reject(slider.replace("\"step\":0.5", "\"step\":0"));
        reject(slider.replace("\"min\":-2", "\"min\":3"));
        reject(slider.replace("-1.5", "1e999"));
        reject(slider.replace("-1.5", "\"1\""));
        PluginPageSpec.validate(page("{\"type\":\"Action\",\"title\":\"Run\",\"callback\":\"ok\"}"), false, Collections.singleton("ok"));
        System.out.println("PASS: native page descriptors, scoped keys, defaults and " + rejected + " invalid schemas");
    }
}
