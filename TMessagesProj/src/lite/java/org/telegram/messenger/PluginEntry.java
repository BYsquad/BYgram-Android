package org.telegram.messenger;

import org.telegram.ui.ActionBar.BaseFragment;

public final class PluginEntry {
    public static BaseFragment page() { return null; }
    public static void resume() { }
    public static void accountChanged(int slot, long user, String generation) { }
    public static void pageEvent(BaseFragment page, boolean opened) { }
    public static Integer color(int key) { return null; }
    public static String summary() { return ""; }
}
