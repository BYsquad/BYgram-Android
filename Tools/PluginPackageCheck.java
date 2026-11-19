package org.telegram.messenger.plugins;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class PluginPackageCheck {
    public static void main(String[] args) throws Exception {
        int count = 0;
        for (File fixture : Objects.requireNonNull(new File(args[0]).listFiles())) {
            if (!fixture.getName().endsWith(".zip")) continue;
            File root = Files.createTempDirectory("bygram-stage-").toFile();
            boolean accepted = false;
            try (InputStream input = new FileInputStream(fixture)) {
                PluginPackage pkg = PluginPackage.stage(input, new File(root, "staging"), 1, 72379, 7237);
                assert pkg.sha256.matches("[a-f0-9]{64}");
                accepted = true;
            } catch (IOException | PluginManifest.ValidationException expected) {
                if (fixture.getName().startsWith("valid")) throw expected;
            } finally { PluginPackage.delete(root); }
            assert accepted == fixture.getName().startsWith("valid") : fixture.getName();
            count++;
        }
        List<Integer> order = new ArrayList<>();
        PluginScope scope = new PluginScope("test.plugin", 1);
        scope.own(() -> order.add(1)); scope.own(() -> { order.add(2); throw new IOException(); });
        scope.own(() -> order.add(3)); scope.close(); scope.close();
        assert order.equals(Arrays.asList(3, 2, 1));
        assert !scope.isActive();
        try { scope.own(() -> order.add(4)); throw new AssertionError(); }
        catch (IllegalStateException expected) { assert order.contains(4); }
        try { scope.own(() -> { assert !Thread.holdsLock(scope) : "cleanup ran under scope monitor"; }); throw new AssertionError(); }
        catch (IllegalStateException expected) { }
        PluginScope callback = new PluginScope("test.plugin", 2);
        assert callback.beginCallback(); callback.invalidate();
        assert !callback.beginCallback(); assert !callback.awaitIdle(1);
        callback.endCallback(); assert callback.awaitIdle(1); callback.close();
        PluginScope cycling = new PluginScope("test.plugin", 3);
        for (int i = 0; i < 2000; i++) cycling.own(() -> { }).close();
        cycling.close();
        PluginScope full = new PluginScope("test.plugin", 4);
        for (int i = 0; i < 1024; i++) full.own(() -> { });
        try { full.own(() -> { assert !Thread.holdsLock(full) : "limit cleanup ran under scope monitor"; }); throw new AssertionError(); }
        catch (IllegalStateException expected) { assert expected.getMessage().equals("HandleLimitExceeded"); }
        full.close();
        System.out.println("PASS: " + count + " ZIP fixtures; generation invalidation and reverse cleanup including errors");
    }
}
