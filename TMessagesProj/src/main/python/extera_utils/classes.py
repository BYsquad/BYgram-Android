"""Managed Java subclasses backed by the Full client's DexMaker bridge."""
from contextvars import ContextVar
from inspect import Parameter, signature

_invocation = ContextVar("bygram_proxy_invocation", default=None)
_unset = object()


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


def joverride(name=None, arg_types=None):
    def decorate(fn):
        fn._java_override = name or fn.__name__, arg_types
        return fn
    return decorate


def joverload(name, arg_types):
    return joverride(name, arg_types)


def jpreconstructor(arg_types):
    def decorate(callback):
        callback = callback if isinstance(callback, (classmethod, staticmethod)) else classmethod(callback)
        return _constructor_hook("_java_preconstructor", arg_types)(callback)
    return decorate


def jconstructor(arg_types):
    return _constructor_hook("_java_constructor", arg_types)


def _constructor_hook(attribute, arg_types):
    def decorate(callback):
        setattr(getattr(callback, "__func__", callback), attribute, tuple(arg_types))
        return callback
    return decorate


def jclassbuilder():
    def decorate(callback):
        getattr(callback, "__func__", callback)._java_classbuilder = True
        return callback
    return decorate


class _Mvel:
    def __init__(self, name, return_type, arguments, code, override):
        self.name, self.result, self.code, self.override = name, return_type, code, override
        self.names = tuple(name for name, _ in arguments)
        self.types = tuple(kind for _, kind in arguments)
        if len(set(self.names)) != len(self.names):
            raise ValueError("Duplicate MVEL argument")

    def __set_name__(self, owner, name):
        if self.name is None:
            self.name = name

    def __get__(self, instance, owner):
        return self if instance is None else getattr(instance.java, self.name)


def jMVELmethod(name=None, return_type="void", arguments=(), code=""):
    return _Mvel(name, return_type, arguments, code, False)


def jMVELoverride(name=None, arguments=(), code=""):
    return _Mvel(name, None, arguments, code, True)


jmvelmethod, jmveloverride = jMVELmethod, jMVELoverride


def jmethod(name=None, return_type=None, arg_types=None):
    def decorate(fn):
        declared = signature(fn)
        result = declared.return_annotation if return_type is None else return_type
        types = arg_types if arg_types is not None else [
            arg.annotation for arg in list(declared.parameters.values())[1:]
        ]
        if result is Parameter.empty or any(value is Parameter.empty for value in types):
            raise TypeError("jmethod requires Java argument and return types")
        fn._java_method = name or fn.__name__, "void" if result is None else result, types
        return fn
    return decorate


def jgetmethod(name):
    return name, True


def jsetmethod(name):
    return name, False


class jfield:
    def __init__(self, field_type, default=_unset, methods=()):
        self.field_type = field_type
        self.default = default
        self.methods = tuple(methods)

    def __set_name__(self, owner, name):
        self.name = name

    def __get__(self, instance, owner=None):
        return self if instance is None else getattr(instance.java, self.name)

    def __set__(self, instance, value):
        setattr(instance.java, self.name, value)


