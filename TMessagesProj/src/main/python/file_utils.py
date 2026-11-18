"""File helpers for ported plugins."""
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from tempfile import NamedTemporaryFile


class FilesController:
    class Place(Enum):
        UNKNOWN = 1
        ChatActivity = 2
        FilteredSearchView = 3
        SharedMediaLayout = 4
        SearchDownloadsContainer = 5
        ChannelAdminLogActivity = 6

    @dataclass
    class FileInfo:
        ext: str
        on_click: object = field(compare=False, repr=False)
        whitelist_places: list = field(default_factory=list, kw_only=True)
        blacklist_places: list = field(default_factory=list, kw_only=True)
        get_icon: object = field(default=None, compare=False, repr=False, kw_only=True)

        def __post_init__(self):
            import re
            self.ext = self.ext.removeprefix(".").lower()
            if not re.fullmatch(r"[a-z0-9][a-z0-9_+.-]{0,31}", self.ext):
                raise ValueError("Invalid file extension")
            if not callable(self.on_click) or self.get_icon is not None and not callable(self.get_icon):
                raise TypeError("Expected file hook callbacks")

    @dataclass
    class OnClickArgs:
        place: object
        file: object = field(compare=False, repr=False)
        file_name: str
        message: object = field(compare=False, repr=False)
        activity: object = field(compare=False, repr=False)
        parent_fragment: object = field(default=None, compare=False, repr=False)

    class ExtensionAlreadyRegistered(ValueError):
        pass

    class ExtensionNotRegistered(ValueError):
        pass

    class SecretInvalid(ValueError):
        pass

    @classmethod
    def register(cls, file_info):
        from client_utils import _context, _invoke
        context = _context()

        def on_open(place, file, name, message, activity, parent):
            args = cls.OnClickArgs(cls.Place(place), file, name, message, activity, parent)
            _invoke(context, int(message.currentAccount), file_info.on_click, args)

        def mask(places):
            value = 0
            for place in places:
                value |= 1 << (cls.Place(place).value - 1)
            return value

        def icon(account):
            return _invoke(context, account, file_info.get_icon)

        secret = context._bridge.registerFileHook(file_info.ext, mask(file_info.whitelist_places),
                                                 mask(file_info.blacklist_places), on_open,
                                                 None if file_info.get_icon is None else icon)
        if secret is None:
            raise cls.ExtensionAlreadyRegistered(file_info.ext)
        return str(secret)

    @classmethod
    def unregister(cls, ext, secret):
        from client_utils import _context
        failure = _context()._bridge.unregisterFileHook(ext.removeprefix(".").lower(), secret)
        if failure is not None:
            raise getattr(cls, str(failure))(ext)


def get_plugins_dir():
    from client_utils import _context
    return str(_context()._bridge.filesPath())


def _directory(kind):
    from java import jclass
    loader = jclass("org.telegram.messenger.FileLoader")
    return str(loader.getDirectory(getattr(loader, "MEDIA_DIR_" + kind)).getAbsolutePath())


def get_cache_dir(): return _directory("CACHE")
def get_files_dir(): return _directory("FILES")
def get_images_dir(): return _directory("IMAGE")
def get_videos_dir(): return _directory("VIDEO")
def get_audios_dir(): return _directory("AUDIO")
def get_documents_dir(): return _directory("DOCUMENT")


def ensure_dir_exists(path):
    try:
        Path(path).mkdir(parents=True, exist_ok=True)
        return True
    except OSError:
        return False


def list_dir(path, recursive=False, include_files=True, include_dirs=False, extensions=None):
    root = Path(path)
    suffixes = {suffix.lower() for suffix in extensions} if extensions else None
    entries = root.rglob("*") if recursive else root.glob("*")
    return sorted(str(entry) for entry in entries if
                  (include_dirs and entry.is_dir()) or
                  (include_files and entry.is_file() and (suffixes is None or entry.suffix.lower() in suffixes)))


def read_file(path):
    try:
        return Path(path).read_text(encoding="utf-8")
    except (OSError, UnicodeError):
        return None


def read_file_bytes(path):
    try:
        return Path(path).read_bytes()
    except OSError:
        return None


def write_file_bytes(path, content):
    path = Path(path)
    temporary = None
    try:
        with NamedTemporaryFile(dir=path.parent, delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(content)
        temporary.replace(path)
        return True
    except OSError:
        return False
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def write_file(path, content):
    return write_file_bytes(path, content.encode("utf-8"))


def delete_file(path):
    try:
        Path(path).unlink()
        return True
    except OSError:
        return False
