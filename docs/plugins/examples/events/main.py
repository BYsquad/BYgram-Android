from bygram import Plugin


class EventPlugin(Plugin):
    def on_enable(self):
        self.context.events.subscribe("ui.page_opened", self.opened)

    def opened(self, event):
        self.context.log.info("page_opened: " + event.page)
