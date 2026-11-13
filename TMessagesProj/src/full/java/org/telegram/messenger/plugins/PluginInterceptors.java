package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Consumer;

/** Synchronous Telegram operation hooks. Registration and cleanup belong to a plugin scope. */
public final class PluginInterceptors {
    public interface Callback {
        Object invoke(int account, String name, Object value, Object detail);
    }

    public static final class Result {
        private final Object replacement;
        private final boolean terminal;
        private Result(Object replacement, boolean terminal) {
            this.replacement = replacement;
            this.terminal = terminal;
        }
        public static Result cancel() { return new Result(null, true); }
        public static Result replace(Object value, boolean terminal) {
            if (value == null) throw new IllegalArgumentException("InvalidHookReplacement");
            return new Result(value, terminal);
        }
    }

    private static final class Hook {
        String kind, filter;
        boolean substring;
        volatile boolean closed;
        Callback callback;
        Consumer<RuntimeException> onError;
        String owner;
        int priority;
    }

    private static volatile Hook[] hooks = new Hook[0];
    private static final ThreadLocal<Boolean> dispatching = new ThreadLocal<>();

    private PluginInterceptors() {}

    static synchronized AutoCloseable add(String owner, String kind, String filter, boolean substring,
                                          int priority, Callback callback, Consumer<RuntimeException> onError) {
        Hook hook = new Hook();
        hook.owner = owner;
        hook.kind = kind;
        hook.filter = filter;
        hook.substring = substring;
        hook.priority = priority;
        hook.callback = callback;
        hook.onError = onError;
        ArrayList<Hook> entries = new ArrayList<>(java.util.Arrays.asList(hooks));
        entries.add(hook);
        entries.sort(Comparator.<Hook>comparingInt(entry -> entry.priority).reversed()
                .thenComparing(entry -> entry.owner));
        hooks = entries.toArray(new Hook[0]);
        return () -> remove(hook);
    }

    private static synchronized void remove(Hook hook) {
        hook.closed = true;
        ArrayList<Hook> entries = new ArrayList<>(java.util.Arrays.asList(hooks));
        entries.remove(hook);
        hooks = entries.toArray(new Hook[0]);
    }

    public static boolean has(String kind) {
        for (Hook hook : hooks) if (hook.kind.equals(kind)) return true;
        return false;
    }

    public static <T> T dispatch(String kind, int account, Class<?> source, Class<T> type, T value, Object detail) {
        Hook[] snapshot = hooks;
        if (snapshot.length == 0 || dispatching.get() != null) return value;
        dispatching.set(true);
        try {
            String name = source.getSimpleName();
            for (Hook hook : snapshot) {
                if (hook.closed || !hook.kind.equals(kind)) continue;
                if (!hook.filter.isEmpty() && !(hook.substring ? name.contains(hook.filter) : name.equals(hook.filter))) continue;
                try {
                    Result result = (Result) hook.callback.invoke(account, name, value, detail);
                    if (result == null) continue;
                    if (result.replacement == null) return null;
                    value = type.cast(result.replacement);
                    if (result.terminal) break;
                } catch (RuntimeException error) {
                    hook.onError.accept(error);
                }
            }
            return value;
        } finally {
            dispatching.remove();
        }
    }
}
