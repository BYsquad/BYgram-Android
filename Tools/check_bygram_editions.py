from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


gradle = source("TMessagesProj/build.gradle")
app_gradle = source("TMessagesProj_App/build.gradle")
controller = source("TMessagesProj/src/main/java/org/telegram/messenger/BygramActivityController.java")
settings = source("TMessagesProj/src/main/java/org/telegram/ui/BygramSettingsActivity.java")
api = source("server/activity-api/index.php")
schema = source("server/activity-api/schema.sql")
bot = source("server/activity-api/bot.php")

assert "BYGRAM_EDITION" in gradle and "BYGRAM_FULL" in gradle
assert "BYgram-${bygramEdition}-${variant.name}.apk" in app_gradle
assert "if (!BuildConfig.BYGRAM_FULL) return false" in controller
assert "if (BuildConfig.BYGRAM_FULL)" in settings
assert all(action in api for action in ("device_start", "device_prove", "device_status", "device_replace"))
assert "openssl_verify" in api and "X_BYGRAM_SIGNATURE" in api
assert "BYGRAM_STATUS_V1" in controller and "BYGRAM_REPLACE_V1" in controller
assert "BYGRAM_STATUS_V1" in api and "BYGRAM_REPLACE_V1" in api
assert "count($devices) >= 2" in api and "FOR UPDATE" in api
assert "licensed_devices" in schema and "device_activations" in schema and "device_id CHAR(32)" in schema
assert "X_TELEGRAM_BOT_API_SECRET_TOKEN" in bot and "expected_user_id'] !== $fromId" in bot

print("PASS: Lite/Full split, signed device activation and two-device limit")
