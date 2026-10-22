"""Fast regression check for BYgram appearance settings."""
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
admin = source("server/activity-api/admin.php")
admin_ui = source("server/site/src/admin.jsx")
bootstrap = source("server/activity-api/bootstrap.php")
gradle = source("gradle.properties")
main_site = source("server/site/src/main.jsx")
config_example = source("server/activity-api/config.example.php")

assert "new Slider(new ContextThemeWrapper" in seekbar, "MD3 slider is not backed by Material Slider"
assert "setTrackHeight(" not in seekbar and "setThumbWidth(" not in seekbar and "setThumbHeight(" not in seekbar, "MD3 slider overrides the official Material geometry"
assert "setTrackStopIndicatorSize(0)" not in seekbar and "setTickVisibilityMode(2)" not in seekbar, "MD3 slider hides its official stops/ticks"
assert "new MaterialSwitch(new ContextThemeWrapper" in switch and "Theme_Material3_DayNight" in switch, "MD3 switch must use Google's implementation and animations"
assert "? 52 : 37" in switch and "LayoutHelper.createFrame(52, 32, Gravity.CENTER)" in switch, "MD3 switch must reserve a 52 x 32 dp track"
assert "MD3_THUMB_MIN_RADIUS_DP" not in switch and "materialSwitch.setChecked(checked)" in switch and "materialSwitch.setPressed(value)" in switch, "MD3 handle animation must be driven by the official component"
assert "Theme.getColor(thumbColorKey, resourcesProvider)" in switch and "materialSwitch.setThumbTintList(switchColors(" in switch and "materialSwitch.setTrackTintList(switchColors(" in switch and "materialSwitch.setTrackDecorationTintList(" in switch, "MD3 colors must use the Telegram theme"
assert "materialSwitch.jumpDrawablesToCurrentState()" in switch and "setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO)" in switch, "MD3 binding must preserve nonanimated updates and a single accessibility node"
assert "getMeasuredWidth() / AndroidUtilities.dpf2(52)" in switch and "getMeasuredHeight() / AndroidUtilities.dpf2(32)" in switch, "Compact switch containers must scale without clipping"
for caller in ("Cells/CheckBoxUserCell.java", "Cells/NotificationsCheckCell.java", "Cells/AvailableReactionCell.java", "Cells/PollCreateCheckCell.java", "Cells/TextCheckCell.java", "Cells/TextCell.java", "Cells/TextCheckCell2.java", "Components/MultilineTextCheckCell.java", "Components/PhonebookShareAlert.java", "LiteModeSettingsActivity.java", "SessionBottomSheet.java"):
    assert "Switch.layoutWidth()" in source("TMessagesProj/src/main/java/org/telegram/ui/" + caller), f"Switch width is stale in {caller}"
reactions = source("TMessagesProj/src/main/java/org/telegram/ui/Components/Reactions/ReactionsLayoutInBubble.java")
avatars = source("TMessagesProj/src/main/java/org/telegram/ui/Components/AvatarsDrawable.java")
voters = source("TMessagesProj/src/main/java/org/telegram/ui/Components/AvatarsListDrawable.java")
action_bar = source("TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java")
assert "float rad = height / 2f;" not in reactions and reactions.count("BygramConfig.avatarRadius(height)") >= 2, "Reaction background and effect disagree on shape"
assert "drawCircle(imageReceiver.getCenterX()" not in avatars and "BygramConfig.avatarRadius(avatar.getImageHeight())" in avatars, "Overlapping avatar masks are circular"
assert "drawCircle(cx, cy, r" not in voters and "BygramConfig.avatarRadius(w)" in voters, "Poll voter avatar masks are circular"
assert "BygramConfig.avatarRadius(dp(46))" in action_bar and "setRadius(leadingRadius, radius, radius, leadingRadius, false)" in action_bar, "Chat header edge ignores avatar shape"
for selector in (dialogs, source("TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java")):
    account_view = selector.split("public LinearLayout accountView(", 1)[1].split("return btn;", 1)[0]
    assert "canvas.drawCircle(" not in account_view and "canvas.drawRoundRect(AndroidUtilities.rectTmp, BygramConfig.avatarRadius(dp(32))" in account_view, "Selected account outline ignores the BYgram avatar shape"
