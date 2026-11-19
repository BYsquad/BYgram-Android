import builtins
from importlib import import_module as _import_module
from importlib.machinery import PathFinder, SourceFileLoader, SourcelessFileLoader
from importlib.util import spec_from_file_location
import json
from pathlib import Path
import sys
from types import ModuleType

_plugins = {}
_data_suffixes = (".json", ".yaml", ".yml", ".txt")
_runtime_roots = frozenset(("android", "androidx", "base_plugin", "bygram", "client_utils", "com", "de",
                            "elyx", "hook_utils", "importlib", "java", "org", "ui"))


def current():
    frame = sys._getframe(1)
    while frame is not None:
        name = frame.f_globals.get("__name__", "")
        plugin = _plugins.get(name.partition(".")[0])
        if plugin is not None:
            return plugin, frame.f_globals.get("__package__")
        frame = frame.f_back
    raise RuntimeError("ElyxEnvironmentUnavailable")


def attach(namespace, root, context):
    root = Path(root)
    metadata = root / "_bygram_elyx.json"
    if not metadata.is_file():
        return
    with metadata.open("rb") as stream:
        raw = stream.read(1024 * 1024 + 1)
    if len(raw) > 1024 * 1024:
        raise ValueError("ElyxEnvironmentLimitExceeded")
    from elyx import Assets, SettingsController, Strings
    configuration = json.loads(raw)
    environment = {key: configuration[key] for key in ("metainfo", "refmap")}
    environment["settings"] = SettingsController(environment["metainfo"]["id"], context.settings)
    if configuration["locales"]:
        environment["strings"] = Strings(configuration["locales"])
    asset_path = environment["refmap"].get("assets")
    if asset_path is not None:
        assets = (root / asset_path).resolve()
        if not assets.is_relative_to(root.resolve()):
            raise ValueError("InvalidElyxAssetPath")
        if assets.is_dir():
            environment["assets"] = Assets(assets)
    _plugins[namespace] = namespace, environment


def detach(namespace):
    _plugins.pop(namespace, None)


def has_environment(namespace):
    return namespace in _plugins


def local_name(name, namespace, package):
    first = name.partition(".")[0]
    if first in _runtime_roots:
        return None
    for candidate in dict.fromkeys((package, namespace)):
        parent = sys.modules.get(candidate)
        path = getattr(parent, "__path__", None)
        qualified = f"{candidate}.{first}"
        if path is not None and (qualified in sys.modules or ElyxFinder.find_spec(qualified, path) is not None):
            return f"{candidate}.{name}"
    return None


def import_module(name, package=None):
    (namespace, _), caller = current()
    if name.startswith("."):
        return _import_module(name, package or caller)
    return _import_module(local_name(name, namespace, caller) or name, package)


def _local_import(name, globals=None, locals=None, fromlist=(), level=0):
    if not level and globals:
        namespace = globals.get("__name__", "").partition(".")[0]
        qualified = local_name(name, namespace, globals.get("__package__"))
        if qualified is not None:
            if fromlist:
                return builtins.__import__(qualified, globals, locals, fromlist)
            _import_module(qualified)
            prefix = qualified[:-len(name)]
            return sys.modules[prefix + name.partition(".")[0]]
    return builtins.__import__(name, globals, locals, fromlist, level)


class _LocalImports:
    def exec_module(self, module):
        module.__dict__["__builtins__"] = dict(vars(builtins), __import__=_local_import)
        super().exec_module(module)


class ElyxLoader(_LocalImports, SourceFileLoader):
    def set_data(self, path, data, **kwargs):
        pass


class ElyxBytecodeLoader(_LocalImports, SourcelessFileLoader):
    pass


class _DataModule:
    def create_module(self, spec):
        return None

    def exec_module(self, module):
        from elyx import Asset, LazyDict
        value = Asset.from_path(module.__spec__.origin).content()
        module.content = value
        if isinstance(value, dict):
            def field(name):
                if name.startswith("__") or name not in value:
                    raise AttributeError(name)
                item = value[name]
                return LazyDict(item) if isinstance(item, dict) else item
            module.__getattr__ = field
            module.get = value.get
            module.__class__ = _MappingModule


class _MappingModule(ModuleType):
    def __getitem__(self, key):
        from elyx import LazyDict
        value = self.content[key]
        return LazyDict(value) if isinstance(value, dict) else value


class ElyxFinder:
    @staticmethod
    def find_spec(fullname, path=None, target=None):
        if path is None or fullname.partition(".")[0] not in _plugins:
            return None
        spec = PathFinder.find_spec(fullname, path, target)
        if spec is not None and isinstance(spec.loader, SourceFileLoader):
            spec.loader = ElyxLoader(fullname, spec.origin)
        elif spec is not None and isinstance(spec.loader, SourcelessFileLoader):
            spec.loader = ElyxBytecodeLoader(fullname, spec.origin)
        if spec is None:
            stem = fullname.rpartition(".")[2]
            for directory in path:
                for suffix in _data_suffixes:
                    file = Path(directory, stem + suffix)
                    if file.is_file():
                        return spec_from_file_location(fullname, file, loader=_DataModule())
        return spec


sys.meta_path.insert(sys.meta_path.index(PathFinder) if PathFinder in sys.meta_path else 0, ElyxFinder)


def source_spec(name, path, *, locations=None):
    if has_environment(name.partition(".")[0]):
        loader = ElyxBytecodeLoader(name, path) if str(path).endswith(".pyc") else ElyxLoader(name, path)
    else:
        loader = None
    return spec_from_file_location(name, path, loader=loader, submodule_search_locations=locations)
