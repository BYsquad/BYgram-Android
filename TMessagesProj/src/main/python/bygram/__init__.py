"""BYgram SDK v1. In-process Python code requires the owner's trust."""
import json
from types import SimpleNamespace

SDK_VERSION = 1


class Plugin:
    context = None

    def on_load(self): pass
    def on_enable(self): pass
    def on_disable(self): pass
    def on_unload(self): pass
    def create_settings(self): return []
    def migrate_settings(self, old_version, new_version, values): return values

    def _settings_json(self):
        return self.context.ui._page_json({"title": self.context.plugin_id, "items": self.create_settings()})

    def _settings_link_json(self, target):
        *ancestors, alias = target.split("/")
        page = self._settings_page(ancestors)
        if not any(item.get("link_alias") == alias for item in page["items"]):
            raise ValueError("PluginSettingsLinkUnavailable")
        return self.context.ui._page_json(dict(page, focus_alias=alias))

    def _settings_page(self, ancestors):
        page = {"title": self.context.plugin_id, "items": self.create_settings()}
        for parent in ancestors:
            item = next((row for row in page["items"] if row.get("link_alias") == parent), None)
            if item is None or "_subpage" not in item:
                raise ValueError("PluginSettingsLinkUnavailable")
            page = item["_subpage"]()
        return dict(page, link_path=ancestors)

    def _reload_settings_json(self, path):
        return self.context.ui._page_json(self._settings_page(json.loads(path)))

    def _migrate_json(self, old_version, new_version, values):
        migrated = self.migrate_settings(old_version, new_version, json.loads(values))
        if not isinstance(migrated, dict): raise TypeError("SettingsMigrationFailed")
        return json.dumps(migrated, allow_nan=False, ensure_ascii=False)


class Handle:
    def __init__(self, handle): self._handle = handle
    def close(self):
        if self._handle is not None:
            handle, self._handle = self._handle, None
            handle.close()


class Settings:
    def __init__(self, bridge, account=""):
        self._bridge, self._account = bridge, account

    def get(self, key, default=None):
        value = self._bridge.settingsGet(key, self._account)
        return default if value == "null" and not self._bridge.settingsHas(key, self._account) else json.loads(value)

    def set(self, key, value):
        self._bridge.settingsSet(key, json.dumps(value, allow_nan=False, ensure_ascii=False), self._account)


class UI:
    def __init__(self, bridge): self._bridge = bridge
    def run(self, callback): self._bridge.runUi(callback)
    def bulletin(self, text, kind="info"):
        if kind == "info":
            self._bridge.bulletin(text)
        else:
            self._bridge.bulletin(text, kind)

    def alert(self, options, on_click=None, view=None):
        return self._bridge.alert(json.dumps(options, ensure_ascii=False), view, on_click)

    def current_fragment(self):
        return self._bridge.currentFragment()

    def add_menu(self, location, id, title, on_click):
        def invoke(event): on_click(SimpleNamespace(**json.loads(event)))
        return Handle(self._bridge.menu(location, id, title, invoke))

    def _page_json(self, page):
        page = dict(page)
        items = []
        registered = []
        try:
            for original in page.get("items", []):
                item = dict(original)
                subpage = item.pop("_subpage", None)
                if subpage is not None:
                    parent = page.get("link_path", [])
                    alias = item.get("link_alias")
                    path = [*parent, alias] if alias is not None and parent is not None and len(parent) < 11 else None
                    item["on_click"] = lambda *_, build=subpage, path=path: self.open_page(dict(build(), link_path=path))
                for source, target in _PAGE_CALLBACKS.items():
                    callback = item.pop(source, None)
                    if callback is not None:
                        item[target] = self._bridge.registerCallback(callback)
                        registered.append(item[target])
                items.append(item)
            page["items"] = items
            serialized = json.dumps(page, allow_nan=False, ensure_ascii=False)
            if len(serialized.encode("utf-8")) > 64 * 1024: raise ValueError("PageLimitExceeded")
            return serialized
        except BaseException:
            for key in registered: self._bridge.releaseCallback(key)
            raise

    def open_page(self, page_spec):
        serialized = self._page_json(page_spec)
        try: self._bridge.openPage(serialized)
        except BaseException:
            for item in json.loads(serialized)["items"]:
                for key in _PAGE_CALLBACKS.values():
                    if key in item:
                        self._bridge.releaseCallback(item[key])
            raise


_PAGE_CALLBACKS = {"on_click": "callback", "on_change": "change_callback",
                   "on_long_click": "long_callback", "factory": "row_factory"}


class _Log:
    def __init__(self, bridge): self._bridge = bridge
    def info(self, text): self._bridge.log("info", str(text))
    def warning(self, text): self._bridge.log("warning", str(text))
    def error(self, text): self._bridge.log("error", str(text))


class _Storage:
    def __init__(self, bridge): self._bridge = bridge
    def read_text(self, path): return self._bridge.readText(path, False)
    def read_asset(self, path): return self._bridge.readText(path, True)
    def write_text(self, path, text): self._bridge.writeText(path, text)


