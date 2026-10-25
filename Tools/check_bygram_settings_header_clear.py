"""Exercise ActionBar through Android's backgroundless ViewGroup drawing fast path."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
draw = 'public void draw(Canvas canvas)' + source.split('public void draw(Canvas canvas)', 1)[1].split('    private void drawPageGlassBackground', 1)[0]
setter = 'public void setOpaquePageBackground(int themeKey)' + source.split('public void setOpaquePageBackground(int themeKey)', 1)[1].split('    @Override', 1)[0]
harness = r'''
class Canvas {
    int pixel = 123, clears, buttons;
    void drawColor(int color) { pixel = color; clears++; }
    boolean isHardwareAccelerated() { return false; }
    void translate(int x, int y) {}
}
class View {
    boolean willNotDraw = true;
    void setWillNotDraw(boolean value) { willNotDraw = value; }
    void setBackground(Object background) {} // Removing glass background keeps WILL_NOT_DRAW.
    void drawFromParent(Canvas canvas) {
        if (willNotDraw) dispatchDraw(canvas);
        else draw(canvas);
    }
    void dispatchDraw(Canvas canvas) { canvas.buttons++; }
    void draw(Canvas canvas) { dispatchDraw(canvas); }
    void getLocationInWindow(int[] position) {}
}
class Build {
    static class VERSION { static int SDK_INT = 31; }
    static class VERSION_CODES { static final int S = 31; }
}
class Theme { static final int key_windowBackgroundWhite = 1; }
class Glass {
    boolean recording;
    boolean inRecording() { return recording; }
    void setColor(int c) {}
    int getColor() { return 9; }
    Canvas beginRecording(int w, int h) { return new Canvas(); }
    void endRecording() {}
    void invalidateDisplayListForDrawables() {}
    void updateColors() {}
}
class Fragment { View getFragmentView() { return null; } }
public class SettingsHeaderClearCheck extends View {
    Integer opaquePageBackgroundKey;
    int themeColor = 0xff17212b;
    Glass pageGlassColor = new Glass(), pageGlassSource = new Glass();
    Glass glassDrawable = new Glass(), glassDrawableBack = new Glass(), glassDrawableMenu = new Glass();
    Fragment parentFragment;
    int[] pageGlassPosition = new int[2], pageGlassRootPosition = new int[2];
    int getThemedColor(int key) { return themeColor; }
    int getWidth() { return 360; }
    int getHeight() { return 100; }
    void invalidate() {}
    void drawPageGlassBackground(Canvas canvas, View root) {}
    __SETTER__
    __DRAW__
    public static void main(String[] args) {
        SettingsHeaderClearCheck bar = new SettingsHeaderClearCheck();
        Canvas canvas = new Canvas();
        bar.drawFromParent(canvas);
        assert canvas.clears == 0 : "Other pages changed";
        bar.setOpaquePageBackground(2);
        bar.setBackground(null); // setupGlass() at first onMeasure must not restore the fast path.
        for (int sdk : new int[]{23, 31, 36}) {
            Build.VERSION.SDK_INT = sdk;
            for (int i = 0; i < 5; i++) {
                canvas.pixel = 123 + i; // Previous avatar or title left on the surface.
                int buttons = canvas.buttons;
                bar.drawFromParent(canvas);
                assert canvas.pixel == bar.themeColor : "Previous page leaked";
                assert canvas.buttons == buttons + 1 : "Buttons not rendered";
            }
        }
        bar.themeColor = 0xffeeeeee;
        bar.drawFromParent(canvas);
        assert canvas.pixel == bar.themeColor : "Theme change ignored";
        int clears = canvas.clears;
        bar.pageGlassSource.recording = true;
        bar.drawFromParent(canvas);
        assert canvas.clears == clears : "Glass recording reentered";
        bar.pageGlassSource = null;
        bar.pageGlassColor = null;
        bar.drawFromParent(canvas);
        assert canvas.clears == clears + 1 : "Non-glass fallback not cleared";
        System.out.println("PASS: backgroundless ViewGroup fast path, retained avatar/title clearing, repeated navigation, themes, SDK fallback and recording guard");
    }
}
'''.replace('__SETTER__', setter).replace('__DRAW__', draw)
with tempfile.TemporaryDirectory(prefix='bygram-header-clear-') as tmp:
    java = Path(tmp) / 'SettingsHeaderClearCheck.java'
    java.write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', str(java)], check=True)
    subprocess.run(['java', '-ea', '-cp', tmp, 'SettingsHeaderClearCheck'], check=True)
