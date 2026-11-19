"""Exercise TextCell's real measurement with wrapped titles and value columns."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram/ui"
cell = (JAVA / "Cells/TextCell.java").read_text(encoding="utf-8")
measure = "protected void onMeasure(" + cell.split("protected void onMeasure(", 1)[1].split("    public void setMultiline", 1)[0]
harness = r'''
public class SettingsLayoutCheck {
    static class MeasureSpec {
        static final int EXACTLY=1<<30, AT_MOST=2<<30, UNSPECIFIED=0;
        static int getSize(int n) { return n & 0x3fffffff; }
        static int makeMeasureSpec(int n, int mode) { return Math.max(0,n) | mode; }
    }
    static class TextUtils {
        static class TruncateAt { static final int END=0; }
        static CharSequence ellipsize(CharSequence s, Object p, float w, int mode) { return s; }
    }
    static class AndroidUtilities { static class Size { int x=400; } static Size displaySize=new Size(); }
    static class LocaleController { static boolean isRTL; }
    static class Switch { static int layoutWidth() { return 40; } static int layoutHeight() { return 24; } }
    static class Widget {
        int chars, width, height, lines=1, visibility=0;
        Widget(int chars) { this.chars=chars; }
        void measure(int w, int h) {
            width=MeasureSpec.getSize(w);
            int lineCount=Math.min(lines, Math.max(1,(chars*8+Math.max(1,width)-1)/Math.max(1,width)));
            height=MeasureSpec.getSize(h)==0 ? lineCount*20 : MeasureSpec.getSize(h);
        }
        int getMeasuredWidth() { return width; }
        int getTextWidth() { return Math.min(width,chars*8); }
        int getTextHeight() { return height; }
        int getVisibility() { return visibility; }
        Object getPaint() { return null; }
        void setText(CharSequence text, boolean animated) {}
        int width() { return chars*8; }
    }
    int dp(int n) { return n; }
    int measuredHeight, lastWidth, leftPadding=20, heightDp=50;
    static final int VISIBLE=0;
    boolean prioritizeTitleOverValue, multiline, needDivider;
    CharSequence valueText;
    Widget textView=new Widget(12), subtitleView=new Widget(0), valueTextView=new Widget(0),
        valueSpoilersTextView=new Widget(0), imageView=new Widget(0), valueImageView=new Widget(0), checkBox;
    void setMeasuredDimension(int w, int h) { measuredHeight=h; }
    MEASURE
    void check(int width, int chars, int valueChars, boolean wrap) {
        multiline=wrap; textView.chars=chars; textView.lines=wrap?Integer.MAX_VALUE:1;
        valueTextView.chars=valueChars;
        onMeasure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),0);
        assert measuredHeight >= 50;
        assert !wrap || measuredHeight >= textView.getTextHeight()+24;
    }
    public static void main(String[] args) {
        SettingsLayoutCheck t=new SettingsLayoutCheck();
        t.check(400,12,0,true); assert t.measuredHeight==50;
        t.check(320,80,0,true); assert t.measuredHeight>50;
        int fullWidthHeight=t.measuredHeight;
        t.check(320,80,10,true); assert t.measuredHeight>fullWidthHeight;
        LocaleController.isRTL=true;
        t.check(320,80,10,true); assert t.measuredHeight>fullWidthHeight;
        t.check(320,80,0,false); assert t.measuredHeight==50;
        t.needDivider=true; t.check(320,80,0,true);
        assert t.measuredHeight==fullWidthHeight+1;
        System.out.println("PASS: real TextCell measurement: short/long titles, value column, RTL, opt-out and divider");
    }
}
'''.replace("MEASURE", measure)
with tempfile.TemporaryDirectory(prefix="bygram-settings-layout-") as directory:
    path = Path(directory) / "SettingsLayoutCheck.java"
    path.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(path)], check=True)
    subprocess.run(["java", "-ea", "-cp", directory, "SettingsLayoutCheck"], check=True)

settings = (JAVA / "BygramSettingsActivity.java").read_text(encoding="utf-8")
assert "if (bind != null) bind.run(view);" in settings
assert settings.count("setMultilineTextAndValue(null)") == 1
profile = (JAVA / "ProfileActivity.java").read_text(encoding="utf-8")
assert "bygramPageBackground(holder.itemView, background, holder.itemView != bygramMusicView)" in profile
assert "super.drawCardBackground(canvas, bounds);" in profile
music = (JAVA / "Components/ProfileMusicView.java").read_text(encoding="utf-8")
assert "drawCardBackground(canvas, rect);" in music
assert "bygramPageBackground(this, getBackground(), false)" in profile
assert "drawBygramPageBackground(c, root);" in profile
assert "glassBackground != null && !isCapturingProfileGlass()" in profile
assert "glassBackground.setCallback(target)" in profile
for folder in ("values", "values-ru", "values-uk"):
    entries = {n.get("name"): "".join(n.itertext()) for n in ET.parse(ROOT / "TMessagesProj/src/main/res" / folder / "bygram_strings.xml").getroot()}
    for key in ("BygramCustomTypingInfo", "BygramShareActivityInfo", "BygramLicenseMessage", "BygramLicenseUnavailable", "BygramLicenseActivationMessage", "BygramLicenseActivate", "BygramProfileViewsVerify", "BygramTypingVerify", "BygramBackgroundVerify"):
        assert not any(word in entries[key].lower() for word in ("full", "лиценз", "ліценз", "license", "бот", "bot")), (folder, key)
print("PASS: shared settings binding, conditional glass source, fallback wiring and neutral en/ru/uk wording")
