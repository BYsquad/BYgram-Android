package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
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
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public final class BygramActivityController {
    private static final String ENDPOINT = "https://byxarek.site/TEST-PROJECT/BYgram/api/";
    private static final long POSITIVE_REFRESH_MS = 60 * 60 * 1000L;
    private static final long NEGATIVE_REFRESH_MS = 60 * 1000L;
    private static final long LICENSE_GRACE_MS = 24 * 60 * 60 * 1000L;
    private static final long ACTIVITY_CACHE_MS = 7_000L;
    private static final long ACTIVITY_HEARTBEAT_MS = 7_000L;

    public interface Callback { void run(String activity); }
    public interface LicenseCallback { void run(Boolean licensed, String botUrl); }
    private interface JsonCallback { void run(JSONObject response); }
    public interface PluginCatalogCallback { void run(JSONObject response); }
    public static void pluginCatalogRequest(int account, String repository, PluginCatalogCallback callback) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId == 0) { callback.run(null); return; }
        refreshLicense(account, true, (licensed, url) -> {
            if (!Boolean.TRUE.equals(licensed) || UserConfig.getInstance(account).getClientUserId() != userId) { callback.run(null); return; }
            request(userId, repository == null ? json("action", "plugin_submissions") : json("action", "plugin_submit", "repository", repository),
                    true, response -> { if (UserConfig.getInstance(account).getClientUserId() == userId) callback.run(response); else callback.run(null); });
        });
    }
    private static final int PEER_USER = 1;
    private static final int PEER_BOT = 2;
    private static final int PEER_GROUP = 3;
    private static final int PEER_CHANNEL = 4;
    public static final class ActivityInfo {
        public final int type;
        public final long peerId;
        public final String peerName;
        public final int peerKind;

        ActivityInfo(int type, long peerId, String peerName, int peerKind) {
            this.type = type;
            this.peerId = peerId;
            this.peerName = peerName;
            this.peerKind = peerKind;
        }

        public String text() {
            if (type == BygramConfig.ACTIVITY_DIALOGS) return LocaleController.getString(R.string.BygramActivityDialogs);
            if (type == BygramConfig.ACTIVITY_CHAT && peerKind == PEER_BOT) return LocaleController.formatString(R.string.BygramActivityBot, peerName);
            if (type == BygramConfig.ACTIVITY_CHAT && peerKind == PEER_GROUP) return LocaleController.formatString(R.string.BygramActivityGroup, peerName);
            if (peerKind == PEER_CHANNEL) return LocaleController.formatString(R.string.BygramActivityChannel, peerName);
            if (type == BygramConfig.ACTIVITY_PROFILE) return LocaleController.formatString(R.string.BygramActivityProfile, peerName);
            return LocaleController.formatString(R.string.BygramViewingChat, peerName);
        }
    }
    private static final class OwnActivity {
        final int type;
        final long peerId;
        final String peerName;
        final int peerKind;
        OwnActivity(int type, long peerId, String peerName, int peerKind) { this.type = type; this.peerId = peerId; this.peerName = peerName; this.peerKind = peerKind; }
    }
    private static final class CachedActivity {
        final ActivityInfo info;
        final long loadedAt;
        CachedActivity(ActivityInfo info) { this.info = info; this.loadedAt = System.currentTimeMillis(); }
    }
    private static final OwnActivity[] ownActivities = new OwnActivity[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Map<String, CachedActivity> activityCache = new HashMap<>();
    private static final Map<Integer, Set<Long>> pendingActivities = new HashMap<>();
    private static final Map<String, ArrayList<Runnable>> activityCallbacks = new HashMap<>();
    private static final Set<Long> viewersSyncing = new HashSet<>();
    private static boolean activityFlushScheduled;
    private static boolean heartbeatScheduled;
    private static final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            heartbeatScheduled = false;
            boolean active = false;
            for (int account = 0; account < ownActivities.length; account++) {
                if (ownActivities[account] != null) { sendActivity(account); active = true; }
            }
            if (active) scheduleHeartbeat();
        }
    };
    private BygramActivityController() {}

    public static String purchaseUrl(String botUrl) {
        if (botUrl != null) {
            try {
                Uri uri = Uri.parse(botUrl);
                String host = uri.getHost();
                if ("https".equals(uri.getScheme()) && ("t.me".equalsIgnoreCase(host) || "byxarek.site".equalsIgnoreCase(host))) {
                    return botUrl;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return "";
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bygram_licenses", Context.MODE_PRIVATE);
    }

    private static String key(String name, long userId) { return name + '_' + userId; }

    public static boolean isLicensed(int account) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        return userId > 0 && preferences().getBoolean(key("licensed", userId), false)
                && System.currentTimeMillis() - preferences().getLong(key("checked", userId), 0) < LICENSE_GRACE_MS;
    }

    public static void refreshLicenses(boolean force) {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).getClientUserId() > 0
                    && (BuildConfig.BYGRAM_FULL || BygramConfig.isActivityEnabled(account))) refreshLicense(account, force, null);
        }
    }

    public static void refreshLicense(int account, boolean force, LicenseCallback callback) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId <= 0) { if (callback != null) callback.run(false, ""); return; }
        SharedPreferences prefs = preferences();
        String access = prefs.getString(key("access", userId), "");
        if (access.isEmpty()) {
            if (prefs.getString(key("refresh", userId), "").isEmpty()) authenticate(account, callback);
            else refreshAccess(account, callback);
            return;
        }
        if (prefs.getLong(key("access_expires", userId), 0) <= System.currentTimeMillis() / 1000 + 30) {
            refreshAccess(account, callback);
            return;
        }
        boolean cached = prefs.getBoolean(key("licensed", userId), false);
        long now = System.currentTimeMillis();
        long age = now - prefs.getLong(key("checked", userId), 0);
        long attemptAge = now - prefs.getLong(key("attempted", userId), 0);
        if (!force && (attemptAge < NEGATIVE_REFRESH_MS || age < (cached ? POSITIVE_REFRESH_MS : NEGATIVE_REFRESH_MS))) {
            if (callback != null) callback.run(cached, prefs.getString("manager_url", ""));
            return;
        }
        prefs.edit().putLong(key("attempted", userId), now).apply();
        request(userId, json("action", "license"), true, response -> {
            if (response == null || !response.optBoolean("ok")) { if (callback != null) callback.run(null, prefs.getString("manager_url", "")); return; }
            boolean licensed = response.optBoolean("licensed");
            String managerUrl = response.optString("manager_url", prefs.getString("manager_url", ""));
            prefs.edit().putBoolean(key("licensed", userId), licensed).putLong(key("checked", userId), System.currentTimeMillis()).putString("manager_url", managerUrl).apply();
            if (callback != null) callback.run(licensed, managerUrl);
        });
    }

    private static void authenticate(int account, LicenseCallback callback) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        SharedPreferences prefs = preferences();
        String activation = prefs.getString(key("activation", userId), "");
        if (!activation.isEmpty()) {
            try {
                request(userId, json("action", "device_status", "activation", activation,
                        "signature", BygramDeviceKey.sign(userId, "BYGRAM_STATUS_V1\n" + activation)), false,
                        response -> handleActivation(account, response, callback, true));
            } catch (Exception e) {
                FileLog.e(e);
                clearActivation(userId);
                if (callback != null) callback.run(null, "");
            }
            return;
        }
        request(userId, json("action", "device_start", "user_id", userId), false, response -> {
            if (response == null || !response.optBoolean("ok")) { if (callback != null) callback.run(null, ""); return; }
            if ("denied".equals(response.optString("state"))) { markDenied(userId, response.optString("bot_url", ""), callback); return; }
            String nextActivation = response.optString("activation", "");
            String challenge = response.optString("challenge", "");
            if (nextActivation.isEmpty() || challenge.isEmpty()) { if (callback != null) callback.run(null, ""); return; }
            prefs.edit().putString(key("activation", userId), nextActivation)
                    .putString(key("activation_challenge", userId), challenge)
                    .putString("manager_url", response.optString("bot_url", "")).apply();
            proveDevice(account, callback);
        });
    }

    private static void proveDevice(int account, LicenseCallback callback) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        SharedPreferences prefs = preferences();
        String activation = prefs.getString(key("activation", userId), "");
        String challenge = prefs.getString(key("activation_challenge", userId), "");
        try {
            String proof = BygramDeviceKey.sign(userId, "BYGRAM_DEVICE_V1\n" + activation + "\n" + challenge + "\n" + userId);
            String name = (Build.MANUFACTURER + " " + Build.MODEL).trim();
            request(userId, json("action", "device_prove", "activation", activation,
                    "public_key", BygramDeviceKey.publicKey(userId), "signature", proof,
                    "device_name", name.isEmpty() ? "Android" : name), false,
                    response -> handleActivation(account, response, callback, false));
        } catch (Exception e) {
            FileLog.e(e);
            clearActivation(userId);
            BygramDeviceKey.delete(userId);
            if (callback != null) callback.run(null, "");
        }
    }

    private static void handleActivation(int account, JSONObject response, LicenseCallback callback, boolean retryExpired) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (response == null) { if (callback != null) callback.run(null, ""); return; }
        if (!response.optBoolean("ok")) {
            if (retryExpired) { clearActivation(userId); authenticate(account, callback); }
            else if (callback != null) callback.run(null, "");
            return;
        }
        String state = response.optString("state");
        if ("denied".equals(state)) { markDenied(userId, response.optString("bot_url", ""), callback); return; }
        if (!response.optString("access_token", "").isEmpty()) {
            saveTokens(userId, response);
            clearActivation(userId);
            preferences().edit().putBoolean(key("licensed", userId), true)
                    .putLong(key("checked", userId), System.currentTimeMillis()).apply();
            if (callback != null) callback.run(true, "");
            return;
        }
        String botUrl = response.optString("bot_url", preferences().getString("manager_url", ""));
        preferences().edit().putString(key("devices", userId), response.optJSONArray("devices") == null
                ? "" : response.optJSONArray("devices").toString()).putString("manager_url", botUrl).apply();
        if (callback != null) callback.run(false, botUrl);
    }

    private static void markDenied(long userId, String botUrl, LicenseCallback callback) {
        clearActivation(userId);
        botUrl = purchaseUrl(botUrl);
        preferences().edit().putBoolean(key("licensed", userId), false)
                .putLong(key("checked", userId), System.currentTimeMillis()).putString("manager_url", botUrl).apply();
        if (callback != null) callback.run(false, botUrl);
    }

    private static void clearActivation(long userId) {
        preferences().edit().remove(key("activation", userId)).remove(key("activation_challenge", userId))
                .remove(key("devices", userId)).apply();
    }

    public static JSONArray getPendingDevices(int account) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        try { return new JSONArray(preferences().getString(key("devices", userId), "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static void replaceDevice(int account, String deviceId, LicenseCallback callback) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        String activation = preferences().getString(key("activation", userId), "");
        if (activation.isEmpty()) { if (callback != null) callback.run(false, ""); return; }
        try {
            request(userId, json("action", "device_replace", "activation", activation, "device_id", deviceId,
                    "signature", BygramDeviceKey.sign(userId, "BYGRAM_REPLACE_V1\n" + activation + "\n" + deviceId)), false,
                    response -> handleActivation(account, response, callback, false));
        } catch (Exception e) {
            FileLog.e(e);
            if (callback != null) callback.run(null, "");
        }
    }

    private static void refreshAccess(int account, LicenseCallback callback) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        String refresh = preferences().getString(key("refresh", userId), "");
        if (refresh.isEmpty()) { preferences().edit().remove(key("access", userId)).apply(); authenticate(account, callback); return; }
        request(userId, json("action", "refresh", "refresh_token", refresh), false, response -> {
            if (response == null || response.optInt("_http_status") >= 500 || response.optInt("_http_status") == 429) {
                if (callback != null) callback.run(null, "");
            } else if (!response.optBoolean("ok")) {
                if (response.optInt("_http_status") != 401 && response.optInt("_http_status") != 403) {
                    if (callback != null) callback.run(null, "");
                    return;
                }
                preferences().edit().remove(key("access", userId)).remove(key("refresh", userId)).apply();
                authenticate(account, callback);
            } else {
                saveTokens(userId, response);
                refreshLicense(account, true, callback);
            }
        });
    }

    private static void saveTokens(long userId, JSONObject response) {
        SharedPreferences.Editor editor = preferences().edit().putString(key("access", userId), response.optString("access_token", ""))
                .putLong(key("access_expires", userId), response.optLong("access_expires", 0));
        String refresh = response.optString("refresh_token", "");
        if (!refresh.isEmpty()) editor.putString(key("refresh", userId), refresh);
        editor.apply();
    }

    private static boolean hasAccess(int account, Runnable retry) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (!preferences().getString(key("access", userId), "").isEmpty()
                && preferences().getLong(key("access_expires", userId), 0) > System.currentTimeMillis() / 1000 + 30) return true;
        refreshAccess(account, (licensed, botUrl) -> { if (Boolean.TRUE.equals(licensed)) retry.run(); });
        return false;
    }

    public static void setCurrentActivity(int account, int type, long dialogId, TLRPC.User user, TLRPC.Chat chat) {
        long selfId = UserConfig.getInstance(account).getClientUserId();
        if (!isLicensed(account) || !BygramConfig.isActivityEnabled(account)
                || (BuildConfig.BYGRAM_FULL && BygramConfig.getActivityViewers(account).isEmpty())) return;
        if (type != BygramConfig.ACTIVITY_DIALOGS && type != BygramConfig.ACTIVITY_PROFILE && type != BygramConfig.ACTIVITY_CHAT) return;
        if (type != BygramConfig.ACTIVITY_DIALOGS && (dialogId == 0 || dialogId == selfId)) return;
        String username = user != null ? UserObject.getPublicUsername(user) : chat != null ? ChatObject.getPublicUsername(chat) : null;
        String title = username != null && !username.isEmpty() ? "@" + username
                : user != null ? UserObject.getUserName(user) : chat != null ? chat.title : "";
        if (type != BygramConfig.ACTIVITY_DIALOGS && title.isEmpty()) return;
        int peerKind = user != null ? (user.bot ? PEER_BOT : PEER_USER)
                : chat != null ? (ChatObject.isChannelAndNotMegaGroup(chat) ? PEER_CHANNEL : PEER_GROUP) : 0;
        ownActivities[account] = new OwnActivity(type, dialogId, title, peerKind);
        sendActivity(account);
        scheduleHeartbeat();
    }

    private static void scheduleHeartbeat() {
        if (heartbeatScheduled) return;
        heartbeatScheduled = true;
        AndroidUtilities.runOnUIThread(heartbeat, ACTIVITY_HEARTBEAT_MS);
    }

    private static void sendActivity(int account) {
        OwnActivity activity = ownActivities[account];
        long selfId = UserConfig.getInstance(account).getClientUserId();
        if (activity == null) return;
        if (!isLicensed(account) || !BygramConfig.isActivityEnabled(account)) { ownActivities[account] = null; return; }
        if (viewersDirty(selfId)) { syncViewers(account); return; }
        if (!hasAccess(account, () -> sendActivity(account))) return;
        request(selfId, json("action", "update", "activity_type", activity.type,
                "peer_id", activity.peerId, "peer_name", activity.peerName, "peer_kind", activity.peerKind), true, null);
    }

    public static void updateViewers(int account) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId <= 0) return;
        preferences().edit().putBoolean(key("viewers_dirty", userId), true).apply();
        syncViewers(account);
    }

    private static boolean viewersDirty(long userId) {
        return preferences().getBoolean(key("viewers_dirty", userId), false);
    }

    private static JSONArray viewerPayload(int account) {
        JSONArray viewers = new JSONArray();
        if (!BuildConfig.BYGRAM_FULL) return viewers;
        for (Map.Entry<Long, Integer> viewer : BygramConfig.getActivityPermissions(account).entrySet()) {
            viewers.put(json("user_id", viewer.getKey(), "permissions", viewer.getValue()));
        }
        return viewers;
    }

    private static void syncViewers(int account) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (!isLicensed(account)) return;
        if (!hasAccess(account, () -> syncViewers(account))) return;
        if (!viewersSyncing.add(userId)) return;
        JSONArray viewers = viewerPayload(account);
        String sent = viewers.toString();
        request(userId, json("action", "viewers", "viewers", viewers), true, response -> {
            viewersSyncing.remove(userId);
            if (UserConfig.getInstance(account).getClientUserId() != userId) return;
            if (response == null || !response.optBoolean("ok")) {
                AndroidUtilities.runOnUIThread(() -> syncViewers(account), ACTIVITY_HEARTBEAT_MS);
                return;
            }
            if (!sent.equals(viewerPayload(account).toString())) {
                syncViewers(account);
                return;
            }
            preferences().edit().putBoolean(key("viewers_dirty", userId), false).apply();
            if (BygramConfig.isActivityEnabled(account)) sendActivity(account);
        });
    }

    public static void clear(int account) {
        ownActivities[account] = null;
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (!isLicensed(account) || userId <= 0 || !hasAccess(account, () -> clear(account))) return;
        request(userId, json("action", "clear"), true, null);
    }

    public static void clearCurrentActivity(int account, int type, long peerId) {
        OwnActivity activity = ownActivities[account];
        if (activity != null && activity.type == type && activity.peerId == peerId) clear(account);
    }

    public static void clearSettings() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            ownActivities[account] = null;
            long userId = UserConfig.getInstance(account).getClientUserId();
            if (userId <= 0 || preferences().getString(key("access", userId), "").isEmpty()
                    || preferences().getLong(key("access_expires", userId), 0) <= System.currentTimeMillis() / 1000) continue;
            request(userId, json("action", "clear"), true, null);
            request(userId, json("action", "viewers", "viewers", new JSONArray()), true, null);
        }
    }

    public static void get(int account, long ownerId, Callback callback) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (!BuildConfig.BYGRAM_FULL || !isLicensed(account) || ownerId <= 0) { callback.run(null); return; }
        if (!hasAccess(account, () -> get(account, ownerId, callback))) return;
        request(userId, json("action", "activity_get", "owner_id", ownerId), true, response -> {
            if (response == null || !response.optBoolean("ok")) return;
            ActivityInfo info = parseActivity(response);
            callback.run(info == null ? null : info.text());
        });
    }

    public static ActivityInfo getCachedActivity(int account, long ownerId, Runnable callback) {
        if (!BuildConfig.BYGRAM_FULL || !isLicensed(account) || ownerId <= 0) return null;
        String key = account + ":" + ownerId;
        CachedActivity cached = activityCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.loadedAt < ACTIVITY_CACHE_MS) return cached.info;
        pendingActivities.computeIfAbsent(account, unused -> new HashSet<>()).add(ownerId);
        if (callback != null) activityCallbacks.computeIfAbsent(key, unused -> new ArrayList<>()).add(callback);
        if (!activityFlushScheduled) {
            activityFlushScheduled = true;
            AndroidUtilities.runOnUIThread(BygramActivityController::flushActivities, 150);
        }
        return cached == null ? null : cached.info;
    }

    private static void flushActivities() {
        activityFlushScheduled = false;
        HashMap<Integer, Set<Long>> batches = new HashMap<>(pendingActivities);
        pendingActivities.clear();
        for (Map.Entry<Integer, Set<Long>> batch : batches.entrySet()) {
            int account = batch.getKey();
            ArrayList<Long> ids = new ArrayList<>(batch.getValue());
            if (ids.isEmpty()) continue;
            if (!hasAccess(account, () -> {
                pendingActivities.computeIfAbsent(account, unused -> new HashSet<>()).addAll(ids);
                if (!activityFlushScheduled) {
                    activityFlushScheduled = true;
                    AndroidUtilities.runOnUIThread(BygramActivityController::flushActivities, 150);
                }
            })) continue;
            long userId = UserConfig.getInstance(account).getClientUserId();
            request(userId, json("action", "activities", "owner_ids", new JSONArray(ids)), true, response -> {
                if (response == null || !response.optBoolean("ok")) {
                    for (long id : ids) {
                        ArrayList<Runnable> callbacks = activityCallbacks.remove(account + ":" + id);
                        if (callbacks != null) for (Runnable callback : callbacks) callback.run();
                    }
                    return;
                }
                HashMap<Long, ActivityInfo> loaded = new HashMap<>();
                JSONArray values = response.optJSONArray("activities");
                if (values != null) for (int i = 0; i < values.length(); i++) {
                    JSONObject value = values.optJSONObject(i);
                    ActivityInfo info = parseActivity(value);
                    if (value != null && info != null) loaded.put(value.optLong("owner_id"), info);
                }
                for (long id : ids) {
                    String key = account + ":" + id;
                    activityCache.put(key, new CachedActivity(loaded.get(id)));
                    ArrayList<Runnable> callbacks = activityCallbacks.remove(key);
                    if (callbacks != null) for (Runnable callback : callbacks) callback.run();
                }
            });
        }
    }

    private static ActivityInfo parseActivity(JSONObject value) {
        if (value == null || !value.optBoolean("ok", true)) return null;
        int type = value.optInt("activity_type");
        String name = value.optString("peer_name", "");
        if (type != BygramConfig.ACTIVITY_DIALOGS && type != BygramConfig.ACTIVITY_PROFILE && type != BygramConfig.ACTIVITY_CHAT) return null;
        if (type != BygramConfig.ACTIVITY_DIALOGS && name.isEmpty()) return null;
        return new ActivityInfo(type, value.optLong("peer_id"), name, value.optInt("peer_kind"));
    }

    private static JSONObject json(Object... values) {
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    private static void request(long userId, JSONObject data, boolean authenticated, JsonCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            JSONObject response = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ENDPOINT);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(7000); connection.setReadTimeout(7000); connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] body = data.toString().getBytes(StandardCharsets.UTF_8);
                if (authenticated) {
                    String token = preferences().getString(key("access", userId), "");
                    if (token.isEmpty()) throw new IllegalStateException("Missing BYgram access token");
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }
                if (authenticated || "refresh".equals(data.optString("action"))) {
                    String timestamp = Long.toString(System.currentTimeMillis() / 1000);
                    String nonce = UUID.randomUUID().toString();
                    String canonical = "POST\n" + url.getPath() + "\n" + timestamp + "\n" + nonce + "\n" + sha256(body);
                    connection.setRequestProperty("X-BYgram-Timestamp", timestamp);
                    connection.setRequestProperty("X-BYgram-Nonce", nonce);
                    connection.setRequestProperty("X-BYgram-Signature", BygramDeviceKey.sign(userId, canonical));
                }
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
                if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    StringBuilder text = new StringBuilder(); String line;
                    while ((line = reader.readLine()) != null) text.append(line);
                    if (text.length() > 0) response = new JSONObject(text.toString());
                }
                if (response == null) response = new JSONObject();
                response.put("_http_status", status);
            } catch (Exception e) { FileLog.e(e); }
            finally { if (connection != null) connection.disconnect(); }
            if (callback != null) { JSONObject result = response; AndroidUtilities.runOnUIThread(() -> callback.run(result)); }
        });
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte part : digest) result.append(String.format("%02x", part & 0xff));
        return result.toString();
    }
}
