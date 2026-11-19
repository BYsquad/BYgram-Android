package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.Arrays;

final class PluginInterceptorsCheck {
    public static void main(String[] args) throws Exception {
        ArrayList<String> visited = new ArrayList<>();
        ArrayList<RuntimeException> failures = new ArrayList<>();
        try (AutoCloseable last = PluginInterceptors.add("a.last", "request", "", false, -1,
                    (account, name, value, detail) -> { throw new AssertionError("Terminal result ignored"); }, failures::add);
             AutoCloseable second = PluginInterceptors.add("b.second", "request", "Str", true, 0,
                    (account, name, value, detail) -> {
                        visited.add((String) value);
                        return PluginInterceptors.Result.replace("final", true);
                    }, failures::add);
             AutoCloseable first = PluginInterceptors.add("a.first", "request", "String", false, 0,
                    (account, name, value, detail) -> {
                        assert account == 3 && name.equals("String") && detail.equals("detail");
                        assert dispatch("request", "nested").equals("nested");
                        visited.add((String) value);
                        return PluginInterceptors.Result.replace("changed", false);
                    }, failures::add);
             AutoCloseable bad = PluginInterceptors.add("a.bad", "request", "", false, 1,
                    (account, name, value, detail) -> PluginInterceptors.Result.replace(42, false), failures::add);
             AutoCloseable cancel = PluginInterceptors.add("a.cancel", "send", "", false, 0,
                    (account, name, value, detail) -> PluginInterceptors.Result.cancel(), failures::add)) {
            assert dispatch("request", "original").equals("final");
            assert visited.equals(Arrays.asList("original", "changed"));
            assert failures.size() == 1 && failures.get(0) instanceof ClassCastException;
            assert dispatch("send", "original") == null;
            assert dispatch("update", "original").equals("original");
            assert PluginInterceptors.has("request");
        }
        assert !PluginInterceptors.has("request");
        assert dispatch("request", "original").equals("original");
        System.out.println("PASS: Telegram hook filters, priority, reentrancy, invalid result, cancellation and unregistration");
    }

    private static String dispatch(String kind, String value) {
        return PluginInterceptors.dispatch(kind, 3, String.class, String.class, value, "detail");
    }
}
