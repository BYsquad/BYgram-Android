"""Behavior checks for the shipped Java validators and Python namespace loader; no APK."""
from pathlib import Path
import json
import os
import struct
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def run(*command):
    subprocess.run(command, cwd=ROOT, check=True)


def main():
    run(sys.executable, "Tools/check_extera_port.py")
    jars = list((Path.home() / ".gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.11.0").rglob("*.jar"))
    if len(jars) != 1:
        raise RuntimeError("Resolve existing Gson 2.11.0 with Gradle before running checks")
    with tempfile.TemporaryDirectory(prefix="bygram-plugin-check-") as directory:
        work = Path(directory)
        source = ROOT / "TMessagesProj/src/plugins/java/org/telegram/messenger/plugins"
        run("javac", "--release", "8", "-encoding", "UTF-8", "-cp", str(jars[0]), "-d", str(work),
            "Tools/fixtures/plugins/android/util/AtomicFile.java",
            "Tools/fixtures/plugins/android/util/Base64.java",
            *(str(source / name) for name in ("PluginManifest.java", "PluginSignature.java", "PluginPackage.java", "PluginPackageAccess.java", "PluginScope.java", "PluginTasks.java", "PluginWork.java", "PluginCallbacks.java", "PluginFiles.java", "PluginSettingsFile.java", "PluginPageSpec.java", "PluginStore.java", "PluginCatalog.java")))
        cp = str(work) + os.pathsep + str(jars[0])
        run("javac", "--release", "8", "-encoding", "UTF-8", "-d", str(work),
            str(source / "PluginInterceptors.java"), "Tools/PluginInterceptorsCheck.java")
        run("java", "-ea", "-cp", cp, "org.telegram.messenger.plugins.PluginInterceptorsCheck")
        run("javac", "-encoding", "UTF-8", "-cp", cp, "-d", str(work), "Tools/PluginTasksCheck.java")
        run("java", "-ea", "-cp", cp, "org.telegram.messenger.plugins.PluginTasksCheck")
        run("javac", "-encoding", "UTF-8", "-cp", cp, "-d", str(work), "Tools/PluginFilesCheck.java")
        run("java", "-ea", "-cp", cp, "org.telegram.messenger.plugins.PluginFilesCheck")
        run("javac", "-encoding", "UTF-8", "-cp", cp, "-d", str(work), "Tools/PluginSignatureCheck.java")
        run("java", "-ea", "-cp", cp, "org.telegram.messenger.plugins.PluginSignatureCheck", "Tools/fixtures/plugins/hello-manifest.json")
        run("javac", "-encoding", "UTF-8", "-cp", cp, "-d", str(work), "Tools/PluginManifestCheck.java", "Tools/PluginPackageCheck.java", "Tools/PluginSettingsCheck.java", "Tools/PluginPageSpecCheck.java", "Tools/PluginStoreCheck.java", "Tools/PluginStoreCrashCheck.java")
        run("java", "-ea", "-cp", cp, "PluginManifestCheck")
        run("java", "-ea", "-cp", cp, "PluginSettingsCheck")
        run("java", "-ea", "-cp", cp, "org.telegram.messenger.plugins.PluginPageSpecCheck")
        run("java", "-ea", "-cp", cp, "org.telegram.messenger.plugins.PluginStoreCheck")
        run("java", "-ea", "-cp", cp, "org.telegram.messenger.plugins.PluginStoreCrashCheck")
        run("javac", "--release", "8", "-encoding", "UTF-8", "-cp", cp, "-d", str(work),
            *(str(path) for path in (ROOT / "Tools/fixtures/plugins/gate").rglob("*.java")),
            str(source / "HookBridge.java"), "Tools/PluginBackendGateCheck.java")
        run("java", "-ea", "-cp", cp, "PluginBackendGateCheck")
        fixtures = work / "fixtures"
        fixtures.mkdir()
        manifest = (ROOT / "Tools/fixtures/plugins/hello-manifest.json").read_bytes()
        base = [("manifest.json", manifest), ("main.py", b"raise AssertionError('MUST NOT EXECUTE DURING INSTALL')")]

        def package(name, entries=base):
            file = fixtures / (name + ".zip")
            with zipfile.ZipFile(file, "w", zipfile.ZIP_DEFLATED) as archive:
                for path, data in entries:
                    if isinstance(path, str):
                        info = zipfile.ZipInfo()
                        info.filename = path
                        info.compress_type = zipfile.ZIP_DEFLATED
                        path = info
                    archive.writestr(path, data)
            return file

        package("valid_basic")
        package("valid_subdirectory", base + [("assets/", b""), ("assets/note.txt", b"hello")])
        for index, name in enumerate(("../escape.py", "/escape.py", "C:/escape.py", "a\\escape.py", "a//b", "./b", "a/../b", "a.pyc", "a.so", "a.dex", "a.jar")):
            package("bad_path_" + str(index), base + [(name, b"test")])
        package("bad_case", base + [("Assets/a.txt", b"a"), ("assets/b.txt", b"b")])
        package("bad_duplicate", base + [("main.py", b"other")])
        package("bad_parent_file", base + [("a", b"file"), ("a/b", b"child")])
        package("bad_entrypoint", base[:1])
        package("bad_no_manifest", base[1:])
        package("bad_python_utf8", [("manifest.json", manifest), ("main.py", b"\xff")])
        package("bad_many", base + [(f"assets/{i}", b"") for i in range(512)])
        package("bad_big_file", base + [("big.txt", b"x" * (5 * 1024 * 1024 + 1))])
        package("bad_total", base + [(f"big{i}.txt", b"x" * (5 * 1024 * 1024)) for i in range(7)])
        package("bad_deep", base + [("/".join(["a"] * 13), b"x")])
        link = zipfile.ZipInfo("link"); link.create_system = 3; link.external_attr = 0o120777 << 16
        package("bad_symlink", base + [(link, b"../outside")])
        encrypted = package("bad_encrypted")
        data = bytearray(encrypted.read_bytes())
        local = data.index(b"PK\x03\x04"); central = data.index(b"PK\x01\x02")
        struct.pack_into("<H", data, local + 6, 1); struct.pack_into("<H", data, central + 8, 1)
        encrypted.write_bytes(data)
        crc = package("bad_crc")
        data = bytearray(crc.read_bytes()); central = data.index(b"PK\x01\x02")
        struct.pack_into("<I", data, central + 16, 0); crc.write_bytes(data)
        (fixtures / "bad_truncated.zip").write_bytes(b"PK\x03\x04")
        from package_plugin import package as pack_source
        for name in ("hello", "theme", "events", "accounts", "hooks", "composer"):
            pack_source(ROOT / "docs/plugins/examples" / name, fixtures / ("valid_example_" + name + ".zip"))
        run("java", "-ea", "-cp", cp, "org.telegram.messenger.plugins.PluginPackageCheck", str(fixtures))
        from build_plugin_catalog import build
        catalog_dir = work / "catalog"
        catalog = build(catalog_dir)
        import hashlib
        for entry in catalog["plugins"]:
            data = (catalog_dir / entry["file"]).read_bytes()
            assert len(data) == entry["size"] and hashlib.sha256(data).hexdigest() == entry["sha256"]
        run("javac", "-cp", cp, "-d", str(work), "Tools/PluginCatalogCheck.java")
        run("java", "-ea", "-cp", cp, "org.telegram.messenger.plugins.PluginCatalogCheck", str(catalog_dir / "index.json"))
        check_loader(work)
        check_examples(work)
        check_account_events()
        check_page_callbacks()
        check_hook_example()
        check_network()
        check_composer()
        check_theme()


