import org.telegram.messenger.BygramTextUtils;

public class BygramTextCheck {
    public static void main(String[] args) {
        for (String value : new String[]{"", "😀".repeat(2000), "я".repeat(5000), "a b\n😀".repeat(900)}) {
            java.util.ArrayList<String> parts = BygramTextUtils.splitTranslationText(value, 1500);
            if (!String.join("", parts).equals(value)) throw new AssertionError("Translation split lost text");
            for (String part : parts) {
                if (part.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1500
                        || !part.isEmpty() && Character.isHighSurrogate(part.charAt(part.length() - 1)))
                    throw new AssertionError("Translation split broke Unicode or byte limit");
            }
        }
        if (BygramTextUtils.profileId(123, false, false, true) != 123
                || BygramTextUtils.profileId(123, true, false, true) != -123
                || BygramTextUtils.profileId(123, true, true, true) != -1000000000123L
                || BygramTextUtils.profileId(123, true, true, false) != 123) throw new AssertionError("Peer ID formats");
        for (String name : new String[]{"BYgram", "My files", "photos_2026-09"}) {
            if (!BygramTextUtils.isValidSaveFolder(name)) throw new AssertionError(name);
        }
        for (String name : new String[]{"", ".", "..", "../outside", "a/b", "a\\b", "a\n", " ", "folder.", "a".repeat(256)}) {
            if (BygramTextUtils.isValidSaveFolder(name)) throw new AssertionError(name);
        }
        for (String text : new String[]{"Привет", "café", "e\u0301", "x\u0301\u0302", "👨‍👩‍👧‍👦", "العربية"}) {
            if (!text.equals(BygramTextUtils.filterZalgo(text))) throw new AssertionError(text);
        }
        String text = "😀Z\u0301\u0302\u0303 text";
        String clean = BygramTextUtils.filterZalgo(text);
        if (!clean.equals("😀Z\u2060\u2060\u2060 text") || clean.length() != text.length()) throw new AssertionError(clean);
        if (!BygramTextUtils.filterZalgo("a\u202eb").equals("a\u2060b")) throw new AssertionError("Bidi control");
        if (!"14".equals(BygramTextUtils.calculate("2+3*4"))
                || !"20".equals(BygramTextUtils.calculate("(2+3)*4"))
                || !"2.5".equals(BygramTextUtils.calculate("5/2"))
                || BygramTextUtils.calculate("2+/3") != null) throw new AssertionError("Calculator");
        System.out.println("PASS: translation UTF-8 chunks, ordinary text, accents, emoji, Zalgo, entity offsets, bidi controls");
    }
}
