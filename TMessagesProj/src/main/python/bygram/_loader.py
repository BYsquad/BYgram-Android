import hashlib
import importlib.util
import keyword
import os
import sys
import types
from . import Plugin, Context
from . import _elyx

_namespaces = {}
_contexts = {}


def calling_context():
    frame = sys._getframe(1)
    while frame is not None:
        context = _contexts.get(frame.f_globals.get("__name__", "").partition(".")[0])
        if context is not None:
            return context
        frame = frame.f_back
    return None


def _module_part(name):
    if name.isidentifier() and not keyword.iskeyword(name) and not name.startswith("_bygram_path_"): return name
    return "_bygram_path_" + hashlib.sha256(name.encode()).hexdigest()


def _register(name, module):
    sys.modules[name] = module
    parent, _, child = name.rpartition(".")
    if parent in sys.modules: setattr(sys.modules[parent], child, module)


def _remove(namespace):
    _contexts.pop(namespace, None)
    for name in list(sys.modules):
        if name == namespace or name.startswith(namespace + "."):
            del sys.modules[name]
    _elyx.detach(namespace)


def load(root, plugin_id, entrypoint, bridge):
    if plugin_id in _namespaces: raise RuntimeError("PluginAlreadyLoaded")
    namespace = "_bygram_" + hashlib.sha256(plugin_id.encode()).hexdigest() + "_" + str(bridge.generation())
    _namespaces[plugin_id] = namespace
    relative, class_name = entrypoint.split(":")
    package = namespace
    directory = root
    try:
        context = Context(bridge)
        _contexts[namespace] = context
        _elyx.attach(namespace, root, context)
        def load_package(name, path):
            if name in sys.modules: return sys.modules[name]
            initializer = os.path.join(path, "__init__.py")
            if os.path.isfile(initializer):
                spec = _elyx.source_spec(name, initializer, locations=[path])
                module = importlib.util.module_from_spec(spec)
                add_vendor_path(name, module)
                _register(name, module)
                spec.loader.exec_module(module)
            else:
                module = types.ModuleType(name)
                module.__path__ = [path]
                module.__package__ = name
                add_vendor_path(name, module)
                _register(name, module)
            return module
        def add_vendor_path(name, module):
            vendor = os.path.join(root, "_bygram_elyx_vendor")
            if name == namespace and os.path.isfile(os.path.join(root, "_bygram_elyx.json")) and os.path.isdir(vendor):
                module.__path__.append(vendor)
        module = load_package(package, directory)
        for part in relative.split("/")[:-1]:
            directory = os.path.join(directory, part)
            package += "." + _module_part(part)
            module = load_package(package, directory)
        filename = relative.split("/")[-1]
        name = package if filename == "__init__.py" else package + "." + _module_part(filename[:-3])
        module = sys.modules.get(name)
        if module is None:
            spec = _elyx.source_spec(name, os.path.join(root, relative))
            module = importlib.util.module_from_spec(spec)
            _register(name, module)
            spec.loader.exec_module(module)
        cls = getattr(module, class_name)
        if not isinstance(cls, type) or not issubclass(cls, Plugin):
            raise TypeError("InvalidPluginClass")
        plugin = cls()
        plugin.context = context
        return plugin
    except BaseException:
        _remove(namespace)
        _namespaces.pop(plugin_id, None)
        raise


def unload(plugin, plugin_id):
    try:
        if plugin is not None: plugin.on_unload()
    finally:
        namespace = _namespaces.pop(plugin_id, None)
        if namespace: _remove(namespace)
        if plugin is not None: plugin.context = None
