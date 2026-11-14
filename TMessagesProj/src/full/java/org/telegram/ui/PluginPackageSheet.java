package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.*;
import com.google.gson.JsonObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.PluginManager;
import org.telegram.messenger.plugins.PluginManifest;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.BackupImageView;
import java.util.function.BooleanSupplier;

final class PluginPackageSheet {
    private static final int TEXT = Theme.key_windowBackgroundWhiteBlackText;
    private static final int SECONDARY = Theme.key_windowBackgroundWhiteGrayText2;

    static String byline(JsonObject manifest, String previousVersion) {
        String version = manifest.get("version").getAsString();
        if (previousVersion != null && !version.equals(previousVersion)) version = previousVersion + " → " + version;
        return LocaleController.formatString(R.string.BygramPluginByline, version, manifest.get("author").getAsString());
    }

    static BackupImageView pluginIcon(Context context, JsonObject manifest, String token, int account, int size) {
        BackupImageView view = new BackupImageView(context);
        view.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Drawable fallback = context.getDrawable(R.drawable.settings_account).mutate();
        fallback.setTint(Theme.getColor(Theme.key_featuredStickers_buttonText));
        view.setImageDrawable(fallback);
        view.setSize(dp(size - 16), dp(size - 16));
        view.setBackground(Theme.createRoundRectDrawable(dp(24), Theme.getColor(Theme.key_featuredStickers_addButton)));
        view.getImageReceiver().setAspectFit(true);
        view.getImageReceiver().setCurrentAccount(account);
        java.util.function.Consumer<String> load = reference -> {
            if (!PluginManifest.isStickerIcon(reference) || !UserConfig.getInstance(account).isClientActivated()) return;
            int slash = reference.indexOf('/');
            int index = Integer.parseInt(reference.substring(slash + 1));
            TLRPC.TL_inputStickerSetShortName setName = new TLRPC.TL_inputStickerSetShortName();
            setName.short_name = reference.substring(0, slash);
            long userId = UserConfig.getInstance(account).getClientUserId();
            MediaDataController.getInstance(account).getStickerSet(setName, 0, false, set -> {
                if (set == null || index >= set.documents.size()
                        || userId != UserConfig.getInstance(account).getClientUserId()) return;
                TLRPC.Document sticker = set.documents.get(index);
                if (sticker == null) return;
                view.setSize(dp(size), dp(size));
                view.setImage(ImageLocation.getForDocument(sticker), size + "_" + size + "_firstframe", fallback, 0, set);
            });
        };
        if (manifest.has("sticker_icon")) {
            load.accept(manifest.get("sticker_icon").getAsString());
        } else if (manifest.get("id").getAsString().startsWith("extera.")) {
            PluginManager.get().importedStickerIcon(manifest.get("id").getAsString(), token, (reference, error) -> {
                if (error == null) load.accept(reference);
            });
        }
        return view;
    }

