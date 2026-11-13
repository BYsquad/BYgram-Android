package org.telegram.messenger.plugins;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** ADB entry point. The authorization token is provisioned through run-as. */
public final class PluginDevActivity extends Activity {
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!BuildConfig.DEBUG_VERSION || !BuildConfig.BYGRAM_FULL) { finish(); return; }
        if ("org.telegram.bygram.DEV_STOP".equals(getIntent().getAction())) {
            PluginDevServer.stop();
            finish();
            return;
        }
        ApplicationLoader.postInitApplication();
        TextView status = new TextView(this);
        status.setText("Starting BYgram plugin development server…");
        setContentView(status);
        new Thread(() -> {
            String message;
            File file = new File(getFilesDir(), "plugin-dev-token");
            try {
                if (file.length() != 64) throw new IOException("DeveloperTokenRequired");
                String token = new String(PluginPackage.read(file), StandardCharsets.US_ASCII);
                if (!file.delete() || !token.matches("[a-f0-9]{64}")) throw new IOException("InvalidDeveloperToken");
                PluginDevServer.start(token);
                message = "BYgram plugin development server is listening on 127.0.0.1:42690";
            } catch (IOException failure) {
                message = String.valueOf(failure.getMessage());
            }
            String result = message;
            runOnUiThread(() -> status.setText(result));
        }, "BYgramDevStart").start();
    }
}
