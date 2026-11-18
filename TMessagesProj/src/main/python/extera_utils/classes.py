"""Managed Java subclasses backed by the Full client's DexMaker bridge."""
from contextvars import ContextVar
from inspect import Parameter, signature as inspect_signature

_invocation = ContextVar("bygram_proxy_invocation", default=None)
_unset = object()


def _static(modifiers):
    return "static" in modifiers.replace("|", " ").lower().split()


def java_subclass(base, *interfaces, methods=None, constructors=None, custom_name=None):
    method_entries = tuple(methods.items() if isinstance(methods, dict) else methods or ())
    method_specs = []
    for entry in method_entries:
        if isinstance(entry, str):
            java_name, types, attr = entry, None, entry
        elif hasattr(entry, "getParameterTypes"):
            java_name = attr = str(entry.getName())
            types = tuple(entry.getParameterTypes())
        else:
            java_name, types, *handler = entry
            attr = handler[0] if handler else java_name
            if len(handler) > 1 or not isinstance(java_name, str) or not isinstance(attr, str):
                raise TypeError("Expected (Java method name, argument types[, Python method name])")
        method_specs.append((java_name, None if types is None else tuple(types), attr))
    constructor_specs = None if constructors is None else tuple(
        tuple(entry.getParameterTypes() if hasattr(entry, "getParameterTypes") else entry)
        for entry in constructors
    )

    def decorate(cls):
        if not issubclass(cls, Base):
            raise TypeError("Java subclass must inherit Base")
        cls._java_base, cls._java_interfaces = base, interfaces
        cls._java_name = custom_name
        cls._java_methods = tuple(method_specs)
        cls._java_constructors = constructor_specs
        override_names = {attr for _, _, attr in method_specs}
        override_names.update(name for name, method in vars(cls).items()
                              if getattr(method, "_java_override", None) is not None)
        parent_methods = {
            name: Base.call_super for name in override_names
            if not any(hasattr(parent, name) for parent in cls.__bases__)
        }
        if parent_methods:
            parent = type(f"_{cls.__name__}JavaParent", cls.__bases__, {"__slots__": (), **parent_methods})
            cls.__bases__ = (parent,)
        return cls
    return decorate


def _types(positional, explicit):
    if not positional:
        return explicit
    if explicit is not None:
        raise TypeError("Java argument types specified twice")
    if len(positional) == 1 and (positional[0] is None or isinstance(positional[0], (list, tuple))):
        return positional[0]
    return positional


def joverride(*signature, arg_types=None, modifiers="public", throws=None, name=None):
    if signature and isinstance(signature[0], str):
        name, *signature = signature
    arg_types = _types(signature, arg_types)
    def decorate(fn):
        fn._java_override = name or fn.__name__, arg_types
        fn._java_options = modifiers, throws
        return fn
    return decorate


def joverload(name, arg_types, *, modifiers="public", throws=None):
    return joverride(name, arg_types=arg_types, modifiers=modifiers, throws=throws)


def jpreconstructor(*signature, arg_types=None):
    def decorate(callback):
        callback = callback if isinstance(callback, (classmethod, staticmethod)) else classmethod(callback)
        return _constructor_hook("_java_preconstructor", _types(signature, arg_types))(callback)
    return decorate


def jconstructor(*signature, arg_types=None):
    return _constructor_hook("_java_constructor", _types(signature, arg_types))


def _constructor_hook(attribute, arg_types):
    def decorate(callback):
        fn = getattr(callback, "__func__", callback)
        kinds = arg_types if arg_types is not None else [
            arg.annotation for arg in list(inspect_signature(fn).parameters.values())[1:]
        ]
        if any(kind is Parameter.empty for kind in kinds):
            raise TypeError("Constructor requires Java argument types")
        setattr(fn, attribute, tuple(kinds))
        return callback
    return decorate


def jclassbuilder():
    def decorate(callback):
        getattr(callback, "__func__", callback)._java_classbuilder = True
        return callback
    return decorate


