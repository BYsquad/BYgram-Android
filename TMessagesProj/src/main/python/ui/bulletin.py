"""Native bulletin helpers for ported plugins."""
from client_utils import _context, _invoke, get_hook_account


def _show(text, kind, fragment, icon=0, detail=None, duration=0, action=None, commit=None):
    context, account = _context(), get_hook_account()

    def wrap(callback):
        return None if callback is None else lambda: _invoke(context, account, callback)

    context._bridge.pluginBulletin(text, kind, fragment, icon, detail, duration, wrap(action), wrap(commit))


class BulletinHelper:
    DURATION_SHORT = 1500
    DURATION_LONG = 2750
    DURATION_PROLONG = 5000

    @staticmethod
    def show_info(message, fragment=None):
        _show(message, "info", fragment)

    @staticmethod
    def show_error(message, fragment=None):
        _show(message, "error", fragment)

    @staticmethod
    def show_success(message, fragment=None):
        _show(message, "success", fragment)

    @staticmethod
    def show_simple(text, icon_res_id, fragment=None):
        _show(text, "simple", fragment, icon_res_id)

    @staticmethod
    def show_two_line(title, subtitle, icon_res_id, fragment=None):
        _show(title, "two_line", fragment, icon_res_id, subtitle)

    @staticmethod
    def show_with_button(text, icon_res_id, button_text, on_click, fragment=None, duration=DURATION_PROLONG):
        _show(text, "button", fragment, icon_res_id, button_text, duration, action=on_click)

    @staticmethod
    def show_undo(text, on_undo, on_action=None, subtitle=None, fragment=None):
        _show(text, "undo", fragment, detail=subtitle, action=on_undo, commit=on_action)

    @staticmethod
    def show_copied_to_clipboard(message=None, fragment=None):
        _show(message or "", "copied", fragment)

    @staticmethod
    def show_link_copied(is_private_link_info=False, fragment=None):
        _show("", "link_copied", fragment, int(is_private_link_info))

    @staticmethod
    def show_file_saved_to_gallery(is_video=False, amount=1, fragment=None):
        _show("", "gallery", fragment, int(is_video), duration=amount)

    @staticmethod
    def show_file_saved_to_downloads(file_type_enum_name="UNKNOWN", amount=1, fragment=None):
        _show("", "downloads", fragment, detail=file_type_enum_name, duration=amount)
