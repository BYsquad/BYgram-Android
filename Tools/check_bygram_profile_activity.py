from pathlib import Path


source = Path("TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java").read_text(encoding="utf-8")
controller = Path("TMessagesProj/src/main/java/org/telegram/messenger/BygramActivityController.java").read_text(encoding="utf-8")
music = Path("TMessagesProj/src/main/java/org/telegram/ui/Components/ProfileMusicView.java").read_text(encoding="utf-8")
strings = Path("TMessagesProj/src/main/res/values-ru/bygram_strings.xml").read_text(encoding="utf-8")
api = Path("server/activity-api/index.php").read_text(encoding="utf-8")
schema = Path("server/activity-api/schema.sql").read_text(encoding="utf-8")

assert "AndroidUtilities.runOnUIThread(this, 7_000);" in source, "profile activity is not refreshed every seven seconds"
assert (
    "musicView != null && musicView.getVisibility() == View.VISIBLE" in source
), "profile header height ignores an activity-only card"
assert "musicView.getHeaderHeight()" in source and "return getContentHeight()" in music, "profile reserves a fixed height instead of the visible music/activity content"
assert "int headerHeightDelta = getHeaderExtraHeight() - previousHeaderHeight" in source and "adjustHeaderForMusicChange(headerHeightDelta);" in source and "extraHeight = Math.max(0, extraHeight + delta);" in source, "late activity does not preserve profile header geometry"
assert "drawActivityCard" in music and "ACTIVITY_CARD_HEIGHT" in music, "activity is not rendered as a compact card"
assert source.index("bygramMusicRow = rowCount++") < source.index("channelRow = rowCount++"), "custom music must precede the linked channel"
assert "view = bygramMusicView;" in source and "return VIEW_TYPE_MUSIC;" in source, "custom music is not a profile list row"
assert "bygramMusicView.setOnClickListener(v -> musicView.performClick())" in source, "list music must use the existing playback handler"
assert "profileList || !BygramConfig.getBoolean(BygramConfig.CUSTOM_PROFILE_MUSIC)" in music, "custom music is duplicated in the avatar header"
assert "profileList ? 1f" in music, "list music must remain visible and clickable when the header collapses"
assert "createSimpleBulletin(R.raw.copy, getString(R.string.BygramProfileIdCopied))" in source, "ID confirmation must appear even when Android supplies its own clipboard toast"

for text in ("Смотрит канал", "Сидит в группе", "Общается с ботом", "В чате с"):
    assert text in strings, f"missing detailed activity label: {text}"
assert "peerKind" in controller and '"peer_kind"' in controller, "client does not preserve the viewed peer kind"
assert "peer_kind" in api and "peer_kind" in schema, "server does not persist the viewed peer kind"

get_activity = controller[controller.index("public static void get("):controller.index("public static ActivityInfo getCachedActivity")]
assert 'response == null || !response.optBoolean("ok")' in get_activity, "a failed refresh incorrectly clears the visible activity"

print("BYgram profile activity checks passed")
