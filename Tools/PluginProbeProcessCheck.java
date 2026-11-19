import android.content.*;
import android.os.*;
import java.lang.reflect.*;

/** Runs the production Service's built-in probe in a disposable app_process, without an APK. */
public final class PluginProbeProcessCheck {
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        Context system = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
        Context installed = system.createPackageContext("com.bygram.mobile", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        ClassLoader loader = new dalvik.system.DexClassLoader(args[0] + java.io.File.pathSeparator + installed.getApplicationInfo().sourceDir,
                installed.getCodeCacheDir().getAbsolutePath(), (args.length > 1 ? args[1] + java.io.File.pathSeparator : "")
                        + installed.getApplicationInfo().nativeLibraryDir, ClassLoader.getSystemClassLoader().getParent());
        Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class); attach.setAccessible(true);
        android.app.Application application = new android.app.Application(); attach.invoke(application, installed);
        Context wrapper = new ContextWrapper(application) {
            @Override public Context getApplicationContext() { return application; }
            @Override public ClassLoader getClassLoader() { return loader; }
        };
        Class.forName("org.telegram.messenger.ApplicationLoader", true, loader).getField("applicationContext").set(null, wrapper);
        Object service = Class.forName("org.telegram.messenger.plugins.PluginBackendProbe", true, loader).getConstructor().newInstance();
        attach.invoke(service, wrapper);
        IBinder binder = (IBinder) service.getClass().getMethod("onBind", Intent.class).invoke(service, new Intent());
        Message request = Message.obtain(null, 1);
        request.replyTo = new Messenger(new Handler(Looper.getMainLooper(), reply -> {
            System.out.println(reply.arg1 == 1 ? "PASS: production device probe, Python/native callbacks and GC in disposable process" : "FAIL: device probe rejected");
            System.exit(reply.arg1 == 1 ? 0 : 1); return true;
        }));
        new Messenger(binder).send(request);
        Looper.loop();
    }
}
