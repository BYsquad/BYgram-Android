from java import jclass
from bygram import Context
from types import SimpleNamespace
import importlib.util
import threading

factory = jclass("PluginDebugContextFactory")
first, second = factory.create(), factory.createSecond()
jclass("org.telegram.messenger.plugins.HookBridge").verify()
with open("/proc/self/maps", encoding="utf-8") as maps:
    assert CANDIDATE in maps.read(), "candidate native library not loaded"
print("STAGE: candidate library confirmed", flush=True)
flag_method = next(method for method in jclass("java.lang.Class").forName("org.telegram.messenger.plugins.HookBridge$Probe").getDeclaredMethods()
                   if str(method.getName()) == "label" and str(method.getParameterTypes()[0].getName()) == "int")
flag_field = jclass("java.lang.Class").forName("java.lang.reflect.Executable").getDeclaredField("accessFlags")
flag_field.setAccessible(True)
original_flags = flag_field.getInt(flag_method)
one, two = SimpleNamespace(context=Context(first[0])), SimpleNamespace(context=Context(second[0]))
spec = importlib.util.spec_from_file_location("unhook_selftest", SELFTEST)
test = importlib.util.module_from_spec(spec)
spec.loader.exec_module(test)
try:
    test.two_plugins(one, two)
    test.original_error(two)
    for _ in range(3):
        test.repeat(two)
    jclass("java.lang.System").err.println("STAGE: serial two-owner/exception/300-cycle checks passed")
    probe = jclass("org.telegram.messenger.plugins.HookBridge$Probe")
    target = "org.telegram.messenger.plugins.HookBridge$Probe"
    stopped = threading.Event()
    failures = []
    def caller():
        try:
            while not stopped.is_set():
                assert probe.value(2) in (4, 6)
        except BaseException as error:
            import traceback
            failures.append(traceback.format_exc())
    worker = threading.Thread(target=caller, daemon=True)
    worker.start()
    try:
        for _ in range(100):
            handle = two.context.hooks.method(target, "value", ["int"], before=lambda call: call.set_arg(0, 3))
            handle.close()
    finally:
        stopped.set()
        worker.join(10)
    assert not worker.is_alive() and not failures, failures
    handle = []
    handle.append(two.context.hooks.method(target, "value", ["int"], before=lambda call: handle[0].close()))
    assert probe.value(2) == 4
    test.hooks(one)
finally:
    first[1].close()
    second[1].close()
backend = jclass("java.lang.Class").forName("org.telegram.messenger.plugins.HookBridge")
count = next(method for method in backend.getDeclaredMethods() if str(method.getName()) == "dispatcherCount")
count.setAccessible(True)
assert int(count.invoke(None)) == 0, "native dispatchers retained"
for _ in range(10):
    jclass("java.lang.System").gc()
assert probe.value(2) == 4
fresh_method = next(method for method in jclass("java.lang.Class").forName("org.telegram.messenger.plugins.HookBridge$Probe").getDeclaredMethods()
                    if str(method.getName()) == "label" and str(method.getParameterTypes()[0].getName()) == "int")
assert flag_field.getInt(fresh_method) == original_flags, "original ART method flags not restored"
jclass("java.lang.System").out.println("PASS: physical unhook, two owners, original exception, 400 cycles, concurrent calls, self-unhook, GC and zero dispatchers")
