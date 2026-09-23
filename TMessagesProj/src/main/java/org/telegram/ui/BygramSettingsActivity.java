package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.TextView;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.BygramConfig;
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
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
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

public class BygramSettingsActivity extends BaseFragment {

    private UniversalRecyclerView listView;
    private boolean waitingForLicense;

    @Override
    public View createView(Context context) {
        BygramConfig.clearRemovedCustomizationSettings();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.BygramSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView, true);
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopViewStatic("BYgram", "v" + BuildVars.BUILD_VERSION_STRING, R.drawable.bygram_logo));
        items.add(UItem.asHeader(getString(R.string.BygramCategories)));
        items.add(UItem.asButton(9, R.drawable.settings_account, getString(R.string.BygramCustomization)));
        items.add(UItem.asButton(8, R.drawable.settings_account, getString(R.string.BygramOther)));
        items.add(UItem.asButton(1, R.drawable.settings_privacy, getString(R.string.BygramGhostMode)));
        items.add(UItem.asButton(2, R.drawable.settings_devices, getString(R.string.BygramSpyMode)));
        if (BuildConfig.BYGRAM_FULL) {
            items.add(UItem.asButton(3, R.drawable.msg_location, getString(R.string.BygramShareActivity)));
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.BygramLinks)));
        items.add(UItem.asButton(10, R.drawable.msg_channel, getString(R.string.BygramChannel)));
        items.add(UItem.asButton(11, R.drawable.msg_discussion, getString(R.string.BygramChat)));
        items.add(UItem.asButton(12, R.drawable.msg_link, getString(R.string.BygramTranslations)));
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 8) {
            presentFragment(new OtherSettingsActivity());
        } else if (item.id == 9) {
            presentFragment(new CustomizationSettingsActivity());
        } else if (item.id == 1) {
            presentFragment(new GhostSettingsActivity());
        } else if (item.id == 2) {
            presentFragment(new SpySettingsActivity());
        } else if (item.id == 3) {
            openActivitySharing();
        } else if (item.id == 10) {
            org.telegram.messenger.browser.Browser.openUrl(getContext(), "https://t.me/BYgram_official");
        } else if (item.id == 11) {
            org.telegram.messenger.browser.Browser.openUrl(getContext(), "https://t.me/BYgram_community");
        } else if (item.id == 12) {
            org.telegram.messenger.browser.Browser.openUrl(getContext(), "https://github.com/BYxarek/BYgram-translations");
        }
    }

    public static class CustomizationSettingsActivity extends ToggleSettingsActivity {
        @Override int getTitleRes() { return R.string.BygramCustomization; }

        @Override void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader(getString(R.string.BygramCustomization)));
            items.add(UItem.asButton(1, R.drawable.settings_account, getString(R.string.BygramProfile)));
            items.add(UItem.asButton(2, R.drawable.settings_chat, getString(R.string.BygramGeneral)));
            items.add(UItem.asButton(3, R.drawable.settings_chat, getString(R.string.BygramCustomTyping)));
            items.add(UItem.asShadow(null));
        }

        @Override String keyFor(int id) { return null; }

        @Override void onCustomClick(int id) {
            if (id == 1) presentFragment(new ProfileCustomizationActivity());
            else if (id == 2) presentFragment(new GeneralCustomizationActivity());
            else if (id == 3) presentFragment(new TypingCustomizationActivity());
        }
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
            items.add(UItem.asHeader(getString(R.string.BygramCustomTyping)));
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
            EditText input = new EditText(getContext());
            input.setSingleLine(true);
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
            input.setText(BygramConfig.getCustomTyping(currentAccount, action));
            input.setSelection(input.length());
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(LABELS[action]))
                    .setView(input)
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                        BygramConfig.setCustomTyping(currentAccount, action, input.getText().toString());
                        listView.adapter.update(true);
                        publish();
                    }).create());
        }

        private void publish() {
            BygramTypingController.publish(currentAccount, (ok, botUrl) -> {
                if (getContext() == null || ok) return;
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

    public static class GeneralCustomizationActivity extends BaseFragment {
        private UniversalRecyclerView listView;
        private boolean glassExpanded;
        private boolean md3Expanded;

        @Override
        public View createView(Context context) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(getString(R.string.BygramGeneral));
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
                    LauncherIconController.LauncherIcon.VINTAGE,
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
            listView = new UniversalRecyclerView(this, (items, adapter) -> {
                items.add(UItem.asHeader(getString(R.string.BygramAppIcons)));
                items.add(UItem.asCustom(icons, 104));
                items.add(UItem.asShadow(null));
                items.add(UItem.asCheck(1, getString(R.string.BygramFilterZalgo))
                        .setChecked(BygramConfig.getBoolean(BygramConfig.FILTER_ZALGO)));
                items.add(UItem.asShadow(getString(R.string.BygramFilterZalgoInfo)));
                int md3 = (BygramConfig.getBoolean(BygramConfig.MD3_LOADING) ? 1 : 0)
                        + (BygramConfig.getBoolean(BygramConfig.MD3_SLIDERS) ? 1 : 0)
                        + (BygramConfig.getBoolean(BygramConfig.MD3_SWITCHES) ? 1 : 0);
                items.add(UItem.asButton(3, getString(R.string.BygramMaterial3),
                        LocaleController.formatString(R.string.BygramLiquidGlassSelected, md3)));
                if (md3Expanded) {
                    items.add(UItem.asCheck(200, getString(R.string.BygramMd3Loading))
                            .setChecked(BygramConfig.getBoolean(BygramConfig.MD3_LOADING)));
                    items.add(UItem.asCheck(201, getString(R.string.BygramMd3Sliders))
                            .setChecked(BygramConfig.getBoolean(BygramConfig.MD3_SLIDERS)));
                    items.add(UItem.asCheck(202, getString(R.string.BygramMd3Switches))
                            .setChecked(BygramConfig.getBoolean(BygramConfig.MD3_SWITCHES)));
                }
                items.add(UItem.asShadow(null));
                items.add(UItem.asHeader(getString(R.string.BygramAvatarCorners)));
                items.add(UItem.asCustom(avatarCorners));
                items.add(UItem.asShadow(null));
                int glass = BygramConfig.getInt(BygramConfig.LIQUID_GLASS_PLACEMENTS, BygramConfig.GLASS_ALL);
                String glassValue = glass == 0 ? getString(R.string.BygramLiquidGlassDisabled)
                        : glass == BygramConfig.GLASS_ALL ? getString(R.string.BygramLiquidGlassEverywhere)
                        : LocaleController.formatString(R.string.BygramLiquidGlassSelected, Integer.bitCount(glass));
                items.add(UItem.asButton(2, getString(R.string.BygramLiquidGlass), glassValue));
                if (glassExpanded) {
                    String[] places = getContext().getResources().getStringArray(R.array.BygramLiquidGlassPlaces);
                    int[] flags = {BygramConfig.GLASS_MEDIA, BygramConfig.GLASS_CONTEXT_PANELS,
                            BygramConfig.GLASS_FLOATING_BUTTONS, BygramConfig.GLASS_SEARCH,
                            BygramConfig.GLASS_SELECTION, BygramConfig.GLASS_POPUPS,
                            BygramConfig.GLASS_MESSAGE_MENUS, BygramConfig.GLASS_BUTTON_MENUS};
                    String[] examples = getContext().getResources().getStringArray(R.array.BygramLiquidGlassExamples);
                    for (int i = 0; i < flags.length && i < places.length && i < examples.length; i++) {
                        items.add(UItem.asButtonCheck(100 + i, places[i], examples[i]).setChecked((glass & flags[i]) != 0));
                    }
                }
                items.add(UItem.asShadow(getString(R.string.BygramLiquidGlassInfo)));
            }, (item, view, position, x, y) -> {
                if (item.id == 1) {
                    BygramConfig.setBoolean(BygramConfig.FILTER_ZALGO, !BygramConfig.getBoolean(BygramConfig.FILTER_ZALGO));
                } else if (item.id == 2) {
                    glassExpanded = !glassExpanded;
                } else if (item.id == 3) {
                    md3Expanded = !md3Expanded;
                } else if (item.id >= 200 && item.id <= 202) {
                    String[] keys = {BygramConfig.MD3_LOADING, BygramConfig.MD3_SLIDERS, BygramConfig.MD3_SWITCHES};
                    String key = keys[item.id - 200];
                    BygramConfig.setBoolean(key, !BygramConfig.getBoolean(key));
                } else if (item.id >= 100 && item.id < 108) {
                    int flag = 1 << (item.id - 100);
                    int mask = BygramConfig.getInt(BygramConfig.LIQUID_GLASS_PLACEMENTS, BygramConfig.GLASS_ALL);
                    BygramConfig.setInt(BygramConfig.LIQUID_GLASS_PLACEMENTS, mask ^ flag);
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

        @Override int getTitleRes() { return R.string.BygramProfile; }

        @Override void fillToggleItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader(getString(R.string.BygramProfile)));
            items.add(UItem.asCheck(1, getString(R.string.BygramCustomProfileMusic))
                    .setChecked(BygramConfig.getBoolean(BygramConfig.CUSTOM_PROFILE_MUSIC)));
            items.add(UItem.asShadow(getString(R.string.BygramCustomProfileMusicInfo)));
            int mode = BygramConfig.getInt(BygramConfig.PROFILE_ID_MODE, 0);
            String[] modes = getContext().getResources().getStringArray(R.array.BygramIdOptions);
            items.add(UItem.asButton(2, getString(R.string.BygramProfileId), modes[Math.max(0, Math.min(mode, modes.length - 1))]));
            items.add(UItem.asShadow(getString(R.string.BygramProfileIdModeInfo)));
        }

        @Override String keyFor(int id) {
            return id == 1 ? BygramConfig.CUSTOM_PROFILE_MUSIC : null;
        }

        @Override void onCustomClick(int id) {
            if (id != 2) return;
            String[] modes = getContext().getResources().getStringArray(R.array.BygramIdOptions);
            showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.BygramProfileId))
                    .setItems(modes, (dialog, which) -> {
                        BygramConfig.setInt(BygramConfig.PROFILE_ID_MODE, which);
                        listView.adapter.update(true);
                    }).create());
        }

    }

    private void openActivitySharing() {
        if (!BuildConfig.BYGRAM_FULL) return;
        if (BygramActivityController.isLicensed(currentAccount)) {
            presentFragment(new ActivitySharingSettingsActivity());
            return;
        }
        BygramActivityController.refreshLicense(currentAccount, true, this::handleLicenseResult);
    }

    private void handleLicenseResult(Boolean licensed, String botUrl) {
        if (Boolean.TRUE.equals(licensed)) {
            waitingForLicense = false;
            dismissCurrentDialog();
            presentFragment(new ActivitySharingSettingsActivity());
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

    public static class OtherSettingsActivity extends BaseFragment {
        private UniversalRecyclerView listView;

        @Override public View createView(Context context) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(getString(R.string.BygramOther));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
            });
            FrameLayout content = new FrameLayout(context);
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            listView = new UniversalRecyclerView(this, (items, adapter) -> {
                items.add(UItem.asHeader(getString(R.string.BygramFirebase)));
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

    public static class ActivitySharingSettingsActivity extends BaseFragment {
        private final ArrayList<Long> userIds = new ArrayList<>();
        private UniversalRecyclerView listView;
        private boolean expanded;
        private String query = "";

        @Override
        public View createView(Context context) {
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
            listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
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
            items.add(UItem.asHeader(getString(R.string.BygramActivitySelectedUsers)));
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
                items.add(UItem.asHeader(getString(R.string.BygramActivityAvailableUsers)));
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

    private abstract static class ToggleSettingsActivity extends BaseFragment {
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
            listView = new UniversalRecyclerView(this, (items, adapter) -> {
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
            items.add(UItem.asHeader(getString(R.string.BygramGhostMode)));
            items.add(UItem.asCheck(1, BygramConfig.isGhostEnabled()
                    ? LocaleController.formatString(R.string.BygramGhostMaster, BygramConfig.ghostOptionsEnabled())
                    : getString(R.string.BygramGhostMode)).setChecked(BygramConfig.isGhostEnabled()));
            if (BygramConfig.isGhostEnabled()) {
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
            items.add(UItem.asHeader(getString(R.string.BygramSpyMode)));
            items.add(UItem.asCheck(1, getString(R.string.BygramSaveDeleted)).setChecked(BygramConfig.getBoolean(BygramConfig.SPY_SAVE_DELETED)));
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
