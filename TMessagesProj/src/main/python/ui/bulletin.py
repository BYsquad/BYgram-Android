"""Native bulletin helpers for ported plugins."""
from client_utils import _context


def _show(text, kind):
    _context().ui.bulletin(text, kind)


class BulletinHelper:
    @staticmethod
    def show_info(message, fragment=None):
        _show(message, "info")

    @staticmethod
    def show_error(message, fragment=None):
        _show(message, "error")

    @staticmethod
    def show_success(message, fragment=None):
        _show(message, "success")
