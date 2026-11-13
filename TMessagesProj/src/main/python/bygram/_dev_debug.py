"""Debugger adapters for authenticated Full Debug requests."""
from functools import partial
import os
from pathlib import Path
import pdb
import shutil
import sys
import zipfile

_loaded_adapter = None
_listen_port = None


class _Terminal:
    def __init__(self, bridge):
        self._bridge = bridge

    def readline(self):
        line = self._bridge.readLine()
        return "" if line is None else str(line) + "\n"

    def write(self, text):
        self._bridge.write(text)
        return len(text)

    def flush(self):
        pass


def _load_helpers(adapter, directory):
    global _loaded_adapter
    if _loaded_adapter is not None:
        if _loaded_adapter != adapter:
            raise RuntimeError("Restart BYgram before switching IDE debugger families")
        return
    source = Path(directory) / f"plugin-dev-{adapter}.zip"
    if not source.is_file():
        raise RuntimeError(f"Provision {adapter} with Tools/plugin_dev.py install-debugger")
    helpers = source.parent / "plugin-dev-helpers"
    if helpers.exists():
        shutil.rmtree(helpers)
    helpers.mkdir()
    try:
        with zipfile.ZipFile(source) as archive:
            archive.extractall(helpers)
    except Exception:
        shutil.rmtree(helpers)
        raise
    os.environ["PYDEVD_USE_CYTHON"] = "NO"
    os.environ["PYDEVD_USE_FRAME_EVAL"] = "NO"
    sys.path.insert(0, str(helpers))
    _loaded_adapter = adapter


def _ide_debug(target, adapter, port, directory):
    _load_helpers(adapter, directory)
    if adapter == "debugpy":
        import debugpy
        import debugpy.server
        import pydevd
        try:
            debugpy.configure(subProcess=False)
            debugpy.connect(("127.0.0.1", port))
            return target()
        finally:
            pydevd.stoptrace()
    else:
        import pydevd_pycharm
        try:
            pydevd_pycharm.settrace("127.0.0.1", port=port, suspend=False,
                                   patch_multiprocessing=False, trace_only_current_thread=True)
            return target()
        finally:
            pydevd_pycharm.stoptrace()


def ide_session(adapter, port, start, directory):
    global _listen_port
    if not start and _loaded_adapter != adapter:
        return
    _load_helpers(adapter, directory)
    if adapter == "debugpy":
        import debugpy
        import pydevd
        if start:
            if _listen_port is not None and _listen_port != port:
                raise RuntimeError("Restart BYgram before changing the VS Code debug port")
            if _listen_port is None:
                debugpy.configure(subProcess=False)
                debugpy.listen(("127.0.0.1", port), in_process_debug_adapter=True)
                _listen_port = port
            debugpy.trace_this_thread(True)
        else:
            debugpy.trace_this_thread(False)
            pydevd.stoptrace()
            # debugpy.listen is a one-shot API in this process.
    else:
        import pydevd_pycharm
        if start:
            pydevd_pycharm.settrace("127.0.0.1", port=port, suspend=False,
                                   patch_multiprocessing=False, trace_only_current_thread=False)
        else:
            pydevd_pycharm.stoptrace()


def debug(plugin, method, bridge, adapter="pdb", port=0, directory=None):
    target = getattr(plugin, method)
    if not callable(target):
        raise TypeError("DebugMethodNotCallable")
    if adapter != "pdb":
        context = getattr(plugin, "context", None)
        if context is not None:
            from client_utils import _invoke
            target = partial(_invoke, context, None, target)
        return _ide_debug(target, adapter, port, directory)
    terminal = _Terminal(bridge)
    debugger = pdb.Pdb(stdin=terminal, stdout=terminal, readrc=False, nosigint=True)
    debugger.use_rawinput = False
    debugger.runcall(target)
