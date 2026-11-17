package org.telegram.messenger.plugins;

import java.util.ArrayList;

public final class PluginScope {
    public final String id;
    public final long generation;
    private volatile boolean active = true;
    private final ArrayList<AutoCloseable> handles = new ArrayList<>();
    private int callbacks;

    PluginScope(String id, long generation) { this.id = id; this.generation = generation; }
    public boolean isActive() { return active; }
    public void check() { if (!active) throw new IllegalStateException("PluginDisabled"); }
    public AutoCloseable own(AutoCloseable handle) throws Exception {
        class Owned implements AutoCloseable {
            private boolean closed;
            @Override public void close() throws Exception {
                synchronized (PluginScope.this) {
                    if (closed) return;
                    closed = true; handles.remove(this);
                }
                handle.close();
            }
        }
        String error;
        synchronized (this) {
            if (active && handles.size() < 1024) {
                Owned owned = new Owned(); handles.add(owned); return owned;
            }
            error = active ? "HandleLimitExceeded" : "PluginDisabled";
        }
        handle.close(); throw new IllegalStateException(error);
    }
    public void invalidate() { active = false; }
    public synchronized boolean beginCallback() {
        if (!active) return false;
        callbacks++; return true;
    }
    synchronized void beginLifecycle() { callbacks++; }
    public synchronized void endCallback() { callbacks--; notifyAll(); }
    public synchronized boolean awaitIdle(long timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout * 1000000L;
        while (callbacks > 0) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return false;
            wait(Math.max(1, remaining / 1000000L));
        }
        return true;
    }
    public void close() {
        ArrayList<AutoCloseable> closing;
        synchronized (this) { active = false; closing = new ArrayList<>(handles); handles.clear(); }
        for (int i = closing.size() - 1; i >= 0; i--) {
            try { closing.get(i).close(); } catch (Exception ignored) { /* Remaining handles must still close. */ }
        }
    }
}
