from bygram import Plugin, Context
from java import jclass
from types import SimpleNamespace
import importlib.util
import java
import json
import traceback


class Main(Plugin):
    def on_enable(self):
        try:
            result = self.check_backend()
        except Exception:
            result = "FAIL: " + traceback.format_exc()
        self.context.storage.write_text("result.txt", result)

    def check_backend(self):
        app = jclass("org.telegram.messenger.ApplicationLoader").applicationContext
        loader = jclass("dalvik.system.DexClassLoader")(
            REMOTE_ROOT + "/classes.dex", str(app.getCodeCacheDir().getAbsolutePath()), None, app.getClassLoader())
        backend_class = loader.loadClass("org.telegram.messenger.plugins.ExperimentalHookBridge")
        backend = backend_class.newInstance()
        factory = loader.loadClass("PluginDebugContextFactory")

        def invoke(name, owner=factory):
            method = next(m for m in owner.getDeclaredMethods() if str(m.getName()) == name and len(m.getParameterTypes()) == 0)
            method.setAccessible(True)
            return method.invoke(None)

        first = invoke("create")
        second = invoke("createSecond")
        old = java.jclass
        java.jclass = lambda name: backend if name == "org.telegram.messenger.plugins.HookBridge" else old(name)
        try:
            spec = importlib.util.spec_from_file_location("source_backend_selftest", REMOTE_ROOT + "/_selftest.py")
            test = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(test)
            one = SimpleNamespace(context=Context(first[0]))
            two = SimpleNamespace(context=Context(second[0]))
            test.two_plugins(one, two)
            test.original_error(two)
            targets = int(invoke("dispatcherCount", backend_class))
            test.repeat(two)
            test.repeat(two)
            assert int(invoke("dispatcherCount", backend_class)) == targets, "metadata grew per cycle"
            test.hooks(one)
        finally:
            java.jclass = old
            first[1].close()
            second[1].close()
        assert int(invoke("registrationCount", backend_class)) == 0, "callback/context references retained"
        assert int(invoke("dispatcherCount", backend_class)) == 0, "native dispatchers retained"
        for _ in range(5):
            old("java.lang.System").gc()
        return "PASS: " + json.dumps({"targets": int(invoke("dispatcherCount", backend_class)), "registrations": 0,
            "checks": "two plugins, original throwable, constructor/private/overload, reentrancy, 300 cycles, quarantine, GC"})