def check_theme():
    from bygram import _Theme
    class Handle:
        def close(self): pass
    class Bridge:
        def __init__(self): self.values = []
        def theme(self, key, value):
            assert key == "actionBarDefaultTitle"
            self.values.append(value)
            return Handle()
    bridge = Bridge(); theme = _Theme(bridge)
    for color, expected in (("#123456", -15584170), ("#ff123456", -15584170),
                            ("#00123456", 0x123456), (0xffffffff, -1), (-0x80000000, -0x80000000), (0, 0)):
        theme.override("actionBarDefaultTitle", color).close()
        assert bridge.values[-1] == expected, color
    count = len(bridge.values)
    for color in ("123456", "#123", "#1234567", "#12_456", "#gg123456", True, 1.0, None, -0x80000001, 0x100000000):
        try: theme.override("actionBarDefaultTitle", color)
        except ValueError: pass
        else: raise AssertionError("invalid theme color reached Java: " + repr(color))
    assert len(bridge.values) == count
    print("PASS: RGB/ARGB alpha, signed/unsigned colors and invalid color rejection before Java")


def check_composer():
    from bygram import _loader
    class Handle:
        def close(self): pass
    class Bridge:
        identity = "1:123:first-login"
        preview = None
        def generation(self): return 1
        def id(self): return "dev.bygram.composer"
        def version(self): return "1.0.0"
        def menu(self, location, id, title, callback):
            assert location == "chat.actions"
            self.click = callback
            return Handle()
        def accountIdentity(self, slot):
            assert slot == 1
            return self.identity
        def account(self, slot, identity):
            if slot != 1 or identity != self.identity: raise RuntimeError("AccountUnavailable")
        def draftText(self, slot, identity, peer):
            self.account(slot, identity)
            assert peer == 456
            return "Hello черновик"
        def previewText(self, slot, identity, peer, text):
            self.account(slot, identity)
            self.preview = (slot, identity, peer, text)
        def sendText(self, *args): raise AssertionError("composer sent a message")
    bridge = Bridge()
    plugin = _loader.load(str(ROOT / "docs/plugins/examples/composer"), bridge.id(), "main.py:ComposerPlugin", bridge)
    try:
        plugin.on_enable()
        bridge.click('{"account":1}')
        assert bridge.preview is None, "private/ungranted chat was transformed"
        bridge.click('{"account":1,"dialog_id":456}')
        assert bridge.preview == (1, bridge.identity, 456, "HELLO ЧЕРНОВИК")
        client = plugin.context.client(1)
        bridge.identity = "1:123:second-login"
        for operation in (lambda: client.draft_text(456), lambda: client.preview_text(456, "text")):
            try: operation()
            except RuntimeError: pass
            else: raise AssertionError("stale client touched composer")
    finally: _loader.unload(plugin, bridge.id())
    print("PASS: actual composer example, explicit identity/peer, preview without send and stale client rejection")


