package de.robv.android.xposed;
import java.lang.reflect.Member;
/** Linkage only: any registration would fail in the XposedBridge stand-in. */
public class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) { }
    protected void afterHookedMethod(MethodHookParam param) { }
    public static final class Unhook { public void unhook() { throw new AssertionError("Native unhook by gate"); } }
    public static final class MethodHookParam {
        public Member method; public Object thisObject; public Object[] args;
        public Object getResult() { throw new AssertionError(); }
        public Throwable getThrowable() { throw new AssertionError(); }
        public void setResult(Object value) { throw new AssertionError(); }
        public void setThrowable(Throwable error) { throw new AssertionError(); }
    }
}
