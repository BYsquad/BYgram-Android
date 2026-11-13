"""Basic settings descriptors translated to the native BYgram renderer."""
from bygram import settings
import inspect
import json


def _row_click(callback):
    if callback is None:
        return None
    accepts_view = bool(inspect.signature(callback).parameters)
    return lambda view: callback(view) if accepts_view else callback()


def Header(text):
    return settings.Header(text)


def _options(row, *, subtext=None, icon=None, on_change=None, on_long_click=None, link_alias=None):
    for key, value in (("subtext", subtext), ("icon", icon),
                       ("on_change", None if on_change is None else lambda value: on_change(json.loads(value))),
                       ("on_long_click", _row_click(on_long_click)), ("link_alias", link_alias)):
        if value is not None:
            row[key] = value
    return row


def Divider(text=""):
    return {"type": "Divider", "title": text or ""}


def _subpage(factory, title):
    page = factory()
    return page if isinstance(page, dict) else {"title": title, "items": page}


def Text(text, *, on_click=None, create_sub_fragment=None, accent=False, red=False, **options):
    row = settings.Text(text) if on_click is None and create_sub_fragment is None else dict(settings.Action(text, _row_click(on_click)), pass_view=True)
    if create_sub_fragment is not None:
        row["_subpage"] = lambda: _subpage(create_sub_fragment, text)
    return _options(dict(row, accent=accent, red=red), **options)


def Switch(key, text, default, **options):
    return _options(settings.Switch(key, text, default), **options)


def Selector(key, text, default, items, **kwargs):
    options = [{"title": title, "value": index} for index, title in enumerate(items)]
    return _options(settings.Select(key, text, options, default), **kwargs)


def Input(key, text, default="", **options):
    return _options(settings.TextInput(key, text, default), **options)


def EditText(key, hint, default="", max_length=1000, *, multiline=False, mask=None, **options):
    row = dict(settings.TextInput(key, hint, default, max_length), multiline=multiline)
    if mask is not None:
        row["mask"] = mask
    return _options(row, **options)


def Custom(*, item=None, view=None, factory=None, factory_args=None, on_click=None,
           on_long_click=None, create_sub_fragment=None, link_alias=None):
    if sum(value is not None for value in (item, view, factory)) != 1:
        raise ValueError("Provide one custom settings row")
    def build(context):
        if factory is not None:
            return factory(context, *(factory_args or ()))
        return view if view is not None else item
    row = {"type": "Custom", "title": "", "factory": build, "on_click": _row_click(on_click), "pass_view": True}
    if create_sub_fragment is not None:
        row["_subpage"] = lambda: _subpage(create_sub_fragment, "Settings")
    return _options(row,
                    on_long_click=on_long_click, link_alias=link_alias)
