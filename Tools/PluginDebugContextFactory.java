import android.content.Context;
import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;

/** Fixed synthetic context for a disposable AVD, without patching any application methods. */
public final class PluginDebugContextFactory {
    public static Object[] create() throws Exception {
        return create("test.debug.sdk", 99L);
    }
    public static Object[] createSecond() throws Exception {
        return create("test.debug.second", 100L);
    }
    private static Object[] create(String id, long generation) throws Exception {
        String base = "org.telegram.messenger.plugins.";
        String text = "{\"manifest_version\":1,\"id\":\"test.debug.sdk\",\"name\":\"Test\",\"description\":\"\",\"author\":\"BYgram\","
                + "\"version\":\"1.0.0\",\"entrypoint\":\"main.py:Main\",\"sdk\":{\"min\":1,\"max\":1},\"python\":\"3.11\","
                + "\"capabilities\":[\"hooks.raw\"],\"account_scope\":\"global\",\"settings_schema\":1,\"requires_hook_backend\":true,"
                + "\"bygram\":{\"min_build\":1,\"max_build\":2147483647},\"telegram\":{\"min_build\":1,\"max_build\":2147483647},"
                + "\"hook_targets\":[{\"class\":\"org.telegram.messenger.plugins.HookBridge$Probe\",\"method\":\"value\",\"args\":[\"int\"]},"
                + "{\"class\":\"org.telegram.messenger.plugins.HookBridge$Probe\",\"constructor\":true,\"args\":[]},"
                + "{\"class\":\"org.telegram.messenger.plugins.HookBridge$Probe\",\"method\":\"label\",\"args\":[\"java.lang.String\"]},"
                + "{\"class\":\"org.telegram.messenger.plugins.HookBridge$Probe\",\"method\":\"label\",\"args\":[\"int\"]},"
                + "{\"class\":\"org.telegram.messenger.plugins.HookBridge$Probe\",\"method\":\"throwsOriginal\",\"args\":[]}]}";
        text = text.replace("test.debug.sdk", id);
        Class<?> manifestType = Class.forName(base + "PluginManifest"), scopeType = Class.forName(base + "PluginScope");
        Object manifest = manifestType.getMethod("parse", byte[].class, int.class, int.class, int.class)
                .invoke(null, text.getBytes(StandardCharsets.UTF_8), 1, 72379, 7237);
        Constructor<?> scopeCtor = scopeType.getDeclaredConstructor(String.class, long.class); scopeCtor.setAccessible(true);
        Object scope = scopeCtor.newInstance(id, generation);
        Object manager = Class.forName(base + "PluginManager").getMethod("get").invoke(null);
        Context app = (Context) Class.forName("org.telegram.messenger.ApplicationLoader").getField("applicationContext").get(null);
        File root = new File(app.getCacheDir(), id);
        Constructor<?> contextCtor = Class.forName(base + "PluginContext").getDeclaredConstructors()[0]; contextCtor.setAccessible(true);
        return new Object[]{contextCtor.newInstance(scope, manifest, root, new File(root, "data"), manager), scope};
    }
}
