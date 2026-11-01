package de.robv.android.xposed;
import java.lang.reflect.Member;
public final class XposedBridge {
    public static int initializations;
    public static XC_MethodHook.Unhook hookMethod(Member member, XC_MethodHook hook) {
        initializations++; throw new UnsatisfiedLinkError("Fixture unavailable backend");
    }
    public static Object invokeOriginalMethod(Member member, Object instance, Object[] args) throws java.lang.reflect.InvocationTargetException { throw new AssertionError("Native init by gate"); }
}
