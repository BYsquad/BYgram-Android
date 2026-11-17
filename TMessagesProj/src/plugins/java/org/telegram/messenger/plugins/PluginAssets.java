package org.telegram.messenger.plugins;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.Components.RLottieDrawable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public final class PluginAssets {
    public static RLottieDrawable lottie(PluginContext owner, String path, int width, int height) throws Exception {
        owner.check();
        int w = AndroidUtilities.dp(width), h = AndroidUtilities.dp(height);
        if (w < 1 || h < 1 || w > 2048 || h > 2048) throw new IllegalArgumentException("InvalidAssetSize");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(path))) {
            stream.mark(2);
            boolean gzip = stream.read() == 0x1f && stream.read() == 0x8b;
            stream.reset();
            try (InputStream decoded = gzip ? new GZIPInputStream(stream) : stream) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = decoded.read(buffer)) != -1) {
                    if (bytes.size() + count > PluginPackage.MAX_FILE) throw new IOException("FileLimitExceeded");
                    bytes.write(buffer, 0, count);
                }
            }
        }
        // The Telegram renderer deletes its input on decode failure.
        File scratch = File.createTempFile("plugin-lottie-", ".json", ApplicationLoader.applicationContext.getCacheDir());
        RLottieDrawable drawable;
        try {
            drawable = new RLottieDrawable(scratch, new String(bytes.toByteArray(), StandardCharsets.UTF_8),
                    w, h, null, true, null, 0, false);
        } finally {
            scratch.delete();
        }
        if (drawable.getFramesCount() <= 0) {
            AndroidUtilities.runOnUIThread(() -> drawable.recycle(true));
            throw new IOException("InvalidLottie");
        }
        owner.scope.own(() -> AndroidUtilities.runOnUIThread(() -> drawable.recycle(true)));
        return drawable;
    }
}
