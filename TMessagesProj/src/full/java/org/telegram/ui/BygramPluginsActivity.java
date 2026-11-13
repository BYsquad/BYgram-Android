package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.AndroidUtilities.dp;
import android.app.Activity;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.widget.CheckBox;
import android.widget.Switch;
import com.google.gson.*;
import org.telegram.messenger.*;
import org.telegram.messenger.plugins.*;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.*;

public final class BygramPluginsActivity extends BygramSettingsPage {
    private final PluginManager manager = PluginManager.get();
    private final String pluginId;
    private UniversalRecyclerView list;
    private TextView catalogButton;
    private boolean busy;
    private boolean destroyed;
    private String stagedToken;
    private BottomSheet installSheet;
    private PluginCatalog.Request catalogRequest;
    private long catalogGeneration;
    private AutoCloseable fileRequest;
    private long fileGeneration;
    private final Runnable changed = () -> { if (list != null) list.adapter.update(true); };
    public BygramPluginsActivity() { this(null); }
    private BygramPluginsActivity(String id) { pluginId = id; }

    @Override public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        manager.listen(changed); manager.refresh(); return true;
    }
    @Override public void onFragmentDestroy() {
        destroyed = true; manager.unlisten(changed);
        cancelCatalog();
        cancelFile();
        if (stagedToken != null) { manager.commit(stagedToken, false, null); stagedToken = null; }
        if (installSheet != null) { installSheet.dismiss(); installSheet = null; }
        catalogButton = null;
        list = null; super.onFragmentDestroy();
    }
    @Override public void onResume() { super.onResume(); manager.refresh(); }
    @Override public View createView(Context context) {
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(getString(R.string.BygramPluginsBeta));
        if (pluginId == null) simpleActionBar.createMenu().addItem(3, R.drawable.msg_info)
                .setContentDescription(getString(R.string.BygramPluginHelp));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                if (id == 3) message(getString(R.string.BygramPluginHelpText));
            }
        });
        FrameLayout content = createSettingsContent(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        list = new UniversalRecyclerView(this, this::fill, this::click, (item, view, position, x, y) ->
                BygramSettingsActivity.showLinkMenu(this, view, list, "plugins"));
        list.setSections(); list.adapter.setApplyBackground(false);
        content.addView(list, LayoutHelper.createFrame(-1, -1));
        if (pluginId == null) {
            catalogButton = PluginPackageSheet.primaryButton(context, getString(R.string.BygramPluginCatalog));
            catalogButton.setPadding(dp(24), 0, dp(24), 0);
            catalogButton.setOnClickListener(view -> {
                if (!busy && state().has("plugins")) openRepositories();
            });
            content.addView(catalogButton, LayoutHelper.createFrame(-2, 48, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 12, 0, 12, 10));
        }
        return fragmentView = content;
    }
    @Override public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        if (catalogButton != null) {
            catalogButton.setTranslationY(-bottom);
            BaseFragmentSimpleGlass.setListViewParams(list, top, bottom, ActionBar.getCurrentActionBarHeight(), dp(64));
        }
    }

    private JsonObject state() { return manager.snapshot(); }
    private JsonObject record() {
        JsonObject state = state();
        return state.has("plugins") && pluginId != null ? state.getAsJsonObject("plugins").getAsJsonObject(pluginId) : null;
    }
    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        JsonObject state = state();
        if (!state.has("plugins")) {
            items.add(UItem.asShadow(manager.failure().isEmpty() ? getString(R.string.Loading) : errorText(manager.failure())));
            if (pluginId == null) {
                items.add(UItem.asButton(5, getString(R.string.BygramPluginRecoveryTitle)));
                if (!manager.failure().isEmpty()) items.add(UItem.asButton(6, getString(R.string.BygramPluginResetRegistry)));
            }
            return;
        }
        if (pluginId == null) {
            items.add(UItem.asCustomShadow(1, engineCard(state.get("engine").getAsBoolean())));
            if (state.has("recovery_error")) items.add(UItem.asShadow(getString(R.string.BygramPluginErrorRecovery) + "\n" + state.get("recovery_error").getAsString()));
            if (state.has("recovered") && state.get("recovered").getAsBoolean()) items.add(UItem.asShadow(getString(R.string.BygramPluginRegistryRecovered)));
            if (!HookBridge.available()) items.add(UItem.asShadow(getString(R.string.BygramPluginHooksUnavailable)));
            JsonObject plugins = state.getAsJsonObject("plugins");
            if (plugins.size() == 0) items.add(UItem.asShadow(getString(R.string.BygramPluginEmpty)));
            ArrayList<String> ids = new ArrayList<>(plugins.keySet());
            ids.sort(Comparator.comparing(id -> !isPinned(plugins.getAsJsonObject(id))));
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                items.add(UItem.asCustomShadow(100 + i, pluginCard(id, plugins.getAsJsonObject(id))));
            }
            items.add(UItem.asButton(2, getString(R.string.BygramPluginInstall)));
            items.add(UItem.asButton(9, getString(R.string.BygramPluginSubmit)));
            items.add(UItem.asCheck(4, getString(R.string.BygramPluginSafeMode)).setChecked(state.get("safe").getAsBoolean()));
            items.add(UItem.asButton(5, getString(R.string.BygramPluginRecoveryTitle)));
        } else {
            JsonObject record = record();
            if (record == null) { items.add(UItem.asShadow(getString(R.string.BygramPluginEmpty))); return; }
            JsonObject manifest = record.getAsJsonObject("manifest");
            items.add(UItem.asHeader(manifest.get("name").getAsString()));
            if (record.has("catalog_release")) items.add(UItem.asShadow(releaseInfo(record)));
            items.add(UItem.asCheck(10, getString(R.string.Enable)).setChecked(record.get("desired").getAsBoolean()));
            items.add(UItem.asShadow(manifest.get("description").getAsString() + "\n" + pluginId + "\n"
                    + manifest.get("version").getAsString() + " · " + manifest.get("author").getAsString() + "\n"
                    + status(record) + "\n" + authorSignature(record) + "\nSHA-256: " + record.get("hash").getAsString()
                    + "\n" + capabilities(manifest) + "\n" + scope(manifest)
                    + "\n" + getString(record.has("source") && record.get("source").getAsString().equals("catalog")
                            ? R.string.BygramPluginCatalog : R.string.BygramPluginSourceLocal) + " · "
                    + android.text.format.DateFormat.getMediumDateFormat(getContext()).format(new Date(record.get("installed").getAsLong()))));
            items.add(UItem.asButton(11, getString(R.string.Settings)));
            items.add(UItem.asButton(12, getString(R.string.BygramPluginJournal)));
            items.add(UItem.asButton(16, getString(R.string.BygramPluginExportSettings)));
            items.add(UItem.asButton(17, getString(R.string.BygramPluginImportSettings)));
            items.add(UItem.asButton(18, getString(R.string.BygramPluginResetData)));
            if (manifest.getAsJsonArray("capabilities").contains(new JsonPrimitive("hooks.raw")))
                items.add(UItem.asShadow(getString(R.string.BygramPluginHookLifetime)));
            if (record.has("pending")) {
                JsonObject pending = record.getAsJsonObject("pending");
                items.add(UItem.asShadow(getString(R.string.BygramPluginPending) + "\n"
                        + pending.getAsJsonObject("manifest").get("version").getAsString() + "\nSHA-256: " + pending.get("hash").getAsString()
                        + "\n" + capabilities(pending.getAsJsonObject("manifest"))));
                items.add(UItem.asButton(19, getString(R.string.BygramPluginCancelUpdate)));
            }
            if (manifest.get("account_scope").getAsString().equals("selected_accounts")) items.add(UItem.asButton(15, getString(R.string.BygramPluginAccounts)));
            if (record.has("rollback")) items.add(UItem.asButton(13, getString(R.string.BygramPluginRollback)));
            items.add(UItem.asButton(14, getString(R.string.Delete)));
        }
        if (busy) items.add(UItem.asShadow(getString(R.string.Loading)));
        if (catalogRequest != null) items.add(0, UItem.asButton(8, getString(R.string.Cancel)));
        if (fileRequest != null) items.add(0, UItem.asButton(20, getString(R.string.Cancel)));
    }

    private static boolean isPinned(JsonObject record) {
        return record.has("pinned") && record.get("pinned").getAsBoolean();
    }

    private View engineCard(boolean enabled) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(4), dp(12), dp(4));
        row.setBackground(Theme.createRoundRectDrawable(dp(24), Theme.getColor(enabled
                ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhite)));
        Switch toggle = new Switch(getContext());
        toggle.setText(getString(R.string.BygramPluginEngine));
        toggle.setTextColor(Theme.getColor(enabled ? Theme.key_featuredStickers_buttonText : Theme.key_windowBackgroundWhiteBlackText));
        toggle.setChecked(enabled);
        toggle.setEnabled(!busy);
        toggle.setOnClickListener(view -> {
            toggle.setChecked(enabled);
            click(UItem.asCustom(1, row), view, 0, 0, 0);
        });
        row.addView(toggle, LayoutHelper.createLinear(-1, 48));
        return insetCard(row);
    }

    private View pluginCard(String id, JsonObject record) {
        JsonObject manifest = record.getAsJsonObject("manifest");
        String name = manifest.get("name").getAsString();
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(6));
        card.setBackground(Theme.createRoundRectDrawable(dp(24), Theme.getColor(Theme.key_windowBackgroundWhite)));

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = PluginPackageSheet.text(getContext(), name, 18, Theme.key_windowBackgroundWhiteBlackText);
        title.setTypeface(AndroidUtilities.bold());
        title.setOnClickListener(view -> openDetails(id));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(getContext());
        boolean desired = record.get("desired").getAsBoolean();
        toggle.setChecked(desired);
        toggle.setContentDescription(name + " · " + getString(R.string.Enable));
        toggle.setEnabled(!busy);
        toggle.setOnClickListener(view -> {
            toggle.setChecked(desired);
            togglePlugin(id, record);
        });
        header.addView(toggle, LayoutHelper.createLinear(-2, 48));
        card.addView(header, LayoutHelper.createLinear(-1, -2));
        card.addView(PluginPackageSheet.text(getContext(), PluginPackageSheet.byline(manifest, null), 14,
                Theme.key_windowBackgroundWhiteGrayText2), LayoutHelper.createLinear(-1, -2));
        TextView description = PluginPackageSheet.text(getContext(), manifest.get("description").getAsString(), 15,
                Theme.key_windowBackgroundWhiteBlackText);
        description.setMaxLines(4);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        description.setOnClickListener(view -> openDetails(id));
        card.addView(description, LayoutHelper.createLinear(-1, -2, 0, 10, 0, 6));
        card.addView(PluginPackageSheet.text(getContext(), status(record), 13, Theme.key_windowBackgroundWhiteGrayText2),
                LayoutHelper.createLinear(-1, -2, 0, 0, 0, 8));
        View divider = new View(getContext());
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        card.addView(divider, new LinearLayout.LayoutParams(-1, 1));
        LinearLayout actions = new LinearLayout(getContext());
        int normal = Theme.key_windowBackgroundWhiteGrayText2;
        cardAction(actions, R.drawable.msg_share, R.string.BygramPluginShareFile, normal, () -> sharePackage(id));
        cardAction(actions, R.drawable.msg_openin, R.string.BygramPluginViewSource, normal,
                () -> PluginPackageSheet.showSources(getContext(), id, null, () -> !destroyed));
        boolean pinned = isPinned(record);
        cardAction(actions, pinned ? R.drawable.msg_unpin : R.drawable.msg_pin, pinned ? R.string.DialogUnpin : R.string.DialogPin,
                pinned ? Theme.key_windowBackgroundWhiteBlueText : normal, () -> manager.pin(id, this::completed));
        if (record.get("active").getAsBoolean()) {
            cardAction(actions, R.drawable.msg_settings, R.string.Settings, normal, () -> manager.settings(id, this::completed));
        } else {
            cardAction(actions, R.drawable.msg_settings, R.string.Settings, normal, () -> openDetails(id));
        }
        cardAction(actions, R.drawable.msg_delete, R.string.Delete, Theme.key_text_RedRegular, () -> removePlugin(id));
        card.addView(actions, LayoutHelper.createLinear(-1, 48));
        return insetCard(card);
    }

    private View insetCard(View card) {
        FrameLayout inset = new FrameLayout(getContext());
        inset.addView(card, LayoutHelper.createFrame(-1, -2, Gravity.TOP, 12, 6, 12, 6));
        return inset;
    }

    private void cardAction(LinearLayout row, int icon, int label, int color, Runnable action) {
        ImageButton button = new ImageButton(getContext());
        button.setImageResource(icon);
        button.setColorFilter(Theme.getColor(color));
        button.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
        button.setContentDescription(getString(label));
        button.setEnabled(!busy);
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void openDetails(String id) {
        BygramPluginsActivity page = new BygramPluginsActivity(id);
        page.setCurrentAccount(currentAccount);
        presentFragment(page);
    }

    private void sharePackage(String id) {
        busy = true;
        changed.run();
        manager.exportPackage(id, (uri, error) -> {
            completed(null, error);
            if (error != null || destroyed || getParentActivity() == null) return;
            Intent send = new Intent(Intent.ACTION_SEND).setType(PluginFileActivity.MIME_TYPE)
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri(id + ".byplugin", uri));
            try { getContext().startActivity(Intent.createChooser(send, getString(R.string.BygramPluginShareFile))); }
            catch (ActivityNotFoundException failure) { message(getString(R.string.ErrorOccurred)); }
        });
    }

    private void togglePlugin(String id, JsonObject record) {
        boolean next = !record.get("desired").getAsBoolean();
        Runnable set = () -> {
            busy = true;
            manager.setEnabled(id, next, this::completed);
            changed.run();
        };
        if (next) confirm(getString(R.string.BygramPluginTrust) + "\nSHA-256: " + record.get("hash").getAsString(), set);
        else set.run();
    }

    private void removePlugin(String id) {
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.Delete))
                .setItems(new CharSequence[]{getString(R.string.BygramPluginKeepData), getString(R.string.BygramPluginDeleteData)},
                        (dialog, which) -> confirm(getString(R.string.Delete) + " " + id + "?", () -> {
                            busy = true;
                            changed.run();
                            manager.remove(id, which == 1, (value, error) -> {
                                completed(value, error);
                                if (error == null && id.equals(pluginId) && !destroyed) finishFragment();
                            });
                        })).create());
    }
    private String status(JsonObject record) {
        String status = activeStatus(record);
        return record.has("update_error") ? status + "\n" + getString(R.string.BygramPluginUpdateSkipped)
                + " " + errorText(record.get("update_error").getAsString()) : status;
    }

    private String activeStatus(JsonObject record) {
        if (record.has("error")) return errorText(record.get("error").getAsString());
        if (record.has("pending")) return getString(R.string.BygramPluginPending);
        if (record.get("desired").getAsBoolean() && !record.get("active").getAsBoolean()) return getString(R.string.BygramPluginWaitingEngine);
        return getString(record.get("active").getAsBoolean() ? R.string.BygramPluginActive : R.string.BygramPluginDisabled);
    }
    private static String scope(JsonObject manifest) {
        return getString(manifest.get("account_scope").getAsString().equals("global") ? R.string.BygramPluginScopeGlobal : R.string.BygramPluginScopeAccounts);
    }
    static String releaseInfo(JsonObject record) {
        if (!record.has("catalog_release")) return "";
        JsonObject release = record.getAsJsonObject("catalog_release");
        return getString(R.string.BygramPluginApproved) + "\n" + release.get("repository").getAsString() + "\nGitHub Release: " + release.get("tag").getAsString();
    }
    static String authorSignature(JsonObject record) {
        return record.has("author_key") ? getString(R.string.BygramPluginAuthorSignature) + "\n" + record.get("author_key").getAsString()
                : getString(R.string.BygramPluginUnsigned);
    }
    static String capabilities(JsonObject manifest) {
        ArrayList<String> titles = new ArrayList<>();
        for (JsonElement item : manifest.getAsJsonArray("capabilities")) {
            switch (item.getAsString()) {
                case "ui": titles.add(getString(R.string.BygramPluginCapUi)); break;
                case "theme": titles.add(getString(R.string.BygramPluginCapTheme)); break;
                case "messages.observe": titles.add(getString(R.string.BygramPluginCapObserve)); break;
                case "messages.send": titles.add(getString(R.string.BygramPluginCapSend)); break;
                case "network": titles.add(getString(R.string.BygramPluginCapNetwork)); break;
                case "hooks.raw": titles.add(getString(R.string.BygramPluginCapHooks)); break;
                case "hooks.telegram": titles.add(getString(R.string.BygramPluginCapTelegramHooks)); break;
                case "telegram.requests": titles.add(getString(R.string.BygramPluginCapTelegramRequests)); break;
                case "messages.media": titles.add(getString(R.string.BygramPluginCapMedia)); break;
            }
        }
        return android.text.TextUtils.join(", ", titles);
    }
    private void completed(Void value, String error) {
        busy = false; changed.run();
        if (destroyed || error == null) return;
        String[] dependency = error.split("\n", 2);
        if (error.startsWith("PluginDependency") && dependency.length == 2 && getParentActivity() != null) {
            showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPlugins))
                    .setMessage(errorText(error)).setNegativeButton(getString(R.string.Cancel), null)
                    .setPositiveButton(getString(R.string.Open), (dialog, which) ->
                            org.telegram.messenger.browser.Browser.openUrl(getContext(), dependency[1])).create());
        } else message(errorText(error));
    }
    private void finishStage(String token, boolean accept) {
        if (!token.equals(stagedToken)) return;
        stagedToken = null; busy = true; changed.run(); manager.commit(token, accept, this::completed);
    }
    static String errorText(String error) {
        String code = error.split("[: (]", 2)[0];
        int text;
        switch (code) {
            case "PluginVersionNotNewer": text = R.string.BygramPluginVersionNotNewer; break;
            case "PluginRevoked": text = R.string.BygramPluginRevoked; break;
            case "CatalogUnavailable": text = R.string.BygramPluginCatalogUnavailable; break;
            case "CatalogBusy": text = R.string.BygramPluginCatalogBusy; break;
            case "InvalidCatalog": case "CatalogPackageMismatch": case "CatalogLimitExceeded": text = R.string.BygramPluginCatalogInvalid; break;
            case "CatalogCancelled": text = R.string.BygramPluginCatalogCancelled; break;
            case "FileCancelled": text = R.string.BygramPluginFileCancelled; break;
            case "FileTimeout": text = R.string.BygramPluginFileTimeout; break;
            case "FileBusy": case "WorkQueueFull": text = R.string.BygramPluginWorkBusy; break;
            case "FileLimitExceeded": text = R.string.BygramPluginErrorStorage; break;
            case "InvalidAuthorSignature": case "AuthorKeyChanged": text = R.string.BygramPluginSignatureError; break;
            case "HookBackendUnavailable": case "HookProbeFailed": text = R.string.BygramPluginHooksUnavailable; break;
            case "HookTargetUnavailable": case "UnsupportedHookTarget": text = R.string.BygramPluginErrorCompatibility; break;
            case "HookLimitExceeded": text = R.string.BygramPluginErrorHookLimit; break;
            case "PluginRestartRequired": text = R.string.BygramPluginErrorRestart; break;
            case "EngineDisabled": text = R.string.BygramPluginErrorEngine; break;
            case "AccountRequired": case "AccountUnavailable": text = R.string.BygramPluginErrorAccount; break;
            case "InvalidSetting": case "InvalidPage": case "InvalidPluginSettings": case "SettingsLimitExceeded":
            case "SettingSchemaConflict": case "SettingsMigrationFailed": text = R.string.BygramPluginErrorSettings; break;
            case "IncompatibleSdk": case "IncompatibleBuild": case "IncompatiblePython": case "InvalidPythonBytecode":
                text = R.string.BygramPluginErrorCompatibility; break;
            case "StorageUnavailable": case "StorageLimitExceeded": case "InstallCommitFailed": case "FileUnavailable": text = R.string.BygramPluginErrorStorage; break;
            case "ContributionConflict": case "HookConflict": text = R.string.BygramPluginErrorConflict; break;
            case "AlreadyInstalled": text = R.string.BygramPluginErrorInstalled; break;
            case "InvalidManifest": case "InvalidPackagePath": case "CorruptPackage": case "InvalidPythonEncoding":
            case "PackageLimitExceeded": case "ManifestMissing": case "EntrypointMissing": case "InvalidIcon":
            case "UnsupportedZip": case "UnsupportedPackageFile": case "DuplicateEntry": case "CaseCollision": text = R.string.BygramPluginErrorPackage; break;
            default: text = R.string.ErrorOccurred;
        }
        return getString(text) + "\n" + error;
    }
    private void message(String text) {
        if (getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPlugins))
                .setMessage(text).setPositiveButton(getString(R.string.OK), null).create());
    }
    private void confirm(String text, Runnable action) {
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPlugins))
                .setMessage(text).setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> action.run()).create());
    }
    private void click(UItem item, View view, int position, float x, float y) {
        if (item.id == 20) { cancelFile(); return; }
        if (item.id == 8) { cancelCatalog(); return; }
        if (pluginId == null && item.id == 5) { getContext().startActivity(new Intent(getContext(), PluginRecovery.class)); return; }
        if (busy) return;
        if (pluginId == null) {
            if (item.id == 6) {
                confirm(getString(R.string.BygramPluginResetRegistryConfirm), () -> { busy = true; changed.run(); manager.resetRegistry(this::completed); });
                return;
            }
            if (item.id == 1) {
                boolean next = !state().get("engine").getAsBoolean();
                Runnable set = () -> { busy = true; manager.setEngine(next, this::completed); changed.run(); };
                if (next) confirm(getString(R.string.BygramPluginTrust), set); else set.run();
            } else if (item.id == 2) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
                try { startActivityForResult(intent, 5092); } catch (Exception e) { message(getString(R.string.ErrorOccurred)); }
            } else if (item.id == 9) { PluginSubmissionPage page = new PluginSubmissionPage(); page.setCurrentAccount(currentAccount); presentFragment(page); }
            else if (item.id == 4) manager.safeMode(!state().get("safe").getAsBoolean(), this::completed);
        } else {
            JsonObject record = record(); if (record == null) return;
            if (item.id == 10) togglePlugin(pluginId, record);
            else if (item.id == 11) manager.settings(pluginId, this::completed);
            else if (item.id == 12) manager.journal(pluginId, false, (text, error) -> {
                if (destroyed || getParentActivity() == null) return;
                if (error != null) { message(errorText(error)); return; }
                showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPluginJournal))
                        .setMessage(getString(R.string.BygramPluginLogWarning) + "\n\n" + text)
                        .setPositiveButton(getString(R.string.Copy), (dialog, which) -> AndroidUtilities.addToClipboard(text))
                        .setNeutralButton(getString(R.string.Clear), (dialog, which) -> manager.journal(pluginId, true, (v, e) -> { }))
                        .setNegativeButton(getString(R.string.Cancel), null).create());
            });
            else if (item.id == 13) confirm(getString(R.string.BygramPluginRollbackWarning), () -> manager.rollback(pluginId, this::completed));
            else if (item.id == 14) removePlugin(pluginId);
            else if (item.id == 15) chooseAccounts();
            else if (item.id == 16 || item.id == 17) confirm(getString(R.string.BygramPluginDataWarning), () -> {
                Intent intent = new Intent(item.id == 16 ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
                        .setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);
                if (item.id == 16) intent.putExtra(Intent.EXTRA_TITLE, pluginId + ".settings.json");
                try { startActivityForResult(intent, item.id == 16 ? 5093 : 5094); }
                catch (Exception e) { message(getString(R.string.ErrorOccurred)); }
            });
            else if (item.id == 18) confirm(getString(R.string.BygramPluginResetDataWarning), () -> {
                busy = true; changed.run(); manager.resetData(pluginId, this::completed);
            });
            else if (item.id == 19) { busy = true; changed.run(); manager.cancelUpdate(pluginId, this::completed); }
        }
    }
    private void chooseAccounts() {
        LinearLayout container = new LinearLayout(getContext()); container.setOrientation(LinearLayout.VERTICAL);
        ArrayList<CheckBox> boxes = new ArrayList<>(); ArrayList<String> identities = new ArrayList<>();
        JsonObject record = record();
        for (int slot = 0; slot < UserConfig.MAX_ACCOUNT_COUNT; slot++) {
            UserConfig config = UserConfig.getInstance(slot); if (!config.isClientActivated()) continue;
            String identity = PluginContext.currentIdentity(slot); identities.add(identity);
            CheckBox box = new CheckBox(getContext()); box.setText(UserObject.getUserName(config.getCurrentUser()));
            if (record.has("accounts")) for (JsonElement item : record.getAsJsonArray("accounts")) if (item.getAsString().equals(identity)) box.setChecked(true);
            boxes.add(box); container.addView(box);
        }
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPluginAccounts)).setView(container)
                .setNegativeButton(getString(R.string.Cancel), null).setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                    ArrayList<String> selected = new ArrayList<>();
                    for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isChecked()) selected.add(identities.get(i));
                    manager.accounts(pluginId, selected.toArray(new String[0]), this::completed);
                }).create());
    }
    @Override public void onActivityResultFragment(int request, int result, Intent intent) {
        super.onActivityResultFragment(request, result, intent);
        if ((request == 5093 || request == 5094) && result == Activity.RESULT_OK && intent != null && intent.getData() != null && pluginId != null) {
            busy = true; changed.run();
            long generation = ++fileGeneration;
            PluginManager.Result<Void> completion = (value, error) -> {
                if (generation != fileGeneration) return;
                fileRequest = null; completed(value, error);
            };
            fileRequest = request == 5093 ? manager.exportSettings(pluginId, intent.getData(), completion)
                    : manager.importSettings(pluginId, intent.getData(), completion);
            changed.run();
            return;
        }
        if (request != 5092 || result != Activity.RESULT_OK || intent == null || intent.getData() == null) return;
        busy = true; changed.run();
        long generation = ++fileGeneration;
        fileRequest = manager.stage(intent.getData(), (token, error) -> {
            if (generation != fileGeneration || destroyed) { if (token != null) manager.commit(token, false, null); return; }
            fileRequest = null; staged(token, error);
        });
        changed.run();
    }
    private void staged(String token, String error) {
            busy = false; changed.run();
            if (destroyed) { if (token != null) manager.commit(token, false, null); return; }
            if (error != null) { message(errorText(error)); return; }
            stagedToken = token;
            if (getParentActivity() == null) { finishStage(token, false); return; }
            manager.preview(token, (manifest, previewError) -> {
                if (destroyed || !token.equals(stagedToken)) return;
                if (getParentActivity() == null) { finishStage(token, false); return; }
                if (previewError != null) { finishStage(token, false); message(errorText(previewError)); return; }
                installSheet = PluginPackageSheet.show(getContext(), manifest, token, accept -> {
                    installSheet = null;
                    finishStage(token, accept);
                });
            });
    }
    private SharedPreferences repositoryPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bygram_plugin_repositories", Context.MODE_PRIVATE);
    }
    private void openRepositories() {
        Set<String> saved = repositoryPreferences().getStringSet("urls", Collections.emptySet());
        ArrayList<String> urls = new ArrayList<>(saved);
        Collections.sort(urls);
        CharSequence[] titles = new CharSequence[urls.size() + 1];
        titles[0] = getString(R.string.BygramPluginOfficialCatalog);
        for (int i = 0; i < urls.size(); i++) titles[i + 1] = urls.get(i);
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPluginCatalog))
                .setItems(titles, (dialog, index) -> {
                    if (index == 0) {
                        openCatalog(null);
                        return;
                    }
                    String repository = urls.get(index - 1);
                    showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPluginRepositoryIndex))
                            .setMessage(repository)
                            .setPositiveButton(getString(R.string.Open), (d, which) -> openCatalog(repository))
                            .setNeutralButton(getString(R.string.Delete), (d, which) -> saveRepository(repository, false))
                            .setNegativeButton(getString(R.string.Cancel), null).create());
                }).setPositiveButton(getString(R.string.BygramPluginAddRepository), (d, which) -> addRepository())
                .setNegativeButton(getString(R.string.Cancel), null).create());
    }
    private void addRepository() {
        EditTextBoldCursor address = new EditTextBoldCursor(getContext());
        address.setSingleLine(true);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setHint("https://example.org/plugins/index.json");
        address.setContentDescription(getString(R.string.BygramPluginRepositoryIndex));
        address.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        address.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        android.widget.FrameLayout field = new android.widget.FrameLayout(getContext());
        field.addView(address, LayoutHelper.createFrame(-1, -2, Gravity.TOP, 24, 8, 24, 8));
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPluginAddRepository))
                .setView(field).setPositiveButton(getString(R.string.OK), (d, which) -> {
                    try {
                        String repository = PluginCatalog.sourceAddress(address.getText().toString().trim());
                        saveRepository(repository, true);
                        openCatalog(repository);
                    } catch (java.io.IOException invalid) {
                        message(errorText("InvalidCatalog"));
                    }
                }).setNegativeButton(getString(R.string.Cancel), null).create());
    }
    private void saveRepository(String repository, boolean add) {
        SharedPreferences preferences = repositoryPreferences();
        Set<String> urls = new HashSet<>(preferences.getStringSet("urls", Collections.emptySet()));
        if (add) urls.add(repository);
        else urls.remove(repository);
        preferences.edit().putStringSet("urls", urls).apply();
    }
    private void openCatalog(String repository) {
        busy = true; changed.run();
        long generation = ++catalogGeneration;
        catalogRequest = manager.catalog(repository, currentAccount, (entries, error) -> {
            if (generation != catalogGeneration) return;
            catalogRequest = null;
            busy = false; changed.run();
            if (destroyed || getParentActivity() == null) return;
            if (error != null) { message(errorText(error)); return; }
            if (entries.size() == 0) { message(getString(R.string.BygramPluginEmpty)); return; }
            CharSequence[] titles = new CharSequence[entries.size()];
            for (int i = 0; i < titles.length; i++) {
                JsonObject manifest = entries.get(i).getAsJsonObject().getAsJsonObject("manifest");
                String version = manifest.get("version").getAsString();
                titles[i] = manifest.get("name").getAsString() + (version.isEmpty() ? "" : " · " + version);
            }
            showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPluginCatalog))
                    .setItems(titles, (d, index) -> {
                        busy = true; changed.run();
                        long download = ++catalogGeneration;
                        catalogRequest = manager.stageCatalog(repository, currentAccount, entries.get(index).getAsJsonObject(), (token, failure) -> {
                            if (download != catalogGeneration || destroyed) {
                                if (token != null) manager.commit(token, false, null);
                                return;
                            }
                            catalogRequest = null; staged(token, failure);
                        });
                        changed.run();
                    }).setNegativeButton(getString(R.string.Cancel), null).create());
        });
        changed.run();
    }
    private void cancelCatalog() {
        ++catalogGeneration;
        if (catalogRequest != null) { catalogRequest.close(); catalogRequest = null; busy = false; changed.run(); }
    }
    private void cancelFile() {
        ++fileGeneration;
        if (fileRequest != null) {
            try { fileRequest.close(); } catch (Exception ignored) { }
            fileRequest = null; busy = false; changed.run();
        }
    }

    public static final class PluginSubmissionPage extends BygramSettingsPage {
        private android.widget.EditText repository;
        private android.widget.TextView status;
        private android.widget.Button submit;
        private boolean closed;
        private android.widget.TextView help;
        @Override protected int settingsBackgroundKey() { return Theme.key_windowBackgroundWhite; }
        @Override public ArrayList<ThemeDescription> getThemeDescriptions() {
            ArrayList<ThemeDescription> descriptions = super.getThemeDescriptions();
            for (android.widget.TextView view : new android.widget.TextView[]{help, repository, status})
                descriptions.add(new ThemeDescription(view, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            return descriptions;
        }
        @Override public void onFragmentDestroy() { closed = true; super.onFragmentDestroy(); }
        @Override public View createView(Context context) {
            simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
            simpleActionBar.setTitle(getString(R.string.BygramPluginSubmit));
            simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
            });
            android.widget.FrameLayout content = createSettingsContent(context);
            android.widget.ScrollView scroll = new android.widget.ScrollView(context);
            android.widget.LinearLayout form = new android.widget.LinearLayout(context); form.setOrientation(1);
            int padding = AndroidUtilities.dp(20); form.setPadding(padding, padding, padding, padding);
            help = new android.widget.TextView(context);
            help.setText(getString(R.string.BygramPluginSubmissionHelp)); help.setTextSize(16);
            help.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)); form.addView(help);
            repository = new org.telegram.ui.Components.EditTextBoldCursor(context);
            repository.setSingleLine(true); repository.setHint("https://github.com/owner/plugin");
            repository.setContentDescription(getString(R.string.BygramPluginRepository));
            repository.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
            repository.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            repository.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            repository.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)}); form.addView(repository);
            submit = new android.widget.Button(context); submit.setText(getString(R.string.Send)); form.addView(submit);
            status = new android.widget.TextView(context); status.setTextSize(16);
            status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)); form.addView(status);
            submit.setOnClickListener(view -> {
                String url = repository.getText().toString().trim();
                if (!url.matches("https://github\\.com/[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}/?")) {
                    repository.setError(getString(R.string.BygramPluginRepository)); return;
                }
                submit.setEnabled(false); status.setText(getString(R.string.Loading));
                BygramActivityController.pluginCatalogRequest(currentAccount, url, response -> {
                    if (closed) return;
                    submit.setEnabled(true);
                    if (response == null || !response.optBoolean("ok")) status.setText(response == null ? getString(R.string.ErrorOccurred) : response.optString("error", getString(R.string.ErrorOccurred)));
                    else { repository.setText(""); loadStatus(); }
                });
            });
            scroll.addView(form); content.addView(scroll, LayoutHelper.createFrame(-1, -1));
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            loadStatus(); return fragmentView = content;
        }
        private void loadStatus() {
            BygramActivityController.pluginCatalogRequest(currentAccount, null, response -> {
                if (closed) return;
                if (response == null || !response.optBoolean("ok")) { status.setText(getString(R.string.ErrorOccurred)); return; }
                StringBuilder text = new StringBuilder();
                org.json.JSONArray entries = response.optJSONArray("submissions");
                if (entries != null) for (int i = 0; i < entries.length(); i++) {
                    org.json.JSONObject item = entries.optJSONObject(i); if (item == null) continue;
                    String state = item.optString("status");
                    int label;
                    switch (state) {
                        case "approved": label = R.string.BygramPluginApproved; break;
                        case "revoked": label = R.string.BygramPluginRevoked; break;
                        case "rejected": label = R.string.BygramPluginRejected; break;
                        default: label = R.string.BygramPluginReview;
                    }
                    text.append(item.optString("repository")).append("\n").append(getString(label));
                    if (!item.optString("release_tag").isEmpty()) text.append(" · ").append(item.optString("release_tag"));
                    if (!item.optString("review_note").isEmpty()) text.append("\n").append(item.optString("review_note"));
                    text.append("\n\n");
                }
                status.setText(text.length() == 0 ? getString(R.string.BygramPluginNoSubmissions) : text);
            });
        }
    }

    public static final class PluginPage extends BygramSettingsPage {
        private final PluginContext owner; private JsonObject specification;
        private final Map<Integer, UItem> customRows = new HashMap<>();
        private UniversalRecyclerView list;
        private String accountKey;
        private boolean focusedLink;
        AutoCloseable closeHandle;
        boolean closed;
        PluginPage(PluginContext owner, JsonObject specification) { this.owner = owner; this.specification = specification; }
        void refreshSettings() { if (!closed && list != null) list.adapter.update(true); }
        void reloadSettings(PluginContext requested) {
            if (owner != requested || closed) return;
            JsonElement path = specification.get("link_path");
            if (path != null && path.isJsonNull()) return;
            PluginManager.get().reloadSettings(owner, path == null ? "[]" : path.toString(), (page, error) -> {
                if (closed || !owner.active()) { owner.releasePage(page); return; }
                owner.releasePage(specification);
                specification = page;
                customRows.clear();
                refreshSettings();
            });
        }
        @Override public void onResume() {
            super.onResume();
            refreshSettings();
            if (focusedLink || list == null || !specification.has("focus_alias")) return;
            focusedLink = true;
            String alias = specification.get("focus_alias").getAsString();
            list.post(() -> list.highlightRow(() -> {
                JsonArray fields = specification.getAsJsonArray("items");
                for (int index = 0; index < fields.size(); index++) {
                    JsonObject field = fields.get(index).getAsJsonObject();
                    if (!field.has("link_alias") || !alias.equals(field.get("link_alias").getAsString())) continue;
                    for (int position = 0; position < list.adapter.getItemCount(); position++) {
                        UItem row = list.adapter.getItem(position);
                        if (row != null && row.id == index + 1) {
                            list.layoutManager.scrollToPositionWithOffset(position, AndroidUtilities.dp(60));
                            return position;
                        }
                    }
                }
                return -1;
            }, 1800));
        }
        @Override public void onFragmentDestroy() {
            if (closed) return;
            closed = true; owner.releasePage(specification);
            try { if (closeHandle != null) closeHandle.close(); } catch (Exception ignored) { }
            closeHandle = null; super.onFragmentDestroy();
        }
        @Override public View createView(Context context) {
            try {
                String identity = PluginContext.currentIdentity(currentAccount);
                for (String allowed : allowed()) if (identity.equals(allowed)) accountKey = identity;
            } catch (IllegalStateException | IllegalArgumentException unavailable) { }
            simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
            simpleActionBar.setTitle(specification.get("title").getAsString());
            simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); } });
            FrameLayout content = createSettingsContent(context); content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            list = new UniversalRecyclerView(this, (items, adapter) -> {
                JsonArray fields = specification.getAsJsonArray("items");
                if (fields.size() > 100) throw new IllegalArgumentException("PageLimitExceeded");
                boolean accountFields = false;
                for (JsonElement element : fields) if (isAccount(element.getAsJsonObject())) accountFields = true;
                List<String> availableAccounts = accountFields ? Arrays.asList(allowed()) : Collections.emptyList();
                if (accountKey != null && !availableAccounts.contains(accountKey)) accountKey = null;
                if (accountFields) items.add(UItem.asButton(1000, getString(R.string.BygramPluginAccounts), accountKey == null ? "—"
                        : UserObject.getUserName(UserConfig.getInstance(Integer.parseInt(accountKey.split(":")[0])).getCurrentUser())));
                for (int i = 0; i < fields.size(); i++) {
                    JsonObject field = fields.get(i).getAsJsonObject(); String type = field.get("type").getAsString(), title = field.get("title").getAsString();
                    if (type.equals("Header")) items.add(UItem.asHeader(title));
                    else if (type.equals("Text") || type.equals("Divider")) items.add(UItem.asShadow(i + 1, title));
                    else if (type.equals("Custom")) {
                        int id = i + 1;
                        if (!owner.active()) continue;
                        UItem custom = customRows.get(id);
                        if (custom == null && !customRows.containsKey(id)) {
                            Object row = owner.createSettingsRow(field.get("row_factory").getAsString(), context);
                            if (row instanceof UItem) custom = ((UItem) row).setId(id);
                            if (row instanceof View) custom = UItem.asCustom(id, (View) row);
                            customRows.put(id, custom);
                        }
                        if (custom != null) items.add(custom);
                    }
                    else {
                        UItem row = type.equals("Switch") ? UItem.asCheck(i + 1, title).setChecked(value(field).getAsBoolean())
                                : UItem.asButton(i + 1, title, field.has("key") ? valueText(field) : "");
                        row.enabled = owner.active() && (!isAccount(field) || accountKey != null && availableAccounts.contains(accountKey));
                        if (field.has("subtext")) row.subtext = field.get("subtext").getAsString();
                        if (field.has("icon")) row.iconResId = context.getResources().getIdentifier(field.get("icon").getAsString(), "drawable", context.getPackageName());
                        row.accent = field.has("accent") && field.get("accent").getAsBoolean();
                        row.red = field.has("red") && field.get("red").getAsBoolean();
                        items.add(row);
                    }
                }
            }, (item, view, position, x, y) -> { if (item.id == 1000) chooseAccount(); else edit(item.id - 1, view); },
                    (item, view, position, x, y) -> longPress(item.id - 1, view));
            content.addView(list, LayoutHelper.createFrame(-1, -1)); return fragmentView = content;
        }
        private static boolean isAccount(JsonObject field) { return field.has("scope") && field.get("scope").getAsString().equals("account"); }
        private boolean longPress(int index, View view) {
            JsonArray rows = specification.getAsJsonArray("items");
            if (closed || !owner.active() || index < 0 || index >= rows.size()) return false;
            JsonObject row = rows.get(index).getAsJsonObject();
            JsonElement path = specification.get("link_path");
            if (!row.has("link_alias") || path != null && path.isJsonNull())
                return invokeRow(index, "long_callback", view);
            ArrayList<String> aliases = new ArrayList<>();
            if (path != null) for (JsonElement alias : path.getAsJsonArray()) aliases.add(alias.getAsString());
            aliases.add(row.get("link_alias").getAsString());
            String url = android.net.Uri.parse(org.telegram.messenger.BygramSettingsLink.PREFIX + "plugins").buildUpon()
                    .appendQueryParameter("plugin", owner.id())
                    .appendQueryParameter("row", String.join("/", aliases)).build().toString();
            invokeRow(index, "long_callback", view);
            return BygramSettingsActivity.showUrlMenu(this, view, list, url);
        }
        private String[] allowed() { try { return owner.allowedAccounts(); } catch (IllegalStateException unavailable) { return new String[0]; } }
        private void chooseAccount() {
            String[] accounts = allowed();
            if (accounts.length == 0 || getParentActivity() == null) return;
            CharSequence[] names = new CharSequence[accounts.length];
            for (int i = 0; i < names.length; i++) names[i] = UserObject.getUserName(UserConfig.getInstance(Integer.parseInt(accounts[i].split(":")[0])).getCurrentUser());
            showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPluginAccounts)).setItems(names, (d, which) -> {
                accountKey = accounts[which]; list.adapter.update(true);
            }).create());
        }
        private JsonElement value(JsonObject field) {
            try {
                if (isAccount(field) && accountKey == null) return field.get("default");
                String json = owner.validSetting(field.get("key").getAsString(), field.get("default").toString(), isAccount(field) ? accountKey : "");
                return JsonParser.parseString(json);
            } catch (Exception e) { return field.get("default"); }
        }
        private String valueText(JsonObject field) {
            JsonElement selected = value(field);
            if (field.get("type").getAsString().equals("Select"))
                for (JsonElement option : field.getAsJsonArray("options"))
                    if (option.getAsJsonObject().get("value").equals(selected)) return option.getAsJsonObject().get("title").getAsString();
            return selected.getAsString();
        }
        private boolean set(JsonObject field, JsonElement value) {
            if (closed || isAccount(field) && accountKey == null) return false;
            try {
                owner.settingsSet(field.get("key").getAsString(), value.toString(), isAccount(field) ? accountKey : "");
                if (field.has("change_callback")) owner.callNamed(field.get("change_callback").getAsString(), value.toString());
                return true;
            }
            catch (Exception e) { return false; }
        }
        private boolean invokeRow(int index, String property, Object... args) {
            if (closed || !owner.active() || index < 0 || index >= specification.getAsJsonArray("items").size()) return false;
            JsonObject field = specification.getAsJsonArray("items").get(index).getAsJsonObject();
            if (!field.has(property) || isAccount(field) && (accountKey == null || !Arrays.asList(allowed()).contains(accountKey))) return false;
            owner.callNamed(field.get(property).getAsString(), args);
            return true;
        }
        private void edit(int index, View view) {
            if (closed || !owner.active() || index < 0 || index >= specification.getAsJsonArray("items").size()) return;
            JsonObject field = specification.getAsJsonArray("items").get(index).getAsJsonObject();
            if (isAccount(field) && (accountKey == null || !Arrays.asList(allowed()).contains(accountKey))) return;
            String type = field.get("type").getAsString();
            if (type.equals("Action") || type.equals("Custom")) {
                if (field.has("pass_view") && field.get("pass_view").getAsBoolean()) invokeRow(index, "callback", view);
                else invokeRow(index, "callback");
                return;
            }
            if (type.equals("Switch")) { set(field, new JsonPrimitive(!value(field).getAsBoolean())); return; }
            if (type.equals("Select")) {
                JsonArray options = field.getAsJsonArray("options"); CharSequence[] titles = new CharSequence[options.size()];
                for (int i = 0; i < titles.length; i++) titles[i] = options.get(i).getAsJsonObject().get("title").getAsString();
                showDialog(new AlertDialog.Builder(getContext()).setTitle(field.get("title").getAsString()).setItems(titles, (d, which) -> set(field, options.get(which).getAsJsonObject().get("value"))).create()); return;
            }
            if (!type.equals("TextInput") && !type.equals("Slider")) return;
            EditTextBoldCursor input = BygramSettingsActivity.createDialogInput(getContext(), field.get("title").getAsString());
            input.setText(value(field).getAsString());
            LinearLayout controls = new LinearLayout(getContext()); controls.setOrientation(LinearLayout.VERTICAL);
            if (type.equals("Slider")) {
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
                double min = field.get("min").getAsDouble(), max = field.get("max").getAsDouble(), step = field.get("step").getAsDouble();
                double steps = Math.floor((max - min) / step + 0.000001);
                SeekBarView slider = new SeekBarView(getContext(), getResourceProvider());
                slider.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                    @Override public void onSeekBarDrag(boolean stop, float progress) {
                        double index = Math.max(0, Math.min(steps, Math.rint(progress * steps)));
                        input.setText(java.math.BigDecimal.valueOf(min).add(java.math.BigDecimal.valueOf(step).multiply(java.math.BigDecimal.valueOf(index))).stripTrailingZeros().toPlainString());
                    }
                    @Override public int getStepsCount() { return steps > 0 && steps <= 10000 ? (int) steps : 0; }
                    @Override public CharSequence getContentDescription() { return field.get("title").getAsString() + ", " + input.getText(); }
                });
                slider.setProgress(max > min ? (float) ((value(field).getAsDouble() - min) / (max - min)) : 0);
                slider.setEnabled(steps > 0);
                controls.addView(slider, LayoutHelper.createLinear(-1, 48, 16, 0, 16, 0));
            } else {
                android.text.InputFilter length = new android.text.InputFilter.LengthFilter(field.get("max_length").getAsInt());
                if (field.has("mask")) {
                    java.util.regex.Pattern mask = java.util.regex.Pattern.compile(field.get("mask").getAsString());
                    android.text.InputFilter filter = (source, start, end, dest, from, to) -> {
                        if (start == end) return null;
                        String next = dest.subSequence(0, from).toString() + source.subSequence(start, end) + dest.subSequence(to, dest.length());
                        java.util.regex.Matcher matcher = mask.matcher(next);
                        return matcher.matches() || matcher.hitEnd() ? null : dest.subSequence(from, to);
                    };
                    input.setFilters(new android.text.InputFilter[]{length, filter});
                } else {
                    input.setFilters(new android.text.InputFilter[]{length});
                }
                if (field.has("multiline") && field.get("multiline").getAsBoolean()) {
                    input.setSingleLine(false);
                    input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                }
            }
            controls.addView(BygramSettingsActivity.dialogInputContainer(input));
            AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle(field.get("title").getAsString())
                    .setView(controls).setNegativeButton(getString(R.string.Cancel), null)
                    .setPositiveButton(getString(R.string.OK), null).create();
            showDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                JsonElement next;
                try { next = type.equals("TextInput") ? new JsonPrimitive(input.getText().toString()) : new JsonPrimitive(Double.parseDouble(input.getText().toString())); }
                catch (NumberFormatException invalid) { input.setError(getString(R.string.ErrorOccurred)); return; }
                if (set(field, next)) dialog.dismiss(); else input.setError(getString(R.string.ErrorOccurred));
            });
        }
    }
}
