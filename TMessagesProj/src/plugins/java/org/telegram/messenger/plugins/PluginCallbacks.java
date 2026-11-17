package org.telegram.messenger.plugins;

import java.util.concurrent.*;

/** Host UI dispatch never waits for author code. Callbacks preserve per-plugin order. */
final class PluginCallbacks implements AutoCloseable {
    private final PluginWork callbacks = new PluginWork(1);
    private static final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "BYgramPluginCallbackDeadline"); thread.setDaemon(true); return thread;
    });
    void dispatch(PluginScope scope, Runnable callback, Runnable timeout) throws Exception {
        dispatch(scope, callback, timeout, 5000);
    }
    void dispatch(PluginScope scope, Runnable callback, Runnable timeout, long millis) throws Exception {
        callbacks.submit(scope, () -> {
            ScheduledFuture<?> timer = deadlines.schedule(timeout, millis, TimeUnit.MILLISECONDS);
            try { if (scope.isActive()) callback.run(); } finally { timer.cancel(false); }
        });
    }
    @Override public void close() { callbacks.close(); }
}