def check_loader(work):
    sys.path.insert(0, str(ROOT / "TMessagesProj/src/main/python"))
    from bygram import _loader
    class Bridge:
        def generation(self): return 1
        def id(self): return "test.plugin"
        def version(self): return "1.0.0"
    plugins = []
    for index in range(2):
        root = work / ("plugin" + str(index)); root.mkdir()
        (root / "helper.py").write_text("VALUE = " + str(index), encoding="utf-8")
        (root / "main.py").write_text("from bygram import Plugin\nfrom .helper import VALUE\nclass Main(Plugin):\n def on_enable(self): self.value = VALUE\n", encoding="utf-8")
        instance = _loader.load(str(root), "test.plugin" + str(index), "main.py:Main", Bridge())
        instance.on_enable(); assert instance.value == index
        plugins.append(instance)
    for index, instance in enumerate(plugins): _loader.unload(instance, "test.plugin" + str(index))
    nested = work / "nested"; (nested / "pkg").mkdir(parents=True)
    (nested / "__init__.py").write_text("ROOT = 42\n", encoding="utf-8")
    (nested / "pkg/__init__.py").write_text("from .. import ROOT, pkg\nimport sys\nassert pkg is sys.modules[__name__]\nVALUE = ROOT + 1\nfrom . import main\n", encoding="utf-8")
    (nested / "pkg/main.py").write_text("from bygram import Plugin\nfrom . import VALUE\nfrom ..pkg import VALUE as AGAIN\nclass Main(Plugin):\n value = VALUE + AGAIN\n", encoding="utf-8")
    plugin = _loader.load(str(nested), "test.nested", "pkg/main.py:Main", Bridge())
    assert plugin.value == 86
    try: _loader.load(str(nested), "test.nested", "pkg/main.py:Main", Bridge())
    except RuntimeError: pass
    else: raise AssertionError("duplicate load replaced an active namespace")
    _loader.unload(plugin, "test.nested")
    unusual = work / "unusual"; (unusual / "some.folder").mkdir(parents=True)
    (unusual / "some.folder/helper.py").write_text("VALUE = 17\n", encoding="utf-8")
    (unusual / "some.folder/main-script.py").write_text("from bygram import Plugin\nfrom .helper import VALUE\nclass Main(Plugin):\n value = VALUE\n", encoding="utf-8")
    plugin = _loader.load(str(unusual), "test.unusual", "some.folder/main-script.py:Main", Bridge())
    assert plugin.value == 17
    _loader.unload(plugin, "test.unusual")
    (nested / "pkg/__init__.py").write_text("raise RuntimeError('failed package init')\n", encoding="utf-8")
    try: _loader.load(str(nested), "test.failed", "pkg/main.py:Main", Bridge())
    except RuntimeError: pass
    else: raise AssertionError("failed package initialization was swallowed")
    assert not any(name.startswith("_bygram_") for name in sys.modules)
    assert not _loader._namespaces
    print("PASS: private/nested relative imports, package initialization, duplicate-load rejection and failure/unload cleanup")


