"""Android callbacks and UI helpers for exteraGram plugins."""
from client_utils import _context, _invoke, get_hook_account
from elyx import gen


def __getattr__(name):
    if name in ("R", "OnClickListener"):
        import elyx
        return getattr(elyx, "Runnable" if name == "R" else name)
    if name == "OnLongClickListener":
        from java import jclass
        proxy = gen(jclass("android.view.View$OnLongClickListener"), "onLongClick", True, False)
        globals()[name] = proxy
        return proxy
    raise AttributeError(name)


def run_on_ui_thread(func, delay=0):
    context, account = _context(), get_hook_account()
    callback = lambda: _invoke(context, account, func)
    context._bridge.runUiDelayed(callback, delay)


def log(data):
    from log_utils import get_logger
    get_logger().debug(data)


def copy_to_clipboard(text):
    from java import jclass
    return jclass("org.telegram.messenger.AndroidUtilities").addToClipboard(text)
