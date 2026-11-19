"""Account-scoped client helpers for plugins ported from exteraGram."""
from contextlib import contextmanager
from contextvars import ContextVar
from bygram import Client

_current = ContextVar("bygram_compat_client", default=(None, None))


def _context():
    from bygram._loader import calling_context
    context = _current.get()[0] or calling_context()
    if context is None:
        raise RuntimeError("PluginCallbackRequired")
    return context


def _invoke(context, account, callback, *args):
    token = _current.set((context, account))
    try:
        return callback(*args)
    finally:
        _current.reset(token)


def get_hook_account():
    return _current.get()[1]


@contextmanager
def account_scope(account):
    context = _context()
    if account is not None:
        AccountClient(account, _bridge=context._bridge)
    token = _current.set((context, account))
    try:
        yield
    finally:
        _current.reset(token)


def get_selected_account():
    from java import jclass
    return int(jclass("org.telegram.messenger.UserConfig").selectedAccount)


def get_last_fragment():
    return _context().ui.current_fragment()


def get_client(account=None):
    callback_account = get_hook_account()
    if account is None:
        account = callback_account if callback_account is not None else get_selected_account()
    return AccountClient(account)


def send_request(request, fn, account=None):
    return get_client(account).send_request(request, fn)


def _entities(values):
    from java import jclass
    if values is None:
        return None
    result = jclass("java.util.ArrayList")()
    for value in values:
        result.add(value)
    return result


def _reply(value, peer, client):
    if not isinstance(value, int):
        return value
    fragment = get_last_fragment()
    from java import jclass
    if isinstance(fragment, jclass("org.telegram.ui.ChatActivity")) and fragment.getCurrentAccount() == client.account and fragment.getDialogId() == peer:
        for message in fragment.getFilteredMessages():
            if message.getId() == value:
                return message
    raise ValueError("Reply message is not loaded in the active chat")


def send_message(params, parse_mode=None, account=None, *, _client=None):
    from java import jclass
    client = get_client(account) if _client is None else _client
    bridge = client._bridge
    params = {"notify": True, **params}
    if parse_mode is not None:
        from extera_utils.text_formatting import parse_text
        caption = "caption" in params
        key = "caption" if caption else "message"
        params.update(parse_text(params[key], parse_mode, caption))
    prepared = jclass("org.telegram.messenger.SendMessagesHelper$SendMessageParams")()
    for key, value in params.items():
        if key == "entities":
            value = _entities(value)
        elif key == "replyToMsg":
            value = _reply(value, params["peer"], client)
        elif key == "params" and isinstance(value, dict):
            mapping = jclass("java.util.HashMap")()
            for name, data in value.items():
                mapping.put(name, data)
            value = mapping
        bridge.setSendParameter(prepared, key, value)
    bridge.sendPrepared(client.account, client._identity, prepared)


def send_text(peer, text, account=None, *, parse_mode=None, **options):
    if parse_mode is None and not options:
        return get_client(account).send_text(peer, text)
    return send_message(dict(options, peer=peer, message=text), parse_mode, account)


def _send_media(kind, peer, path, caption, account, *, parse_mode=None, entities=None, replyToMsg=None,
                notify=True, scheduleDate=0, ttl=0, hasMediaSpoilers=False, high_quality=False, edit=None, _client=None):
    client = get_client(account) if _client is None else _client
    if parse_mode is not None:
        from extera_utils.text_formatting import parse_text
        formatted = parse_text(caption, parse_mode, True)
        caption, entities = formatted["caption"], formatted["entities"]
    client._bridge.mediaPrepared(client.account, client._identity, peer, kind, str(path), caption,
                                 _entities(entities), _reply(replyToMsg, peer, client), notify,
                                 scheduleDate, ttl, hasMediaSpoilers, high_quality, edit)


def send_photo(peer, path, caption="", account=None, **options):
    return _send_media("photo", peer, path, caption, account, **options)


def send_document(peer, path, caption="", account=None, **options):
    return _send_media("document", peer, path, caption, account, **options)


def send_video(peer, path, caption="", account=None, **options):
    return _send_media("video", peer, path, caption, account, **options)


def send_audio(peer, path, caption="", account=None, **options):
    return _send_media("audio", peer, path, caption, account, **options)


def edit_message(message, text=None, account=None, *, parse_mode=None, file_path=None, spoiler=False, with_spoiler=None,
                 _client=None, **options):
    if with_spoiler is not None:
        spoiler = with_spoiler
    if account is None:
        account = int(message.currentAccount)
    client = get_client(account) if _client is None else _client
    if file_path is not None:
        from pathlib import Path
        suffix = Path(file_path).suffix.lower()
        kind = "photo" if suffix in (".jpg", ".jpeg", ".png", ".webp") else "video" if suffix in (".mp4", ".mov") else "document"
        caption = str(message.messageOwner.message or "") if text is None else text
        return _send_media(kind, message.getDialogId(), file_path, caption, client.account,
                           parse_mode=parse_mode, hasMediaSpoilers=spoiler, edit=message, _client=client, **options)
    if parse_mode is None:
        return client.edit_text(message, text)
    from extera_utils.text_formatting import parse_text
    formatted = parse_text(text, parse_mode)
    return client._bridge.editPrepared(client.account, client._identity, message,
                                       formatted["message"], _entities(formatted["entities"]))


