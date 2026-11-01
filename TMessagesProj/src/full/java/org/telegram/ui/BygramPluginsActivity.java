package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;
import android.app.Activity;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.widget.CheckBox;
import com.google.gson.*;
import org.telegram.messenger.*;
import org.telegram.messenger.plugins.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.*;

public final class BygramPluginsActivity extends BygramSettingsPage {
    private final PluginManager manager = PluginManager.get();
    private final String pluginId;
    private UniversalRecyclerView list;
    private final ArrayList<String> rows = new ArrayList<>();
    private boolean busy;
    private boolean destroyed;
    private String stagedToken;
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
        list = null; super.onFragmentDestroy();
    }
    @Override public void onResume() { super.onResume(); manager.refresh(); }
    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.BygramPlugins));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout content = createSettingsContent(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        list = new UniversalRecyclerView(this, this::fill, this::click, (item, view, position, x, y) ->
                BygramSettingsActivity.showLinkMenu(this, view, list, "plugins"));
        list.setSections(); list.adapter.setApplyBackground(false);
        content.addView(list, LayoutHelper.createFrame(-1, -1)); return fragmentView = content;
    }
    private JsonObject state() { return manager.snapshot(); }
    private JsonObject record() {
        JsonObject state = state();
        return state.has("plugins") && pluginId != null ? state.getAsJsonObject("plugins").getAsJsonObject(pluginId) : null;
    }
    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        rows.clear();
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
            items.add(UItem.asCheck(1, getString(R.string.BygramPluginEngine)).setChecked(state.get("engine").getAsBoolean()));
            items.add(UItem.asShadow(getString(R.string.BygramPluginTrust)));
            if (state.has("recovery_error")) items.add(UItem.asShadow(getString(R.string.BygramPluginErrorRecovery) + "\n" + state.get("recovery_error").getAsString()));
            if (state.has("recovered") && state.get("recovered").getAsBoolean()) items.add(UItem.asShadow(getString(R.string.BygramPluginRegistryRecovered)));
            items.add(UItem.asButton(2, getString(R.string.BygramPluginInstall)));
            items.add(UItem.asButton(7, getString(R.string.BygramPluginCatalog)));
            items.add(UItem.asButton(9, getString(R.string.BygramPluginSubmit)));
            items.add(UItem.asButton(3, getString(R.string.BygramPluginHelp)));
            if (!HookBridge.available()) items.add(UItem.asShadow(getString(R.string.BygramPluginHooksUnavailable)));
            items.add(UItem.asCheck(4, getString(R.string.BygramPluginSafeMode)).setChecked(state.get("safe").getAsBoolean()));
            items.add(UItem.asButton(5, getString(R.string.BygramPluginRecoveryTitle)));
            if (state.getAsJsonObject("plugins").size() == 0) items.add(UItem.asShadow(getString(R.string.BygramPluginEmpty)));
            for (String id : state.getAsJsonObject("plugins").keySet()) {
                JsonObject record = state.getAsJsonObject("plugins").getAsJsonObject(id);
                JsonObject manifest = record.getAsJsonObject("manifest");
                rows.add(id);
                items.add(UItem.asButton(100 + rows.size() - 1, R.drawable.settings_account, manifest.get("name").getAsString(), status(record)));
            }
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
    private String status(JsonObject record) {
        if (record.has("error")) return errorText(record.get("error").getAsString());
        if (record.has("pending")) return getString(R.string.BygramPluginPending);
        if (record.get("desired").getAsBoolean() && !record.get("active").getAsBoolean()) return getString(R.string.BygramPluginWaitingEngine);
        return getString(record.get("active").getAsBoolean() ? R.string.BygramPluginActive : R.string.BygramPluginDisabled);
    }
    private static String scope(JsonObject manifest) {
        return getString(manifest.get("account_scope").getAsString().equals("global") ? R.string.BygramPluginScopeGlobal : R.string.BygramPluginScopeAccounts);
    }
    private static String releaseInfo(JsonObject record) {
        if (!record.has("catalog_release")) return "";
        JsonObject release = record.getAsJsonObject("catalog_release");
        return getString(R.string.BygramPluginApproved) + "\n" + release.get("repository").getAsString() + "\nGitHub Release: " + release.get("tag").getAsString();
    }
    private static String authorSignature(JsonObject record) {
        return record.has("author_key") ? getString(R.string.BygramPluginAuthorSignature) + "\n" + record.get("author_key").getAsString()
                : getString(R.string.BygramPluginUnsigned);
    }
    private static String capabilities(JsonObject manifest) {
        ArrayList<String> titles = new ArrayList<>();
        for (JsonElement item : manifest.getAsJsonArray("capabilities")) {
            switch (item.getAsString()) {
                case "ui": titles.add(getString(R.string.BygramPluginCapUi)); break;
                case "theme": titles.add(getString(R.string.BygramPluginCapTheme)); break;
                case "messages.observe": titles.add(getString(R.string.BygramPluginCapObserve)); break;
                case "messages.send": titles.add(getString(R.string.BygramPluginCapSend)); break;
                case "network": titles.add(getString(R.string.BygramPluginCapNetwork)); break;
                case "hooks.raw": titles.add(getString(R.string.BygramPluginCapHooks)); break;
            }
        }
        return android.text.TextUtils.join(", ", titles);
    }
    private void completed(Void value, String error) {
        busy = false; changed.run();
        if (!destroyed && error != null) message(errorText(error));
    }
    private void finishStage(String token, boolean accept) {
        if (!token.equals(stagedToken)) return;
        stagedToken = null; busy = true; changed.run(); manager.commit(token, accept, this::completed);
    }
    private static String errorText(String error) {
        String code = error.split("[: (]", 2)[0];
        int text;
        switch (code) {
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
            case "HookLimitExceeded": text = R.string.BygramPluginErrorHookLimit; break;
            case "PluginRestartRequired": text = R.string.BygramPluginErrorRestart; break;
            case "EngineDisabled": text = R.string.BygramPluginErrorEngine; break;
            case "AccountRequired": case "AccountUnavailable": text = R.string.BygramPluginErrorAccount; break;
            case "InvalidSetting": case "InvalidPage": case "InvalidPluginSettings": case "SettingsLimitExceeded":
            case "SettingSchemaConflict": case "SettingsMigrationFailed": text = R.string.BygramPluginErrorSettings; break;
            case "IncompatibleSdk": case "IncompatibleBuild": case "IncompatiblePython": text = R.string.BygramPluginErrorCompatibility; break;
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
            } else if (item.id == 7) openCatalog();
            else if (item.id == 9) { PluginSubmissionPage page = new PluginSubmissionPage(); page.setCurrentAccount(currentAccount); presentFragment(page); }
            else if (item.id == 3) message(getString(R.string.BygramPluginHelpText));
            else if (item.id == 4) manager.safeMode(!state().get("safe").getAsBoolean(), this::completed);
            else if (item.id >= 100 && item.id - 100 < rows.size()) {
                BygramPluginsActivity page = new BygramPluginsActivity(rows.get(item.id - 100));
                page.setCurrentAccount(currentAccount); presentFragment(page);
            }
        } else {
            JsonObject record = record(); if (record == null) return;
            if (item.id == 10) {
                boolean next = !record.get("desired").getAsBoolean();
                Runnable set = () -> { busy = true; manager.setEnabled(pluginId, next, this::completed); changed.run(); };
                if (next) confirm(getString(R.string.BygramPluginTrust) + "\nSHA-256: " + record.get("hash").getAsString(), set); else set.run();
            } else if (item.id == 11) manager.settings(pluginId, this::completed);
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
            else if (item.id == 14) showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.Delete))
                    .setItems(new CharSequence[]{getString(R.string.BygramPluginKeepData), getString(R.string.BygramPluginDeleteData)},
                            (dialog, which) -> confirm(getString(R.string.Delete) + " " + pluginId + "?", () ->
                                    manager.remove(pluginId, which == 1, (v, error) -> { completed(v, error); if (error == null) finishFragment(); }))).create());
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
            if (request == 5093) manager.exportSettings(pluginId, intent.getData(), this::completed);
            else {
                long generation = ++fileGeneration;
                fileRequest = manager.importSettings(pluginId, intent.getData(), (value, error) -> {
                    if (generation != fileGeneration) return;
                    fileRequest = null; completed(value, error);
                });
                changed.run();
            }
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
                AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle(manifest.get("name").getAsString())
                        .setMessage(manifest.get("id").getAsString() + " · " + manifest.get("version").getAsString()
                                + "\n" + manifest.get("author").getAsString() + "\n" + manifest.get("description").getAsString()
                                + "\n" + capabilities(manifest) + "\n" + scope(manifest) + "\nSHA-256: " + manifest.get("sha256").getAsString()
                                + "\n" + releaseInfo(manifest)
                                + "\n\n" + authorSignature(manifest))
                        .setNegativeButton(getString(R.string.Cancel), (d, which) -> finishStage(token, false))
                        .setPositiveButton(getString(R.string.BygramPluginInstallDisabled), (d, which) -> {
                            if (manifest.get("source").getAsString().equals("catalog")) finishStage(token, true);
                            else {
                                AlertDialog warning = new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPlugins))
                                        .setMessage(getString(R.string.BygramPluginLocalWarning))
                                        .setNegativeButton(getString(R.string.Cancel), (cancelled, button) -> finishStage(token, false))
                                        .setPositiveButton(getString(R.string.BygramPluginInstallDisabled), (accepted, button) -> finishStage(token, true)).create();
                                warning.setOnCancelListener(cancelled -> finishStage(token, false)); showDialog(warning);
                            }
                        }).create();
                dialog.setOnCancelListener(d -> finishStage(token, false)); showDialog(dialog);
            });
    }
    private void openCatalog() {
        busy = true; changed.run();
        long generation = ++catalogGeneration;
        catalogRequest = manager.catalog((entries, error) -> {
            if (generation != catalogGeneration) return;
            catalogRequest = null;
            busy = false; changed.run();
            if (destroyed || getParentActivity() == null) return;
            if (error != null) { message(errorText(error)); return; }
            if (entries.size() == 0) { message(getString(R.string.BygramPluginEmpty)); return; }
            CharSequence[] titles = new CharSequence[entries.size()];
            for (int i = 0; i < titles.length; i++) {
                JsonObject manifest = entries.get(i).getAsJsonObject().getAsJsonObject("manifest");
                titles[i] = manifest.get("name").getAsString() + " · " + manifest.get("version").getAsString();
            }
            showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.BygramPluginCatalog))
                    .setItems(titles, (d, index) -> {
                        busy = true; changed.run();
                        long download = ++catalogGeneration;
                        catalogRequest = manager.stageCatalog(entries.get(index).getAsJsonObject(), (token, failure) -> {
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
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(getString(R.string.BygramPluginSubmit));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
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
                    int label = state.equals("approved") ? R.string.BygramPluginApproved : state.equals("rejected") ? R.string.BygramPluginRejected : R.string.BygramPluginReview;
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
        private final PluginContext owner; private final JsonObject specification;
        private UniversalRecyclerView list;
        private String accountKey;
        AutoCloseable closeHandle;
        boolean closed;
        PluginPage(PluginContext owner, JsonObject specification) { this.owner = owner; this.specification = specification; }
        void refreshSettings() { if (!closed && list != null) list.adapter.update(true); }
        @Override public void onResume() { super.onResume(); refreshSettings(); }
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
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(specification.get("title").getAsString());
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); } });
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
                    else if (type.equals("Text")) items.add(UItem.asShadow(title));
                    else {
                        UItem row = type.equals("Switch") ? UItem.asCheck(i + 1, title).setChecked(value(field).getAsBoolean())
                                : UItem.asButton(i + 1, title, field.has("key") ? valueText(field) : "");
                        row.enabled = owner.active() && (!isAccount(field) || accountKey != null && availableAccounts.contains(accountKey));
                        items.add(row);
                    }
                }
            }, (item, view, position, x, y) -> { if (item.id == 1000) chooseAccount(); else edit(item.id - 1); }, null);
            content.addView(list, LayoutHelper.createFrame(-1, -1)); return fragmentView = content;
        }
        private static boolean isAccount(JsonObject field) { return field.has("scope") && field.get("scope").getAsString().equals("account"); }
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
            try { owner.settingsSet(field.get("key").getAsString(), value.toString(), isAccount(field) ? accountKey : ""); return true; }
            catch (Exception e) { return false; }
        }
        private void edit(int index) {
            if (closed || !owner.active() || index < 0 || index >= specification.getAsJsonArray("items").size()) return;
            JsonObject field = specification.getAsJsonArray("items").get(index).getAsJsonObject();
            if (isAccount(field) && (accountKey == null || !Arrays.asList(allowed()).contains(accountKey))) return;
            String type = field.get("type").getAsString();
            if (type.equals("Action")) { owner.callNamed(field.get("callback").getAsString()); return; }
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
            } else input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(field.get("max_length").getAsInt())});
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
