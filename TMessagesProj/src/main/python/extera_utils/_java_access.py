"""Attribute modifiers for Chaquopy Java objects and classes."""
from functools import lru_cache

_persistent = {
    alias: (flag, value)
    for long, short, flag, value in (
        ("JUseGetterAndSetter", "JGS", "beans", True),
        ("JNotUseGetterAndSetter", "JNGS", "beans", False),
        ("JAccessAll", "JA", "private", True),
        ("JNotAccessAll", "JNA", "private", False),
    )
    for alias in (long, short)
}
_safe = {"JSafe", "JS"}
_unsafe = {"JNotSafe", "JNS"}
_ignore = {"JIgnoreResult", "JIR"}
_result = {"JNotIgnoreResult", "JNIR"}
_names = _persistent.keys() | _safe | _unsafe | _ignore | _result


class _Empty:
    def __bool__(self):
        return False

    def __getattr__(self, name):
        return self

    def __call__(self, *args, **kwargs):
        return self


JNone = _Empty()


class _Chain:
    def __init__(self, receiver, safe, ignore):
        self._receiver, self._safe, self._ignore = receiver, safe, ignore

    def _wrap(self, value):
        if self._safe and value is None:
            return JNone
        if (self._safe or self._ignore) and issubclass(type(value), (_object, _meta)):
            return _Chain(value, self._safe, self._ignore)
        return value

    def __getattr__(self, name):
        if name in _safe | _unsafe | _ignore | _result:
            safe = name in _safe or self._safe and name not in _unsafe
            ignore = name in _ignore or self._ignore and name not in _result
            return _Chain(self._receiver, safe, ignore) if safe or ignore else self._receiver
        try:
            value = getattr(self._receiver, name)
        except AttributeError:
            if self._safe:
                return JNone
            raise
        if callable(value) and not _class_access(value):
            def call(*args, **kwargs):
                try:
                    result = value(*args, **kwargs)
                except (AttributeError, TypeError):
                    if self._safe:
                        return JNone
                    raise
                return self if self._ignore else self._wrap(result)
            return call
        return self._wrap(value)

    def __setattr__(self, name, value):
        if name.startswith("_"):
            object.__setattr__(self, name, value)
        else:
            try:
                setattr(self._receiver, name, value)
            except AttributeError:
                if not self._safe:
                    raise

    def __call__(self, *args, **kwargs):
        return self._wrap(self._receiver(*args, **kwargs))


def _class_access(receiver):
    return issubclass(type(receiver), _meta)


def _raw(receiver, name):
    return (_class_get if _class_access(receiver) else _object_get)(receiver, name)


def _settings(receiver):
    return (type.__getattribute__ if _class_access(receiver) else object.__getattribute__)(receiver, "__dict__")


@lru_cache(maxsize=256)
def _members(klass, static):
    fields, methods, nested = {}, {}, {}
    while klass is not None:
        for field in klass.getDeclaredFields():
            if bool(_modifier.isStatic(field.getModifiers())) == static:
                fields.setdefault(str(field.getName()), field)
        signatures = set()
        for method in klass.getDeclaredMethods():
            if bool(_modifier.isStatic(method.getModifiers())) != static or method.isBridge():
                continue
            name = str(method.getName())
            types = tuple(method.getParameterTypes())
            key = name, types
            if key not in signatures:
                group = methods.setdefault(name, {})
                group.setdefault(types, method)
                signatures.add(key)
        if static:
            for item in klass.getDeclaredClasses():
                nested.setdefault(str(item.getSimpleName()), item)
        klass = klass.getSuperclass()
    return fields, methods, nested


