"""Compatibility adapters for the declared native hook targets in a BYgram manifest."""
from client_utils import _invoke


class MethodHook:
    def before_hooked_method(self, param):
        pass

    def after_hooked_method(self, param):
        pass


class MethodReplacement:
    def replace_hooked_method(self, param):
        raise NotImplementedError


class BaseHook(MethodHook):
    def __init__(self, before=None, after=None):
        if before is not None:
            self.before_hooked_method = before
        if after is not None:
            self.after_hooked_method = after


def hook_filters(*filters):
    def decorate(callback):
        callback._hook_filters = filters
        return callback
    return decorate


class HookFilter:
    RESULT_IS_NULL = staticmethod(lambda param: param.getResult() is None)
    RESULT_NOT_NULL = staticmethod(lambda param: param.getResult() is not None)
    RESULT_IS_TRUE = staticmethod(lambda param: param.getResult() is True)
    RESULT_IS_FALSE = staticmethod(lambda param: param.getResult() is False)
    ResultEqual = staticmethod(lambda value: lambda param: param.getResult() == value)
    ResultIsInstanceOf = staticmethod(lambda clazz: lambda param: _instance_of(param.getResult(), clazz))
    ArgumentIsNull = staticmethod(lambda index: lambda param: param.args[index] is None)
    ArgumentNotNull = staticmethod(lambda index: lambda param: param.args[index] is not None)
    ArgumentIsFalse = staticmethod(lambda index: lambda param: param.args[index] is False)
    ArgumentIsTrue = staticmethod(lambda index: lambda param: param.args[index] is True)
    ArgumentEqual = staticmethod(lambda index, value: lambda param: param.args[index] == value)
    ArgumentIsInstanceOf = staticmethod(lambda index, clazz: lambda param: _instance_of(param.args[index], clazz))
    Condition = staticmethod(lambda condition, object=None:
                             lambda param: condition(param if object is None else object))
    Or = staticmethod(lambda *filters: lambda param: any(test(param) for test in filters))


def _instance_of(value, clazz):
    return isinstance(value, clazz) if isinstance(clazz, type) else clazz.isInstance(value)


class _Args:
    def __init__(self, call):
        self._call = call

    def __len__(self):
        return len(self._call.args)

    def __getitem__(self, index):
        return self._call.args[index]

    def __setitem__(self, index, value):
        self._call.set_arg(index, value)


class _Param:
    def __init__(self, call):
        self._call = call
        self.args = _Args(call)
        self.method = call.member
        self.thisObject = call.this_object

    def getResult(self): return self._call.result
    def setResult(self, value): self._call.set_result(value)
    def getThrowable(self): return self._call.exception
    def setThrowable(self, value): self._call.set_exception(value)
    def hasThrowable(self): return self.getThrowable() is not None


class _Unhook:
    def __init__(self, member, handle):
        self.member, self.handle = member, handle

    def getHookedMethod(self): return self.member
    def unhook(self): self.handle.close()


def _type_name(clazz):
    return _type_name(clazz.getComponentType()) + "[]" if clazz.isArray() else str(clazz.getName())


def register(plugin, member, hook, priority, before, after, before_filters, after_filters):
    from java import jclass
    constructor = isinstance(member, jclass("java.lang.reflect.Constructor"))
    clazz = str(member.getDeclaringClass().getName())
    name = "<init>" if constructor else str(member.getName())
    args = tuple(map(_type_name, member.getParameterTypes()))

    def wrap(callback, filters=()):
        if callback is None:
            return None
        filters = (*filters, *getattr(callback, "_hook_filters", ()))

        def invoke(call):
            param = _Param(call)
            if all(test(param) for test in filters):
                return _invoke(plugin.context, None, callback, param)
        return invoke

    hooks = plugin.context.hooks
    if isinstance(hook, MethodReplacement):
        if before is not None or after is not None:
            raise TypeError("InvalidHookCombination")
        handle = hooks.replace(clazz, name, wrap(hook.replace_hooked_method), args, priority)
    else:
        before = before or getattr(hook, "before_hooked_method", None)
        after = after or getattr(hook, "after_hooked_method", None)
        if before is None and after is None:
            raise TypeError("MissingHookCallback")
        handle = hooks.method(clazz, name, args, wrap(before, before_filters), wrap(after, after_filters), priority)
    return _Unhook(member, handle)


def register_many(plugin, members, hook, priority, options):
    handles = []
    try:
        for member in members:
            handles.append(plugin.hook_method(member, hook, priority, **options))
    except BaseException:
        for handle in handles:
            handle.unhook()
        raise
    return handles
