"""Exercise packaging and loading a port without a Java runtime."""
import json
import hashlib
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
from unittest.mock import patch
from zipfile import ZipFile

from port_extera_plugin import port
from port_extera_repository import convert


SOURCE = '''from pathlib import Path
from base_plugin import BasePlugin, HookResult, HookStrategy
from client_utils import get_hook_account, send_request, send_text
from ui.settings import Header, Switch

__id__ = "My-Hook"
__name__ = "Example"
__version__ = "1.2"
__category__ = ["tools"]
__icon__ = "exteraPlugins/1"
__app_version__ = ">=12.5.1"
Path(__file__).with_name("loaded").touch()

class Example(BasePlugin):
    def create_settings(self):
        return [Header("Example"), Switch("enabled", "Enabled", True)]

    def on_plugin_load(self):
        self.add_on_send_message_hook(priority=7)

    def on_send_message_hook(self, account, params):
        assert account == get_hook_account() == 2
        send_request("request", lambda response, error: send_text(4, response))
        return HookResult(strategy=HookStrategy.MODIFY_FINAL, params=params.upper())
'''


def check_elyx(root, loader, bridge):
    from bygram._port_extera import catalog_metadata, convert_mobile
    from bygram._dev_elyx import compare, patch
    installed = []
    path_before = tuple(sys.path)
    try:
        for value in ("one", "two"):
            source = root / (value + ".elyx")
            wheel = root / (value + "-1.0-py3-none-any.whl")
            with ZipFile(wheel, "w") as archive:
                archive.writestr("vendorlib/__init__.py", "from vendorlib.constants import VALUE\n")
                archive.writestr("vendorlib/constants.py", f"VALUE = {value!r}\n")
                archive.writestr(f"{value}-1.0.dist-info/WHEEL", "Root-Is-Purelib: true\nTag: py3-none-any\n")
            with ZipFile(source, "w") as archive:
                archive.writestr("refmap.json", json.dumps(dict(main="src/main.py", metainfo="meta.json",
                    strings="locales", assets="assets", wheels="wheels")))
                archive.writestr("meta.json", json.dumps(dict(id=value, name="{title}", description="Plugin {title}",
                                                              icon="exteraPlugins/1")))
                archive.writestr("locales/strings_en.json", json.dumps(dict(title=value, hello="Hello {name}")))
                archive.writestr("assets/nested/config.json", json.dumps(dict(value=value)))
                archive.writestr("src/helpers.py", "from vendorlib import VALUE\n")
                archive.writestr("src/config.json", json.dumps({"nested": {"value": value}}))
                archive.writestr("src/notice.txt", value)
                archive.writestr("wheels/" + wheel.name, wheel.read_bytes())
                archive.writestr("src/main.py", """from base_plugin import BasePlugin
from helpers import VALUE
from . import helpers
import config, notice
from elyx import assets, metainfo, strings, get_environment, import_module
class Example(BasePlugin):
    def value(self):
        assert helpers is import_module("helpers")
        assert metainfo is get_environment()["metainfo"]
        assert config["nested"].value == notice.content == VALUE
        return (VALUE, metainfo["id"], strings("hello", name="BYgram"), assets.nested.config.content_json()["value"])
""")
            target = source.with_suffix(".byplugin")
            cached = root / (value + ".bin")
            cached.write_bytes(source.read_bytes())
            assert json.loads(catalog_metadata(cached, True))["name"] == value
            port(source, target)
            converted = root / "mobile.byplugin"
            mobile = convert_mobile(source, converted, value, "1.0")
            assert "messages.media" in mobile["capabilities"] and mobile["account_scope"] == "selected_accounts"
            with ZipFile(converted) as archive:
                assert "src/main.py" in archive.namelist()
                assert json.loads(archive.read("manifest.json")) == mobile
            with ZipFile(target) as archive:
                manifest = json.loads(archive.read("manifest.json"))
                install = root / ("installed-" + value)
                archive.extractall(install)
            assert manifest["name"] == value
            assert manifest["description"] == f"Plugin {value}"
            assert manifest["sticker_icon"] == "exteraPlugins/1"
            assert manifest["entrypoint"] == "src/main.py:Example"
            compared = json.loads(compare(install, json.dumps({"files": {"src/main.py": "wrong"}})))
            assert compared["changed"] == ["src/main.py"] and "src/notice.txt" in compared["removed"]
            from io import BytesIO
            changed = patch(install, json.dumps({"changes": [
                {"#": "moved", "path": "src/notice.txt", "dest_path": "docs/notice.txt"},
                {"#": "created", "path": "assets/other.txt", "content": "b2s="},
            ]}))
            with ZipFile(BytesIO(changed)) as archive:
                assert archive.read("docs/notice.txt").decode() == value
                assert archive.read("assets/other.txt") == b"ok"
                assert "manifest.json" not in archive.namelist()
            plugin = loader.load(str(install), manifest["id"], manifest["entrypoint"], bridge)
            installed.append((plugin, manifest["id"], value))
        for plugin, plugin_id, value in installed:
            assert plugin.value() == (value, value, "Hello BYgram", value)
        assert tuple(sys.path) == path_before
        assert "vendorlib" not in sys.modules and "helpers" not in sys.modules
    finally:
        for plugin, plugin_id, value in installed:
            loader.unload(plugin, plugin_id)
    assert not any(name.startswith("_bygram_") for name in sys.modules)
    if sys.version_info[:2] == (3, 11):
        import py_compile
        module = root / "compiled.py"
        module.write_text("from base_plugin import BasePlugin\nclass Compiled(BasePlugin): pass\n")
        compiled = Path(py_compile.compile(str(module), str(module.with_suffix(".pyc")), doraise=True))
        source = root / "compiled.elyx"
        with ZipFile(source, "w") as archive:
            archive.writestr("refmap.json", '{"main":"main.pyc"}')
            archive.writestr("metainfo.json", '{"id":"compiled","name":"Compiled"}')
            archive.write(compiled, "main.pyc")
        package = source.with_suffix(".byplugin")
        port(source, package)
        with ZipFile(package) as archive:
            install = root / "compiled"
            archive.extractall(install)
        manifest = json.loads((install / "manifest.json").read_text())
        plugin = loader.load(str(install), manifest["id"], manifest["entrypoint"], bridge)
        try:
            assert type(plugin).__name__ == "Compiled"
        finally:
            loader.unload(plugin, manifest["id"])
    bad = root / "invalid.elyx"
    with ZipFile(bad, "w") as archive:
        archive.writestr("../escaped.py", "raise RuntimeError")
    try:
        port(bad, root / "invalid.byplugin")
    except ValueError as error:
        assert "Invalid archive path" in str(error)
    else:
        raise AssertionError("Archive traversal accepted")
    print("PASS: Elyx resources, locales, per-plugin absolute imports, pure wheel and archive validation")


