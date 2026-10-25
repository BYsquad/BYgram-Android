"""Run the real profile backdrop method against viewport and fallback fixtures."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
profile = (JAVA / "ui/ProfileActivity.java").read_text(encoding="utf-8")
dialogs = (JAVA / "ui/DialogsActivity.java").read_text(encoding="utf-8")
scroll = dialogs.split("public void onScrolled(RecyclerView recyclerView, int dx, int dy) {", 1)[1].split("                    if (contentView != null)", 1)[0]
method = "    private boolean drawBygramPageBackground(" + profile.split("    private boolean drawBygramPageBackground(", 1)[1].split("    private Drawable bygramPageBackground", 1)[0]
config = (JAVA / "messenger/BygramConfig.java").read_text(encoding="utf-8")
active = "    private static boolean isActiveSetting(" + config.split("    private static boolean isActiveSetting(", 1)[1].split("    public static void clearRemovedCustomizationSettings", 1)[0]
constants = "\n".join(line for line in config.splitlines() if 'static final String' in line)
harness = r"""
public class ProfileScrollCheck {
    static class Canvas {
        int saves, draws; float dx, dy;
        void save() { saves++; }
        void restore() { saves--; }
        void translate(float x, float y) { dx=x; dy=y; }
        void drawColor(int color) { draws++; }
    }
    static class View {
        final int x, y;
        View(int x, int y) { this.x=x; this.y=y; }
        void getLocationInWindow(int[] p) { p[0]=x; p[1]=y; }
        int getWidth() { return 400; }
        int getHeight() { return 800; }
    }
    static class ImageReceiver {
        boolean loaded=true; int draws; float x, y, width, height;
        boolean hasImageLoaded() { return loaded; }
        void setImageCoords(float x, float y, float w, float h) { this.x=x; this.y=y; width=w; height=h; }
        void draw(Canvas c) { draws++; }
    }
    static class TopView extends View { TopView() { super(20,30); } ImageReceiver bygramBackground = new ImageReceiver(); }
    static class BygramConfig {
        static final String FULL_PROFILE_BACKGROUND="full", COLLAPSE_FOLDERS_ON_SCROLL="folders", COLLAPSE_NAVIGATION_ON_SCROLL="navigation";
        static boolean enabled, folders, navigation;
        static boolean getBoolean(String key) { return key.equals("full") ? enabled : key.equals("folders") ? folders : navigation; }
    }
    static class ActionBar { boolean mode; boolean isActionModeShowed() { return mode; } }
    static class RecyclerView { boolean canScroll=true; boolean canScrollVertically(int direction) { return canScroll; } }
    static class BoolAnimator { boolean hidden; void setValue(boolean value, boolean animated) { hidden=value; } }
    final Object[] viewPages={new Object()}; final ActionBar actionBar=new ActionBar();
    final BoolAnimator bygramFoldersHidden=new BoolAnimator();
    boolean onlySelect, searching, bygramNavigationHidden, scrollingManually=true; int folderId, communityId;
    void checkUi_mainTabsVisible() {}
    void scroll(Object viewPage, RecyclerView recyclerView, int dy) { @@SCROLL@@ }
    static class Theme { static final int key_windowBackgroundGray=1; static int multAlpha(int color, float alpha) { return color; } }
    int getThemedColor(int key) { return key; }
    TopView topView = new TopView(); View fragmentView = new View(20, 30);
    final int[] bygramBackgroundRootPosition = new int[2], bygramBackgroundTargetPosition = new int[2];
    METHOD
    CONSTANTS
    ACTIVE
    public static void main(String[] args) {
        ProfileScrollCheck test = new ProfileScrollCheck(); Canvas canvas = new Canvas(); View row=new View(20, 230);
        assert !test.drawBygramPageBackground(canvas,row) && canvas.draws==0;
        BygramConfig.enabled=true;
        test.topView.bygramBackground.loaded=false;
        assert !test.drawBygramPageBackground(canvas,row) && canvas.draws==0;
        test.topView.bygramBackground.loaded=true;
        assert test.drawBygramPageBackground(canvas,row);
        assert canvas.saves==0 && canvas.dx==0 && canvas.dy==-200 && canvas.draws==1;
        ImageReceiver image=test.topView.bygramBackground;
        assert image.x==0 && image.y==0 && image.width==400 && image.height==800 && image.draws==1;
        assert test.drawBygramPageBackground(canvas,new View(70,430));
        assert canvas.dx==-50 && canvas.dy==-400 && image.height==800;
        test.topView=null;
        assert !test.drawBygramPageBackground(canvas,row);
        for (String key : new String[]{FULL_PROFILE_BACKGROUND,COLLAPSE_FOLDERS_ON_SCROLL,COLLAPSE_NAVIGATION_ON_SCROLL}) assert isActiveSetting(key);
        assert !isActiveSetting("bygram_unknown");
        System.out.println("PASS: real backdrop viewport alignment, missing-image fallback and active setting keys");
        RecyclerView list=new RecyclerView(); Object page=test.viewPages[0];
        test.scroll(page,list,5); assert !test.bygramFoldersHidden.hidden && !test.bygramNavigationHidden;
        BygramConfig.folders=true;
        test.scroll(page,list,5); assert test.bygramFoldersHidden.hidden && !test.bygramNavigationHidden;
        test.scroll(page,list,-5); assert !test.bygramFoldersHidden.hidden;
        BygramConfig.folders=false; BygramConfig.navigation=true;
        test.scroll(page,list,5); assert !test.bygramFoldersHidden.hidden && test.bygramNavigationHidden;
        test.scroll(page,list,-5); assert !test.bygramNavigationHidden;
        test.onlySelect=true; test.scroll(page,list,5); assert !test.bygramNavigationHidden; test.onlySelect=false;
        test.folderId=1; test.scroll(page,list,5); assert !test.bygramNavigationHidden; test.folderId=0;
        test.searching=true; test.scroll(page,list,5); assert !test.bygramNavigationHidden; test.searching=false;
        test.actionBar.mode=true; test.scroll(page,list,5); assert !test.bygramNavigationHidden; test.actionBar.mode=false;
        test.scrollingManually=false; test.scroll(page,list,5); assert !test.bygramNavigationHidden; test.scrollingManually=true;
        test.scroll(new Object(),list,5); assert !test.bygramNavigationHidden;
        list.canScroll=false; test.scroll(page,list,5); assert !test.bygramNavigationHidden;
        System.out.println("PASS: real scroll handler, independent options, direction, top, archive, selection and search guards");
    }
}
""".replace("METHOD", method).replace("@@SCROLL@@", scroll).replace("CONSTANTS", constants).replace("ACTIVE", active)
with tempfile.TemporaryDirectory(prefix="bygram-profile-scroll-") as directory:
    source = Path(directory) / "ProfileScrollCheck.java"
    source.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(source)], check=True)
    subprocess.run(["java", "-ea", "-cp", directory, "ProfileScrollCheck"], check=True)

for language in ("values", "values-ru", "values-uk"):
    import xml.etree.ElementTree as ET
    entries = {item.attrib.get("name") for item in ET.parse(ROOT / f"TMessagesProj/src/main/res/{language}/bygram_strings.xml").getroot()}
    assert {"BygramFullProfileBackground", "BygramCollapseFoldersOnScroll", "BygramCollapseNavigationOnScroll"} <= entries
print("PASS: new Android options are translated in en/ru/uk")
