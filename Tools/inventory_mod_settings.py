"""Read-only ADB inventory of all visible mod settings."""
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

package, entry, *sections = sys.argv[1:]
out = Path("build") / f"inventory-{package}"
out.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.run(["adb", *args], check=True, capture_output=True).stdout


def root():
    adb("shell", "uiautomator", "dump", "/sdcard/mod-inventory.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/mod-inventory.xml"))


def find(text):
    return next((n for n in root().iter("node") if n.get("text") == text or n.get("content-desc") == text), None)


def click(text):
    for _ in range(12):
        node = find(text)
        if node is not None:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(.6)
            return
        adb("shell", "input", "swipe", "600", "2200", "600", "700", "300")
        time.sleep(.35)
    raise RuntimeError(f"Missing {text}")


adb("shell", "monkey", "-p", package, "-c", "android.intent.category.LAUNCHER", "1")
time.sleep(1)
for _ in range(8):
    if find("Настройки") is not None:
        click("Настройки")
        break
    adb("shell", "input", "keyevent", "4")
    time.sleep(.5)
click(entry)

inventory = {}
for section in sections:
    click(section)
    labels = []
    for page in range(14):
        tree = root()
        (out / f"{section}-{page}.xml").write_bytes(ET.tostring(tree, encoding="utf-8"))
        for node in tree.iter("node"):
            value = node.get("text") or node.get("content-desc")
            if value and value not in labels:
                labels.append(value)
        adb("shell", "input", "swipe", "600", "2200", "600", "650", "350")
        time.sleep(.4)
    inventory[section] = labels
    adb("shell", "input", "keyevent", "4")
    time.sleep(.6)

(out / "inventory.json").write_text(json.dumps(inventory, ensure_ascii=False, indent=2), encoding="utf-8")
print(out / "inventory.json")
