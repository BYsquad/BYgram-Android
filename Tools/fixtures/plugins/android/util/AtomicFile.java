package android.util;

import java.io.*;
import java.nio.file.*;

/** JVM file-system stand-in; real Android AtomicFile is also exercised by the DEX fixture. */
public final class AtomicFile {
    private final File base;
    public static String failWriteName;
    public static int dieAfterWrites = -1;
    public static boolean dieAfterCommit;
    public static final class ProcessDeath extends Error { }
    public AtomicFile(File base) { this.base = base; }
    public File getBaseFile() { return base; }
    public FileInputStream openRead() throws IOException {
        File backup = new File(base + ".bak");
        if (backup.exists()) Files.move(backup.toPath(), base.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return new FileInputStream(base);
    }
    public FileOutputStream startWrite() throws IOException {
        if (base.getName().equals(failWriteName)) { failWriteName = null; throw new IOException("Injected write failure"); }
        Files.createDirectories(base.getParentFile().toPath());
        return new FileOutputStream(base + ".new");
    }
    public void finishWrite(FileOutputStream out) throws IOException {
        out.getFD().sync(); out.close();
        boolean die = dieAfterWrites > 0 && --dieAfterWrites == 0;
        if (die && !dieAfterCommit) throw new ProcessDeath();
        Files.move(new File(base + ".new").toPath(), base.toPath(), StandardCopyOption.REPLACE_EXISTING);
        if (die) throw new ProcessDeath();
    }
    public void failWrite(FileOutputStream out) throws IOException { out.close(); Files.deleteIfExists(new File(base + ".new").toPath()); }
    public void delete() { base.delete(); new File(base + ".new").delete(); new File(base + ".bak").delete(); }
}
