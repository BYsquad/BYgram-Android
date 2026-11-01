package org.telegram.messenger;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import android.content.Context;
import android.content.SharedPreferences;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.BitmapFactory;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.os.Build;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.LaunchActivity;

public final class BygramTypingController {
    private static final String ENDPOINT = "https://byxarek.site/TEST-PROJECT/BYgram/api/";
    private static final long CACHE_MS = 7_000L;
    private static final Map<Long, Entry> cache = new HashMap<>();
    private static final java.util.HashSet<Integer> pollingProfileViews = new java.util.HashSet<>();

    public interface Callback { void run(boolean ok, String botUrl); }
    public interface ProfileViewsCallback { void run(JSONObject response, String botUrl); }
    private interface Result { void run(JSONObject value); }
    private static final class Entry {
        JSONObject statuses;
        long loadedAt;
        boolean loading;
    }

    private BygramTypingController() {}

    public static void loadProfileBackground(long ownerId, ProfileViewsCallback callback) {
        if (!BuildConfig.BYGRAM_FULL) { callback.run(null, ""); return; }
        request(0, json("action", "profile_background_get", "owner_id", ownerId), false,
                response -> callback.run(response, ""));
    }

    public static String profileBackgroundUrl(long ownerId, JSONObject response) {
        String revision = response == null ? "" : response.optString("revision");
        return revision.matches("[a-f0-9]{64}") ? ENDPOINT + "profile-background.php?user_id=" + ownerId + "&v=" + revision : "";
    }

