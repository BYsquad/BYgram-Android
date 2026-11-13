package org.telegram.messenger.plugins;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Provider I/O is bounded independently of plugin management and catalogue downloads. */
final class PluginFiles {
    interface Opener { InputStream open() throws Exception; }
    interface OutputOpener { OutputStream open() throws Exception; }
    private interface Transfer { byte[] run(Request request) throws Exception; }
    interface Result { void done(Request request, byte[] bytes, String error); }
    private static final ThreadPoolExecutor readers = pool("BYgramPluginFiles");
    private static final ThreadPoolExecutor closers = pool("BYgramPluginFileCancel");
    private static final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> thread(r, "BYgramPluginFileDeadline"));

    private static Thread thread(Runnable runnable, String name) { Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread; }
    private static ThreadPoolExecutor pool(String name) {
        return new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> thread(r, name));
    }
    static final class Request implements AutoCloseable {
        private final AtomicBoolean finished = new AtomicBoolean();
        private final Result result;
        private final Runnable cancelOpen;
        private volatile Closeable stream;
        private volatile Future<?> task;
        private volatile ScheduledFuture<?> deadline;
        private volatile String cancelled;
        Request(Result result, Runnable cancelOpen) { this.result = result; this.cancelOpen = cancelOpen; }
        void check() throws IOException { if (cancelled != null) throw new IOException(cancelled); }
        void finish(byte[] bytes, String error) {
            if (!finished.compareAndSet(false, true)) return;
            ScheduledFuture<?> timer = deadline; if (timer != null) timer.cancel(false);
            result.done(this, bytes, error);
        }
        private void cancel(String error) {
            cancelled = error;
            if (!finished.compareAndSet(false, true)) return;
            ScheduledFuture<?> timer = deadline; if (timer != null) timer.cancel(false);
            Future<?> running = task; if (running != null) running.cancel(true);
            // A broken provider may also block cancellation/close; never do either on UI/deadline threads.
            try { closers.execute(() -> {
                try { cancelOpen.run(); } finally { Closeable stream = this.stream; if (stream != null) try { stream.close(); } catch (IOException ignored) { } }
            }); } catch (RejectedExecutionException ignored) { }
            result.done(this, null, error);
        }
        @Override public void close() { cancel("FileCancelled"); }
    }
    static Request read(Opener opener, Runnable cancelOpen, int limit, Result result) {
        return read(opener, cancelOpen, limit, result, 30000);
    }
    static Request read(Opener opener, Runnable cancelOpen, int limit, Result result, long timeout) {
        return transfer(request -> {
            try (InputStream input = opener.open(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                request.stream = input; request.check();
                if (input == null) throw new IOException("FileUnavailable");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    request.check();
                    if (out.size() + count > limit) throw new IOException("FileLimitExceeded");
                    out.write(buffer, 0, count);
                }
                return out.toByteArray();
            }
        }, cancelOpen, result, timeout);
    }

    static Request write(OutputOpener opener, Callable<byte[]> content, Runnable cancelOpen, Result result, long timeout) {
        return transfer(request -> {
            byte[] bytes = content.call();
            request.check();
            try (OutputStream output = opener.open()) {
                request.stream = output; request.check();
                if (output == null) throw new IOException("FileUnavailable");
                output.write(bytes);
            }
            return null;
        }, cancelOpen, result, timeout);
    }

    private static Request transfer(Transfer transfer, Runnable cancelOpen, Result result, long timeout) {
        Request request = new Request(result, cancelOpen);
        request.deadline = deadlines.schedule(() -> request.cancel("FileTimeout"), timeout, TimeUnit.MILLISECONDS);
        try {
            request.task = readers.submit(() -> {
                try {
                    byte[] bytes = transfer.run(request);
                    request.check();
                    request.finish(bytes, null);
                } catch (Exception error) {
                    if (error instanceof ExecutionException && error.getCause() instanceof Exception)
                        error = (Exception) error.getCause();
                    String code = error instanceof IOException ? error.getMessage() : "FileUnavailable";
                    request.finish(null, code != null && code.matches("[A-Za-z]+") ? code : "FileUnavailable");
                } finally { request.stream = null; }
            });
            if (request.cancelled != null) request.task.cancel(true);
        } catch (RejectedExecutionException busy) { request.finish(null, "FileBusy"); }
        return request;
    }
}