    static BottomSheet show(Context context, JsonObject manifest, String token, java.util.function.Consumer<Boolean> result) {
        PluginManager manager = PluginManager.get();
        String id = manifest.get("id").getAsString();
        JsonObject plugins = manager.snapshot().getAsJsonObject("plugins");
        JsonObject installed = plugins == null ? null : plugins.getAsJsonObject(id);
        String hash = manifest.get("sha256").getAsString();
        boolean duplicate = installed != null && (hash.equals(installed.get("hash").getAsString())
                || installed.has("pending") && hash.equals(installed.getAsJsonObject("pending").get("hash").getAsString()));
        boolean catalog = "catalog".equals(manifest.get("source").getAsString());

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(8));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(0, 0, 0, dp(8));
        scroll.addView(details, LayoutHelper.createScroll(-1, -2, Gravity.TOP));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -2, 1));

        BackupImageView icon = pluginIcon(context, manifest, token, UserConfig.selectedAccount, 44);
        details.addView(icon, LayoutHelper.createLinear(44, 44, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 10));
        TextView title = text(context, manifest.get("name").getAsString(), 22, TEXT);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        details.addView(title, LayoutHelper.createLinear(-1, -2));
        TextView version = text(context, byline(manifest, installed == null ? null
                : installed.getAsJsonObject("manifest").get("version").getAsString()), 14, SECONDARY);
        version.setGravity(Gravity.CENTER);
        details.addView(version, LayoutHelper.createLinear(-1, -2, 0, 4, 0, 6));
        TextView source = text(context, getString(catalog ? R.string.BygramPluginApproved : R.string.BygramPluginUnknownSource),
                14, catalog ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_text_RedRegular);
        source.setGravity(Gravity.CENTER);
        details.addView(source, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 10));

        addPanel(details, manifest.get("description").getAsString());
        String capabilities = BygramPluginsActivity.capabilities(manifest);
        addPanel(details, getString(R.string.BygramPluginDeclaredCapabilities) + "\n"
                + (capabilities.isEmpty() ? "—" : capabilities) + "\n\n"
                + BygramPluginsActivity.authorSignature(manifest) + "\nSHA-256: " + hash
                + (catalog ? "\n" + BygramPluginsActivity.releaseInfo(manifest) : ""));
        if (duplicate) addPanel(details, getString(R.string.BygramPluginErrorInstalled));

        BottomSheet sheet = new BottomSheet.Builder(context).setCustomView(content).create();
        TextView code = text(context, getString(R.string.BygramPluginViewSource), 15, Theme.key_windowBackgroundWhiteBlueText);
        code.setGravity(Gravity.CENTER);
        code.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
        code.setOnClickListener(view -> showSources(context, id, token, sheet::isShowing));
        details.addView(code, LayoutHelper.createLinear(-1, 48));

        boolean[] accepted = {false};
        TextView install = primaryButton(context, getString(duplicate ? R.string.Close : R.string.BygramPluginInstallDisabled));
        install.setOnClickListener(view -> {
            if (duplicate || catalog) {
                accepted[0] = !duplicate;
                sheet.dismiss();
            } else {
                new AlertDialog.Builder(context).setTitle(getString(R.string.BygramPluginUnknownSource))
                        .setMessage(getString(R.string.BygramPluginLocalWarning))
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .setPositiveButton(getString(R.string.BygramPluginInstallDisabled), (dialog, which) -> {
                            if (!sheet.isShowing()) return;
                            accepted[0] = true;
                            sheet.dismiss();
                        }).show();
            }
        });
        content.addView(install, LayoutHelper.createLinear(-1, 48));
        sheet.setOnDismissListener(() -> result.accept(accepted[0]));
        sheet.show();
        return sheet;
    }

    private static void addPanel(LinearLayout layout, String value) {
        TextView panel = text(layout.getContext(), value, 15, TEXT);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(Theme.createRoundRectDrawable(dp(18), Theme.getColor(Theme.key_windowBackgroundGray)));
        layout.addView(panel, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 8));
    }

    static TextView text(Context context, String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Theme.getColor(color));
        return view;
    }

    static TextView primaryButton(Context context, String label) {
        TextView button = text(context, label, 16, Theme.key_featuredStickers_buttonText);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24),
                Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        return button;
    }

    static void showSources(Context context, String id, String token, BooleanSupplier open) {
        PluginManager manager = PluginManager.get();
        manager.sourceFiles(id, token, (files, error) -> {
            if (!open.getAsBoolean()) return;
            if (error != null) { showError(context, error); return; }
            new AlertDialog.Builder(context).setTitle(id + ".byplugin")
                    .setItems(files.toArray(new String[0]), (dialog, which) -> {
                        String path = files.get(which);
                        manager.sourceFile(id, token, path, getString(R.string.BygramPluginSourceTruncated), (source, failure) -> {
                            if (!open.getAsBoolean()) return;
                            if (failure != null) { showError(context, failure); return; }
                            TextView code = text(context, source, 13, TEXT);
                            code.setTypeface(Typeface.MONOSPACE);
                            code.setPadding(dp(16), dp(8), dp(16), dp(8));
                            code.setTextIsSelectable(true);
                            ScrollView scroll = new ScrollView(context);
                            scroll.addView(code, LayoutHelper.createScroll(-1, -2, Gravity.TOP));
                            new AlertDialog.Builder(context).setTitle(path).setView(scroll)
                                    .setPositiveButton(getString(R.string.Close), null).show();
                        });
                    }).setNegativeButton(getString(R.string.Cancel), null).show();
        });
    }

    static void showError(Context context, String error) {
        new AlertDialog.Builder(context).setTitle(getString(R.string.BygramPlugins))
                .setMessage(BygramPluginsActivity.errorText(error))
                .setPositiveButton(getString(R.string.OK), null).show();
    }
}
