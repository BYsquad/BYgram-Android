package android.os;

/** JVM-only process-bitness fixture; never included in Android source sets. */
public final class Process {
    public static boolean bit64 = true;
    public static boolean is64Bit() { return bit64; }
}
