from pathlib import Path
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


config = read("TMessagesProj/src/main/java/org/telegram/messenger/BygramConfig.java")
activity = read("TMessagesProj/src/main/java/org/telegram/messenger/BygramActivityController.java")
settings = read("TMessagesProj/src/main/java/org/telegram/ui/BygramSettingsActivity.java")
translate = read("TMessagesProj/src/main/java/org/telegram/ui/Components/TranslateAlert2.java")
application = read("TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java")
images = read("TMessagesProj/src/main/java/org/telegram/messenger/ImageReceiver.java")
api = read("server/activity-api/index.php")
admin_api = read("server/activity-api/admin-api.php")
admin_ui = read("server/site/src/admin.jsx")
self_test = read("server/activity-api/self-test.php")
messages = read("TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java")
stories = read("TMessagesProj/src/main/java/org/telegram/ui/Stories/StoriesController.java")


def resource_names(folder: str, file: str) -> set[str]:
    root = ET.parse(ROOT / "TMessagesProj/src/main/res" / folder / file).getroot()
    return {item.attrib["name"] for item in root if "name" in item.attrib}


english_resources = resource_names("values", "bygram_strings.xml") | resource_names("values", "bygram_preferences.xml")
russian_resources = resource_names("values-ru", "bygram_strings.xml") | resource_names("values-ru", "bygram_preferences.xml")

checks = {
    "unlicensed users receive the manager URL": "'bot_url' => managerUrl()" in api and "appSetting('manager_username'" in api and 'callback.run(false, botUrl)' in activity,
    "license status returns the manager URL": "'manager_url' => managerUrl()" in api,
    "manual authorization remains in the admin panel": "auth.confirm" in admin_api and "auth.confirm" in admin_ui,
    "activation links use Telegram start payloads": "?start=bind_" in api,
    "telemetry has one synchronisation method": "applyTelemetrySettings" in config and "applyTelemetrySettings" in application,
    "activity master is account-specific": "isActivityEnabled(int account)" in config and "isActivityEnabled(account)" in activity,
    "activity is opt-in": 'activityEnabledKey(account), false' in config,
    "reset clears activity viewers": "clearActivitySettings" in config and "clearSettings" in config and "LauncherIcon.DEFAULT" in settings,
    "forum avatars obey the visible corner setting": "bygram_uniform_avatars" not in images,
    "first read event uses the previous outbox maximum": "previousMaxMessageId" in config and "dialogId, value, update.max_id, date" in messages,
    "spy pruning runs and trims individual revisions": "BygramConfig::pruneSpyData" in application and "history.remove(i)" in config,
    "activity ACL waits for server acknowledgement": 'key("viewers_dirty", userId)' in activity and 'response.optBoolean("ok")' in activity,
    "activity publication waits for ACL synchronisation": "if (viewersDirty(selfId))" in activity,
    "secret read-on-action supplies a read date": "DialogObject.isEncryptedDialog(dialogId) ? getConnectionsManager().getCurrentTime()" in messages,
    "Ghost keeps local unread state consistent": "boolean blockReadReceipt = BygramConfig.shouldBlockReadReceipts()" in messages and "if (!blockReadReceipt)" in stories,
    "Russian BYgram resources match the source key set": english_resources == russian_resources,
    "backend self-test cannot use production database": "self-test never uses the configured production database" in self_test and "CREATE DATABASE" in self_test and "DROP DATABASE IF EXISTS" in self_test,
    "viewer search is locale-stable": "toLowerCase(Locale.ROOT)" in settings,
    "unknown selected viewers remain removable": "for (Map.Entry<Long, Integer> entry : selected.entrySet())" in settings and 'return "ID " + userId' in settings,
    "Telegram channel and chat links are mapped correctly": settings.index('item.id == 10')
    < settings.index('"https://t.me/BYgram_official"')
    < settings.index('item.id == 11')
    < settings.index('"https://t.me/BYgram_community"'),
    "active settings use an allowlist": 'key.startsWith("bygram_ghost_")' not in config and 'key.startsWith("bygram_spy_")' not in config,
    "removed settings no longer select dead implementations": "getMicrosoftTranslatorKey" not in config + translate and "bygram_translation_provider" not in translate,
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    raise SystemExit("FAIL:\n- " + "\n- ".join(failed))
print(f"PASS: {len(checks)} BYgram settings regression contracts")