def check_network():
    from unittest.mock import patch
    from bygram import _Network
    class Bridge:
        size = None
        def networkAllowed(self): pass
        def check(self): pass
        def networkCompleted(self, size): self.size = size
    class Response:
        url = "https://example.test/test"
        body = b'{"ok":true}'
        def read(self, size): return self.body[:size]
        def __enter__(self): return self
        def __exit__(self, *args): pass
    response = Response()
    class Opener:
        def open(self, url, timeout):
            assert timeout == 10
            return response
    handlers = []
    def opener(handler): handlers.append(handler); return Opener()
    bridge = Bridge()
    client = _Network(bridge)
    with patch("urllib.request.build_opener", side_effect=opener):
        assert client.get_json("https://example.test/test") == {"ok": True}
        assert bridge.size == len(response.body)
        for url in ("http://example.test/test", "https://user:password@example.test/test", "https://@example.test/test"):
            try: client.get_json(url)
            except ValueError: pass
            else: raise AssertionError("Accepted invalid URL")
        try: handlers[0].redirect_request(None, None, 302, "redirect", {}, "http://example.test")
        except ValueError: pass
        else: raise AssertionError("Followed HTTPS downgrade")
        response.body = b"x" * (1024 * 1024 + 1)
        try: client.get_json("https://example.test/test")
        except ValueError: pass
        else: raise AssertionError("Accepted oversized network response")
    print("PASS: SDK network JSON, URL validation, redirect downgrade rejection and response limit without network access")


def check_account_events():
    from bygram import _loader
    import shutil
    with tempfile.TemporaryDirectory(prefix="bygram-account-check-") as directory:
        root = Path(directory) / "accounts"
        shutil.copytree(ROOT / "docs/plugins/examples/accounts", root)
        class Handle:
            closed = False
            def close(self): self.closed = True
        class Bridge:
            identity = "0:123:original"
            granted = identity
            last = None
            def generation(self): return 1
            def id(self): return "dev.bygram.accounts"
            def version(self): return "1.0.0"
            def menu(self, location, id, title, callback): self.menu_click = callback; return Handle()
            def accountIdentity(self, slot):
                assert slot == 0
                return self.identity
            def account(self, slot, identity):
                if slot != 0 or identity != self.identity: raise RuntimeError("AccountUnavailable")
            def accountAvailable(self, slot, identity): return slot == 0 and identity == self.identity
            def allowedAccounts(self): return [self.identity] if self.identity == self.granted else []
            def subscribe(self, name, callback): self.event = callback; return Handle()
            def settingsGet(self, key, account): self.account(0, account); return "true"
            def log(self, level, text): self.last = text
        bridge = Bridge()
        plugin = _loader.load(str(root), bridge.id(), "main.py:AccountPlugin", bridge)
        try:
            assert plugin.context.accounts == (0,)
            plugin.on_enable(); bridge.menu_click('{"account":0}')
            bridge.event('{"account":1,"page":"Other"}'); assert bridge.last is None
            bridge.event('{"account":0,"page":"Dialogs"}'); assert bridge.last == "page_opened: Dialogs"
            previous = plugin.subscription
            bridge.menu_click('{"account":0}'); assert previous._handle is None
            bridge.identity = "0:123:relogin"
            assert plugin.context.accounts == (), "new login inherited account selection"
            bridge.event('{"account":0,"page":"Stale"}')
            assert bridge.last == "page_opened: Dialogs"
        finally: _loader.unload(plugin, bridge.id())
    print("PASS: actual account example, explicit account filtering, subscription replacement and stale login rejection")


