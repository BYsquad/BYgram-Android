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


class SimpleSettingFactory:
    def __init__(self, create_view, bind_view, is_clickable=False, is_shadow=False, create_item=None,
                 on_click=None, on_long_click=None, attached_view=None, equals=None, content_equals=None):
        self.create_view, self.bind_view = create_view, bind_view
        self.is_clickable, self.is_shadow = is_clickable, is_shadow
        self.create_item, self.on_click, self.on_long_click = create_item, on_click, on_long_click
        self.attached_view = attached_view
        self.equals, self.content_equals = equals, content_equals
        self._proxy = None
        self._released = False

    @property
    def instance(self):
        return self

    @property
    def java(self):
        return self

    def __call__(self, *args, **options):
        return Custom(factory=self, factory_args=args, **options)

    def release(self):
        self._released = True
        # Existing recycler rows can still hold the view type until the page closes.


def Custom(*, item=None, view=None, factory=None, factory_args=None, on_click=None,
           on_long_click=None, create_sub_fragment=None, link_alias=None):
    if sum(value is not None for value in (item, view, factory)) != 1:
        raise ValueError("Provide one custom settings row")
    if isinstance(factory, SimpleSettingFactory):
        return _factory_row(factory, factory_args, on_click, on_long_click, create_sub_fragment, link_alias)
    def build(context):
        if factory is not None:
            return factory(context, *(factory_args or ()))
        return view if view is not None else item
    row = {"type": "Custom", "title": "", "factory": build, "on_click": _row_click(on_click), "pass_view": True}
    if create_sub_fragment is not None:
        row["_subpage"] = lambda: _subpage(create_sub_fragment, "Settings")
    return _options(row,
                    on_long_click=on_long_click, link_alias=link_alias)


def _factory_row(factory, args, on_click, on_long_click, subpage, alias):
    from client_utils import _context
    from extera_utils.classes import Base, java_subclass, joverride
    context = _context()
    item = None

    def build(android_context):
        nonlocal item
        if factory._released:
            return None
        if factory._proxy is None:
            factory._proxy = RowFactory.new_instance()
            context._bridge.ownSettingsFactory(factory._proxy.java)
        item = factory._proxy.java.scopedItem()
        if factory.create_item is not None:
            custom = factory.create_item(None, None, args)
            if custom is None:
                return None
            custom.viewType = item.viewType
            item = custom
        else:
            from java import jclass
            arguments = jclass("java.util.ArrayList")()
            for value in args or ():
                arguments.add(value)
            item.object = arguments
        return item

    @java_subclass("org.telegram.ui.Components.UItem$UItemFactory")
    class RowFactory(Base):
        @joverride()
        def createView(self, android_context, list_view, account, guid, resources):
            return factory.create_view(android_context, list_view, account, guid, resources)

        @joverride()
        def bindView(self, view, row, divider, adapter, list_view):
            if not factory._released:
                factory.bind_view(view, row, divider, adapter, list_view)

        @joverride()
        def attachedView(self, list_view, view, row):
            if not factory._released and factory.attached_view is not None:
                factory.attached_view(list_view, view, row)

        @joverride()
        def isShadow(self):
            return factory.is_shadow

        @joverride()
        def isClickable(self):
            return factory.is_clickable

        @joverride("equals", ("org.telegram.ui.Components.UItem", "org.telegram.ui.Components.UItem"))
        def items_equal(self, a, b):
            return self.call_super() if factory.equals is None else factory.equals(a, b)

        @joverride()
        def contentsEquals(self, a, b):
            return self.call_super() if factory.content_equals is None else factory.content_equals(a, b)

    def action(callback):
        return None if callback is None else lambda view: callback(None, item, view) if not factory._released else None

    click = action(factory.on_click) if factory.is_clickable else None
    return Custom(factory=build, on_click=on_click or click, on_long_click=on_long_click or action(factory.on_long_click),
                  create_sub_fragment=subpage, link_alias=alias)
