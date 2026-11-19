"""Explicit device acceptance; sends only its own marker to each granted account's Saved Messages."""
from bygram import Plugin, _selftest
from bygram.settings import Text, Switch, TextInput, Select, Slider, Action
from java import jclass, dynamic_proxy
import gc
import json
import threading
import time
import traceback
import weakref


class Main(Plugin):
    def on_enable(self):
        self.theme_handle = None
        self.busy = False
        self.last = "Ready"
        self.context.ui.add_menu("dialogs.toolbar", "qa", "SDK verification", lambda event: self.open())

    def open(self):
        self.context.ui.open_page({"title": "SDK verification", "items": self.create_settings()})

    def create_settings(self):
        return [Text("Device acceptance: synthetic hooks/UI and Saved Messages only"),
            Switch("switch", "QA switch", False), TextInput("label", "QA input", "Test", 40),
            Select("choice", "QA select", [{"title":"First","value":"one"},{"title":"Second","value":"two"}], "one"),
            Slider("amount", "QA slider", 0, 10, 1, 3),
            Action("Run hooks and cleanup checks", self.start),
            Action("Theme: highlight / restore", self.theme),
            Action("Send test to granted Saved Messages", self.send),
            Action("Show result", lambda: self.context.ui.bulletin(self.last[:1000]))]

    def save(self, result):
        self.last = result
        self.context.storage.write_text("result.txt", result)
        self.context.log.info(result)
        jclass("android.util.Log").i("BYgramDeviceAcceptance", result)
        self.context.ui.bulletin(result[:1000])

    def start(self):
        if self.busy: return
        self.busy = True
        threading.Thread(target=self.check, daemon=True).start()

    def check(self):
        try:
            started = time.monotonic()
            _selftest.original_error(self)
            self.safe_hooks()
            for _ in range(3): _selftest.repeat(self)
            refs = []
            for i in range(100):
                def callback(event): pass
                refs.append(weakref.ref(callback))
                handle = self.context.ui.add_menu("dialogs.toolbar", "cycle", "QA cycle", callback)
                handle.close()
                callback = handle = None
            for _ in range(5):
                gc.collect(); jclass("java.lang.System").gc(); time.sleep(.1)
            assert all(ref() is None for ref in refs), "menu callbacks retained"
            self.save("PASS: Release hooks/original throwable/constructor/private/overload/reentry; 300 hook cycles, 100 menu cleanup cycles, GC; %.2fs" % (time.monotonic()-started))
        except Exception: self.save("FAIL: " + traceback.format_exc())
        finally: self.busy = False

    def safe_hooks(self):
        target = "org.telegram.messenger.plugins.HookBridge$Probe"
        probe = jclass(target)
        visits = []
        constructor = self.context.hooks.constructor(target, before=lambda call: visits.append("before"), after=lambda call: visits.append("after"))
        try:
            instance = probe()
            assert visits == ["before", "after"]
        finally: constructor.close()
        private = self.context.hooks.method(target, "label", ["java.lang.String"], before=lambda call: call.set_arg(0, "changed"), after=lambda call: call.set_result("after"))
        overload = self.context.hooks.method(target, "label", ["int"], after=lambda call: call.set_result("overloaded"))
        try: assert instance.privateLabel(None) == "after" and instance.label(3) == "overloaded"
        finally: private.close(); overload.close()
        def reentrant(call): assert probe.value(1) == 2
        recursive = self.context.hooks.method(target, "value", ["int"], before=reentrant)
        try: assert probe.value(2) == 4
        finally: recursive.close()
        replacement = self.context.hooks.replace(target, "value", lambda call: call.call_original(), ["int"])
        try:
            previous = probe.calls
            assert probe.value(2) == 4 and probe.calls == previous + 1
        finally: replacement.close()

    def theme(self):
        if self.theme_handle is not None:
            self.theme_handle.close(); self.theme_handle = None
        else: self.theme_handle = self.context.theme.override("windowBackgroundGray", "#ff9b1b6e")

    def send(self):
        if self.busy: return
        self.busy = True
        threading.Thread(target=self.check_accounts, daemon=True).start()

    def ui(self, function):
        done = threading.Event()
        def run():
            try: function()
            finally: done.set()
        self.context.ui.run(run)
        assert done.wait(10), "UI queue blocked"

    def check_accounts(self):
        records = []
        try:
            assert self.context.accounts, "Choose test accounts in the plugin card first"
            center = jclass("org.telegram.messenger.NotificationCenter")
            for slot in self.context.accounts:
                user = jclass("org.telegram.messenger.UserConfig").getInstance(slot).getClientUserId()
                marker = "BYgram SDK verification " + str(int(time.time()*1000))
                received = threading.Event(); delivered = threading.Event()
                ids = []
                def observed(event):
                    if event.dialog_id == user:
                        ids.extend(event.message_ids); received.set()
                subscription = self.context.events.subscribe("messages.received", observed, account=slot)
                class Receipt(dynamic_proxy(jclass("org.telegram.messenger.NotificationCenter$NotificationCenterDelegate"))):
                    def didReceivedNotification(self, event, account, args):
                        try:
                            if int(account) != slot or int(args[3]) != user or int(args[1]) <= 0: return
                            if int(args[0]) in ids or args[2] is not None and str(args[2].message) == marker: delivered.set()
                        except Exception:
                            pass
                receipt = Receipt()
                self.ui(lambda: center.getInstance(slot).addObserver(receipt, center.messageReceivedByServer))
                try:
                    self.context.account_settings(slot).set("qa", True)
                    assert self.context.account_settings(slot).get("qa") is True
                    self.context.client(slot).send_text(user, marker)
                    assert received.wait(20), "SDK observe did not receive Saved Messages event"
                    assert delivered.wait(30), "Telegram did not confirm test delivery"
                    records.append({"slot":slot,"observe":True,"server_confirmed":True,"account_settings":True})
                finally:
                    subscription.close()
                    self.ui(lambda: center.getInstance(slot).removeObserver(receipt, center.messageReceivedByServer))
            self.save("PASS: actual account identity/settings/send/observe; Saved Messages only; " + json.dumps(records))
        except Exception: self.save("FAIL: " + traceback.format_exc())
        finally: self.busy = False

    def on_disable(self):
        if self.theme_handle is not None: self.theme_handle.close()
