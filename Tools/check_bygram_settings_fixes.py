from pathlib import Path
import re
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
storage = read("TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java")
notifications = read("TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java")
privacy = read("TMessagesProj/src/main/java/org/telegram/messenger/BygramChatPrivacy.java")
search_pager = read("TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java")
dialogs_activity = read("TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java")
main_tabs = read("TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java")
chat = read("TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java")
viewer = read("TMessagesProj/src/main/java/org/telegram/ui/PhotoViewer.java")
sender = read("TMessagesProj/src/main/java/org/telegram/messenger/SendMessagesHelper.java")
message_object = read("TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java")
message_cell = read("TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java")
media_controller = read("TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java")
dialogs = read("TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsAdapter.java")
search = read("TMessagesProj/src/main/java/org/telegram/ui/Adapters/DialogsSearchAdapter.java")
navigation = read("TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarLayout.java")
lite_settings = read("TMessagesProj/src/main/java/org/telegram/ui/LiteModeSettingsActivity.java")
stories = read("TMessagesProj/src/main/java/org/telegram/ui/Stories/StoriesController.java")
story_cells = read("TMessagesProj/src/main/java/org/telegram/ui/Stories/DialogStoriesCell.java")


def resource_names(folder: str, file: str) -> set[str]:
    root = ET.parse(ROOT / "TMessagesProj/src/main/res" / folder / file).getroot()
    names = [item.attrib["name"] for item in root if "name" in item.attrib]
    assert len(names) == len(set(names)), f"Duplicate resources in {folder}/{file}"
    return {item.attrib["name"] for item in root if "name" in item.attrib}


english_resources = resource_names("values", "bygram_strings.xml") | resource_names("values", "bygram_preferences.xml")
russian_resources = resource_names("values-ru", "bygram_strings.xml") | resource_names("values-ru", "bygram_preferences.xml")
menu_option_ids = re.findall(r"public (?:final static|static final) int OPTION_\w+ = (\d+);", chat)

