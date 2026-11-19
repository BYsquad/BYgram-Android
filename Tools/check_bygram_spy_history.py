"""Execute the production history merge with a small persisted-row fixture."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java').read_text(encoding='utf-8')
method = 'public void mergeBygramDeletedMessagesSync' + source.split('public void mergeBygramDeletedMessagesSync', 1)[1].split('    public java.util.Set<Integer>', 1)[0]
harness = r'''
import java.util.*;
import java.util.concurrent.CountDownLatch;
public class SpyHistoryCheck {
 static class TLRPC {
  static class Message {
   int id, date; long dialog_id; boolean bygramDeleted;
   static Message TLdeserialize(NativeByteBuffer b, int c, boolean e) { return new Message(); }
   void readAttachPath(NativeByteBuffer b, long id) {}
  }
  static class TL_message extends Message {}
 }
 static class NativeByteBuffer {
  boolean deleted;
  NativeByteBuffer(boolean d) { deleted=d; }
  int readInt32(boolean b) { return 0; }
  void reuse() {}
 }
 static class MessageCustomParamsHelper {
  static void readLocalParams(TLRPC.Message m, NativeByteBuffer b) { m.bygramDeleted=b.deleted; }
  static void copyParams(TLRPC.Message a, TLRPC.Message b) { b.bygramDeleted=a.bygramDeleted; }
 }
 static class MessageObject { static void setUnreadFlags(TLRPC.Message m, int f) {} }
 static class Queue { void postRunnable(Runnable r) { r.run(); } }
 static class UserConfig { long clientUserId=1; }
 static class SQLiteCursor {
  int index=-1; List<Integer> rows;
  SQLiteCursor(List<Integer> r) { rows=r; }
  boolean next() { return ++index<rows.size(); }
  int intValue(int col) { return col==0 ? rows.get(index) : 0; }
  NativeByteBuffer byteBufferValue(int col) { return new NativeByteBuffer(rows.get(index)!=18); }
  void dispose() {}
 }
 static class Database {
  SQLiteCursor queryFinalized(String sql, Object... args) {
   int lo=(int)args[1], hi=(int)args[2];
   List<Integer> rows=new ArrayList<>();
   for (int id : new int[]{30,25,20,18,15,5}) if(id>=lo && id<=hi) rows.add(id);
   return new SQLiteCursor(rows);
  }
 }
 Queue storageQueue=new Queue(); Database database=new Database();
 UserConfig getUserConfig() { return new UserConfig(); }
 void checkSQLException(Exception e) { throw new AssertionError(e); }
 __METHOD__
 static TLRPC.Message msg(int id) { TLRPC.Message m=new TLRPC.Message(); m.id=id; return m; }
 static void check(boolean initial, int[] server, int... expected) {
  ArrayList<TLRPC.Message> rows=new ArrayList<>();
  for(int id:server) rows.add(msg(id));
  new SpyHistoryCheck().mergeBygramDeletedMessagesSync(42, rows, initial);
  if(!Arrays.equals(rows.stream().mapToInt(m->m.id).toArray(), expected)) throw new AssertionError(rows.stream().map(m->m.id).toList());
  for(TLRPC.Message m:rows) if(!Arrays.stream(server).anyMatch(id->id==m.id) && (!m.bygramDeleted || m.dialog_id!=42)) throw new AssertionError("Lost saved marker");
 }
 public static void main(String[] args) {
  check(true,new int[]{20,10},30,25,20,15,10);
  check(false,new int[]{20,10},20,15,10);
  check(false,new int[]{10,1},10,5,1);
  check(true,new int[]{},30,25,20,15,5);
  check(false,new int[]{});
  System.out.println("PASS: cold start, deleted latest message, page bounds, duplicates, live rows and empty history");
 }
}
'''.replace('__METHOD__', method)
with tempfile.TemporaryDirectory(prefix='bygram-spy-history-') as tmp:
    path = Path(tmp) / 'SpyHistoryCheck.java'
    path.write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', str(path)], check=True)
    subprocess.run(['java', '-ea', '-cp', tmp, 'SpyHistoryCheck'], check=True)
