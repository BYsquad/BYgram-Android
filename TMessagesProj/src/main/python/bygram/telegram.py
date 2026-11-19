from dataclasses import dataclass
from enum import Enum, auto

from . import Handle


class HookStrategy(Enum):
    DEFAULT = auto()
    CANCEL = auto()
    MODIFY = auto()
    MODIFY_FINAL = auto()


@dataclass
class HookResult:
    strategy: HookStrategy = HookStrategy.DEFAULT
    value: object = None


class TelegramHooks:
    def __init__(self, bridge):
        self._bridge = bridge

    def intercept(self, kind, callback, name="", *, match_substring=False, priority=0):
        if not callable(callback):
            raise TypeError("InvalidTelegramHook")
        from java import jclass
        native = jclass("org.telegram.messenger.plugins.PluginInterceptors$Result")

        def invoke(name, account, value, detail):
            result = callback(name, account, value, detail)
            if result is None:
                return None
            if not isinstance(result, HookResult) or not isinstance(result.strategy, HookStrategy):
                raise TypeError("InvalidTelegramHookResult")
            if result.strategy is HookStrategy.DEFAULT:
                return None
            if result.strategy is HookStrategy.CANCEL:
                return native.cancel()
            return native.replace(result.value, result.strategy is HookStrategy.MODIFY_FINAL)

        return Handle(self._bridge.intercept(kind, name, match_substring, priority, invoke))
