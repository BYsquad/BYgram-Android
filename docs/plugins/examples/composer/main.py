from bygram import Plugin


class ComposerPlugin(Plugin):
    def on_enable(self):
        self.context.ui.add_menu("chat.actions", "uppercase", "Uppercase draft", self.transform)

    def transform(self, event):
        if not hasattr(event, "dialog_id"): return
        client = self.context.client(event.account)
        client.preview_text(event.dialog_id, client.draft_text(event.dialog_id).upper())
