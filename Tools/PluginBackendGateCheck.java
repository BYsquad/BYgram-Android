import android.os.Build;
import org.telegram.messenger.plugins.HookBridge;

public final class PluginBackendGateCheck {
    public static void main(String[] args) {
        for (String abi : new String[]{"x86_64", "arm64-v8a", "x86", "armeabi-v7a"}) {
            Build.SUPPORTED_ABIS = new String[]{"unknown", abi};
            android.os.Process.bit64 = abi.contains("64");
            Build.VERSION.SDK_INT = 23;
            assert !HookBridge.available() : abi;
            for (int api = 24; api <= 38; api++) {
                Build.VERSION.SDK_INT = api;
                assert HookBridge.available() : abi + "/" + api;
            }
            android.os.Process.bit64 = !android.os.Process.bit64;
            assert !HookBridge.available() : abi;
        }
        android.os.Process.bit64 = true;
        Build.SUPPORTED_ABIS = new String[0];
        assert !HookBridge.available();
        assert de.robv.android.xposed.XposedBridge.initializations == 0 : "availability initialized native backend";
        Build.SUPPORTED_ABIS = new String[]{"x86_64"};
        for (int attempt = 0; attempt < 2; attempt++) {
            try { HookBridge.verify(); throw new AssertionError("unavailable library accepted"); }
            catch (IllegalStateException expected) { assert expected.getMessage().equals("HookBackendUnavailable"); }
            catch (Exception unexpected) { throw new AssertionError(unexpected); }
        }
        assert de.robv.android.xposed.XposedBridge.initializations == 1 : "failed native init retried";
        System.out.println("PASS: four packaged ABI candidates from API 24; wrong process bitness rejected, native availability lazy; device service probe required before enable");
    }
}
