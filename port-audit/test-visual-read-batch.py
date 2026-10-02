"""Exercise production receipt caches without Instagram, Android or network access."""
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / 'extensions/instagram/src/main/java/app/morphe/extension/instagram/patches/dm'
with tempfile.TemporaryDirectory() as temporary:
    folder = Path(temporary)
    test = folder / 'VisualBatchTest.java'
    test.write_text('''
package app.morphe.extension.instagram.patches.dm;
public class VisualBatchTest {
 static int checks;
 static void require(boolean v) { checks++; if (!v) throw new AssertionError("check " + checks); }
 public static void main(String[] args) {
  Object controller = new Object(), first = new Object(), second = new Object();
  Object replacement = new Object(), fresh = new Object();
  PendingVisualReads batch = new PendingVisualReads(2, 100);
  batch.put("one", first, controller, 1);
  batch.put("two", second, controller, 2);
  require(batch.requests(3).size() == 2);
  require(batch.requests(3).get(0).media() == first && batch.requests(3).get(1).media() == second);
  batch.put("one", replacement, controller, 4);
  require(batch.requests(5).size() == 2 && batch.requests(5).get(1).media() == replacement);
  PendingVisualReads.Request oldReplacement = batch.requests(5).get(1);
  batch.put("one", fresh, controller, 6);
  require(!batch.remove(oldReplacement));
  require(batch.requests(7).size() == 2 && batch.requests(7).get(1).media() == fresh);
  PendingVisualReads.Request successful = batch.requests(7).get(0);
  PendingVisualReads.Request failed = batch.requests(7).get(1);
  require(batch.remove(successful));
  require(!batch.remove(successful));
  require(batch.requests(8).size() == 1 && batch.requests(8).get(0).media() == fresh);
  require(batch.requests(8).get(0) != failed && batch.requests(8).get(0).media() == failed.media());
  // A failed replay is intentionally not removed and remains available for retry.
  require(batch.requests(8).size() == 1);
  batch.put("three", first, controller, 6);
  require(batch.requests(7).size() == 2 && batch.requests(7).get(0).media() == fresh);
  batch.put("", first, controller, 8);
  batch.put("null", null, controller, 8);
  require(batch.requests(8).size() == 2);
  require(batch.requests(105).size() == 2);
  require(batch.requests(107).isEmpty());
  PendingReadCache<Object> sessions = new PendingReadCache<>(2, 100);
  Object loginA = new Object(), loginB = new Object();
  Object requestA = new Object(), requestB = new Object();
  sessions.put(loginA, "thread-one", 1, 1, requestA);
  require(sessions.get(loginA, "thread-two", 2) == null);
  require(sessions.get(loginB, "thread-one", 2) == null);
  sessions.put(loginA, "thread-one", 2, 2, requestB);
  require(!sessions.remove(loginA, "thread-one", requestA));
  require(sessions.get(loginA, "thread-one", 3) == requestB);
  require(sessions.remove(loginA, "thread-one", requestB));
  require(sessions.get(loginA, "thread-one", 3) == null);
  sessions.put(loginB, "thread-one", 2, 2, requestB);
  require(sessions.get(loginA, "thread-one", 3) == null);
  require(sessions.get(loginB, "thread-one", 103) == null);
  System.out.println(checks + " conditional receipt consumption checks passed; device/server validation is separate");
 }
}
''')
    subprocess.run(['javac', '-d', str(folder), str(source / 'PendingVisualReads.java'),
                    str(source / 'PendingReadCache.java'), str(test)], check=True)
    subprocess.run(['java', '-cp', str(folder),
                    'app.morphe.extension.instagram.patches.dm.VisualBatchTest'], check=True)