PLUGINS_QUEUE = "pluginsQueue"
GLOBAL_QUEUE = "globalQueue"
STAGE_QUEUE = "stageQueue"
SEARCH_QUEUE = "searchQueue"
CACHE_CLEAR_QUEUE = "cacheClearQueue"
PHONE_BOOK_QUEUE = "phoneBookQueue"
THEME_QUEUE = "themeQueue"
EXTERNAL_NETWORK_QUEUE = "externalNetworkQueue"

_QUEUE_ALIASES = {"plugins": PLUGINS_QUEUE, "global": GLOBAL_QUEUE, "stage": STAGE_QUEUE, "search": SEARCH_QUEUE}
_HOST_QUEUES = {GLOBAL_QUEUE, STAGE_QUEUE, SEARCH_QUEUE, CACHE_CLEAR_QUEUE, PHONE_BOOK_QUEUE, THEME_QUEUE,
                EXTERNAL_NETWORK_QUEUE}


def get_queue_by_name(name):
    from java import jclass
    name = _QUEUE_ALIASES.get(name, name)
    if name == PLUGINS_QUEUE:
        name = GLOBAL_QUEUE
    if name not in _HOST_QUEUES:
        raise KeyError(name)
    return getattr(jclass("org.telegram.messenger.Utilities"), name)


def run_on_queue(func, queue_name=PLUGINS_QUEUE, delay=0):
    from android_utils import R
    context, account = _context(), get_hook_account()
    def callback():
        if context._bridge.active():
            _invoke(context, account, func)
    if _QUEUE_ALIASES.get(queue_name, queue_name) == PLUGINS_QUEUE:
        if not delay:
            return context.work.submit(callback)

        def enqueue():
            if context._bridge.active():
                context.work.submit(callback)

        return get_queue_by_name(GLOBAL_QUEUE).postRunnable(R(enqueue), delay)
    get_queue_by_name(queue_name).postRunnable(R(callback), delay)


def get_account_instance(account=None):
    return get_client(account).get_account_instance()


def get_media_controller():
    from java import jclass
    return jclass("org.telegram.messenger.MediaController").getInstance()


def _controller(name):
    def get(account=None, *, client=None):
        client = get_client(account) if client is None else client
        return getattr(client.get_account_instance(), name)()
    return get


get_messages_controller = _controller("getMessagesController")
get_contacts_controller = _controller("getContactsController")
get_media_data_controller = _controller("getMediaDataController")
get_connections_manager = _controller("getConnectionsManager")
get_location_controller = _controller("getLocationController")
get_notifications_controller = _controller("getNotificationsController")
get_messages_storage = _controller("getMessagesStorage")
get_send_messages_helper = _controller("getSendMessagesHelper")
get_file_loader = _controller("getFileLoader")
get_secret_chat_helper = _controller("getSecretChatHelper")
get_download_controller = _controller("getDownloadController")
get_notifications_settings = _controller("getNotificationsSettings")
get_notification_center = _controller("getNotificationCenter")
get_user_config = _controller("getUserConfig")


class AccountClient(Client):
    def __init__(self, account, legacy_account=None, *, _bridge=None):
        if legacy_account is not None:
            _bridge, account = account, legacy_account
        super().__init__(_context()._bridge if _bridge is None else _bridge, account)

    def send_request(self, request, fn):
        if not callable(fn):
            raise TypeError("InvalidTelegramRequest")
        context = _context()
        return super().send_request(request, lambda response, error: _invoke(context, self.account, fn, response, error))

    def send_text(self, peer, text, *, parse_mode=None, **options):
        if parse_mode is None and not options:
            return super().send_text(peer, text)
        return self.send_message(dict(options, peer=peer, message=text), parse_mode)

    def send_message(self, params, parse_mode=None):
        return send_message(params, parse_mode, _client=self)

    def send_media(self, peer, kind, path, caption="", **options):
        return _send_media(kind, peer, path, caption, self.account, _client=self, **options)

    def send_photo(self, peer, file_path=None, caption="", high_quality=False, **options):
        return self.send_media(peer, "photo", options.pop("path", file_path), caption, high_quality=high_quality, **options)

    def send_document(self, peer, file_path=None, caption="", **options):
        return self.send_media(peer, "document", options.pop("path", file_path), caption, **options)

    def send_video(self, peer, file_path=None, caption="", **options):
        return self.send_media(peer, "video", options.pop("path", file_path), caption, **options)

    def send_audio(self, peer, file_path=None, caption="", **options):
        return self.send_media(peer, "audio", options.pop("path", file_path), caption, **options)

    def edit_message(self, message_obj, text=None, file_path=None, with_spoiler=False, *, parse_mode=None, **options):
        return edit_message(message_obj, text, file_path=file_path, with_spoiler=with_spoiler,
                            parse_mode=parse_mode, _client=self, **options)

    def get_account_instance(self):
        from java import jclass
        self._bridge.account(self.account, self._identity)
        return jclass("org.telegram.messenger.AccountInstance").getInstance(self.account)

    def __getattr__(self, name):
        helper = globals().get(name)
        if name.startswith("get_") and getattr(helper, "__name__", None) == "get":
            return lambda: helper(self.account, client=self)
        raise AttributeError(name)


def __getattr__(name):
    if name == "NotificationCenterDelegate":
        from java import dynamic_proxy, jclass
        delegate = dynamic_proxy(jclass("org.telegram.messenger.NotificationCenter$NotificationCenterDelegate"))
        globals()[name] = delegate
        return delegate
    raise AttributeError(name)
