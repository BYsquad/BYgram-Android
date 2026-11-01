from bygram import Plugin


class ThemePlugin(Plugin):
    def on_enable(self):
        self.context.theme.override("actionBarDefaultTitle", "#ff5070d8")
