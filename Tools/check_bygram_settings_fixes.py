from pathlib import Path
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


config = read("TMessagesProj/src/main/java/org/telegram/messenger/BygramConfig.java")
activity = read("TMessagesProj/src/main/java/org/telegram/messenger/BygramActivityController.java")
settings = read("TMessagesProj/src/main/java/org/telegram/ui/BygramSettingsActivity.java")
settings_root = read("TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java")
badge = read("TMessagesProj/src/main/java/org/telegram/ui/Components/BygramBadgeDrawable.java")
chat_header = read("TMessagesProj/src/main/java/org/telegram/ui/Components/ChatAvatarContainer.java")
badge_controller = read("TMessagesProj/src/main/java/org/telegram/messenger/BygramBadgeController.java")
music_view = read("TMessagesProj/src/main/java/org/telegram/ui/Components/ProfileMusicView.java")
dialog_cell = read("TMessagesProj/src/main/java/org/telegram/ui/Cells/DialogCell.java")
profile = read("TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java")
title_icons = chat_header.split("public void setTitleIcons(", 1)[1].split("public AnimatedEmojiDrawable", 1)[0]
translate = read("TMessagesProj/src/main/java/org/telegram/ui/Components/TranslateAlert2.java")
application = read("TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java")
images = read("TMessagesProj/src/main/java/org/telegram/messenger/ImageReceiver.java")
api = read("server/activity-api/index.php")
admin_api = read("server/activity-api/admin-api.php")
admin_ui = read("server/site/src/admin.jsx")
bootstrap = read("server/activity-api/bootstrap.php")
schema = read("server/activity-api/schema.sql")
self_test = read("server/activity-api/self-test.php")
messages = read("TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java")
stories = read("TMessagesProj/src/main/java/org/telegram/ui/Stories/StoriesController.java")


def resource_names(folder: str, file: str) -> set[str]:
    root = ET.parse(ROOT / "TMessagesProj/src/main/res" / folder / file).getroot()
    return {item.attrib["name"] for item in root if "name" in item.attrib}


english_resources = resource_names("values", "bygram_strings.xml") | resource_names("values", "bygram_preferences.xml")
russian_resources = resource_names("values-ru", "bygram_strings.xml") | resource_names("values-ru", "bygram_preferences.xml")

checks = {
    "BYgram settings keep a reserved ID while Telegram uses 24": "BYGRAM_SETTINGS_ID = 1000" in settings_root and "Factory.of(BYGRAM_SETTINGS_ID," in settings_root and "case BYGRAM_SETTINGS_ID:" in settings_root and "Factory.of(24," in settings_root and "case 24:" in settings_root,
    "unlicensed users receive the manager URL": "'bot_url' => managerUrl()" in api and "appSetting('manager_username'" in api and 'callback.run(false, botUrl)' in activity,
    "license status returns the manager URL": "'manager_url' => managerUrl()" in api,
    "unused server paths are absent": all(name not in admin_api + admin_ui + bootstrap + schema for name in (
        "auth.confirm", "auth_challenges", "profile_badges", "promo_uses", "manual_payment_requests",
        "validateMiniApp", "grantLicense", "addReferralToOrder")),
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
    "typing publish confirms server success": 'if (ok) {' in settings and 'BygramTypingPublishSuccess' in settings,
    "badge notice uses Telegram language and theme": 'LocaleController.getString(resource ? R.string.BygramResourceBadgeNotice' in badge and 'BulletinFactory.global().createSimpleBulletin' in badge,
    "chat title restores badge after title icons": title_icons.index('titleTextView.setRightDrawable2(mutedIcon)') < title_icons.index('updateBygramBadge();'),
    "chat badges refresh users and chats": 'MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_CHAT_NAME' in badge_controller,
    "chat row rebuilds when fetched badge changes": 'boolean badgeChanged = (bygramNameBadge != null)' in dialog_cell and '!needUpdate && !badgeChanged' in dialog_cell,
    "cached badges appear before network refresh": badge_controller.index('getStringSet("assigned"') < badge_controller.index('Entry entry = cache.get'),
    "badge requests are deduplicated while loading": 'if (!entry.loading &&' in badge_controller and 'entry.loading = true;' in badge_controller and 'retryLater("chat"' in badge_controller,
    "music cover falls back to local embedded art": 'ImageLocation.getForDocument(cover, document)' in music_view and 'AudioInfo.getAudioInfo(file)' in music_view,
    "profile header includes music at entry and after loading": 'extraHeight = getHeaderExtraHeight();\n        return fragmentView;' in profile and 'musicView.setMusicDocument(userInfo.saved_music);\n                int headerHeightDelta' in profile and 'adjustHeaderForMusicChange(headerHeightDelta);' in profile and 'savedScrollOffset = Math.max(0, header.getTop() + delta)' in profile,
    "legacy commerce is absent from admin panel": all(name not in admin_ui + admin_api for name in ('promo.save', 'method.save', 'referral.grant', 'requisite.save', 'payment_methods', 'referral_earnings')),
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