main_settings, customization = settings.split("public static class CustomizationSettingsActivity", 1)
assert "asButton(4," not in main_settings and "asButton(4," in customization.split("public static class TypingCustomizationActivity", 1)[0], "Typing statuses must live in Customization"
assert "new EditText(" not in settings and "shape.setStroke(" in settings and "dialogInputContainer(input)" in chat, "BYgram dialogs must share the themed input style"
icons = settings.split("AppIconsSelectorCell icons =", 1)[1].split("BackupImageView avatarPreview", 1)[0]
launcher = source("TMessagesProj/src/main/java/org/telegram/ui/LauncherIconController.java")
manifest = source("TMessagesProj/src/main/AndroidManifest.xml")
assert "LauncherIcon.DEFAULT" in icons and "LauncherIcon.AQUA" in icons and "LauncherIcon.VINTAGE" not in icons, "Customization must only offer BYgram and Telegram icons"
assert 'DEFAULT("DefaultIcon", R.drawable.bygram_logo_new, 0, R.string.AppName)' in launcher and 'if (icon == LauncherIcon.VINTAGE) setIcon(LauncherIcon.DEFAULT)' in launcher, "Default and obsolete icon migration must use logo-new.png"
default_alias = manifest.split('android:name="org.telegram.messenger.DefaultIcon"', 1)[1].split('</activity-alias>', 1)[0]
assert '@drawable/bygram_logo_new"' in default_alias, "Fresh installs must use the BYgram logo"
assert (ROOT / "logo-new.png").read_bytes() == (ROOT / "TMessagesProj/src/main/res/drawable-nodpi/bygram_logo_new.png").read_bytes(), "Default icon asset differs from logo-new.png"
assert 'AQUA("AquaIcon", R.drawable.telegram_logo' in launcher, "Telegram preview must not use the branded adaptive icon"
aqua_alias = manifest.split('android:name="org.telegram.messenger.AquaIcon"', 1)[1].split('</activity-alias>', 1)[0]
assert aqua_alias.count('@drawable/telegram_logo"') == 2, "Telegram launcher icons must match the preview"
standalone = source("TMessagesProj/config/release/AndroidManifest_standalone.xml")
assert standalone.split('android:name="org.telegram.messenger.AquaIcon"', 1)[1].split('</activity-alias>', 1)[0].count('@drawable/telegram_logo"') == 2, "Standalone must use the same blue Telegram icon"
telegram_icon = (ROOT / "TMessagesProj/src/main/res/drawable-nodpi/telegram_logo.png").read_bytes()
assert not (ROOT / "TMessagesProj/src/main/res/drawable/telegram_logo.xml").exists(), "Old Telegram vector must not override the PNG at device densities"
assert int.from_bytes(telegram_icon[16:20], "big") == int.from_bytes(telegram_icon[20:24], "big"), "Telegram artwork must be square without stretching"
assert (ROOT / "logo-no-fon.png").read_bytes() == (ROOT / "TMessagesProj/src/main/res/drawable-nodpi/bygram_logo_no_fon.png").read_bytes(), "Transparent logo differs from logo-no-fon.png"
assert 'android:drawable="@drawable/bygram_logo_no_fon"' in source("TMessagesProj/src/main/res/drawable/bygram_splash.xml"), "Splash must use the transparent logo"
assert 'R.drawable.bygram_logo_no_fon' in main_settings and 'bygram_logo_no_fon' not in launcher, "Transparent logo belongs to settings, never app icon choices"
assert "new LoadingIndicator(getContext())" in progress, "MD3 loading is not backed by Material LoadingIndicator"
assert "new LoadingIndicator(context)" in circular_progress, "Drawable loading is not backed by Material LoadingIndicator"
lite_settings = source("TMessagesProj/src/main/java/org/telegram/ui/LiteModeSettingsActivity.java")
assert "BygramLiquidGlass" not in settings and "LIQUID_GLASS_PLACEMENTS" not in config, "Removed glass settings have returned"
assert "LiteOptionsLiquidGlass" in lite_settings and "LiteMode.FLAG_LIQUID_GLASS" in lite_settings and "LiteMode.toggleFlag(item.flags" in lite_settings, "Native liquid-glass setting has no consumer"
assert "bygram_uniform_avatars" not in image_receiver and "Arrays.fill(avatarRoundRadius" in image_receiver, "Visible avatar radius does not apply uniformly"
assert "avatarRadius(" in config and "BygramConfig.avatarRadius" in image_receiver + profile + stories + profile_stories, "Profile avatars and story rings do not share the configured shape"
assert "bygram_logo_new" not in badge and "getProfileBadgeInfo" not in activity_controller, "Profile badges are still present in the client"
assert "setMultilineTextAndValue" in settings, "Long BYgram toggle labels are still ellipsized"
assert "APP_VERSION_CODE=7183" in gradle and "APP_VERSION_NAME=13.0.3" in gradle and "APP_PACKAGE=com.bygram.mobile" in gradle, "Client version or package differs from the approved Telegram update"
assert "telegram-widget.js" in admin and "data-telegram-login" in admin and "password" not in (admin + admin_ui).lower(), "Admin panel is not using Telegram Login Widget authorization"
assert "BYxarek" + "_manager" not in activity_controller + api + bootstrap + main_site + config_example and "BYgram_manager" in activity_controller + api + main_site + config_example, "Manager username is outdated"
print("PASS: MD3, native liquid glass, avatar endpoints and removed profile badges")
