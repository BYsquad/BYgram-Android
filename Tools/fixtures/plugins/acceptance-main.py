"""Disposable acceptance plugin: exercises the real manager and public SDK in Release/R8."""
from bygram import Plugin, _selftest
from bygram.settings import Switch, Select, TextInput, Slider, Action
from java import jclass, dynamic_proxy
import builtins
import gc
import json
import threading
import time
import traceback

TAG = "BYgramSdkAcceptance"
FIRST = "test.bygram.acceptance_one"
SECOND = "test.bygram.acceptance_two"
MANAGER = globals().get("MANAGER", "org.telegram.messenger.plugins.PluginManager")
METHODS = globals().get("METHODS", {name: name for name in ("enable", "detach", "cleanup", "publish")})


def operation(enabled, plugin_id):
    complete = threading.Event()
    result = []
    class Task(dynamic_proxy(jclass("java.lang.Runnable"))):
        def run(self):
            try:
                manager = builtins.sdk_manager
                def invoke(name, *args):
                    method = next(m for m in manager.getClass().getDeclaredMethods()
                        if str(m.getName()) == METHODS[name] and len(m.getParameterTypes()) == len(args))
                    method.setAccessible(True)
                    return method.invoke(manager, *args)
                if enabled: invoke("enable", plugin_id)
                else:
                    running = invoke("detach", plugin_id)
                    if running is not None: invoke("cleanup", plugin_id, running)
                invoke("publish")
                result.append(None)
            except Exception: result.append(traceback.format_exc())
            finally: complete.set()
    builtins.sdk_queue.execute(Task())
    assert complete.wait(30), "manager operation timed out"
    assert result == [None], result


class Main(Plugin):
    def on_enable(self):
        builtins.sdk_acceptance = {}
        builtins.sdk_callback_refs = []
        manager_field = next(field for field in self.context._bridge.getClass().getDeclaredFields()
            if str(field.getType().getName()) == MANAGER)
        manager_field.setAccessible(True)
        builtins.sdk_manager = manager_field.get(self.context._bridge)
        queue_field = next(field for field in builtins.sdk_manager.getClass().getDeclaredFields()
            if str(field.getType().getName()) == "java.util.concurrent.ExecutorService")
        queue_field.setAccessible(True)
        builtins.sdk_queue = queue_field.get(builtins.sdk_manager)
        threading.Thread(target=self.check, name="SDKAcceptance", daemon=True).start()

    def check(self):
        try:
            operation(True, FIRST)
            operation(True, SECOND)
            first, second = builtins.sdk_acceptance[FIRST], builtins.sdk_acceptance[SECOND]
            backend = jclass("org.telegram.messenger.plugins.HookBridge")
            raw = bool(backend.available())
            if raw:
                _selftest.two_plugins(first, second)
                _selftest.original_error(second)
                _selftest.repeat(second)
                _selftest.repeat(second)
                _selftest.hooks(first)
            operation(False, FIRST)
            operation(False, SECOND)
            first = second = None
            for cycle in range(100):
                operation(True, FIRST)
                active = builtins.sdk_acceptance[FIRST].context
                assert active.settings.get("generation") == active.generation
                operation(False, FIRST)
                try: active.settings.set("late", True)
                except Exception: pass
                else: raise AssertionError("old generation remained active")
                active = None
            builtins.sdk_acceptance.clear()
            for _ in range(5):
                jclass("java.lang.System").gc()
                gc.collect()
                time.sleep(.1)
            assert all(ref() is None for ref in builtins.sdk_callback_refs), "SDK retained menu callbacks"
            assert self.context.accounts == (), "ungranted accounts visible"
            result = "PASS: " + json.dumps(dict(raw_hooks=raw, hook_cycles=300 if raw else 0,
                manager_cycles=100, retained_callbacks=0, sdk=self.context.version))
            self.context.storage.write_text("acceptance.txt", result)
            jclass("android.util.Log").i(TAG, result)
            time.sleep(12)
            self.context.ui.open_page({"title": "SDK acceptance", "items": self.create_settings()})
        except Exception:
            result = "FAIL: " + traceback.format_exc()
            jclass("android.util.Log").e(TAG, result)
            self.context.storage.write_text("acceptance.txt", result)

    def create_settings(self):
        return [Switch("enabled", "SDK switch", False),
            Select("choice", "SDK select", [{"value": "one", "title": "First"}, {"value": "two", "title": "Second"}], "one"),
            TextInput("label", "SDK input", "Test", 30),
            Slider("amount", "SDK slider", 0, 10, 1, 3),
            Action("Toggle from Python", lambda: self.context.settings.set("enabled", not self.context.settings.get("enabled", False))),
            Switch("account_only", "Account unavailable", False, scope="account"),
            Action("Finish acceptance", self.finish)]

    def finish(self):
        plugin_id = self.context.plugin_id
        def cleanup():
            try:
                for item in (FIRST, SECOND, plugin_id): operation(False, item)
                import os
                import shutil
                app = jclass("org.telegram.messenger.ApplicationLoader").applicationContext
                root = str(app.getNoBackupFilesDir().getAbsolutePath()) + "/plugins"
                with open(root + "/registry.json") as file: state = json.load(file)
                assert set(state["plugins"]) == {FIRST, SECOND, plugin_id}
                for item, record in state["plugins"].items():
                    shutil.rmtree(root + "/packages/" + record["hash"])
                    shutil.rmtree(root + "/data/" + item, ignore_errors=True)
                for name in ("registry.json", "registry.previous.json"):
                    atomic = jclass("android.util.AtomicFile")(jclass("java.io.File")(root, name))
                    stream = atomic.startWrite()
                    stream.write(b'{"schema":1,"engine":false,"plugins":{}}')
                    atomic.finishWrite(stream)
                jclass("android.util.Log").i(TAG, "CLEAN: acceptance plugins removed; engine off")
            except Exception: jclass("android.util.Log").e(TAG, traceback.format_exc())
        threading.Thread(target=cleanup, name="SDKAcceptanceCleanup", daemon=True).start()
