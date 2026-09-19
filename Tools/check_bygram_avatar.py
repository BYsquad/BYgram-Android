"""Check avatar shape on an unlocked Russian BYgram UI; restore its saved radius.

Run: python Tools/check_bygram_avatar.py
Requires ADB, Pillow and a visible avatar (photo or initials).
"""
import re
import time

from PIL import Image, ImageChops

from check_bygram_preferences import OUT, adb, back, capture, click, open_root, snapshot


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds"))))


def slider():
    return next(n for n in snapshot().iter("node") if n.get("class") == "android.widget.SeekBar")


def radius():
    node = slider()
    label = (node.get("text") or node.get("content-desc")).split(",")[0]
    return {"Квадрат": 0, "Круг": 28}.get(label, int(label) if label.isdigit() else -1)


def set_radius(value):
    left, top, right, bottom = bounds(slider())
    density = (bottom - top) / 38
    y = (top + bottom) // 2
    # Move away first so tapping near the existing thumb still selects the target.
    adb("shell", "input", "tap", str(right - 1 if value < 14 else left + 1), str(y))
    x = round(left + 12 * density + (right - left - 32 * density) * value / 28)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.5)
    assert radius() == value, f"Radius was not set to {value}"


def preview(name):
    root = snapshot()
    slider_bottom = bounds(next(n for n in root.iter("node") if n.get("class") == "android.widget.SeekBar"))[3]
    avatar = next(n for n in root.iter("node") if n.get("class") == "android.view.View"
                  and bounds(n)[1] > slider_bottom
                  and bounds(n)[2] - bounds(n)[0] == bounds(n)[3] - bounds(n)[1]
                  and bounds(n)[2] - bounds(n)[0] > 0)
    capture(name)
    return Image.open(OUT / f"{name}.png").convert("RGB").crop(bounds(avatar))


if __name__ == "__main__":
    open_root()
    click("Внешний вид")
    original = radius()
    assert 0 <= original <= 28, "Cannot read the saved radius"
    try:
        set_radius(28)
        circle = preview("avatar-circle-checked")
        for value in (0, 1):
            set_radius(value)
            square = preview(f"avatar-radius-{value}-checked")
            assert circle.size == square.size, "Preview bounds changed"
            size = circle.width
            inset, patch = max(1, size // 20), max(1, size // 10)
            changed = 0
            for x in (inset, size - inset - patch):
                for y in (inset, size - inset - patch):
                    diff = ImageChops.difference(circle, square).crop((x, y, x + patch, y + patch))
                    changed += sum(max(pixel) > 20 for pixel in diff.getdata())
            assert changed > patch * patch, f"Avatar corners stayed circular at radius {value}"
    finally:
        set_radius(original)
        back()
    print("PASS: avatar corners change at 0 and 1; original radius restored")
