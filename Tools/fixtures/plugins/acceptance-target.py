from bygram import Plugin
import builtins
import weakref


class Main(Plugin):
    def on_enable(self):
        builtins.sdk_acceptance[self.context.plugin_id] = self
        def callback(event): pass
        builtins.sdk_callback_refs.append(weakref.ref(callback))
        self.context.ui.add_menu("dialogs.toolbar", "fixture", "SDK fixture", callback)
        self.context.settings.set("generation", self.context.generation)

