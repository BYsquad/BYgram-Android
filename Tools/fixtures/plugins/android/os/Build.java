package android.os;

/** JVM-only fixture for testing the compiled backend gate without loading Android/native code. */
public final class Build {
    public static String[] SUPPORTED_ABIS = {"x86_64"};
    public static final class VERSION { public static int SDK_INT = 28; }
}
