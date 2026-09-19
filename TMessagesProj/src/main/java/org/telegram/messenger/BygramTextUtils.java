package org.telegram.messenger;

/** Display-only filtering; UTF-16 offsets stay intact for message entities. */
public final class BygramTextUtils {
    private BygramTextUtils() {}

    public static java.util.ArrayList<String> splitTranslationText(String text, int maxUtf8Bytes) {
        if (maxUtf8Bytes < 4) throw new IllegalArgumentException("A Unicode code point needs up to four bytes");
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        int start = 0, bytes = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            int size = cp <= 0x7f ? 1 : cp <= 0x7ff ? 2 : cp <= 0xffff ? 3 : 4;
            if (bytes + size > maxUtf8Bytes) {
                parts.add(text.substring(start, i));
                start = i;
                bytes = 0;
            }
            bytes += size;
            i += Character.charCount(cp);
        }
        if (start < text.length() || text.isEmpty()) parts.add(text.substring(start));
        return parts;
    }

    public static boolean isValidSaveFolder(String name) {
        return name != null && name.matches("(?!\\.{1,2}$)[A-Za-z0-9._ -]{1,255}")
                && !name.startsWith(" ") && !name.endsWith(" ") && !name.endsWith(".");
    }

    public static long profileId(long id, boolean chat, boolean channel, boolean botApi) {
        return !botApi || !chat ? id : channel ? -1000000000000L - id : -id;
    }

    public static String filterZalgo(String text) {
        StringBuilder result = null;
        for (int i = 0; i < text.length();) {
            int start = i;
            int cp = text.codePointAt(i);
            int allowed = allowedMarks(cp);
            boolean replace;
            if (allowed > 0) {
                int count = 0;
                while (i < text.length() && allowedMarks(text.codePointAt(i)) > 0) {
                    cp = text.codePointAt(i);
                    allowed = Math.min(allowed, allowedMarks(cp));
                    count++;
                    i += Character.charCount(cp);
                }
                replace = count > allowed;
            } else {
                i += Character.charCount(cp);
                replace = cp == 0x061c || cp == 0x200e || cp == 0x200f || cp >= 0x202a && cp <= 0x202e || cp >= 0x2066 && cp <= 0x2069;
            }
            if (replace) {
                if (result == null) result = new StringBuilder(text);
                for (int j = start; j < i; j++) result.setCharAt(j, '\u2060');
            }
        }
        return result == null ? text : result.toString();
    }

    public static String calculate(String expression) {
        try {
            final String input = expression.replace(" ", "").replace(',', '.');
            final class Parser {
                int position;
                double parseExpression() {
                    double value = parseTerm();
                    while (position < input.length()) {
                        char operation = input.charAt(position);
                        if (operation != '+' && operation != '-') break;
                        position++;
                        double right = parseTerm();
                        value = operation == '+' ? value + right : value - right;
                    }
                    return value;
                }
                double parseTerm() {
                    double value = parseFactor();
                    while (position < input.length()) {
                        char operation = input.charAt(position);
                        if (operation != '*' && operation != '/') break;
                        position++;
                        double right = parseFactor();
                        value = operation == '*' ? value * right : value / right;
                    }
                    return value;
                }
                double parseFactor() {
                    if (position < input.length() && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                        return input.charAt(position++) == '-' ? -parseFactor() : parseFactor();
                    }
                    if (position < input.length() && input.charAt(position) == '(') {
                        position++;
                        double value = parseExpression();
                        if (position >= input.length() || input.charAt(position++) != ')') throw new IllegalArgumentException();
                        return value;
                    }
                    int start = position;
                    while (position < input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) position++;
                    if (start == position) throw new IllegalArgumentException();
                    return Double.parseDouble(input.substring(start, position));
                }
            }
            if (input.isEmpty()) return null;
            Parser parser = new Parser();
            double value = parser.parseExpression();
            if (parser.position != input.length() || !Double.isFinite(value)) return null;
            long integer = (long) value;
            return value == integer ? Long.toString(integer) : Double.toString(value);
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    private static int allowedMarks(int cp) {
        int type = Character.getType(cp);
        if (cp < 0x0300 || type != Character.NON_SPACING_MARK && type != Character.ENCLOSING_MARK) return 0;
        return cp <= 0x036f || cp >= 0x1ab0 && cp <= 0x1aff || cp >= 0x1dc0 && cp <= 0x1dff || cp >= 0x20d0 && cp <= 0x20ff || cp >= 0xfe20 && cp <= 0xfe2f ? 2 : 4;
    }
}