def _declared(receiver, name, *, write=False):
    static = _class_access(receiver)
    target = None if static else receiver
    klass = _raw(receiver, "getClass")()
    fields, methods, nested = _members(klass, static)
    if name in fields:
        field = fields[name]
        field.setAccessible(True)
        if write:
            kind = field.getType()
            if kind.isPrimitive():
                label = str(kind.getName())
                setter = getattr(field, "set" + label.title())
                return lambda value: setter(target, value)
            return lambda value: field.set(target, value)
        return field.get(target)
    if not write and name in methods:
        def invoke(*values):
            for types, method in methods[name].items():
                if len(types) != len(values):
                    continue
                try:
                    arguments = _box(values, types)
                except (TypeError, ValueError, OverflowError):
                    continue
                method.setAccessible(True)
                try:
                    return method.invoke(target, arguments)
                except _argument_error:
                    continue
                except _invocation_error as error:
                    raise error.getCause() from None
            raise TypeError(f"No matching Java overload for {name}")
        return invoke
    if not write and name in nested:
        return _java.jclass(str(nested[name].getName()))
    raise AttributeError(name)


def _member(receiver, name, private):
    try:
        return _raw(receiver, name)
    except AttributeError:
        if private:
            return _declared(receiver, name)
        raise


def _property(name):
    return name and not (name.startswith(("get", "set")) and name[3:4].isupper()
                         or name.startswith("is") and name[2:3].isupper())


def _get(receiver, name):
    if not name or name.startswith("_"):
        return _raw(receiver, name)
    if name in _persistent:
        flag, value = _persistent[name]
        key = "_bygram_java_" + flag
        if _class_access(receiver):
            type.__setattr__(receiver, key, value)
        else:
            _settings(receiver)[key] = value
        return receiver
    if name in _safe | _ignore:
        return _Chain(receiver, name in _safe, name in _ignore)
    if name in _unsafe | _result:
        return receiver
    modes = _settings(receiver)
    private = modes.get("_bygram_java_private", False)
    if modes.get("_bygram_java_beans", True) and _property(name):
        suffix = name[:1].upper() + name[1:]
        for prefix in ("get", "is"):
            try:
                getter = _member(receiver, prefix + suffix, private)
                if callable(getter):
                    return getter()
            except (AttributeError, TypeError):
                continue
    return _member(receiver, name, private)


def _set(receiver, name, value):
    raw_set = _class_set if _class_access(receiver) else _object_set
    if not name or name.startswith("_"):
        return raw_set(receiver, name, value)
    modes = _settings(receiver)
    private = modes.get("_bygram_java_private", False)
    if modes.get("_bygram_java_beans", True) and _property(name):
        try:
            setter = _member(receiver, "set" + name[:1].upper() + name[1:], private)
            if callable(setter):
                setter(value)
                return
        except (AttributeError, TypeError):
            pass
    if private:
        try:
            setter = _declared(receiver, name, write=True)
        except AttributeError:
            pass
        else:
            setter(value)
            return
    raw_set(receiver, name, value)


def _box(values, types):
    from extera_utils.classes import _arguments
    return _arguments(_objects, values, types)


def install():
    global _java, _object, _meta, _object_get, _object_set, _class_get, _class_set
    global _objects, _modifier, _argument_error, _invocation_error
    import java as _java
    if hasattr(_java, "JNone"):
        return
    _object = _java.jclass("java.lang.Object")
    _meta = type(_object)
    _objects = _java.jarray(_object)
    _modifier = _java.jclass("java.lang.reflect.Modifier")
    _argument_error = _java.jclass("java.lang.IllegalArgumentException")
    _invocation_error = _java.jclass("java.lang.reflect.InvocationTargetException")
    _object_get, _object_set = _object.__getattribute__, _object.__setattr__
    _class_get, _class_set = _meta.__getattribute__, _meta.__setattr__
    for cls in (_object, _meta):
        original_dir = cls.__dir__
        type.__setattr__(cls, "__dir__", lambda receiver, original=original_dir: sorted(set(original(receiver)) | _names))
        type.__setattr__(cls, "__getattribute__", _get)
        type.__setattr__(cls, "__setattr__", _set)
    _java.JNone = JNone
