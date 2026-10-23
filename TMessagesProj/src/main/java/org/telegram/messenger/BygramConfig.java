package org.telegram.messenger;

import android.content.SharedPreferences;
import android.util.Base64;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BygramConfig {

    public static final String GHOST_ENABLED = "bygram_ghost_enabled";
    public static final String GHOST_NO_READ = "bygram_ghost_no_read";
    public static final String GHOST_NO_STORY_READ = "bygram_ghost_no_story_read";
    public static final String GHOST_NO_ONLINE = "bygram_ghost_no_online";
    public static final String GHOST_NO_TYPING = "bygram_ghost_no_typing";
    public static final String GHOST_READ_ON_ACTIONS = "bygram_ghost_read_on_actions";

    public static final String SPY_SAVE_DELETED = "bygram_spy_save_deleted";
    public static final String SPY_SAVE_OWN_DELETED = "bygram_spy_save_own_deleted";
    public static final String SPY_SAVE_EDITS = "bygram_spy_save_edits";
    public static final String SPY_SAVE_READ_DATE = "bygram_spy_save_read_date";
    public static final String SPY_SAVE_LAST_ONLINE = "bygram_spy_save_last_online";
    public static final String FIREBASE_CRASHLYTICS = "bygram_firebase_crashlytics";
    public static final String FIREBASE_ANALYTICS = "bygram_firebase_analytics";
    public static final String CUSTOM_PROFILE_MUSIC = "bygram_custom_profile_music";
    public static final String HIDE_PHONE = "bygram_hide_phone";
    public static final String RELATIVE_ONLINE = "bygram_relative_online";
    public static final String NO_UNARCHIVE_SWIPE = "bygram_no_unarchive_swipe";
    public static final String DISABLE_VIBRATION = "bygram_disable_vibration";
    public static final String CALL_VIBRATION = "bygram_call_vibration";
    private static final String CUSTOM_TYPING_PREFIX = "bygram_custom_typing_";
    public static final String FILTER_ZALGO = "bygram_filter_zalgo";
    public static final String COLLAPSE_SEARCH_ON_START = "bygram_collapse_search_on_start";
    public static final String KEEP_SEARCH_COLLAPSED = "bygram_keep_search_collapsed";
    public static final String HIDE_PROFILE_BACKGROUNDS = "bygram_hide_profile_backgrounds";
    public static final String PROFILE_ID_MODE = "bygram_profile_id";
    public static final String MD3_LOADING = "bygram_md3_loading";
    public static final String MD3_SLIDERS = "bygram_md3_sliders";
    public static final String MD3_SWITCHES = "bygram_md3_switches";
    public static final String AVATAR_CORNERS = "bygram_avatar_corners";
    public static final String TITLE_TEXT = "bygram_title_text";
    public static final String TITLE_POSITION = "bygram_title_position";
    private static final int DATA_RETENTION_SECONDS = 90 * 24 * 60 * 60;
    private static final String LEGACY_ACTIVITY_ENABLED = "bygram_activity_enabled";
    public static final int ACTIVITY_DIALOGS = 1;
    public static final int ACTIVITY_PROFILE = 1 << 1;
    public static final int ACTIVITY_CHAT = 1 << 2;
    public static final int ACTIVITY_ALL = ACTIVITY_DIALOGS | ACTIVITY_PROFILE | ACTIVITY_CHAT;

    private static String activityEnabledKey(int account) {
        return "bygram_activity_enabled_" + account;
    }

    private static String activityViewersKey(int account) {
        return "bygram_activity_viewers_" + account;
    }

    private static boolean isActivityStorageKey(String key) {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (activityEnabledKey(account).equals(key) || activityViewersKey(account).equals(key)) return true;
        }
        return false;
    }

    public static Map<Long, Integer> getActivityPermissions(int account) {
        HashMap<Long, Integer> result = new HashMap<>();
        try {
            String stored = preferences().getString(activityViewersKey(account), "{}");
            if (stored.trim().startsWith("[")) {
                JSONArray values = new JSONArray(stored);
                for (int i = 0; i < values.length(); i++) {
                    long id = values.optLong(i);
                    if (id > 0) result.put(id, ACTIVITY_CHAT);
                }
            } else {
                JSONObject values = new JSONObject(stored);
                java.util.Iterator<String> keys = values.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    long id = Long.parseLong(key);
                    int permissions = values.optInt(key) & ACTIVITY_ALL;
                    if (id > 0 && permissions != 0) result.put(id, permissions);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public static Set<Long> getActivityViewers(int account) {
        return new HashSet<>(getActivityPermissions(account).keySet());
    }

    public static void setActivityPermissions(int account, long userId, int permissions) {
        Map<Long, Integer> viewers = getActivityPermissions(account);
        permissions &= ACTIVITY_ALL;
        if (userId > 0 && permissions != 0) viewers.put(userId, permissions); else viewers.remove(userId);
        JSONObject stored = new JSONObject();
        try {
            for (Map.Entry<Long, Integer> viewer : viewers.entrySet()) {
                stored.put(Long.toString(viewer.getKey()), viewer.getValue());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        preferences().edit().putString(activityViewersKey(account), stored.toString()).apply();
    }

    public static boolean isActivityEnabled(int account) {
        return preferences().getBoolean(activityEnabledKey(account), false);
    }

    public static void setActivityEnabled(int account, boolean enabled) {
        preferences().edit().putBoolean(activityEnabledKey(account), enabled).apply();
    }

    public static void clearActivitySettings() {
        SharedPreferences.Editor editor = preferences().edit().remove(LEGACY_ACTIVITY_ENABLED);
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            editor.remove(activityEnabledKey(account)).remove(activityViewersKey(account));
        }
        editor.apply();
    }

    private static final ThreadLocal<Boolean> allowReadReceipt = new ThreadLocal<>();

    private BygramConfig() {
    }

    private static SharedPreferences preferences() {
        return MessagesController.getGlobalMainSettings();
    }

    private static boolean isActiveSetting(String key) {
        if (key.startsWith(CUSTOM_TYPING_PREFIX)) return true;
        switch (key) {
            case GHOST_ENABLED:
            case GHOST_NO_READ:
            case GHOST_NO_STORY_READ:
            case GHOST_NO_ONLINE:
            case GHOST_NO_TYPING:
            case GHOST_READ_ON_ACTIONS:
            case SPY_SAVE_DELETED:
            case SPY_SAVE_OWN_DELETED:
            case SPY_SAVE_EDITS:
            case SPY_SAVE_READ_DATE:
            case SPY_SAVE_LAST_ONLINE:
            case FIREBASE_CRASHLYTICS:
            case FIREBASE_ANALYTICS:
            case CUSTOM_PROFILE_MUSIC:
            case HIDE_PHONE:
            case RELATIVE_ONLINE:
            case NO_UNARCHIVE_SWIPE:
            case DISABLE_VIBRATION:
            case CALL_VIBRATION:
            case FILTER_ZALGO:
            case COLLAPSE_SEARCH_ON_START:
            case KEEP_SEARCH_COLLAPSED:
            case HIDE_PROFILE_BACKGROUNDS:
            case PROFILE_ID_MODE:
            case MD3_LOADING:
            case MD3_SLIDERS:
            case MD3_SWITCHES:
            case AVATAR_CORNERS:
            case TITLE_TEXT:
            case TITLE_POSITION:
                return true;
            default:
                return false;
        }
    }

    public static void clearRemovedCustomizationSettings() {
        SharedPreferences prefs = preferences();
        SharedPreferences.Editor editor = prefs.edit();
        if (prefs.contains(LEGACY_ACTIVITY_ENABLED)) {
            boolean enabled = prefs.getBoolean(LEGACY_ACTIVITY_ENABLED, false);
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                if (!prefs.contains(activityEnabledKey(account))) editor.putBoolean(activityEnabledKey(account), enabled);
            }
            editor.remove(LEGACY_ACTIVITY_ENABLED);
        }
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("bygram_") && !isActiveSetting(key)
                    && !key.startsWith("bygram_edit_") && !key.startsWith("bygram_read_")
                    && !key.startsWith("bygram_online_") && !isActivityStorageKey(key)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    public static boolean getBoolean(String key) {
        if (!isActiveSetting(key)) return false;
        boolean defaultValue = GHOST_NO_READ.equals(key) || GHOST_NO_STORY_READ.equals(key)
                || GHOST_NO_ONLINE.equals(key) || GHOST_NO_TYPING.equals(key) || FIREBASE_CRASHLYTICS.equals(key)
                || CALL_VIBRATION.equals(key);
        return preferences().getBoolean(key, defaultValue);
    }

    public static String getCustomTyping(int account, int action) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT || action < 0 || action > 10) return "";
        return preferences().getString(CUSTOM_TYPING_PREFIX + account + "_" + action, "");
    }

    public static void setCustomTyping(int account, int action, String value) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT || action < 0 || action > 10) return;
        value = value == null ? "" : value.trim();
        if (value.length() > 64) value = value.substring(0, 64);
        preferences().edit().putString(CUSTOM_TYPING_PREFIX + account + "_" + action, value).apply();
    }

    public static void setBoolean(String key, boolean value) {
        if (!isActiveSetting(key)) return;
        preferences().edit().putBoolean(key, value).apply();
    }

    public static int getInt(String key, int fallback) {
        return PROFILE_ID_MODE.equals(key) || AVATAR_CORNERS.equals(key) || TITLE_TEXT.equals(key) || TITLE_POSITION.equals(key)
                ? preferences().getInt(key, fallback) : fallback;
    }

    public static void setInt(String key, int value) {
        if (PROFILE_ID_MODE.equals(key) || AVATAR_CORNERS.equals(key) || TITLE_TEXT.equals(key) || TITLE_POSITION.equals(key)) {
            preferences().edit().putInt(key, value).apply();
        }
    }

    public static float avatarRadius(float size) {
        int corners = Math.max(0, Math.min(28, getInt(AVATAR_CORNERS, 28)));
        return size * corners / 56f;
    }

    public static String dialogsTitle(int account) {
        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        int mode = getInt(TITLE_TEXT, 0);
        if (self != null) {
            if (mode == 1 && self.username != null && !self.username.isEmpty()) return "@" + self.username;
            if (mode == 2) {
                String name = UserObject.getUserName(self);
                if (!name.isEmpty()) return name;
            }
        }
        return LocaleController.getString(R.string.AppName);
    }

    private static boolean isExportableSetting(String key) {
        return isActiveSetting(key);
    }

    public static JSONObject exportSettings() throws org.json.JSONException {
        JSONObject result = new JSONObject();
        for (java.util.Map.Entry<String, ?> entry : preferences().getAll().entrySet()) {
            if (isExportableSetting(entry.getKey())) result.put(entry.getKey(), entry.getValue());
        }
        return new JSONObject().put("app", "BYgram").put("version", 1).put("settings", result);
    }

    public static void resetSettings() {
        BygramChatPrivacy.reset();
        SharedPreferences.Editor editor = preferences().edit();
        for (String key : preferences().getAll().keySet()) {
            if (isExportableSetting(key) || key.equals("bygram_microsoft_translator_key")) editor.remove(key);
        }
        editor.apply();
        clearActivitySettings();
        BygramActivityController.clearSettings();
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).getClientUserId() > 0) {
                BygramTypingController.clear(account);
                BygramTypingController.clearProfileViews(account);
            }
        }
        applyTelemetrySettings();
    }

    public static void applyTelemetrySettings() {
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(getBoolean(FIREBASE_CRASHLYTICS));
            FirebaseAnalytics.getInstance(ApplicationLoader.applicationContext).setAnalyticsCollectionEnabled(getBoolean(FIREBASE_ANALYTICS));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static String getSaveFolder() {
        return "Telegram";
    }

    public static int videoSeekMs() {
        return 10000;
    }

    public static int hapticFeedback(int effect) {
        return getBoolean(DISABLE_VIBRATION) ? android.view.HapticFeedbackConstants.NO_HAPTICS : effect;
    }

    public static boolean isCallVibrationEnabled() {
        return !getBoolean(DISABLE_VIBRATION) || getBoolean(CALL_VIBRATION);
    }

    public static int callHapticFeedback(int effect) {
        return isCallVibrationEnabled() ? effect : android.view.HapticFeedbackConstants.NO_HAPTICS;
    }

    public static boolean isGhostEnabled() {
        return getBoolean(GHOST_ENABLED);
    }

    public static boolean shouldBlockReadReceipts() {
        return isGhostEnabled() && getBoolean(GHOST_NO_READ) && !Boolean.TRUE.equals(allowReadReceipt.get());
    }

    public static boolean isReadReceiptAllowed() {
        return Boolean.TRUE.equals(allowReadReceipt.get());
    }

    public static boolean shouldBlockStoryReads() {
        return isGhostEnabled() && getBoolean(GHOST_NO_STORY_READ);
    }

    public static boolean shouldBlockOnline() {
        return isGhostEnabled() && getBoolean(GHOST_NO_ONLINE);
    }

    public static boolean shouldBlockTyping() {
        return isGhostEnabled() && getBoolean(GHOST_NO_TYPING);
    }

    public static boolean shouldForceOffline() {
        return shouldBlockOnline();
    }

    public static boolean shouldReadOnActions() {
        return isGhostEnabled() && getBoolean(GHOST_READ_ON_ACTIONS);
    }

    public static void withReadReceipt(Runnable action) {
        allowReadReceipt.set(true);
        try {
            action.run();
        } finally {
            allowReadReceipt.remove();
        }
    }

    public static int ghostOptionsEnabled() {
        int count = 0;
        if (getBoolean(GHOST_NO_READ)) count++;
        if (getBoolean(GHOST_NO_STORY_READ)) count++;
        if (getBoolean(GHOST_NO_ONLINE)) count++;
        if (getBoolean(GHOST_NO_TYPING)) count++;
        if (getBoolean(GHOST_READ_ON_ACTIONS)) count++;
        return count;
    }

    private static String historyKey(int account, long dialogId, int messageId) {
        return "bygram_edit_" + account + '_' + dialogId + '_' + messageId;
    }

    public static void saveEditHistory(int account, long dialogId, int messageId, int date, TLRPC.Message message) {
        if (!getBoolean(SPY_SAVE_EDITS) || message == null) {
            return;
        }
        String key = historyKey(account, dialogId, messageId);
        try {
            JSONArray history = new JSONArray(preferences().getString(key, "[]"));
            String text = message.message == null ? "" : message.message;
            String serializedEntities = serializeEntities(message.entities);
            String serializedMedia = serializeMedia(message.media);
            if (history.length() > 0 && text.equals(history.getJSONObject(history.length() - 1).optString("text"))
                    && serializedEntities.equals(history.getJSONObject(history.length() - 1).optString("entities"))
                    && serializedMedia.equals(history.getJSONObject(history.length() - 1).optString("media"))) {
                return;
            }
            JSONObject entry = new JSONObject();
            entry.put("date", date);
            entry.put("text", text);
            if (!serializedEntities.isEmpty()) entry.put("entities", serializedEntities);
            if (!serializedMedia.isEmpty()) entry.put("media", serializedMedia);
            entry.put("attachPath", message.attachPath);
            history.put(entry);
            // shortcut: SharedPreferences stores at most 20 snapshots; move to SQLite for larger histories.
            while (history.length() > 20) {
                history.remove(0);
            }
            preferences().edit().putString(key, history.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean hasEditHistory(int account, long dialogId, int messageId) {
        return preferences().contains(historyKey(account, dialogId, messageId));
    }

    public static ArrayList<MessageObject> getEditHistory(int account, TLRPC.Message source) {
        ArrayList<MessageObject> result = new ArrayList<>();
        try {
            JSONArray history = new JSONArray(preferences().getString(historyKey(account, MessageObject.getDialogId(source), source.id), "[]"));
            for (int i = history.length() - 1; i >= 0; i--) {
                try {
                    JSONObject entry = history.getJSONObject(i);
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    // Local preview IDs keep playback of different revisions separate from the live message.
                    message.id = Integer.MIN_VALUE + i;
                    message.dialog_id = source.dialog_id;
                    message.peer_id = source.peer_id;
                    message.from_id = source.from_id;
                    message.out = source.out;
                    message.noforwards = source.noforwards;
                    message.date = entry.optInt("date");
                    message.message = entry.optString("text");
                    message.attachPath = entry.optString("attachPath");
                    String entities = entry.optString("entities");
                    if (!entities.isEmpty()) {
                        SerializedData data = new SerializedData(Base64.decode(entities, Base64.NO_WRAP));
                        try {
                            int count = data.readInt32(true);
                            if (count < 0 || count > data.remaining() / 4) throw new IllegalArgumentException("Invalid history entities");
                            for (int j = 0; j < count; j++) {
                                message.entities.add(TLRPC.MessageEntity.TLdeserialize(data, data.readInt32(true), true));
                            }
                        } finally {
                            data.cleanup();
                        }
                    }
                    String media = entry.optString("media");
                    if (!media.isEmpty()) {
                        SerializedData data = new SerializedData(Base64.decode(media, Base64.NO_WRAP));
                        try {
                            message.media = TLRPC.MessageMedia.TLdeserialize(data, data.readInt32(true), true);
                        } finally {
                            data.cleanup();
                        }
                    }
                    message.flags = (message.from_id != null ? 256 : 0)
                            | (!message.entities.isEmpty() ? 128 : 0) | (message.media != null ? 512 : 0);
                    result.add(new MessageObject(account, message, true, true));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public static void rememberReadDate(int account, long dialogId, int previousMaxMessageId, int maxMessageId, int date) {
        if (!getBoolean(SPY_SAVE_READ_DATE) || maxMessageId <= 0 || date <= 0) return;
        String key = "bygram_read_ranges_" + account + '_' + dialogId;
        try {
            JSONArray ranges = new JSONArray(preferences().getString(key, "[]"));
            String baselineKey = "bygram_read_baseline_" + account + '_' + dialogId;
            int previousMax = ranges.length() == 0 ? Math.max(0, previousMaxMessageId) : ranges.getJSONObject(ranges.length() - 1).optInt("end");
            if (maxMessageId <= previousMax) return;
            ranges.put(new JSONObject().put("start", previousMax + 1).put("end", maxMessageId).put("date", date));
            int cutoff = date - DATA_RETENTION_SECONDS;
            while (ranges.length() > 0 && (ranges.getJSONObject(0).optInt("date") < cutoff || ranges.length() > 256)) ranges.remove(0);
            preferences().edit().putString(key, ranges.toString()).putInt(baselineKey, maxMessageId).putInt(baselineKey + "_time", date).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static String serializeEntities(ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) return "";
        int size = 4;
        for (TLRPC.MessageEntity entity : entities) size += entity.getObjectSize();
        SerializedData data = new SerializedData(size);
        data.writeInt32(entities.size());
        for (TLRPC.MessageEntity entity : entities) entity.serializeToStream(data);
        String result = Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        data.cleanup();
        return result;
    }

    public static boolean sameEntities(ArrayList<TLRPC.MessageEntity> left, ArrayList<TLRPC.MessageEntity> right) {
        return serializeEntities(left).equals(serializeEntities(right));
    }

    private static String serializeMedia(TLRPC.MessageMedia media) {
        if (media == null || media instanceof TLRPC.TL_messageMediaEmpty) return "";
        SerializedData data = new SerializedData(media.getObjectSize());
        try {
            media.serializeToStream(data);
            return Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        } finally {
            data.cleanup();
        }
    }

    public static boolean sameMedia(TLRPC.MessageMedia left, TLRPC.MessageMedia right) {
        return serializeMedia(left).equals(serializeMedia(right));
    }

    public static int getReadDate(int account, long dialogId, int messageId) {
        if (!getBoolean(SPY_SAVE_READ_DATE)) return 0;
        try {
            JSONArray ranges = new JSONArray(preferences().getString("bygram_read_ranges_" + account + '_' + dialogId, "[]"));
            for (int i = ranges.length() - 1; i >= 0; i--) {
                JSONObject range = ranges.getJSONObject(i);
                if (messageId >= range.optInt("start") && messageId <= range.optInt("end")) return range.optInt("date");
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public static void rememberLastOnline(int account, long userId, int date) {
        if (getBoolean(SPY_SAVE_LAST_ONLINE) && userId > 0 && date > 0) {
            preferences().edit().putInt("bygram_online_" + account + '_' + userId, date).apply();
        }
    }

    public static int getLastOnline(int account, long userId) {
        int date = getBoolean(SPY_SAVE_LAST_ONLINE) ? preferences().getInt("bygram_online_" + account + '_' + userId, 0) : 0;
        return date > ConnectionsManager.getInstance(account).getCurrentTime() - DATA_RETENTION_SECONDS ? date : 0;
    }

    public static void clearSpyData() {
        SharedPreferences.Editor editor = preferences().edit();
        for (String key : preferences().getAll().keySet()) {
            if (key.startsWith("bygram_edit_") || key.startsWith("bygram_read_") || key.startsWith("bygram_online_")) editor.remove(key);
        }
        editor.apply();
    }

    public static void pruneSpyData() {
        int cutoff = (int) (System.currentTimeMillis() / 1000) - DATA_RETENTION_SECONDS;
        SharedPreferences.Editor editor = preferences().edit();
        for (java.util.Map.Entry<String, ?> item : preferences().getAll().entrySet()) {
            String key = item.getKey();
            try {
                if (key.startsWith("bygram_online_") && item.getValue() instanceof Integer && (Integer) item.getValue() < cutoff) {
                    editor.remove(key);
                } else if (key.startsWith("bygram_edit_") && item.getValue() instanceof String) {
                    JSONArray history = new JSONArray((String) item.getValue());
                    for (int i = history.length() - 1; i >= 0; i--) {
                        if (history.getJSONObject(i).optInt("date") < cutoff) history.remove(i);
                    }
                    if (history.length() == 0) editor.remove(key); else editor.putString(key, history.toString());
                } else if (key.startsWith("bygram_read_ranges_") && item.getValue() instanceof String) {
                    JSONArray ranges = new JSONArray((String) item.getValue());
                    while (ranges.length() > 0 && ranges.getJSONObject(0).optInt("date") < cutoff) ranges.remove(0);
                    if (ranges.length() == 0) editor.remove(key); else editor.putString(key, ranges.toString());
                } else if (key.startsWith("bygram_read_baseline_") && key.endsWith("_time") && item.getValue() instanceof Integer && (Integer) item.getValue() < cutoff) {
                    editor.remove(key).remove(key.substring(0, key.length() - 5));
                }
            } catch (Exception e) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

}
