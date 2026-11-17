package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.View;
import org.telegram.messenger.*;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.UUID;

/** Local video uses the same silent, looping decoder as animated chat media. */
public final class BygramVideoWallpaper extends Drawable {
    private static final java.util.Map<String, Object> imports = new java.util.HashMap<>();
    private final ImageReceiver receiver = new ImageReceiver();
    private final AnimatedFileDrawable video;
    private final Drawable fallback;
    public BygramVideoWallpaper(File file, int account, Drawable fallback) {
        this.fallback = fallback;
        video = new AnimatedFileDrawable(file, false, 0, 0, null, null, null, 0, account, true, 720, 1280, null);
        receiver.onAttachedToWindow();
        receiver.setImageBitmap(video);
        video.start();
    }
    public void setParent(View view) { receiver.setParentView(view); }
    public void release() { video.stop(); receiver.onDetachedFromWindow(); receiver.clearImage(); video.recycle(); }
    @Override public void draw(Canvas canvas) {
        if (fallback != null && !video.hasBitmap()) { fallback.setBounds(getBounds()); fallback.draw(canvas); }
        receiver.setImageCoords(getBounds().left, getBounds().top, getBounds().width(), getBounds().height());
        receiver.draw(canvas);
    }
    @Override public void setAlpha(int alpha) { receiver.setAlpha(alpha / 255f); }
    @Override public void setColorFilter(ColorFilter filter) { receiver.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    private static String key(int account, long chat) { return UserConfig.getInstance(account).getClientUserId() + "_" + chat; }
    private static android.content.SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bygram_video_wallpapers", 0);
    }
    public static File file(int account, long chat) {
        return file(key(account, chat));
    }
    private static File file(String key) {
        String path = preferences().getString(key, "");
        if (!path.matches("bygram-wallpaper-[a-fA-F0-9-]+\\.mp4")) return null;
        File file = path.isEmpty() ? null : new File(ApplicationLoader.getFilesDirFixed(), path);
        return file != null && file.isFile() ? file : null;
    }
    public static void clear(int account, long chat) {
        imports.remove(key(account, chat));
        File previous = file(account, chat);
        if (preferences().edit().remove(key(account, chat)).commit() && previous != null) previous.delete();
    }
    public static void importVideo(int account, long chat, Uri uri, java.util.function.Consumer<Boolean> done) {
        String key = key(account, chat);
        Object token = new Object();
        imports.put(key, token);
        Utilities.globalQueue.postRunnable(() -> {
            File destination = new File(ApplicationLoader.getFilesDirFixed(), "bygram-wallpaper-" + UUID.randomUUID() + ".mp4");
            boolean success = false;
            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
            try {
                try (InputStream input = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(destination)) {
                    if (input == null) throw new java.io.IOException("No video");
                    copyVideo(input, output);
                }
                metadata.setDataSource(destination.getAbsolutePath());
                int width = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int height = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                if (Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) <= 0
                        || width <= 0 || height <= 0 || width > 3840 || height > 3840) throw new java.io.IOException("Invalid video");
                android.graphics.Bitmap frame = metadata.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) throw new java.io.IOException("Cannot decode video");
                frame.recycle();
                success = true;
            } catch (Exception error) { FileLog.e(error); }
            finally { try { metadata.release(); } catch (Exception error) { FileLog.e(error); } }
            boolean result = success;
            AndroidUtilities.runOnUIThread(() -> {
                if (imports.get(key) != token) { destination.delete(); return; }
                imports.remove(key);
                File previous = file(key);
                boolean saved = result && preferences().edit().putString(key, destination.getName()).commit();
                if (saved && previous != null) previous.delete();
                if (!saved) destination.delete();
                done.accept(saved);
            });
        });
    }

    private static void copyVideo(InputStream input, java.io.OutputStream output) throws java.io.IOException {
        byte[] buffer = new byte[64 * 1024];
        long size = 0;
        int length;
        while ((length = input.read(buffer)) != -1) {
            size += length;
            if (size > 100 * 1024 * 1024) throw new java.io.IOException("Video exceeds 100 MiB");
            output.write(buffer, 0, length);
        }
    }
}