    public static void publishProfileBackground(int account, android.net.Uri uri, Callback callback) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId <= 0) { callback.run(false, ""); return; }
        Utilities.globalQueue.postRunnable(() -> {
            String encoded = "";
            try {
                if (uri != null) {
                    try (InputStream input = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                         java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                        if (input == null) throw new java.io.IOException("No image");
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = input.read(buffer)) != -1) {
                            if (output.size() + length > 4 * 1024 * 1024) throw new java.io.IOException("Image exceeds 4 MiB");
                            output.write(buffer, 0, length);
                        }
                        encoded = android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP);
                    }
                }
                request(userId, json("action", "profile_background_put", "user_id", userId, "image", encoded), true, response -> {
                    if (response != null && response.optBoolean("needs_registration")) register(userId, (registered, botUrl) -> {
                        if (registered) publishProfileBackground(account, uri, callback);
                        else callback.run(false, botUrl);
                    });
                    else callback.run(response != null && response.optBoolean("ok"), "");
                });
            } catch (Exception error) {
                FileLog.e(error);
                AndroidUtilities.runOnUIThread(() -> callback.run(false, ""));
            }
        });
    }

    public static boolean profileViewsEnabled(int account) {
        if (!BuildConfig.BYGRAM_FULL) return false;
        long id = UserConfig.getInstance(account).getClientUserId();
        return id > 0 && ApplicationLoader.applicationContext.getSharedPreferences("bygram_profile_views", Context.MODE_PRIVATE).getBoolean("enabled_" + id, false);
    }

    public static void profileViewsRequest(int account, JSONObject data, ProfileViewsCallback callback) {
        long id = UserConfig.getInstance(account).getClientUserId();
        if (id <= 0) { callback.run(null, ""); return; }
        try { data.put("user_id", id); } catch (Exception e) { FileLog.e(e); callback.run(null, ""); return; }
        request(id, data, true, response -> {
            if (response != null && response.optBoolean("needs_registration") && "profile_views_set".equals(data.optString("action"))) {
                register(id, (registered, botUrl) -> {
                    if (registered) profileViewsRequest(account, data, callback);
                    else callback.run(response, botUrl);
                });
                return;
            }
            if (response != null && response.optBoolean("ok") && response.has("enabled")) {
                SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("bygram_profile_views", Context.MODE_PRIVATE);
                boolean enabled = response.optBoolean("enabled");
                if (prefs.getBoolean("enabled_" + id, false) != enabled) {
                    prefs.edit().putBoolean("enabled_" + id, enabled).apply();
                    scheduleProfileViewsJob();
                }
            }
            callback.run(response, "");
        });
    }

    public static void scheduleProfileViewsJob() {
        Context context = ApplicationLoader.applicationContext;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        boolean enabled = false;
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) enabled |= profileViewsEnabled(account);
        if (!enabled) { scheduler.cancel(280928); return; }
        scheduler.schedule(new JobInfo.Builder(280928, new ComponentName(context, BygramProfileViewsJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15 * 60 * 1000L).setPersisted(true).build());
    }

    public static void clearProfileViews(int account) {
        if (profileViewsEnabled(account)) profileViewsRequest(account, json("action", "profile_views_set", "enabled", false), (response, botUrl) -> {});
    }

    public static void pollProfileViews(int account) {
        pollProfileViews(account, null);
    }

    public static void pollProfileViews(int account, Runnable done) {
        if (!profileViewsEnabled(account)) { if (done != null) done.run(); return; }
        if (!androidx.core.app.NotificationManagerCompat.from(ApplicationLoader.applicationContext).areNotificationsEnabled()) {
            if (done != null) done.run();
            return;
        }
        if (!pollingProfileViews.add(account)) { if (done != null) done.run(); return; }
        JSONObject data = json("action", "profile_views_poll");
        profileViewsRequest(account, data, (response, botUrl) -> {
            org.json.JSONArray views = response == null || !response.optBoolean("ok") ? null : response.optJSONArray("views");
            if (views == null || views.length() == 0) {
                pollingProfileViews.remove(account);
                if (done != null) done.run();
                return;
            }
            java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(views.length());
            Runnable finished = () -> {
                if (remaining.decrementAndGet() == 0) {
                    pollingProfileViews.remove(account);
                    if (done != null) done.run();
                }
            };
            for (int i = 0; i < views.length(); i++) {
                JSONObject value = views.optJSONObject(i);
                if (value == null) { finished.run(); continue; }
                long viewerId = value.optLong("viewer_id");
                String name = value.optString("viewer_name", "");
                String avatarUrl = value.optString("avatar_url", "");
                TLRPC.User user = MessagesController.getInstance(account).getUser(viewerId);
                AvatarDrawable avatar = new AvatarDrawable();
                if (user == null) avatar.setInfo(viewerId, name, ""); else avatar.setInfo(user);
                Bitmap bitmap = null;
                if (user != null && user.photo != null && user.photo.photo_small != null) {
                    bitmap = BitmapFactory.decodeFile(FileLoader.getInstance(account).getPathToAttach(user.photo.photo_small, true).toString());
                }
                if (bitmap != null || !avatarUrl.startsWith("https://byxarek.site/TEST-PROJECT/BYgram/api/avatar.php?")) {
                    if (bitmap == null) bitmap = avatarBitmap(avatar);
                    try { notifyProfileView(account, viewerId, name, bitmap); }
                    finally { finished.run(); }
                    continue;
                }
                Bitmap fallback = avatarBitmap(avatar);
                // ponytail: avatar downloads share the global queue; use a bounded pool if large visitor lists become common.
                Utilities.globalQueue.postRunnable(() -> {
                    Bitmap downloaded = null;
                    HttpURLConnection connection = null;
                    try {
                        connection = (HttpURLConnection) new URL(avatarUrl).openConnection();
                        connection.setConnectTimeout(4000); connection.setReadTimeout(4000);
                        if (connection.getResponseCode() == 200) {
                            try (InputStream stream = connection.getInputStream()) { downloaded = BitmapFactory.decodeStream(stream); }
                        }
                    } catch (Exception error) { FileLog.e(error); }
                    finally { if (connection != null) connection.disconnect(); }
                    Bitmap image = downloaded == null ? fallback : downloaded;
                    AndroidUtilities.runOnUIThread(() -> {
                        try { notifyProfileView(account, viewerId, name, image); }
                        finally { finished.run(); }
                    });
                });
            }
        });
    }

    private static Bitmap avatarBitmap(AvatarDrawable avatar) {
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); avatar.setBounds(0, 0, 96, 96); avatar.draw(canvas);
        return bitmap;
    }

    private static void notifyProfileView(int account, long viewerId, String name, Bitmap bitmap) {
        Context context = ApplicationLoader.applicationContext;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        String channel = "bygram_profile_views";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel,
                LocaleController.getString(R.string.BygramProfileViews), NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent = new Intent(context, LaunchActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.putExtra("currentAccount", account);
        intent.putExtra("bygram_profile_views", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 31 * account + (int) (viewerId ^ (viewerId >>> 32)), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification notification = new androidx.core.app.NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.notification).setLargeIcon(bitmap)
                .setContentTitle(LocaleController.getString(R.string.BygramProfileViews))
                .setContentText(LocaleController.formatString(R.string.BygramProfileViewsNotification, name))
                .setContentIntent(pending).setAutoCancel(true).build();
        try { manager.notify("bygram_profile_views_" + account, (int) (viewerId ^ (viewerId >>> 32)), notification); }
        catch (SecurityException error) { FileLog.e(error); }
    }

    public static String status(int account, long userId, int action) {
        if (!BuildConfig.BYGRAM_FULL || userId <= 0 || action < 0 || action > 10) return "";
        Entry entry = cache.get(userId);
        if (entry == null) cache.put(userId, entry = new Entry());
        if (System.currentTimeMillis() - entry.loadedAt > CACHE_MS && !entry.loading) {
            entry.loading = true;
            Entry target = entry;
            request(userId, json("action", "typing_get", "owner_id", userId), false, result -> {
                target.loading = false;
                if (result != null && result.optBoolean("ok")) {
                    target.statuses = result.optJSONObject("statuses");
                    target.loadedAt = System.currentTimeMillis();
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces,
                            MessagesController.UPDATE_MASK_USER_PRINT);
                }
            });
        }
        return entry.statuses == null ? "" : entry.statuses.optString(Integer.toString(action), "");
    }

    public static void publish(int account, Callback callback) {
        publish(account, true, callback);
    }

    public static void clear(int account) {
        publish(account, false, (ok, botUrl) -> {});
    }

    private static void publish(int account, boolean registerIfNeeded, Callback callback) {
        if (!BuildConfig.BYGRAM_FULL) { callback.run(false, ""); return; }
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId <= 0) { callback.run(false, ""); return; }
        JSONObject statuses = new JSONObject();
        try {
            for (int i = 0; i <= 10; i++) {
                String value = BygramConfig.getCustomTyping(account, i);
                if (!value.isEmpty()) statuses.put(Integer.toString(i), value);
            }
        } catch (Exception e) { FileLog.e(e); }
        request(userId, json("action", "typing_put", "user_id", userId, "statuses", statuses), true, result -> {
            if (result != null && result.optBoolean("ok")) {
                Entry entry = new Entry(); entry.statuses = statuses; entry.loadedAt = System.currentTimeMillis();
                cache.put(userId, entry);
                callback.run(true, "");
            } else if (registerIfNeeded && result != null && result.optBoolean("needs_registration")) {
                register(userId, (registered, botUrl) -> {
                    if (registered) publish(account, false, callback);
                    else callback.run(false, botUrl);
                });
            } else callback.run(false, "");
        });
    }

    private static void register(long userId, Callback callback) {
        try {
            request(userId, json("action", "typing_register", "user_id", userId,
                    "public_key", BygramDeviceKey.publicKey(userId),
                    "proof", BygramDeviceKey.sign(userId, "BYGRAM_TYPING_V1\n" + userId)), false,
                    result -> callback.run(result != null && result.optBoolean("registered"), result == null ? "" : result.optString("bot_url", "")));
        } catch (Exception e) { FileLog.e(e); callback.run(false, ""); }
    }

    private static JSONObject json(Object... pairs) {
        JSONObject result = new JSONObject();
        try { for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]); }
        catch (Exception e) { FileLog.e(e); }
        return result;
    }

    private static void request(long userId, JSONObject data, boolean signed, Result callback) {
        Utilities.globalQueue.postRunnable(() -> {
            JSONObject result = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ENDPOINT);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(7000); connection.setReadTimeout(7000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] body = data.toString().getBytes(StandardCharsets.UTF_8);
                if (signed) {
                    String timestamp = Long.toString(System.currentTimeMillis() / 1000);
                    String nonce = UUID.randomUUID().toString();
                    byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
                    StringBuilder hash = new StringBuilder();
                    for (byte part : digest) hash.append(String.format(java.util.Locale.US, "%02x", part & 0xff));
                    String canonical = "POST\n" + url.getPath() + "\n" + timestamp + "\n" + nonce + "\n" + hash;
                    connection.setRequestProperty("X-BYgram-Timestamp", timestamp);
                    connection.setRequestProperty("X-BYgram-Nonce", nonce);
                    connection.setRequestProperty("X-BYgram-Signature", BygramDeviceKey.sign(userId, canonical));
                }
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
                InputStream stream = connection.getResponseCode() < 400 ? connection.getInputStream() : connection.getErrorStream();
                if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder(); String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    if (response.length() > 0) result = new JSONObject(response.toString());
                }
            } catch (Exception e) { FileLog.e(e); }
            finally { if (connection != null) connection.disconnect(); }
            JSONObject response = result;
            AndroidUtilities.runOnUIThread(() -> callback.run(response));
        });
    }
}