def check_repository(root):
    index_url = "https://example.org/plugins/extera-plugins.json"
    resolved_index = "https://cdn.example.org/releases/extera-plugins.json"
    plugin_url = "https://cdn.example.org/releases/releases/example.plugin"
    data = SOURCE.encode()
    entry = dict(id="My-Hook", version="1.2", file="releases/example.plugin",
                 size=len(data), sha256=hashlib.sha256(data).hexdigest())

    def fetch(address, limit):
        assert address in (index_url, plugin_url)
        response = json.dumps(dict(schema=1, plugins=[entry])).encode() if address == index_url else data
        assert len(response) <= limit
        return response, resolved_index if address == index_url else address

    repository = root / "repository"
    convert(index_url, repository, fetch=fetch, telegram_hooks=True)
    index = repository / "index.json"
    old_index = index.read_bytes()
    exported = json.loads(old_index)["plugins"][0]
    package = (repository / exported["file"]).read_bytes()
    assert len(package) == exported["size"]
    assert hashlib.sha256(package).hexdigest() == exported["sha256"]
    for field, value in (("sha256", "0" * 64), ("version", "9.9"), ("id", "other"), ("file", "http://example.org/plugin.plugin")):
        original = entry[field]
        entry[field] = value
        try:
            convert(index_url, repository, fetch=fetch)
        except ValueError:
            assert index.read_bytes() == old_index
        else:
            raise AssertionError(f"Mismatched repository field accepted: {field}")
        entry[field] = original
    print("PASS: extera repository conversion, integrity, identity and atomic index replacement")


