"""Plugin-bound resources, translations and settings for converted Elyx sources."""
from collections.abc import Mapping
from functools import lru_cache
import json
import logging
from pathlib import Path
import re

from bygram._elyx import current, import_module


def get_environment():
    (_, environment), _ = current()
    return dict(environment)


@lru_cache(maxsize=128)
def _compiled_mvel(script):
    from java import jclass
    return jclass("org.telegram.messenger.plugins.PluginMvel").compile(script)


def mvel_execute(script, data, to_type=None, java_instance=None):
    from java import jclass
    runtime = jclass("org.telegram.messenger.plugins.PluginMvel")
    values = jclass("java.util.HashMap")()
    for key, value in data.items():
        values.put(key, value)
    if isinstance(to_type, str):
        to_type = jclass("org.telegram.messenger.plugins.PluginClassProxy").type(to_type)
    elif isinstance(to_type, type):
        to_type = to_type.getClass()
    return runtime.execute(_compiled_mvel(script), values, to_type, java_instance)


_INTERFACES = {
    "OnClickListener": ("android.view.View$OnClickListener", "onClick"),
    "Runnable": ("java.lang.Runnable", "run"),
    "Callback": ("org.telegram.messenger.Utilities$Callback", "run"),
    "Callback2": ("org.telegram.messenger.Utilities$Callback2", "run"),
    "Callback3": ("org.telegram.messenger.Utilities$Callback3", "run"),
    "CallbackReturn": ("org.telegram.messenger.Utilities$CallbackReturn", "run"),
}
__all__ = (
    "Asset", "AssetNotFoundException", "Assets", "AssetsDirNotFoundException", "LazyDict",
    "SettingsController", "Strings", "gen", "gen2", "get_environment", "import_module", *_INTERFACES,
)


@lru_cache(maxsize=len(_INTERFACES))
def _interface(name):
    from java import jclass
    java_name, method = _INTERFACES[name]
    return gen(jclass(java_name), method, return_value=name == "CallbackReturn")


def __getattr__(name):
    if name in _INTERFACES:
        return _interface(name)
    if name in {"assets", "strings", "settings", "metainfo", "refmap"}:
        try:
            return get_environment()[name]
        except (KeyError, RuntimeError):
            pass
    raise AttributeError(name)


def _callback(fn, args, return_value, default=None):
    try:
        result = fn(*args)
        return result if return_value else None
    except Exception:
        logging.getLogger(__name__).exception("Elyx Java callback failed")
        return default


def gen(java_class, method_name, return_value=False, default_value=None):
    from java import dynamic_proxy

    def initialize(self, fn, *extra):
        super(proxy, self).__init__()
        self.fn, self.extra = fn, extra

    def invoke(self, *args):
        return _callback(self.fn, (*args, *self.extra), return_value, default_value)

    proxy = type("ElyxProxy", (dynamic_proxy(java_class),), {"__init__": initialize, method_name: invoke})
    return proxy


def gen2(java_class, return_value=False, **methods):
    from java import dynamic_proxy

    def wrap(fn):
        def invoke(self, *args):
            return _callback(fn, args, return_value)
        return invoke

    return type("ElyxProxy", (dynamic_proxy(java_class),), {method: wrap(callback) for method, callback in methods.items()})


class AssetNotFoundException(KeyError):
    pass


class AssetsDirNotFoundException(FileNotFoundError):
    pass


