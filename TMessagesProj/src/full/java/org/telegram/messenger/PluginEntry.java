package org.telegram.messenger;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.BygramPluginsActivity;
import org.telegram.ui.PluginHost;
import org.telegram.messenger.plugins.PluginManager;

/** Edition boundary: common Telegram code never references Python or hook classes. */
public final class PluginEntry {
    private static final java.util.Map<Integer, String> accounts = new java.util.HashMap<>();
    private static boolean observingTheme;
    public static BaseFragment page() { return new BygramPluginsActivity(); }
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
