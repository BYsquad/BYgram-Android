"""Check that page headers use Telegram's defaults and the chat list opts into glass."""
from pathlib import Path

UI = Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java/org/telegram/ui"
plugins = (UI.parents[4] / "full/java/org/telegram/ui/BygramPluginsActivity.java").read_text(encoding="utf-8")
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
assert "simpleActionBar.setTitle(getString(R.string.BygramSettings));" in root
assert "asTopViewStatic(" not in root
assert "protected boolean useSimpleActionBar()" in page
assert "protected RecyclerListView getListViewForSimpleGlass()" in page
assert "actionBar.setTitle(" not in settings and "actionBar.setTitle(" not in plugins
assert "simpleActionBar.createMenu().addItem(2, R.drawable.msg_add)" in settings
assert "simpleActionBar.createMenu().addItem(0, R.drawable.outline_header_search)" in settings
assert "simpleActionBar.createMenu().addItem(3, R.drawable.msg_info)" in plugins
assert "simpleActionBar.setTitle(getString(R.string.Settings));" in telegram_settings
assert "BaseFragmentSimpleGlass.setListViewParams(listView, systemInsets.top, systemInsets.bottom" in telegram_settings
assert "actionBar.setupGlass(" not in profile and "setTitleCenteredInGlass(" not in contacts
assert "actionBar.setupGlass(glassFactory(), BlurredBackgroundProviderImpl.topPanel(resourceProvider));" in dialogs
print("PASS: shared simple headers, page actions and explicit chat list glass")
