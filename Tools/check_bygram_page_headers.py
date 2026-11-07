"""Check that page headers use Telegram's defaults and the chat list opts into glass."""
from pathlib import Path

UI = Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java/org/telegram/ui"
bar = (UI / "ActionBar/ActionBar.java").read_text(encoding="utf-8")
settings = (UI / "BygramSettingsActivity.java").read_text(encoding="utf-8")
page, root = settings.split("public class BygramSettingsActivity extends BygramSettingsPage", 1)
root, _ = root.split("private void onClick", 1)
dialogs = (UI / "DialogsActivity.java").read_text(encoding="utf-8")
telegram_settings = (UI / "SettingsActivity.java").read_text(encoding="utf-8")
profile = (UI / "ProfileActivity.java").read_text(encoding="utf-8")
contacts = (UI / "ContactsActivity.java").read_text(encoding="utf-8")

assert bar.count("setupGlass(") == 3, "ActionBar must never opt itself into glass"
assert "setPageBackgroundShade(" not in bar
assert "createActionBar(" not in page and "setAdaptiveBackground(" not in page
assert "actionBar.setTitle(getString(R.string.BygramSettings));" in root
assert "asTopViewStatic(" not in root
assert "if (getPaddingTop() != headerHeight) setPadding(0, headerHeight, 0, 0);" in page
assert settings.count("FrameLayout content = createSettingsContent(context);") == 8
assert "actionBarBackground = new View(context) {" in telegram_settings
assert "getParentLayout().drawHeaderShadow(canvas, top);" in telegram_settings
assert "actionBar.setupGlass(" not in profile and "setTitleCenteredInGlass(" not in contacts
assert "actionBar.setupGlass(glassFactory(), BlurredBackgroundProviderImpl.topPanel(resourceProvider));" in dialogs
print("PASS: stock page headers, settings insets and explicit chat list glass")
