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
harness = r'''
public class Check {
    static final int VISIBLE=0, GONE=8, MUSIC_CARD_HEIGHT=100, ACTIVITY_CARD_HEIGHT=56;
    static class View { static final int VISIBLE=0; }
    boolean hasMusicDocument, profileList, companion; Object activity; int visibility=VISIBLE;
    static class BygramConfig {
        static final String CUSTOM_PROFILE_MUSIC="custom"; static boolean custom;
        static boolean getBoolean(String key) { return custom; }
    }
    static int dp(int n) { return n; }
    boolean hasVisibleCompanion() { return companion; }
    int getVisibility() { return visibility; }
    public int getContentHeight() { HEIGHTS
    Check musicView, balanceView;
    private int getMusicExtraHeight() { EXTRA
    public static void main(String[] args) {
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
        System.out.println("PASS: music/activity/balance geometry, custom music, hidden pills and collapse heights");
    }
}
'''.replace("HEIGHTS", heights).replace("EXTRA", extra)
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
