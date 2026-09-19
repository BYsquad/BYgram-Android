"""Read-only inventory of one nested ExteraGram settings screen."""
import json, re, subprocess, sys, time, xml.etree.ElementTree as ET
from pathlib import Path

package, entry, *path = sys.argv[1:]

def adb(*args):
    return subprocess.run(["adb", *args], check=True, capture_output=True).stdout

def root():
    adb("shell", "uiautomator", "dump", "/sdcard/mod-nested.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/mod-nested.xml"))

def click(text):
    for _ in range(16):
        node = next((n for n in root().iter("node") if n.get("text") == text or n.get("content-desc") == text), None)
        if node is not None:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2)); time.sleep(.6); return
        adb("shell", "input", "swipe", "600", "2200", "600", "650", "350"); time.sleep(.4)
    raise RuntimeError(f"Missing {text}")

adb("shell", "am", "force-stop", package)
adb("shell", "monkey", "-p", package, "-c", "android.intent.category.LAUNCHER", "1"); time.sleep(1)
for _ in range(8):
    if any((n.get("text") == "Настройки") for n in root().iter("node")):
        click("Настройки"); break
    adb("shell", "input", "keyevent", "4"); time.sleep(.5)
click(entry)
for label in path: click(label)

values = []
for _ in range(16):
    for node in root().iter("node"):
        value = node.get("text") or node.get("content-desc")
        if value and value not in values:
            values.append(value)
    adb("shell", "input", "swipe", "600", "2200", "600", "650", "350")
    time.sleep(.4)

output = Path("build") / ("nested-" + "-".join(path) + ".json")
output.write_text(json.dumps(values, ensure_ascii=False, indent=2), encoding="utf-8")
print(output)
