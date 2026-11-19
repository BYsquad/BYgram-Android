"""Exercise the profile's real section painter and normal dialog header bounds."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
profile = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java").read_text(encoding="utf-8")
method = "public void drawBackgroundRect(" + profile.split("public void drawBackgroundRect(", 1)[1].split("\n            private VelocityTracker", 1)[0]
dialogs = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java").read_text(encoding="utf-8")
bounds = dialogs.split("// The header already draws separate glass blocks;", 1)[0].rsplit("                    if (", 1)[1]
bounds = "if (" + bounds + "// The header already draws separate glass blocks;" + dialogs.split("// The header already draws separate glass blocks;", 1)[1].split("                    drawBlurRect", 1)[0]
harness = r'''
class Base {
    int fallback;
    public void drawBackgroundRect(Check.Canvas c, Check.RectF r, float t, float b, float a) { fallback++; }
}
public class Check extends Base {
    static class Canvas {}
    static class RectF { float left=12, top=-30, right=388, bottom=250; }
    static class Glass {
        int draws, alpha, left, top, right, bottom; float[] radii;
        void setRadius(float a,float b,float c,float d) { radii=new float[]{a,b,c,d}; }
        void setBounds(int l,int t,int r,int b) { left=l; top=t; right=r; bottom=b; }
        void setAlpha(int a) { alpha=a; }
        void draw(Canvas c) { draws++; }
    }
    static class Image { boolean loaded; boolean hasImageLoaded() { return loaded; } }
    static class Top { Image bygramBackground=new Image(); }
    static class BygramConfig { static final String FULL_PROFILE_BACKGROUND="full"; static boolean enabled; static boolean getBoolean(String k) { return enabled; } }
    Top topView=new Top(); Glass sectionGlass=new Glass(); Object glassSource=new Object();
    boolean capturing;
    Object getGlassSource() { return glassSource; }
    boolean isCapturingProfileGlass() { return capturing; }
    METHOD
    static class Rect { int top; }
    static class Bar { int getBottom() { return 80; } }
    static final int DIALOGS_TYPE_DEFAULT=0;
    boolean onlySelect; int initialDialogsType, communityId; float searchAnimationProgress;
    Rect blurBounds=new Rect(); Bar actionBar=new Bar();
    void header() { BOUNDS }
    public static void main(String[] args) {
        Check t=new Check(); Canvas c=new Canvas(); RectF r=new RectF();
        t.drawBackgroundRect(c,r,16,8,1); assert t.fallback==1;
        BygramConfig.enabled=true;
        t.drawBackgroundRect(c,r,16,8,1); assert t.fallback==2;
        t.topView.bygramBackground.loaded=true;
        t.drawBackgroundRect(c,r,16,8,.5f);
        assert t.fallback==2 && t.sectionGlass.draws==1 && t.sectionGlass.alpha==127;
        assert t.sectionGlass.top==-30 && t.sectionGlass.bottom==250 && t.sectionGlass.left==12 && t.sectionGlass.right==388;
        assert java.util.Arrays.equals(t.sectionGlass.radii,new float[]{16,16,8,8});
        t.drawBackgroundRect(c,r,0,0,1); assert t.sectionGlass.alpha==255 && t.sectionGlass.radii[0]==0;
        t.glassSource=null; t.drawBackgroundRect(c,r,16,16,1); assert t.sectionGlass.alpha==193;
        int draws=t.sectionGlass.draws; t.capturing=true;
        t.drawBackgroundRect(c,r,16,16,1); assert t.sectionGlass.draws==draws;
        t.topView=null; t.drawBackgroundRect(c,r,16,16,1); assert t.fallback==3;
        t.header(); assert t.blurBounds.top==80;
        t.blurBounds.top=140; t.header(); assert t.blurBounds.top==140;
        t.blurBounds.top=0; t.onlySelect=true; t.header(); assert t.blurBounds.top==0;
        t.onlySelect=false; t.communityId=1; t.header(); assert t.blurBounds.top==0;
        t.communityId=0; t.initialDialogsType=1; t.header(); assert t.blurBounds.top==0;
        t.initialDialogsType=0; t.searchAnimationProgress=.5f; t.header(); assert t.blurBounds.top==0;
        System.out.println("PASS: profile section glass, alpha, corners, fallback; dialog glass bounds and special modes");
    }
}
'''.replace("METHOD", method).replace("BOUNDS", bounds)
with tempfile.TemporaryDirectory(prefix="bygram-section-glass-") as tmp:
    java = Path(tmp) / "Check.java"
    java.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp, "Check"], check=True)
