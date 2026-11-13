"""Plugin-scoped logging with Python logging levels and handlers."""
import logging
import pprint
from contextvars import ContextVar
from dataclasses import dataclass
from enum import IntEnum

from client_utils import _context


class LogLevel(IntEnum):
    DEBUG = logging.DEBUG
    INFO = logging.INFO
    WARNING = logging.WARNING
    ERROR = logging.ERROR
    CRITICAL = logging.CRITICAL


@dataclass(frozen=True)
class LogRecord:
    level: LogLevel
    tag: str
    message: str
    location: str | None
    traceback: str | None
    created: float
    text: str


_dispatching = ContextVar("bygram_log_handler", default=False)


def dump(value):
    return pprint.pformat(value, depth=5)


class _HostHandler(logging.Handler):
    def __init__(self, context):
        super().__init__()
        self.context = context

    def emit(self, record):
        if self.context._bridge.active():
            self.context._bridge.log(record.levelname.lower(), self.format(record))


class _PluginLogger(logging.Logger):
    def __init__(self, context):
        super().__init__(context.plugin_id, LogLevel.DEBUG)
        self.context = context
        self.callbacks = []
        self.addHandler(_HostHandler(context))

    def dump(self, value, level=LogLevel.DEBUG):
        self.log(level, dump(value))

    def add_handler(self, callback):
        if callback not in self.callbacks:
            self.callbacks.append(callback)
        return callback

    def remove_handler(self, callback):
        if callback in self.callbacks:
            self.callbacks.remove(callback)

    def callHandlers(self, record):
        super().callHandlers(record)
        if not self.callbacks or _dispatching.get():
            return
        token = _dispatching.set(True)
        try:
            formatter = logging.Formatter()
            text = formatter.format(record)
            level = LogLevel(min(LogLevel, key=lambda value: abs(value - record.levelno)))
            event = LogRecord(level, record.name, record.getMessage(),
                              f"{record.filename}:{record.funcName}:{record.lineno}",
                              record.exc_text, record.created, text)
            for callback in tuple(self.callbacks):
                try:
                    callback(event)
                except Exception:
                    self.exception("Plugin log handler failed")
        finally:
            _dispatching.reset(token)


def get_logger():
    context = _context()
    logger = getattr(context, "_compat_logger", None)
    if logger is None:
        logger = context._compat_logger = _PluginLogger(context)
    return logger
