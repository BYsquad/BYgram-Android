import android.content.Context;
import java.lang.reflect.*;

/** Run the installed Debug fixture through app_process without producing another APK. */
public final class PluginIntegrationProbe {
    public static void main(String[] args) throws Exception {
        Class<?> vm = Class.forName("dalvik.system.VMRuntime");
        try { System.out.println("Java debuggable: " + vm.getDeclaredMethod("isJavaDebuggable").invoke(vm.getDeclaredMethod("getRuntime").invoke(null))); }
        catch (NoSuchMethodException olderAndroid) { System.out.println("Java debug query unavailable on this Android version"); }
        android.os.Looper.prepareMainLooper();
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        Context system = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
        Context installed = system.createPackageContext("com.bygram.mobile", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        java.util.concurrent.atomic.AtomicReference<Throwable> failed = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { System.out.println(runFixture(installed, args.length == 0 ? null : args[0], args.length < 2 ? null : args[1], args.length < 3 ? null : args[2])); }
            catch (Throwable error) { failed.set(error); error.printStackTrace(); }
        }, "BYgramPluginTest");
        worker.start(); worker.join();
        System.exit(failed.get() == null ? 0 : 1);
    }
    private static String runFixture(Context installed, String override, String pythonRoot, String nativeRoot) throws Exception {
        ClassLoader loader = override == null ? installed.getClassLoader() : new dalvik.system.DexClassLoader(
                override + java.io.File.pathSeparator + installed.getApplicationInfo().sourceDir,
                installed.getCodeCacheDir().getAbsolutePath(), (nativeRoot == null ? "" : nativeRoot + java.io.File.pathSeparator) + installed.getApplicationInfo().nativeLibraryDir,
                ClassLoader.getSystemClassLoader().getParent());
        Context context = installed;
        Method attach = android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
        attach.setAccessible(true);
        android.app.Application application = new android.app.Application();
        attach.invoke(application, context);
        context = new android.content.ContextWrapper(application) {
            @Override public Context getApplicationContext() { return application; }
            @Override public ClassLoader getClassLoader() { return loader; }
        };
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("org.telegram.messenger.ApplicationLoader", true, loader).getField("applicationContext").set(null, context);
        if (pythonRoot != null) {
            Class<?> pythonType = Class.forName("com.chaquo.python.Python", true, loader);
            Class<?> platformType = Class.forName("com.chaquo.python.Python$Platform", true, loader);
            Object platform = Class.forName("com.chaquo.python.android.AndroidPlatform", true, loader).getConstructor(Context.class).newInstance(context);
            pythonType.getMethod("start", platformType).invoke(null, platform);
            Object python = pythonType.getMethod("getInstance").invoke(null);
            Object sys = pythonType.getMethod("getModule", String.class).invoke(python, "sys");
            Object path = sys.getClass().getMethod("get", Object.class).invoke(sys, "path");
            path.getClass().getMethod("callAttr", String.class, Object[].class).invoke(path, "insert", new Object[]{0, pythonRoot});
        }
        Class<?> fixture = Class.forName("org.telegram.messenger.plugins.PluginDiagnosticActivity", true, loader);
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepare();
        Object activity = fixture.getConstructor().newInstance();
        attach.invoke(activity, context);
        Method checks = fixture.getDeclaredMethod("checks", boolean.class); checks.setAccessible(true);
        try { return checks.invoke(activity, false).toString(); }
        catch (InvocationTargetException error) { throw new RuntimeException(error.getCause()); }
    }
}
