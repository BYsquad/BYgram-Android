"""Run with python Tools/check_theme.py; guard palette selection and text contrast."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1] / "TMessagesProj/src/main"
source = (root / "java/org/telegram/ui/ActionBar/Theme.java").read_text(encoding="utf-8")
refresh = source.split("public static void refreshThemeColors(boolean bg, boolean messages) {", 1)[1].split("public static boolean hasHue", 1)[0]
assert "currentColors = currentColorsNoAccent.clone();" in refresh
assert "UI_MODE_NIGHT" not in refresh and "night.attheme" not in refresh

# Execute the production file reader and integer parser with small Android API stand-ins.
reader = source.split("    public static SparseIntArray getThemeFileValues(File", 1)[1].split("    public static void createCommonResources", 1)[0]
utilities = (root / "java/org/telegram/messenger/Utilities.java").read_text(encoding="utf-8")
parse_int = utilities.split("    public static Integer parseInt(CharSequence", 1)[1].split("    private static int parseInt", 1)[0]
harness = r'''
import java.io.*;
import java.nio.file.*;
import java.util.*;
class ThemeParserCheck {
    static final int key_wallpaperFileOffset = 99;
    static File getAssetFile(String name) { return new File(name); }
    static class SparseIntArray extends HashMap<Integer, Integer> {
        int get(int key) { return getOrDefault(key, 0); }
    }
    static class ThemeColors {
        static int stringKeyToInt(String key) { return key.equals("text") ? 1 : 2; }
    }
    static class FileLog {
        static void e(Throwable error) { throw new AssertionError(error); }
    }
    static class Color {
        static int parseColor(String value) {
            if (!value.matches("#[0-9a-fA-F]{8}")) throw new IllegalArgumentException(value);
            return (int) Long.parseLong(value.substring(1), 16);
        }
    }
    static class Utilities { public static Integer parseInt(CharSequence PARSE_INT }
    public static SparseIntArray getThemeFileValues(File READER
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        for (String newline : new String[]{"\n", "\r\n"}) {
            for (String value : new String[]{"-1", "#ffffffff"}) {
                Files.writeString(file.toPath(), "text=" + value + newline + "bg=-15198183" + newline);
                SparseIntArray colors = getThemeFileValues(file, null, null);
                if (colors.get(1) != -1 || colors.get(2) != -15198183)
                    throw new AssertionError("Lost color: " + value + " newline=" + newline.length());
            }
        }
        System.out.println("OK: production theme reader, decimal/hex colors with LF and CRLF");
    }
}
'''.replace("PARSE_INT", parse_int).replace("READER", reader)
java = str(Path(os.environ["JAVA_HOME"]) / "bin/java.exe") if "JAVA_HOME" in os.environ else shutil.which("java")
assert java, "Set JAVA_HOME to JDK 17+ to run the theme reader regression check"
with tempfile.TemporaryDirectory() as directory:
    fixture = Path(directory)
    (fixture / "ThemeParserCheck.java").write_text(harness, encoding="utf-8")
    subprocess.run([java, str(fixture / "ThemeParserCheck.java"), str(fixture / "test.attheme")], check=True)


def luminance(color):
    channels = [(color >> shift & 255) / 255 for shift in (16, 8, 0)]
    return sum((v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4) * w
               for v, w in zip(channels, (.2126, .7152, .0722)))


for asset in ("night.attheme", "darkblue.attheme"):
    colors = {}
    for line in (root / "assets" / asset).read_text().splitlines():
        key, value = line.split("=", 1)
        colors[key] = int(value[1:], 16) if value.startswith("#") else int(value)
    pairs = [("windowBackgroundWhiteBlackText", "windowBackgroundWhite"),
         ("chats_name", "windowBackgroundWhite"), ("chats_message", "windowBackgroundWhite"),
         ("dialogTextBlack", "dialogBackground"), ("chat_messagePanelHint", "chat_messagePanelBackground"),
         ("chat_messageTextIn", "chat_inBubble"), ("chat_messageTextOut", "chat_outBubble")]
    pairs += [(key, "windowBackgroundWhite") for key in colors if key.startswith("windowBackgroundWhiteGrayText")]
    pairs += [(key, "dialogBackground") for key in colors if key.startswith("dialogTextGray")]
    for text, background in pairs:
        light, dark = sorted((luminance(colors[text]), luminance(colors[background])), reverse=True)
        ratio = (light + .05) / (dark + .05)
        assert ratio >= 4.5, f"{asset}: {text} on {background}: {ratio:.2f}:1"
    print(f"OK: {asset}; {len(pairs)} text/background pairs >= 4.5:1")
