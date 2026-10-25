"""Exercise the actual header backdrop traversal with a small JVM view tree."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java").read_text(encoding="utf-8")
method = source.split("    private void drawPageGlassBackground(", 1)[1].split("    public INavigationLayout.BackButtonState", 1)[0]
method = "    private void drawPageGlassBackground(" + method
method = method.replace("android.view.ViewParent", "ViewParent")
title_bounds = source.split("} else if (pageGlassColor != null && titleTextView[0] != null", 1)[1]
title_bounds = title_bounds.split(") {", 1)[1].split("} else {", 1)[0]
top_bounds = source.split("        final int t = pageGlassColor != null", 1)[1].split(";", 1)[0]
title_alpha = "    private float getPageGlassTitleAlpha()" + source.split("    private float getPageGlassTitleAlpha()", 1)[1].split("    @Override", 1)[0]

assert "glassMode || parentFragment == null" in source, "Keep native chat glass and standalone viewers"
assert "source != null ? source : color" in source, "Old Android needs a color fallback"
assert "pageGlassSource.inRecording()" in source, "Backdrop recording must not re-enter itself"
assert "blurredBackground && !glassMode" in source, "A rectangular scrim must not cover the blocks"
assert "addOnScrollChangedListener(pageGlassScrollListener)" in source
assert "removeOnScrollChangedListener(pageGlassScrollListener)" in source

settings = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/BygramSettingsActivity.java").read_text(encoding="utf-8")
page = settings.split("abstract class BygramSettingsPage extends BaseFragment", 1)[1].split("public class BygramSettingsActivity", 1)[0]
assert "bar.setDrawGlassTitle(false)" in page
assert "bar.setOpaquePageBackground(Theme.key_windowBackgroundGray)" in page
root = settings.split("public View createView(Context context)", 1)[1].split("private void fillItems", 1)[0]
assert 'actionBar.setTitle("")' in root and "setAdaptiveBackground" not in root
draw = source.split("protected void dispatchDraw(Canvas canvas)", 1)[1]
assert "pageBackgroundShade" not in source
assert "class ProfileViewUserActivity extends BygramSettingsPage" in settings

harness = r'''
class ViewParent {
    ViewParent parent;
    ViewParent getParent() { return parent; }
}
class Canvas {
    enum EdgeType { AA }
    static final int ALL_SAVE_FLAG = 0;
    int depth;
    boolean rejected;
    boolean quickReject(int l, int t, int r, int b, EdgeType edge) { return rejected; }
    void save() { depth++; }
    void restore() { if (--depth < 0) throw new AssertionError("Unbalanced canvas"); }
    void translate(int x, int y) {}
    void concat(Object matrix) {}
    void saveLayerAlpha(int l, int t, int r, int b, int alpha, int flags) { depth++; }
}
class Background {
    int draws;
    void draw(Canvas c) { draws++; }
}
class AndroidUtilities { static int statusBarHeight = 24; }
class View extends ViewParent {
    static final int VISIBLE = 0;
    int visibility, draws;
    float alpha = 1;
    Background background;
    int getVisibility() { return visibility; }
    float getAlpha() { return alpha; }
    int getLeft() { return 0; }
    int getRight() { return getLeft() + getWidth(); }
    int getTop() { return 0; }
    int getWidth() { return 360; }
    int getHeight() { return 56; }
    Object getMatrix() { return null; }
    Background getBackground() { return background; }
    void draw(Canvas c) { draws++; }
}
class ViewGroup extends View {
    java.util.ArrayList<View> children = new java.util.ArrayList<>();
    void add(View v) { children.add(v); v.parent = this; }
    int getChildCount() { return children.size(); }
    View getChildAt(int i) { return children.get(i); }
    int getScrollX() { return 0; }
    int getScrollY() { return 0; }
}
class SimpleTextView extends View {
    CharSequence text = "Settings";
    CharSequence getText() { return text; }
}
class TextUtils {
    static boolean isEmpty(CharSequence text) { return text == null || text.length() == 0; }
}
public class PageHeaderCheck extends View {
__METHOD__
__TITLE_ALPHA__
    SimpleTextView[] titleTextView = {new SimpleTextView() {
        int getLeft() { return 76; }
        int getWidth() { return 84; }
    }};
    View subtitleTextView;
    float searchFieldVisibleAlpha, searchFactor, actionModeFactor;
    Object pageGlassColor = new Object();
    boolean useContainerForTitles;
    View titlesContainer = new View();
    float getActionModeFactor() { return actionModeFactor; }
    boolean occupyStatusBar = true;
    int left, right;
    int dp(int value) { return value; }
    int lerp(int a, int b, float progress) { return (int) (a + (b - a) * progress); }
    void titleBounds(int leftDefault, int rightDefault) {
__BOUNDS__
    }
    int getHeight() { return 200; }
    int getCurrentActionBarHeight() { return 56; }
    int topBounds() {
        int s = 46, p = 6;
        return pageGlassColor != null __TOP__;
    }
    public static void main(String[] args) {
        PageHeaderCheck header = new PageHeaderCheck();
        ViewGroup root = new ViewGroup(), nested = new ViewGroup();
        View content = new View(), faded = new View(), hidden = new View(), transparent = new View();
        root.background = new Background();
        nested.background = new Background();
        root.add(content); root.add(nested); nested.add(header); nested.add(faded);
        root.add(hidden); root.add(transparent);
        faded.alpha = .5f; hidden.visibility = 8; transparent.alpha = 0;
        Canvas canvas = new Canvas();
        header.drawPageGlassBackground(canvas, root);
        assert header.draws == 0 : "Header sampled into itself";
        assert root.draws == 0 && nested.draws == 0 : "Ancestor display list sampled the header";
        assert content.draws == 1 && faded.draws == 1 : "Visible content missing";
        assert root.background.draws == 1 && nested.background.draws == 1 : "Background missing";
        assert hidden.draws == 0 && transparent.draws == 0 : "Invisible content sampled";
        assert canvas.depth == 0 : "Alpha layer leaked";
        View separateRoot = new View();
        header.drawPageGlassBackground(canvas, separateRoot);
        assert separateRoot.draws == 1 : "Separate fragment content missing";
        canvas.rejected = true;
        header.drawPageGlassBackground(canvas, root);
        assert content.draws == 1 : "Offscreen content rendered";
        header.titleBounds(52, 500);
        assert header.left == 58 && header.right == 178 : "Short title padding";
        header.titleBounds(52, 85);
        assert header.right == 85 : "Title overlaps menu";
        header.searchFieldVisibleAlpha = 1;
        header.titleBounds(52, 500);
        assert header.left == 52 && header.right == 500 : "Search must expand title glass";
        header.searchFieldVisibleAlpha = 0;
        header.actionModeFactor = 1;
        header.titleBounds(52, 500);
        assert header.left == 52 && header.right == 500 : "Selection must expand title glass";
        header.actionModeFactor = 0;
        assert header.getPageGlassTitleAlpha() == 1 : "Visible title lost its background";
        header.titleTextView[0].alpha = 0;
        assert header.getPageGlassTitleAlpha() == 0 : "Adaptive title left an empty pill";
        header.titleTextView[0].alpha = .5f;
        assert header.getPageGlassTitleAlpha() == .5f : "Title background must fade with the text";
        header.titleTextView[0].alpha = 1;
        header.useContainerForTitles = true;
        header.titlesContainer.alpha = 0;
        assert header.getPageGlassTitleAlpha() == 0 : "Settings title container left an empty pill";
        header.searchFieldVisibleAlpha = 1;
        assert header.getPageGlassTitleAlpha() == 1 : "Search background must remain visible";
        header.searchFieldVisibleAlpha = 0;
        header.actionModeFactor = 1;
        assert header.getPageGlassTitleAlpha() == 1 : "Selection background must remain visible";
        header.actionModeFactor = 0;
        header.titlesContainer.alpha = 1;
        header.titlesContainer.visibility = 4;
        assert header.getPageGlassTitleAlpha() == 0 : "Invisible container left an empty pill";
        header.titlesContainer.visibility = 0;
        header.titleTextView[0].text = "";
        assert header.getPageGlassTitleAlpha() == 0 : "Empty title left a pill";
        assert header.topBounds() == 23 : "Extra-height tabs must not move page glass below its title";
        header.occupyStatusBar = false;
        assert header.topBounds() == -1 : "Tablet/preview status bar inset";
        header.pageGlassColor = null;
        assert header.getPageGlassTitleAlpha() == 1 : "Native chat/story glass changed";
        assert header.topBounds() == 143 : "Native chat glass geometry changed";
        System.out.println("PASS: page glass backdrop traversal, exclusion, visibility, alpha, clipping and separate roots");
        System.out.println("PASS: compact title padding, menu bounds, search and selection expansion");
        System.out.println("PASS: hidden/empty titles, container and adaptive fades, search, selection and native glass");
    }
}
'''.replace("__METHOD__", method).replace("__BOUNDS__", title_bounds).replace("__TOP__", top_bounds).replace("__TITLE_ALPHA__", title_alpha)

with tempfile.TemporaryDirectory(prefix="bygram-headers-") as tmp:
    java = Path(tmp) / "PageHeaderCheck.java"
    java.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp, "PageHeaderCheck"], check=True)
