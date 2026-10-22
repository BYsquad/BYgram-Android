"""Run the real storage retention decision on fake rows without Android/JNI."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java").read_text(encoding="utf-8")
method = "public int getBygramDialogRetentionStateSync" + source.split("public int getBygramDialogRetentionStateSync", 1)[1].split("public void markDialogMessagesAsBygramDeleted", 1)[0]
harness = r"""
import java.util.concurrent.CountDownLatch;
public class SpyRetentionCheck {
    static class BygramConfig {
        static final String SPY_SAVE_OWN_DELETED = "own";
        static boolean keepOwn;
        static boolean getBoolean(String key) { return keepOwn; }
    }
    static class NativeByteBuffer {
        final boolean saved;
        NativeByteBuffer(boolean saved) { this.saved = saved; }
        void reuse() {}
    }
    static class TLRPC {
        static class Message { boolean bygramDeleted; }
        static class TL_message extends Message {}
    }
    static class MessageCustomParamsHelper {
        static void readLocalParams(TLRPC.Message message, NativeByteBuffer params) {
            message.bygramDeleted = params != null && params.saved;
        }
    }
    static class SQLiteCursor {
        final int[][] rows;
        int index = -1;
        boolean disposed;
        SQLiteCursor(int[][] rows) { this.rows = rows; }
        boolean next() { return ++index < rows.length; }
        int intValue(int column) { return rows[index][column]; }
        NativeByteBuffer byteBufferValue(int column) { return new NativeByteBuffer(rows[index][column] != 0); }
        void dispose() { disposed = true; }
    }
    static class Queue { void postRunnable(Runnable task) { task.run(); } }
    static class Database {
        int[][] rows;
        SQLiteCursor cursor;
        boolean fail;
        SQLiteCursor queryFinalized(String sql, long did) {
            if (fail) throw new IllegalStateException("unavailable");
            if (!sql.equals("SELECT out, custom_params FROM messages_v2 WHERE uid = ?") || did != 42) throw new AssertionError(sql);
            return cursor = new SQLiteCursor(rows);
        }
    }
    final Queue storageQueue = new Queue();
    final Database database = new Database();
    void checkSQLException(Exception e) {}
    // INSERT_METHOD
    static void check(boolean own, int expected, int[]... rows) {
        check(own, false, expected, rows);
    }
    static void check(boolean own, boolean wholeChat, int expected, int[]... rows) {
        SpyRetentionCheck storage = new SpyRetentionCheck();
        storage.database.rows = rows;
        BygramConfig.keepOwn = own;
        int actual = wholeChat ? storage.getBygramDialogRetentionStateSync(42, true) : storage.getBygramDialogRetentionStateSync(42);
        if (actual != expected || !storage.database.cursor.disposed) throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        check(false, 0);
        check(false, 0, new int[]{1, 0});
        check(true, 1, new int[]{1, 0});
        check(false, 1, new int[]{0, 0});
        check(false, 1, new int[]{1, 0}, new int[]{0, 0});
        check(false, 2, new int[]{0, 1}, new int[]{1, 1});
        check(true, 2, new int[]{1, 1});
        check(false, 1, new int[]{0, 1}, new int[]{1, 0});
        check(false, 1, new int[]{1, 0}, new int[]{0, 1});
        check(false, 1, new int[]{0, 1}, new int[]{0, 0});
        check(false, true, 1, new int[]{1, 0});
        check(false, true, 1, new int[]{1, 0}, new int[]{1, 0});
        check(false, true, 2, new int[]{1, 1}, new int[]{1, 1});
        check(false, true, 1, new int[]{1, 1}, new int[]{1, 0});
        check(false, true, 0);
        SpyRetentionCheck unavailable = new SpyRetentionCheck();
        unavailable.database.fail = true;
        if (unavailable.getBygramDialogRetentionStateSync(42) != 1) throw new AssertionError("Failure must keep history");
        System.out.println("PASS: 16 storage retention scenarios (first/repeat delete, whole chats with own toggle off, new messages, unavailable DB)");
    }
}
""".replace("// INSERT_METHOD", method)
with tempfile.TemporaryDirectory(prefix="bygram-spy-check-") as directory:
    java = Path(directory) / "SpyRetentionCheck.java"
    java.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-d", directory, str(java)], check=True)
    subprocess.run(["java", "-cp", directory, "SpyRetentionCheck"], check=True)
