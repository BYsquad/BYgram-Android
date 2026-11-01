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
config = source("TMessagesProj/src/main/java/org/telegram/messenger/BygramConfig.java")
assert "if (!BuildConfig.BYGRAM_FULL) return false" in config
assert "bygramEdition == 'lite' ? 'org.telegram.messenger' : APP_PACKAGE" in app_gradle
assert "if (bygramEdition == 'full') apply plugin: 'com.google.gms.google-services'" in app_gradle
assert "['afatDebug', 'afatRelease', 'afatStandalone', 'bundleAfatRelease']" in app_gradle
assert "sourceSets.maybeCreate(variantName).manifest.srcFile 'src/lite/AndroidManifest.xml'" in app_gradle
assert "if (!isLicensed(account) || !BygramConfig.isActivityEnabled(account)" in controller
assert "(BuildConfig.BYGRAM_FULL && BygramConfig.getActivityViewers(account).isEmpty())" in controller
assert "if (!BuildConfig.BYGRAM_FULL) return viewers;" in controller
assert "if (!BuildConfig.BYGRAM_FULL || !isLicensed(account) || ownerId <= 0)" in controller
assert "if (BuildConfig.BYGRAM_FULL || fragment.getParentActivity() == null) return;" in settings
assert "return BuildConfig.BYGRAM_FULL && super.onFragmentCreate();" in settings
assert "toggleLiteActivity" in settings and "BygramActivityTestWarning" in settings
link = source("TMessagesProj/src/main/java/org/telegram/ui/LinkManager.java")
assert 'if (!BuildConfig.BYGRAM_FULL)' in link.split('private boolean handleBygramSettings', 1)[1].split('// tg://settings', 1)[0]
branding = source("TMessagesProj_App/src/lite/res/values/branding.xml")
assert '<string name="AppName">Telegram</string>' in branding
assert '@mipmap/ic_launcher' in branding
profile = source("TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java")
assert "if (myProfile && org.telegram.messenger.BuildConfig.BYGRAM_FULL)" in profile
assert "if (!org.telegram.messenger.BuildConfig.BYGRAM_FULL || bygramProfilePaused" in profile
chat = source("TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java")
assert "if (org.telegram.messenger.BuildConfig.BYGRAM_FULL && currentUser != null && !currentUser.bot" in chat
assert "if (!org.telegram.messenger.BuildConfig.BYGRAM_FULL || paused) return;" in chat
assert 'if (!org.telegram.messenger.BuildConfig.BYGRAM_FULL) return "";' in chat
video = source("TMessagesProj/src/main/java/org/telegram/ui/Components/BygramVideoWallpaper.java")
assert "if (!BuildConfig.BYGRAM_FULL) return null;" in video
assert "if (!BuildConfig.BYGRAM_FULL) { done.accept(false); return; }" in video
launch = source("TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java")
assert "if (!org.telegram.messenger.BuildConfig.BYGRAM_FULL) return;" in launch.split("private final Runnable profileViewsPoll", 1)[1].split("public final static", 1)[0]
assert "if (BuildConfig.BYGRAM_FULL)" in settings
assert all(action in api for action in ("device_start", "device_prove", "device_status", "device_replace"))
assert "openssl_verify" in api and "X_BYGRAM_SIGNATURE" in api
assert "BYGRAM_STATUS_V1" in controller and "BYGRAM_REPLACE_V1" in controller
assert "BYGRAM_STATUS_V1" in api and "BYGRAM_REPLACE_V1" in api
assert "count($devices) >= 2" in api and "FOR UPDATE" in api
assert "licensed_devices" in schema and "device_activations" in schema and "device_id CHAR(32)" in schema
assert "X_TELEGRAM_BOT_API_SECRET_TOKEN" in bot and "expected_user_id'] !== $fromId" in bot

print("PASS: Lite branding, customization gates, consented activity publishing, signed activation and device limit")