def _owner():
    from client_utils import _context
    return _context()._bridge


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
    def bind(cls, base, *interfaces, methods=None, constructors=None, custom_name=None):
        return java_subclass(base, *interfaces, methods=methods, constructors=constructors,
                             custom_name=custom_name)(cls)

    @classmethod
    def new_instance(cls, *args, arg_types=None, init_args=None):
        from java import jarray, jclass, jint
        bridge = jclass("org.telegram.messenger.plugins.PluginClassProxy")
        classes = jarray(jclass("java.lang.Class"))
        objects = jarray(jclass("java.lang.Object"))
        methods = jarray(jclass("java.lang.reflect.Method"))
        strings = jarray(jclass("java.lang.String"))
        body = jclass("org.telegram.messenger.plugins.PluginMvel$Body")

        def java_type(value):
            if isinstance(value, str):
                return bridge.type(value)
            return value.getClass() if isinstance(value, type) else value

        base = java_type(cls._java_base)
        interfaces = classes([java_type(value) for value in cls._java_interfaces])
        declared_constructors = None
        if cls._java_constructors is not None:
            signatures = [tuple(map(java_type, spec)) for spec in cls._java_constructors]
            declared_constructors = jarray(classes)([classes(spec) for spec in dict.fromkeys(signatures)])
        if arg_types is None:
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
        if declared_constructors is not None and not any(list(spec) == list(constructor) for spec in declared_constructors):
            raise TypeError("Java constructor is outside constructors=")
        owner = _owner()
        members = {name: value for parent in reversed(cls.__mro__) for name, value in vars(parent).items()}
        def constructor_hook(attribute):
            for name, value in members.items():
                types = getattr(getattr(value, "__func__", value), attribute, None)
                if types is not None and [java_type(kind) for kind in types] == list(constructor):
                    return name
            return None

        parent_args = args
        pre = constructor_hook("_java_preconstructor")
        if pre is not None:
            result = getattr(cls, pre)(*args)
            if result is not None:
                parent_args = result if isinstance(result, (list, tuple)) else (result,)
        fields = {name: value for name, value in members.items() if isinstance(value, jfield)}
        scripts = {name: value for name, value in members.items() if isinstance(value, _Mvel)}
        builders = [name for name, value in members.items()
                    if getattr(getattr(value, "__func__", value), "_java_classbuilder", False)]
        builder = None
        builder_key = None
        if builders:
            from uuid import uuid4
            if "_java_builder_key" not in vars(cls):
                cls._java_builder_key = uuid4().hex
            builder_key = cls._java_builder_key

            def builder(dex, generated, parent):
                for name in builders:
                    callback = getattr(cls, name)
                    spec = signature(callback)
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

        declarations = [(name, result, types, 0) for fn in members.values()
                        if (spec := getattr(fn, "_java_method", None)) is not None
                        for name, result, types in (spec,)]
        declarations.extend((script.name, script.result, script.types, 0)
                            for script in scripts.values() if not script.override)
        supers = [bridge.method(base, interfaces, script.name, classes(list(map(java_type, script.types))))
                  for script in scripts.values() if script.override]
        for index, field in enumerate(fields.values(), 1):
            for name, getter in field.methods:
                declarations.append((name, field.field_type, (), index) if getter
                                    else (name, "void", (field.field_type,), -index))
        if fields or declarations or supers or builder is not None or cls._java_name is not None or declared_constructors is not None:
            generated_constructors = declared_constructors if declared_constructors is not None else jarray(classes)([constructor])
            base = bridge.extend(base, generated_constructors, strings(list(fields)),
                                 classes([java_type(field.field_type) for field in fields.values()]),
                                 strings([name for name, _, _, _ in declarations]),
                                 classes([java_type(result) for _, result, _, _ in declarations]),
                                 jarray(classes)([classes([java_type(value) for value in types])
                                                  for _, _, types, _ in declarations]),
                                 jarray(jint)([accessor for _, _, _, accessor in declarations]),
                                 methods(supers), builder_key, builder, cls._java_name)
        overrides = {}
        for attr, fn in members.items():
            declared = getattr(fn, "_java_method", None)
            spec = getattr(fn, "_java_override", None) if declared is None else (declared[0], declared[2])
            if isinstance(fn, _Mvel):
                spec = fn.name, fn.types
            if spec is not None:
                name, types = spec
                method = bridge.method(base, interfaces, name, None if types is None else classes([java_type(value) for value in types]))
                overrides[str(method.toGenericString())] = method, attr
        for name, types, attr in cls._java_methods:
            method = bridge.method(base, interfaces, name, None if types is None else classes(list(map(java_type, types))))
            if not callable(members.get(attr)):
                raise TypeError(f"Missing Python override {attr!r} for Java method {name!r}")
            overrides.setdefault(str(method.toGenericString()), (method, attr))
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
        instance.java = bridge.create(base, interfaces, methods([method for method, _ in overrides.values()]),
                                      constructor, _arguments(objects, parent_args, constructor), owner, dispatch,
                                      instance, jarray(body)(compiled))
        for name, field in fields.items():
            if field.default is not _unset:
                setattr(instance, name, field.default)
        if cls.__init__ is not object.__init__:
            initializer = instance.__init__
            values = args if init_args is None else init_args
            if init_args is None and values and not signature(initializer).parameters:
                values = ()
            initializer(*values)
        post = constructor_hook("_java_constructor")
        if post is not None:
            getattr(instance, post)(*args)
        if getattr(cls, "on_post_init", None) is not None:
            instance.on_post_init(*args)
        return instance

    @classmethod
    def new_java_instance(cls, *args, **options):
        return cls.new_instance(*args, **options).java

    @classmethod
    def from_java(cls, instance):
        from java import jclass
        peer = jclass("org.telegram.messenger.plugins.PluginClassProxy").peer(instance)
        if not isinstance(peer, cls):
            raise ValueError("Object is not an active proxy of this class")
        return peer

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
