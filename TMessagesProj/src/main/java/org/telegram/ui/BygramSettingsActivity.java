package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.BygramConfig;
import org.telegram.messenger.BygramSettingsLink;
import org.telegram.messenger.BygramUpdates;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.BygramChatPrivacy;
import org.telegram.messenger.BygramActivityController;
import org.telegram.messenger.BygramTypingController;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.AppIconsSelectorCell;
import org.telegram.ui.Cells.SlideIntChooseView;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCell;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

abstract class BygramSettingsPage extends BaseFragment {
    BygramSettingsLink linkTarget;
    protected int settingsBackgroundKey() { return Theme.key_windowBackgroundGray; }
    @Override public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = super.getThemeDescriptions();
        descriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, settingsBackgroundKey()));
        return descriptions;
    }

    @Override
    public boolean onFragmentCreate() {
        boolean created = super.onFragmentCreate();
        logSetting(created ? "open" : "rejected", "screen");
        return created;
    }

    final void logSetting(String result, String feature) {
        logSetting(result, feature, "");
    }

    private void logSetting(String result, String feature, String detail) {
        String page = BygramSettingsActivity.settingsPage(this);
        String message = "BYgramSettings page=" + (page == null ? getClass().getSimpleName() : page)
                + " feature=" + feature + " result=" + result + detail;
        if ("failed".equals(result) || "rejected".equals(result)) {
            FileLog.w(message);
        } else {
            FileLog.d(message);
        }
    }

    final void logSettingFailure(String feature, Exception error) {
        logSetting("failed", feature, " error=" + error.getClass().getSimpleName());
    }

    final void logPublishResult(String feature, boolean ok, String authUrl) {
        logSetting(ok ? "success" : authUrl.isEmpty() ? "failed" : "auth_required", feature);
    }

    final void changeSetting(String feature, Runnable change) {
        try {
            change.run();
        } catch (RuntimeException error) {
            logSettingFailure(feature, error);
            throw error;
        }
        logSetting("changed", feature);
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    protected FrameLayout createSettingsContent(Context context) {
        setHasOwnBackground(true);
        return new FrameLayout(context);
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        View content = ((FrameLayout) fragmentView).getChildAt(0);
        return content instanceof RecyclerListView ? (RecyclerListView) content : null;
    }

    @Override
    protected void onViewCreated(View view) {
        super.onViewCreated(view);
        if (getListViewForSimpleGlass() == null) {
            getBaseSimpleGlass().setViews((FrameLayout) view, simpleActionBar, getResourceProvider());
            simpleActionBar.setBackground(getBaseSimpleGlass().createActionBarFadeDrawable(simpleActionBar));
            onInsets(mSystemInsets.left, mSystemInsets.top, mSystemInsets.right, mSystemInsets.bottom);
        }
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (getListViewForSimpleGlass() == null) {
            View content = ((FrameLayout) fragmentView).getChildAt(0);
            AndroidUtilities.setViewLayoutMargins(content, left,
                    top + ActionBar.getCurrentActionBarHeight() + simpleActionBar.getExtraHeight(), right, bottom);
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (simpleActionBar != null && simpleActionBar.isSearchFieldVisible()) {
            if (invoked) simpleActionBar.closeSearchField();
            return false;
        }
        return super.onBackPressed(invoked);
    }
}

public class BygramSettingsActivity extends BygramSettingsPage implements NotificationCenter.NotificationCenterDelegate {
    private static final int ROW_PLUGINS = 14;
    private UniversalRecyclerView listView;
    private FrameLayout updateRow;
    private boolean linkOpened;

    @Override
    public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.bygramUpdateChanged);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.bygramUpdateChanged);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (listView != null) listView.adapter.update(true);
    }

    public static BaseFragment forLink(BygramSettingsLink target) {
        BygramSettingsPage page;
        switch (target.customizationSection()) {
            case "plugins": return org.telegram.messenger.PluginEntry.page();
            case "customization": page = new CustomizationSettingsActivity(); break;
            case "general": page = new GeneralCustomizationActivity(); break;
            case "appearance": page = new AppearanceCustomizationActivity(); break;
            case "chat_list": page = new ChatListCustomizationActivity(); break;
            case "winter": page = new WinterCustomizationActivity(); break;
            case "profile": page = new ProfileCustomizationActivity(); break;
            case "typing": page = new TypingCustomizationActivity(); break;
            case "ghost": page = new GhostSettingsActivity(); break;
            case "spy": page = new SpySettingsActivity(); break;
            case "other": page = new OtherSettingsActivity(); break;
            case "profile_views": page = new ProfileViewsActivity(); break;
            case "protected": page = new PrivateChatsActivity(false); break;
            default: page = new BygramSettingsActivity(); break;
        }
        page.linkTarget = target;
        return page;
    }

    private void presentSettingsPage(BaseFragment page) {
        ((BygramSettingsPage) page).linkTarget = linkTarget;
        page.setCurrentAccount(currentAccount);
        if (!presentFragment(page)) logSetting("rejected", settingsPage(page));
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (!isOpen || backward || linkOpened || linkTarget == null) return;
        linkOpened = true;
        fragmentView.post(() -> {
            if (getParentLayout() == null) return;
            // Use the normal entry points: links must not bypass PIN or licensing.
            if ("hidden".equals(linkTarget.page)) onClick(UItem.asButton(5, ""), null, 0, 0, 0);
            else if ("activity".equals(linkTarget.page)) presentSettingsPage(new ActivitySharingSettingsActivity());
        });
    }

    static String settingsPage(BaseFragment fragment) {
        if (fragment instanceof BygramSettingsActivity) return "root";
        if (fragment instanceof CustomizationSettingsActivity) return "customization";
        if (fragment instanceof GeneralCustomizationActivity) return "general";
        if (fragment instanceof AppearanceCustomizationActivity) return "appearance";
        if (fragment instanceof ChatListCustomizationActivity) return "chat_list";
        if (fragment instanceof WinterCustomizationActivity) return "winter";
        if (fragment instanceof ProfileCustomizationActivity) return "profile";
        if (fragment instanceof TypingCustomizationActivity) return "typing";
        if (fragment instanceof GhostSettingsActivity) return "ghost";
        if (fragment instanceof SpySettingsActivity) return "spy";
        if (fragment instanceof OtherSettingsActivity) return "other";
        if (fragment instanceof ProfileViewsActivity) return "profile_views";
        if (fragment instanceof ActivitySharingSettingsActivity) return "activity";
        if (fragment instanceof PrivateChatsActivity) return ((PrivateChatsActivity) fragment).hidden ? "hidden" : "protected";
        return null;
    }

    private static int linkedRow(BaseFragment fragment) {
        BygramSettingsLink target = ((BygramSettingsPage) fragment).linkTarget;
        return target != null && target.customizationSection().equals(settingsPage(fragment)) ? target.highlightedRow() : 0;
    }

    public static boolean showLinkMenu(BaseFragment fragment, View view, UniversalRecyclerView list, String target) {
        if (target == null) return false;
        return showUrlMenu(fragment, view, list, BygramSettingsLink.PREFIX + target);
    }

    public static boolean showUrlMenu(BaseFragment fragment, View view, UniversalRecyclerView list, String link) {
        ItemOptions.makeOptions(fragment, view)
                .add(R.drawable.msg_link2, getString(R.string.CopyLink), () -> {
                    AndroidUtilities.addToClipboard(link);
                    BulletinFactory.of(fragment).createCopyLinkBulletin().show();
                })
                .add(R.drawable.msg_share, getString(R.string.ShareLink), () ->
                        fragment.showDialog(new ShareAlert(fragment.getContext(), null, link, false, link, false, fragment.getResourceProvider())))
                .setScrimViewBackground(list.getClipBackground(view))
                .show();
        return true;
    }

    private static class SettingsList extends UniversalRecyclerView {
        private final BygramSettingsPage fragment;
        private boolean focusedLink;

        SettingsList(BygramSettingsPage fragment, Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fill,
                Utilities.Callback5<UItem, View, Integer, Float, Float> click,
                Utilities.Callback5Return<UItem, View, Integer, Float, Float, Boolean> longClick) {
            super(fragment, (items, adapter) -> {
                try {
                    fill.run(items, adapter);
                } catch (RuntimeException error) {
                    fragment.logSettingFailure("build_rows", error);
                    throw error;
                }
                for (UItem item : items) {
                    if (item.viewType == UniversalAdapter.VIEW_TYPE_CHECK
                            || item.viewType == UniversalAdapter.VIEW_TYPE_TEXT) {
                        Utilities.Callback<View> bind = item.bind;
                        item.onBind(view -> {
                            if (bind != null) bind.run(view);
                            if (view instanceof TextCheckCell) ((TextCheckCell) view).setMultilineTextAndValue(null);
                            else if (view instanceof TextCell) ((TextCell) view).setMultiline(true);
                        });
                    }
                }
            }, (item, view, position, x, y) -> {
                try {
                    click.run(item, view, position, x, y);
                } catch (RuntimeException error) {
                    fragment.logSettingFailure("row_action", error);
                    throw error;
                }
            }, longClick);
            this.fragment = fragment;
            setOnItemLongClickListener((view, position, x, y) -> {
                UItem item = adapter.getItem(position);
                if (item == null) return false;
                String page = settingsPage(fragment);
                int row = item.id;
                return showLinkMenu(fragment, view, this, BygramSettingsLink.target(page, row));
            });
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (focusedLink || linkedRow(fragment) == 0) return;
            focusedLink = true;
            post(() -> highlightRow(() -> {
                for (int i = 0; i < adapter.getItemCount(); i++) {
                    UItem item = adapter.getItem(i);
                    if (item != null && item.id == linkedRow(fragment)) {
                        layoutManager.scrollToPositionWithOffset(i, AndroidUtilities.dp(60));
                        return i;
                    }
                }
                return -1;
            }, 1800));
        }
    }

    @Override
    public View createView(Context context) {
        BygramConfig.clearRemovedCustomizationSettings();
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(getString(R.string.BygramSettings));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout content = createSettingsContent(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        updateRow = new FrameLayout(context);
        ButtonWithCounterView download = new ButtonWithCounterView(context, getResourceProvider()).setRound();
        download.setText(getString(R.string.BygramUpdateDownload));
        download.setOnClickListener(view -> Browser.openUrl(view.getContext(), BygramUpdates.DOWNLOAD_URL));
        updateRow.addView(download, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP, 16, 12, 16, 0));
        listView = new SettingsList(this, this::fillItems, this::onClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (BygramUpdates.isAvailable()) items.add(UItem.asCustom(updateRow, 60));
        items.add(UItem.asHeader(getString(R.string.BygramCategories)).setId(-1));
        items.add(UItem.asButton(9, R.drawable.settings_account, getString(R.string.BygramCustomization)));
        items.add(UItem.asButton(ROW_PLUGINS, R.drawable.settings_account, getString(R.string.BygramPluginsBeta), org.telegram.messenger.PluginEntry.summary()));
        items.add(UItem.asButton(1, R.drawable.settings_privacy, getString(R.string.BygramGhostMode)));
        items.add(UItem.asButton(2, R.drawable.settings_devices, getString(R.string.BygramSpyMode)));
        items.add(UItem.asButton(3, R.drawable.msg_location, getString(R.string.BygramShareActivity)));
        items.add(UItem.asButton(5, R.drawable.settings_privacy, getString(R.string.BygramHiddenChats)));
        items.add(UItem.asButton(6, R.drawable.settings_privacy, getString(R.string.BygramProtectedChats)));
        items.add(UItem.asButton(13, R.drawable.bygram_footprints, getString(R.string.BygramProfileViews)));
        items.add(UItem.asButton(8, R.drawable.settings_account, getString(R.string.BygramOther)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.BygramLinks)).setId(-2));
        items.add(UItem.asButton(10, R.drawable.msg_channel, getString(R.string.BygramChannel)));
        items.add(UItem.asButton(11, R.drawable.msg_discussion, getString(R.string.BygramChat)));
        items.add(UItem.asButton(12, R.drawable.msg_link, getString(R.string.BygramTranslations)));
        items.add(UItem.asShadow(LocaleController.formatString(R.string.BygramReleaseNumber, BygramUpdates.RELEASE_NUMBER)));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_PLUGINS) {
            BaseFragment page = org.telegram.messenger.PluginEntry.page();
            page.setCurrentAccount(currentAccount);
            if (!presentFragment(page)) logSetting("rejected", "plugins");
            return;
        }
        if (item.id == 8) {
            presentSettingsPage(new OtherSettingsActivity());
        } else if (item.id == 9) {
            presentSettingsPage(new CustomizationSettingsActivity());
        } else if (item.id == 1) {
            presentSettingsPage(new GhostSettingsActivity());
        } else if (item.id == 2) {
            presentSettingsPage(new SpySettingsActivity());
        } else if (item.id == 3) {
            presentSettingsPage(new ActivitySharingSettingsActivity());
        } else if (item.id == 5) {
            if (BygramChatPrivacy.hasPin(currentAccount)) {
                askSecret(getString(R.string.BygramHiddenPin), true, secret -> {
                    if (BygramChatPrivacy.verifyPin(currentAccount, secret)) presentSettingsPage(new PrivateChatsActivity(true));
                    else privacyError();
                });
            } else {
                presentSettingsPage(new PrivateChatsActivity(true));
            }
        } else if (item.id == 6) {
            presentSettingsPage(new PrivateChatsActivity(false));
        } else if (item.id == 13) {
            presentSettingsPage(new ProfileViewsActivity());
        } else if (item.id == 10) {
            org.telegram.messenger.browser.Browser.openUrl(getContext(), "https://t.me/BYgram_official");
        } else if (item.id == 11) {
            org.telegram.messenger.browser.Browser.openUrl(getContext(), "https://t.me/BYgram_community");
        } else if (item.id == 12) {
            org.telegram.messenger.browser.Browser.openUrl(getContext(), "https://github.com/BYxarek/BYgram-translations");
        }
    }

    private interface SecretCallback { void accept(String secret); }

    private void askSecret(String title, boolean numeric, SecretCallback callback) {
        EditText input = createDialogInput(getContext(), title);
        input.setInputType(numeric ? 18 : 129);
        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                .setTitle(title).setView(dialogInputContainer(input))
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> callback.accept(input.getText().toString()))
                .create());
    }

    private void privacyError() {
        logSetting("rejected", "hidden_chats_unlock");
        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.BygramPrivacyError))
                .setPositiveButton(getString(R.string.OK), null).create());
    }

    static EditTextBoldCursor createDialogInput(Context context, String hint) {
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setSingleLine(true);
        input.setTextSize(18);
        input.setMinHeight(AndroidUtilities.dp(56));
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setCursorColor(Theme.getColor(Theme.key_dialogTextBlue));
        input.setHint(hint);
        input.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        input.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
        for (boolean focused : new boolean[]{true, false}) {
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setColor(Theme.getColor(Theme.key_dialogBackgroundGray));
            shape.setCornerRadius(AndroidUtilities.dp(12));
            shape.setStroke(AndroidUtilities.dp(focused ? 2 : 1), Theme.getColor(focused ? Theme.key_dialogTextBlue : Theme.key_dialogInputField));
            background.addState(focused ? new int[]{android.R.attr.state_focused} : new int[]{}, shape);
        }
        input.setBackground(background);
        return input;
    }

    static FrameLayout dialogInputContainer(EditText input) {
        FrameLayout container = new FrameLayout(input.getContext());
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 8, 24, 8));
        return container;
    }

    public static class ProfileViewsActivity extends BygramSettingsPage {
        private UniversalRecyclerView listView;
        private JSONArray views = new JSONArray();

        @Override public View createView(Context context) {
            simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
            simpleActionBar.setTitle(getString(R.string.BygramProfileViews));
            simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
            });
            FrameLayout content = createSettingsContent(context);
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            listView = new SettingsList(this, this::fillItems, (item, view, position, x, y) -> {
                if (item.id != 1) return;
                boolean enabled = !BygramTypingController.profileViewsEnabled(currentAccount);
                JSONObject data = new JSONObject();
                try { data.put("action", "profile_views_set"); data.put("enabled", enabled); }
                catch (Exception error) { logSettingFailure("set_profile_views", error); return; }
                logSetting("started", "set_profile_views");
                BygramTypingController.profileViewsRequest(currentAccount, data, (response, authUrl) -> {
                    logPublishResult("set_profile_views", response != null && response.optBoolean("ok"), authUrl);
                    if (!authUrl.isEmpty()) {
                        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                                .setTitle(getString(R.string.BygramProfileViews))
                                .setMessage(getString(R.string.BygramProfileViewsVerify))
                                .setPositiveButton(getString(R.string.BygramTypingOpenLogin), (dialog, which) -> BygramTypingController.openAccountLogin(getContext(), authUrl))
                                .setNegativeButton(getString(R.string.Cancel), null).create());
                    } else if (response == null || !response.optBoolean("ok")) {
                        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                                .setTitle(getString(R.string.ErrorOccurred)).setPositiveButton(getString(R.string.OK), null).create());
                    } else if (listView != null) { listView.adapter.update(true); load(); }
                });
            }, null);
            listView.setSections();
            listView.adapter.setApplyBackground(false);
            content.addView(listView, LayoutHelper.createFrame(-1, -1));
            load();
            return fragmentView = content;
        }

        @Override public void onResume() { super.onResume(); load(); }

        private void load() {
            if (listView == null) return;
            JSONObject data = new JSONObject();
            try { data.put("action", "profile_views_list"); }
            catch (Exception error) { logSettingFailure("load_profile_views", error); return; }
            BygramTypingController.profileViewsRequest(currentAccount, data, (response, authUrl) -> {
                logPublishResult("load_profile_views", response != null && response.optBoolean("ok"), authUrl);
                if (response != null && response.optBoolean("ok") && listView != null) {
                    views = response.optJSONArray("views");
                    if (views == null) views = new JSONArray();
                    listView.adapter.update(true);
                }
            });
        }

        private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asCheck(1, getString(R.string.BygramProfileViews))
                    .setChecked(BygramTypingController.profileViewsEnabled(currentAccount)));
            items.add(UItem.asShadow(getString(R.string.BygramProfileViewsInfo)));
            if (!BygramTypingController.profileViewsEnabled(currentAccount) && linkedRow(this) != -1) return;
            items.add(UItem.asHeader(getString(R.string.BygramProfileViewsRecent)).setId(-1));
            if (views.length() == 0) items.add(UItem.asShadow(getString(R.string.BygramProfileViewsEmpty)));
            for (int i = 0; i < views.length(); i++) {
                JSONObject value = views.optJSONObject(i);
                if (value == null) continue;
                long id = value.optLong("viewer_id");
                String name = value.optString("viewer_name", "");
                String username = value.optString("viewer_username", "");
                String avatarUrl = value.optString("avatar_url", "");
                int visitedAt = value.optInt("visited_at");
                boolean isNew = value.optInt("is_new") != 0;
                TLRPC.User user = getMessagesController().getUser(id);
                FrameLayout row = new FrameLayout(getContext());
                row.setBackgroundColor(isNew ? (Theme.getColor(Theme.key_windowBackgroundWhiteBlueText) & 0x00ffffff) | 0x18000000 : Theme.getColor(Theme.key_windowBackgroundWhite));
                BackupImageView avatar = new BackupImageView(getContext());
                avatar.setRoundRadius(AndroidUtilities.dp(24));
                AvatarDrawable placeholder = new AvatarDrawable();
                if (user != null) {
                    placeholder.setInfo(user);
                } else {
                    placeholder.setInfo(id, name, "");
                }
                if (user != null && user.photo != null) {
                    avatar.setForUserOrChat(user, placeholder);
                } else if (avatarUrl.startsWith("https://byxarek.site/TEST-PROJECT/BYgram/api/avatar.php?")) {
                    avatar.setImage(avatarUrl, "48_48", placeholder);
                } else {
                    avatar.setImage((org.telegram.messenger.ImageLocation) null, null, placeholder, null);
                }
                row.addView(avatar, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));
                TextView title = new TextView(getContext());
                title.setTextSize(16);
                title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                title.setText(name);
                title.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(title, LayoutHelper.createFrame(-1, 32, Gravity.LEFT | Gravity.TOP, 76, 6, 16, 0));
                TextView time = new TextView(getContext());
                time.setTextSize(13);
                time.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                if (visitedAt > 0) time.setText(org.telegram.messenger.LocaleController.formatDateTime(visitedAt, false));
                row.addView(time, LayoutHelper.createFrame(-1, 26, Gravity.LEFT | Gravity.TOP, 76, 36, 16, 0));
                row.setOnClickListener(v -> {
                    if (user == null && !username.isEmpty()) getMessagesController().openByUserName(username, this, 0, null);
                    else if (user == null) presentFragment(new ProfileViewUserActivity(id, name, avatarUrl));
                    else {
                        android.os.Bundle args = new android.os.Bundle(); args.putLong("user_id", id);
                        presentFragment(new ProfileActivity(args));
                    }
                });
                items.add(UItem.asCustom(row, 64));
            }
        }
    }

    private static class ProfileViewUserActivity extends BygramSettingsPage {
        private final long userId;
        private final String name;
        private final String avatarUrl;

        ProfileViewUserActivity(long userId, String name, String avatarUrl) {
            this.userId = userId; this.name = name; this.avatarUrl = avatarUrl;
        }

        @Override public View createView(Context context) {
            simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
            simpleActionBar.setTitle(name);
            simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
            });
            FrameLayout content = createSettingsContent(context);
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            LinearLayout details = new LinearLayout(context);
            details.setGravity(Gravity.CENTER);
            details.setOrientation(LinearLayout.VERTICAL);
            BackupImageView avatar = new BackupImageView(context);
            avatar.setRoundRadius(AndroidUtilities.dp(48));
            AvatarDrawable placeholder = new AvatarDrawable(); placeholder.setInfo(userId, name, "");
            if (avatarUrl.startsWith("https://byxarek.site/TEST-PROJECT/BYgram/api/avatar.php?")) avatar.setImage(avatarUrl, "96_96", placeholder);
            else avatar.setImage((org.telegram.messenger.ImageLocation) null, null, placeholder, null);
            details.addView(avatar, new LinearLayout.LayoutParams(AndroidUtilities.dp(96), AndroidUtilities.dp(96)));
            TextView title = new TextView(context);
            title.setText(name);
            title.setTextSize(22);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
            titleParams.topMargin = AndroidUtilities.dp(16);
            details.addView(title, titleParams);
            TextView id = new TextView(context);
            id.setText("ID " + userId);
            id.setGravity(Gravity.CENTER);
            details.addView(id, new LinearLayout.LayoutParams(-1, -2));
            content.addView(details, LayoutHelper.createFrame(-1, -1));
            return fragmentView = content;
        }
    }

    public static class PrivateChatsActivity extends BygramSettingsPage {
        private static class PrivateDialogCellFactory extends UItem.UItemFactory<FrameLayout> {
            @Override public FrameLayout createView(Context context, org.telegram.ui.Components.RecyclerListView listView,
                                                    int account, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                DialogCell cell = new DialogCell(null, context, true, false, account, resourcesProvider);
                cell.showBygramProtectedMessage = true;
                FrameLayout row = new FrameLayout(context);
                row.addView(cell, LayoutHelper.createFrame(-1, -2, Gravity.TOP | Gravity.LEFT, 0, 0, 48, 0));
                ImageView remove = new ImageView(context);
                remove.setImageResource(R.drawable.msg_delete);
                remove.setColorFilter(0xffe53935);
                remove.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
                remove.setContentDescription(getString(R.string.Delete));
                row.addView(remove, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
                return row;
            }

            @Override public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                                           UniversalRecyclerView listView) {
                ((DialogCell) ((FrameLayout) view).getChildAt(0)).setDialog((TLRPC.Dialog) item.object, DialogsActivity.DIALOGS_TYPE_DEFAULT, 0);
            }
        }
        static { UItem.UItemFactory.setup(new PrivateDialogCellFactory()); }

        private final boolean hidden;
        private final boolean revealOnly;
        private final ArrayList<Long> ids = new ArrayList<>();
        private UniversalRecyclerView listView;

        public PrivateChatsActivity(boolean hidden) { this(hidden, false); }
        public PrivateChatsActivity(boolean hidden, boolean revealOnly) {
            this.hidden = hidden;
            this.revealOnly = revealOnly;
        }

        @Override public View createView(Context context) {
            simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
            simpleActionBar.setTitle(getString(hidden ? R.string.BygramHiddenChats : R.string.BygramProtectedChats));
            simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override public void onItemClick(int id) {
                    if (id == -1) finishFragment();
                    else if (id == 2) openChatPicker();
                }
            });
            if (!revealOnly) {
                simpleActionBar.createMenu().addItem(2, R.drawable.msg_add).setContentDescription(getString(R.string.SelectChat));
            }
            FrameLayout content = createSettingsContent(context);
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            listView = new SettingsList(this, this::fillItems, this::onClick, null);
            listView.setSections();
            listView.adapter.setApplyBackground(false);
            content.addView(listView, LayoutHelper.createFrame(-1, -1));
            return fragmentView = content;
        }

        private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            ids.clear();
            items.add(UItem.asHeader(getString(hidden ? R.string.BygramHiddenChats : R.string.BygramProtectedChats)).setId(-1));
            for (TLRPC.Dialog dialog : getMessagesController().getAllDialogs()) {
                long id = dialog.id;
                boolean selected = hidden ? BygramChatPrivacy.isHidden(currentAccount, id)
                        : BygramChatPrivacy.isProtected(currentAccount, id);
                if (!selected) continue;
                if (id > 0) {
                    TLRPC.User user = getMessagesController().getUser(id);
                    if (user == null) continue;
                } else if (id < 0 && !DialogObject.isEncryptedDialog(id)) {
                    TLRPC.Chat chat = getMessagesController().getChat(-id);
                    if (chat == null) continue;
                } else continue;
                int index = ids.size();
                ids.add(id);
                UItem item = UItem.ofFactory(PrivateDialogCellFactory.class);
                item.id = 100 + index;
                item.object = dialog;
                item.onBind(view -> ((FrameLayout) view).getChildAt(1).setOnClickListener(v -> {
                    if (hidden) BygramChatPrivacy.setHidden(currentAccount, id, false);
                    else BygramChatPrivacy.removePassword(currentAccount, id);
                    listView.adapter.update(true);
                }));
                items.add(item);
            }
            if (!revealOnly) {
                if (hidden) {
                    items.add(UItem.asButton(1, getString(BygramChatPrivacy.hasPin(currentAccount)
                            ? R.string.BygramChangePin : R.string.BygramSetPin)));
                }
                items.add(UItem.asShadow(getString(hidden ? R.string.BygramHiddenChatsInfo : R.string.BygramProtectedChatsInfo)));
            }
        }

        private void onClick(UItem item, View view, int position, float x, float y) {
            if (item.id == 1 && hidden) {
                if (BygramChatPrivacy.hasPin(currentAccount)) {
                    askSecret(getString(R.string.BygramHiddenPin), true, old -> {
                        if (BygramChatPrivacy.verifyPin(currentAccount, old)) setPin();
                        else privacyError();
                    });
                } else setPin();
                return;
            }
            int index = item.id - 100;
            if (index < 0 || index >= ids.size()) return;
            long id = ids.get(index);
            if (hidden && !BygramChatPrivacy.hasPin(currentAccount)) { setPin(); return; }
            if (revealOnly || !hidden && !BygramChatPrivacy.isProtected(currentAccount, id)) {
                android.os.Bundle args = new android.os.Bundle();
                if (id > 0) args.putLong("user_id", id); else args.putLong("chat_id", -id);
                if (hidden) BygramChatPrivacy.allowHiddenOnce(currentAccount, id);
                presentFragment(new ChatActivity(args));
                return;
            }
            if (hidden) {
                android.os.Bundle args = new android.os.Bundle();
                if (id > 0) args.putLong("user_id", id); else args.putLong("chat_id", -id);
                BygramChatPrivacy.allowHiddenOnce(currentAccount, id);
                presentFragment(new ChatActivity(args));
            } else if (BygramChatPrivacy.isProtected(currentAccount, id)) {
                askSecret(getString(R.string.BygramChatPassword), false, old -> {
                    if (!BygramChatPrivacy.verifyPassword(currentAccount, id, old)) { privacyError(); return; }
                    android.os.Bundle args = new android.os.Bundle();
                    if (id > 0) args.putLong("user_id", id); else args.putLong("chat_id", -id);
                    presentFragment(new ChatActivity(args));
                });
            } else setPassword(id);
        }

        private void openChatPicker() {
            if (revealOnly) return;
            android.os.Bundle args = new android.os.Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("checkCanWrite", false);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_DEFAULT);
            DialogsActivity picker = new DialogsActivity(args);
            picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                if (dids.isEmpty()) return false;
                long id = dids.get(0).dialogId;
                if (id == 0 || DialogObject.isEncryptedDialog(id)) return false;
                fragment.finishFragment();
                if (hidden) {
                    if (BygramChatPrivacy.hasPin(currentAccount)) {
                        BygramChatPrivacy.setHidden(currentAccount, id, true);
                        listView.adapter.update(true);
                    } else {
                        AndroidUtilities.runOnUIThread(() -> setPin(() -> {
                            BygramChatPrivacy.setHidden(currentAccount, id, true);
                            listView.adapter.update(true);
                        }), 300);
                    }
                } else if (!BygramChatPrivacy.isProtected(currentAccount, id)) {
                    AndroidUtilities.runOnUIThread(() -> setPassword(id), 300);
                }
                return true;
            });
            presentFragment(picker);
        }

        private void setPin() { setPin(null); }

        private void setPin(Runnable onSuccess) {
            askSecret(getString(R.string.BygramSetPin), true, pin -> {
                if (!pin.matches("[0-9]{4,}")) { privacyError(); return; }
                BygramChatPrivacy.setPin(currentAccount, pin);
                if (onSuccess != null) onSuccess.run();
                listView.adapter.update(true);
            });
        }

        private void setPassword(long id) {
            askSecret(getString(R.string.BygramChatPassword), false, password -> {
                if (password.length() < 4) { privacyError(); return; }
                BygramChatPrivacy.setPassword(currentAccount, id, password);
                listView.adapter.update(true);
            });
        }

        private void askSecret(String title, boolean numeric, SecretCallback callback) {
            EditText input = createDialogInput(getContext(), title);
            input.setInputType(numeric ? 18 : 129);
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(title).setView(dialogInputContainer(input))
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .setPositiveButton(getString(R.string.OK), (dialog, which) -> callback.accept(input.getText().toString())).create());
        }

        private void privacyError() {
            logSetting("rejected", "privacy_validation");
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.BygramPrivacyError))
                    .setPositiveButton(getString(R.string.OK), null).create());
        }
    }

    public static class CustomizationSettingsActivity extends ToggleSettingsActivity {
        @Override int getTitleRes() { return R.string.BygramCustomization; }

        @Override void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asButton(2, R.drawable.msg_settings, getString(R.string.BygramGeneral)));
            items.add(UItem.asButton(3, R.drawable.msg_discussion, getString(R.string.BygramChatList)));
            items.add(UItem.asButton(1, R.drawable.msg_contacts, getString(R.string.BygramProfile)));
            items.add(UItem.asButton(5, R.drawable.msg_theme, getString(R.string.BygramAppearance)));
            items.add(UItem.asButton(6, R.drawable.bygram_snowflake, getString(R.string.BygramWinter)));
            items.add(UItem.asButton(4, R.drawable.msg_edit, getString(R.string.BygramCustomTyping)));
            items.add(UItem.asShadow(null));
        }

        @Override String keyFor(int id) { return null; }

        @Override void onCustomClick(int id) {
            BygramSettingsLink target = BygramSettingsLink.parse(BygramSettingsLink.target("customization", id));
            if ("root".equals(target.page)) return;
            BaseFragment page = forLink(target);
            page.setCurrentAccount(currentAccount);
            if (!presentFragment(page)) logSetting("rejected", target.page);
        }
    }

    public static class TypingCustomizationActivity extends ToggleSettingsActivity {
        private boolean publishing;
        private boolean verifyOnResume;
        private static final int[] LABELS = {
                R.string.BygramTypingText, R.string.BygramTypingRecordAudio,
                R.string.BygramTypingCancel, R.string.BygramTypingFile,
                R.string.BygramTypingPhoto, R.string.BygramTypingVideo,
                R.string.BygramTypingRecordVideo, R.string.BygramTypingRecordRound,
                R.string.BygramTypingUploadRound, R.string.BygramTypingAudio,
                R.string.BygramTypingGame
        };

        @Override int getTitleRes() { return R.string.BygramCustomTyping; }

        @Override void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader(getString(R.string.BygramCustomTyping)).setId(-1));
            for (int i = 0; i < LABELS.length; i++) {
                if (i == 2) continue;
                String value = BygramConfig.getCustomTyping(currentAccount, i);
                items.add(UItem.asButton(100 + i, getString(LABELS[i]), value.isEmpty() ? getString(R.string.BygramTypingDefault) : value));
            }
            items.add(UItem.asShadow(getString(R.string.BygramCustomTypingInfo)));
        }

        @Override String keyFor(int id) { return null; }

        @Override void onCustomClick(int id) {
            if (publishing) return;
            int action = id - 100;
            if (action < 0 || action >= LABELS.length || action == 2) return;
            EditText input = createDialogInput(getContext(), getString(R.string.BygramTypingDefault));
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
            input.setText(BygramConfig.getCustomTyping(currentAccount, action));
            input.setSelection(input.length());
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(LABELS[action]))
                    .setView(dialogInputContainer(input))
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                        changeSetting("typing_action_" + action,
                                () -> BygramConfig.setCustomTyping(currentAccount, action, input.getText().toString()));
                        listView.adapter.update(true);
                        publish();
                    }).create());
        }

        private void publish() {
            if (publishing || getContext() == null) return;
            publishing = true;
            org.telegram.ui.ActionBar.AlertDialog progress = new org.telegram.ui.ActionBar.AlertDialog(getContext(), 3);
            progress.setCanCancel(false);
            showDialog(progress);
            progress.setCanceledOnTouchOutside(false);
            progress.setCancelable(false);
            logSetting("started", "publish_typing");
            BygramTypingController.publish(currentAccount, (ok, authUrl) -> {
                logPublishResult("publish_typing", ok, authUrl);
                publishing = false;
                progress.dismiss();
                if (getContext() == null) return;
                if (ok) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                            getString(R.string.BygramTypingPublishSuccess)).show();
                    return;
                }
                if (!authUrl.isEmpty()) {
                    showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.BygramCustomTyping))
                            .setMessage(getString(R.string.BygramTypingVerify))
                            .setNegativeButton(getString(R.string.Cancel), null)
                            .setPositiveButton(getString(R.string.BygramTypingOpenLogin), (dialog, which) -> {
                                verifyOnResume = true;
                                BygramTypingController.openAccountLogin(getContext(), authUrl);
                            }).create());
                } else {
                    showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.BygramCustomTyping))
                            .setMessage(getString(R.string.BygramTypingPublishFailed))
                            .setPositiveButton(getString(R.string.OK), null).create());
                }
            });
        }

        @Override public void onResume() {
            super.onResume();
            if (verifyOnResume) {
                verifyOnResume = false;
                publish();
            }
        }

        @Override public boolean onBackPressed(boolean invoked) {
            return !publishing && super.onBackPressed(invoked);
        }

        @Override public boolean canBeginSlide() {
            return !publishing && super.canBeginSlide();
        }

        @Override public boolean dismissDialogOnPause(android.app.Dialog dialog) {
            return !publishing && super.dismissDialogOnPause(dialog);
        }
    }

    private static class TitlePreview extends FrameLayout {
        private final org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;
        TitlePreview(Context context, int account, BaseFragment owner) {
            super(context);
            boolean storiesVisible = !BygramConfig.getBoolean(BygramConfig.HIDE_STORIES)
                    && org.telegram.messenger.MessagesController.getInstance(account).getStoriesController().hasStories();
            setMinimumHeight(AndroidUtilities.dp(128));
            ActionBar bar = new ActionBar(context);
            bar.setOccupyStatusBar(false);
            bar.setTitle(BygramConfig.dialogsTitle(account));
            bar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
            bar.createMenu().addItem(1, R.drawable.outline_header_search).setContentDescription(getString(R.string.Search));
            bar.createMenu().addItem(2, R.drawable.msg_edit).setContentDescription(getString(R.string.Edit));
            org.telegram.utils.glass.source.GlassSourceColor color = new org.telegram.utils.glass.source.GlassSourceColor();
            color.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            bar.setupGlass(new org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory(color),
                    org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl.topPanel(null));
            org.telegram.ui.ActionBar.SimpleTextView title = bar.getTitleTextView();
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setAlpha(storiesVisible ? 0f : 1f);
            int position = BygramConfig.getInt(BygramConfig.TITLE_POSITION, 0);
            bar.setTitleGravityInGlass(position == 1 ? Gravity.CENTER_HORIZONTAL : position == 2 ? Gravity.RIGHT : Gravity.LEFT);
            emoji = new org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(title, AndroidUtilities.dp(26));
            Long documentId = UserObject.getEmojiStatusDocumentId(org.telegram.messenger.UserConfig.getInstance(account).getCurrentUser());
            if (documentId != null) emoji.set(documentId, false);
            title.setRightDrawable(emoji);
            addView(bar, LayoutHelper.createFrame(-1, 60, Gravity.TOP, 8, 4, 8, 0));
            if (storiesVisible) {
                org.telegram.ui.Stories.DialogStoriesCell stories = new org.telegram.ui.Stories.DialogStoriesCell(context, owner, account, org.telegram.ui.Stories.DialogStoriesCell.TYPE_DIALOGS) {
                    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                        setTranslationY(-(getMeasuredHeight() - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(4)));
                    }
                    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) { return true; }
                    @Override public boolean onTouchEvent(android.view.MotionEvent event) { return true; }
                };
                stories.setActionBar(bar);
                stories.setMenuItemsOffset(AndroidUtilities.dpf2(16.66f));
                stories.updateStatus(org.telegram.messenger.UserConfig.getInstance(account).getCurrentUser(), false);
                stories.setProgressToCollapse(1, false);
                addView(stories, LayoutHelper.createFrame(-1, org.telegram.ui.Stories.DialogStoriesCell.HEIGHT_IN_DP, Gravity.TOP, 12, 4, 12, 0));
            }
            addView(folderPreview(context, account), LayoutHelper.createFrame(-1, 48, Gravity.TOP, 12, 72, 12, 0));
            setContentDescription(getString(R.string.BygramHeaderPreview));
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); emoji.attach(); }
        @Override protected void onDetachedFromWindow() { emoji.detach(); super.onDetachedFromWindow(); }
    }

    private static View folderPreview(Context context, int account) {
        ArrayList<org.telegram.messenger.MessagesController.DialogFilter> filters = org.telegram.messenger.MessagesController.getInstance(account).getDialogFilters();
        org.telegram.ui.Components.FilterTabsView tabs = new org.telegram.ui.Components.FilterTabsView(context, null);
        tabs.setDelegate(new org.telegram.ui.Components.FilterTabsView.FilterTabsViewDelegate() {
            public void onPageSelected(org.telegram.ui.Components.FilterTabsView.Tab tab, boolean forward) {}
            public void onPageScrolled(float progress) {}
            public void onSamePageSelected() {}
            public int getTabCounter(int id) { return id >= 0 && id < filters.size() ? Math.max(0, filters.get(id).unreadCount) : 0; }
            public boolean didSelectTab(org.telegram.ui.Components.FilterTabsView.TabView view, boolean selected) { return false; }
            public boolean isTabMenuVisible() { return false; }
            public void onDeletePressed(int id) {}
            public void onPageReorder(int from, int to) {}
            public boolean canPerformActions() { return false; }
        });
        boolean hideAll = BygramConfig.getBoolean(BygramConfig.HIDE_ALL_TAB) && filters.size() > 1;
        for (int i = 0; i < filters.size(); i++) {
            org.telegram.messenger.MessagesController.DialogFilter filter = filters.get(i);
            if (filter.isDefault() && hideAll) continue;
            tabs.addTab(i, filter.localId, filter.isDefault() ? getString(R.string.FilterAllChats) : filter.name,
                    filter.entities, filter.title_noanimate, filter.isDefault(), false);
        }
        if (filters.isEmpty()) tabs.addTab(0, 0, getString(R.string.FilterAllChats), null, false, true, false);
        tabs.finishAddingTabs(false);
        return tabs;
    }

    public static class GeneralCustomizationActivity extends ToggleSettingsActivity {
        @Override int getTitleRes() { return R.string.BygramGeneral; }

        @Override void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asCheck(1, getString(R.string.BygramFilterZalgo))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.FILTER_ZALGO)));
            items.add(UItem.asShadow(getString(R.string.BygramFilterZalgoInfo)));
            items.add(UItem.asHeader(getString(R.string.BygramFeedbackSection)));
            items.add(UItem.asCheck(9, getString(R.string.BygramAppVibration))
                    .setChecked(!BygramConfig.getBoolean(BygramConfig.DISABLE_VIBRATION)));
            if (BygramConfig.getBoolean(BygramConfig.DISABLE_VIBRATION)) {
                items.add(UItem.asCheck(10, "    " + getString(R.string.BygramCallVibration))
                        .setChecked(BygramConfig.getBoolean(BygramConfig.CALL_VIBRATION)));
            }
            items.add(UItem.asShadow(null));
            items.add(UItem.asCheck(25, getString(R.string.BygramSeconds)).setChecked(BygramConfig.getBoolean(BygramConfig.SECONDS)));
            items.add(UItem.asShadow(null));
        }

        @Override String keyFor(int id) {
            switch (id) {
                case 1: return BygramConfig.FILTER_ZALGO;
                case 9: return BygramConfig.DISABLE_VIBRATION;
                case 10: return BygramConfig.CALL_VIBRATION;
                case 25: return BygramConfig.SECONDS;
                default: return null;
            }
        }

        @Override void onSettingChanged(String key) {
            if ((BygramConfig.DISABLE_VIBRATION.equals(key) && BygramConfig.getBoolean(key)
                    || BygramConfig.CALL_VIBRATION.equals(key) && !BygramConfig.getBoolean(key))
                    && !BygramConfig.isCallVibrationEnabled()) {
                android.os.Vibrator vibrator = (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) vibrator.cancel();
            }
        }
    }

    public static class ChatListCustomizationActivity extends ToggleSettingsActivity {
        @Override int getTitleRes() { return R.string.BygramChatList; }

        private String[] titleOptions() {
            TLRPC.User user = getUserConfig().getCurrentUser();
            String username = UserObject.getPublicUsername(user);
            String name = UserObject.getUserName(user);
            return new String[]{"BYgram", TextUtils.isEmpty(username) ? getString(R.string.BygramTitleUsername) : "@" + username,
                    TextUtils.isEmpty(name) ? getString(R.string.BygramTitleName) : name};
        }

        private String[] positionOptions() {
            return new String[]{getString(R.string.BygramTitleLeft), getString(R.string.BygramTitleCenter), getString(R.string.BygramTitleRight)};
        }

        private String[] folderStyleOptions() {
            return new String[]{getString(R.string.BygramFolderNames), getString(R.string.BygramFolderNamesIcons), getString(R.string.BygramFolderIcons)};
        }

        @Override void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader(getString(R.string.BygramFoldersAppearance)));
            items.add(UItem.asCustom(folderPreview(getContext(), currentAccount), 48));
            items.add(UItem.asButton(15, getString(R.string.BygramFolderStyle), folderStyleOptions()[Math.max(0, Math.min(2, BygramConfig.getInt(BygramConfig.FOLDER_STYLE, 0)))]));
            items.add(UItem.asCheck(16, getString(R.string.BygramHideFolderCounters)).setChecked(BygramConfig.getBoolean(BygramConfig.HIDE_FOLDER_COUNTERS)));
            items.add(UItem.asCheck(17, getString(R.string.BygramHideAllTab)).setChecked(BygramConfig.getBoolean(BygramConfig.HIDE_ALL_TAB)));
            items.add(UItem.asShadow(getString(R.string.BygramFoldersPreviewInfo)));
            items.add(UItem.asCheck(12, getString(R.string.BygramCollapseFoldersOnScroll))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.COLLAPSE_FOLDERS_ON_SCROLL)));
            items.add(UItem.asCheck(13, getString(R.string.BygramCollapseNavigationOnScroll))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.COLLAPSE_NAVIGATION_ON_SCROLL)));
            items.add(UItem.asCheck(4, getString(R.string.BygramCollapseSearchOnStart))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.COLLAPSE_SEARCH_ON_START)));
            if (BygramConfig.getBoolean(BygramConfig.COLLAPSE_SEARCH_ON_START)) {
                items.add(UItem.asCheck(11, "    " + getString(R.string.BygramKeepSearchCollapsed))
                        .setChecked(BygramConfig.getBoolean(BygramConfig.KEEP_SEARCH_COLLAPSED)));
            }
            items.add(UItem.asShadow(getString(R.string.BygramCollapseSearchOnStartInfo)));
            items.add(UItem.asCheck(5, getString(R.string.BygramHideArchive))
                    .setChecked(org.telegram.messenger.SharedConfig.archiveHidden));
            items.add(UItem.asShadow(getString(R.string.BygramHideArchiveInfo)));
            items.add(UItem.asCheck(8, getString(R.string.BygramNoUnarchiveSwipe))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.NO_UNARCHIVE_SWIPE)));
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.BygramHeaderSection)));
            items.add(UItem.asCheck(14, getString(R.string.BygramHideStories)).setChecked(BygramConfig.getBoolean(BygramConfig.HIDE_STORIES)));
            items.add(UItem.asCustom(new TitlePreview(getContext(), currentAccount, this)));
            String[] titles = titleOptions();
            String[] positions = positionOptions();
            items.add(UItem.asButton(6, getString(R.string.BygramTitleText),
                    titles[Math.max(0, Math.min(2, BygramConfig.getInt(BygramConfig.TITLE_TEXT, 0)))]));
            items.add(UItem.asButton(7, getString(R.string.BygramTitlePosition),
                    positions[Math.max(0, Math.min(2, BygramConfig.getInt(BygramConfig.TITLE_POSITION, 0)))]));
            items.add(UItem.asShadow(null));
        }

        @Override String keyFor(int id) {
            switch (id) {
                case 4: return BygramConfig.COLLAPSE_SEARCH_ON_START;
                case 8: return BygramConfig.NO_UNARCHIVE_SWIPE;
                case 11: return BygramConfig.KEEP_SEARCH_COLLAPSED;
                case 12: return BygramConfig.COLLAPSE_FOLDERS_ON_SCROLL;
                case 13: return BygramConfig.COLLAPSE_NAVIGATION_ON_SCROLL;
                case 14: return BygramConfig.HIDE_STORIES;
                case 16: return BygramConfig.HIDE_FOLDER_COUNTERS;
                case 17: return BygramConfig.HIDE_ALL_TAB;
                default: return null;
            }
        }

        @Override void onCustomClick(int id) {
            if (id == 5) {
                changeSetting("archive_hidden", org.telegram.messenger.SharedConfig::toggleArchiveHidden);
                listView.adapter.update(true);
                return;
            }
            if (id != 6 && id != 7 && id != 15) return;
            String key = id == 15 ? BygramConfig.FOLDER_STYLE : id == 6 ? BygramConfig.TITLE_TEXT : BygramConfig.TITLE_POSITION;
            String[] options = id == 15 ? folderStyleOptions() : id == 6 ? titleOptions() : positionOptions();
            int title = id == 15 ? R.string.BygramFolderStyle : id == 6 ? R.string.BygramTitleText : R.string.BygramTitlePosition;
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(title))
                    .setItems(options, (dialog, which) -> {
                        setIntSetting(key, which);
                        listView.adapter.update(true);
                    }).create());
        }
    }

    public static class AppearanceCustomizationActivity extends ToggleSettingsActivity {
        @Override int getTitleRes() { return R.string.BygramAppearance; }

        private boolean md3Expanded;
        private AppIconsSelectorCell icons;
        private FrameLayout avatarCorners;
        private SlideIntChooseView sectionCorners;

        @Override void createControls(Context context) {
            int row = linkedRow(this);
            md3Expanded = md3Expanded || row >= 200 && row <= 203;
            icons = new AppIconsSelectorCell(context, this, currentAccount, Arrays.asList(
                    LauncherIconController.LauncherIcon.DEFAULT,
                    LauncherIconController.LauncherIcon.AQUA)) {
                @Override protected void selectIcon(LauncherIconController.LauncherIcon icon) {
                    changeSetting("app_icon", () -> super.selectIcon(icon));
                }
            };
            BackupImageView avatarPreview = new BackupImageView(context);
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(getUserConfig().getCurrentUser());
            avatarPreview.setForUserOrChat(getUserConfig().getCurrentUser(), avatarDrawable);
            avatarCorners = new FrameLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(148), View.MeasureSpec.EXACTLY));
                }
            };
            avatarCorners.addView(avatarPreview, LayoutHelper.createFrame(64, 64, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
            SlideIntChooseView cornersSlider = new SlideIntChooseView(context, getResourceProvider());
            cornersSlider.setLabel(getString(R.string.BygramAvatarCorners));
            cornersSlider.set(Math.max(0, Math.min(28, BygramConfig.getInt(BygramConfig.AVATAR_CORNERS, 28))),
                    SlideIntChooseView.Options.make(0, 0, 28, value -> value == 0
                            ? getString(R.string.BygramSquare)
                            : value == 28 ? getString(R.string.BygramCircle) : String.valueOf(value)), value -> {
                        setIntSetting(BygramConfig.AVATAR_CORNERS, value);
                        avatarPreview.invalidate();
                    });
            avatarCorners.addView(cornersSlider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 75, Gravity.BOTTOM));
            sectionCorners = new SlideIntChooseView(context, getResourceProvider());
            sectionCorners.setLabel(getString(R.string.BygramSectionRadius));
            sectionCorners.set(BygramConfig.getInt(BygramConfig.SECTION_RADIUS, 16),
                    SlideIntChooseView.Options.make(0, 0, 28, value -> value == 0 ? getString(R.string.BygramOff) : value + " dp"), value -> {
                        setIntSetting(BygramConfig.SECTION_RADIUS, value);
                        listView.setSections();
                        listView.invalidate();
                    });
        }

        @Override void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader(getString(R.string.BygramAppIcons)).setId(-1));
            items.add(UItem.asCustom(icons, 104).setId(-1));
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.BygramSections)));
            items.add(UItem.asCustom(sectionCorners, 75).setId(26));
            items.add(UItem.asCheck(27, getString(R.string.BygramSeparateHeaders)).setChecked(BygramConfig.getBoolean(BygramConfig.SEPARATE_HEADERS)));
            items.add(UItem.asCheck(28, getString(R.string.BygramQrGlassStyle))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.QR_GLASS_STYLE)));
            items.add(UItem.asShadow(null));
            int md3 = (BygramConfig.getBoolean(BygramConfig.MD3_LOADING) ? 1 : 0)
                    + (BygramConfig.getBoolean(BygramConfig.MD3_SLIDERS) ? 1 : 0)
                    + (BygramConfig.getBoolean(BygramConfig.MD3_SWITCHES) ? 1 : 0)
                    + (BygramConfig.getBoolean(BygramConfig.MD3_PROFILE_BUTTONS) ? 1 : 0);
            items.add(UItem.asButton(3, getString(R.string.BygramMaterial3),
                    md3 + "/4"));
            if (md3Expanded) {
                items.add(UItem.asCheck(203, getString(R.string.BygramMd3ProfileButtons)).setChecked(BygramConfig.getBoolean(BygramConfig.MD3_PROFILE_BUTTONS)));
                items.add(UItem.asCheck(200, getString(R.string.BygramMd3Loading))
                        .setChecked(BygramConfig.getBoolean(BygramConfig.MD3_LOADING)));
                items.add(UItem.asCheck(201, getString(R.string.BygramMd3Sliders))
                        .setChecked(BygramConfig.getBoolean(BygramConfig.MD3_SLIDERS)));
                items.add(UItem.asCheck(202, getString(R.string.BygramMd3Switches))
                        .setChecked(BygramConfig.getBoolean(BygramConfig.MD3_SWITCHES)));
            }
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.BygramAvatarCorners)).setId(-2));
            items.add(UItem.asCustom(avatarCorners).setId(-2));
            items.add(UItem.asShadow(null));
        }

        @Override String keyFor(int id) {
            switch (id) {
                case 27: return BygramConfig.SEPARATE_HEADERS;
                case 28: return BygramConfig.QR_GLASS_STYLE;
                case 200: return BygramConfig.MD3_LOADING;
                case 201: return BygramConfig.MD3_SLIDERS;
                case 202: return BygramConfig.MD3_SWITCHES;
                case 203: return BygramConfig.MD3_PROFILE_BUTTONS;
                default: return null;
            }
        }

        @Override void onSettingChanged(String key) {
            if (BygramConfig.SEPARATE_HEADERS.equals(key)) {
                listView.setSections();
            } else if (BygramConfig.QR_GLASS_STYLE.equals(key)) {
                listView.invalidate();
                if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
            }
        }

        @Override void onCustomClick(int id) {
            if (id != 3) return;
            md3Expanded = !md3Expanded;
            logSetting(md3Expanded ? "expanded" : "collapsed", "material3");
            listView.adapter.update(true);
        }
    }

    public static class WinterCustomizationActivity extends ToggleSettingsActivity {
        @Override int getTitleRes() { return R.string.BygramWinter; }

        private SlideIntChooseView snowAmount;

        @Override void createControls(Context context) {
            snowAmount = new SlideIntChooseView(context, getResourceProvider());
            snowAmount.setLabel(getString(R.string.BygramSnowAmount));
            snowAmount.set(BygramConfig.getInt(BygramConfig.SNOW_AMOUNT, BygramConfig.SNOW_AMOUNT_MAX),
                    SlideIntChooseView.Options.make(0, BygramConfig.SNOW_AMOUNT_MIN,
                            BygramConfig.SNOW_AMOUNT_MAX, value -> value + "%"), value -> {
                        setIntSetting(BygramConfig.SNOW_AMOUNT, value);
                        fragmentView.invalidate();
                    });
        }

        @Override void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asCheck(22, getString(R.string.BygramSnowScreens)).setChecked(BygramConfig.getBoolean(BygramConfig.SNOW_SCREENS)));
            items.add(UItem.asCheck(18, getString(R.string.BygramSnowChats)).setChecked(BygramConfig.getBoolean(BygramConfig.SNOW_CHATS)));
            items.add(UItem.asCheck(21, getString(R.string.BygramSnowNavigation)).setChecked(BygramConfig.getBoolean(BygramConfig.SNOW_NAVIGATION)));
            items.add(UItem.asCustom(snowAmount, 75).setId(29));
            items.add(UItem.asShadow(getString(R.string.BygramSnowInfo)));
        }

        @Override String keyFor(int id) {
            switch (id) {
                case 18: return BygramConfig.SNOW_CHATS;
                case 21: return BygramConfig.SNOW_NAVIGATION;
                case 22: return BygramConfig.SNOW_SCREENS;
                default: return null;
            }
        }

        @Override void onSettingChanged(String key) {
            if (BygramConfig.SNOW_SCREENS.equals(key)) updateBygramSnow();
        }
    }

    public static class ProfileCustomizationActivity extends ToggleSettingsActivity {
        private android.net.Uri pendingBackground;
        private boolean backgroundPublishing;
        private boolean verifyBackgroundOnResume;

        @Override int getTitleRes() { return R.string.BygramProfile; }

        @Override void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader(getString(R.string.BygramProfile)).setId(-1));
            items.add(UItem.asButton(5, getString(R.string.BygramProfileBackground)));
            items.add(UItem.asCheck(7, getString(R.string.BygramFullProfileBackground))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.FULL_PROFILE_BACKGROUND)));
            items.add(UItem.asCheck(6, getString(R.string.BygramHideProfileBackgrounds))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.HIDE_PROFILE_BACKGROUNDS)));
            items.add(UItem.asShadow(getString(R.string.BygramProfileBackgroundInfo)));
            items.add(UItem.asCheck(1, getString(R.string.BygramCustomProfileMusic))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.CUSTOM_PROFILE_MUSIC)));
            items.add(UItem.asShadow(getString(R.string.BygramCustomProfileMusicInfo)));
            int mode = BygramConfig.getInt(BygramConfig.PROFILE_ID_MODE, 0);
            String[] modes = getContext().getResources().getStringArray(R.array.BygramIdOptions);
            items.add(UItem.asButton(2, getString(R.string.BygramProfileId), modes[Math.max(0, Math.min(mode, modes.length - 1))]));
            items.add(UItem.asShadow(getString(R.string.BygramProfileIdModeInfo)));
            items.add(UItem.asCheck(3, getString(R.string.BygramHidePhone))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.HIDE_PHONE)));
            items.add(UItem.asShadow(getString(R.string.BygramHideOwnPhoneInfo)));
            items.add(UItem.asCheck(4, getString(R.string.BygramRelativeOnline))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.RELATIVE_ONLINE)));
            items.add(UItem.asShadow(null));
        }

        @Override String keyFor(int id) {
            return id == 1 ? BygramConfig.CUSTOM_PROFILE_MUSIC
                    : id == 3 ? BygramConfig.HIDE_PHONE : id == 4 ? BygramConfig.RELATIVE_ONLINE
                    : id == 6 ? BygramConfig.HIDE_PROFILE_BACKGROUNDS
                    : id == 7 ? BygramConfig.FULL_PROFILE_BACKGROUND : null;
        }

        @Override void onCustomClick(int id) {
            if (id == 5) {
                showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                        .setTitle(getString(R.string.BygramProfileBackground))
                        .setItems(new CharSequence[]{getString(R.string.BygramChooseBackground), getString(R.string.Delete)}, (dialog, which) -> {
                            if (which == 0) {
                                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
                                intent.setType("image/*");
                                intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                                startActivityForResult(intent, 5081);
                            } else if (which == 1) { pendingBackground = null; publishBackground(); }
                        }).create());
                return;
            }
            if (id != 2) return;
            String[] modes = getContext().getResources().getStringArray(R.array.BygramIdOptions);
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.BygramProfileId))
                    .setItems(modes, (dialog, which) -> {
                        setIntSetting(BygramConfig.PROFILE_ID_MODE, which);
                        listView.adapter.update(true);
                    }).create());
        }

        @Override public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            if (requestCode == 5081 && resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
                pendingBackground = data.getData();
                publishBackground();
            }
        }

        private void publishBackground() {
            if (backgroundPublishing) return;
            backgroundPublishing = true;
            org.telegram.ui.ActionBar.AlertDialog progress = new org.telegram.ui.ActionBar.AlertDialog(getContext(), 3);
            progress.setCanCancel(false);
            showDialog(progress);
            progress.setCanceledOnTouchOutside(false);
            progress.setCancelable(false);
            String feature = pendingBackground == null ? "remove_background" : "publish_background";
            logSetting("started", feature);
            BygramTypingController.publishProfileBackground(currentAccount, pendingBackground, (ok, authUrl) -> {
                logPublishResult(feature, ok, authUrl);
                backgroundPublishing = false;
                progress.dismiss();
                if (getContext() == null) return;
                if (ok) {
                    pendingBackground = null;
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.BygramBackgroundPublished)).show();
                } else if (!authUrl.isEmpty()) {
                    showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.BygramProfileBackground))
                            .setMessage(getString(R.string.BygramBackgroundVerify))
                            .setNegativeButton(getString(R.string.Cancel), null)
                            .setPositiveButton(getString(R.string.BygramTypingOpenLogin), (dialog, which) -> {
                                verifyBackgroundOnResume = true;
                                BygramTypingController.openAccountLogin(getContext(), authUrl);
                            }).create());
                } else {
                    showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.BygramProfileBackground)).setMessage(getString(R.string.BygramBackgroundFailed))
                            .setPositiveButton(getString(R.string.OK), null).create());
                }
            });
        }

        @Override public void onResume() {
            super.onResume();
            if (verifyBackgroundOnResume) {
                verifyBackgroundOnResume = false;
                publishBackground();
            }
        }

        @Override public boolean onBackPressed(boolean invoked) {
            return !backgroundPublishing && super.onBackPressed(invoked);
        }

        @Override public boolean canBeginSlide() {
            return !backgroundPublishing && super.canBeginSlide();
        }

        @Override public boolean dismissDialogOnPause(android.app.Dialog dialog) {
            return !backgroundPublishing && super.dismissDialogOnPause(dialog);
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) listView.adapter.update(true);
    }

    public static class OtherSettingsActivity extends BygramSettingsPage {
        private UniversalRecyclerView listView;

        @Override public View createView(Context context) {
            simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
            simpleActionBar.setTitle(getString(R.string.BygramOther));
            simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
            });
            FrameLayout content = createSettingsContent(context);
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            listView = new SettingsList(this, (items, adapter) -> {
                items.add(UItem.asHeader(getString(R.string.BygramFirebase)).setId(-1));
                items.add(UItem.asCheck(4, getString(R.string.BygramFirebaseCrashlytics))
                        .setChecked(BygramConfig.getBoolean(BygramConfig.FIREBASE_CRASHLYTICS)));
                items.add(UItem.asCheck(5, getString(R.string.BygramFirebaseAnalytics))
                        .setChecked(BygramConfig.getBoolean(BygramConfig.FIREBASE_ANALYTICS)));
                items.add(UItem.asShadow(getString(R.string.BygramTelemetryInfo)));
                items.add(UItem.asButton(1, getString(R.string.BygramExportSettings)));
                items.add(UItem.asButton(2, getString(R.string.BygramResetSettings)));
                items.add(UItem.asShadow(getString(R.string.BygramExportInfo)));
            }, (item, view, position, x, y) -> {
                if (item.id == 1) {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
                    intent.setType("application/json");
                    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                    intent.putExtra(android.content.Intent.EXTRA_TITLE, "BYgram-settings.json");
                    try { startActivityForResult(intent, 9201); }
                    catch (Exception e) { showError(e); }
                } else if (item.id == 2) {
                    showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.BygramResetSettings)).setMessage(getString(R.string.BygramResetInfo))
                            .setPositiveButton(getString(R.string.Reset), (dialog, which) -> {
                                changeSetting("reset_settings", () -> {
                                    BygramConfig.resetSettings();
                                    LauncherIconController.setIcon(LauncherIconController.LauncherIcon.DEFAULT);
                                });
                                finishFragment();
                            }).setNegativeButton(getString(R.string.Cancel), null).create());
                } else if (item.id == 4) {
                    changeSetting(BygramConfig.FIREBASE_CRASHLYTICS, () -> {
                        BygramConfig.setBoolean(BygramConfig.FIREBASE_CRASHLYTICS, !BygramConfig.getBoolean(BygramConfig.FIREBASE_CRASHLYTICS));
                        BygramConfig.applyTelemetrySettings();
                    });
                    listView.adapter.update(true);
                } else if (item.id == 5) {
                    changeSetting(BygramConfig.FIREBASE_ANALYTICS, () -> {
                        BygramConfig.setBoolean(BygramConfig.FIREBASE_ANALYTICS, !BygramConfig.getBoolean(BygramConfig.FIREBASE_ANALYTICS));
                        BygramConfig.applyTelemetrySettings();
                    });
                    listView.adapter.update(true);
                }
            }, null);
            listView.setSections();
            content.addView(listView, LayoutHelper.createFrame(-1, -1));
            return fragmentView = content;
        }

        private void showError(Exception error) {
            logSettingFailure("export_settings", error);
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext()).setTitle(getString(R.string.ErrorOccurred))
                    .setMessage(getString(R.string.BygramExportFailed)).setPositiveButton(getString(R.string.OK), null).create());
        }

        @Override public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            if (requestCode != 9201 || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
            try (java.io.OutputStream output = getContext().getContentResolver().openOutputStream(data.getData(), "wt")) {
                if (output == null) throw new java.io.IOException("Document provider returned no stream");
                output.write(BygramConfig.exportSettings().toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) { showError(e); return; }
            logSetting("success", "export_settings");
        }
    }

    public static class ActivitySharingSettingsActivity extends BygramSettingsPage {
        private final ArrayList<Long> userIds = new ArrayList<>();
        private UniversalRecyclerView listView;
        private boolean expanded;
        private String query = "";

        @Override
        public View createView(Context context) {
            expanded = expanded || linkedRow(this) == -2;
            simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
            simpleActionBar.setTitle(getString(R.string.BygramShareActivity));
            simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) finishFragment();
                }
            });
            simpleActionBar.createMenu().addItem(0, R.drawable.outline_header_search).setIsSearchField(true)
                    .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                        @Override public void onSearchExpand() { expanded = true; listView.adapter.update(true); }
                        @Override public void onSearchCollapse() { query = ""; listView.adapter.update(true); }
                        @Override public void onTextChanged(EditText editText) { query = editText.getText().toString().trim().toLowerCase(Locale.ROOT); listView.adapter.update(true); }
                    }).setSearchFieldHint(getString(R.string.Search));
            FrameLayout content = createSettingsContent(context);
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            listView = new SettingsList(this, this::fillItems, this::onClick, null);
            listView.setSections();
            listView.adapter.setApplyBackground(false);
            content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            return fragmentView = content;
        }

        private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            userIds.clear();
            Map<Long, Integer> selected = BygramConfig.getActivityPermissions(currentAccount);
            HashSet<Long> added = new HashSet<>();
            items.add(UItem.asCheck(800, getString(R.string.BygramShareActivityMaster)).setChecked(BygramConfig.isActivityEnabled(currentAccount)));
            items.add(UItem.asShadow(getString(R.string.BygramShareActivityPrivacy)));
            items.add(UItem.asHeader(getString(R.string.BygramActivitySelectedUsers)).setId(-1));
            for (Map.Entry<Long, Integer> entry : selected.entrySet()) {
                TLRPC.User user = getMessagesController().getUser(entry.getKey());
                String title = activityUserTitle(entry.getKey(), user);
                if (!query.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(query)) continue;
                int index = userIds.size();
                userIds.add(entry.getKey());
                int permissions = entry.getValue();
                items.add(UItem.asButtonCheck(100_000 + index, title, activityPermissionsSummary(permissions)).setChecked(true));
                items.add(UItem.asCheck(200_000 + index, "    " + getString(R.string.BygramActivityDialogsPermission)).setChecked((permissions & BygramConfig.ACTIVITY_DIALOGS) != 0));
                items.add(UItem.asCheck(300_000 + index, "    " + getString(R.string.BygramActivityProfilePermission)).setChecked((permissions & BygramConfig.ACTIVITY_PROFILE) != 0));
                items.add(UItem.asCheck(400_000 + index, "    " + getString(R.string.BygramActivityChatPermission)).setChecked((permissions & BygramConfig.ACTIVITY_CHAT) != 0));
                added.add(entry.getKey());
            }
            if (selected.isEmpty()) items.add(UItem.asShadow(getString(R.string.BygramActivityNobodySelected)));
            items.add(UItem.asButton(900, getString(R.string.BygramActivityAddUsers), expanded ? getString(R.string.BygramActivityHideUsers) : null));
            if (expanded) {
                items.add(UItem.asHeader(getString(R.string.BygramActivityAvailableUsers)).setId(-2));
                ArrayList<Long> candidates = new ArrayList<>();
                for (TLRPC.TL_contact contact : getContactsController().contacts) candidates.add(contact.user_id);
                for (TLRPC.Dialog dialog : getMessagesController().getAllDialogs()) candidates.add(dialog.id);
                for (long userId : candidates) {
                    if (!DialogObject.isUserDialog(userId) || !added.add(userId) || userId == getUserConfig().getClientUserId()) continue;
                    TLRPC.User user = getMessagesController().getUser(userId);
                    if (user != null && (user.bot || UserObject.isDeleted(user))) continue;
                    if (user == null) continue;
                    String title = activityUserTitle(userId, user);
                    if (!query.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(query)) continue;
                    int index = userIds.size();
                    userIds.add(userId);
                    items.add(UItem.asCheck(100_000 + index, title).setChecked(false));
                }
            }
            items.add(UItem.asShadow(getString(R.string.BygramShareActivityInfo)));
        }

        private String activityUserTitle(long userId, TLRPC.User user) {
            if (user == null) return "ID " + userId;
            String username = UserObject.getPublicUsername(user);
            return UserObject.getUserName(user) + (username == null || username.isEmpty() ? "" : " (@" + username + ")");
        }

        private String activityPermissionsSummary(int permissions) {
            ArrayList<String> values = new ArrayList<>();
            if ((permissions & BygramConfig.ACTIVITY_DIALOGS) != 0) values.add(getString(R.string.BygramActivityDialogsShort));
            if ((permissions & BygramConfig.ACTIVITY_PROFILE) != 0) values.add(getString(R.string.BygramActivityProfileShort));
            if ((permissions & BygramConfig.ACTIVITY_CHAT) != 0) values.add(getString(R.string.BygramActivityChatShort));
            return android.text.TextUtils.join(", ", values);
        }

        private void onClick(UItem item, View view, int position, float x, float y) {
            if (item.id == 800) {
                boolean enabled = !BygramConfig.isActivityEnabled(currentAccount);
                changeSetting("activity_sharing", () -> {
                    BygramConfig.setActivityEnabled(currentAccount, enabled);
                    if (enabled) BygramActivityController.updateViewers(currentAccount); else BygramActivityController.clear(currentAccount);
                });
                listView.adapter.update(true);
                return;
            }
            if (item.id == 900) {
                expanded = !expanded;
                listView.adapter.update(true);
                return;
            }
            int index = item.id - 100_000;
            int permission = 0;
            if (item.id >= 400_000) { index = item.id - 400_000; permission = BygramConfig.ACTIVITY_CHAT; }
            else if (item.id >= 300_000) { index = item.id - 300_000; permission = BygramConfig.ACTIVITY_PROFILE; }
            else if (item.id >= 200_000) { index = item.id - 200_000; permission = BygramConfig.ACTIVITY_DIALOGS; }
            if (index < 0 || index >= userIds.size()) return;
            long userId = userIds.get(index);
            int permissions = BygramConfig.getActivityPermissions(currentAccount).getOrDefault(userId, 0);
            if (permission != 0) permissions ^= permission;
            else permissions = permissions == 0 ? BygramConfig.ACTIVITY_CHAT : 0;
            BygramConfig.setActivityPermissions(currentAccount, userId, permissions);
            BygramActivityController.updateViewers(currentAccount);
            logSetting("changed", "activity_permissions");
            listView.adapter.update(true);
        }
    }

    private abstract static class ToggleSettingsActivity extends BygramSettingsPage {
        UniversalRecyclerView listView;

        abstract int getTitleRes();
        abstract void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter);
        abstract String keyFor(int id);

        @Override
        public View createView(Context context) {
            try {
                createControls(context);
            } catch (RuntimeException error) {
                logSettingFailure("create_controls", error);
                throw error;
            }
            simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
            simpleActionBar.setTitle(getString(getTitleRes()));
            simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) finishFragment();
                }
            });
            FrameLayout content = createSettingsContent(context);
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            listView = new SettingsList(this, this::fillToggleItems, this::onClick, null);
            listView.setSections();
            listView.adapter.setApplyBackground(false);
            content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            return fragmentView = content;
        }

        void onClick(UItem item, View view, int position, float x, float y) {
            String key = keyFor(item.id);
            if (key != null) {
                changeSetting(key, () -> {
                    BygramConfig.setBoolean(key, !BygramConfig.getBoolean(key));
                    onSettingChanged(key);
                    listView.adapter.update(true);
                });
            } else {
                onCustomClick(item.id);
            }
        }

        final void setIntSetting(String key, int value) {
            changeSetting(key, () -> BygramConfig.setInt(key, value));
        }

        void createControls(Context context) {}
        void onSettingChanged(String key) {}
        void onCustomClick(int id) {}
    }

    public static class GhostSettingsActivity extends ToggleSettingsActivity {
        @Override void onSettingChanged(String key) {
            if (BygramConfig.GHOST_NO_ONLINE.equals(key) || BygramConfig.GHOST_ENABLED.equals(key)) {
                getMessagesController().updateTimerProc();
            }
        }

        @Override int getTitleRes() { return R.string.BygramGhostMode; }

        @Override
        void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader(getString(R.string.BygramGhostMode)).setId(-1));
            items.add(UItem.asCheck(1, BygramConfig.isGhostEnabled()
                    ? LocaleController.formatString(R.string.BygramGhostMaster, BygramConfig.ghostOptionsEnabled())
                    : getString(R.string.BygramGhostMode)).setChecked(BygramConfig.isGhostEnabled()));
            if (BygramConfig.isGhostEnabled() || linkedRow(this) > 1) {
                items.add(UItem.asCheck(2, getString(R.string.BygramNoReadMessages)).setChecked(BygramConfig.getBoolean(BygramConfig.GHOST_NO_READ)));
                items.add(UItem.asCheck(3, getString(R.string.BygramNoReadStories)).setChecked(BygramConfig.getBoolean(BygramConfig.GHOST_NO_STORY_READ)));
                items.add(UItem.asCheck(4, getString(R.string.BygramNoOnline)).setChecked(BygramConfig.getBoolean(BygramConfig.GHOST_NO_ONLINE)));
                items.add(UItem.asCheck(5, getString(R.string.BygramNoTyping)).setChecked(BygramConfig.getBoolean(BygramConfig.GHOST_NO_TYPING)));
                items.add(UItem.asCheck(7, getString(R.string.BygramReadOnActions)).setChecked(BygramConfig.getBoolean(BygramConfig.GHOST_READ_ON_ACTIONS)));
                items.add(UItem.asShadow(getString(R.string.BygramReadOnActionsInfo)));
            }
            items.add(UItem.asShadow(getString(R.string.BygramGhostInfo)));
        }

        @Override
        String keyFor(int id) {
            switch (id) {
                case 1: return BygramConfig.GHOST_ENABLED;
                case 2: return BygramConfig.GHOST_NO_READ;
                case 3: return BygramConfig.GHOST_NO_STORY_READ;
                case 4: return BygramConfig.GHOST_NO_ONLINE;
                case 5: return BygramConfig.GHOST_NO_TYPING;
                case 7: return BygramConfig.GHOST_READ_ON_ACTIONS;
                default: return null;
            }
        }
    }

    public static class SpySettingsActivity extends ToggleSettingsActivity {
        @Override int getTitleRes() { return R.string.BygramSpyMode; }

        @Override
        void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader(getString(R.string.BygramSpyMode)).setId(-1));
            items.add(UItem.asCheck(1, getString(R.string.BygramSaveDeleted)).setChecked(BygramConfig.getBoolean(BygramConfig.SPY_SAVE_DELETED)));
            items.add(UItem.asCheck(3, getString(R.string.BygramSaveOwnDeleted)).setChecked(BygramConfig.getBoolean(BygramConfig.SPY_SAVE_OWN_DELETED)));
            items.add(UItem.asCheck(7, getString(R.string.BygramNotifyDeleted)).setChecked(BygramConfig.getBoolean(BygramConfig.SPY_NOTIFY_DELETED)));
            items.add(UItem.asCheck(2, getString(R.string.BygramSaveEdits)).setChecked(BygramConfig.getBoolean(BygramConfig.SPY_SAVE_EDITS)));
            items.add(UItem.asShadow(null));
            items.add(UItem.asCheck(4, getString(R.string.BygramSaveReadDate)).setChecked(BygramConfig.getBoolean(BygramConfig.SPY_SAVE_READ_DATE)));
            items.add(UItem.asShadow(getString(R.string.BygramSaveReadDateInfo)));
            items.add(UItem.asCheck(5, getString(R.string.BygramSaveLastOnline)).setChecked(BygramConfig.getBoolean(BygramConfig.SPY_SAVE_LAST_ONLINE)));
            items.add(UItem.asShadow(getString(R.string.BygramSaveLastOnlineInfo)));
            items.add(UItem.asButton(6, getString(R.string.BygramClearSpyData)));
        }

        @Override
        String keyFor(int id) {
            switch (id) {
                case 1: return BygramConfig.SPY_SAVE_DELETED;
                case 3: return BygramConfig.SPY_SAVE_OWN_DELETED;
                case 7: return BygramConfig.SPY_NOTIFY_DELETED;
                case 2: return BygramConfig.SPY_SAVE_EDITS;
                case 4: return BygramConfig.SPY_SAVE_READ_DATE;
                case 5: return BygramConfig.SPY_SAVE_LAST_ONLINE;
                default: return null;
            }
        }

        @Override
        void onCustomClick(int id) {
            if (id != 6) return;
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.BygramClearSpyData))
                    .setMessage(getString(R.string.BygramClearSpyDataConfirm))
                    .setPositiveButton(getString(R.string.Delete), (dialog, which) -> changeSetting("clear_spy_data", BygramConfig::clearSpyData))
                    .setNegativeButton(getString(R.string.Cancel), null).create());
        }
    }
}
