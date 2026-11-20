import org.telegram.messenger.BygramSettingsLink;

public class BygramSettingsLinkCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check("root".equals(BygramSettingsLink.parse(null).page), "Missing target opens overview");
        check("root".equals(BygramSettingsLink.parse("").page), "Empty target opens overview");
        String[] pages = {"root", "customization", "general", "chat_list", "appearance", "winter", "profile", "typing", "ghost",
                "spy", "hidden", "protected", "profile_views", "activity", "other", "plugins"};
        int count = 0;
        for (String page : pages) {
            check(BygramSettingsLink.parse(page) != null, "Page: " + page);
            for (int row = -2; row <= 900; row++) {
                String target = BygramSettingsLink.target(page, row);
                if (target == null) continue;
                check(BygramSettingsLink.parse(target) != null, "Generated target: " + target);
                count++;
            }
        }
        for (String value : new String[]{"unknown", "ghost.0", "ghost.6", "ghost.02", "ghost.+2",
                "ghost.2.3", "ghost.", "ghost.2147483648", "activity.100000", "hidden.100", "root.100",
                "../ghost", "Ghost", "general.-3"}) {
            check(BygramSettingsLink.parse(value) == null, "Reject: " + value);
        }
        check("general".equals(BygramSettingsLink.target("customization", 2)), "Nested category");
        check("chat_list".equals(BygramSettingsLink.target("customization", 3)), "Chat list category");
        check("appearance".equals(BygramSettingsLink.target("customization", 5)), "Appearance category");
        check("winter".equals(BygramSettingsLink.target("customization", 6)), "Winter category");
        for (String page : new String[]{"chat_list", "appearance", "winter"}) {
            int generated = 0;
            for (int row = -2; row <= 203; row++) {
                String target = BygramSettingsLink.target(page, row);
                BygramSettingsLink legacy = BygramSettingsLink.parse("general." + row);
                if (legacy != null && page.equals(legacy.customizationSection())) {
                    check(("general." + row).equals(target), "Preserve row link for " + page + ": " + row);
                    generated++;
                } else {
                    check(target == null, "Do not generate another section's row link: " + page + ", " + row);
                }
            }
            check(generated > 0, "Section must have shareable rows: " + page);
        }
        check("chat_list".equals(BygramSettingsLink.parse("general.8").customizationSection()), "Moved archive setting");
        check("appearance".equals(BygramSettingsLink.parse("general.202").customizationSection()), "Moved MD3 setting");
        for (int row : new int[]{19, 22, 23, 24}) {
            BygramSettingsLink link = BygramSettingsLink.parse("general." + row);
            check("winter".equals(link.customizationSection()) && link.highlightedRow() == 22, "Merged snow option: " + row);
        }
        BygramSettingsLink removed = BygramSettingsLink.parse("general.20");
        check("winter".equals(removed.customizationSection()) && removed.highlightedRow() == 0, "Removed snow option");
        BygramSettingsLink slider = BygramSettingsLink.parse("general.29");
        check("winter".equals(slider.customizationSection()) && slider.highlightedRow() == 29, "Snow slider");
        check("hidden".equals(BygramSettingsLink.target("root", 5)), "PIN-protected category");
        check(BygramSettingsLink.parse("general.202").row == 202, "Collapsed MD3 destination");
        for (int row : new int[]{22, 23, 24, 25, 26, 27, 28, 203}) {
            check(BygramSettingsLink.parse("general." + row) != null, "Extended customization destination");
        }
        for (String target : new String[]{"general.8", "general.9", "general.12", "general.13", "general.14", "general.15", "general.16", "general.17", "general.18", "general.19", "general.20", "general.21", "profile.3", "profile.4", "profile.7"}) {
            check(BygramSettingsLink.parse(target) != null, "Customization destination: " + target);
        }
        check(BygramSettingsLink.target("activity", 100_000) == null, "Never share a selected viewer index");
        check(BygramSettingsLink.target("hidden", 100) == null, "Never share a hidden chat index");
        System.out.println("PASS: " + count + " generated setting/category links and invalid/private targets");
    }
}
