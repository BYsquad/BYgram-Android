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
        String subtext;
        int icon, priority;
        boolean compatibility;
        PyObject condition;
    }
    private static final class Subscription { PluginContext context; String name; PyObject callback; }
    private static final class IntentHandler {
        final PluginContext owner;
        final boolean before;
        final int priority;
        final PyObject callback;
        IntentHandler(PluginContext owner, boolean before, int priority, PyObject callback) {
            this.owner = owner; this.before = before; this.priority = priority; this.callback = callback;
        }
    }
    private static final java.util.concurrent.CopyOnWriteArrayList<IntentHandler> intentHandlers = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static AutoCloseable intentHandler(PluginContext owner, boolean before, int priority, PyObject callback) {
        IntentHandler handler = new IntentHandler(owner, before, priority, callback);
        intentHandlers.add(handler);
        intentHandlers.sort(Comparator.comparingInt(entry -> entry.priority));
        return () -> intentHandlers.remove(handler);
    }
    public static void dispatchIntent(android.content.Intent intent, int account, boolean before) {
        if (intentHandlers.isEmpty() || !android.content.Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        String identity;
        try { identity = PluginContext.currentIdentity(account); }
        catch (IllegalStateException | IllegalArgumentException unavailable) { return; }
        for (IntentHandler handler : intentHandlers) {
            if (handler.before != before || !handler.owner.accountAvailable(account, identity)) continue;
            PluginContext owner = handler.owner;
            if (!owner.scope.beginCallback()) continue;
            try { handler.callback.call(new android.content.Intent(intent), account); }
            catch (Exception error) { owner.failed(error); }
            finally { owner.scope.endCallback(); }
        }
    }
    public static BaseFragment currentFragment() {
        return current.get();
    }
    public static void bulletin(PluginContext context, String text, String type) {
        AndroidUtilities.runOnUIThread(() -> {
            BaseFragment fragment = current.get();
            if (!context.active() || fragment == null || fragment.getParentActivity() == null) return;
            BulletinFactory factory = BulletinFactory.of(fragment);
            if ("error".equals(type)) factory.createErrorBulletin(text).show();
            else if ("success".equals(type)) factory.createSuccessBulletin(text).show();
            else factory.createSimpleBulletin(R.raw.info, text).show();
        });
    }
    public static final class PluginAlert implements AutoCloseable {
        private final AutoCloseable registration;
        private volatile boolean closed;
        private AlertDialog dialog;
        private int progress;

        PluginAlert(PluginContext owner) throws Exception {
            registration = owner.scope.own(() -> {
                closed = true;
                AndroidUtilities.runOnUIThread(() -> { if (dialog != null) dialog.dismiss(); dialog = null; });
            });
        }
        @Override public void close() throws Exception { registration.close(); }
        public void setCancellation(boolean cancelable, boolean outside) {
            AndroidUtilities.runOnUIThread(() -> {
                if (dialog == null) return;
                dialog.setCanceledOnTouchOutside(cancelable && outside);
                dialog.setCancelable(cancelable);
            });
        }

        public void setProgress(int value) {
            int bounded = Math.max(0, Math.min(100, value));
            AndroidUtilities.runOnUIThread(() -> {
                progress = bounded;
                if (dialog != null) dialog.setProgress(progress);
            });
        }
    }
    public static PluginAlert alert(PluginContext context, JsonObject spec, android.view.View view, PyObject callback) throws Exception {
        int style = spec.get("style").getAsInt();
        if (style != AlertDialog.ALERT_TYPE_MESSAGE && style != AlertDialog.ALERT_TYPE_LOADING && style != AlertDialog.ALERT_TYPE_SPINNER)
            throw new IllegalArgumentException("InvalidDialogStyle");
        if (spec.has("items") && spec.getAsJsonArray("items").size() > 100) throw new IllegalArgumentException("PageLimitExceeded");
        PluginAlert handle = new PluginAlert(context);
        AndroidUtilities.runOnUIThread(() -> {
            BaseFragment fragment = current.get();
            try {
                if (handle.closed || !context.active() || fragment == null || fragment.getParentActivity() == null) {
                    handle.close();
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext(), style, fragment.getResourceProvider());
                AlertDialog.OnButtonClickListener click = callback == null ? null
                        : (dialog, which) -> context.call(callback, which);
                if (spec.has("title")) builder.setTitle(spec.get("title").getAsString());
                if (spec.has("message")) builder.setMessage(spec.get("message").getAsString());
                if (spec.has("messageClickable")) builder.setMessageTextViewClickable(spec.get("messageClickable").getAsBoolean());
                if (spec.has("dim")) builder.setDimEnabled(spec.get("dim").getAsBoolean());
                if (spec.has("buttonColorKey")) builder.setDialogButtonColorKey(spec.get("buttonColorKey").getAsInt());
                if (spec.has("positive")) builder.setPositiveButton(spec.get("positive").getAsString(), click);
                if (spec.has("negative")) builder.setNegativeButton(spec.get("negative").getAsString(), click);
                if (spec.has("neutral")) builder.setNeutralButton(spec.get("neutral").getAsString(), click);
                if (spec.has("items")) {
                    JsonArray entries = spec.getAsJsonArray("items");
                    CharSequence[] items = new CharSequence[entries.size()];
                    for (int i = 0; i < items.length; i++) items[i] = entries.get(i).getAsString();
                    int[] icons = null;
                    if (spec.has("icons")) {
                        JsonArray resources = spec.getAsJsonArray("icons");
                        if (resources.size() != items.length) throw new IllegalArgumentException("InvalidDialogIcons");
                        icons = new int[items.length];
                        for (int i = 0; i < icons.length; i++) icons[i] = resources.get(i).getAsInt();
                    }
                    builder.setItems(items, icons, callback == null ? null : (dialog, which) -> context.call(callback, which));
                }
                if (view != null) builder.setView(view, spec.has("viewHeight") ? spec.get("viewHeight").getAsInt() : -2);
                if (callback != null) {
                    builder.setOnBackButtonListener((dialog, which) -> context.call(callback, -4));
                    builder.setOnCancelListener(dialog -> context.call(callback, -5));
                }
                AlertDialog dialog = builder.create();
                handle.dialog = dialog;
                if (style == AlertDialog.ALERT_TYPE_LOADING) dialog.setProgress(handle.progress);
                if (fragment.showDialog(dialog, dismissed -> {
                    try { handle.close(); } catch (Exception ignored) { }
                    if (callback != null) context.call(callback, -6);
                }) == null) handle.close();
                else handle.setCancellation(!spec.has("cancelable") || spec.get("cancelable").getAsBoolean(),
                        !spec.has("outside") || spec.get("outside").getAsBoolean());
            } catch (Exception error) {
                try { handle.close(); } catch (Exception ignored) { }
                context.failed(error);
            }
        });
        return handle;
    }
    public static synchronized AutoCloseable menu(PluginContext context, String location, String id,
                                                   String title, PyObject callback) {
        return registerMenu(context, location, id, title, null, null, 0, false, callback, null);
    }
    public static synchronized AutoCloseable menu(PluginContext context, String location, String id, String title,
                                                   String icon, String subtext, int priority, PyObject callback, PyObject condition) {
        return registerMenu(context, location, id, title, icon, subtext, priority, true, callback, condition);
    }
    private static AutoCloseable registerMenu(PluginContext context, String location, String id, String title,
                                              String icon, String subtext, int priority, boolean compatibility, PyObject callback, PyObject condition) {
        if (!Arrays.asList("dialogs.toolbar", "profile.actions", "chat.actions", "message.context", "drawer.menu").contains(location)
                || !id.matches("[A-Za-z_][A-Za-z0-9_.-]{0,119}") || title.isEmpty() || title.length() > 120)
            throw new IllegalArgumentException("InvalidContribution");
        for (Contribution item : menus) if (item.context.id().equals(context.id()) && item.id.equals(id))
            throw new IllegalStateException("ContributionConflict");
        Contribution item = new Contribution(); item.context = context; item.id = id;
        item.location = location; item.title = title; item.callback = callback;
        item.compatibility = compatibility; item.priority = priority;
        item.condition = condition;
        item.subtext = subtext == null || subtext.isEmpty() ? null : subtext;
        if (icon != null && icon.matches("[a-z0-9_]{1,100}")) {
            android.content.Context app = ApplicationLoader.applicationContext;
            item.icon = app.getResources().getIdentifier(icon, "drawable", app.getPackageName());
        }
        menus.add(item);
        AndroidUtilities.runOnUIThread(PluginHost::refreshMenu);
        return () -> { menus.remove(item); AndroidUtilities.runOnUIThread(PluginHost::refreshMenu); };
    }
    public static void addDrawerItems(BaseFragment fragment, org.telegram.ui.Components.ItemOptions options) {
        List<Contribution> available = new ArrayList<>();
        for (Contribution item : menus)
            if (item.location.equals("drawer.menu") && item.context.menuVisible(item.condition,
                    actionContext(item, fragment).toString(), fragment, null, null)) available.add(item);
        available.sort(Comparator.comparingInt((Contribution item) -> item.priority).reversed());
        for (Contribution item : available) {
            options.add(item.icon, item.title, item.subtext, () -> {
                if (!menus.contains(item) || !item.context.active()) return;
                JsonObject event = new JsonObject();
                event.addProperty("account", fragment.getCurrentAccount());
                item.context.call(item.callback, event.toString(), fragment, null, null);
            });
        }
    }
    private static JsonObject actionContext(Contribution item, BaseFragment fragment) {
        JsonObject event = new JsonObject();
        event.addProperty("account", fragment.getCurrentAccount());
        long peer = fragment instanceof ChatActivity ? ((ChatActivity) fragment).getDialogId()
                : fragment instanceof ProfileActivity ? ((ProfileActivity) fragment).getDialogId() : 0;
        if (peer != 0 && item.context.dialogAvailable(fragment.getCurrentAccount(), peer)) {
            event.addProperty("dialog_id", peer);
            event.addProperty("topic_id", fragment instanceof ChatActivity ? ((ChatActivity) fragment).getTopicId()
                    : ((ProfileActivity) fragment).getTopicId());
        }
        return event;
    }
    private static boolean messageMenuAvailable(Contribution item, ChatActivity chat, MessageObject message) {
        long peer = message.getDialogId();
        if (!item.location.equals("message.context") || DialogObject.isEncryptedDialog(peer)
                || !item.context.dialogAvailable(chat.getCurrentAccount(), peer)) return false;
        if (item.condition == null) return true;
        JsonObject event = actionContext(item, chat);
        event.addProperty("message_id", message.getId());
        return item.context.menuVisible(item.condition, event.toString(), chat, message, chat.getValidGroupedMessage(message));
    }
    public static boolean hasMessageMenu(ChatActivity chat, MessageObject message) {
        if (message == null) return false;
        for (Contribution item : menus) if (messageMenuAvailable(item, chat, message)) return true;
        return false;
    }
    public static void showMessageMenu(ChatActivity chat, MessageObject message) {
        if (message == null || chat.getParentActivity() == null) return;
        int account = chat.getCurrentAccount(), messageId = message.getId();
        long peer = message.getDialogId(), topic = chat.getTopicId();
        ArrayList<Contribution> available = new ArrayList<>();
        for (Contribution item : menus) if (messageMenuAvailable(item, chat, message)) available.add(item);
        if (available.isEmpty()) return;
        String identity;
        try { identity = PluginContext.currentIdentity(account); }
        catch (IllegalStateException unavailable) { return; }
        showContributions(chat, available, (dialog, index) -> {
                    Contribution item = available.get(index);
                    if (chat.getCurrentAccount() != account || chat.getTopicId() != topic
                            || !menus.contains(item) || !messageMenuAvailable(item, chat, message)
                            || !item.context.accountAvailable(account, identity)) return;
                    JsonObject event = new JsonObject();
                    event.addProperty("account", account);
                    event.addProperty("dialog_id", peer);
                    event.addProperty("message_id", messageId);
                    event.addProperty("topic_id", topic);
                    if (item.compatibility) {
                        item.context.callMessageMenu(item.callback, account, identity, peer, event.toString(), chat,
                                message, chat.getValidGroupedMessage(message));
                    } else {
                        item.context.callMessageMenu(item.callback, account, identity, peer, event.toString());
                    }
                });
    }
    private static void showContributions(BaseFragment fragment, List<Contribution> items, android.content.DialogInterface.OnClickListener click) {
        if (items.isEmpty()) return;
        items.sort(Comparator.comparingInt((Contribution item) -> item.priority).reversed());
        CharSequence[] titles = new CharSequence[items.size()];
        int[] icons = new int[items.size()];
        for (int i = 0; i < titles.length; i++) {
            Contribution item = items.get(i);
            titles[i] = item.subtext == null ? item.title : item.title + "\n" + item.subtext;
            icons[i] = item.icon;
        }
        fragment.showDialog(new AlertDialog.Builder(fragment.getContext())
                .setTitle(LocaleController.getString(R.string.BygramPlugins)).setItems(titles, icons, click).create());
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
            for (Contribution item : menus) if (item.location.equals(location(fragment))
                    && item.context.menuVisible(item.condition, actionContext(item, fragment).toString(), fragment, null, null)) clicked.add(item);
            showContributions(fragment, clicked, (dialog, index) -> {
                        Contribution item = clicked.get(index);
                        if (!menus.contains(item) || !item.context.active()) return;
                        JsonObject event = actionContext(item, fragment);
                        if (item.compatibility) item.context.call(item.callback, event.toString(), fragment, null, null);
                        else item.context.call(item.callback, event.toString());
                    });
        });
    }
    public static void openPage(PluginContext context, String json) {
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64 * 1024) throw new IllegalArgumentException("PageLimitExceeded");
        JsonObject specification = JsonParser.parseString(json).getAsJsonObject();
        AndroidUtilities.runOnUIThread(() -> {
            BaseFragment fragment = current.get();
            if (fragment == null && specification.has("focus_alias")) fragment = LaunchActivity.getLastFragment();
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
                        bulletin(context, LocaleController.getString(R.string.BygramPluginDraftChanged), "info");
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
