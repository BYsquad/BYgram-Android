"""Exercise the actual relative-time/haptic branches and their UI wiring."""
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
config = (JAVA / "messenger/BygramConfig.java").read_text(encoding="utf-8")
locale = (JAVA / "messenger/LocaleController.java").read_text(encoding="utf-8")
settings = (JAVA / "ui/BygramSettingsActivity.java").read_text(encoding="utf-8")
profile = (JAVA / "ui/ProfileActivity.java").read_text(encoding="utf-8")
music = (JAVA / "ui/Components/ProfileMusicView.java").read_text(encoding="utf-8")
notifications = (JAVA / "messenger/NotificationsController.java").read_text(encoding="utf-8")

for key in ("HIDE_PHONE", "RELATIVE_ONLINE", "NO_UNARCHIVE_SWIPE", "DISABLE_VIBRATION"):
    assert f"case {key}:" in config, f"{key} is lost on restart/export/import/reset"
    assert f"BygramConfig.{key}" in settings, f"Missing UI for {key}"
defaults = config.split("boolean defaultValue =", 1)[1].split(";", 1)[0]
assert "DISABLE_VIBRATION" not in defaults, "Vibration must be enabled by default"
assert ".setChecked(!BygramConfig.getBoolean(BygramConfig.DISABLE_VIBRATION))" in settings
assert "UserObject.isUserSelf(user) && BygramConfig.getBoolean(BygramConfig.HIDE_PHONE)" in profile
assert "if (!BygramConfig.getBoolean(BygramConfig.HIDE_PHONE)) numberRow" in profile
assert "if (folderId == 1 && BygramConfig.getBoolean(BygramConfig.NO_UNARCHIVE_SWIPE)) return 0;" in (JAVA / "ui/DialogsActivity.java").read_text(encoding="utf-8")
assert "MessageObject.getArtworkUrl(document, true)" in music
assert "coverImage.hasBitmapImage()" in music, "A loaded thumbnail must be drawn too"
assert "playing.currentAccount == account" in music, "Do not reuse another account's art"
assert "generation != bygramBackgroundGeneration" in profile, "A late profile background response can replace the current choice"
assert "paint.setColor(peerColor != null ? Color.BLACK : currentColor)" in profile, "Colored profiles need a dark scrim behind their white title"
assert "bygramChatDeletedAt" in (JAVA / "ui/Cells/ChatActionCell.java").read_text(encoding="utf-8"), "Service-message anchors lose the deletion notice"
assert "coverDocumentId == documentId" in music, "Old background tasks must not replace another track"
for event in ("fileLoaded", "messagePlayingDidStart", "messagePlayingPlayStateChanged"):
    assert f"addObserver(this, NotificationCenter.{event})" in music
    assert f"removeObserver(this, NotificationCenter.{event})" in music
assert 'key += "_bygram_no_vibration"' in notifications, "Keep normal notification channels intact"
assert "if (!disableVibration && hasVibration != vibrate)" in notifications
for name in ("VoIPService.java", "VoIPPreNotificationService.java"):
    assert "if (org.telegram.messenger.BygramConfig.isCallVibrationEnabled()" in (JAVA / "messenger/voip" / name).read_text(encoding="utf-8")

for path in (JAVA.parent.parent).rglob("*.java"):
    source = path.read_text(encoding="utf-8")
    source = re.sub(r"/\*.*?\*/|//[^\n]*", "", source, flags=re.S)
    for call in re.finditer(r"\bperformHapticFeedback\(\s*([^\s]+)", source):
        assert any(gate in call[1] for gate in ("BygramConfig.hapticFeedback(", "BygramConfig.callHapticFeedback(")), f"Ungated haptic in {path}"

names = ("BygramHideOwnPhoneInfo", "BygramAppVibration", "BygramLastSeenDays", "BygramLastSeenMonths", "BygramLastSeenYears")
for language in ("values", "values-ru", "values-uk"):
    strings = {node.attrib["name"]: node.text for node in ET.parse(ROOT / "TMessagesProj/src/main/res" / language / "bygram_preferences.xml").getroot().findall("string")}
    assert all(strings.get(name) for name in names), f"Missing translation: {language}"

