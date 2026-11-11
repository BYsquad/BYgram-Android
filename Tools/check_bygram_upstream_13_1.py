"""Exercise profile geometry after adding Telegram's balance pill beside BYgram cards."""
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram/ui"
music = (JAVA / "Components/ProfileMusicView.java").read_text(encoding="utf-8")
profile = (JAVA / "ProfileActivity.java").read_text(encoding="utf-8")
heights = music.split("    public int getContentHeight() {", 1)[1].split("    protected int textColor", 1)[0]
extra = profile.split("    private int getMusicExtraHeight() {", 1)[1].split("    private int getHeaderExtraHeight()", 1)[0]
poll = (JAVA / "Cells/ChatMessagePoll.java").read_text(encoding="utf-8")
poll_preview = poll.split("    static boolean showResultsPreview", 1)[1].split("\n    }", 1)[0]
harness = r'''
public class Check {
    static final int VISIBLE=0, GONE=8, MUSIC_CARD_HEIGHT=100, ACTIVITY_CARD_HEIGHT=56;
    static class View { static final int VISIBLE=0; }
    boolean hasMusicDocument, profileList, companion; Object activity; int visibility=VISIBLE;
    static class BygramConfig {
        static final String CUSTOM_PROFILE_MUSIC="custom"; static boolean custom, pollPreview;
        static boolean getBoolean(String key) { return "bygram_poll_results".equals(key) ? pollPreview : custom; }
    }
    static int dp(int n) { return n; }
    boolean hasVisibleCompanion() { return companion; }
    int getVisibility() { return visibility; }
    public int getContentHeight() { HEIGHTS
    Check musicView, balanceView;
    private int getMusicExtraHeight() { EXTRA
    static class TLRPC {
        static class MessageMedia {}
        static class TL_messageMediaPoll extends MessageMedia {
            final Poll poll = new Poll();
            Results results = new Results();
        }
        static class Poll { boolean hide_results_until_close; }
        static class Results { final java.util.List<Integer> results = new java.util.ArrayList<>(); }
    }
    static class MessageObject {
        boolean forceShowPollResults;
        TLRPC.MessageMedia media;
        static TLRPC.MessageMedia getMedia(MessageObject message) { return message.media; }
    }
    static boolean showResultsPreviewPOLL
    public static void main(String[] args) {
        MessageObject message = new MessageObject();
        assert !showResultsPreview(message);
        BygramConfig.pollPreview = true;
        assert !showResultsPreview(message);
        TLRPC.TL_messageMediaPoll poll = new TLRPC.TL_messageMediaPoll();
        message.media = poll;
        assert !showResultsPreview(message);
        poll.results.results.add(1);
        assert showResultsPreview(message);
        BygramConfig.pollPreview = false;
        assert !showResultsPreview(message);
        BygramConfig.pollPreview = true;
        poll.poll.hide_results_until_close = true;
        assert !showResultsPreview(message);
        message.forceShowPollResults = true;
        assert showResultsPreview(message);
        Check m=new Check();
        assert m.getContentHeight()==0 && m.getHeaderHeight()==0;
        m.activity=new Object(); assert m.getContentHeight()==56 && m.getHeaderHeight()==32;
        m.companion=true; assert m.getContentHeight()==97 && m.getHeaderHeight()==97;
        m.hasMusicDocument=true; assert m.getContentHeight()==97;
        BygramConfig.custom=true; assert m.getContentHeight()==97;
        m.profileList=true; assert m.getContentHeight()==160;
        m.activity=null; assert m.getContentHeight()==100;
        m.profileList=false; m.companion=false; assert m.getContentHeight()==37;
        Check p=new Check(); p.musicView=m; p.balanceView=new Check();
        p.balanceView.hasMusicDocument=true;
        assert p.getMusicExtraHeight()==37;
        m.visibility=GONE; assert p.getMusicExtraHeight()==37;
        p.balanceView.visibility=GONE; assert p.getMusicExtraHeight()==0;
        m.visibility=VISIBLE; m.hasMusicDocument=false; m.activity=new Object();
        assert p.getMusicExtraHeight()==32;
        m.companion=true; p.balanceView.visibility=VISIBLE;
        assert p.getMusicExtraHeight()==97;
        System.out.println("PASS: music/activity/balance geometry, collapse heights and poll-preview visibility");
    }
}
'''.replace("HEIGHTS", heights).replace("EXTRA", extra).replace("POLL", poll_preview.replace("org.telegram.messenger.BygramConfig", "BygramConfig") + "\n    }")
with tempfile.TemporaryDirectory(prefix="bygram-upstream-") as tmp:
    source = Path(tmp) / "Check.java"
    source.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(source)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp, "Check"], check=True)

for relative in ("QrShare/QrScanOverlay.java", "QrShare/QrShareView.java", "Wallet/WalletGiftStickers.java"):
    source = (JAVA / relative).read_text(encoding="utf-8")
    calls = re.findall(r"performHapticFeedback\(([^;]+)", source)
    assert calls and all("BygramConfig.hapticFeedback(" in call for call in calls), relative
qr = (JAVA / "QrShare/QrShareView.java").read_text(encoding="utf-8")
assert "logo = d(R.drawable.bygram_logo_new)" in qr
assert "logo.draw(new Canvas(logoOptimal))" in qr
assert "plane_logo_plain" not in qr
assert not (JAVA / "QrActivity.java").exists()
assert "if (profileList && BygramConfig.getBoolean(BygramConfig.CUSTOM_PROFILE_MUSIC))" in music
assert "final float cy = dpf2(18.5f)" in music
assert "canvas.drawRoundRect(activityRect" in music
assert "titleMenuWidth" in (JAVA / "ActionBar/ActionBar.java").read_text(encoding="utf-8")
print("PASS: QR live/export branding, vibration in QR and wallet, profile balance and title integration")
