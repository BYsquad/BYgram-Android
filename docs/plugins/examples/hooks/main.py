from bygram import Plugin


class HookPlugin(Plugin):
    def on_enable(self):
        self.context.hooks.method(
            "org.telegram.messenger.plugins.HookBridge$Probe", "value", ["int"],
            before=lambda call: call.set_arg(0, int(call.args[0]) + 1),
            after=lambda call: call.set_result(int(call.result) + 1))
