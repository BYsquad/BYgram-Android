import android.app.UiAutomation;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.reflect.*;
import java.util.*;

/** Verify ACTION_CLICK through Android accessibility, without coordinate injection or changing TalkBack. */
public final class PluginAccessibilityCheck {
    public static void main(String[] args) throws Exception {
        try { run(args); } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        Looper.prepareMainLooper();
        HandlerThread thread = new HandlerThread("BYgramAccessibilityTest"); thread.start();
        Object connection = Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance();
        Constructor<?> constructor = Arrays.stream(UiAutomation.class.getDeclaredConstructors())
                .filter(candidate -> candidate.getParameterCount() == 2 && candidate.getParameterTypes()[0] == Looper.class).findFirst().get();
        constructor.setAccessible(true);
        UiAutomation automation = (UiAutomation) constructor.newInstance(thread.getLooper(), connection);
        UiAutomation.class.getMethod("connect", int.class).invoke(automation, UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        try {
            AccessibilityNodeInfo root = null;
            long deadline = android.os.SystemClock.uptimeMillis() + 5000;
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                root = automation.getRootInActiveWindow();
                if (root != null && "com.bygram.mobile".contentEquals(root.getPackageName())) break;
                android.os.SystemClock.sleep(100);
            }
            if (root == null || !"com.bygram.mobile".contentEquals(root.getPackageName())) throw new AssertionError("Open the BYgram plugin page first: " + (root == null ? "no root" : root.getPackageName()));
            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            Deque<AccessibilityNodeInfo> pending = new ArrayDeque<>(); pending.add(root);
            while (!pending.isEmpty()) {
                AccessibilityNodeInfo node = pending.removeFirst(); nodes.add(node);
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i); if (child != null) pending.addLast(child);
                }
            }
            AccessibilityNodeInfo target = null;
            for (AccessibilityNodeInfo node : nodes) {
                if ((args[0].equals(String.valueOf(node.getText())) || args[0].equals(String.valueOf(node.getContentDescription()))) && node.isClickable()) { target = node; break; }
            }
            if (target == null) throw new AssertionError("Enabled plugin row is not exposed as clickable");
            if (!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new AssertionError("ACTION_CLICK rejected");
            System.out.println("PASS: plugin row exposes click action and Android accessibility dispatch accepts it");
        } finally {
            UiAutomation.class.getMethod("disconnect").invoke(automation); thread.quitSafely();
        }
    }
}
