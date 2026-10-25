"""Exercise production push scheduling/waiting on JVM with payload decoding stubbed."""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "TMessagesProj/src/main/java/org/telegram/messenger/PushListenerController.java").read_text(encoding="utf-8")
method = "public static void processRemoteMessage" + source.split("public static void processRemoteMessage", 1)[1].split("private static String getReactedText", 1)[0]
assert not re.search(r"static\s+CountDownLatch\s+countDownLatch", source), "Push completion must not be shared"
assert "final CountDownLatch countDownLatch = new CountDownLatch(1);" in method
assert method.count("onDecryptError(countDownLatch);") == 3
oauth = method.split('case "OAUTH_REQUEST":', 1)[1].split("final long dialogId", 1)[0]
assert len(re.findall(r"countDownLatch.countDown\(\);\s+return;", oauth)) == 3, "Malformed OAuth must release its push"
decrypt = "private static void onDecryptError" + source.split("private static void onDecryptError", 1)[1].split("@Keep", 1)[0]
handoff = re.search(r"NotificationsController.getInstance\(currentAccount\).processNewMessages\(arrayList, true, true, countDownLatch\);", method).group()
# Keep the actual UI/stage scheduling and await; substitute only payload processing.
start = method.index("                int currentAccount = -1;")
end = method.rindex("            });")
method = method[:start] + '''
                int currentAccount = 0;
                ArrayList<Object> arrayList = new ArrayList<>();
                if (data.equals("decrypt-error")) {
                    onDecryptError(countDownLatch);
                } else {
                    HANDOFF
                }
'''.replace("HANDOFF", handoff) + method[end:]
harness = r'''
import java.util.*;
import java.util.concurrent.*;
public class PushCompletionCheck {
    @interface PushType {}
    static final int PUSH_TYPE_FIREBASE = 2;
    static class BuildVars { static boolean LOGS_ENABLED = false, DEBUG_VERSION = false; }
    static class SystemClock { static long elapsedRealtime() { return System.nanoTime() / 1000000; } }
    static class FileLog { static void d(String s) {} }
    static class Queue {
        BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        void postRunnable(Runnable r) { tasks.add(r); }
        void next() throws Exception {
            Runnable r = tasks.poll(2, TimeUnit.SECONDS);
            if (r == null) throw new AssertionError("Missing queued task");
            r.run();
        }
    }
    static Queue ui = new Queue(), notifications = new Queue();
    static class AndroidUtilities { static void runOnUIThread(Runnable r) { ui.postRunnable(r); } }
    static class ApplicationLoader { static void postInitApplication() {} }
    static class Utilities { static Queue stageQueue = new Queue(); }
    static class UserConfig {
        static final int MAX_ACCOUNT_COUNT = 2;
        static UserConfig getInstance(int a) { return new UserConfig(); }
        boolean isClientActivated() { return true; }
    }
    static class ConnectionsManager {
        static int resumed;
        static void onInternalPushReceived(int a) {}
        static ConnectionsManager getInstance(int a) { return new ConnectionsManager(); }
        void resumeNetworkMaybe() { resumed++; }
    }
    static class NotificationsController {
        static NotificationsController getInstance(int a) { return new NotificationsController(); }
        void processNewMessages(ArrayList<Object> a, boolean last, boolean fcm, CountDownLatch done) {
            notifications.postRunnable(done::countDown);
        }
    }
    // PRODUCTION
    static Thread begin(int type, String payload) throws Exception {
        Thread t = new Thread(() -> processRemoteMessage(type, payload, 0));
        t.setDaemon(true);
        t.start();
        waiting(t);
        ui.next();
        Utilities.stageQueue.next();
        return t;
    }
    static void waiting(Thread t) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (t.isAlive() && t.getState() != Thread.State.WAITING && System.nanoTime() < deadline) Thread.yield();
        if (!t.isAlive() || t.getState() != Thread.State.WAITING) throw new AssertionError("Push returned before completion");
    }
    static void finished(Thread t) throws Exception {
        t.join(2000);
        if (t.isAlive()) throw new AssertionError("Push did not finish after completion");
    }
    public static void main(String[] args) throws Exception {
        for (int type : new int[] {2, 13}) {
            for (int i = 0; i < 2; i++) {
                Thread t = begin(type, "message");
                waiting(t);
                notifications.next();
                finished(t);
            }
        }
        Thread a = begin(2, "message"), b = begin(13, "message");
        notifications.next();
        finished(a);
        waiting(b);
        Thread error = begin(2, "decrypt-error");
        finished(error);
        waiting(b);
        if (ConnectionsManager.resumed != 2) throw new AssertionError("Decrypt fallback lost account wakeups");
        notifications.next();
        finished(b);
        System.out.println("PASS: repeated FCM/HCM, overlapping pushes, decrypt fallback isolation; OAuth exit contracts");
    }
}
'''.replace("// PRODUCTION", method + decrypt)
with tempfile.TemporaryDirectory(prefix="bygram-push-check-") as directory:
    java = Path(directory) / "PushCompletionCheck.java"
    java.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-d", directory, str(java)], check=True, timeout=30)
    subprocess.run(["java", "-cp", directory, "PushCompletionCheck"], check=True, timeout=15)
