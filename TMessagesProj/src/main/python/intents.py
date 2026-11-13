"""Handlers for unlocked Telegram links."""
import inspect
import re
from urllib.parse import parse_qsl

from bygram import Handle
from client_utils import _context, _invoke


class IntentsManager:
    @staticmethod
    def new_global_before_handler(callback, **filters):
        return _register(callback, True, **filters)

    @staticmethod
    def new_global_after_handler(callback, **filters):
        return _register(callback, False, **filters)


def _register(callback, before, *, scheme=None, host=None, path=None, required_path_args_names=(),
              action=None, whitelist_flags=0, blacklist_flags=0, type=None, categories=(), priority=0):
    context = _context()
    parameters = inspect.signature(callback).parameters
    all_arguments = any(parameter.kind == inspect.Parameter.VAR_KEYWORD for parameter in parameters.values())
    whitelist_flags = _flag_mask(whitelist_flags)
    blacklist_flags = _flag_mask(blacklist_flags)
    pattern = None
    if path is not None:
        parts = re.split(r"(\{[A-Za-z_]\w*\})", "/" + path.lstrip("/"))
        pattern = re.compile("".join("(?P<" + part[1:-1] + ">[^/]+)" if index % 2 else re.escape(part)
                                     for index, part in enumerate(parts)))

    def receive(intent, account):
        uri = intent.getData()
        flags = intent.getFlags()
        actual_categories = set(intent.getCategories() or ())
        values = dict(intent=intent, scheme=uri.getScheme(), host=uri.getHost(), path=uri.getPath(),
                      action=intent.getAction(), flags=flags, type=intent.getType(), categories=actual_categories)
        for name, expected in (("scheme", scheme), ("host", host), ("action", action), ("type", type)):
            if expected is not None and values[name] != expected:
                return
        if flags & whitelist_flags != whitelist_flags or flags & blacklist_flags or not set(categories) <= actual_categories:
            return
        query = dict(parse_qsl(str(uri.getEncodedQuery() or ""), keep_blank_values=True))
        if not set(required_path_args_names) <= query.keys():
            return
        match = pattern.fullmatch(str(values["path"] or "")) if pattern else None
        if pattern and match is None:
            return
        arguments = {**query, **(match.groupdict() if match else {}), **values, "query_args": query}
        selected = arguments if all_arguments else {name: arguments[name] for name in parameters if name in arguments}
        return _invoke(context, account, lambda: callback(**selected)) is True

    return Handle(context._bridge.intentHandler(before, priority, receive))


def _flag_mask(flags):
    if isinstance(flags, int):
        return flags
    mask = 0
    for flag in flags:
        mask |= flag
    return mask