class _Mvel:
    def __init__(self, name, return_type, arguments, code, override, modifiers, throws):
        self.name, self.result, self.code, self.override = name, return_type, code, override
        self.options = modifiers, throws
        self.names = tuple(name for name, _ in arguments)
        self.types = tuple(kind for _, kind in arguments)
        if len(set(self.names)) != len(self.names):
            raise ValueError("Duplicate MVEL argument")

    def __set_name__(self, owner, name):
        if self.name is None:
            self.name = name

    def __get__(self, instance, owner):
        return self if instance is None else getattr(instance.java, self.name)


def jMVELmethod(name=None, return_type=None, arguments=None, code="", *, arg_types=None,
                modifiers="public", throws=None, override=False):
    if arguments is not None and arg_types is not None:
        raise TypeError("Use arguments or arg_types")
    args = arguments if arguments is not None else [(f"arg{i}", kind) for i, kind in enumerate(arg_types or ())]
    return _Mvel(name, return_type or "void", args, code, override, modifiers, throws)


def jMVELoverride(*signature, code="", arguments=None, arg_types=None, modifiers="public", throws=None, name=None):
    if signature and isinstance(signature[0], str):
        name, *signature = signature
    if signature and signature[0] and isinstance(signature[0], (list, tuple)) and isinstance(signature[0][0], (list, tuple)):
        arguments, *signature = signature
        if signature:
            code, = signature
            signature = ()
    return jMVELmethod(name, arguments=arguments, code=code, arg_types=_types(signature, arg_types),
                       modifiers=modifiers, throws=throws, override=True)


jmvelmethod, jmveloverride = jMVELmethod, jMVELoverride


def jmethod(*signature, return_type=None, arg_types=None, modifiers="public", throws=None, name=None):
    if signature and isinstance(signature[0], str) and (
        return_type is not None or len(signature) >= 3
        or len(signature) == 2 and not isinstance(signature[1], (list, tuple))
    ):
        name, *signature = signature
    if signature:
        return_type, *signature = signature
    arg_types = _types(signature, arg_types)
    def decorate(fn):
        descriptor = fn
        fn = getattr(fn, "__func__", fn)
        declared = inspect_signature(fn)
        result = declared.return_annotation if return_type is None else return_type
        first = 0 if _static(modifiers) else 1
        types = arg_types if arg_types is not None else [
            arg.annotation for arg in list(declared.parameters.values())[first:]
        ]
        if result is Parameter.empty or any(value is Parameter.empty for value in types):
            raise TypeError("jmethod requires Java argument and return types")
        fn._java_method = name or fn.__name__, "void" if result is None else result, types
        fn._java_options = modifiers, throws
        return descriptor
    return decorate


def jgetmethod(name=None, *, modifiers="public"):
    return name, True, modifiers


def jsetmethod(name=None, *, modifiers="public"):
    return name, False, modifiers


class jfield:
    def __init__(self, field_type, default=_unset, methods=None, *, modifiers="public"):
        self.field_type = field_type
        self.default = default
        self.methods = tuple(methods or ())
        self.modifiers = modifiers

    def __set_name__(self, owner, name):
        self.name = name

    def __get__(self, instance, owner=None):
        if instance is None:
            return self
        from java import jclass
        return jclass("org.telegram.messenger.plugins.PluginClassProxy").readField(instance.java, self.name)

    def __set__(self, instance, value):
        from java import jclass
        jclass("org.telegram.messenger.plugins.PluginClassProxy").writeField(instance.java, self.name, value)


class JHelper:
    Override = staticmethod(joverride)
    Overload = staticmethod(joverload)
    Method = staticmethod(jmethod)
    MVELMethod = staticmethod(jMVELmethod)
    MVELOverride = staticmethod(jMVELoverride)
    Constructor = staticmethod(jconstructor)
    PreConstructor = staticmethod(jpreconstructor)
    Field = staticmethod(jfield)
    GetMethod = staticmethod(jgetmethod)
    SetMethod = staticmethod(jsetmethod)
    ClassBuilder = staticmethod(jclassbuilder)


def _context():
    from client_utils import _context
    return _context()


def _arguments(array, values, types):
    import java
    if len(values) != len(types):
        raise TypeError("Pass one Java argument type per argument")
    boxed = []
    for kind, value in zip(types, values):
        if kind.isPrimitive():
            primitive = getattr(java, "j" + str(kind.getName()))
            if not isinstance(value, primitive):
                value = primitive(value)
        boxed.append(value)
    return array(boxed)


