package org.telegram.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BygramIconDrawable;
import org.telegram.messenger.BygramIconPacks;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.AndroidUtilities.dp;

public final class BygramIconPacksActivity extends IconPacksPage {
    private static final int IMPORT = 9210;
    static {
        UItem.UItemFactory.setup(new PackRow.Factory());
        UItem.UItemFactory.setup(new IconRowFactory());
    }
    private ArrayList<BygramIconPacks.Summary> packs = new ArrayList<>();
    private File incomingPack;

    public static boolean openFile(BaseFragment parent, File file, String name) {
        if (parent == null || name == null) return false;
        String filename = name.toLowerCase(Locale.ROOT);
        if (!filename.endsWith(BygramIconPacks.EXTENSION) && !filename.endsWith(BygramIconPacks.EXTERA_EXTENSION)) return false;
        BygramIconPacksActivity page = new BygramIconPacksActivity();
        page.setCurrentAccount(parent.getCurrentAccount());
        page.incomingPack = file;
        return parent.presentFragment(page);
    }

    @Override String title() {
        return getString(R.string.BygramIconPacks);
    }

    @Override
    public void onResume() {
        super.onResume();
        long account = getUserConfig().getClientUserId();
        runOperation(() -> BygramIconPacks.list(account), result -> {
            packs = result;
            list.adapter.update(false);
            if (incomingPack != null) {
                File file = incomingPack;
                incomingPack = null;
                showDialog(new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setTitle(getString(R.string.BygramIconPackImport))
                        .setMessage(getString(R.string.BygramIconPackImportConfirm))
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .setPositiveButton(getString(R.string.Import), (dialog, which) ->
                                runOperation(() -> {
                                    try (InputStream in = new FileInputStream(file)) {
                                        return BygramIconPacks.importPack(in);
                                    }
                                }, this::open)).create());
            }
        });
    }

