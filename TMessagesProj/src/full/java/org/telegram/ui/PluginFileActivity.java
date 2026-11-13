package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.PluginManager;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;

public final class PluginFileActivity extends Activity {
    public static final String MIME_TYPE = "application/vnd.bygram.plugin";
    private final PluginManager manager = PluginManager.get();
    private AutoCloseable request;
    private String token;
    private BottomSheet sheet;
    private AlertDialog progress;

    public static boolean supports(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".byplugin") || lower.endsWith(".plugin") || lower.endsWith(".elyx")
                || lower.endsWith(".eaf") || lower.endsWith(".elyx.zip") || lower.endsWith(".eaf.zip");
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent intent = getIntent();
        Uri uri = intent.getData();
        boolean internal = intent.getComponent() != null && getClass().getName().equals(intent.getComponent().getClassName());
        if (!Intent.ACTION_VIEW.equals(intent.getAction()) || uri == null || !"content".equals(uri.getScheme())
                || (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0
                || !(MIME_TYPE.equals(intent.getType()) || supports(uri.getPath()))) {
            finish();
            return;
        }
        if (!internal && checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            finish();
            return;
        }

        ApplicationLoader.postInitApplication();
        progress = new AlertDialog(this, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setOnCancelListener(dialog -> finish());
        progress.show();
        request = manager.stage(uri, (staged, error) -> {
            request = null;
            if (isFinishing() || isDestroyed()) {
                if (staged != null) manager.commit(staged, false, null);
                return;
            }
            progress.dismiss();
            progress = null;
            if (error != null) { showFailure(error); return; }
            token = staged;
            manager.preview(staged, (manifest, failure) -> {
                if (isFinishing() || isDestroyed()) return;
                if (failure != null) { showFailure(failure); return; }
                sheet = PluginPackageSheet.show(this, manifest, staged, accept -> {
                    if (!staged.equals(token)) return;
                    token = null;
                    manager.commit(staged, accept, accept ? (value, installError) -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (installError != null) { showFailure(installError); return; }
                        Toast.makeText(this, LocaleController.getString(R.string.BygramPluginInstalledDisabled), Toast.LENGTH_SHORT).show();
                        finish();
                    } : null);
                    if (!accept) finish();
                });
            });
        });
    }

    private void showFailure(String error) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(LocaleController.getString(R.string.BygramPlugins))
                .setMessage(BygramPluginsActivity.errorText(error))
                .setPositiveButton(LocaleController.getString(R.string.OK), null).create();
        dialog.setOnDismissListener(dismissed -> finish());
        dialog.show();
    }

    @Override protected void onDestroy() {
        if (request != null) {
            try { request.close(); } catch (Exception ignored) { }
        }
        if (token != null) { manager.commit(token, false, null); token = null; }
        if (sheet != null) sheet.dismiss();
        if (progress != null) progress.dismiss();
        super.onDestroy();
    }
}
