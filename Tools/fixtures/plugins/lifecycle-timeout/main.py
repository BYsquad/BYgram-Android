from bygram import Plugin
import time

class Main(Plugin):
    def on_enable(self):
        while True: time.sleep(.1)
