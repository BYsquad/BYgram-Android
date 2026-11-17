package org.telegram.messenger.plugins;

import android.app.Service;
import android.content.*;
import android.os.*;
import com.chaquo.python.*;
import com.chaquo.python.android.AndroidPlatform;
import de.robv.android.xposed.*;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.*;

/** Only built-in probe code runs in this disposable process, never an installed plugin. */
public final class PluginBackendProbe extends Service {
    private static Boolean accepted;
    public static synchronized void ensure(Context context) throws Exception {
        if (accepted != null) { if (!accepted) throw new IOException("HookProbeFailed"); return; }
        accepted = false;
        if (!HookBridge.available()) throw new IOException("HookBackendUnavailable");
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Messenger reply = new Messenger(new Handler(Looper.getMainLooper(), message -> { result.complete(message.arg1 == 1); return true; }));
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                Message message = Message.obtain(null, 1); message.replyTo = reply;
                try { new Messenger(binder).send(message); } catch (RemoteException error) { result.complete(false); }
            }
            @Override public void onServiceDisconnected(ComponentName name) { result.complete(false); }
            @Override public void onNullBinding(ComponentName name) { result.complete(false); }
        };
        boolean bound = context.bindService(new Intent(context, PluginBackendProbe.class), connection, Context.BIND_AUTO_CREATE);
        try {
            if (!bound || !result.get(20, TimeUnit.SECONDS)) throw new IOException("HookProbeFailed");
            accepted = true;
        } catch (TimeoutException timeout) { throw new IOException("HookProbeFailed", timeout); }
        finally { if (bound) context.unbindService(connection); }
    }
    @Override public IBinder onBind(Intent intent) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 15000);
        return new Messenger(new Handler(Looper.getMainLooper(), message -> {
            Messenger reply = message.replyTo;
            if (reply == null) return true;
            new Thread(() -> {
                boolean passed = false;
                try {
                    Python.start(new AndroidPlatform(this));
                    Python python = Python.getInstance(); python.getModule("bygram");
                    HookBridge.verify();
                    PyObject increment = python.getBuiltins().callAttr("eval", "lambda value: value + 1", python.getBuiltins().callAttr("dict"));
                    Method target = HookBridge.Probe.class.getDeclaredMethod("value", int.class);
                    for (int i = 0; i < 30; i++) {
                        XC_MethodHook.Unhook handle = XposedBridge.hookMethod(target, new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam param) { param.args[0] = increment.call(param.args[0]).toInt(); }
                        });
                        try {
                            if (!target.invoke(null, 2).equals(6)) throw new IllegalStateException("HookProbeFailed");
                        } finally { handle.unhook(); }
                        System.gc(); System.runFinalization();
                        if (!target.invoke(null, 2).equals(4)) throw new IllegalStateException("HookProbeFailed");
                    }
                    passed = true;
                } catch (Throwable error) { android.util.Log.e("BYgramHookProbe", "Device probe failed", error); }
                Message answer = Message.obtain(null, 1); answer.arg1 = passed ? 1 : 0;
                try { reply.send(answer); } catch (RemoteException ignored) { }
                new Handler(Looper.getMainLooper()).postDelayed(() -> { stopSelf(); android.os.Process.killProcess(android.os.Process.myPid()); }, 500);
            }, "BYgramDeviceProbe").start();
            return true;
        })).getBinder();
    }
}
