"""File helpers for ported plugins."""
from pathlib import Path
from tempfile import NamedTemporaryFile


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
