"""Compatibility API for single-file plugins ported to BYgram."""
from dataclasses import dataclass
from enum import Enum

from bygram import Plugin
from bygram.telegram import HookResult as _TelegramResult, HookStrategy
from client_utils import _invoke, get_hook_account, get_selected_account


@dataclass
class HookResult:
    strategy: HookStrategy = HookStrategy.DEFAULT
    request: object = None
    response: object = None
    update: object = None
    updates: object = None
    params: object = None


class MenuItemType(Enum):
    MESSAGE_CONTEXT_MENU = "message.context"
    CHAT_ACTION_MENU = "chat.actions"
    PROFILE_ACTION_MENU = "profile.actions"
    MAIN_MENU = "dialogs.toolbar"
    DRAWER_MENU = "drawer.menu"


class AppEvent(Enum):
    START = "START"
    STOP = "STOP"
    PAUSE = "PAUSE"
    RESUME = "RESUME"


@dataclass
class MenuItemData:
    menu_type: MenuItemType
    text: str
    on_click: object
    item_id: str = None
    icon: str = None
    subtext: str = None
    priority: int = 0
    condition: object = None


class BasePlugin(Plugin):
    @property
    def logger(self):
        from log_utils import get_logger
        return _invoke(self.context, None, get_logger)

    def on_load(self):
        self._registered_hooks = {}
        self._menu_items = {}
        self._menu_sequence = 0
        _invoke(self.context, None, self.on_plugin_load)

    def on_unload(self):
        if "_registered_hooks" in self.__dict__:
            self._registered_hooks.clear()
            self._menu_items.clear()
            _invoke(self.context, None, self.on_plugin_unload)

    def client(self, account=None):
        if account is None:
            account = get_hook_account()
        return self.context.client(get_selected_account() if account is None else account)

    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    def on_app_event(self, event_type):
        pass

    def _compat_app_event(self, event):
        _invoke(self.context, None, self.on_app_event, AppEvent(event))

    def get_setting(self, key, default=None):
        return self.context.settings.get(key, default)

    def set_setting(self, key, value, reload_settings=False):
        self.context.settings.set(key, value)

    def export_settings(self):
        import json
        return json.loads(self.context._bridge.settingsExport())

    def import_settings(self, values, reload_settings=True):
        import json
        if not isinstance(values, dict):
            raise TypeError("Expected a settings dictionary")
        self.context._bridge.settingsImport(json.dumps(values, ensure_ascii=False, allow_nan=False))

    def log(self, message):
        if self.context._bridge.active():
            self.context.log.info(message)
        else:
            print(f"{self.context.plugin_id}: {message}")

    def add_menu_item(self, item):
        if item.item_id is None:
            self._menu_sequence += 1
        item_id = item.item_id or f"_extera_menu_{self._menu_sequence}"
        self.remove_menu_item(item_id)
        import json
        from bygram import Handle

        def menu_context(event, fragment, message, grouped):
            data = json.loads(event)
            data["fragment"] = fragment
            data["context"] = fragment.getParentActivity()
            if message is not None:
                data["message"] = message
            if grouped is not None:
                data["groupedMessages"] = grouped.messages
            if "dialog_id" in data:
                from client_utils import get_messages_controller
                controller = _invoke(self.context, data["account"], get_messages_controller)
                peer = data["dialog_id"]
                if peer > 0:
                    data.update(user=controller.getUser(peer), userId=peer, userFull=controller.getUserFull(peer))
                elif peer < 0:
                    data.update(chat=controller.getChat(-peer), chatId=-peer, chatFull=controller.getChatFull(-peer))
            return data

        def click(event, fragment, message, grouped):
            data = menu_context(event, fragment, message, grouped)
            return _invoke(self.context, data["account"], item.on_click, data)

        condition = item.condition
        if isinstance(condition, str):
            from bygram._menu_condition import compile_condition
            condition = compile_condition(condition)

        def visible(event, fragment, message, grouped):
            data = menu_context(event, fragment, message, grouped)
            return bool(_invoke(self.context, data["account"], condition, data))

        self._menu_items[item_id] = Handle(self.context._bridge.compatMenu(
            item.menu_type.value, item_id, item.text, item.icon, item.subtext, item.priority, click,
            visible if condition is not None else None))
        return item_id

    def remove_menu_item(self, item_id):
        handle = self._menu_items.pop(item_id, None)
        if handle is not None:
            handle.close()

    def _add_hooks(self, key, name, match_substring, priority, methods):
        self._remove_hooks(key)
        handles = []
        try:
            for kind, method, field in methods:
                callback = getattr(self, method, None)
                if callback is None:
                    continue

                def invoke(name, account, value, detail, callback=callback, kind=kind, field=field):
                    if kind == "send":
                        result = _invoke(self.context, account, callback, account, value)
                    elif kind == "response":
                        result = _invoke(self.context, account, callback, name, account, value, detail)
                    else:
                        result = _invoke(self.context, account, callback, name, account, value)
                    if result is None:
                        return None
                    if not isinstance(result, HookResult):
                        raise TypeError("InvalidTelegramHookResult")
                    replacement = getattr(result, field)
                    return _TelegramResult(result.strategy, value if replacement is None else replacement)

                handles.append(self.context.telegram.intercept(kind, invoke, name,
                    match_substring=match_substring, priority=priority))
        except BaseException:
            for handle in handles:
                handle.close()
            raise
        self._registered_hooks[key] = handles

    def _remove_hooks(self, key):
        for handle in self._registered_hooks.pop(key, ()):
            handle.close()

    def add_hook(self, name, match_substring=False, priority=0):
        self._add_hooks(("named", name), name, match_substring, priority, (
            ("request", "pre_request_hook", "request"),
            ("response", "post_request_hook", "response"),
            ("update", "on_update_hook", "update"),
            ("updates", "on_updates_hook", "updates"),
        ))

    def remove_hook(self, name):
        self._remove_hooks(("named", name))

    def add_on_send_message_hook(self, priority=0):
        self._add_hooks(("send", ""), "", False, priority, (("send", "on_send_message_hook", "params"),))

    def remove_on_send_message_hook(self):
        self._remove_hooks(("send", ""))

    def hook_method(self, member, hook=None, priority=0, *, before=None, after=None,
                    before_filters=(), after_filters=()):
        from bygram._xposed import register
        return register(self, member, hook, priority, before, after, before_filters, after_filters)

    def hook_all_methods(self, clazz, name, hook=None, priority=0, **options):
        from bygram._xposed import register_many
        return register_many(self, (method for method in clazz.getDeclaredMethods() if str(method.getName()) == name),
                             hook, priority, options)

    def hook_all_constructors(self, clazz, hook=None, priority=0, **options):
        from bygram._xposed import register_many
        return register_many(self, clazz.getDeclaredConstructors(), hook, priority, options)

    def unhook_method(self, handle):
        handle.unhook()


from bygram._xposed import BaseHook, HookFilter, MethodHook, MethodReplacement, hook_filters
