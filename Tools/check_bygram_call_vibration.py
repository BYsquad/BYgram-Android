"""Run the production call-vibration policy for every combination of switches."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
config = (JAVA / "messenger/BygramConfig.java").read_text(encoding="utf-8")
settings = (JAVA / "ui/BygramSettingsActivity.java").read_text(encoding="utf-8")
assert "case CALL_VIBRATION:" in config
assert "CALL_VIBRATION.equals(key)" in config.split("boolean defaultValue =", 1)[1].split(";", 1)[0]
assert 'if (BygramConfig.getBoolean(BygramConfig.DISABLE_VIBRATION)) {\n                    items.add(UItem.asCheck(10,' in settings
assert ".setChecked(BygramConfig.getBoolean(BygramConfig.CALL_VIBRATION))" in settings
for file in ("messenger/voip/VoIPService.java", "messenger/voip/VoIPPreNotificationService.java",
             "ui/GroupCallActivity.java", "ui/Components/GroupCallPip.java", "ui/Components/GroupCallPipAlertView.java"):
    text = (JAVA / file).read_text(encoding="utf-8")
    assert "BygramConfig.hapticFeedback(" not in text, file
    assert "bygram_disable_vibration" not in text and "BygramConfig.DISABLE_VIBRATION" not in text, file
assert "APP_ERROR.vibrate(true)" in (JAVA / "ui/GroupCallActivity.java").read_text(encoding="utf-8")

methods = config.split("public static int hapticFeedback(int effect) {", 1)[1].split("public static boolean isGhostEnabled()", 1)[0]
methods = "public static int hapticFeedback(int effect) {" + methods
methods = methods.replace("android.view.HapticFeedbackConstants.NO_HAPTICS", "-1")
source = '''class CallVibrationCheck {
    static final String DISABLE_VIBRATION = "disabled", CALL_VIBRATION = "calls";
    static boolean disabled, calls;
    static boolean getBoolean(String key) { return key.equals(DISABLE_VIBRATION) ? disabled : calls; }
    METHODS
    public static void main(String[] args) {
        for (int mask = 0; mask < 4; mask++) {
            disabled = (mask & 1) != 0;
            calls = (mask & 2) != 0;
            boolean expected = !disabled || calls;
            if (isCallVibrationEnabled() != expected
                    || callHapticFeedback(4) != (expected ? 4 : -1)
                    || hapticFeedback(4) != (disabled ? -1 : 4)) throw new AssertionError(mask);
        }
    }
}'''.replace("METHODS", methods)
with tempfile.TemporaryDirectory(prefix="bygram-call-vibration-") as directory:
    path = Path(directory) / "CallVibrationCheck.java"
    path.write_text(source, encoding="utf-8")
    subprocess.run(["javac", str(path)], check=True)
    subprocess.run(["java", "-cp", directory, "CallVibrationCheck"], check=True)
print("PASS: call vibration UI, persistence, call sites and all four switch combinations")
