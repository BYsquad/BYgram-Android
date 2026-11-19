from . import Handle


class HookCall:
    def __init__(self, call): self._call = call
    @property
    def member(self): return self._call.member()
    @property
    def this_object(self): return self._call.thisObject()
    @property
    def args(self): return self._call.args()
    @property
    def result(self): return self._call.result()
    @property
    def exception(self): return self._call.exception()
    def set_arg(self, index, value): self._call.setArgument(index, value)
    def set_result(self, value): self._call.setPythonResult(value)
    def set_exception(self, value): self._call.setException(value)
    def call_original(self): return self._call.callOriginal()


class Hooks:
    def __init__(self, bridge): self._bridge = bridge

    def _register(self, class_name, method, args, before, after, replacement, priority):
        from java import jclass, jarray
        backend = jclass("org.telegram.messenger.plugins.HookBridge")
        def wrap(callback):
            if callback is None: return None
            return lambda call: callback(HookCall(call))
        def replace(call):
            call.setPythonResult(replacement(HookCall(call)))
        return Handle(backend.register(self._bridge, class_name, method,
            jarray(jclass("java.lang.String"))(args), wrap(before), wrap(after),
            replace if replacement is not None else None, priority))

    def method(self, class_name, method, args=(), before=None, after=None, priority=0):
        return self._register(class_name, method, args, before, after, None, priority)

    def constructor(self, class_name, args=(), before=None, after=None, priority=0):
        return self.method(class_name, "<init>", args, before, after, priority)

    def replace(self, class_name, method, callback, args=(), priority=0):
        return self._register(class_name, method, args, None, None, callback, priority)
