"""Exercise the production activity publishing gate on the JVM without Android."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
controller = (root / 'TMessagesProj/src/main/java/org/telegram/messenger/BygramActivityController.java').read_text(encoding='utf-8')
start = controller.index('public static void setCurrentActivity(')
method = controller[start:controller.index('    private static void scheduleHeartbeat()', start)]
harness = r'''
import java.util.*;
public class LiteActivityCheck {
    static class BuildConfig { static boolean BYGRAM_FULL; }
    static class BygramConfig {
        static final int ACTIVITY_DIALOGS = 1, ACTIVITY_PROFILE = 2, ACTIVITY_CHAT = 4;
        static boolean enabled;
        static Set<Long> viewers = new HashSet<>();
        static boolean isActivityEnabled(int a) { return enabled; }
        static Set<Long> getActivityViewers(int a) { return viewers; }
    }
    static class TLRPC {
        static class User { boolean bot; }
        static class Chat { String title = "Group"; }
    }
    static class UserConfig {
        static UserConfig getInstance(int a) { return new UserConfig(); }
        long getClientUserId() { return 42; }
    }
    static class UserObject {
        static String getPublicUsername(TLRPC.User u) { return null; }
        static String getUserName(TLRPC.User u) { return "User"; }
    }
    static class ChatObject {
        static String getPublicUsername(TLRPC.Chat c) { return null; }
        static boolean isChannelAndNotMegaGroup(TLRPC.Chat c) { return false; }
    }
    static final int PEER_USER = 1, PEER_BOT = 2, PEER_GROUP = 3, PEER_CHANNEL = 4;
    static class OwnActivity { OwnActivity(int t, long id, String name, int kind) {} }
    static OwnActivity[] ownActivities = new OwnActivity[1];
    static boolean licensed;
    static int sent;
    static boolean isLicensed(int a) { return licensed; }
    static void sendActivity(int a) { sent++; }
    static void scheduleHeartbeat() {}
    METHOD
    static void check(boolean full, boolean consent, boolean license, boolean viewer, int type, long peer, int expected) {
        BuildConfig.BYGRAM_FULL = full;
        BygramConfig.enabled = consent;
        licensed = license;
        BygramConfig.viewers.clear();
        if (viewer) BygramConfig.viewers.add(99L);
        sent = 0;
        setCurrentActivity(0, type, peer, type == 1 ? null : new TLRPC.User(), null);
        if (sent != expected) throw new AssertionError("Publishing gate: " + full + "/" + consent + "/" + license + "/" + viewer + "/" + type);
    }
    public static void main(String[] args) {
        for (boolean full : new boolean[]{false, true})
            for (boolean consent : new boolean[]{false, true})
                for (boolean license : new boolean[]{false, true})
                    for (boolean viewer : new boolean[]{false, true})
                        for (int type : new int[]{1, 2, 4})
                            check(full, consent, license, viewer, type, type == 1 ? 0 : 99,
                                consent && license && (!full || viewer) ? 1 : 0);
        check(false, true, true, false, 8, 99, 0);
        check(false, true, true, false, 4, 42, 0);
        check(false, true, true, false, 2, 0, 0);
        System.out.println("PASS: 96 edition/consent/license/recipient cases and invalid activities");
    }
}
'''.replace('METHOD', method)
with tempfile.TemporaryDirectory(prefix='bygram-lite-activity-') as temp:
    path = Path(temp) / 'LiteActivityCheck.java'
    path.write_text(harness, encoding='utf-8')
    subprocess.run(['javac', str(path)], check=True)
    subprocess.run(['java', '-cp', temp, 'LiteActivityCheck'], check=True)

settings = (root / 'TMessagesProj/src/main/java/org/telegram/ui/DebugSettingsActivity.java').read_text(encoding='utf-8')
profile = (root / 'TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java').read_text(encoding='utf-8')
assert 'if (!org.telegram.messenger.BuildConfig.BYGRAM_FULL)' in settings
assert 'case ID_BYGRAM_LITE_ACTIVITY:' in settings
assert 'which == 39 && !org.telegram.messenger.BuildConfig.BYGRAM_FULL' in profile
assert 'BygramSettingsActivity.toggleLiteActivity(' in settings and 'BygramSettingsActivity.toggleLiteActivity(' in profile
page = (root / 'TMessagesProj/src/main/java/org/telegram/ui/BygramSettingsActivity.java').read_text(encoding='utf-8')
toggle = page.split('public static void toggleLiteActivity(', 1)[1].split('private static void handleLiteActivityLicense(', 1)[0]
assert toggle.index('setPositiveButton') < toggle.index('setActivityEnabled(account, true)')
assert 'setNegativeButton(getString(R.string.Cancel), null)' in toggle
assert 'setMessage(getString(R.string.BygramActivityTestWarning))' in toggle
assert toggle.index('setActivityEnabled(account, false)') < toggle.index('BygramActivityController.clear(account)')
assert 'return BuildConfig.BYGRAM_FULL && super.onFragmentCreate();' in page
assert 'showDeviceLimit(fragment, account, devices,' in page
assert 'BygramActivityController.replaceDevice(account, ids[which], callback)' in page
print('PASS: both debug menus route to the same Lite toggle')