def check_proxies():
    from elyx import gen, gen2

    with patch.dict(sys.modules, java=SimpleNamespace(dynamic_proxy=lambda interface: interface)):
        listener = gen(object, "onClick", return_value=True)(lambda value, extra: value + extra, 7)
        assert listener.onClick(3) == 10
        callbacks = gen2(object, return_value=True, first=lambda value: value * 2, second=lambda: "ready")()
        assert callbacks.first(5) == 10 and callbacks.second() == "ready"
    print("PASS: Elyx Java interface proxy callback adapters")


def check_raster_bridge(root):
    from elyx import Asset

    def decode(path, options):
        if options.inJustDecodeBounds:
            options.outWidth, options.outHeight = 4096, 2048
            return None
        assert options.inSampleSize == 64
        return "decoded"

    classes = {
        "android.graphics.BitmapFactory": SimpleNamespace(decodeFile=decode),
        "android.graphics.BitmapFactory$Options": SimpleNamespace,
        "android.graphics.Bitmap": SimpleNamespace(createScaledBitmap=lambda *args: args),
        "android.content.res.Resources": SimpleNamespace(getSystem=lambda: "resources"),
        "android.graphics.drawable.BitmapDrawable": lambda *args: args,
        "org.telegram.messenger.ImageLocation": SimpleNamespace(getForPath=lambda path: path),
    }
    asset = Asset.from_path(root / "sample.png")
    with patch.dict(sys.modules, java=SimpleNamespace(jclass=classes.__getitem__)):
        assert asset.to_bitmap_drawable(64, 32) == ("resources", ("decoded", 64, 32, True))
        assert asset.to_image_location() == str(root / "sample.png")
    print("PASS: Elyx raster bridge sampling and drawable parameters")


def check_debugger():
    from bygram._dev_debug import debug

    class Sample:
        value = 21

        def run(self):
            self.value *= 2

    output = []
    commands = iter(("p self.value", "continue"))
    sample = Sample()
    debug(sample, "run", SimpleNamespace(readLine=lambda: next(commands, None), write=output.append))
    assert "(Pdb)" in "".join(output) and "21" in "".join(output)
    assert sample.value == 42
    print("PASS: developer remote terminal drives Pdb on a plugin method")


def check_alert_options():
    from client_utils import _current
    from ui.alert import AlertDialogBuilder

    events = []
    cancellation = []

    def alert(payload, view, callback, android_context, resources, drawable, show):
        spec = json.loads(payload)
        assert spec["icons"] == [17] and spec["viewHeight"] == 48 and view == "view"
        assert spec["messageClickable"] is True and spec["dim"] is False
        assert spec["topAnimation"] == 21 and spec["blur"] is True and not show
        return SimpleNamespace(setCancellation=lambda *args: cancellation.append(args),
                               show=lambda: None, getDialog=lambda: "dialog",
                               close=lambda: callback(AlertDialogBuilder._DISMISS), click=callback)

    scope = _current.set((SimpleNamespace(_bridge=SimpleNamespace(pluginAlert=alert)), 2))
    try:
        dialog = (AlertDialogBuilder().set_view("view", 48).set_items(["item"], icons=[17])
                  .set_message_text_view_clickable(True).set_dim_enabled(False)
                  .set_top_animation(21, 48, False, 0).set_blurred_background(True)
                  .set_on_back_button_listener(lambda _, which: events.append(which))
                  .set_on_cancel_listener(lambda _: events.append("cancel"))
                  .set_on_dismiss_listener(lambda _: events.append("dismiss")).create())
        assert dialog.get_dialog() == "dialog"
        dialog.show()
        dialog.set_canceled_on_touch_outside(False).set_cancelable(False)
        dialog._handle.click(dialog._BACK)
        dialog._handle.click(dialog._CANCEL)
        dialog.dismiss()
    finally:
        _current.reset(scope)
    assert events == [-2, "cancel", "dismiss"]
    assert cancellation == [(True, False), (False, False)]
    print("PASS: alert options and lifecycle callbacks")


