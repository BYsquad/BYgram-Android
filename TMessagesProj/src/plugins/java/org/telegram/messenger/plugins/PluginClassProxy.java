package org.telegram.messenger.plugins;

import com.android.dx.Code;
import com.android.dx.DexMaker;
import com.android.dx.FieldId;
import com.android.dx.Local;
import com.android.dx.TypeId;
import com.android.dx.stock.ProxyBuilder;
import com.chaquo.python.PyObject;
import org.telegram.messenger.ApplicationLoader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PluginClassProxy {
    private static final Map<Object, PyObject> peers = new java.util.IdentityHashMap<>();

    public static synchronized PyObject peer(Object instance) {
        return peers.get(instance);
    }
    private static final Map<List<?>, Class<?>> shapes = new LinkedHashMap<>();

    private PluginClassProxy() {}

    public static Class<?> type(String name) throws ClassNotFoundException {
        switch (name) {
            case "void": return void.class;
            case "boolean": return boolean.class;
            case "byte": return byte.class;
            case "char": return char.class;
            case "short": return short.class;
            case "int": return int.class;
            case "long": return long.class;
            case "float": return float.class;
            case "double": return double.class;
            default:
                if (name.endsWith("[]")) return java.lang.reflect.Array.newInstance(type(name.substring(0, name.length() - 2)), 0).getClass();
                return Class.forName(name, false, ApplicationLoader.applicationContext.getClassLoader());
        }
    }

    private static TypeId<?>[] dexTypes(Class<?>[] classes) {
        return Arrays.stream(classes).map(TypeId::get).toArray(TypeId<?>[]::new);
    }

    private static ClassLoader loader(Class<?> base) {
        return base.getClassLoader() == null
                ? ApplicationLoader.applicationContext.getClassLoader() : base.getClassLoader();
    }

    public static Class<?>[] constructorTypes(Class<?> base, Class<?>[] supplied, Class<?>[][] allowed) throws NoSuchMethodException {
        Class<?>[] best = null;
        boolean ambiguous = false;
        int bestScore = Integer.MAX_VALUE;
        for (java.lang.reflect.Constructor<?> candidate : base.getDeclaredConstructors()) {
            int flags = candidate.getModifiers();
            if (!(Modifier.isPublic(flags) || Modifier.isProtected(flags))) continue;
            Class<?>[] types = candidate.getParameterTypes();
            if (types.length != supplied.length) continue;
            if (allowed != null && Arrays.stream(allowed).noneMatch(spec -> Arrays.equals(spec, types))) continue;
            int score = 0;
            for (int i = 0; i < types.length; i++) {
                if (types[i] == supplied[i]) continue;
                if (types[i].isPrimitive() || supplied[i] != null && !types[i].isAssignableFrom(supplied[i])) {
                    score = Integer.MAX_VALUE;
                    break;
                }
                score++;
            }
            if (score == Integer.MAX_VALUE) continue;
            if (score < bestScore) {
                best = types;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }
        if (best == null || ambiguous) throw new NoSuchMethodException(base.getName() + " constructor needs explicit arg_types");
        return best;
    }

    public static synchronized Class<?> extend(Class<?> base, Class<?>[][] constructors,
            String[] fields, Class<?>[] fieldTypes, String[] methods,
            Class<?>[] returnTypes, Class<?>[][] parameters, int[] accessors, Method[] supers,
            String builderKey, PyObject builder, String customName) throws Exception {
        List<?> key = Arrays.asList(base, Arrays.stream(constructors).map(Arrays::asList).collect(java.util.stream.Collectors.toList()), Arrays.asList(fields),
                Arrays.asList(fieldTypes), Arrays.asList(methods), Arrays.asList(returnTypes),
                Arrays.stream(parameters).map(Arrays::asList).collect(java.util.stream.Collectors.toList()),
                Arrays.toString(accessors), Arrays.asList(supers), builderKey, customName);
        Class<?> existing = shapes.get(key);
        if (existing != null) return existing;
        if (!Modifier.isPublic(base.getModifiers()) || Modifier.isFinal(base.getModifiers()))
            throw new IllegalArgumentException("InaccessibleProxySuperclass");

        String fingerprint = java.util.UUID.nameUUIDFromBytes(
                key.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "");
        String label = customName == null ? "Shape" : customName.substring(0, Math.min(80, customName.length()))
                .replaceAll("[^A-Za-z0-9_$]", "_") + "_";
        String name = "org.telegram.messenger.plugins.generated.Proxy_" + label + fingerprint;
        TypeId<Object> generated = TypeId.get("L" + name.replace('.', '/') + ";");
        TypeId<?> parent = TypeId.get(base);
        DexMaker dex = new DexMaker();
        dex.declare(generated, "PluginClassProxy", Modifier.PUBLIC, parent);
        for (Class<?>[] constructor : constructors) {
            int flags = base.getDeclaredConstructor(constructor).getModifiers();
            if (!(Modifier.isPublic(flags) || Modifier.isProtected(flags)))
                throw new IllegalArgumentException("InaccessibleProxyConstructor");
            TypeId<?>[] argumentTypes = dexTypes(constructor);
            Code init = dex.declare(generated.getConstructor(argumentTypes), Modifier.PUBLIC);
            Local<?>[] arguments = new Local<?>[constructor.length];
            for (int i = 0; i < arguments.length; i++) arguments[i] = init.getParameter(i, argumentTypes[i]);
            forwardConstructor(init, parent, argumentTypes, init.getThis(generated), arguments);
            init.returnVoid();
        }
        for (int i = 0; i < fields.length; i++)
            dex.declare(generated.getField(TypeId.get(fieldTypes[i]), fields[i]), Modifier.PUBLIC, null);
        for (int i = 0; i < methods.length; i++) {
            TypeId<?> result = TypeId.get(returnTypes[i]);
            Code body = dex.declare(generated.getMethod(result, methods[i], dexTypes(parameters[i])), Modifier.PUBLIC);
            int accessor = accessors[i];
            if (accessor != 0) {
                int field = Math.abs(accessor) - 1;
                emitAccessor(body, generated, TypeId.get(fieldTypes[field]), fields[field], accessor > 0);
            } else if (returnTypes[i] == void.class) body.returnVoid();
            else returnDefault(body, result, defaultValue(returnTypes[i]));
        }
        for (Method method : supers) {
            if (!Modifier.isAbstract(method.getModifiers())) emitSuper(dex, generated, parent, method);
        }
        if (builder != null) builder.call(dex, generated, parent);
        java.io.File cache = new java.io.File(ApplicationLoader.applicationContext.getCodeCacheDir(), "plugin-shapes");
        if (!cache.isDirectory() && !cache.mkdirs()) throw new java.io.IOException("ProxyCacheUnavailable");
        Class<?> result = dex.generateAndLoad(loader(base), cache).loadClass(name);
        shapes.put(key, result);
        return result;
    }

    private static <T> void emitSuper(DexMaker dex, TypeId<Object> generated, TypeId<?> parent, Method method) {
        @SuppressWarnings("unchecked") TypeId<T> result = (TypeId<T>) TypeId.get(method.getReturnType());
        TypeId<?>[] types = dexTypes(method.getParameterTypes());
        Code body = dex.declare(generated.getMethod(result, "SUPER_" + method.getName(), types), Modifier.PUBLIC | Modifier.FINAL);
        Local<?>[] args = new Local<?>[types.length];
        for (int i = 0; i < types.length; i++) args[i] = body.getParameter(i, types[i]);
        Local<T> value = method.getReturnType() == void.class ? null : body.newLocal(result);
        body.invokeSuper(parent.getMethod(result, method.getName(), types), value, body.getThis(generated), args);
        if (value == null) body.returnVoid(); else body.returnValue(value);
    }

    private static <T> void emitAccessor(Code body, TypeId<Object> owner, TypeId<T> type, String name, boolean getter) {
        FieldId<Object, T> field = owner.getField(type, name);
        Local<Object> self = body.getThis(owner);
        if (getter) {
            Local<T> value = body.newLocal(type);
            body.iget(field, value, self);
            body.returnValue(value);
        } else {
            body.iput(field, self, body.getParameter(0, type));
            body.returnVoid();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void forwardConstructor(Code code, TypeId parent, TypeId<?>[] types, Local self, Local<?>[] args) {
        code.invokeDirect(parent.getConstructor(types), null, self, args);
    }

    @SuppressWarnings("unchecked")
    private static <T> void returnDefault(Code code, TypeId<T> type, Object value) {
        Local<T> result = code.newLocal(type);
        code.loadConstant(result, (T) value);
        code.returnValue(result);
    }

    public static Method method(Class<?> base, Class<?>[] interfaces, String name, Class<?>[] parameters) throws NoSuchMethodException {
        Map<String, Method> matches = new LinkedHashMap<>();
        for (Class<?> type = base; type != null; type = type.getSuperclass()) {
            for (Method candidate : type.getDeclaredMethods()) collect(matches, candidate, name, parameters);
        }
        for (Method candidate : base.getMethods()) collect(matches, candidate, name, parameters);
        for (Class<?> type : interfaces) for (Method candidate : type.getMethods()) collect(matches, candidate, name, parameters);
        if (matches.size() != 1) throw new NoSuchMethodException(name + (matches.isEmpty() ? " unavailable" : " needs argument types"));
        Method target = matches.values().iterator().next();
        if (Modifier.isFinal(target.getModifiers())) throw new NoSuchMethodException(name + " is final");
        return target;
    }

    private static void collect(Map<String, Method> matches, Method candidate, String name, Class<?>[] parameters) {
        int flags = candidate.getModifiers();
        if (candidate.getName().equals(name) && (Modifier.isPublic(flags) || Modifier.isProtected(flags))
                && !Modifier.isStatic(flags) && !candidate.isBridge() && !candidate.isSynthetic()
                && (parameters == null || Arrays.equals(parameters, candidate.getParameterTypes())))
            matches.putIfAbsent(Arrays.toString(candidate.getParameterTypes()), candidate);
    }

    public static Object create(Class<?> base, Class<?>[] interfaces, Method[] methods, Class<?>[] constructor,
                                Object[] arguments, PluginContext owner, PyObject callback, PyObject peer,
                                PluginMvel.Body[] bodies) throws Exception {
        if (base.isInterface() || Modifier.isFinal(base.getModifiers()) || callback == null || owner == null)
            throw new IllegalArgumentException("InvalidProxyClass");
        owner.check();
        java.util.concurrent.atomic.AtomicReference<PyObject> active = new java.util.concurrent.atomic.AtomicReference<>(callback);
        java.util.concurrent.atomic.AtomicReference<PyObject> activePeer = new java.util.concurrent.atomic.AtomicReference<>(peer);
        java.util.concurrent.atomic.AtomicReference<Object> activeInstance = new java.util.concurrent.atomic.AtomicReference<>();
        Map<Method, PluginMvel.Body> scripts = new LinkedHashMap<>();
        for (int i = 0; i < methods.length; i++) if (bodies[i] != null) scripts.put(methods[i], bodies[i]);
        AutoCloseable registration = owner.scope.own(() -> {
            active.set(null);
            activePeer.set(null);
            synchronized (PluginClassProxy.class) {
                peers.remove(activeInstance.getAndSet(null));
            }
        });
        try {
            Object instance = ProxyBuilder.forClass(base)
                .parentClassLoader(loader(base))
                .dexCache(ApplicationLoader.applicationContext.getCodeCacheDir())
                .implementing(interfaces)
                .onlyMethods(methods)
                .constructorArgTypes(constructor)
                .constructorArgValues(arguments)
                .handler((proxy, method, args) -> {
                    if (!owner.scope.beginCallback()) return fallback(proxy, method, args);
                    try {
                        PyObject function = active.get();
                        if (function == null) return fallback(proxy, method, args);
                        PluginMvel.Body script = scripts.get(method);
                        if (script != null)
                            return script.run(proxy, activePeer.get(), args == null ? new Object[0] : args, method.getReturnType());
                        PyObject result = function.call(proxy, method, args == null ? new Object[0] : args);
                        return method.getReturnType() == void.class || result == null ? null : result.toJava(method.getReturnType());
                    } finally {
                        owner.scope.endCallback();
                    }
                }).build();
            synchronized (PluginClassProxy.class) {
                if (activePeer.get() != null) {
                    activeInstance.set(instance);
                    peers.put(instance, peer);
                }
            }
            return instance;
        } catch (Exception | LinkageError failure) {
            registration.close();
            throw failure;
        }
    }

    private static Object fallback(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (!Modifier.isAbstract(method.getModifiers())) return ProxyBuilder.callSuper(proxy, method, arguments);
        return defaultValue(method.getReturnType());
    }

    private static Object defaultValue(Class<?> type) {
        return type.isPrimitive() && type != void.class ? java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(type, 1), 0) : null;
    }

    public static Object callSuper(Object proxy, Method method, Object[] arguments) throws Throwable {
        return ProxyBuilder.callSuper(proxy, method, arguments);
    }
}
