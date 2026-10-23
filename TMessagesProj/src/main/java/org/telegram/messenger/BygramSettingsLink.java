package org.telegram.messenger;

/** Public setting IDs must stay stable when rows move or translations change. */
public final class BygramSettingsLink {
    public static final String PREFIX = "https://t.me/BYsettings?s=";
    public final String page;
    public final int row;

    private BygramSettingsLink(String page, int row) {
        this.page = page;
        this.row = row;
    }

    public static BygramSettingsLink parse(String value) {
        if (value == null || value.isEmpty()) return new BygramSettingsLink("root", 0);
        String[] parts = value.split("\\.", -1);
        if (parts.length > 2) return null;
        int row = 0;
        if (parts.length == 2) {
            try { row = Integer.parseInt(parts[1]); }
            catch (NumberFormatException e) { return null; }
            if (!Integer.toString(row).equals(parts[1]) || row == 0) return null;
        }
        int[] rows;
        switch (parts[0]) {
            case "root": rows = new int[]{-1, -2, 10, 11, 12}; break;
            case "customization": rows = new int[]{-1}; break;
            case "general": rows = new int[]{-1, -2, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 200, 201, 202}; break;
            case "profile": rows = new int[]{-1, 1, 2, 3, 4, 5, 6}; break;
            case "typing": rows = new int[]{-1, 100, 101, 103, 104, 105, 106, 107, 108, 109, 110, 200}; break;
            case "ghost": rows = new int[]{-1, 1, 2, 3, 4, 5, 7}; break;
            case "spy": rows = new int[]{-1, 1, 2, 3, 4, 5, 6}; break;
            case "hidden": rows = new int[]{-1, 1}; break;
            case "protected": rows = new int[]{-1}; break;
            case "profile_views": rows = new int[]{-1, 1}; break;
            case "activity": rows = new int[]{-1, -2, 800, 900}; break;
            case "other": rows = new int[]{-1, 1, 2, 4, 5}; break;
            default: return null;
        }
        if (row != 0) {
            boolean found = false;
            for (int id : rows) if (id == row) found = true;
            if (!found) return null;
        }
        return new BygramSettingsLink(parts[0], row);
    }

    public static String target(String page, int row) {
        if ("root".equals(page)) {
            switch (row) {
                case 1: return "ghost";
                case 2: return "spy";
                case 3: return "activity";
                case 5: return "hidden";
                case 6: return "protected";
                case 8: return "other";
                case 9: return "customization";
                case 13: return "profile_views";
            }
        } else if ("customization".equals(page)) {
            switch (row) {
                case 1: return "profile";
                case 2: return "general";
                case 4: return "typing";
            }
        }
        String target = page + "." + row;
        return row == 0 || parse(target) == null ? null : target;
    }

    public boolean requiresFullEdition() {
        return "activity".equals(page) || "typing".equals(page);
    }

}