def check_compat_helpers(root):
    from bygram._port_extera import dependencies, bygram_id
    from bygram._port_elyx import write_environment
    from extera_utils.text_formatting import parse_text
    from file_utils import list_dir, read_file, write_file
    from ui.settings import Custom, Switch
    from base_plugin import HookFilter
    from client_utils import _current, get_hook_account, get_queue_by_name, CACHE_CLEAR_QUEUE
    from intents import IntentsManager
    from ui.bulletin import BulletinHelper
    from elyx import Asset

    hooks, bulletins = [], []
    bridge = SimpleNamespace(
        intentHandler=lambda before, priority, callback: hooks.append(callback) or SimpleNamespace(close=lambda: None),
        pluginBulletin=lambda *args: bulletins.append(args),
    )
    bridge.tempAssetDirectory = lambda: str(root)
    downloaded = []
    bridge.networkCompleted = downloaded.append
    network = SimpleNamespace(_get_bytes=lambda url: b"asset")
    scope = _current.set((SimpleNamespace(_bridge=bridge, network=network), 2))
    try:
        context = []
        IntentsManager.new_global_before_handler(lambda **values: context.append(values) or True,
                                                 path="/{name}", whitelist_flags=[1, 4], blacklist_flags=[2])
        uri = SimpleNamespace(getScheme=lambda: "tg", getHost=lambda: "test", getPath=lambda: "/demo",
                              getEncodedQuery=lambda: "id=1", isHierarchical=lambda: True)
        intent = SimpleNamespace(getData=lambda: uri, getFlags=lambda: 5, getCategories=lambda: (),
                                 getAction=lambda: "android.intent.action.VIEW", getType=lambda: None)
        assert hooks[0](intent, 2) is True and context[0]["name"] == "demo" and context[0]["id"] == "1"
        intent.getFlags = lambda: 7
        assert hooks[0](intent, 2) is None
        IntentsManager.new_global_before_handler(lambda **values: context.append(values) or True,
                                                 action="android.intent.action.SEND", type="text/plain")
        intent.getData = lambda: None
        intent.getAction = lambda: "android.intent.action.SEND"
        intent.getType = lambda: "text/plain"
        assert hooks[0](intent, 2) is None
        assert hooks[1](intent, 2) is True and context[-1]["path"] is None and not context[-1]["query_args"]
        BulletinHelper.show_with_button("Test", 7, "Open", lambda: get_hook_account(), fragment="fragment")
        assert bulletins[0][:6] == ("Test", "button", "fragment", 7, "Open", 5000)
        assert bulletins[0][6]() == 2
        assert Asset.temp_asset_from_url("https://example.org/asset", "asset.txt").content_string() == "asset"
        assert downloaded == [5]
        try:
            Asset.temp_asset_from_url("https://example.org/asset", "../escape")
        except ValueError:
            pass
        else:
            raise AssertionError("unsafe asset filename accepted")
    finally:
        _current.reset(scope)

    utilities = SimpleNamespace(cacheClearQueue=object(), globalQueue=object())
    with patch.dict(sys.modules, java=SimpleNamespace(jclass=lambda name: utilities)):
        assert get_queue_by_name(CACHE_CLEAR_QUEUE) is utilities.cacheClearQueue
        assert get_queue_by_name("global") is get_queue_by_name("globalQueue") is utilities.globalQueue

    java = SimpleNamespace(jclass=lambda name: lambda: SimpleNamespace(kind=name.rsplit("Entity", 1)[1]))
    with patch.dict(sys.modules, java=java):
        result = parse_text("🙂 <b>bold</b> <a href='https://example.org'>link</a>")
        assert result["message"] == "🙂 bold link"
        assert [(e.kind, e.offset, e.length) for e in result["entities"]] == [("Bold", 3, 4), ("TextUrl", 8, 4)]
        result = parse_text("**bold** and `code`", "Markdown", True)
        assert result["caption"] == "bold and code"
        assert [(e.kind, e.offset, e.length) for e in result["entities"]] == [("Bold", 0, 4), ("Code", 9, 4)]

    work = root / "compat"
    work.mkdir()
    write_environment(work, {}, {"requires": {"helper (1.2)": "https://example.org/helper.elyx"}}, {})
    assert json.loads(dependencies(work)) == {bygram_id("helper", structured=True): "1.2.0"}
    assert write_file(work / "sample.txt", "пример")
    assert read_file(work / "sample.txt") == "пример"
    assert list_dir(work, extensions=[".TXT"]) == [str(work / "sample.txt")]
    changes = []
    Switch("key", "Title", False, on_change=changes.append)["on_change"]("true")
    assert changes == [True]
    assert Custom(factory=lambda context, value: (context, value), factory_args=[42])["factory"]("host") == ("host", 42)
    assert HookFilter.Or(HookFilter.ArgumentIsNull(0), HookFilter.ArgumentEqual(0, 2))(SimpleNamespace(args=[2]))
    from bygram import Plugin, UI
    from ui.settings import EditText, Text
    callbacks = []
    class SettingsPlugin(Plugin):
        def create_settings(self):
            return [Text("Nested", link_alias="nested", create_sub_fragment=lambda: [
                EditText("pin", "PIN", mask="[0-9]{4}", link_alias="pin")])]
    plugin = SettingsPlugin()
    plugin.context = SimpleNamespace(plugin_id="test.settings",
        ui=UI(SimpleNamespace(registerCallback=lambda callback: callbacks.append(callback) or str(len(callbacks)))))
    linked = json.loads(plugin._settings_link_json("nested/pin"))
    assert linked["focus_alias"] == "pin" and linked["link_path"] == ["nested"]
    assert linked["items"][0]["mask"] == "[0-9]{4}"
    try:
        plugin._settings_link_json("nested/missing")
    except ValueError:
        pass
    else:
        raise AssertionError("Accepted a missing setting alias")
    print("PASS: compatibility helpers, UTF-16 entities, dependencies, file operations and settings callbacks")


