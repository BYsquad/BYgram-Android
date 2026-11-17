"""Run the actual preference whitelist/getters/setters against an in-memory store."""
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
source = (JAVA / "messenger/BygramConfig.java").read_text(encoding="utf-8")


def method(signature):
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


constants = "\n".join(re.findall(r'(?:public|private) static final String \w+ = "[^"]*";', source))
constants += "\n" + "\n".join(re.findall(r"public static final int SNOW_AMOUNT_\w+ = \d+;", source))
methods = "\n".join(method(s) for s in ["public static void clearRemovedCustomizationSettings(", "private static boolean isActiveSetting(", "public static boolean getBoolean(", "public static void setBoolean(", "public static int getInt(", "public static void setInt("])
bar = (JAVA / "ui/ActionBar/ActionBar.java").read_text(encoding="utf-8")
measure = "int availableWidth =" + bar.split("int availableWidth =", 1)[1].split("availableWidth = Math.max(availableWidth, 0);", 1)[0] + "availableWidth = Math.max(availableWidth, 0);"
position = "int titleLeft = textLeft;" + bar.split("int titleLeft = textLeft;", 1)[1].split("titleTextView[i].layout(", 1)[0]
harness = r'''
import java.util.*;
public class WinterSettingsCheck {
    static final class Store {
        final Map<String,Object> values = new HashMap<>();
        boolean getBoolean(String key, boolean fallback) { return (boolean)values.getOrDefault(key, fallback); }
        int getInt(String key, int fallback) { return (int)values.getOrDefault(key, fallback); }
        boolean contains(String key) { return values.containsKey(key); }
        Map<String,Object> getAll() { return new HashMap<>(values); }
        Store edit() { return this; }
        Store remove(String key) { values.remove(key); return this; }
        Store putBoolean(String key, boolean value) { values.put(key,value); return this; }
        Store putInt(String key, int value) { values.put(key,value); return this; }
        void apply() {}
    }
    static final Store store = new Store();
    static final class LocaleController {
        static final LocaleController instance = new LocaleController();
        int resets;
        static LocaleController getInstance() { return instance; }
        void recreateFormatters() { resets++; }
    }
    static Store preferences() { return store; }
    static final class UserConfig { static final int MAX_ACCOUNT_COUNT = 1; }
    static String activityEnabledKey(int account) { return "activity_" + account; }
    static boolean isActivityStorageKey(String key) { return false; }
    static final class Gravity { static final int HORIZONTAL_GRAVITY_MASK=7, CENTER_HORIZONTAL=1, RIGHT=5; }
    static final class Menu { int width; Menu(int w) { width=w; } int getMeasuredWidth() { return width; } }
    static final class SimpleTextView {
        int width;
        int getMeasuredWidth() { return width; }
    }
    static SimpleTextView[] titleTextView = {new SimpleTextView()};
    static int width;
    static boolean titleGravityInGlass;
    static int getMeasuredWidth() { return width; }
    static int dp(int n) { return n; }
    static void checkTitle() {
        int i=0, titleGravity=Gravity.CENTER_HORIZONTAL, titleRightMargin=0;
        for (width=320; width<=800; width+=120) for (int left : new int[]{24,76})
        for (int right : new int[]{0,54,108}) for (int text : new int[]{16,110,600}) {
            int textLeft=left;
            Menu menu=new Menu(right);
            boolean centerTitleAndGlass = false;
            int titleMenuWidth=menu.getMeasuredWidth();
            __MEASURE__
            titleTextView[0].width=Math.min(text,availableWidth);
            __POSITION__
            assert titleLeft == (width-titleTextView[0].width)/2 : "Title off center";
            assert titleLeft >= textLeft : "Title overlaps back button";
            assert titleLeft+titleTextView[0].width <= width-right-16 : "Title overlaps menu";
        }
    }
    __CONSTANTS__
    __METHODS__
    public static void main(String[] args) {
        checkTitle();
        for (String key : new String[]{HIDE_STORIES, HIDE_FOLDER_COUNTERS, HIDE_ALL_TAB, SNOW_CHATS, SNOW_NAVIGATION, SNOW_SCREENS, SECONDS, MD3_PROFILE_BUTTONS, SEPARATE_HEADERS}) {
            assert isActiveSetting(key) : key;
            assert !getBoolean(key) : "Default must be off: " + key;
            setBoolean(key,true);
            assert getBoolean(key) : "Did not persist: " + key;
            setBoolean(key,false);
            assert !getBoolean(key) : "Did not disable: " + key;
        }
        assert isActiveSetting(FOLDER_STYLE);
        assert getInt(FOLDER_STYLE,0) == 0;
        for (int mode : new int[]{-1,0,1,2,3,999}) {
            setInt(FOLDER_STYLE,mode);
            assert getInt(FOLDER_STYLE,0) == Math.max(0,Math.min(2,mode));
        }
        assert getInt(SNOW_AMOUNT, SNOW_AMOUNT_MAX) == 100;
        for (int amount : new int[]{-1, 10, 55, 100, 101}) {
            setInt(SNOW_AMOUNT, amount);
            assert getInt(SNOW_AMOUNT, SNOW_AMOUNT_MAX) == Math.max(10, Math.min(100, amount));
        }
        store.putInt(SNOW_AMOUNT, Integer.MAX_VALUE);
        store.putBoolean("bygram_snow_header", true);
        clearRemovedCustomizationSettings();
        assert getInt(SNOW_AMOUNT, SNOW_AMOUNT_MAX) == 100;
        assert !getBoolean(SNOW_SCREENS) && !store.contains("bygram_snow_header");
        for (String legacy : new String[]{LEGACY_SNOW_PROFILES, LEGACY_SNOW_DIALOGS, LEGACY_SNOW_SETTINGS, LEGACY_SNOW_BYGRAM_SETTINGS}) {
            store.remove(SNOW_SCREENS);
            store.putBoolean(legacy, true);
            clearRemovedCustomizationSettings();
            assert getBoolean(SNOW_SCREENS) && !store.contains(legacy) : legacy;
        }
        setBoolean(SNOW_SCREENS, false);
        store.putBoolean(LEGACY_SNOW_SETTINGS, true);
        clearRemovedCustomizationSettings();
        assert !getBoolean(SNOW_SCREENS) : "Explicit new setting wins";
        setBoolean("bygram_unknown",true);
        assert getInt(SECTION_RADIUS, -1) == -1;
        for (int radius : new int[]{-1, 0, 1, 16, 28, 29, 999}) {
            setInt(SECTION_RADIUS, radius);
            assert getInt(SECTION_RADIUS, -1) == Math.max(0, Math.min(28, radius));
        }
        assert !getBoolean("bygram_unknown");
        assert !store.values.containsKey("bygram_unknown");
        assert LocaleController.instance.resets == 2 : "Clock caches must follow both seconds toggle transitions";
        System.out.println("PASS: preference persistence, bounds and whitelist");
    }
}
'''.replace("__CONSTANTS__", constants).replace("__METHODS__", methods.replace("SharedPreferences.Editor editor", "Store editor").replace("SharedPreferences prefs", "Store prefs")).replace("__MEASURE__", measure).replace("__POSITION__", position)
with tempfile.TemporaryDirectory(prefix="bygram-winter-") as temp:
    path = Path(temp) / "WinterSettingsCheck.java"
    path.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(path)], check=True)
    subprocess.run(["java", "-ea", "-cp", temp, "WinterSettingsCheck"], check=True)
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", temp, str(JAVA / "messenger/BygramSettingsLink.java"), str(ROOT / "Tools/BygramSettingsLinkCheck.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", temp, "BygramSettingsLinkCheck"], check=True)

