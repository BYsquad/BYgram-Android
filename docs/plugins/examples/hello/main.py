from bygram import Plugin
from bygram.settings import Switch, TextInput, Action


class HelloPlugin(Plugin):
    def on_enable(self):
        self.context.ui.add_menu("dialogs.toolbar", "hello", "Hello BYgram", self.show)

    def show(self, event=None):
        if self.context.settings.get("enabled", True):
            self.context.ui.bulletin(self.context.settings.get("text", "Hello BYgram!"))

    def create_settings(self):
        return [Switch("enabled", "Show greeting", True),
                TextInput("text", "Greeting", "Hello BYgram!", max_length=120),
                Action("Preview", self.show)]
