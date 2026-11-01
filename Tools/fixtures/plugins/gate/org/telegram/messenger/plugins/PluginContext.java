package org.telegram.messenger.plugins;
/** Gate-only type linkage; runtime behavior is checked on Android, not here. */
public final class PluginContext {
    public PluginScope scope; public PluginManifest manifest;
    public void capability(String name) { throw new AssertionError(); }
    public String id() { throw new AssertionError(); }
    public boolean active() { throw new AssertionError(); }
    public void failed(Exception error) { throw new AssertionError(); }
}
