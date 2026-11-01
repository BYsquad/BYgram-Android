import org.telegram.messenger.BygramSettingsLink;

public class BygramSettingsLinkCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check("root".equals(BygramSettingsLink.parse(null).page), "Missing target opens overview");
        check("root".equals(BygramSettingsLink.parse("").page), "Empty target opens overview");
        String[] pages = {"root", "customization", "general", "profile", "typing", "ghost",
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
        check("hidden".equals(BygramSettingsLink.target("root", 5)), "PIN-protected category");
        check(BygramSettingsLink.parse("activity.800").requiresFullEdition(), "Activity requires Full");
        check(BygramSettingsLink.parse("typing.100").requiresFullEdition(), "Typing requires Full");
        check(!BygramSettingsLink.parse("ghost.2").requiresFullEdition(), "Ghost works in Lite");
        check(BygramSettingsLink.parse("general.202").row == 202, "Collapsed MD3 destination");
        for (int row : new int[]{22, 23, 24, 25, 26, 27, 203}) {
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
