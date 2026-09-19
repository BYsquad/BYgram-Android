from pathlib import Path


source = Path("TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java").read_text(encoding="utf-8")
controller = Path("TMessagesProj/src/main/java/org/telegram/messenger/BygramActivityController.java").read_text(encoding="utf-8")
music = Path("TMessagesProj/src/main/java/org/telegram/ui/Components/ProfileMusicView.java").read_text(encoding="utf-8")
strings = Path("TMessagesProj/src/main/res/values-ru/bygram_strings.xml").read_text(encoding="utf-8")
api = Path("server/activity-api/index.php").read_text(encoding="utf-8")
schema = Path("server/activity-api/schema.sql").read_text(encoding="utf-8")

assert "AndroidUtilities.runOnUIThread(this, 20_000);" in source, "profile activity is not refreshed periodically"
assert (
    "musicView != null && musicView.getVisibility() == View.VISIBLE" in source
), "profile header height ignores an activity-only card"
assert "musicView.getContentHeight()" in source, "profile reserves a fixed height instead of the visible music/activity content"
assert "previousHeaderHeight" in source and "extraHeight += getHeaderExtraHeight() - previousHeaderHeight" in source, "late activity does not preserve profile header geometry"
assert "drawActivityCard" in music and "ACTIVITY_CARD_HEIGHT" in music, "activity is not rendered as a compact card"

for text in ("Смотрит канал", "Сидит в группе", "Общается с ботом", "В чате с"):
    assert text in strings, f"missing detailed activity label: {text}"
assert "peerKind" in controller and '"peer_kind"' in controller, "client does not preserve the viewed peer kind"
assert "peer_kind" in api and "peer_kind" in schema, "server does not persist the viewed peer kind"

get_activity = controller[controller.index("public static void get("):controller.index("public static ActivityInfo getCachedActivity")]
assert 'response == null || !response.optBoolean("ok")' in get_activity, "a failed refresh incorrectly clears the visible activity"

print("BYgram profile activity checks passed")
