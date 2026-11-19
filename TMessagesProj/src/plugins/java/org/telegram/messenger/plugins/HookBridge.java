package org.telegram.messenger.plugins;

import android.os.Build;
import com.chaquo.python.PyObject;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.*;
import java.util.*;

/** One native dispatcher per Member. No global ART optimization changes. */
public final class HookBridge {
    private static final Map<Member, Dispatcher> dispatchers = new HashMap<>();
    private static long sequence;
    private static Boolean checked;
    private static final int MAX_DISPATCHERS = 1024;
    private static final ThreadLocal<Set<Registration>> entered = new ThreadLocal<Set<Registration>>() {
        @Override protected Set<Registration> initialValue() { return new HashSet<>(); }
    };

    public static synchronized boolean available() {
        // Candidate OS/ABI; PluginBackendProbe checks this device before main-process initialization.
        if (Build.VERSION.SDK_INT < 24) return false;
        boolean process64 = android.os.Process.is64Bit();
        for (String abi : Build.SUPPORTED_ABIS) {
            if (process64 ? "x86_64".equals(abi) || "arm64-v8a".equals(abi)
                    : "x86".equals(abi) || "armeabi-v7a".equals(abi)) return true;
        }
        return false;
    }
    public static void verify() throws Exception {
        if (!available()) throw new IllegalStateException("HookBackendUnavailable");
        probe();
    }
    private static synchronized void probe() throws Exception {
        if (checked != null) { if (!checked) throw new IllegalStateException("HookBackendUnavailable"); return; }
        checked = false;
        Method method = Probe.class.getDeclaredMethod("value", int.class);
        XC_MethodHook.Unhook handle;
        try {
            handle = XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { p.args[0] = ((Integer) p.args[0]) + 1; }
                @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(((Integer) p.getResult()) + 1); }
            });
        } catch (LinkageError unavailable) { throw new IllegalStateException("HookBackendUnavailable", unavailable); }
        try {
            if (!method.invoke(null, 2).equals(7)) throw new IllegalStateException("HookProbeFailed");
        } finally { handle.unhook(); }
        if (!method.invoke(null, 2).equals(4)) throw new IllegalStateException("HookProbeFailed");
        checked = true;
    }
    public static final class Probe {
        public static int calls;
        public static int value(int value) { calls++; return value * 2; }
        private String label(String value) { return value; }
        public String privateLabel(String value) { return label(value); }
        public String label(int value) { return "int:" + value; }
        public int throwsOriginal() { throw new IllegalStateException("fixture"); }
    }

    public static AutoCloseable register(PluginContext context, String className, String methodName,
                                         String[] argumentNames, PyObject before, PyObject after,
                                         PyObject replacement, int priority) throws Exception {
        context.capability("hooks.raw");
        if (replacement != null && (before != null || after != null)) throw new IllegalArgumentException("InvalidHookCombination");
        if (!available()) throw new IllegalStateException("HookBackendUnavailable");
        com.google.gson.JsonObject manifest = context.manifest.metadata();
        boolean declared = false;
        for (com.google.gson.JsonElement target : manifest.getAsJsonArray("hook_targets")) {
            com.google.gson.JsonObject t = target.getAsJsonObject();
            String name = t.has("constructor") ? "<init>" : t.get("method").getAsString();
            List<String> args = new ArrayList<>();
            for (com.google.gson.JsonElement arg : t.getAsJsonArray("args")) args.add(arg.getAsString());
            if (t.get("class").getAsString().equals(className) && name.equals(methodName)
                    && args.equals(Arrays.asList(argumentNames))) declared = true;
        }
        if (!declared) throw new IllegalArgumentException("HookTargetMissing");
        Member member = resolve(className, methodName, argumentNames);
        if (member instanceof Constructor && replacement != null) throw new IllegalArgumentException("UnsupportedHookTarget");
        ((AccessibleObject) member).setAccessible(true);
        synchronized (HookBridge.class) {
            probe();
            Dispatcher dispatcher = dispatchers.get(member);
            if (dispatcher == null) {
                if (dispatchers.size() >= MAX_DISPATCHERS) throw new IllegalStateException("HookLimitExceeded");
                dispatcher = new Dispatcher(member); dispatchers.put(member, dispatcher);
            }
            Registration registration = new Registration(context, before, after, replacement, priority, ++sequence);
            if (replacement != null) for (Registration item : dispatcher.callbacks)
                if (item.replacement != null) throw new IllegalStateException("HookConflict: " + item.context.id());
            dispatcher.callbacks.add(registration);
            dispatcher.callbacks.sort(Comparator.comparingInt((Registration r) -> r.priority).reversed()
                    .thenComparing(r -> r.context.id()).thenComparingLong(r -> r.sequence));
            Dispatcher owner = dispatcher;
            return context.scope.own(() -> {
                synchronized (HookBridge.class) {
                    if (!owner.callbacks.remove(registration)) return;
                    owner.retire();
                }
            });
        }
    }
    static void verifyTargets(PluginManifest manifest) throws ReflectiveOperationException {
        for (com.google.gson.JsonElement target : manifest.metadata().getAsJsonArray("hook_targets")) {
            com.google.gson.JsonObject entry = target.getAsJsonObject();
            com.google.gson.JsonArray args = entry.getAsJsonArray("args");
            String[] names = new String[args.size()];
            for (int i = 0; i < names.length; i++) names[i] = args.get(i).getAsString();
            String name = entry.has("constructor") ? "<init>" : entry.get("method").getAsString();
            resolve(entry.get("class").getAsString(), name, names);
        }
    }
    public static String describeTargets(String className, String methodName) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className, false, HookBridge.class.getClassLoader());
        com.google.gson.JsonArray targets = new com.google.gson.JsonArray();
        Member[] members = "<init>".equals(methodName) ? clazz.getDeclaredConstructors() : clazz.getDeclaredMethods();
        Arrays.sort(members, Comparator.comparing(Object::toString));
        for (Member member : members) {
            if (!(member instanceof Constructor) && !member.getName().equals(methodName)) continue;
            if (member.isSynthetic() || Modifier.isNative(member.getModifiers()) || Modifier.isAbstract(member.getModifiers())) continue;
            com.google.gson.JsonObject target = new com.google.gson.JsonObject();
            target.addProperty("class", className);
            if (member instanceof Constructor) target.addProperty("constructor", true);
            else target.addProperty("method", methodName);
            com.google.gson.JsonArray args = new com.google.gson.JsonArray();
            Class<?>[] parameters = member instanceof Constructor
                    ? ((Constructor<?>) member).getParameterTypes() : ((Method) member).getParameterTypes();
            for (Class<?> argument : parameters) args.add(typeName(argument));
            target.add("args", args);
            targets.add(target);
        }
        return targets.toString();
    }
    private static String typeName(Class<?> type) {
        return type.isArray() ? typeName(type.getComponentType()) + "[]" : type.getName();
    }
    private static Member resolve(String className, String methodName, String[] argumentNames) throws ReflectiveOperationException {
        try {
            Class<?> clazz = Class.forName(className, false, HookBridge.class.getClassLoader());
            Class<?>[] arguments = new Class<?>[argumentNames.length];
            for (int i = 0; i < arguments.length; i++) arguments[i] = type(argumentNames[i]);
            Member member = methodName.equals("<init>") ? clazz.getDeclaredConstructor(arguments) : clazz.getDeclaredMethod(methodName, arguments);
            if (Modifier.isAbstract(member.getModifiers()) || Modifier.isNative(member.getModifiers()))
                throw new IllegalArgumentException("UnsupportedHookTarget: " + member);
            return member;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError missing) {
            throw new NoSuchMethodException("HookTargetUnavailable: " + className + "." + methodName + Arrays.toString(argumentNames));
        }
    }
    static synchronized int dispatcherCount() { return dispatchers.size(); }
    static synchronized int registrationCount() {
        int count = 0; for (Dispatcher dispatcher : dispatchers.values()) count += dispatcher.callbacks.size(); return count;
    }
    private static Class<?> type(String name) throws ClassNotFoundException {
        if (name.endsWith("[]")) return Array.newInstance(type(name.substring(0, name.length() - 2)), 0).getClass();
        switch (name) {
            case "boolean": return boolean.class; case "byte": return byte.class; case "char": return char.class;
            case "short": return short.class; case "int": return int.class; case "long": return long.class;
            case "float": return float.class; case "double": return double.class;
            default: return Class.forName(name, false, HookBridge.class.getClassLoader());
        }
    }
    private static final class Registration {
        final PluginContext context; final PyObject before, after, replacement; final int priority; final long sequence;
        Registration(PluginContext context, PyObject before, PyObject after, PyObject replacement, int priority, long sequence) {
            this.context = context; this.before = before; this.after = after; this.replacement = replacement;
            this.priority = priority; this.sequence = sequence;
        }
    }
    public static final class Call {
        private final XC_MethodHook.MethodHookParam param;
        private Object[] arguments;
        private Object result;
        private Throwable throwable;
        private boolean changed, originalCalled;
        private boolean allowOriginal;
        private Object originalResult;
        private Throwable originalError;
        Call(XC_MethodHook.MethodHookParam param) {
            this.param = param; arguments = param.args.clone(); result = param.getResult(); throwable = param.getThrowable();
        }
        public Object[] args() { return arguments; }
        public Member member() { return param.method; }
        public Object thisObject() { return param.thisObject; }
        public Object result() { return result; }
        public Throwable exception() { return throwable; }
        public void setArgument(int index, PyObject value) {
            Class<?>[] types = param.method instanceof Method ? ((Method) param.method).getParameterTypes() : ((Constructor<?>) param.method).getParameterTypes();
            arguments[index] = value == null ? null : value.toJava(types[index]);
        }
        public void setPythonResult(PyObject value) {
            Class<?> expected = param.method instanceof Method ? ((Method) param.method).getReturnType() : void.class;
            if (expected == void.class && value != null) throw new IllegalArgumentException("InvalidHookResult");
            setResult(value == null || expected == void.class ? null : value.toJava(expected));
        }
        public void setResult(Object result) {
            if (param.method instanceof Constructor) throw new IllegalArgumentException("InvalidHookResult");
            Class<?> expected = param.method instanceof Method ? ((Method) param.method).getReturnType() : void.class;
            if (expected == void.class && result != null || expected != void.class && !valid(expected, result)) throw new IllegalArgumentException("InvalidHookResult");
            this.result = result; throwable = null; changed = true;
        }
        public void setException(Throwable error) { if (error == null) throw new IllegalArgumentException("InvalidHookException"); throwable = error; result = null; changed = true; }
        public Object callOriginal() throws Throwable {
            if (!allowOriginal) throw new IllegalStateException("OriginalOnlyInReplacement");
            if (originalCalled) {
                if (originalError != null) throw originalError;
                return originalResult;
            }
            Class<?>[] types = param.method instanceof Method ? ((Method) param.method).getParameterTypes() : ((Constructor<?>) param.method).getParameterTypes();
            for (int i = 0; i < types.length; i++) if (!valid(types[i], arguments[i])) throw new IllegalArgumentException("InvalidHookArgument");
            originalCalled = true;
            try { originalResult = XposedBridge.invokeOriginalMethod(param.method, param.thisObject, arguments); return originalResult; }
            catch (InvocationTargetException e) {
                originalError = e.getCause() == null ? e : e.getCause(); throw originalError;
            }
            catch (Throwable e) { originalError = e; throw e; }
        }
    }
    private static boolean valid(Class<?> type, Object value) {
        if (!type.isPrimitive()) return value == null || type.isInstance(value);
        if (value == null) return false;
        return (type == boolean.class && value instanceof Boolean) || (type == char.class && value instanceof Character)
                || (type == byte.class && value instanceof Byte) || (type == short.class && value instanceof Short)
                || (type == int.class && value instanceof Integer) || (type == long.class && value instanceof Long)
                || (type == float.class && value instanceof Float) || (type == double.class && value instanceof Double);
    }
    private static final class Dispatcher extends XC_MethodHook {
        final Member member;
        final List<Registration> callbacks = new ArrayList<>();
        final Unhook handle;
        int inFlight;
        boolean retired;
        final ThreadLocal<Deque<List<Registration>>> frames = new ThreadLocal<Deque<List<Registration>>>() {
            @Override protected Deque<List<Registration>> initialValue() { return new ArrayDeque<>(); }
        };
        Dispatcher(Member member) { this.member = member; handle = XposedBridge.hookMethod(member, this); }
        @Override protected void beforeHookedMethod(MethodHookParam param) {
            List<Registration> snapshot;
            synchronized (HookBridge.class) { inFlight++; snapshot = new ArrayList<>(callbacks); }
            List<Registration> visited = new ArrayList<>(); frames.get().push(visited);
            for (Registration registration : snapshot) {
                if (!registration.context.active() || entered.get().contains(registration)) continue;
                visited.add(registration);
                Call call = new Call(param);
                invoke(registration, registration.before, call);
                if (call.changed) break;
                if (registration.replacement != null) {
                    invoke(registration, registration.replacement, call);
                    if (call.changed) break;
                }
            }
        }
        @Override protected void afterHookedMethod(MethodHookParam param) {
            try {
                List<Registration> visited = frames.get().pop();
                for (int i = visited.size() - 1; i >= 0; i--) {
                    Registration registration = visited.get(i);
                    if (registration.context.active()) invoke(registration, registration.after, new Call(param));
                }
                if (frames.get().isEmpty()) frames.remove();
            } finally {
                synchronized (HookBridge.class) { inFlight--; retire(); }
            }
        }
        private void retire() {
            if (retired || !callbacks.isEmpty() || inFlight != 0) return;
            handle.unhook(); retired = true; dispatchers.remove(member);
        }
        private void invoke(Registration registration, PyObject callback, Call call) {
            if (callback == null) return;
            if (!registration.context.scope.beginCallback()) return;
            call.allowOriginal = callback == registration.replacement;
            entered.get().add(registration);
            try {
                callback.call(call);
                Class<?>[] types = member instanceof Method ? ((Method) member).getParameterTypes() : ((Constructor<?>) member).getParameterTypes();
                for (int i = 0; i < types.length; i++) if (!valid(types[i], call.arguments[i])) throw new IllegalArgumentException("InvalidHookArgument");
                call.param.args = call.arguments;
                if (call.changed) {
                    if (call.throwable != null) call.param.setThrowable(call.throwable); else call.param.setResult(call.result);
                }
            } catch (Exception e) {
                if (call.originalCalled) {
                    if (call.originalError != null) call.param.setThrowable(call.originalError);
                    else call.param.setResult(call.originalResult);
                    call.changed = true;
                } else call.changed = false;
                boolean originalFailure = false;
                Set<Throwable> causes = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
                for (Throwable cause = e; cause != null && causes.add(cause); cause = cause.getCause())
                    if (cause == call.originalError) { originalFailure = true; break; }
                if (!originalFailure) registration.context.failed(e);
            } finally { entered.get().remove(registration); registration.context.scope.endCallback(); }
        }
    }
}
