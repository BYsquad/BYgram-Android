import android.content.*;
import java.lang.reflect.*;

/** Fixed Python/native experiment in app_process; uses no Telegram accounts or plugin registry. */
public final class PluginUnhookProbe {
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        Context installed = system.createPackageContext("com.bygram.mobile", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        ClassLoader loader = new dalvik.system.DexClassLoader(args[0] + java.io.File.pathSeparator + installed.getApplicationInfo().sourceDir,
                installed.getCodeCacheDir().getAbsolutePath(), args[1] + java.io.File.pathSeparator + installed.getApplicationInfo().nativeLibraryDir,
                ClassLoader.getSystemClassLoader().getParent());
        Thread.currentThread().setContextClassLoader(loader);
        android.app.Application application = new android.app.Application();
        Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class); attach.setAccessible(true); attach.invoke(application, installed);
        Context context = new ContextWrapper(application) {
            @Override public Context getApplicationContext() { return application; }
            @Override public ClassLoader getClassLoader() { return loader; }
        };
        Class.forName("org.telegram.messenger.ApplicationLoader", true, loader).getField("applicationContext").set(null, context);
        Class<?> pythonType = Class.forName("com.chaquo.python.Python", true, loader);
        Object platform = Class.forName("com.chaquo.python.android.AndroidPlatform", true, loader).getConstructor(Context.class).newInstance(context);
        pythonType.getMethod("start", Class.forName("com.chaquo.python.Python$Platform", true, loader)).invoke(null, platform);
        Object python = pythonType.getMethod("getInstance").invoke(null);
        Object builtins = pythonType.getMethod("getModule", String.class).invoke(python, "builtins");
        String code = "exec(open(" + "'" + args[2] + "'" + ", encoding='utf-8').read())";
        Method call = builtins.getClass().getMethod("callAttr", String.class, Object[].class);
        Object globals = call.invoke(builtins, "dict", new Object[]{});
        try { call.invoke(builtins, "exec", new Object[]{code, globals}); }
        catch (InvocationTargetException error) { error.getCause().printStackTrace(); System.exit(1); }
        System.exit(0);
    }
}
