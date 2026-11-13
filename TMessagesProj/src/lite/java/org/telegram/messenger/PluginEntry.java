package org.telegram.messenger;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

public final class PluginEntry {
    public static BaseFragment page() { return null; }
    public static boolean openSettingsLink(android.net.Uri uri) { return false; }
    public static void appEvent(String event) { }
    public static void addDrawerItems(BaseFragment fragment, org.telegram.ui.Components.ItemOptions options) { }
    public static void dispatchIntent(android.content.Intent intent, int account, boolean before) { }
    public static boolean hasInterceptor(String kind) { return false; }
    public static boolean hasMessageMenu(ChatActivity chat, MessageObject message) { return false; }
    public static void showMessageMenu(ChatActivity chat, MessageObject message) { }
    public static <T> T intercept(String kind, int account, Class<?> source, Class<T> type, T value, Object detail) {
        return value;
    }
    public static boolean openFile(android.app.Activity activity, java.io.File file, String name) { return false; }
    public static void resume() { }
    public static void accountChanged(int slot, long user, String generation) { }
    public static void pageEvent(BaseFragment page, boolean opened) { }
    public static Integer color(int key) { return null; }
    public static String summary() { return ""; }
}
