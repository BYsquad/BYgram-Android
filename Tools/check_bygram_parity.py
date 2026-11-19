"""ADB UI checks; requires the parity APK and an unlocked, signed-in BYgram.

python Tools/check_bygram_parity.py --serial DEVICE
Uses Android CLI for UI observations and restores switches changed by the check.
"""
import argparse
import json
import re
import shlex
import subprocess
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
args = parser.parse_args()
android = Path.home() / ".android/bin/android-cli.exe"
output = Path("build/parity-device")
output.mkdir(parents=True, exist_ok=True)
results = []


def adb(*command):
    return subprocess.run(["adb", "-s", args.serial, *command], check=True, capture_output=True).stdout


def nodes(tree):
    for node in tree:
        yield node
        yield from nodes(node.get("children", []))


def layout():
    subprocess.run([str(android), "layout", "--device", args.serial, "--output", str(output / "current.json")],
                   check=True, capture_output=True)
    return list(nodes(json.loads((output / "current.json").read_text(encoding="utf-8"))))


def find(label, current=None):
    return next((n for n in (layout() if current is None else current)
                 if label in (n.get("text"), n.get("content-desc"))), None)


def click(label):
    node = find(label)
    assert node, f"Missing: {label}"
    x, y = re.findall(r"\d+", node["center"])
    adb("shell", "input", "tap", x, y)
    time.sleep(.6)


def open_section(section, key=""):
    uri = f"tg://bygram_settings?section={section}&key={key}"
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", shlex.quote(uri), "com.bygram.mobile")
    time.sleep(.7)


def save(name):
    current = layout()
    (output / (name + ".json")).write_text(json.dumps(current, ensure_ascii=False, indent=2), encoding="utf-8")
    results.append(name)
    return current


def checked(label):
    current = layout()
    parents = {id(child): parent for parent in current for child in parent.get("children", [])}
    node = find(label, current)
    assert node, label
    while node is not None:
        if "CHECKABLE" in node.get("interactions", []):
            return "CHECKED" in node.get("state", [])
        candidates = [n for n in nodes([node]) if "CHECKABLE" in n.get("interactions", [])]
        if len(candidates) == 1:
            return "CHECKED" in candidates[0].get("state", [])
        node = parents.get(id(node))
    raise AssertionError(f"No unambiguous accessible switch state: {label}")


def toggle_roundtrip(section, key, label):
    open_section(section, key)
    node = find(label)
    assert node, label
    before = checked(label)
    click(label)
    try:
        assert checked(label) != before, f"Switch did not change: {label}"
        save(key)
    finally:
        if checked(label) != before:
            click(label)
    assert checked(label) == before


try:
    open_section(4, "bygram_double_tap_in")
    click("Двойное нажатие на входящие")
    current = save("incoming-actions")
    assert not find("Редактировать своё сообщение", current)
    click("Отмена")
    click("Двойное нажатие на исходящие")
    assert find("Редактировать своё сообщение")
    save("outgoing-actions")
    click("Отмена")

    open_section(6, "bygram_bottom_navigation")
    click("Нижняя навигация")
    assert find("Плавающая")
    save("navigation-modes")
    click("Отмена")
    click("Планшетный режим (нужен перезапуск)")
    assert find("Авто") and find("Включён") and find("Выключен")
    save("tablet-modes")
    click("Отмена")

    for section, key, label in [
        (2, "bygram_quick_channels", "Быстрый свайп к следующему каналу"),
        (2, "bygram_quick_topics", "Быстрый свайп к следующей теме"),
        (2, "bygram_camera_stabilization", "Стабилизация видео Camera2"),
        (2, "bygram_camera_wide_zoom", "Широкоугольный и плавный зум Camera2 (если поддерживается)"),
        (5, "bygram_menu_details", "Сведения о сообщении"),
        (5, "bygram_menu_save", "Избранное"),
        (5, "bygram_menu_repeat", "Повторить через черновик"),
        (5, "bygram_menu_copy_photo", "Копировать фото"),
    ]:
        toggle_roundtrip(section, key, label)

    open_section(2, "bygram_camera_fps")
    click("Частота кадров Camera2")
    assert all(find(label) for label in ("Авто", "24 FPS", "30 FPS", "60 FPS"))
    save("camera-fps")
    click("Отмена")
    open_section(3)
    save("account-folders-preview")
    open_section(7)
    assert find("Показывать кнопку «Перевести»") or find("Переводить чаты целиком")
    save("settings-search-index")
    print("PASS: " + ", ".join(results), flush=True)
finally:
    (output / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
