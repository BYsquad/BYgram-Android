"""Native settings descriptors. Values are validated again by the host."""


def _item(type, **values): return {"type": type, **values}
def Header(title): return _item("Header", title=title)
def Text(title): return _item("Text", title=title)
def Switch(key, title, default=False, scope="global"): return _item("Switch", key=key, title=title, default=default, scope=scope)
def TextInput(key, title, default="", max_length=1000, scope="global"): return _item("TextInput", key=key, title=title, default=default, max_length=max_length, scope=scope)
def Select(key, title, options, default=None, scope="global"): return _item("Select", key=key, title=title, options=options, default=default, scope=scope)
def Slider(key, title, min=0, max=100, step=1, default=0, scope="global"): return _item("Slider", key=key, title=title, min=min, max=max, step=step, default=default, scope=scope)
def Action(title, on_click): return _item("Action", title=title, on_click=on_click)
