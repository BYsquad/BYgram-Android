package org.telegram.messenger;

import org.json.JSONObject;

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

public final class BygramTypingController {
    private static final String ENDPOINT = "https://byxarek.site/TEST-PROJECT/BYgram/api/";
    private static final long CACHE_MS = 7_000L;
    private static final Map<Long, Entry> cache = new HashMap<>();

    public interface Callback { void run(boolean ok, String botUrl); }
    private interface Result { void run(JSONObject value); }
    private static final class Entry {
        JSONObject statuses;
        long loadedAt;
        boolean loading;
    }

    private BygramTypingController() {}

    public static String status(int account, long userId, int action) {
        if (userId <= 0 || action < 0 || action > 10) return "";
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
                register(userId, callback);
            } else callback.run(false, "");
        });
    }

    private static void register(long userId, Callback callback) {
        try {
            request(userId, json("action", "typing_register", "user_id", userId,
                    "public_key", BygramDeviceKey.publicKey(userId),
                    "proof", BygramDeviceKey.sign(userId, "BYGRAM_TYPING_V1\n" + userId)), false,
                    result -> callback.run(false, result == null ? "" : result.optString("bot_url", "")));
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