class Asset:
    def __init__(self, dir_path, filename, name):
        self.path = Path(dir_path) / filename
        self.filename = filename
        self.name = name

    @classmethod
    def temp_asset_from_url(cls, url, filename):
        from client_utils import _context
        from file_utils import write_file_bytes

        if (not isinstance(filename, str) or filename in ("", ".", "..")
                or any(char in filename for char in ("/", "\\", ":", "\0"))):
            raise ValueError("InvalidAssetFilename")
        context = _context()
        data = context.network._get_bytes(url)
        directory = Path(str(context._bridge.tempAssetDirectory()))
        asset = directory / filename
        if not write_file_bytes(asset, data):
            raise OSError("AssetWriteFailed")
        context._bridge.networkCompleted(len(data))
        return cls.from_path(asset)

    @classmethod
    def from_path(cls, path):
        if hasattr(path, "getAbsolutePath"):
            path = str(path.getAbsolutePath())
        path = Path(path)
        return cls(path.parent, path.name, path.stem)

    @property
    def ext(self):
        return self.path.suffix.lstrip(".")

    @property
    def path_str(self):
        return str(self.path)

    @property
    def java_file(self):
        from java import jclass
        return jclass("java.io.File")(self.path_str)

    def content_bytes(self):
        return self.path.read_bytes()

    def content_string(self):
        return self.path.read_text(encoding="utf-8")

    def content_json(self):
        return json.loads(self.content_string())

    def content_yaml(self):
        return import_module("yaml").safe_load(self.content_string())

    def content(self):
        suffix = self.path.suffix.lower()
        if suffix == ".json":
            return self.content_json()
        if suffix in {".yaml", ".yml"}:
            return self.content_yaml()
        if suffix in {".txt", ".md", ".csv", ".svg", ".xml"}:
            return self.content_string()
        return self.content_bytes()

    def to_image_location(self):
        from java import jclass
        return jclass("org.telegram.messenger.ImageLocation").getForPath(str(self.path))

    def to_drawable(self):
        if self.ext.lower() == "svg":
            return self.to_svg_drawable()
        return self._bitmap_drawable(None, None)

    def _svg(self):
        from java import jclass
        xml = self.content_string()
        if re.search(r"<!\s*(?:DOCTYPE|ENTITY)\b", xml, re.IGNORECASE):
            raise ValueError("SVG declarations are unsupported")
        return jclass("org.telegram.messenger.SvgHelper"), xml

    def to_svg_bitmap(self, width, height, white=False):
        _asset_size(width, height)
        svg, xml = self._svg()
        bitmap = svg.getBitmap(xml, width, height, white)
        if bitmap is None:
            raise ValueError("Invalid SVG")
        return bitmap

    def to_svg_drawable(self, width=None, height=None):
        from java import jclass
        if width is not None or height is not None:
            bitmap = self.to_svg_bitmap(width, height)
            resources = jclass("org.telegram.messenger.ApplicationLoader").applicationContext.getResources()
            return jclass("android.graphics.drawable.BitmapDrawable")(resources, bitmap)
        svg, xml = self._svg()
        drawable = svg.getDrawable(xml)
        if drawable is None:
            raise ValueError("Invalid SVG")
        return drawable

    def to_svg_thumb(self, color_key, alpha=1.0):
        if not 0 <= alpha <= 1:
            raise ValueError("Invalid SVG alpha")
        drawable = self.to_svg_drawable()
        drawable.setupGradient(color_key, alpha, False)
        return drawable

    def to_lottie_drawable(self, width=32, height=32):
        from java import jclass
        from client_utils import _context
        _asset_size(width, height)
        return jclass("org.telegram.messenger.plugins.PluginAssets").lottie(
            _context()._bridge, str(self.path), width, height)

    def to_bitmap_drawable(self, width=32, height=32):
        _asset_size(width, height)
        return self._bitmap_drawable(width, height)

    def _bitmap_drawable(self, width, height):
        from java import jclass
        decoder = jclass("android.graphics.BitmapFactory")
        options = jclass("android.graphics.BitmapFactory$Options")()
        options.inJustDecodeBounds = True
        path = str(self.path)
        decoder.decodeFile(path, options)
        if options.outWidth <= 0 or options.outHeight <= 0:
            raise ValueError("Unsupported raster image")
        target = max(width, height) if width is not None else 2048
        sample = 1
        while max(options.outWidth, options.outHeight) > target * sample:
            sample *= 2
        options.inJustDecodeBounds = False
        options.inSampleSize = sample
        bitmap = decoder.decodeFile(path, options)
        if bitmap is None:
            raise ValueError("Unable to decode raster image")
        if width is not None:
            bitmap = jclass("android.graphics.Bitmap").createScaledBitmap(bitmap, width, height, True)
        resources = jclass("android.content.res.Resources").getSystem()
        return jclass("android.graphics.drawable.BitmapDrawable")(resources, bitmap)