class _Events:
    def __init__(self, bridge): self._bridge = bridge
    def subscribe(self, name, callback, account=None):
        identity = None if account is None else self._bridge.accountIdentity(account)
        if identity is not None: self._bridge.account(account, identity)
        def invoke(event):
            value = json.loads(event)
            if identity is not None:
                if value.get("account") != account: return
                if name == "account.logged_out":
                    if value.get("identity") != identity: return
                elif not self._bridge.accountAvailable(account, identity): return
            callback(SimpleNamespace(**value))
        if name == "messages.received":
            if account is None: raise ValueError("AccountRequired")
            return Handle(self._bridge.observeMessages(account, identity, invoke))
        return Handle(self._bridge.subscribe(name, invoke))


class _Work:
    def __init__(self, bridge): self._bridge = bridge
    def submit(self, callback): return Handle(self._bridge.submit(callback))


class _Theme:
    def __init__(self, bridge): self._bridge = bridge
    def override(self, key, value):
        if isinstance(value, str):
            import re
            if not re.fullmatch(r"#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})", value): raise ValueError("InvalidThemeColor")
            value = int(value[1:], 16) | (0xff000000 if len(value) == 7 else 0)
        if isinstance(value, bool) or not isinstance(value, int) or not -0x80000000 <= value <= 0xffffffff:
            raise ValueError("InvalidThemeColor")
        if value > 0x7fffffff: value -= 0x100000000
        return Handle(self._bridge.theme(key, value))


class Context:
    @property
    def accounts(self): return tuple(sorted(int(identity.split(":", 1)[0]) for identity in self._bridge.allowedAccounts()))
    @property
    def plugin_id(self): return self._bridge.id()
    @property
    def version(self): return self._bridge.version()
    @property
    def generation(self): return self._bridge.generation()
    @property
    def migration_scope(self): return self._bridge.migrationScope

    def __init__(self, bridge):
        self._bridge = bridge
        self.settings = Settings(bridge)
        self.ui, self.log, self.storage = UI(bridge), _Log(bridge), _Storage(bridge)
        self.events, self.work, self.theme = _Events(bridge), _Work(bridge), _Theme(bridge)
        self.network = _Network(bridge)
        from .hooks import Hooks
        from .telegram import TelegramHooks
        self.hooks = Hooks(bridge)
        self.telegram = TelegramHooks(bridge)

    def account_settings(self, account):
        identity = self._bridge.accountIdentity(account)
        self._bridge.account(account, identity)
        return Settings(self._bridge, identity)

    def client(self, account):
        if account is None: raise ValueError("AccountRequired")
        return Client(self._bridge, account)


class Client:
    def __init__(self, bridge, account):
        self._bridge, self.account = bridge, account
        self._identity = bridge.accountIdentity(account)
        bridge.account(account, self._identity)

    def send_text(self, peer, text):
        self._bridge.sendText(self.account, self._identity, peer, text)

    def send_request(self, request, callback):
        return self._bridge.sendRequest(self.account, self._identity, request, callback)

    def send_media(self, peer, kind, path, caption=""):
        self._bridge.sendMedia(self.account, self._identity, peer, kind, str(path), caption)

    def send_photo(self, peer, path, caption=""):
        self.send_media(peer, "photo", path, caption)

    def send_document(self, peer, path, caption=""):
        self.send_media(peer, "document", path, caption)

    def send_video(self, peer, path, caption=""):
        self.send_media(peer, "video", path, caption)

    def send_audio(self, peer, path, caption=""):
        self.send_media(peer, "audio", path, caption)

    def edit_text(self, message, text):
        self._bridge.editText(self.account, self._identity, message, text)

    def draft_text(self, peer):
        return self._bridge.draftText(self.account, self._identity, peer)

    def preview_text(self, peer, text):
        self._bridge.previewText(self.account, self._identity, peer, text)


class _Network:
    def __init__(self, bridge): self._bridge = bridge
    def get_json(self, url):
        import urllib.request
        import urllib.parse
        self._bridge.networkAllowed()
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username is not None or parsed.password is not None:
            raise ValueError("InvalidNetworkUrl")
        class HTTPSRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, request, fp, code, message, headers, new_url):
                target = urllib.parse.urlsplit(new_url)
                if target.scheme != "https" or not target.hostname or target.username is not None or target.password is not None:
                    raise ValueError("InsecureRedirect")
                return super().redirect_request(request, fp, code, message, headers, new_url)
        with urllib.request.build_opener(HTTPSRedirect()).open(url, timeout=10) as response:
            if urllib.parse.urlsplit(response.url).scheme != "https": raise ValueError("InsecureRedirect")
            body = response.read(1024 * 1024 + 1)
        if len(body) > 1024 * 1024: raise ValueError("NetworkLimitExceeded")
        self._bridge.check()
        result = json.loads(body)
        self._bridge.networkCompleted(len(body))
        return result
