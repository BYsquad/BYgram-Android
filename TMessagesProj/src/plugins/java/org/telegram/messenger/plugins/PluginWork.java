package org.telegram.messenger.plugins;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** One plugin cannot consume another plugin's workers or queue. */
final class PluginWork implements AutoCloseable {
    private final ThreadPoolExecutor workers;
    PluginWork() { this(2); }
    PluginWork(int threads) {
        workers = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            Thread thread = new Thread(runnable, "BYgramPluginWork"); thread.setDaemon(true); return thread;
        });
        workers.allowCoreThreadTimeOut(true);
    }

    AutoCloseable submit(PluginScope scope, Runnable callback) throws Exception {
        scope.check();
        AtomicReference<AutoCloseable> owned = new AtomicReference<>();
        FutureTask<Void> task = new FutureTask<Void>(() -> { if (scope.isActive()) callback.run(); return null; }) {
            @Override protected void done() {
                AutoCloseable handle = owned.get();
                if (handle != null) try { handle.close(); } catch (Exception ignored) { }
            }
        };
        AutoCloseable handle = scope.own(() -> { task.cancel(true); workers.remove(task); });
        owned.set(handle);
        try { workers.execute(task); }
        catch (RejectedExecutionException full) { handle.close(); throw new IllegalStateException("WorkQueueFull", full); }
        return handle;
    }

    @Override public void close() {
        for (Runnable task : workers.shutdownNow()) if (task instanceof Future<?>) ((Future<?>) task).cancel(true);
    }
}