def _asset_size(width, height):
    if type(width) is not int or type(height) is not int or not (1 <= width <= 2048 and 1 <= height <= 2048):
        raise ValueError("Invalid asset size")


class Assets(Mapping):
    def __init__(self, dir_path):
        self._root = Path(dir_path).resolve()
        if not self._root.is_dir():
            raise AssetsDirNotFoundException(dir_path)

    def __iter__(self):
        return iter(sorted(path.name for path in self._root.iterdir()))

    def __len__(self):
        return sum(1 for _ in self)

    def __getitem__(self, key):
        if not isinstance(key, str) or any(part in {"", ".", ".."} for part in key.split("/")):
            raise AssetNotFoundException(key)
        head, _, tail = key.partition("/")
        path = self._root / head
        if not path.exists():
            matches = [candidate for candidate in self._root.iterdir()
                       if candidate.stem == head or re.sub(r"[^A-Za-z0-9]", "_", candidate.name.split(".")[0]) == head]
            if len(matches) != 1:
                raise AssetNotFoundException(key)
            path = matches[0]
        if not path.resolve().is_relative_to(self._root):
            raise AssetNotFoundException(key)
        if path.is_dir():
            nested = Assets(path)
            return nested[tail] if tail else nested
        if tail or not path.is_file():
            raise AssetNotFoundException(key)
        return Asset.from_path(path)

    def __getattr__(self, name):
        try:
            return self[name]
        except AssetNotFoundException as error:
            raise AttributeError(name) from error


class Strings:
    def __init__(self, all_strings):
        self._catalog = all_strings

    @staticmethod
    def _locale():
        try:
            from java import jclass
        except ImportError:
            return "en"
        locale = jclass("org.telegram.messenger.LocaleController").getInstance().getCurrentLocale()
        if locale is None:
            locale = jclass("java.util.Locale").getDefault()
        return str(locale.getLanguage())

    def get_with_locale(self, key, locale=None, default=None):
        locale = self._locale() if locale is None else locale.lower()
        english = self._catalog.get("en", {})
        return self._catalog.get(locale, {}).get(key, english.get(key, key if default is None else default))

    def get(self, key, default=None):
        return self.get_with_locale(key, default=default)

    def __getitem__(self, key):
        return self.get(key)

    def __getattr__(self, key):
        if key.startswith("_"):
            raise AttributeError(key)
        return self.get(key)

    def __call__(self, key, *args, locale=None, default=None, **values):
        result = self.get_with_locale(key, locale, default)
        return result.format(*args, **values) if isinstance(result, str) and (args or values) else result

    def pluralize(self, count, key):
        forms = self.get(key)
        if not isinstance(forms, (list, tuple)) or len(forms) != 3:
            raise ValueError("Expected three plural forms")
        number = abs(count)
        index = 0 if number % 10 == 1 and number % 100 != 11 else (
            1 if 2 <= number % 10 <= 4 and not 12 <= number % 100 <= 14 else 2)
        return f"{count} {forms[index]}"


class SettingsController:
    def __init__(self, plugin_id, backing=None):
        if backing is None:
            active = get_environment()["settings"]
            if active.plugin_id != plugin_id:
                raise ValueError("ElyxSettingsOwnerMismatch")
            backing = active._backing
        self.plugin_id = plugin_id
        self._backing = backing

    def get_setting(self, key, default=None):
        return self._backing.get(key, default)

    def set_setting(self, key, value, reload_settings=False):
        self._backing.set(key, value)
        if reload_settings:
            self._backing._bridge.reloadSettings()

    def get_settings(self):
        return json.loads(self._backing._bridge.settingsExport())

    def clear_settings(self):
        self._backing._bridge.settingsImport("{}")

    get = __getitem__ = __call__ = get_setting
    set = __setitem__ = set_setting


class LazyDict(dict):
    def __getattr__(self, name):
        if name.startswith("__") or name not in self:
            raise AttributeError(name)
        return self[name]

    def __getitem__(self, name):
        value = super().__getitem__(name)
        return LazyDict(value) if isinstance(value, dict) else value
