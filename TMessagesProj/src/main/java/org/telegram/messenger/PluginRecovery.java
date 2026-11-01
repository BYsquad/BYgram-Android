package org.telegram.messenger;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.util.AtomicFile;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.*;

/** Independent recovery process: no Telegram UI, Python, plugin assets or registry writes. */
public final class PluginRecovery extends Activity {
    public static boolean isRecoveryProcess(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= 28) return independentProcess(android.app.Application.getProcessName());
        int pid = android.os.Process.myPid();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        java.util.List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes)
            if (process.pid == pid) return independentProcess(process.processName);
        return false;
    }
    private static boolean independentProcess(String name) { return name.endsWith(":plugin_recovery") || name.endsWith(":plugin_probe"); }

    private static AtomicFile flag(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), "plugin-safe-mode"));
    }
    public static boolean isSafe(Context context) {
        AtomicFile flag = flag(context);
        return flag.getBaseFile().exists() || new File(flag.getBaseFile() + ".bak").exists()
                || new File(flag.getBaseFile() + ".new").exists();
    }
    public static void setSafe(Context context, boolean safe) throws IOException {
        AtomicFile flag = flag(context);
        if (!safe) { flag.delete(); return; }
        FileOutputStream out = null;
        try { out = flag.startWrite(); out.write(1); flag.finishWrite(out); }
        catch (IOException e) { if (out != null) flag.failWrite(out); throw e; }
    }
    private String recoveryString(String name) {
        return getResources().getString(getResources().getIdentifier(name, "string", getPackageName()));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!BuildConfig.BYGRAM_FULL) { finish(); return; }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        TextView text = new TextView(this);
        text.setText(recoveryString("BygramPluginRecoveryHelp"));
        try {
            AtomicFile status = new AtomicFile(new File(getNoBackupFilesDir(), "plugins/last-stage.json"));
            try (InputStream in = status.openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[512]; int count;
                while ((count = in.read(bytes)) != -1 && out.size() + count <= 4096) out.write(bytes, 0, count);
                org.json.JSONObject value = new org.json.JSONObject(out.toString("UTF-8"));
                text.append("\n\n" + value.optString("plugin_id") + " · " + value.optString("version") + "\n" + value.optString("stage"));
            }
        } catch (Exception ignored) { }
        text.setTextSize(18);
        layout.addView(text);
        Button button = new Button(this);
        button.setText(recoveryString("BygramPluginSafeMode"));
        button.setOnClickListener(view -> {
            try { setSafe(this, true); text.setText(recoveryString("BygramPluginRecoveryDone")); }
            catch (IOException error) { text.setText(recoveryString("ErrorOccurred")); }
        });
        layout.addView(button);
        setContentView(layout);
    }
}
