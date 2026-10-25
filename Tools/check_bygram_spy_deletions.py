"""Exercise the production deletion decisions on the JVM without Android/JNI."""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java").read_text(encoding="utf-8")
method = "private boolean shouldKeepBygramDeletedMessage" + source.split("private boolean shouldKeepBygramDeletedMessage", 1)[1].split("private void processMessageIDUpdate", 1)[0]
push = "protected void deleteMessagesByPush" + source.split("protected void deleteMessagesByPush", 1)[1].split("public void checkChatInviter", 1)[0]
local = re.search(r"if \((alreadySaved.contains\(messageId\).*?)\) \{", source, re.S).group(1)
harness = r"""
import java.util.*;
public class SpyDeletionCheck {
    static class BygramConfig {
        static final String SPY_SAVE_DELETED = "keep", SPY_SAVE_OWN_DELETED = "own", SPY_NOTIFY_DELETED = "notify";
        static boolean keep, own, notify;
        static boolean getBoolean(String key) { return key.equals("keep") ? keep : key.equals("own") ? own : notify; }
    }
    static class TLRPC { static class Message { boolean out; Message(boolean out) { this.out = out; } } }
    static class MessageObject {
        TLRPC.Message messageOwner;
        long dialogId;
        long getDialogId() { return dialogId; }
        boolean deleted;
        int getId() { return 1; }
        boolean isBygramDeleted() { return false; }
    }
    static class DialogObject { static boolean isUserDialog(long id) { return id > 0; } }
    static class AndroidUtilities { static void runOnUIThread(Runnable r) { r.run(); } }
    static class UserConfig { long getClientUserId() { return 10; } }
    static class Queue { void postRunnable(Runnable r) { r.run(); } }
    static class NotificationCenter {
        static final int bygramMessagesDeleted = 1, messagesDeleted = 2;
        void postNotificationName(int event, Object... args) {}
    }
    static class Storage {
        TLRPC.Message message;
        long peer = 20;
        boolean saved, physicallyRemoved;
        Queue getStorageQueue() { return new Queue(); }
        void deletePushMessages(long peer, ArrayList<Integer> ids) {}
        ArrayList<Long> markMessagesAsDeleted(long peer, ArrayList<Integer> ids, boolean a, boolean b, int c, int d) { physicallyRemoved = true; return new ArrayList<>(); }
        void updateDialogsWithDeletedMessages(long peer, long channel, ArrayList<Integer> ids, ArrayList<Long> peers) {}
        long getDialogIdForMessageSync(int id) { return peer; }
        Set<Integer> getBygramDeletedMessageIdsSync(long peer, ArrayList<Integer> ids) { return saved ? Set.of(ids.get(0)) : Set.of(); }
        TLRPC.Message getMessage(long peer, int id) { return message; }
        void markMessageAsBygramDeleted(long peer, int id) { saved = true; }
    }
    static class Notifications {
        int count;
        void notifyBygramDeletedMessage(long peer) { if (BygramConfig.notify) count++; }
    }
    Storage storage = new Storage();
    Notifications notifications = new Notifications();
    Map<Integer, MessageObject> dialogMessagesByIds = new HashMap<>();
    Map<Long, ArrayList<MessageObject>> dialogMessage = new HashMap<>();
    NotificationCenter getNotificationCenter() { return new NotificationCenter(); }
    UserConfig getUserConfig() { return new UserConfig(); }
    Storage getMessagesStorage() { return storage; }
    Notifications getNotificationsController() { return notifications; }
    // PUSH
    // METHOD
    boolean locallyRemoved(long dialogId, TLRPC.Message storedMessage) {
        Set<Integer> alreadySaved = Set.of();
        MessageObject messageObject = null;
        int messageId = 1;
        return LOCAL;
    }
    static void check(boolean keep, boolean own, boolean notify, long peer, Boolean out, boolean expectedKeep, int expectedNotifications) {
        BygramConfig.keep = keep; BygramConfig.own = own; BygramConfig.notify = notify;
        SpyDeletionCheck c = new SpyDeletionCheck();
        c.storage.peer = peer;
        c.storage.message = out == null ? null : new TLRPC.Message(out);
        boolean kept = c.shouldKeepBygramDeletedMessage(1, 0);
        if (kept != expectedKeep || c.notifications.count != expectedNotifications) throw new AssertionError("peer=" + peer + " kept=" + kept + " notifications=" + c.notifications.count);
        if (kept) {
            c.shouldKeepBygramDeletedMessage(1, 0);
            if (c.notifications.count != expectedNotifications) throw new AssertionError("Repeated event notified again");
        }
    }
    public static void main(String[] args) {
        check(false, false, false, 20, false, false, 0);
        check(false, false, true, 20, false, false, 1);
        check(true, false, true, 20, false, true, 1);
        check(true, false, false, 20, false, true, 0);
        check(true, false, true, 20, true, false, 1);
        check(true, true, true, 20, true, true, 1);
        check(true, false, true, 10, false, false, 0);
        check(true, true, true, 10, false, true, 0);
        check(true, false, true, -20, false, true, 0);
        check(true, false, true, 20, null, false, 0);
        SpyDeletionCheck c = new SpyDeletionCheck();
        BygramConfig.own = false;
        if (!c.locallyRemoved(10, new TLRPC.Message(false))) throw new AssertionError("Saved Messages with out=false must be removed");
        if (c.locallyRemoved(20, new TLRPC.Message(false))) throw new AssertionError("Incoming private message must be retained");
        if (!c.locallyRemoved(20, new TLRPC.Message(true))) throw new AssertionError("Outgoing must be removed");
        BygramConfig.own = true;
        if (c.locallyRemoved(10, new TLRPC.Message(false))) throw new AssertionError("Own toggle must enable saving");
        c.storage.peer = 10;
        c.storage.message = new TLRPC.Message(false);
        BygramConfig.keep = true;
        BygramConfig.own = false;
        c.deleteMessagesByPush(10, new ArrayList<>(List.of(1)), 0);
        if (!c.storage.physicallyRemoved || c.storage.saved) throw new AssertionError("Push must physically remove own Saved Messages");
        c = new SpyDeletionCheck();
        c.storage.message = new TLRPC.Message(false);
        BygramConfig.keep = false;
        BygramConfig.notify = true;
        c.deleteMessagesByPush(20, new ArrayList<>(List.of(1)), 0);
        if (!c.storage.physicallyRemoved || c.notifications.count != 1) throw new AssertionError("Push notification must work with retention off");
        System.out.println("PASS: production local/remote deletion decisions, Saved Messages out=false, notification toggle, repeated events and missing records");
    }
}
""".replace("// PUSH", push).replace("// METHOD", method).replace("LOCAL", local)
with tempfile.TemporaryDirectory(prefix="bygram-deletion-check-") as directory:
    java = Path(directory) / "SpyDeletionCheck.java"
    java.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-d", directory, str(java)], check=True)
    subprocess.run(["java", "-cp", directory, "SpyDeletionCheck"], check=True)