def check_extended_compatibility():
    from base_plugin import BasePlugin
    from client_utils import AccountClient, _current, account_scope, get_hook_account
    from extera_utils.classes import Base, JHelper
    from file_utils import FilesController

    observed, hooks = [], {}
    def register(ext, allow, deny, open_file, icon):
        if ext in hooks:
            return None
        hooks[ext] = (allow, deny, open_file, icon)
        return "secret"

    def unregister(ext, token):
        if ext not in hooks:
            return "ExtensionNotRegistered"
        if token != "secret":
            return "SecretInvalid"
        hooks.pop(ext)

    bridge = SimpleNamespace(active=lambda: True, awaitStop=lambda timeout: timeout == 250,
                             accountIdentity=lambda slot: f"id-{slot}", account=lambda slot, identity: None,
                             sendRequest=lambda slot, identity, request, callback: callback("reply", None),
                             registerFileHook=register, unregisterFileHook=unregister,
                             editText=lambda slot, identity, message, text: observed.append((slot, message, text)))
    plugin = BasePlugin()
    plugin.context = SimpleNamespace(_bridge=bridge)
    plugin.on_load()
    token = _current.set((plugin.context, 2))
    try:
        assert plugin.stop_event.wait(.25) and not plugin.stop_event.is_set()
        with account_scope(1):
            assert get_hook_account() == 1
            client = AccountClient(1)
            client.send_request("sample", lambda reply, error: observed.append((reply, get_hook_account())))
            client.edit_message(SimpleNamespace(currentAccount=1), "edited")
        assert observed[0] == ("reply", 1) and observed[1][0::2] == (1, "edited")
        assert get_hook_account() == 2

        info = FilesController.FileInfo(".EXAMPLE", lambda args: observed.append((args.place, args.file_name, get_hook_account())),
                                        whitelist_places=[FilesController.Place.ChatActivity], get_icon=lambda: get_hook_account())
        secret = plugin.add_file_hook(info)
        allow, deny, callback, icon = hooks["example"]
        assert (allow, deny) == (2, 0)
        callback(2, object(), "demo.example", SimpleNamespace(currentAccount=1), None, None)
        assert observed[-1] == (FilesController.Place.ChatActivity, "demo.example", 1) and icon(1) == 1
        for operation, error in ((lambda: plugin.add_file_hook(info), FilesController.ExtensionAlreadyRegistered),
                                 (lambda: plugin.remove_file_hook("example", "wrong"), FilesController.SecretInvalid)):
            try:
                operation()
            except error:
                pass
            else:
                raise AssertionError("File hook registration or ownership check failed")
        plugin.remove_file_hook(".example", secret)
        assert not hooks
    finally:
        _current.reset(token)

    cleanup = []
    plugin.register_cleanup(lambda: cleanup.append("first"))
    plugin.register_cleanup(lambda: cleanup.append("second"))
    def failing_unload():
        raise ValueError("on_plugin_unload")
    plugin.on_plugin_unload = failing_unload
    try:
        plugin.on_unload()
    except ValueError:
        pass
    else:
        raise AssertionError("Unloading error was swallowed")
    assert cleanup == ["second", "first"]

    @Base.extends("java.util.ArrayList")
    class Peer(Base):
        @JHelper.Override("size", [])
        def size(self):
            return super().size()
        @JHelper.Method("describe", "java.lang.String", ["int"], modifiers="private final",
                        throws=["java.io.IOException"])
        def description(self, count):
            return str(count)
        @JHelper.Method("int", [])
        def value(self):
            return 1
        @staticmethod
        @JHelper.Method(modifiers="PUBLIC | STATIC")
        def twice(number: "int") -> "int":
            return number * 2
        @JHelper.Constructor(arg_types=["int"])
        def initialize(self, count):
            pass
        counter = JHelper.Field("int", modifiers="private", methods=[JHelper.GetMethod()])
    assert Peer._java_base == "java.util.ArrayList" and Peer.size._java_override == ("size", [])
    assert Peer.description._java_method == ("describe", "java.lang.String", ["int"])
    assert Peer.description._java_options == ("private final", ["java.io.IOException"])
    assert Peer.value._java_method == ("value", "int", [])
    assert Peer.twice._java_method == ("twice", "int", ["int"]) and Peer.twice(7) == 14
    assert Peer.initialize._java_constructor == ("int",)
    assert Peer.counter.modifiers == "private" and Peer.counter.methods == ((None, True, "public"),)
    print("PASS: account scope, bound callbacks/editing, file hooks and ownership, reverse cleanup and proxy decorators")


