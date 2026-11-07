"""Run the production glass title placement across changing block widths."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
bar = (JAVA / "ui/ActionBar/ActionBar.java").read_text(encoding="utf-8")
block = bar.split("            if (titleGravityInGlass) {", 1)[1]
block = block.split("            glassDrawable.draw(canvas);", 1)[0].rsplit("}", 1)[0]
harness = r'''
public class TitleAlignmentCheck {
    static class Gravity { static final int HORIZONTAL_GRAVITY_MASK=7, RIGHT=5, CENTER_HORIZONTAL=1; }
    static class SimpleTextView {
        int width, left; float scale, x;
        int getMeasuredWidth() { return width; }
        int getHeight() { return 24; }
        int getLeft() { return left; }
        float getX() { return left; }
        void setPivotX(float v) { assert v == 0; }
        void setPivotY(float v) {}
        void setScaleX(float v) { scale=v; }
        void setScaleY(float v) { assert scale==v; }
        void setTranslationX(float v) { x=left+v; }
    }
    static boolean useContainerForTitles;
    static SimpleTextView titlesContainer = new SimpleTextView();
    static int dp(int v) { return v; }
    public static void main(String[] args) {
        SimpleTextView title=new SimpleTextView();
        SimpleTextView[] titleTextView={title,null};
        for (boolean container : new boolean[]{false,true})
        for (int left : new int[]{0,52}) for (int right : new int[]{200,280,400,800})
        for (int text : new int[]{0,70,150,1000}) {
            useContainerForTitles=container; titlesContainer.left=container ? 4 : 0;
            title.width=text; title.left=17;
            float[] positions=new float[3]; int index=0;
            for (int titleGravity : new int[]{3,1,5}) {
                __BLOCK__
                view.x += titlesContainer.left;
                float contentLeft=left+18, available=Math.max(0,right-left-36);
                assert title.x>=contentLeft;
                assert title.x+text*title.scale<=contentLeft+available+0.001f;
                positions[index++]=title.x;
            }
            if (text<right-left-36) {
                assert positions[0]<positions[1] && positions[1]<positions[2] : "Center and right coincide";
                assert Math.abs(positions[1]+text/2f-(left+right)/2f)<0.001f;
            }
        }
        System.out.println("PASS: title and emoji alignment follows glass bounds, including narrow blocks");
    }
}
'''.replace("SimpleTextView title=new", "SimpleTextView view=new").replace("={title,null}", "={view,null}").replace("title.", "view.").replace("__BLOCK__", block)
with tempfile.TemporaryDirectory(prefix="bygram-title-") as temp:
    path = Path(temp) / "TitleAlignmentCheck.java"
    path.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(path)], check=True)
    subprocess.run(["java", "-ea", "-cp", temp, "TitleAlignmentCheck"], check=True)

stories = (JAVA / "ui/Stories/DialogStoriesCell.java").read_text(encoding="utf-8")
assert "actionBar.getLeft() + actionBar.getTitleBlockRight() - getLeft()" in stories
for file in ("ui/DialogsActivity.java", "ui/BygramSettingsActivity.java"):
    assert "setTitleGravityInGlass(position" in (JAVA / file).read_text(encoding="utf-8")