expected = {"BygramHideStories", "BygramFoldersAppearance", "BygramFolderStyle", "BygramFolderNames", "BygramFolderNamesIcons", "BygramFolderIcons", "BygramHideFolderCounters", "BygramHideAllTab", "BygramWinter", "BygramSnowChats", "BygramSnowScreens", "BygramSnowAmount", "BygramSnowNavigation", "BygramAppearance", "BygramChatList"}
for locale in ("values", "values-ru", "values-uk"):
    resources = ET.parse(ROOT / "TMessagesProj/src/main/res" / locale / "bygram_preferences.xml").getroot()
    names = [item.attrib["name"] for item in resources if "name" in item.attrib]
    assert len(names) == len(set(names)), f"Duplicate resources: {locale}"
    assert expected <= set(names), f"Missing translation: {locale}"
print("PASS: English, Russian and Ukrainian navigation/winter resources")
dialogs = (JAVA / "ui/DialogsActivity.java").read_text(encoding="utf-8")
assert "hideDefaultTab && !filterTabsView.hasTab(filterTabsView.getCurrentTabId())" in dialogs
assert "if (filterTabsView.getDefaultTabId() < 0)" in dialogs
print("PASS: actual title geometry (90 screen layouts), hidden active All Chats and last-folder fallback contracts")
