package org.telegram.messenger.plugins;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public final class PluginTasksCheck {
    public static void main(String[] args) throws Exception {
        PluginScope stuck = new PluginScope("test.stuck", 1);
        CountDownLatch release = new CountDownLatch(1);
        long start = System.nanoTime();
        try {
            PluginTasks.call(stuck, () -> {
                while (release.getCount() != 0) try { release.await(); } catch (InterruptedException ignored) { }
                return null;
            }, 100);
            throw new AssertionError("hung callback accepted");
        } catch (IOException expected) { assert expected.getMessage().equals("PluginRestartRequired"); }
        assert !stuck.isActive() && !stuck.awaitIdle(0) && stuck.awaitStop(0);
        assert (System.nanoTime() - start) / 1000000 < 1500 : "manager blocked";
        PluginScope healthy = new PluginScope("test.healthy", 2);
        assert !healthy.awaitStop(0);
        assert PluginTasks.call(healthy, () -> 42, 1000) == 42 : "other plugin blocked";
        release.countDown(); assert stuck.awaitIdle(1000);
        try { PluginTasks.call(healthy, () -> { throw new IOException("original"); }, 1000); }
        catch (IOException expected) { assert expected.getMessage().equals("original"); }
        PluginWork first = new PluginWork(), second = new PluginWork();
        PluginScope firstScope = new PluginScope("test.first", 3), secondScope = new PluginScope("test.second", 4);
        CountDownLatch blocked = new CountDownLatch(2), finish = new CountDownLatch(1), completed = new CountDownLatch(1);
        try {
            for (int i = 0; i < 2; i++) first.submit(firstScope, () -> {
                blocked.countDown();
                while (finish.getCount() != 0) try { finish.await(); } catch (InterruptedException ignored) { }
            });
            assert blocked.await(2, java.util.concurrent.TimeUnit.SECONDS);
            for (int i = 0; i < 32; i++) first.submit(firstScope, () -> { throw new AssertionError("cancelled task ran"); }).close();
            second.submit(secondScope, completed::countDown);
            assert completed.await(2, java.util.concurrent.TimeUnit.SECONDS) : "other plugin work starved";
            AutoCloseable queued = first.submit(firstScope, () -> { throw new AssertionError("cancelled task ran"); });
            queued.close();
        } finally { firstScope.close(); secondScope.close(); first.close(); second.close(); finish.countDown(); }
        System.out.println("PASS: per-plugin workers, bounded queues, queued cancellation frees capacity, other plugin progresses");
        PluginCallbacks callbacks = new PluginCallbacks(), otherCallbacks = new PluginCallbacks();
        PluginScope hungUi = new PluginScope("test.ui.hung", 5), healthyUi = new PluginScope("test.ui.healthy", 6);
        CountDownLatch endUi = new CountDownLatch(1), deadline = new CountDownLatch(1), otherRan = new CountDownLatch(1);
        try {
            callbacks.dispatch(hungUi, () -> {
                while (endUi.getCount() != 0) try { endUi.await(); } catch (InterruptedException ignored) { }
            }, () -> { hungUi.invalidate(); deadline.countDown(); }, 100);
            otherCallbacks.dispatch(healthyUi, otherRan::countDown, () -> { throw new AssertionError("healthy timed out"); });
            assert otherRan.await(2, java.util.concurrent.TimeUnit.SECONDS);
            assert deadline.await(2, java.util.concurrent.TimeUnit.SECONDS) && !hungUi.isActive();
        } finally { endUi.countDown(); hungUi.close(); healthyUi.close(); callbacks.close(); otherCallbacks.close(); }
        System.out.println("PASS: asynchronous callbacks, per-plugin progress, hung callback invalidated by deadline");
        CountDownLatch releaseLifecycle = new CountDownLatch(1);
        try {
            for (int i = 0; i < 4; i++) {
                PluginScope blockedLifecycle = new PluginScope("test.lifecycle." + i, i + 10);
                try {
                    PluginTasks.call(blockedLifecycle, () -> {
                        while (releaseLifecycle.getCount() != 0) try { releaseLifecycle.await(); } catch (InterruptedException ignored) { }
                        return null;
                    }, 50);
                    throw new AssertionError("lifecycle timeout missing");
                } catch (IOException expected) { assert expected.getMessage().equals("PluginRestartRequired"); }
            }
            assert PluginTasks.call(new PluginScope("test.lifecycle.healthy", 20), () -> 42, 1000) == 42;
        } finally { releaseLifecycle.countDown(); }
        System.out.println("PASS: four hung lifecycle generations do not starve a healthy plugin");
        System.out.println("PASS: stuck lifecycle bounded, scope invalidated, other plugin usable, original exception preserved");
        healthy.close();
        assert healthy.awaitStop(0);
    }
}
