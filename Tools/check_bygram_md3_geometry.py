"""Fast regression check for the shared MD3 drawing geometry."""
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def source(relative):
    return (ROOT / relative).read_text(encoding="utf-8")


seekbar = source("TMessagesProj/src/main/java/org/telegram/ui/Components/SeekBarView.java")
switch = source("TMessagesProj/src/main/java/org/telegram/ui/Components/Switch.java")
progress = source("TMessagesProj/src/main/java/org/telegram/ui/Components/RadialProgressView.java")
circular_progress = source("TMessagesProj/src/main/java/org/telegram/ui/Components/CircularProgressDrawable.java")
chat = source("TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java")
dialogs = source("TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java")
photo = source("TMessagesProj/src/main/java/org/telegram/ui/PhotoViewer.java")
popups = source("TMessagesProj/src/main/java/org/telegram/ui/Components/ChatAttachAlert.java") + source("TMessagesProj/src/main/java/org/telegram/ui/Components/ShareAlert.java")
settings = source("TMessagesProj/src/main/java/org/telegram/ui/BygramSettingsActivity.java")
config = source("TMessagesProj/src/main/java/org/telegram/messenger/BygramConfig.java")
activity_controller = source("TMessagesProj/src/main/java/org/telegram/messenger/BygramActivityController.java")
image_receiver = source("TMessagesProj/src/main/java/org/telegram/messenger/ImageReceiver.java")
profile = source("TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java")
stories = source("TMessagesProj/src/main/java/org/telegram/ui/Stories/StoriesUtilities.java")
profile_stories = source("TMessagesProj/src/main/java/org/telegram/ui/Stories/ProfileStoriesView.java")
badge = source("TMessagesProj/src/main/java/org/telegram/ui/Components/StatusBadgeComponent.java")
api = source("server/activity-api/index.php")
admin_api = source("server/activity-api/admin-api.php")
admin = source("server/activity-api/admin.php")
admin_ui = source("server/site/src/admin.jsx")
bootstrap = source("server/activity-api/bootstrap.php")
release = source("server/activity-api/release.json")
gradle = source("gradle.properties")
main_site = source("server/site/src/main.jsx")
config_example = source("server/activity-api/config.example.php")

assert "new Slider(new ContextThemeWrapper" in seekbar, "MD3 slider is not backed by Material Slider"
assert "setTrackHeight(AndroidUtilities.dp(8))" in seekbar and "setThumbWidth(AndroidUtilities.dp(3))" in seekbar and "setThumbHeight(AndroidUtilities.dp(24))" in seekbar, "MD3 slider geometry differs from ExteraGram"
assert "MD3_TRACK_HEIGHT_DP = 26f" in switch and "drawRoundRect(rectF, radius, radius" in switch, "MD3 switch geometry differs from ExteraGram"
assert "new LoadingIndicator(getContext())" in progress, "MD3 loading is not backed by Material LoadingIndicator"
assert "new LoadingIndicator(context)" in circular_progress, "Drawable loading is not backed by Material LoadingIndicator"
glass_sources = chat + dialogs + photo + popups
for placement in ("GLASS_MEDIA", "GLASS_CONTEXT_PANELS", "GLASS_FLOATING_BUTTONS", "GLASS_SEARCH", "GLASS_SELECTION", "GLASS_POPUPS", "GLASS_MESSAGE_MENUS", "GLASS_BUTTON_MENUS"):
    assert placement in glass_sources, f"Liquid glass placement has no UI consumer: {placement}"
assert "BygramLiquidGlassExamples" in settings, "Liquid-glass rows have no examples"
assert "bygram_uniform_avatars" not in image_receiver and "Arrays.fill(avatarRoundRadius" in image_receiver, "Visible avatar radius does not apply uniformly"
assert "avatarRadius(" in config and "BygramConfig.avatarRadius" in image_receiver + profile + stories + profile_stories, "Profile avatars and story rings do not share the configured shape"
assert "bygram_logo_new" in badge and "loadBadges()" in api and "badge.save" in admin_api, "BYgram badge is not wired end to end"
assert "setMultilineTextAndValue" in settings, "Long BYgram toggle labels are still ellipsized"
assert "APP_VERSION_CODE=7038" in gradle and "APP_VERSION_NAME=12.10.1" in gradle and '"version": "12.10.1"' in release and '"version_code": "70389"' in release, "Release version changed without approval"
assert "telegram-widget.js" in admin and "data-telegram-login" in admin and "password" not in (admin + admin_ui).lower(), "Admin panel is not using Telegram Login Widget authorization"
assert "hasExteraBadge" in bootstrap and "hasExteraBadge($id)" in admin_api, "ExteraGram badge ownership is not verified"
assert "BYxarek" + "_manager" not in activity_controller + api + bootstrap + main_site + config_example and "BYgram_manager" in activity_controller + api + main_site + config_example, "Manager username is outdated"
print("PASS: MD3, liquid-glass examples, avatar endpoints and BYgram badge are wired")