def check_hook_example():
    from bygram import _loader
    from types import SimpleNamespace
    from unittest.mock import patch
    class Handle:
        closed = False
        def close(self): self.closed = True
    class Bridge:
        def generation(self): return 1
        def id(self): return "dev.bygram.hooks"
        def version(self): return "1.0.0"
    class Backend:
        @staticmethod
        def register(bridge, target, method, args, before, after, replacement, priority):
            assert target == "org.telegram.messenger.plugins.HookBridge$Probe"
            assert method == "value" and args == ["int"] and replacement is None and priority == 0
            Backend.before, Backend.after = before, after
            Backend.handle = Handle()
            return Backend.handle
    class Call:
        arguments = [2]
        value = None
        def args(self): return self.arguments
        def result(self): return self.value
        def setArgument(self, index, value): self.arguments[index] = value
        def setPythonResult(self, value): self.value = value
    java = SimpleNamespace(jclass=lambda name: str if name == "java.lang.String" else Backend, jarray=lambda cls: list)
    with patch.dict(sys.modules, {"java": java}):
        plugin = _loader.load(str(ROOT / "docs/plugins/examples/hooks"), Bridge().id(), "main.py:HookPlugin", Bridge())
        try:
            plugin.on_enable()
            call = Call(); Backend.before(call); call.value = call.arguments[0] * 2; Backend.after(call)
            assert call.value == 7
            Backend.handle.close(); assert Backend.handle.closed
        finally: _loader.unload(plugin, Bridge().id())
    print("PASS: actual hook example and Python adapter against fake Java bridge; native dispatch requires Android fixture")


def check_page_callbacks():
    from bygram import UI, _Events
    class Bridge:
        callbacks = {}
        def registerCallback(self, callback):
            key = str(len(self.callbacks)); self.callbacks[key] = callback; return key
        def releaseCallback(self, key): del self.callbacks[key]
        def accountIdentity(self, slot): return "0:123:first-login"
        def account(self, slot, identity): pass
        def openPage(self, json): raise RuntimeError("CapabilityDenied")
        def subscribe(self, name, callback): self.event = callback; return None
    bridge = Bridge()
    ui = UI(bridge)
    for item in ({"type": "Action", "title": object(), "on_click": lambda: None},
                 {"type": "Action", "title": "x" * (64 * 1024), "on_click": lambda: None},
                 {"type": "Action", "title": "я" * 40000, "on_click": lambda: None}):
        try: ui._page_json({"title": "Test", "items": [item]})
        except (ValueError, TypeError): pass
        else: raise AssertionError("invalid page serialized")
        assert not bridge.callbacks, "failed serialization retained a Python callback"
    try: ui.open_page({"title": "Test", "items": [{"type": "Action", "title": "Run", "on_click": lambda: None}]})
    except RuntimeError: pass
    else: raise AssertionError("host page failure was swallowed")
    assert not bridge.callbacks, "rejected page retained a Python callback"
    seen = []
    _Events(bridge).subscribe("account.logged_out", lambda event: seen.append(event.identity), account=0)
    bridge.event('{"account":0,"user_id":123,"identity":"0:123:second-login"}')
    assert not seen, "stale subscription saw logout from a different login"
    bridge.event('{"account":0,"user_id":123,"identity":"0:123:first-login"}')
    assert seen == ["0:123:first-login"]
    print("PASS: callback rollback on invalid/oversized page serialization and logout generation filtering")


def check_examples(work):
    import shutil
    from bygram import _loader
    class Handle:
        def close(self): pass
    class Bridge:
        values = {}
        callback = None
        last = None
        def generation(self): return 1
        def id(self): return "dev.bygram.example"
        def version(self): return "1.0.0"
        def menu(self, location, id, title, callback):
            assert location == "chat.actions"; self.callback = callback; return Handle()
        def settingsGet(self, key, account): return json.dumps(self.values.get(key))
        def settingsHas(self, key, account): return key in self.values
        def bulletin(self, text): self.last = text
        def theme(self, key, value):
            assert key == "actionBarDefaultTitle" and -2**31 <= value < 2**31
            self.last = value; return Handle()
        def subscribe(self, name, callback):
            assert name == "ui.page_opened"; self.callback = callback; return Handle()
        def log(self, level, text): self.last = text
    for name in ("hello", "theme", "events"):
        root = work / ("example-" + name)
        shutil.copytree(ROOT / "docs/plugins/examples" / name, root)
        manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
        bridge = Bridge()
        plugin = _loader.load(str(root), manifest["id"], manifest["entrypoint"], bridge)
        plugin.on_load(); plugin.on_enable()
        if name == "hello":
            bridge.callback('{"account":0}'); assert bridge.last == "Hello BYgram!"
        elif name == "theme": assert isinstance(bridge.last, int)
        else:
            bridge.callback('{"page":"Fixture","account":0}'); assert bridge.last == "page_opened: Fixture"
        try: plugin.context.plugin_id = "changed"
        except AttributeError: pass
        else: raise AssertionError("Context identity is writable")
        plugin.on_disable(); _loader.unload(plugin, manifest["id"])
    print("PASS: three actual SDK examples against fake host, callbacks and read-only identity")


if __name__ == "__main__": main()