    @Override void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        UUID active = BygramIconPacks.activeId();
        items.add(UItem.asButton(1, R.drawable.msg_add, getString(R.string.BygramIconPackCreate)));
        items.add(UItem.asButton(2, R.drawable.msg_download, getString(R.string.BygramIconPackImport)));
        items.add(UItem.asShadow(getString(R.string.BygramIconPacksInfo)));
        items.add(UItem.asButton(3, getString(R.string.BygramStockIcons), active == null ? getString(R.string.BygramIconPackActive) : null));
        for (int i = 0; i < packs.size(); i++) {
            BygramIconPacks.Summary pack = packs.get(i);
            UItem row = UItem.ofFactory(PackRow.Factory.class);
            row.id = 4 + i;
            row.object = pack;
            row.checked = pack.id.equals(active);
            row.clickCallback = view -> runOperation(() -> BygramIconPacks.load(pack.id), this::open);
            items.add(row);
        }
    }

    @Override void click(UItem item) {
        if (item.id == 1) {
            createPack();
        } else if (item.id == 2) {
            pick(IMPORT, "*/*");
        } else if (item.id == 3) {
            BygramIconPacks.activate(null);
            refreshIcons();
            list.adapter.update(false);
        } else if (item.id >= 4 && item.id - 4 < packs.size()) {
            BygramIconPacks.Summary pack = (BygramIconPacks.Summary) item.object;
            if (!item.checked) {
                runOperation(() -> BygramIconPacks.load(pack.id), selected -> {
                    BygramIconPacks.activate(selected);
                    refreshIcons();
                    list.adapter.update(false);
                });
            }
        }
    }

    private void createPack() {
        long account = getUserConfig().getClientUserId();
        if (account == 0) {
            showError(R.string.BygramIconPackSignIn);
            return;
        }
        EditText input = BygramSettingsActivity.createDialogInput(getContext(), getString(R.string.BygramIconPackName));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        EditText username = BygramSettingsActivity.createDialogInput(getContext(), getString(R.string.BygramIconPackUsername));
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        username.setFilters(new InputFilter[]{new InputFilter.LengthFilter(33)});
        LinearLayout fields = new LinearLayout(getContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(BygramSettingsActivity.dialogInputContainer(input), LayoutHelper.createLinear(-1, -2));
        fields.addView(BygramSettingsActivity.dialogInputContainer(username), LayoutHelper.createLinear(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.BygramIconPackCreate))
                .setView(fields)
                .setNegativeButton(getString(R.string.Cancel), (d, which) -> d.dismiss())
                .setPositiveButton(getString(R.string.Create), (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        input.setError(getString(R.string.BygramIconPackName));
                        input.requestFocus();
                        return;
                    }
                    String author = BygramIconPacks.authorUsername(username.getText().toString());
                    if (author == null) {
                        username.setError(getString(R.string.BygramIconPackUsernameInvalid));
                        username.requestFocus();
                        return;
                    }
                    d.dismiss();
                    runOperation(() -> BygramIconPacks.create(account, author, name), this::open);
                }).create();
        dialog.setDismissDialogByButtons(false);
        showDialog(dialog);
    }

    private static Drawable preview(Context context, int id, byte[] replacement) {
        Bitmap bitmap = BygramIconPacks.preview(replacement);
        if (bitmap != null) return new BitmapDrawable(context.getResources(), bitmap);
        Drawable drawable = context.getDrawable(id);
        if (drawable instanceof BygramIconDrawable) drawable = ((BygramIconDrawable) drawable).getDrawable();
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
        }
        return drawable;
    }

    private static final class PackRow extends LinearLayout {
        private static final int[] DEFAULT_COVER = {R.drawable.msg_add, R.drawable.msg_search, R.drawable.msg_share, R.drawable.msg_edit};
        private final ImageView[] icons = new ImageView[BygramIconPacks.COVER_SIZE];
        private final TextView title;
        private final TextView author;
        private final ImageView selected;
        private final ImageView action;

        private PackRow(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setGravity(Gravity.CENTER_VERTICAL);
            setPaddingRelative(dp(12), dp(8), dp(4), dp(8));
            setLayoutParams(new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            FrameLayout cover = new FrameLayout(context);
            cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            cover.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)));
            for (int i = 0; i < icons.length; i++) {
                ImageView image = new ImageView(context);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cover.addView(image, LayoutHelper.createFrame(20, 20, Gravity.TOP | Gravity.LEFT, 6 + i % 2 * 24, 6 + i / 2 * 24, 0, 0));
                icons[i] = image;
            }
            addView(cover, LayoutHelper.createLinear(56, 56));

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPaddingRelative(dp(12), 0, dp(4), 0);
            title = new TextView(context);
            title.setTextSize(16);
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            labels.addView(title, LayoutHelper.createLinear(-1, -2));
            author = new TextView(context);
            author.setTextSize(14);
            author.setSingleLine();
            author.setEllipsize(TextUtils.TruncateAt.END);
            author.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            labels.addView(author, LayoutHelper.createLinear(-1, -2));
            addView(labels, LayoutHelper.createLinear(0, -2, 1f));

            selected = new ImageView(context);
            selected.setImageDrawable(preview(context, R.drawable.msg_check_s, null));
            selected.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
            selected.setScaleType(ImageView.ScaleType.FIT_CENTER);
            selected.setContentDescription(getString(R.string.BygramIconPackActive));
            addView(selected, LayoutHelper.createLinear(20, 20));

            action = new ImageView(context);
            action.setPadding(dp(12), dp(12), dp(12), dp(12));
            action.setScaleType(ImageView.ScaleType.FIT_CENTER);
            action.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));
            addView(action, LayoutHelper.createLinear(48, 48));
        }

        private static final class Factory extends UItem.UItemFactory<PackRow> {
            @Override public PackRow createView(Context context, RecyclerListView list, int account, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new PackRow(context, resourcesProvider);
            }

            @Override public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView list) {
                PackRow row = (PackRow) view;
                BygramIconPacks.Summary pack = (BygramIconPacks.Summary) item.object;
                row.title.setText(pack.title);
                row.author.setText(pack.author);
                row.setSelected(item.checked);
                row.selected.setVisibility(item.checked ? VISIBLE : INVISIBLE);
                row.action.setImageDrawable(preview(row.getContext(), pack.editable ? R.drawable.msg_edit : R.drawable.ic_ab_other, null));
                row.action.setContentDescription(getString(pack.editable ? R.string.Edit : R.string.AccDescrMoreOptions));
                row.action.setOnClickListener(item.clickCallback);

                int slot = 0;
                for (byte[] replacement : pack.cover.values()) {
                    row.icons[slot].setImageDrawable(preview(row.getContext(), DEFAULT_COVER[slot], replacement));
                    slot++;
                }
                for (int id : DEFAULT_COVER) {
                    if (slot == row.icons.length) break;
                    String name = row.getResources().getResourceEntryName(id);
                    if (!pack.cover.containsKey(name)) row.icons[slot++].setImageDrawable(preview(row.getContext(), id, null));
                }
            }
        }
    }

    private static final class IconRowFactory extends UItem.UItemFactory<TextCell> {
        @Override public TextCell createView(Context context, RecyclerListView list, int account, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            TextCell cell = new TextCell(context, resourcesProvider);
            ImageView image = cell.getImageView();
            image.setAdjustViewBounds(true);
            image.setMaxWidth(dp(24));
            image.setMaxHeight(dp(24));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return cell;
        }

        @Override public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView list) {
            TextCell cell = (TextCell) view;
            cell.setTextAndValueAndIcon(item.text, item.textValue, preview(cell.getContext(), item.id, (byte[]) item.object), divider);
        }
    }

    private void open(BygramIconPacks.Pack pack) {
        long account = getUserConfig().getClientUserId();
        runOperation(() -> BygramIconPacks.canEdit(pack, account), editable -> {
            PackPage page = new PackPage(pack, editable);
            page.setCurrentAccount(currentAccount);
            presentFragment(page);
        });
    }

    @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode != IMPORT || resultCode != Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        runOperation(() -> {
            try (InputStream in = openContent(uri)) {
                return BygramIconPacks.importPack(in);
            }
        }, this::open);
    }

    private static final class PackPage extends IconPacksPage {
        private static final int IMAGE = 9211;
        private final Map<Integer, String> catalog = new LinkedHashMap<>();
        private BygramIconPacks.Pack pack;
        private final boolean editable;
        private EditText search;
        private String query = "";
        private String pendingIcon;

        private PackPage(BygramIconPacks.Pack pack, boolean editable) {
            this.pack = pack;
            this.editable = editable;
        }

        @Override String title() {
            return pack.title;
        }

        @Override public View createView(Context context) {
            if (catalog.isEmpty()) {
                TypedArray ids = context.getResources().obtainTypedArray(R.array.bygram_icon_catalog);
                try {
                    for (int i = 0; i < ids.length(); i++) {
                        int id = ids.getResourceId(i, 0);
                        if (id != 0) catalog.put(id, context.getResources().getResourceEntryName(id));
                    }
                } finally {
                    ids.recycle();
                }
            }
            search = BygramSettingsActivity.createDialogInput(context, getString(R.string.BygramIconPackSearch));
            search.setText(query);
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    query = s.toString().trim().toLowerCase(Locale.ROOT);
                    if (list != null) list.adapter.update(false);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            return super.createView(context);
        }

        @Override void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
            String counts = LocaleController.formatString(R.string.BygramIconPackCount, pack.icons.size());
            String author = pack.authorId == 0
                    ? LocaleController.formatString(R.string.BygramIconPackExteraAuthor, pack.author)
                    : LocaleController.formatString(R.string.BygramIconPackAuthor, pack.author, pack.authorId);
            items.add(UItem.asShadow(author + "\n" + counts));
            items.add(UItem.asButton(2, R.drawable.msg_share, getString(R.string.BygramIconPackShare)));
            items.add(UItem.asButton(3, R.drawable.msg_delete, getString(R.string.Delete)));
            items.add(UItem.asShadow(editable ? getString(R.string.BygramIconPackEditorInfo) : getString(R.string.BygramIconPackReadOnly)));
            for (Map.Entry<Integer, String> icon : catalog.entrySet()) {
                String name = icon.getValue();
                byte[] replacement = pack.icons.get(name);
                if ((!editable && replacement == null) || !name.contains(query)) continue;
                UItem row = UItem.ofFactory(IconRowFactory.class);
                row.id = icon.getKey();
                row.text = name;
                row.textValue = replacement == null ? null : getString(R.string.BygramIconPackReplaced);
                row.object = replacement;
                items.add(row);
            }
        }

        @Override void addList(FrameLayout content) {
            if (!editable) {
                super.addList(content);
                return;
            }
            LinearLayout layout = new LinearLayout(content.getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(search, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 16, 4, 16, 8));
            layout.addView(list, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 0, 1));
            content.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        @Override void click(UItem item) {
            if (item.id == 2) {
                share();
            } else if (item.id == 3) {
                showDialog(new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setTitle(pack.title)
                        .setMessage(getString(R.string.BygramIconPackDelete))
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .setPositiveButton(getString(R.string.Delete), (dialog, which) ->
                                runOperation(() -> {
                                    BygramIconPacks.remove(pack.id);
                                    return true;
                                }, done -> {
                                    refreshIcons();
                                    finishFragment();
                                })).create());
            } else if (catalog.containsKey(item.id)) {
                showIcon(item.id, catalog.get(item.id));
            }
        }

        private void showIcon(int id, String name) {
            Context context = getContext();
            byte[] replacement = pack.icons.get(name);
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            content.setPadding(dp(24), dp(20), dp(24), dp(8));

            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(dp(16), dp(16), dp(16), dp(16));
            image.setBackground(Theme.createRoundRectDrawable(dp(16), getThemedColor(Theme.key_windowBackgroundGray)));
            image.setImageDrawable(preview(context, id, replacement));
            content.addView(image, LayoutHelper.createLinear(96, 96));

            TextView label = new TextView(context);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            label.setTextSize(16);
            label.setText(name);
            content.addView(label, LayoutHelper.createLinear(-1, -2, 0, 12, 0, 0));

            AlertDialog.Builder dialog = new AlertDialog.Builder(context, getResourceProvider()).setView(content);
            if (editable) {
                dialog.setPositiveButton(getString(R.string.BygramIconPackReplace), (d, which) -> pickIcon(name));
                if (replacement != null) {
                    dialog.setNegativeButton(getString(R.string.ThemeResetToDefaults), (d, which) -> updateIcon(name, null));
                }
            } else {
                dialog.setPositiveButton(getString(R.string.OK), null);
            }
            showDialog(dialog.create());
        }

        private void pickIcon(String name) {
            pendingIcon = name;
            pick(IMAGE, "image/*");
        }

        private void updateIcon(String name, Uri uri) {
            BygramIconPacks.Pack previous = pack;
            long account = getUserConfig().getClientUserId();
            runOperation(() -> {
                byte[] image = null;
                if (uri != null) {
                    try (InputStream input = openContent(uri)) {
                        image = BygramIconPacks.prepareIcon(input);
                    }
                }
                return BygramIconPacks.edit(previous, account, name, image);
            }, updated -> {
                pack = updated;
                if (pack.id.equals(BygramIconPacks.activeId())) refreshIcons();
                list.adapter.update(false);
            });
        }

        private void share() {
            Context app = ApplicationLoader.applicationContext;
            BygramIconPacks.Pack selected = pack;
            runOperation(() -> {
                File dir = new File(app.getCacheDir(), "bygram-icon-share/" + UUID.randomUUID());
                if (!dir.mkdirs()) throw new IOException("Cannot make share directory");
                String name = selected.title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
                File file = new File(dir, "BYgram-" + name + BygramIconPacks.EXTENSION);
                BygramIconPacks.shareCopy(selected, file);
                return FileProvider.getUriForFile(app, ApplicationLoader.getApplicationId() + ".provider", file);
            }, uri -> {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(BygramIconPacks.MIME_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setClipData(ClipData.newRawUri(null, uri));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.BygramIconPackShare)), 9212);
                } catch (RuntimeException error) {
                    showError(R.string.BygramIconPackError);
                }
            });
        }

        @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            if (requestCode != IMAGE) return;
            String icon = pendingIcon;
            pendingIcon = null;
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null && icon != null) {
                updateIcon(icon, data.getData());
            }
        }
    }
}

