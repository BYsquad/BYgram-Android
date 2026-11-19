package org.telegram.messenger.plugins;

import java.io.IOException;
import java.util.concurrent.*;

/** A stuck author callback must not occupy the registry/management queue. */
final class PluginTasks {
    static <T> T call(PluginScope scope, Callable<T> callback) throws Exception {
        return call(scope, callback, 5000);
    }
    static <T> T call(PluginScope scope, Callable<T> callback, long timeout) throws Exception {
        FutureTask<T> future = new FutureTask<>(() -> {
            scope.beginLifecycle();
            try { return callback.call(); } finally { scope.endCallback(); }
        });
        // Management serializes calls; a timed-out generation cannot be enabled again.
        Thread worker = new Thread(future, "BYgramPluginLifecycle"); worker.setDaemon(true); worker.start();
        try { return future.get(timeout, TimeUnit.MILLISECONDS); }
        catch (TimeoutException | InterruptedException timeoutError) {
            scope.invalidate(); future.cancel(true);
            if (timeoutError instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IOException("PluginRestartRequired", timeoutError);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new IOException("PluginOperationFailed", cause);
        }
    }
}
