package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
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
import org.telegram.messenger.Utilities;
import org.telegram.messenger.BygramChatPrivacy;
import org.telegram.messenger.BygramActivityController;
import org.telegram.messenger.BygramTypingController;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
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

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar bar = super.createActionBar(context);
        bar.setDrawGlassTitle(false);
        bar.setOpaquePageBackground();
        return bar;
    }
}

public class BygramSettingsActivity extends BygramSettingsPage {

    private UniversalRecyclerView listView;
    private boolean waitingForLicense;
    private boolean linkOpened;

    public static BaseFragment forLink(BygramSettingsLink target) {
        BygramSettingsPage page;
        switch (target.page) {
            case "customization": page = new CustomizationSettingsActivity(); break;
            case "general": page = new CustomizationSettingsActivity(); break;
            case "profile": page = new CustomizationSettingsActivity(); break;
            case "typing": page = BuildConfig.BYGRAM_FULL ? new TypingCustomizationActivity() : new BygramSettingsActivity(); break;
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
        presentFragment(page);
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
            else if ("activity".equals(linkTarget.page)) openActivitySharing();
        });
    }

    private static String settingsPage(BaseFragment fragment) {
        if (fragment instanceof BygramSettingsActivity) return "root";
        if (fragment instanceof CustomizationSettingsActivity) return "customization";
        if (fragment instanceof GeneralCustomizationActivity) return "general";
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
        if (target != null && fragment instanceof GeneralCustomizationActivity) {
            if ("general".equals(target.page)) return target.row;
            if ("profile".equals(target.page)) return target.row > 0 ? target.row + 300 : target.row == -1 ? -301 : 0;
        }
        return target != null && settingsPage(fragment).equals(target.page) ? target.row : 0;
    }

    public static boolean showLinkMenu(BaseFragment fragment, View view, UniversalRecyclerView list, String target) {
        if (target == null) return false;
        String link = BygramSettingsLink.PREFIX + target;
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
        private final BaseFragment fragment;
        private boolean focusedLink;

        SettingsList(BaseFragment fragment, Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fill,
                Utilities.Callback5<UItem, View, Integer, Float, Float> click,
                Utilities.Callback5Return<UItem, View, Integer, Float, Float, Boolean> longClick) {
            super(fragment, fill, click, longClick);
            this.fragment = fragment;
            setOnItemLongClickListener((view, position, x, y) -> {
                UItem item = adapter.getItem(position);
                if (item == null) return false;
                String page = settingsPage(fragment);
                int row = item.id;
                if (fragment instanceof GeneralCustomizationActivity) {
                    page = row > 300 && row < 400 || row == -301 ? "profile" : "general";
                    if (row > 300 && row < 400) row -= 300;
                    else if (row == -301) row = -1;
                    if (row == 400) return showLinkMenu(fragment, view, this, "typing");
                }
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
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new SettingsList(this, this::fillItems, this::onClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopViewStatic("BYgram", "v" + BuildVars.BUILD_VERSION_STRING, R.drawable.bygram_logo_no_fon));
        items.add(UItem.asHeader(getString(R.string.BygramCategories)).setId(-1));
        items.add(UItem.asButton(9, R.drawable.settings_account, getString(R.string.BygramCustomization)));
        items.add(UItem.asButton(1, R.drawable.settings_privacy, getString(R.string.BygramGhostMode)));
        items.add(UItem.asButton(2, R.drawable.settings_devices, getString(R.string.BygramSpyMode)));
        if (BuildConfig.BYGRAM_FULL) {
            items.add(UItem.asButton(3, R.drawable.msg_location, getString(R.string.BygramShareActivity)));
        }
        items.add(UItem.asButton(5, R.drawable.settings_privacy, getString(R.string.BygramHiddenChats)));
        items.add(UItem.asButton(6, R.drawable.settings_privacy, getString(R.string.BygramProtectedChats)));
        items.add(UItem.asButton(13, R.drawable.bygram_footprints, getString(R.string.BygramProfileViews)));
        items.add(UItem.asButton(8, R.drawable.settings_account, getString(R.string.BygramOther)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.BygramLinks)).setId(-2));
        items.add(UItem.asButton(10, R.drawable.msg_channel, getString(R.string.BygramChannel)));
        items.add(UItem.asButton(11, R.drawable.msg_discussion, getString(R.string.BygramChat)));
        items.add(UItem.asButton(12, R.drawable.msg_link, getString(R.string.BygramTranslations)));
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 8) {
            presentSettingsPage(new OtherSettingsActivity());
        } else if (item.id == 9) {
            presentSettingsPage(new CustomizationSettingsActivity());
        } else if (item.id == 1) {
            presentSettingsPage(new GhostSettingsActivity());
        } else if (item.id == 2) {
            presentSettingsPage(new SpySettingsActivity());
        } else if (item.id == 3) {
            openActivitySharing();
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
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(getString(R.string.BygramProfileViews));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
            });
            FrameLayout content = new FrameLayout(context);
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            listView = new SettingsList(this, this::fillItems, (item, view, position, x, y) -> {
                if (item.id != 1) return;
                boolean enabled = !BygramTypingController.profileViewsEnabled(currentAccount);
                JSONObject data = new JSONObject();
                try { data.put("action", "profile_views_set"); data.put("enabled", enabled); }
                catch (Exception error) { org.telegram.messenger.FileLog.e(error); return; }
                BygramTypingController.profileViewsRequest(currentAccount, data, (response, botUrl) -> {
                    if (!botUrl.isEmpty()) {
                        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                                .setTitle(getString(R.string.BygramProfileViews))
                                .setMessage(getString(R.string.BygramProfileViewsVerify))
                                .setPositiveButton(getString(R.string.OK), (dialog, which) -> org.telegram.messenger.browser.Browser.openUrl(getContext(), botUrl))
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
            catch (Exception error) { org.telegram.messenger.FileLog.e(error); return; }
            BygramTypingController.profileViewsRequest(currentAccount, data, (response, botUrl) -> {
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
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(name);
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
            });
            FrameLayout content = new FrameLayout(context);
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
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(getString(hidden ? R.string.BygramHiddenChats : R.string.BygramProtectedChats));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override public void onItemClick(int id) {
                    if (id == -1) finishFragment();
                    else if (id == 2) openChatPicker();
                }
            });
            if (!revealOnly) {
                actionBar.createMenu().addItemWithWidth(2, R.drawable.msg_add, AndroidUtilities.dp(54), getString(R.string.SelectChat));
            }
            FrameLayout content = new FrameLayout(context);
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
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.BygramPrivacyError))
                    .setPositiveButton(getString(R.string.OK), null).create());
        }
    }

    public static class CustomizationSettingsActivity extends GeneralCustomizationActivity {
    }

    public static class TypingCustomizationActivity extends ToggleSettingsActivity {
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
            items.add(UItem.asButton(200, getString(R.string.BygramTypingPublish)));
        }

        @Override String keyFor(int id) { return null; }

        @Override void onCustomClick(int id) {
            if (id == 200) { publish(); return; }
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
                        BygramConfig.setCustomTyping(currentAccount, action, input.getText().toString());
                        listView.adapter.update(true);
                        publish();
                    }).create());
        }

        private void publish() {
            BygramTypingController.publish(currentAccount, (ok, botUrl) -> {
                if (getContext() == null) return;
                if (ok) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                            getString(R.string.BygramTypingPublishSuccess)).show();
                    return;
                }
                if (!botUrl.isEmpty()) {
                    showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.BygramCustomTyping))
                            .setMessage(getString(R.string.BygramTypingVerify))
                            .setNegativeButton(getString(R.string.Cancel), null)
                            .setPositiveButton(getString(R.string.BygramTypingOpenBot), (dialog, which) ->
                                    org.telegram.messenger.browser.Browser.openUrl(getContext(), botUrl)).create());
                } else {
                    showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.BygramCustomTyping))
                            .setMessage(getString(R.string.BygramTypingPublishFailed))
                            .setPositiveButton(getString(R.string.OK), null).create());
                }
            });
        }
    }

    private static class TitlePreview extends FrameLayout {
        private final org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;
        TitlePreview(Context context, int account) {
            super(context);
            setMinimumHeight(AndroidUtilities.dp(72));
            org.telegram.ui.ActionBar.SimpleTextView title = new org.telegram.ui.ActionBar.SimpleTextView(context);
            title.setTextSize(17);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setText(BygramConfig.dialogsTitle(account));
            int position = BygramConfig.getInt(BygramConfig.TITLE_POSITION, 0);
            title.setGravity(Gravity.CENTER_VERTICAL | (position == 1 ? Gravity.CENTER_HORIZONTAL : position == 2 ? Gravity.RIGHT : Gravity.LEFT));
            emoji = new org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(title, AndroidUtilities.dp(26));
            Long documentId = UserObject.getEmojiStatusDocumentId(org.telegram.messenger.UserConfig.getInstance(account).getCurrentUser());
            if (documentId != null) emoji.set(documentId, false);
            title.setRightDrawable(emoji);
            addView(title, LayoutHelper.createFrame(-1, 48, Gravity.CENTER_VERTICAL, 20, 0, 20, 0));
            setContentDescription(getString(R.string.BygramHeaderPreview));
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); emoji.attach(); }
        @Override protected void onDetachedFromWindow() { emoji.detach(); super.onDetachedFromWindow(); }
    }

    public static class GeneralCustomizationActivity extends ProfileCustomizationActivity {
        private boolean md3Expanded;

        @Override
        public View createView(Context context) {
            md3Expanded = md3Expanded || linkedRow(this) >= 200 && linkedRow(this) <= 202;
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(getString(R.string.BygramCustomization));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) finishFragment();
                }
            });

            FrameLayout content = new FrameLayout(context);
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            AppIconsSelectorCell icons = new AppIconsSelectorCell(context, this, currentAccount, Arrays.asList(
                    LauncherIconController.LauncherIcon.DEFAULT,
                    LauncherIconController.LauncherIcon.AQUA));
            BackupImageView avatarPreview = new BackupImageView(context);
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(getUserConfig().getCurrentUser());
            avatarPreview.setForUserOrChat(getUserConfig().getCurrentUser(), avatarDrawable);
            FrameLayout avatarCorners = new FrameLayout(context) {
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
                        BygramConfig.setInt(BygramConfig.AVATAR_CORNERS, value);
                        avatarPreview.invalidate();
                    });
            avatarCorners.addView(cornersSlider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 75, Gravity.BOTTOM));
            listView = new SettingsList(this, (items, adapter) -> {
                ArrayList<UItem> profile = new ArrayList<>();
                super.fillToggleItems(profile, adapter);
                for (UItem row : profile) {
                    if (row.id > 0) row.id += 300;
                    else if (row.id == -1) row.id = -301;
                    items.add(row);
                }
                if (BuildConfig.BYGRAM_FULL) items.add(UItem.asButton(400, getString(R.string.BygramCustomTyping)));
                items.add(UItem.asHeader(getString(R.string.BygramAppIcons)).setId(-1));
                items.add(UItem.asCustom(icons, 104).setId(-1));
                items.add(UItem.asShadow(null));
                items.add(UItem.asCheck(1, getString(R.string.BygramFilterZalgo))
                        .setChecked(BygramConfig.getBoolean(BygramConfig.FILTER_ZALGO)));
                items.add(UItem.asShadow(getString(R.string.BygramFilterZalgoInfo)));
                items.add(UItem.asHeader(getString(R.string.BygramChatListSection)));
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
                items.add(UItem.asHeader(getString(R.string.BygramFeedbackSection)));
                items.add(UItem.asCheck(9, getString(R.string.BygramAppVibration))
                        .setChecked(!BygramConfig.getBoolean(BygramConfig.DISABLE_VIBRATION)));
                if (BygramConfig.getBoolean(BygramConfig.DISABLE_VIBRATION)) {
                    items.add(UItem.asCheck(10, "    " + getString(R.string.BygramCallVibration))
                            .setChecked(BygramConfig.getBoolean(BygramConfig.CALL_VIBRATION)));
                }
                items.add(UItem.asShadow(null));
                items.add(UItem.asHeader(getString(R.string.BygramHeaderSection)));
                items.add(UItem.asCustom(new TitlePreview(context, currentAccount)));
                String[] titles = getContext().getResources().getStringArray(R.array.BygramTitleOptions);
                String[] positions = getContext().getResources().getStringArray(R.array.BygramTitlePositionOptions);
                items.add(UItem.asButton(6, getString(R.string.BygramTitleText),
                        titles[Math.max(0, Math.min(2, BygramConfig.getInt(BygramConfig.TITLE_TEXT, 0)))]));
                items.add(UItem.asButton(7, getString(R.string.BygramTitlePosition),
                        positions[Math.max(0, Math.min(2, BygramConfig.getInt(BygramConfig.TITLE_POSITION, 0)))]));
                items.add(UItem.asShadow(null));
                int md3 = (BygramConfig.getBoolean(BygramConfig.MD3_LOADING) ? 1 : 0)
                        + (BygramConfig.getBoolean(BygramConfig.MD3_SLIDERS) ? 1 : 0)
                        + (BygramConfig.getBoolean(BygramConfig.MD3_SWITCHES) ? 1 : 0);
                items.add(UItem.asButton(3, getString(R.string.BygramMaterial3),
                        md3 + "/3"));
                if (md3Expanded) {
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
            }, (item, view, position, x, y) -> {
                if (item.id == 1) {
                    BygramConfig.setBoolean(BygramConfig.FILTER_ZALGO, !BygramConfig.getBoolean(BygramConfig.FILTER_ZALGO));
                } else if (item.id == 3) {
                    md3Expanded = !md3Expanded;
                } else if (item.id == 4) {
                    BygramConfig.setBoolean(BygramConfig.COLLAPSE_SEARCH_ON_START,
                            !BygramConfig.getBoolean(BygramConfig.COLLAPSE_SEARCH_ON_START));
                } else if (item.id == 11) {
                    BygramConfig.setBoolean(BygramConfig.KEEP_SEARCH_COLLAPSED, !BygramConfig.getBoolean(BygramConfig.KEEP_SEARCH_COLLAPSED));
                } else if (item.id > 300 && item.id < 400) {
                    String key = keyFor(item.id - 300);
                    if (key != null) BygramConfig.setBoolean(key, !BygramConfig.getBoolean(key));
                    else onCustomClick(item.id - 300);
                } else if (item.id == 400) {
                    presentFragment(new TypingCustomizationActivity());
                } else if (item.id == 5) {
                    org.telegram.messenger.SharedConfig.toggleArchiveHidden();
                } else if (item.id == 8 || item.id == 9 || item.id == 10) {
                    String key = item.id == 8 ? BygramConfig.NO_UNARCHIVE_SWIPE
                            : item.id == 9 ? BygramConfig.DISABLE_VIBRATION : BygramConfig.CALL_VIBRATION;
                    BygramConfig.setBoolean(key, !BygramConfig.getBoolean(key));
                    if ((item.id == 9 && BygramConfig.getBoolean(key)
                            || item.id == 10 && !BygramConfig.getBoolean(key))
                            && !BygramConfig.isCallVibrationEnabled()) {
                        android.os.Vibrator vibrator = (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                        if (vibrator != null) vibrator.cancel();
                    }
                } else if (item.id == 6 || item.id == 7) {
                    boolean title = item.id == 6;
                    String[] options = getContext().getResources().getStringArray(title
                            ? R.array.BygramTitleOptions : R.array.BygramTitlePositionOptions);
                    showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                            .setTitle(getString(title ? R.string.BygramTitleText : R.string.BygramTitlePosition))
                            .setItems(Arrays.copyOf(options, 3), (dialog, which) -> {
                                BygramConfig.setInt(title ? BygramConfig.TITLE_TEXT : BygramConfig.TITLE_POSITION, which);
                                listView.adapter.update(true);
                            }).create());
                    return;
                } else if (item.id >= 200 && item.id <= 202) {
                    String[] keys = {BygramConfig.MD3_LOADING, BygramConfig.MD3_SLIDERS, BygramConfig.MD3_SWITCHES};
                    String key = keys[item.id - 200];
                    BygramConfig.setBoolean(key, !BygramConfig.getBoolean(key));
                } else {
                    return;
                }
                listView.adapter.update(true);
            }, null);
            listView.setSections();
            listView.adapter.setApplyBackground(false);
            content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            return fragmentView = content;
        }
    }

    public static class ProfileCustomizationActivity extends ToggleSettingsActivity {
        private android.net.Uri pendingBackground;
        private boolean pendingBackgroundChange;
        private boolean backgroundPublishing;

        @Override int getTitleRes() { return R.string.BygramProfile; }

        @Override void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader(getString(R.string.BygramProfile)).setId(-1));
            items.add(UItem.asButton(5, getString(R.string.BygramProfileBackground)));
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
                    : id == 6 ? BygramConfig.HIDE_PROFILE_BACKGROUNDS : null;
        }

        @Override void onCustomClick(int id) {
            if (id == 5) {
                showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                        .setTitle(getString(R.string.BygramProfileBackground))
                        .setItems(new CharSequence[]{getString(R.string.BygramChooseBackground), getString(R.string.Delete), getString(R.string.BygramPublishBackground)}, (dialog, which) -> {
                            if (which == 0) {
                                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
                                intent.setType("image/*");
                                intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                                startActivityForResult(intent, 5081);
                            } else if (which == 1) { pendingBackground = null; pendingBackgroundChange = true; publishBackground(); }
                            else if (pendingBackgroundChange) publishBackground();
                        }).create());
                return;
            }
            if (id != 2) return;
            String[] modes = getContext().getResources().getStringArray(R.array.BygramIdOptions);
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.BygramProfileId))
                    .setItems(modes, (dialog, which) -> {
                        BygramConfig.setInt(BygramConfig.PROFILE_ID_MODE, which);
                        listView.adapter.update(true);
                    }).create());
        }

        @Override public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            if (requestCode == 5081 && resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
                pendingBackground = data.getData();
                pendingBackgroundChange = true;
                publishBackground();
            }
        }

        private void publishBackground() {
            if (backgroundPublishing) return;
            backgroundPublishing = true;
            BygramTypingController.publishProfileBackground(currentAccount, pendingBackground, (ok, botUrl) -> {
                backgroundPublishing = false;
                if (getContext() == null) return;
                if (ok) {
                    pendingBackground = null;
                    pendingBackgroundChange = false;
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.BygramBackgroundPublished)).show();
                } else if (!botUrl.isEmpty()) {
                    showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.BygramProfileBackground))
                            .setMessage(getString(R.string.BygramBackgroundVerify))
                            .setNegativeButton(getString(R.string.Cancel), null)
                            .setPositiveButton(getString(R.string.BygramTypingOpenBot), (dialog, which) -> org.telegram.messenger.browser.Browser.openUrl(getContext(), botUrl)).create());
                } else {
                    showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.BygramProfileBackground)).setMessage(getString(R.string.BygramBackgroundFailed))
                            .setPositiveButton(getString(R.string.OK), null).create());
                }
            });
        }

    }

    private void openActivitySharing() {
        if (!BuildConfig.BYGRAM_FULL) return;
        if (BygramActivityController.isLicensed(currentAccount)) {
            presentSettingsPage(new ActivitySharingSettingsActivity());
            return;
        }
        BygramActivityController.refreshLicense(currentAccount, true, this::handleLicenseResult);
    }

    private void handleLicenseResult(Boolean licensed, String botUrl) {
        if (Boolean.TRUE.equals(licensed)) {
            waitingForLicense = false;
            dismissCurrentDialog();
            presentSettingsPage(new ActivitySharingSettingsActivity());
            return;
        }
        if (licensed == null) {
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.BygramLicenseTitle))
                    .setMessage(getString(R.string.BygramLicenseUnavailable))
                    .setPositiveButton(getString(R.string.OK), null).create());
            return;
        }
        JSONArray devices = BygramActivityController.getPendingDevices(currentAccount);
        if (devices.length() > 0) { showDeviceLimit(devices); return; }
        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.BygramLicenseTitle))
                .setMessage(botUrl == null || botUrl.isEmpty() ? getString(R.string.BygramLicenseMessage) : getString(R.string.BygramLicenseActivationMessage))
                .setNegativeButton(getString(R.string.Cancel), null);
        if (botUrl != null && !botUrl.isEmpty()) {
            builder.setPositiveButton(getString(botUrl.contains("start=") ? R.string.BygramLicenseActivate : R.string.BygramLicenseBuy), (dialog, which) -> {
                waitingForLicense = true;
                org.telegram.messenger.browser.Browser.openUrl(getContext(), BygramActivityController.purchaseUrl(botUrl));
            });
        }
        showDialog(builder.create());
    }

    private void showDeviceLimit(JSONArray devices) {
        String[] labels = new String[devices.length()];
        String[] ids = new String[devices.length()];
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.optJSONObject(i);
            labels[i] = device == null ? getString(R.string.BygramUnknownDevice) : device.optString("device_name", getString(R.string.BygramUnknownDevice));
            ids[i] = device == null ? "" : device.optString("id", "");
        }
        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.BygramDeviceLimitTitle))
                .setMessage(getString(R.string.BygramDeviceLimitMessage))
                .setItems(labels, (dialog, which) -> showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                        .setTitle(getString(R.string.BygramDeviceReplaceTitle))
                        .setMessage(LocaleController.formatString(R.string.BygramDeviceReplaceMessage, labels[which]))
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .setPositiveButton(getString(R.string.BygramDeviceReplace), (confirm, button) ->
                                BygramActivityController.replaceDevice(currentAccount, ids[which], this::handleLicenseResult)).create()))
                .setNegativeButton(getString(R.string.Cancel), null).create());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!waitingForLicense) return;
        BygramActivityController.refreshLicense(currentAccount, true, this::handleLicenseResult);
    }

    public static class OtherSettingsActivity extends BygramSettingsPage {
        private UniversalRecyclerView listView;

        @Override public View createView(Context context) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(getString(R.string.BygramOther));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
            });
            FrameLayout content = new FrameLayout(context);
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
                                BygramConfig.resetSettings();
                                LauncherIconController.setIcon(LauncherIconController.LauncherIcon.DEFAULT);
                                finishFragment();
                            }).setNegativeButton(getString(R.string.Cancel), null).create());
                } else if (item.id == 4) {
                    BygramConfig.setBoolean(BygramConfig.FIREBASE_CRASHLYTICS, !BygramConfig.getBoolean(BygramConfig.FIREBASE_CRASHLYTICS));
                    BygramConfig.applyTelemetrySettings();
                    listView.adapter.update(true);
                } else if (item.id == 5) {
                    BygramConfig.setBoolean(BygramConfig.FIREBASE_ANALYTICS, !BygramConfig.getBoolean(BygramConfig.FIREBASE_ANALYTICS));
                    BygramConfig.applyTelemetrySettings();
                    listView.adapter.update(true);
                }
            }, null);
            listView.setSections();
            content.addView(listView, LayoutHelper.createFrame(-1, -1));
            return fragmentView = content;
        }

        private void showError(Exception error) {
            org.telegram.messenger.FileLog.e(error);
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext()).setTitle(getString(R.string.ErrorOccurred))
                    .setMessage(getString(R.string.BygramExportFailed)).setPositiveButton(getString(R.string.OK), null).create());
        }

        @Override public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            if (requestCode != 9201 || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
            try (java.io.OutputStream output = getContext().getContentResolver().openOutputStream(data.getData(), "wt")) {
                if (output == null) throw new java.io.IOException("Document provider returned no stream");
                output.write(BygramConfig.exportSettings().toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) { showError(e); }
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
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(getString(R.string.BygramShareActivity));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) finishFragment();
                }
            });
            actionBar.createMenu().addItem(0, R.drawable.outline_header_search).setIsSearchField(true)
                    .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                        @Override public void onSearchExpand() { expanded = true; listView.adapter.update(true); }
                        @Override public void onSearchCollapse() { query = ""; listView.adapter.update(true); }
                        @Override public void onTextChanged(EditText editText) { query = editText.getText().toString().trim().toLowerCase(Locale.ROOT); listView.adapter.update(true); }
                    }).setSearchFieldHint(getString(R.string.Search));
            FrameLayout content = new FrameLayout(context);
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
                BygramConfig.setActivityEnabled(currentAccount, enabled);
                if (enabled) BygramActivityController.updateViewers(currentAccount); else BygramActivityController.clear(currentAccount);
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
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(getString(getTitleRes()));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) finishFragment();
                }
            });
            FrameLayout content = new FrameLayout(context);
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            listView = new SettingsList(this, (items, adapter) -> {
                fillToggleItems(items, adapter);
                for (UItem item : items) {
                    if (item.viewType == UniversalAdapter.VIEW_TYPE_CHECK) {
                        item.onBind(view -> ((TextCheckCell) view).setMultilineTextAndValue(null));
                    }
                }
            }, this::onClick, null);
            listView.setSections();
            listView.adapter.setApplyBackground(false);
            content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            return fragmentView = content;
        }

        void onClick(UItem item, View view, int position, float x, float y) {
            String key = keyFor(item.id);
            if (key != null) {
                BygramConfig.setBoolean(key, !BygramConfig.getBoolean(key));
                if (BygramConfig.GHOST_NO_ONLINE.equals(key) || BygramConfig.GHOST_ENABLED.equals(key)) {
                    getMessagesController().updateTimerProc();
                }
                listView.adapter.update(true);
            } else {
                onCustomClick(item.id);
            }
        }

        void onCustomClick(int id) {}
    }

    public static class GhostSettingsActivity extends ToggleSettingsActivity {
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
                    .setPositiveButton(getString(R.string.Delete), (dialog, which) -> BygramConfig.clearSpyData())
                    .setNegativeButton(getString(R.string.Cancel), null).create());
        }
    }
}