abstract class IconPacksPage extends BygramSettingsPage {
    UniversalRecyclerView list;
    private boolean busy;

    abstract String title();
    abstract void fill(ArrayList<UItem> items, UniversalAdapter adapter);
    abstract void click(UItem item);

    @Override public View createView(Context context) {
        simpleActionBar.setTitle(title());
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        FrameLayout content = createSettingsContent(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        list = new UniversalRecyclerView(this, this::fill, (item, view, position, x, y) -> {
            if (!busy) click(item);
        }, null);
        list.setSections();
        list.adapter.setApplyBackground(false);
        addList(content);
        return fragmentView = content;
    }

    void addList(FrameLayout content) {
        content.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
    }

    final void pick(int requestCode, String type) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, requestCode);
        } catch (RuntimeException error) {
            showError(R.string.BygramIconPackError);
        }
    }

    static InputStream openContent(Uri uri) throws IOException {
        if (uri == null || !"content".equals(uri.getScheme())) throw new IOException("Invalid document URI");
        InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Cannot open document");
        return in;
    }

    final <T> void runOperation(Callable<T> operation, Utilities.Callback<T> done) {
        if (busy) return;
        busy = true;
        Utilities.themeQueue.postRunnable(() -> {
            try {
                T result = operation.call();
                AndroidUtilities.runOnUIThread(() -> {
                    busy = false;
                    if (!finishing && getParentActivity() != null) done.run(result);
                });
            } catch (Exception error) {
                AndroidUtilities.runOnUIThread(() -> {
                    busy = false;
                    if (!finishing && getParentActivity() != null) showError(R.string.BygramIconPackError);
                });
            }
        });
    }

    final void refreshIcons() {
        if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(false, false);
    }

    final void showError(int message) {
        showDialog(new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.ErrorOccurred)).setMessage(getString(message))
                .setPositiveButton(getString(R.string.OK), null).create());
    }
}
