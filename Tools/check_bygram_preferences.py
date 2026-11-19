"""ADB smoke check for the Russian BYgram settings UI; restores changed switches.

Run: python Tools/check_bygram_preferences.py
Requires an unlocked device, an authorized BYgram account and the new APK.
"""
import re
import json
import subprocess
import time
from pathlib import Path

PACKAGE = "com.bygram.mobile"
OUT = Path(__file__).resolve().parents[1] / "build" / "adb-preferences"


def adb(*args):
    return subprocess.run(["adb", *args], check=True, capture_output=True).stdout


def snapshot():
    result = subprocess.run(["android", "layout"], check=True, capture_output=True, text=True)
    return json.loads(result.stdout)


def find(root, text):
    return next((node for node in root if node.get("text") == text or node.get("contentDesc") == text), None)


def locate(text):
    for _ in range(8):
        root = snapshot()
        node = find(root, text)
        if node is not None:
            return root, node
        adb("shell", "input", "swipe", "540", "1500", "540", "500", "250")
        time.sleep(0.3)
    return root, None


def click(text):
    _, node = locate(text)
    if node is None:
        raise AssertionError(f"Missing UI element: {text}")
    center = node.get("center")
    x, y = center if isinstance(center, list) else map(int, re.findall(r"\d+", center))
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.4)


def checked(text):
    root, node = locate(text)
    assert node is not None, f"Missing switch: {text}"
    y1, y2 = [int(value) for value in re.findall(r"\d+", node["bounds"])][1::2]
    checks = [item for item in root if "checkable" in item.get("interactions", [])
              and y1 <= (item.get("center", [0, 0])[1] if isinstance(item.get("center"), list) else int(re.findall(r"\d+", item.get("center", "0,0"))[1])) <= y2]
    assert len(checks) == 1, f"Switch has no unambiguous accessible state: {text}"
    return "checked" in checks[0].get("state", [])


def capture(name):
    OUT.mkdir(parents=True, exist_ok=True)
    subprocess.run(["android", "screen", "capture", "-o", str(OUT / f"{name}.png")], check=True)
    (OUT / f"{name}.json").write_text(json.dumps(snapshot(), ensure_ascii=False, indent=2), encoding="utf-8")


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.4)


def open_root():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
    for _ in range(6):
        root = snapshot()
        if find(root, "Настройки") is not None:
            click("Настройки")
            click("Настройки BYgram")
            return
        back()
    raise AssertionError("Cannot reach main Settings tab")


def open_path(path):
    open_root()
    for section in path:
        click(section)


def check_section(path, switch, name):
    open_path(path)
    original = checked(switch)
    try:
        click(switch)
        assert checked(switch) != original, f"Switch did not change: {switch}"
        open_path(path)
        assert checked(switch) != original, f"Switch did not persist: {switch}"
        capture(name)
    finally:
        open_path(path)
        if checked(switch) != original:
            click(switch)
        assert checked(switch) == original, f"Switch was not restored: {switch}"


if __name__ == "__main__":
    open_root()
    capture("categories")
    for path, switch, name in [
        (["Кастомизация", "Основное"], "Фильтр «Zalgo»", "customization"),
        (["Другое"], "Analytics", "other"),
        (["Шпион"], "Сохранять историю правок", "spy"),
    ]:
        check_section(path, switch, name)
    open_path(["Режим призрака"])
    capture("ghost")
    print("PASS: current categories, persistence, restored switches, ghost/spy screens")
