import android.os.Build;
import org.telegram.messenger.plugins.HookBridge;

public final class PluginBackendGateCheck {
    public static void main(String[] args) {
        assert HookBridge.available();
        android.os.Process.bit64 = false;
        assert !HookBridge.available() : "32-bit process";
        android.os.Process.bit64 = true;
        for (int api : new int[]{23, 38}) {
            Build.VERSION.SDK_INT = api;
            assert !HookBridge.available() : api;
        }
        for (int api = 24; api <= 37; api++) { Build.VERSION.SDK_INT = api; assert HookBridge.available() : api; }
        Build.SUPPORTED_ABIS = new String[]{"arm64-v8a"};
        for (int api : new int[]{23, 38}) { Build.VERSION.SDK_INT = api; assert !HookBridge.available() : api; }
        for (int api = 24; api <= 37; api++) { Build.VERSION.SDK_INT = api; assert HookBridge.available() : api; }
        Build.VERSION.SDK_INT = 36; assert HookBridge.available();
        android.os.Process.bit64 = false; assert !HookBridge.available(); android.os.Process.bit64 = true;
        Build.VERSION.SDK_INT = 28;
        for (String abi : new String[]{"armeabi-v7a", "x86", "unknown"}) {
            Build.SUPPORTED_ABIS = new String[]{abi, "x86_64"};
            assert !HookBridge.available() : abi;
        }
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
        System.out.println("PASS: candidate gate x86_64/arm64 API 24..37; 32-bit/other ABI rejected, native availability lazy; device service probe required before enable");
    }
}
