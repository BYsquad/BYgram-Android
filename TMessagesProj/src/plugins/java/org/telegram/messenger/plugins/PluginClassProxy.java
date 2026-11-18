package org.telegram.messenger.plugins;

import com.android.dx.Code;
import com.android.dx.DexMaker;
import com.android.dx.FieldId;
import com.android.dx.Local;
import com.android.dx.MethodId;
import com.android.dx.PluginDexMetadata;
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
    private static final Map<Object, Registration> registrations = new java.util.IdentityHashMap<>();

    private static final class Registration {
        final PluginContext owner;
        final AutoCloseable handle;
        Registration(PluginContext owner, AutoCloseable handle) { this.owner = owner; this.handle = handle; }
    }

    public static synchronized PyObject peer(Object instance) {
        return peers.get(instance);
    }

    public static void release(Object instance, PluginContext owner) throws Exception {
        Registration registration;
        synchronized (PluginClassProxy.class) { registration = registrations.get(instance); }
        if (registration != null && registration.owner == owner) registration.handle.close();
    }
    private static final Map<List<?>, Class<?>> shapes = new LinkedHashMap<>();
    private interface StaticHandler {
        Object invoke(int index, Object[] arguments);
    }
    private static final Map<Class<?>, StaticHandler> staticHandlers = new java.util.concurrent.ConcurrentHashMap<>();

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
        ClassLoader parent = base.getClassLoader();
        return parent == null || parent == Object.class.getClassLoader()
                ? ApplicationLoader.applicationContext.getClassLoader() : parent;
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
            String[] fields, Class<?>[] fieldTypes, String[] fieldFlags, PyObject[] staticDefaults, String[] methods,
            Class<?>[] returnTypes, Class<?>[][] parameters, int[] accessors, String[] methodFlags,
            Class<?>[][] exceptions, String[] routes, Method[] supers,
            String builderKey, PyObject builder, String customName, PluginContext owner, PyObject staticCallback) throws Exception {
        List<?> key = Arrays.asList(base, Arrays.stream(constructors).map(Arrays::asList).collect(java.util.stream.Collectors.toList()), Arrays.asList(fields),
                Arrays.asList(fieldTypes), Arrays.asList(methods), Arrays.asList(returnTypes),
                Arrays.stream(parameters).map(Arrays::asList).collect(java.util.stream.Collectors.toList()),
                Arrays.toString(accessors), Arrays.asList(fieldFlags), Arrays.asList(methodFlags),
                Arrays.deepToString(exceptions), Arrays.asList(routes), Arrays.asList(supers), builderKey, customName);
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
        Map<MethodId<?, ?>, Class<?>[]> declaredExceptions = new LinkedHashMap<>();
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
        for (int i = 0; i < fields.length; i++) {
            Object value = staticDefaults[i] == null ? null : staticDefaults[i].toJava(fieldTypes[i]);
            dex.declare(generated.getField(TypeId.get(fieldTypes[i]), fields[i]), flags(fieldFlags[i]), value);
        }
        for (int i = 0; i < methods.length; i++) {
            TypeId<?> result = TypeId.get(returnTypes[i]);
            TypeId<?>[] args = dexTypes(parameters[i]);
            MethodId<Object, ?> method = generated.getMethod(result, methods[i], args);
            int flags = flags(methodFlags[i]);
            Code body = dex.declare(method, flags);
            if (exceptions[i].length != 0) declaredExceptions.put(method, exceptions[i]);
            int accessor = accessors[i];
            if (accessor != 0) {
                int field = Math.abs(accessor) - 1;
                emitAccessor(body, generated, TypeId.get(fieldTypes[field]), fields[field], accessor > 0,
                        Modifier.isStatic(flags), Modifier.isStatic(flags(fieldFlags[field])));
            } else if (Modifier.isStatic(flags)) {
                if (supers[i] != null) throw new IllegalArgumentException("A Java override cannot be static");
                emitStatic(body, generated, i, result, parameters[i], returnTypes[i]);
            } else {
                MethodId<Object, ?> route = generated.getMethod(result, routes[i], args);
                Code fallback = dex.declare(route, Modifier.PUBLIC);
                forward(body, generated, route, args, returnTypes[i] == void.class, false);
                Method source = supers[i];
                if (source != null && !Modifier.isAbstract(source.getModifiers())) {
                    if (Modifier.isPrivate(flags) || Modifier.isPublic(source.getModifiers()) && !Modifier.isPublic(flags))
                        throw new IllegalArgumentException("An override cannot reduce Java visibility");
                    forward(fallback, generated, parent.getMethod(result, source.getName(), args), args,
                            returnTypes[i] == void.class, true);
                    emitSuper(dex, generated, parent, source);
                } else if (returnTypes[i] == void.class) fallback.returnVoid();
                else returnDefault(fallback, result, defaultValue(returnTypes[i]));
            }
        }
        if (builder != null) builder.call(dex, generated, parent);
        PluginDexMetadata.addThrows(dex, generated, declaredExceptions);
        java.io.File cache = new java.io.File(ApplicationLoader.applicationContext.getCodeCacheDir(), "plugin-shapes");
        if (!cache.isDirectory() && !cache.mkdirs()) throw new java.io.IOException("ProxyCacheUnavailable");
        Class<?> result = dex.generateAndLoad(loader(base), cache).loadClass(name);
        if (staticCallback != null) {
            StaticHandler handler = (index, arguments) -> {
                if (!owner.scope.beginCallback()) return null;
                try {
                    PyObject value = staticCallback.call(result, index, arguments);
                    return value == null || returnTypes[index] == void.class ? null : value.toJava(returnTypes[index]);
                } finally {
                    owner.scope.endCallback();
                }
            };
            staticHandlers.put(result, handler);
            owner.scope.own(() -> staticHandlers.remove(result, handler));
        }
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

    private static int flags(String value) {
        int flags = 0;
        for (String word : value.trim().toLowerCase(java.util.Locale.ROOT).split("[\\s|]+")) {
            switch (word) {
                case "public": flags |= Modifier.PUBLIC; break;
                case "protected": flags |= Modifier.PROTECTED; break;
                case "private": flags |= Modifier.PRIVATE; break;
                case "static": flags |= Modifier.STATIC; break;
                case "final": flags |= Modifier.FINAL; break;
                case "volatile": flags |= Modifier.VOLATILE; break;
                case "transient": flags |= Modifier.TRANSIENT; break;
                case "synchronized": flags |= Modifier.SYNCHRONIZED; break;
                case "": case "package": break;
                default: throw new IllegalArgumentException("Unknown Java modifier " + word);
            }
        }
        return flags;
    }

    public static Object invokeStatic(Class<?> receiver, int method, Object[] values, Class<?> result) {
        StaticHandler handler = staticHandlers.get(receiver);
        Object value = handler == null ? null : handler.invoke(method, values);
        return value == null ? defaultValue(result) : value;
    }

    private static final Map<Class<?>, Class<?>> boxedTypes = java.util.Map.of(
            boolean.class, Boolean.class, byte.class, Byte.class, char.class, Character.class, short.class, Short.class,
            int.class, Integer.class, long.class, Long.class, float.class, Float.class, double.class, Double.class);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> void emitStatic(Code body, TypeId<Object> generated, int method, TypeId<T> type,
            Class<?>[] parameters, Class<?> resultType) {
        TypeId<Object[]> objects = TypeId.get(Object[].class);
        TypeId<Class> clazz = TypeId.get(Class.class);
        Local<Class> receiver = body.newLocal(clazz);
        Local<Class> resultClass = body.newLocal(clazz);
        Local<Integer> index = body.newLocal(TypeId.INT);
        Local<Integer> methodIndex = body.newLocal(TypeId.INT);
        Local<Object[]> values = body.newLocal(objects);
        Local<Object> result = body.newLocal(TypeId.OBJECT);
        Local<T> returned = resultType == void.class ? null : body.newLocal(type);
        TypeId boxedResult = resultType.isPrimitive() && resultType != void.class ? TypeId.get(boxedTypes.get(resultType)) : null;
        Local unbox = boxedResult == null ? null : body.newLocal(boxedResult);
        Local<?>[] arguments = new Local<?>[parameters.length];
        Local<?>[] boxed = new Local<?>[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            arguments[i] = body.getParameter(i, TypeId.get(parameters[i]));
            if (parameters[i].isPrimitive()) boxed[i] = body.newLocal(TypeId.get(boxedTypes.get(parameters[i])));
        }
        body.loadConstant((Local) receiver, generated);
        if (resultType.isPrimitive()) {
            Class<?> wrapper = resultType == void.class ? Void.class : boxedTypes.get(resultType);
            body.sget(TypeId.get(wrapper).getField(clazz, "TYPE"), resultClass);
        } else body.loadConstant(resultClass, resultType);
        body.loadConstant(methodIndex, method);
        body.loadConstant(index, parameters.length);
        body.newArray(values, index);
        for (int i = 0; i < parameters.length; i++) {
            body.loadConstant(index, i);
            if (boxed[i] != null) {
                TypeId box = TypeId.get(boxedTypes.get(parameters[i]));
                body.invokeStatic(box.getMethod(box, "valueOf", TypeId.get(parameters[i])), boxed[i], arguments[i]);
            }
            body.aput(values, index, boxed[i] == null ? arguments[i] : boxed[i]);
        }
        body.invokeStatic(TypeId.get(PluginClassProxy.class).getMethod(TypeId.OBJECT, "invokeStatic",
                clazz, TypeId.INT, objects, clazz), result, receiver, methodIndex, values, resultClass);
        if (returned == null) {
            body.returnVoid();
            return;
        }
        if (unbox == null) body.cast(returned, result);
        else {
            body.cast(unbox, result);
            body.invokeVirtual(boxedResult.getMethod(type, resultType.getName() + "Value"), returned, unbox);
        }
        body.returnValue(returned);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> void forward(Code code, TypeId<Object> generated, MethodId<?, T> method,
            TypeId<?>[] types, boolean returnsVoid, boolean callSuper) {
        Local<?>[] arguments = new Local<?>[types.length];
        for (int i = 0; i < arguments.length; i++) arguments[i] = code.getParameter(i, types[i]);
        Local<T> result = returnsVoid ? null : code.newLocal(method.getReturnType());
        if (callSuper) code.invokeSuper((MethodId) method, result, code.getThis(generated), arguments);
        else code.invokeVirtual((MethodId) method, result, code.getThis(generated), arguments);
        if (returnsVoid) code.returnVoid(); else code.returnValue(result);
    }

    private static <T> void emitAccessor(Code body, TypeId<Object> owner, TypeId<T> type, String name,
            boolean getter, boolean staticAccessor, boolean staticField) {
        FieldId<Object, T> field = owner.getField(type, name);
        if (staticAccessor && !staticField) throw new IllegalArgumentException("Static accessor needs a static field");
        Local<Object> self = staticAccessor ? null : body.getThis(owner);
        if (getter) {
            Local<T> value = body.newLocal(type);
            if (staticField) body.sget(field, value); else body.iget(field, value, self);
            body.returnValue(value);
        } else {
            if (staticField) body.sput(field, body.getParameter(0, type));
            else body.iput(field, self, body.getParameter(0, type));
            body.returnVoid();
        }
    }

    private static java.lang.reflect.Field field(Object instance, String name) throws NoSuchFieldException {
        java.lang.reflect.Field result = instance.getClass().getSuperclass().getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    public static Object readField(Object instance, String name) throws ReflectiveOperationException {
        return field(instance, name).get(instance);
    }

    public static void writeField(Object instance, String name, PyObject value) throws ReflectiveOperationException {
        java.lang.reflect.Field target = field(instance, name);
        target.set(instance, value == null ? null : value.toJava(target.getType()));
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

    private static ProxyBuilder<?> builder(Class<?> base, Class<?>[] interfaces, Method[] methods) {
        return ProxyBuilder.forClass(base).parentClassLoader(loader(base))
                .dexCache(ApplicationLoader.applicationContext.getCodeCacheDir())
                .implementing(interfaces).onlyMethods(methods);
    }

    public static Class<?> proxyClass(Class<?> base, Class<?>[] interfaces, Method[] methods) throws java.io.IOException {
        return builder(base, interfaces, methods).buildProxyClass();
    }

    public static Class<?>[][] constructors(Class<?> base) {
        return Arrays.stream(base.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()) || Modifier.isProtected(constructor.getModifiers()))
                .map(java.lang.reflect.Constructor::getParameterTypes).toArray(Class<?>[][]::new);
    }

    public static Object create(Class<?> base, Class<?>[] interfaces, Method[] methods, Class<?>[] constructor,
                                Object[] arguments, PluginContext owner, PyObject callback, PyObject peer,
                                PluginMvel.Body[] bodies, Object existing) throws Exception {
        owner.check();
        ProxyBuilder<?> factory = builder(base, interfaces, methods);
        Object instance = existing;
        if (instance == null) {
            // The constructor runs before the managed handler is attached.
            instance = factory.constructorArgTypes(constructor).constructorArgValues(arguments)
                    .handler(PluginClassProxy::fallback).build();
        } else if (instance.getClass() != factory.buildProxyClass()) {
            throw new IllegalArgumentException("InvalidProxyClass");
        }
        final Object proxyInstance = instance;
        final java.util.concurrent.atomic.AtomicReference<PyObject[]> active = new java.util.concurrent.atomic.AtomicReference<>(new PyObject[]{callback, peer});
        Map<Method, PluginMvel.Body> scripts = new LinkedHashMap<>();
        for (int i = 0; i < methods.length; i++) if (bodies[i] != null) scripts.put(methods[i], bodies[i]);
        synchronized (PluginClassProxy.class) {
            if (existing != null && ProxyBuilder.getInvocationHandler(proxyInstance) != null) return null;
            AutoCloseable handle = owner.scope.own(() -> {
                active.set(null);
                synchronized (PluginClassProxy.class) {
                    peers.remove(proxyInstance);
                    registrations.remove(proxyInstance);
                    ProxyBuilder.setInvocationHandler(proxyInstance, PluginClassProxy::fallback);
                }
            });
            peers.put(proxyInstance, peer);
            registrations.put(proxyInstance, new Registration(owner, handle));
            ProxyBuilder.setInvocationHandler(proxyInstance, (proxy, method, args) -> {
                if (!owner.scope.beginCallback()) return fallback(proxy, method, args);
                try {
                    PyObject[] callbacks = active.get();
                    if (callbacks == null) return fallback(proxy, method, args);
                    Object[] values = args == null ? new Object[0] : args;
                    PluginMvel.Body script = scripts.get(method);
                    if (script != null) return script.run(proxy, callbacks[1], values, method.getReturnType());
                    PyObject result = callbacks[0].call(proxy, method, values);
                    return method.getReturnType() == void.class || result == null ? null : result.toJava(method.getReturnType());
                } finally {
                    owner.scope.endCallback();
                }
            });
        }
        return proxyInstance;
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
