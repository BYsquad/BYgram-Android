package org.telegram.messenger;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.BygramPluginsActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.PluginHost;
import org.telegram.messenger.plugins.PluginManager;
import org.telegram.messenger.plugins.PluginInterceptors;

/** Edition boundary: common Telegram code never references Python or hook classes. */
public final class PluginEntry {
    public enum FilePlace { UNKNOWN, ChatActivity, FilteredSearchView, SharedMediaLayout, SearchDownloadsContainer, ChannelAdminLogActivity }

    public static FilePlace filePlace(BaseFragment parent) {
        if (parent instanceof ChatActivity) return FilePlace.ChatActivity;
        if (parent instanceof org.telegram.ui.ProfileActivity) return FilePlace.SharedMediaLayout;
        if (parent instanceof org.telegram.ui.ChannelAdminLogActivity) return FilePlace.ChannelAdminLogActivity;
        return FilePlace.UNKNOWN;
    }

    public static boolean openFileHook(MessageObject message, java.io.File file, String name, android.app.Activity activity,
                                       BaseFragment parent, FilePlace place) {
        return !org.telegram.ui.PluginFileActivity.supports(name)
                && org.telegram.messenger.plugins.PluginFileHooks.open(message, file, name, activity, parent, place);
    }

    public static android.graphics.drawable.Drawable fileHookIcon(MessageObject message, String name, FilePlace place) {
        return org.telegram.messenger.plugins.PluginFileHooks.icon(message, name, place);
    }
    private static final java.util.Map<Integer, String> accounts = new java.util.HashMap<>();
    private static boolean observingTheme;
    public static BaseFragment page() { return new BygramPluginsActivity(); }
    public static boolean openSettingsLink(android.net.Uri uri) {
        if (!"plugins".equals(uri.getQueryParameter("s"))) return false;
        String id = uri.getQueryParameter("plugin"), row = uri.getQueryParameter("row");
        if (id == null || id.length() > 120 || !id.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
                || row == null || !row.matches("[A-Za-z0-9_.-]{1,64}(/[A-Za-z0-9_.-]{1,64}){0,11}")) return false;
        PluginManager.get().settingsLink(id, row, (value, error) -> {
            if (error != null)
                android.widget.Toast.makeText(ApplicationLoader.applicationContext,
                        LocaleController.getString(R.string.ErrorOccurred), android.widget.Toast.LENGTH_SHORT).show();
        });
        return true;
    }
    public static void appEvent(String event) { PluginManager.get().appEvent(event); }
    public static void addDrawerItems(BaseFragment fragment, org.telegram.ui.Components.ItemOptions options) {
        PluginHost.addDrawerItems(fragment, options);
    }
    public static boolean dispatchIntent(android.content.Intent intent, int account, boolean before) {
        return PluginHost.dispatchIntent(intent, account, before);
    }
    public static boolean hasInterceptor(String kind) { return PluginInterceptors.has(kind); }
    public static boolean hasMessageMenu(ChatActivity chat, MessageObject message) {
        return PluginHost.hasMessageMenu(chat, message);
    }
    public static void showMessageMenu(ChatActivity chat, MessageObject message) {
        PluginHost.showMessageMenu(chat, message);
    }
    public static <T> T intercept(String kind, int account, Class<?> source, Class<T> type, T value, Object detail) {
        return PluginInterceptors.dispatch(kind, account, source, type, value, detail);
    }
    public static boolean openFile(android.app.Activity activity, java.io.File file, String name) {
        if (!org.telegram.ui.PluginFileActivity.supports(name)) return false;
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(activity,
                    ApplicationLoader.getApplicationId() + ".provider", file);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setClass(activity, org.telegram.ui.PluginFileActivity.class)
                    .setDataAndType(uri, org.telegram.ui.PluginFileActivity.MIME_TYPE)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (RuntimeException error) {
            FileLog.e(error);
            android.widget.Toast.makeText(activity, LocaleController.getString(R.string.BygramPluginErrorStorage),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
        return true;
    }
    public static void resume() {
        if (!observingTheme) {
            observingTheme = true;
            NotificationCenter.getGlobalInstance().addObserver((id, account, args) -> PluginHost.emit("theme.changed", "{}"), NotificationCenter.didSetNewTheme);
        }
        PluginManager.get().onResume();
        for (int slot = 0; slot < UserConfig.MAX_ACCOUNT_COUNT; slot++) {
            long user = UserConfig.getInstance(slot).isClientActivated() ? UserConfig.getInstance(slot).getClientUserId() : 0;
            accountChanged(slot, user, UserConfig.getInstance(slot).bygramPluginAuthGeneration);
        }
    }
    public static void accountChanged(int slot, long user, String generation) {
        String identity = user == 0 ? "" : slot + ":" + user + ":" + generation;
        AndroidUtilities.runOnUIThread(() -> {
            UserConfig config = UserConfig.getInstance(slot);
            String actual = config.isClientActivated() ? slot + ":" + config.getClientUserId() + ":" + config.bygramPluginAuthGeneration : "";
            if (!actual.equals(identity)) return;
            String previous = accounts.getOrDefault(slot, "");
            if (identity.equals(previous)) return;
            accounts.put(slot, identity);
            if (!previous.isEmpty()) accountEvent("account.logged_out", slot, previous);
            if (!identity.isEmpty()) accountEvent("account.added", slot, identity);
        });
    }
    private static void accountEvent(String name, int slot, String identity) {
        com.google.gson.JsonObject event = new com.google.gson.JsonObject();
        event.addProperty("account", slot); event.addProperty("user_id", Long.parseLong(identity.split(":")[1]));
        event.addProperty("identity", identity); PluginHost.emitAccount(name, identity, event.toString());
    }
    public static void pageEvent(BaseFragment page, boolean opened) { PluginHost.pageEvent(page, opened); }
    public static Integer color(int key) { return PluginHost.color(key); }
    public static String summary() {
        com.google.gson.JsonObject state = PluginManager.get().snapshot();
        int active = 0, total = 0;
        if (state.has("plugins")) for (com.google.gson.JsonElement value : state.getAsJsonObject("plugins").asMap().values()) {
            total++; if (value.getAsJsonObject().get("active").getAsBoolean()) active++;
        }
        return String.format(LocaleController.getString(R.string.BygramPluginCount), active, total);
    }
}
