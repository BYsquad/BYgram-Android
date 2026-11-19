from bygram import Plugin
from bygram.settings import Switch


class AccountPlugin(Plugin):
    subscription = None

    def on_enable(self):
        self.context.ui.add_menu("chat.actions", "observe", "Observe this account", self.observe)

    def create_settings(self):
        return [Switch("observe", "Log page names", True, scope="account")]

    def observe(self, event):
        settings = self.context.account_settings(event.account)
        if self.subscription is not None:
            self.subscription.close()
        self.subscription = self.context.events.subscribe(
            "ui.page_opened", lambda page: self.opened(page, settings), account=event.account)

    def opened(self, page, settings):
        if settings.get("observe", True):
            self.context.log.info("page_opened: " + page.page)
