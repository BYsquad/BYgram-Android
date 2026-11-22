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
            case "root": rows = new int[]{-1, -2, 10, 11}; break;
            case "customization": rows = new int[]{-1}; break;
            case "conversation": rows = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}; break;
            case "ai": rows = new int[]{1, 2, 3}; break;
            case "plugins": rows = new int[]{}; break;
            case "appearance": rows = new int[]{30}; break;
            case "chat_list": case "winter": rows = new int[]{}; break;
            case "general": rows = new int[]{-1, -2, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 200, 201, 202, 203}; break;
            case "profile": rows = new int[]{-1, 1, 2, 3, 4, 5, 6, 7}; break;
            case "typing": rows = new int[]{-1, 100, 101, 103, 104, 105, 106, 107, 108, 109, 110, 200}; break;
            case "ghost": rows = new int[]{-1, 1, 2, 3, 4, 5, 7}; break;
            case "spy": rows = new int[]{-1, 1, 2, 3, 4, 5, 6, 7}; break;
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
                case 14: return "plugins";
            }
        } else if ("customization".equals(page)) {
            switch (row) {
                case 1: return "profile";
                case 2: return "general";
                case 3: return "chat_list";
                case 4: return "typing";
                case 5: return "appearance";
                case 6: return "winter";
                case 7: return "conversation";
                case 8: return "ai";
            }
        }
        if (row == 0) return null;
        if ("appearance".equals(page) && row == 30) return "appearance.30";
        if ("chat_list".equals(page) || "appearance".equals(page) || "winter".equals(page)) {
            // Keep links usable on versions that still use the general.* namespace.
            BygramSettingsLink link = parse("general." + row);
            return link != null && page.equals(link.customizationSection()) ? "general." + row : null;
        }
        String target = page + "." + row;
        return parse(target) == null ? null : target;
    }

    public String customizationSection() {
        if (!"general".equals(page)) return page;
        switch (row) {
            case 4: case 5: case 6: case 7: case 8: case 11:
            case 12: case 13: case 14: case 15: case 16: case 17:
                return "chat_list";
            case 18: case 19: case 20: case 21: case 22: case 23: case 24: case 29:
                return "winter";
            case -1: case -2: case 3: case 26: case 27: case 28:
            case 200: case 201: case 202: case 203:
                return "appearance";
            default:
                return "general";
        }
    }

    public int highlightedRow() {
        if ("general".equals(page)) {
            switch (row) {
                case 19: case 23: case 24: return 22;
                case 20: return 0;
            }
        }
        return row;
    }

}