# Compile the production branches with clock/resource stubs, so boundaries test Java arithmetic.
relative = locale.split("long elapsed =", 1)[1].split("\n            }", 1)[0]
relative = "long elapsed =" + relative
relative = re.sub(r"R.string.(\w+)", r'"\1"', relative)
haptic = config.split("public static int hapticFeedback(int effect) {", 1)[1].split("}", 1)[0]
haptic = haptic.replace("android.view.HapticFeedbackConstants.NO_HAPTICS", "-1")
haptic = haptic.replace("getBoolean(DISABLE_VIBRATION)", "BygramConfig.getBoolean(BygramConfig.DISABLE_VIBRATION)")
dialogs = (JAVA / "ui/DialogsActivity.java").read_text(encoding="utf-8")
search_methods = "\n".join("static " + signature.split(" ", 1)[1] + dialogs.split(signature, 1)[1].split("\n    }", 1)[0] + "\n    }" for signature in ("private int getSearchFieldHeight() {", "private boolean keepSearchCollapsed() {"))
source = """
class ProfileCustomizationCheck {
    static class BygramConfig {
        static final String DISABLE_VIBRATION = "vibration";
        static final String COLLAPSE_SEARCH_ON_START = "collapse";
        static final String KEEP_SEARCH_COLLAPSED = "keep";
        static boolean disabled, collapse, keep;
        static boolean getBoolean(String key) { return key.equals(DISABLE_VIBRATION) ? disabled : key.equals(COLLAPSE_SEARCH_ON_START) ? collapse : keep; }
    }
    static boolean onlySelect, searching, searchIsShowed;
    static int initialDialogsType, folderId;
    static long communityId;
    static final int DIALOGS_TYPE_DEFAULT = 0, SEARCH_FIELD_HEIGHT = 48;
    static int dp(int value) { return value; }
    SEARCH_METHODS
    static class ConnectionsManager {
        static ConnectionsManager getInstance(int account) { return new ConnectionsManager(); }
        int getCurrentTime() { return 2000000000; }
    }
    static String getString(String key) { return key; }
    static String formatString(String key, long count) { return key + ":" + count; }
    static String relative(long delta) {
        int currentAccount = 0;
        long date = 2000000000L - delta;
        BODY
        return "absolute";
    }
    static int hapticFeedback(int effect) { HAPTIC }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        check(relative(-1).equals("absolute"));
        check(relative(0).equals("BygramLastSeenNow"));
        check(relative(59).equals("BygramLastSeenNow"));
        check(relative(60).equals("BygramLastSeenMinutes:1"));
        check(relative(300).equals("BygramLastSeenMinutes:5"));
        check(relative(3599).equals("BygramLastSeenMinutes:59"));
        check(relative(3600).equals("BygramLastSeenHours:1"));
        check(relative(86399).equals("BygramLastSeenHours:23"));
        check(relative(86400).equals("BygramLastSeenDays:1"));
        check(relative(30L * 86400).equals("BygramLastSeenMonths:1"));
        check(relative(365L * 86400).equals("BygramLastSeenYears:1"));
        check(hapticFeedback(0) == 0 && hapticFeedback(4) == 4);
        BygramConfig.disabled = true;
        check(hapticFeedback(0) == -1 && hapticFeedback(4) == -1);
        check(getSearchFieldHeight() == 48);
        BygramConfig.collapse = BygramConfig.keep = true;
        check(getSearchFieldHeight() == 0);
        searching = true; check(getSearchFieldHeight() == 48); searching = false;
        searchIsShowed = true; check(getSearchFieldHeight() == 48); searchIsShowed = false;
        onlySelect = true; check(getSearchFieldHeight() == 48); onlySelect = false;
        folderId = 1; check(getSearchFieldHeight() == 48); folderId = 0;
        communityId = 1; check(getSearchFieldHeight() == 48); communityId = 0;
        initialDialogsType = 1; check(getSearchFieldHeight() == 48); initialDialogsType = 0;
        BygramConfig.collapse = false; check(getSearchFieldHeight() == 48);
        BygramConfig.collapse = true; BygramConfig.keep = false; check(getSearchFieldHeight() == 48);
    }
}
""".replace("BODY", relative).replace("HAPTIC", haptic).replace("SEARCH_METHODS", search_methods)
with tempfile.TemporaryDirectory(prefix="bygram-customization-") as directory:
    path = Path(directory) / "ProfileCustomizationCheck.java"
    path.write_text(source, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(path)], check=True)
    subprocess.run(["java", "-cp", directory, "ProfileCustomizationCheck"], check=True)
print("PASS: customization wiring, haptic coverage, translations and 23 Java boundary checks")
