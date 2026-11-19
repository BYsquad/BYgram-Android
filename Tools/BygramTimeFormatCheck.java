import org.telegram.messenger.BygramTimeFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class BygramTimeFormatCheck {
    public static void main(String[] args) {
        String[][] patterns = {{"HH:mm", "HH:mm:ss"}, {"h:mm a", "h:mm:ss a"},
                {"HH:mm:ss", "HH:mm:ss"}, {"'Summer mm' HH:mm", "'Summer mm' HH:mm:ss"},
                {"'o''clock' h:mm a", "'o''clock' h:mm:ss a"}, {"dd MMM yyyy", "dd MMM yyyy"}};
        for (String[] pattern : patterns) {
            assert BygramTimeFormat.withSeconds(pattern[0]).equals(pattern[1]) : pattern[0];
            assert BygramTimeFormat.withSeconds(pattern[1]).equals(pattern[1]) : "Not idempotent";
        }
        SimpleDateFormat format = new SimpleDateFormat(BygramTimeFormat.withSeconds("HH:mm"), Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        assert format.format(new Date((12 * 3600 + 34 * 60 + 56) * 1000L)).equals("12:34:56");
        System.out.println("PASS: seconds, 12/24-hour clocks, localized quotes, existing seconds and dates");
    }
}