checks = {
    "legacy and V2 voice recorders respect BYgram haptic settings": all(
        "BygramConfig.hapticFeedback(" in argument
        for argument in re.findall(r"feedbackView\.performHapticFeedback\((.*?)HapticFeedbackConstants\.FLAG_IGNORE_GLOBAL_SETTING", media_controller, re.S)
    ) and media_controller.count("feedbackView.performHapticFeedback(") == 3,
    "launch search setting survives cleanup export and reset": 'COLLAPSE_SEARCH_ON_START = "bygram_collapse_search_on_start"' in config and 'case COLLAPSE_SEARCH_ON_START:' in config and 'BygramConfig.COLLAPSE_SEARCH_ON_START' in settings,
    "launch collapse preserves explicit search and scopes the main list": 'searchString == null && !searching && !searchIsShowed' in dialogs_activity and 'folderId == 0 && communityId == 0' in dialogs_activity.split('updateFloatingButtonVisibility(false);', 1)[1].split('checkUi_searchFiltersVisibility();', 1)[0] and 'setScrollY(-getMaxScrollYOffset());' in dialogs_activity,
    "archive customization uses native pull state": 'setChecked(org.telegram.messenger.SharedConfig.archiveHidden)' in settings and 'org.telegram.messenger.SharedConfig.toggleArchiveHidden();' in settings and 'viewPages[a].listView.updatePullState();' in dialogs_activity,
    "chat tab archive is available without custom folders": 'o.add(R.drawable.msg_archive, getString(R.string.ArchivedChats)' in main_tabs and 'args.putInt("folderId", 1);' in main_tabs and 'if (filters == null || filters.size() <= 1) return false;' not in main_tabs,
    "chat list header and stories reuse native glass": 'actionBar.setupGlass(glassFactory(), BlurredBackgroundProviderImpl.topPanel(resourceProvider));' in dialogs_activity and 'storiesBackground.draw(canvas);' in dialogs_activity and 'MathUtils.clamp(getCollapsedProgress(), 0f, 1f)' in dialogs_activity,
    "chat list customization has en ru uk resources": all({'BygramCollapseSearchOnStart', 'BygramCollapseSearchOnStartInfo', 'BygramHideArchive', 'BygramHideArchiveInfo'} <= resource_names(locale, 'bygram_preferences.xml') for locale in ('values', 'values-ru', 'values-uk')),
    "Telegram and BYgram message menu actions have unique IDs": bool(menu_option_ids) and len(menu_option_ids) == len(set(menu_option_ids)),
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
    "Saved Messages header does not inherit own BYgram badge": '!UserObject.isUserSelf(badgeUser)' in chat_header,
    "chat badges refresh users and chats": 'MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_CHAT_NAME' in badge_controller,
    "chat row rebuilds when fetched badge changes": 'boolean badgeChanged = (bygramNameBadge != null)' in dialog_cell and '!needUpdate && !badgeChanged' in dialog_cell,
    "cached badges appear before network refresh": badge_controller.index('getStringSet("assigned"') < badge_controller.index('Entry entry = cache.get'),
    "badge requests are deduplicated while loading": 'if (!entry.loading &&' in badge_controller and 'entry.loading = true;' in badge_controller and 'retryLater("chat"' in badge_controller,
    "music cover falls back to local embedded art": 'ImageLocation.getForDocument(thumb, document)' in music_view and 'AudioInfo.getAudioInfo(audioFile)' in music_view,
    "profile header includes music at entry and after loading": 'extraHeight = getHeaderExtraHeight();\n        return fragmentView;' in profile and 'balanceView.setVisibility(hasBalance ? View.VISIBLE : View.GONE);' in profile and 'adjustHeaderForMusicChange(getHeaderExtraHeight() - previousHeaderHeight);' in profile and 'savedScrollOffset = Math.max(0, header.getTop() + delta)' in profile,
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
    "native glass option replaces BYgram placements": "BygramLiquidGlass" not in settings and "LIQUID_GLASS_PLACEMENTS" not in config and "LiteOptionsLiquidGlass" in lite_settings,
    "spy can physically remove previously saved messages": "getBygramDeletedMessageIdsSync" in storage and "locallyDeletedMessages.add(messageId)" in messages and "toSend.removeAll(alreadySaved)" in messages,
    "spy own deletion preference is persisted and exposed": all(value in config for value in ['SPY_SAVE_OWN_DELETED = "bygram_spy_save_own_deleted"', 'case SPY_SAVE_OWN_DELETED:']) and "case 3: return BygramConfig.SPY_SAVE_OWN_DELETED" in settings and "storedMessage.out" in messages,
    "spy keeps the first removed chat and purges saved-only history": "hasSaved && !hasLive ? 2 : canKeep ? 1 : 0" in storage and "getBygramDialogRetentionStateSync(did, onlyHistory == 0 || BygramConfig.getBoolean(BygramConfig.SPY_SAVE_OWN_DELETED)) == 1" in messages and "markDialogMessagesAsBygramDeleted(did, onlyHistory != 0)" in messages,
    "whole-chat deletion retains outgoing history during Undo": "getBygramDialogRetentionStateSync(did, true) != 1" in messages and "respectOwnDeletionSetting && (dialogId == getUserConfig().getClientUserId() || cursor.intValue(1) != 0)" in storage,
    "story title follows actual avatar bounds in both lists": story_cells.count("getAvatarRight(cell.getMeasuredWidth(), cell.progressToCollapsed)") == 2 and "lastViewRight += dp(12)" in story_cells and "int cellWidth = dp(72)" not in story_cells,
    "private chats cover list, search, notification, navigation and accessibility": all((
        "BygramChatPrivacy" in dialogs, "BygramChatPrivacy" in search,
        "BygramChatPrivacy" in notifications, "requireBygramPassword" in navigation,
        "consumeHiddenAllowed" in chat, "BygramProtectedPreview" in dialog_cell,
    )),
    "private notifications and inline hidden search stay private": all((
        "return isHidden(account, dialogId) || isProtected(account, dialogId);" in privacy,
        notifications.count("BygramChatPrivacy.hidesNotification(currentAccount, messageObject.getDialogId())") == 2,
        "if (hiddenChatsVisible) items.add(new Item(HIDDEN_TYPE));" in search_pager,
        "searchViewPager.showHiddenChats();" in dialogs_activity,
        "presentFragment(new BygramSettingsActivity.PrivateChatsActivity(true, true))" not in dialogs_activity,
    )),
    "private chat settings select from dialogs and show only selected rows": 'args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_DEFAULT)' in settings and 'if (!selected) continue;' in settings and 'R.string.SelectChat' in settings,
    "private chat pages use dialog rows and action bar add": 'setDialog((TLRPC.Dialog) item.object, DialogsActivity.DIALOGS_TYPE_DEFAULT, 0)' in settings and 'cell.showBygramProtectedMessage = true;' in settings and '!showBygramProtectedMessage && currentDialogFolderId == 0' in dialog_cell and 'simpleActionBar.createMenu().addItem(2, R.drawable.msg_add' in settings and 'UItem.asButton(2, getString(R.string.SelectChat))' not in settings,
    "hidden search swaps its page back after closing": 'public void hideHiddenChats()' in search_pager and 'setPosition(0);\n        updateCurrent();\n        hiddenChats.clear();' in search_pager,
    "local saving is independent of forwarding restrictions": 'allowShare = !noforwards;\n                galleryButton.setVisibility(View.VISIBLE);' in viewer and 'if (type == 4 && !selectedObject.needDrawBluredPreview())' in chat and 'else if (type == 6)' in chat,
    "restricted forwards download and upload new copies": all((
        'sendProtectedCopy(msgObj, peer' in sender,
        'downloadProtectedCopy(fileName,' in sender,
        'upload.attributes.addAll(media.document.attributes)' in sender,
        'params.payStars = payStars;' in sender,
        '!messageOwner.noforwards' not in message_object.split('public boolean canForwardMessage()', 1)[1].split('public boolean canEditMedia()', 1)[0],
        '&& !noforwards && selectedObject.type != MessageObject.TYPE_SHARING_OFFER' not in chat,
        'drawSideButton = 0;\n                if (isPinnedChat' not in message_cell,
    )),
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    raise SystemExit("FAIL:\n- " + "\n- ".join(failed))
print(f"PASS: {len(checks)} BYgram settings regression contracts")