def main():
    python = Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/python"
    sys.path.insert(0, str(python))
    from bygram import _loader
    check_extended_compatibility()

    class Bridge:
        def id(self):
            return "test.extera"

        def generation(self):
            return 1

        def intercept(self, kind, name, substring, priority, callback):
            assert (kind, name, substring, priority) == ("send", "", False, 7)
            self.callback = callback
            return SimpleNamespace(close=lambda: None)

        def accountIdentity(self, account):
            assert account == 2
            return "2:identity"

        def account(self, account, identity):
            assert (account, identity) == (2, "2:identity")

        def sendRequest(self, account, identity, request, callback):
            self.account(account, identity)
            assert request == "request"
            self.response_callback = callback
            return 17

        def sendText(self, account, identity, peer, text):
            self.account(account, identity)
            assert (peer, text) == (4, "response")
            self.sent = True

    with TemporaryDirectory() as directory:
        root = Path(directory)
        source = root / "example.plugin"
        source.write_text(SOURCE, encoding="utf-8")
        package = root / "example.byplugin"
        port(source, package, telegram_hooks=True, telegram_requests=True, send_text=True)
        assert not (root / "loaded").exists()
        with ZipFile(package) as archive:
            manifest = json.loads(archive.read("manifest.json"))
            metadata = json.loads(archive.read("_bygram_elyx.json"))["metainfo"]
            assert metadata["category"] == ["tools"] and metadata["app_version"] == ">=12.5.1"
            archive.extractall(root)
        assert manifest["version"] == "1.2.0"
        assert manifest["sticker_icon"] == "exteraPlugins/1"
        assert manifest["account_scope"] == "selected_accounts"
        assert manifest["capabilities"] == ["hooks.telegram", "telegram.requests", "messages.send"]
        assert not (root / "loaded").exists()
        bridge = Bridge()
        java = SimpleNamespace(jclass=lambda name: SimpleNamespace(replace=lambda value, terminal: (value, terminal)))
        with patch.dict(sys.modules, java=java):
            plugin = _loader.load(str(root), manifest["id"], manifest["entrypoint"], bridge)
            try:
                assert (root / "loaded").exists()
                plugin.on_load()
                assert json.loads(plugin._settings_json())["items"][1]["default"] is True
                assert bridge.callback("SendMessageParams", 2, "hello", None) == ("HELLO", True)
                bridge.response_callback("response", None)
                assert bridge.sent
            finally:
                _loader.unload(plugin, manifest["id"])
        source.write_text(SOURCE + '\n__requirements__ = ["external"]\n', encoding="utf-8")
        try:
            port(source, root / "dependencies.byplugin")
        except ValueError as error:
            assert "dependencies" in str(error)
        else:
            raise AssertionError("Unresolved dependencies accepted")
        check_elyx(root, _loader, bridge)
        check_repository(root)
        check_proxies()
        check_raster_bridge(root)
        check_debugger()
        check_alert_options()
        check_compat_helpers(root)
    print("PASS: exteraGram source conversion, metadata, delayed execution and hook result adaptation")


if __name__ == "__main__":
    main()