class Base:
    @classmethod
    def extends(cls, java_class, *interfaces, methods=None, constructors=None, custom_name=None):
        return java_subclass(java_class, *interfaces, methods=methods, constructors=constructors, custom_name=custom_name)

    @classmethod
    def bind(cls, base, *interfaces, methods=None, constructors=None, custom_name=None):
        return java_subclass(base, *interfaces, methods=methods, constructors=constructors,
                             custom_name=custom_name)(cls)

    @classmethod
    def new_instance(cls, *args, arg_types=None, init_args=None):
        return cls._proxy(args, arg_types, init_args)

    @classmethod
    def java_class(cls):
        return cls._proxy((), None, None, class_only=True)

    @classmethod
    def _proxy(cls, args, arg_types, init_args, *, class_only=False, existing=None):
        from java import jarray, jclass, jint
        from java.primitive import Primitive
        bridge = jclass("org.telegram.messenger.plugins.PluginClassProxy")
        classes = jarray(jclass("java.lang.Class"))
        objects = jarray(jclass("java.lang.Object"))
        methods = jarray(jclass("java.lang.reflect.Method"))
        strings = jarray(jclass("java.lang.String"))
        body = jclass("org.telegram.messenger.plugins.PluginMvel$Body")

        def java_type(value):
            if isinstance(value, str):
                return bridge.type(value)
            if isinstance(value, type) and issubclass(value, Primitive):
                return bridge.type(value.name)
            return value.getClass() if isinstance(value, type) else value

        base = java_type(cls._java_base)
        interfaces = classes([java_type(value) for value in cls._java_interfaces])
        declared_constructors = None
        if cls._java_constructors is not None:
            signatures = [tuple(map(java_type, spec)) for spec in cls._java_constructors]
            declared_constructors = jarray(classes)([classes(spec) for spec in dict.fromkeys(signatures)])
        constructor = None
        if class_only or existing is not None:
            pass
        elif arg_types is None:
            def inferred_type(value):
                if value is None:
                    return None
                if type(value) is int:
                    return bridge.type("int" if -(2**31) <= value < 2**31 else "long")
                name = {bool: "boolean", float: "double", str: "java.lang.String"}.get(type(value))
                if name is not None:
                    return bridge.type(name)
                if hasattr(value, "getClass"):
                    return value.getClass()
                raise TypeError("Pass arg_types for this constructor argument")

            inferred = classes([inferred_type(value) for value in args])
            constructor = bridge.constructorTypes(base, inferred, declared_constructors)
        else:
            constructor = classes([java_type(value) for value in arg_types])
        if constructor is not None and declared_constructors is not None and not any(list(spec) == list(constructor) for spec in declared_constructors):
            raise TypeError("Java constructor is outside constructors=")
        context = _context()
        owner = context._bridge
        owner.check()
        members = {name: value for parent in reversed(cls.__mro__) for name, value in vars(parent).items()}
        def constructor_hook(attribute):
            if constructor is None:
                return None
            for name, value in members.items():
                types = getattr(getattr(value, "__func__", value), attribute, None)
                if types is not None and [java_type(kind) for kind in types] == list(constructor):
                    return name
            return None

        parent_args = args
        if constructor is not None:
            result = cls.on_pre_init(*args)
            if result is not None:
                parent_args = result if isinstance(result, (list, tuple)) else (result,)
        pre = constructor_hook("_java_preconstructor")
        if pre is not None:
            result = getattr(cls, pre)(*parent_args)
            if result is not None:
                parent_args = result if isinstance(result, (list, tuple)) else (result,)
        fields = {name: value for name, value in members.items() if isinstance(value, jfield)}
        scripts = {name: value for name, value in members.items() if isinstance(value, _Mvel)}
        builders = [name for name, value in members.items()
                    if getattr(getattr(value, "__func__", value), "_java_classbuilder", False)]
        builder = None
        builder_key = None
        if builders or any(_static(field.modifiers) for field in fields.values()):
            from uuid import uuid4
            if "_java_builder_key" not in vars(cls):
                cls._java_builder_key = uuid4().hex
            builder_key = cls._java_builder_key

        if builders:
            def builder(dex, generated, parent):
                for name in builders:
                    callback = getattr(cls, name)
                    spec = inspect_signature(callback)
                    params = tuple(spec.parameters)
                    prefix = (cls,) if params and params[0] in ("cls", "self") else ()
                    for values in ((dex, generated, parent), (dex, generated), (dex,)):
                        try:
                            spec.bind(*prefix, *values)
                        except TypeError:
                            continue
                        callback(*prefix, *values)
                        break
                    else:
                        raise TypeError("jclassbuilder expects DexMaker, generated TypeId and optional parent TypeId")

        declarations = []
        sources = []
        routes = {}
        static_methods = {}
        for attr, descriptor in members.items():
            fn = getattr(descriptor, "__func__", descriptor)
            spec = getattr(fn, "_java_method", None)
            override = getattr(fn, "_java_override", None)
            options = getattr(fn, "_java_options", ("public", None))
            if isinstance(fn, _Mvel):
                spec = None if fn.override else (fn.name, fn.result, fn.types)
                override = (fn.name, fn.types) if fn.override else None
                options = fn.options
            source = None
            modifiers, throws = options
            if override is not None and (options != ("public", None) or isinstance(fn, _Mvel)):
                name, types = override
                source = bridge.method(base, interfaces, name, None if types is None else classes(list(map(java_type, types))))
                spec = name, source.getReturnType(), source.getParameterTypes()
                if throws is None:
                    throws = source.getExceptionTypes()
            if spec is not None:
                name, result, types = spec
                index = len(declarations)
                route = None
                if _static(modifiers):
                    static_methods[index] = attr, result
                else:
                    route = "_bygramDispatch" + str(index)
                routes[attr] = None if route is None else (route, types)
                declarations.append((name, result, types, 0, modifiers, throws, route))
                sources.append(source)
        for index, field in enumerate(fields.values(), 1):
            for name, getter, modifiers in field.methods:
                name = name or ("get" if getter else "set") + field.name[:1].upper() + field.name[1:]
                declarations.append((name, field.field_type if getter else "void",
                                     () if getter else (field.field_type,), index if getter else -index,
                                     modifiers, None, None))
                sources.append(None)
        def exceptions(value):
            if value is None:
                return classes([])
            values = value if not isinstance(value, (str, type)) and hasattr(value, "__iter__") else (value,)
            return classes(list(map(java_type, values)))

        static_callback = None
        if static_methods:
            from uuid import uuid4
            if "_java_builder_key" not in vars(cls):
                cls._java_builder_key = uuid4().hex
            builder_key = cls._java_builder_key
            static_scripts = {
                index: body(scripts[attr].code, strings(scripts[attr].names))
                for index, (attr, _) in static_methods.items() if attr in scripts
            }

            def static_callback(java_class, index, values):
                attr, result = static_methods[index]
                if index in static_scripts:
                    return static_scripts[index].runStatic(java_class, objects(values), java_type(result))
                return getattr(cls, attr)(*values)

        if fields or declarations or builder is not None or cls._java_name is not None or declared_constructors is not None:
            generated_constructors = declared_constructors if declared_constructors is not None else bridge.constructors(base)
            base = bridge.extend(base, generated_constructors, strings(list(fields)),
                                 classes([java_type(field.field_type) for field in fields.values()]),
                                 strings([field.modifiers for field in fields.values()]),
                                 jarray(jclass("com.chaquo.python.PyObject"))([
                                     field.default if _static(field.modifiers) and field.default is not _unset else None
                                     for field in fields.values()
                                 ]),
                                 strings([entry[0] for entry in declarations]),
                                 classes([java_type(entry[1]) for entry in declarations]),
                                 jarray(classes)([classes([java_type(value) for value in types])
                                                  for _, _, types, *_ in declarations]),
                                 jarray(jint)([entry[3] for entry in declarations]),
                                 strings([entry[4] for entry in declarations]),
                                 jarray(classes)([exceptions(entry[5]) for entry in declarations]),
                                 strings([entry[6] for entry in declarations]),
                                 methods(sources), builder_key, builder, cls._java_name, owner, static_callback)
        overrides = {}
        for attr, descriptor in members.items():
            fn = getattr(descriptor, "__func__", descriptor)
            declared = getattr(fn, "_java_method", None)
            spec = getattr(fn, "_java_override", None) if declared is None else (declared[0], declared[2])
            if isinstance(fn, _Mvel):
                spec = fn.name, fn.types
            spec = routes.get(attr, spec)
            if spec is not None:
                name, types = spec
                method = bridge.method(base, interfaces, name, None if types is None else classes([java_type(value) for value in types]))
                overrides[str(method.toGenericString())] = method, attr
        for name, types, attr in cls._java_methods:
            method = bridge.method(base, interfaces, name, None if types is None else classes(list(map(java_type, types))))
            if not callable(members.get(attr)):
                raise TypeError(f"Missing Python override {attr!r} for Java method {name!r}")
            overrides.setdefault(str(method.toGenericString()), (method, attr))
        override_methods = methods([method for method, _ in overrides.values()])
        if class_only:
            return bridge.proxyClass(base, interfaces, override_methods)
        instance = cls.__new__(cls)

        def dispatch(proxy, method, values):
            key = str(method.toGenericString())
            match = overrides.get(key)
            if match is None:
                return bridge.callSuper(proxy, method, values)
            token = _invocation.set((instance, proxy, method, values, objects, bridge))
            try:
                return getattr(instance, match[1])(*values)
            finally:
                _invocation.reset(token)

        compiled = []
        for _, attr in overrides.values():
            script = scripts.get(attr)
            compiled.append(None if script is None else body(script.code, strings(script.names)))
        instance.java = bridge.create(base, interfaces, override_methods, constructor,
                                      None if constructor is None else _arguments(objects, parent_args, constructor),
                                      owner, dispatch, instance, jarray(body)(compiled), existing)
        if instance.java is None:
            return cls._existing_peer(bridge.peer(existing))
        instance._proxy_context = context
        context.__dict__.setdefault("_proxy_peers", {})[id(instance)] = instance
        try:
            if existing is None:
                for name, field in fields.items():
                    if field.default is not _unset and not _static(field.modifiers):
                        setattr(instance, name, field.default)
            if cls.__init__ is not object.__init__:
                initializer = instance.__init__
                values = args if init_args is None else init_args
                if init_args is None and values and not inspect_signature(initializer).parameters:
                    values = ()
                initializer(*values)
            post = constructor_hook("_java_constructor")
            if post is not None:
                getattr(instance, post)(*args)
            instance.on_post_init(*args)
        except BaseException:
            context._proxy_peers.pop(id(instance), None)
            bridge.release(instance.java, owner)
            raise
        return instance

    @classmethod
    def new_java_instance(cls, *args, **options):
        return cls.new_instance(*args, **options).java

    @classmethod
    def from_java(cls, instance, *, init_args=None):
        from java import jclass
        peer = jclass("org.telegram.messenger.plugins.PluginClassProxy").peer(instance)
        if peer is not None:
            return cls._existing_peer(peer)
        return cls._proxy((), None, init_args, existing=instance)

    @classmethod
    def _existing_peer(cls, peer):
        if peer is not None and not isinstance(peer, cls):
            raise ValueError("Object is not a proxy of this class")
        return peer

    @classmethod
    def on_pre_init(cls, *args):
        pass

    def on_post_init(self, *args):
        pass

    def on_release(self):
        pass

    def jrelease(self):
        from java import jclass
        context = self._proxy_context
        if context._proxy_peers.pop(id(self), None) is None:
            return
        try:
            self.on_release()
        finally:
            jclass("org.telegram.messenger.plugins.PluginClassProxy").release(self.java, context._bridge)

    @property
    def this(self):
        return self.java

    def call_super(self, *args):
        current = _invocation.get()
        if current is None or current[0] is not self:
            raise RuntimeError("call_super requires an active Java override")
        _, proxy, method, original, objects, bridge = current
        values = _arguments(objects, args, method.getParameterTypes()) if args else original
        return bridge.callSuper(proxy, method, values)

    def __getattr__(self, name):
        return getattr(object.__getattribute__(self, "java"), name)
