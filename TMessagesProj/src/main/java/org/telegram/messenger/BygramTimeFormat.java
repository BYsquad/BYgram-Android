package org.telegram.messenger;

/** Adds clock precision without rewriting quoted words in localized date patterns. */
public final class BygramTimeFormat {
    private BygramTimeFormat() {}

    public static String withSeconds(String pattern) {
        StringBuilder result = new StringBuilder(pattern.length() + 3);
        boolean quoted = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') quoted = !quoted;
            result.append(c);
            if (!quoted && c == 'm' && i + 1 < pattern.length() && pattern.charAt(i + 1) == 'm'
                    && (i == 0 || pattern.charAt(i - 1) != 'm')
                    && (i + 2 == pattern.length() || pattern.charAt(i + 2) != 'm')) {
                result.append('m');
                i++;
                if (!pattern.startsWith(":ss", i + 1)) result.append(":ss");
            }
        }
        return result.toString();
    }
}
