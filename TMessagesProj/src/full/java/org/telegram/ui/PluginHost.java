package org.telegram.ui;

import com.chaquo.python.PyObject;
import com.google.gson.*;
import org.telegram.messenger.*;
import org.telegram.messenger.plugins.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.BulletinFactory;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

public final class PluginHost {
    private static volatile WeakReference<BaseFragment> current = new WeakReference<>(null);
    private static final int MENU_ID = 900001;
    private static final CopyOnWriteArrayList<Contribution> menus = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Subscription> events = new CopyOnWriteArrayList<>();
    private static final ArrayList<WeakReference<ActionBarMenuItem>> buttons = new ArrayList<>();
    private static final Map<Integer, TreeMap<String, Integer>> overrides = new HashMap<>();
    private static volatile Map<Integer, Integer> colors = Collections.emptyMap();
    private static final Map<BaseFragment, Integer> themedPages = new WeakHashMap<>();
    private static int themeRevision;
    private static final Runnable refreshSettings = () -> {
        BaseFragment page = current.get();
        if (page instanceof BygramPluginsActivity.PluginPage)
            ((BygramPluginsActivity.PluginPage) page).refreshSettings();
    };
    public static void settingsChanged() {
        if (!(current.get() instanceof BygramPluginsActivity.PluginPage)) return;
        AndroidUtilities.cancelRunOnUIThread(refreshSettings);
        AndroidUtilities.runOnUIThread(refreshSettings);
    }
    private static final Runnable refreshTheme = () -> {
        Theme.applyCommonTheme(); Theme.applyDialogsTheme(); Theme.applyProfileTheme(); Theme.applyChatTheme(false, false);
        themeRevision++;
        for (BaseFragment fragment : new ArrayList<>(themedPages.keySet())) refreshPageTheme(fragment);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, true);
        emit("theme.changed", "{}");
    };

    private static final class Contribution {
        PluginContext context; String id, location, title; PyObject callback;
    }
    private static final class Subscription { PluginContext context; String name; PyObject callback; }
    public static void bulletin(PluginContext context, String text) {
        AndroidUtilities.runOnUIThread(() -> {
            BaseFragment fragment = current.get();
            if (context.active() && fragment != null && fragment.getParentActivity() != null)
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, text).show();
        });
    }
    public static synchronized AutoCloseable menu(PluginContext context, String location, String id,
                                                   String title, PyObject callback) {
        if (!Arrays.asList("dialogs.toolbar", "profile.actions", "chat.actions").contains(location)
                || !id.matches("[A-Za-z_][A-Za-z0-9_.-]{0,119}") || title.isEmpty() || title.length() > 120)
            throw new IllegalArgumentException("InvalidContribution");
        for (Contribution item : menus) if (item.context.id().equals(context.id()) && item.id.equals(id))
            throw new IllegalStateException("ContributionConflict");
        Contribution item = new Contribution(); item.context = context; item.id = id;
        item.location = location; item.title = title; item.callback = callback; menus.add(item);
        AndroidUtilities.runOnUIThread(PluginHost::refreshMenu);
        return () -> { menus.remove(item); AndroidUtilities.runOnUIThread(PluginHost::refreshMenu); };
    }
    public static void pageEvent(BaseFragment page, boolean opened) {
        if (opened) { themedPages.putIfAbsent(page, colors.isEmpty() ? themeRevision : -1); current = new WeakReference<>(page); refreshPageTheme(page); refreshMenu(); }
        else if (current.get() == page) current.clear();
        if (events.isEmpty()) return;
        JsonObject event = new JsonObject();
        event.addProperty("page", pageName(page)); event.addProperty("account", page.getCurrentAccount());
        emit(opened ? "ui.page_opened" : "ui.page_closed", event.toString());
    }
    private static void refreshPageTheme(BaseFragment fragment) {
        if (fragment.getFragmentView() == null) return;
        if (Objects.equals(themedPages.get(fragment), themeRevision)) return;
        themedPages.put(fragment, themeRevision);
        ArrayList<ThemeDescription> descriptions = fragment.getThemeDescriptions();
        if (descriptions != null) for (ThemeDescription description : descriptions)
            description.setColor(Theme.getColor(description.getCurrentKey()), false, false);
        invalidateTree(fragment.getFragmentView());
    }
    private static void invalidateTree(android.view.View view) {
        if (view instanceof Theme.Colorable) ((Theme.Colorable) view).updateColors();
        if (view instanceof androidx.recyclerview.widget.RecyclerView) {
            androidx.recyclerview.widget.RecyclerView list = (androidx.recyclerview.widget.RecyclerView) view;
            list.getRecycledViewPool().clear();
            if (list.getAdapter() instanceof org.telegram.ui.Components.UniversalAdapter) {
                ((org.telegram.ui.Components.UniversalAdapter) list.getAdapter()).updateColors();
            }
        }
        view.invalidate();
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) invalidateTree(group.getChildAt(i));
        }
    }
    private static String location(BaseFragment fragment) {
        if (fragment instanceof DialogsActivity) return "dialogs.toolbar";
        if (fragment instanceof ProfileActivity) return "profile.actions";
        if (fragment instanceof ChatActivity) return "chat.actions";
        return "";
    }
    private static String pageName(BaseFragment page) {
        if (page instanceof ChatActivity) return "ChatActivity";
        if (page instanceof DialogsActivity) return "DialogsActivity";
        if (page instanceof ProfileActivity) return "ProfileActivity";
        if (page instanceof SettingsActivity) return "SettingsActivity";
        if (page instanceof BygramPluginsActivity.PluginPage) return "PluginPage";
        if (page instanceof BygramPluginsActivity) return "BygramPluginsActivity";
        return "Other";
    }
    private static void refreshMenu() {
        if (menus.isEmpty()) {
            buttons.removeIf(reference -> reference.get() == null);
            for (WeakReference<ActionBarMenuItem> reference : buttons) {
                ActionBarMenuItem item = reference.get();
                if (item != null) item.setVisibility(android.view.View.GONE);
            }
            return;
        }
        BaseFragment fragment = current.get();
        if (fragment == null || fragment.getActionBar() == null) return;
        List<Contribution> available = new ArrayList<>();
        for (Contribution item : menus) if (item.context.active() && item.location.equals(location(fragment))) available.add(item);
        ActionBarMenu menu = fragment.getActionBar().createMenu();
        ActionBarMenuItem button = menu.getItem(MENU_ID);
        if (button == null && !available.isEmpty()) {
            button = menu.addItem(MENU_ID, R.drawable.settings_account);
            buttons.add(new WeakReference<>(button));
            button.setContentDescription(LocaleController.getString(R.string.BygramPlugins));
        }
        if (button == null) return;
        button.setVisibility(available.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        button.setOnClickListener(view -> {
            if (fragment.getParentActivity() == null) return;
            List<Contribution> clicked = new ArrayList<>();
            for (Contribution item : menus) if (item.context.active() && item.location.equals(location(fragment))) clicked.add(item);
            CharSequence[] titles = new CharSequence[clicked.size()];
            for (int i = 0; i < titles.length; i++) titles[i] = clicked.get(i).title;
            fragment.showDialog(new AlertDialog.Builder(fragment.getContext())
                    .setTitle(LocaleController.getString(R.string.BygramPlugins)).setItems(titles, (dialog, index) -> {
                        Contribution item = clicked.get(index);
                        JsonObject event = new JsonObject(); event.addProperty("account", fragment.getCurrentAccount());
                        long peer = fragment instanceof ChatActivity ? ((ChatActivity) fragment).getDialogId()
                                : fragment instanceof ProfileActivity ? ((ProfileActivity) fragment).getDialogId() : 0;
                        if (peer != 0 && item.context.dialogAvailable(fragment.getCurrentAccount(), peer)) {
                            event.addProperty("dialog_id", peer);
                            event.addProperty("topic_id", fragment instanceof ChatActivity ? ((ChatActivity) fragment).getTopicId()
                                    : ((ProfileActivity) fragment).getTopicId());
                        }
                        item.context.call(item.callback, event.toString());
                    }).create());
        });
    }
    public static void openPage(PluginContext context, String json) {
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64 * 1024) throw new IllegalArgumentException("PageLimitExceeded");
        JsonObject specification = JsonParser.parseString(json).getAsJsonObject();
        AndroidUtilities.runOnUIThread(() -> {
            BaseFragment fragment = current.get();
            if (!context.active() || fragment == null || fragment.getParentLayout() == null) {
                context.releasePage(specification); return;
            }
            BygramPluginsActivity.PluginPage page = new BygramPluginsActivity.PluginPage(context, specification);
            WeakReference<BygramPluginsActivity.PluginPage> reference = new WeakReference<>(page);
            page.setCurrentAccount(fragment.getCurrentAccount());
            try {
                AutoCloseable handle = context.scope.own(() -> AndroidUtilities.runOnUIThread(() -> {
                    BygramPluginsActivity.PluginPage opened = reference.get();
                    if (opened != null && !opened.closed && opened.getParentLayout() != null) opened.finishFragment();
                }));
                page.closeHandle = handle;
                if (!fragment.presentFragment(page)) page.onFragmentDestroy();
            } catch (Exception e) { page.onFragmentDestroy(); context.failed(e); }
        });
    }
    private static ChatActivity composer(PluginContext context, int slot, String identity, long peer) {
        context.account(slot, identity);
        BaseFragment fragment = current.get();
        if (!(fragment instanceof ChatActivity) || fragment.getCurrentAccount() != slot || !context.dialogAvailable(slot, peer))
            throw new IllegalStateException("ChatUnavailable");
        ChatActivity chat = (ChatActivity) fragment;
        if (chat.getDialogId() != peer || chat.getChatMode() != 0 || chat.isReport()
                || chat.getChatActivityEnterView() == null || chat.getChatActivityEnterView().getEditField() == null
                || !chat.getChatActivityEnterView().getEditField().isEnabled() || chat.getChatActivityEnterView().isRecordingAudioVideo()
                || chat.getChatActivityEnterView().isEditingMessage() || chat.getChatActivityEnterView().isEditingCaption()
                || chat.getChatActivityEnterView().hasAudioToSend()) throw new IllegalStateException("ChatUnavailable");
        return chat;
    }
    public static String draftText(PluginContext context, int slot, String identity, long peer) {
        CharSequence text = composer(context, slot, identity, peer).getChatActivityEnterView().getFieldText();
        return text == null ? "" : text.toString();
    }
    public static void previewText(PluginContext context, int slot, String identity, long peer, String text) throws Exception {
        ChatActivity chat = composer(context, slot, identity, peer);
        if (chat.getParentActivity() == null) throw new IllegalStateException("ChatUnavailable");
        String previous = draftText(context, slot, identity, peer);
        long topic = chat.getTopicId();
        AlertDialog dialog = new AlertDialog.Builder(chat.getContext())
                .setTitle(LocaleController.getString(R.string.BygramPluginDraftPreview))
                .setMessage(text.isEmpty() ? "—" : text)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.OK), (d, which) -> {
                    try {
                        if (composer(context, slot, identity, peer) != chat || chat.getTopicId() != topic
                                || !draftText(context, slot, identity, peer).equals(previous))
                            throw new IllegalStateException("DraftChanged");
                        chat.getChatActivityEnterView().setFieldText(text);
                    } catch (IllegalStateException unavailable) {
                        bulletin(context, LocaleController.getString(R.string.BygramPluginDraftChanged));
                    }
                }).create();
        AutoCloseable handle = context.scope.own(() -> AndroidUtilities.runOnUIThread(dialog::dismiss));
        if (chat.showDialog(dialog, d -> { try { handle.close(); } catch (Exception ignored) { } }) == null) handle.close();
    }
    public static AutoCloseable subscribe(PluginContext context, String name, PyObject callback) {
        if (!Arrays.asList("ui.page_opened", "ui.page_closed", "theme.changed", "account.added", "account.logged_out", "network.completed").contains(name))
            throw new IllegalArgumentException("UnknownEvent");
        Subscription item = new Subscription(); item.context = context; item.name = name; item.callback = callback;
        events.add(item);
        return () -> events.remove(item);
    }
    public static void emit(String name, String json) {
        emit(null, name, json);
    }
    public static void emit(PluginContext owner, String name, String json) {
        for (Subscription item : events) if (item.name.equals(name) && (owner == null || item.context == owner))
            item.context.call(item.callback, json);
    }
    public static void emitAccount(String name, String identity, String json) {
        settingsChanged();
        for (Subscription item : events) if (item.name.equals(name) && item.context.accountEventAllowed(identity))
            item.context.call(item.callback, json);
    }
    public static Integer color(int key) { return colors.get(key); }
    public static synchronized AutoCloseable theme(PluginContext context, String key, int color) {
        int id = ThemeColors.stringKeyToInt(key);
        if (id < 0) throw new IllegalArgumentException("UnsupportedThemeKey");
        TreeMap<String, Integer> owners = overrides.computeIfAbsent(id, unused -> new TreeMap<>());
        if (!owners.isEmpty() && !owners.containsKey(context.id())) throw new IllegalStateException("ContributionConflict");
        if (owners.containsKey(context.id())) throw new IllegalStateException("ContributionConflict");
        owners.put(context.id(), color); publishColors();
        return () -> { synchronized (PluginHost.class) { owners.remove(context.id()); publishColors(); } };
    }
    private static void publishColors() {
        Map<Integer, Integer> next = new HashMap<>();
        for (Map.Entry<Integer, TreeMap<String, Integer>> item : overrides.entrySet())
            if (!item.getValue().isEmpty()) next.put(item.getKey(), item.getValue().firstEntry().getValue());
        colors = next;
        AndroidUtilities.cancelRunOnUIThread(refreshTheme);
        AndroidUtilities.runOnUIThread(refreshTheme);
    }
}
