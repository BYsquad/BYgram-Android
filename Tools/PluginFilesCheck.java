package org.telegram.messenger.plugins;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class PluginFilesCheck {
    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), timedOut = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        PluginFiles.Request hung = PluginFiles.read(() -> {
            started.countDown();
            while (release.getCount() != 0) try { release.await(); } catch (InterruptedException ignored) { }
            return new ByteArrayInputStream(new byte[]{1});
        }, () -> {}, 10, (request, bytes, error) -> {
            assert "FileTimeout".equals(error); calls.incrementAndGet(); timedOut.countDown();
        }, 200);
        try {
            assert started.await(2, TimeUnit.SECONDS);
            assert timedOut.await(2, TimeUnit.SECONDS) : "timeout blocked by provider";
            CountDownLatch healthy = new CountDownLatch(1);
            PluginFiles.read(() -> new ByteArrayInputStream(new byte[]{1, 2}), () -> {}, 2, (request, bytes, error) -> {
                assert error == null && bytes.length == 2; healthy.countDown();
            });
            assert healthy.await(2, TimeUnit.SECONDS) : "another import blocked";
            CountDownLatch limited = new CountDownLatch(1);
            PluginFiles.read(() -> new ByteArrayInputStream(new byte[]{1, 2}), () -> {}, 1, (request, bytes, error) -> {
                assert "FileLimitExceeded".equals(error); limited.countDown();
            });
            assert limited.await(2, TimeUnit.SECONDS);
            hung.close(); assert calls.get() == 1;
            CountDownLatch read = new CountDownLatch(1);
            PluginFiles.Request cancelled = PluginFiles.read(() -> new ByteArrayInputStream(new byte[0]), () -> {}, 1,
                    (request, bytes, error) -> read.countDown());
            assert read.await(2, TimeUnit.SECONDS); cancelled.close();
            try { cancelled.check(); throw new AssertionError("queued mutation survived cancellation"); }
            catch (IOException expected) { assert expected.getMessage().equals("FileCancelled"); }
        } finally { release.countDown(); hung.close(); }
        System.out.println("PASS: hung provider deadline, independent import, byte limit, single completion and cancel before mutation");
    }
}
