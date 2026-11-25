package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AppIconsSelectorCell.AdaptiveIconImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LauncherIconController.LauncherIcon;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.EnumMap;

final class BygramAppAvatarActivity extends BygramSettingsPage {
    private static final int COLUMNS = 4;
    private final EnumMap<LauncherIcon, View> tiles = new EnumMap<>(LauncherIcon.class);
    private LauncherIcon activeIcon;
    private LauncherIcon selectedIcon;
    private AdaptiveIconImageView preview;
    private TextView previewTitle;

    @Override
    public View createView(Context context) {
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(getString(R.string.BygramAppAvatar));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout content = createSettingsContent(context);
        content.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        content.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        preview = new AdaptiveIconImageView(context);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        layout.addView(preview, LayoutHelper.createLinear(88, 88, Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));
        previewTitle = createLabel(context, 15);
        previewTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        layout.addView(previewTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        layout.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(8), dp(4), dp(8), dp(4));
        scroll.addView(grid, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        tiles.clear();
        LinearLayout row = null;
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (icon != LauncherIcon.DEFAULT && icon != LauncherIcon.AQUA
                    && icon.ordinal() < LauncherIcon.BYGRAM_CLASSIC.ordinal()) {
                continue;
            }
            if (tiles.size() % COLUMNS == 0) {
                row = new LinearLayout(context);
                row.setWeightSum(COLUMNS);
                grid.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            View tile = createTile(context, icon);
            tiles.put(icon, tile);
            row.addView(tile, LayoutHelper.createLinear(0, 98, 1f, 2, 2, 2, 2));
        }

        ButtonWithCounterView apply = new ButtonWithCounterView(context, getResourceProvider()).setRound();
        apply.setText(getString(R.string.BygramAppAvatarApply));
        apply.setOnClickListener(view -> applySelection());
        layout.addView(apply, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 8, 16, 8));

        activeIcon = LauncherIconController.getCurrentIcon();
        select(selectedIcon != null ? selectedIcon : activeIcon != null ? activeIcon : LauncherIcon.DEFAULT);
        return fragmentView = content;
    }

    private View createTile(Context context, LauncherIcon icon) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setContentDescription(getString(icon.title));
        tile.setOnClickListener(view -> select(icon));

        int highlight = getThemedColor(Theme.key_listSelector);
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(dp(14));
        outline.setColor(highlight);
        outline.setStroke(dp(2), getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_selected}, outline);
        background.addState(new int[]{android.R.attr.state_focused}, Theme.createRoundRectDrawable(dp(14), highlight));
        background.addState(new int[]{}, Theme.createSimpleSelectorRoundRectDrawable(dp(14), 0, highlight));
        tile.setBackground(background);

        AdaptiveIconImageView image = new AdaptiveIconImageView(context);
        bindIcon(image, icon);
        tile.addView(image, LayoutHelper.createLinear(60, 60, Gravity.CENTER_HORIZONTAL));
        TextView label = createLabel(context, 12);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setText(getString(icon.title));
        tile.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 1, 3, 1, 0));
        return tile;
    }

    private TextView createLabel(Context context, int size) {
        TextView label = new TextView(context);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(size);
        label.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return label;
    }

    private void select(LauncherIcon icon) {
        View previous = tiles.get(selectedIcon);
        if (previous != null) previous.setSelected(false);
        selectedIcon = icon;
        View current = tiles.get(icon);
        if (current != null) current.setSelected(true);
        bindIcon(preview, icon);
        previewTitle.setText(getString(icon.title));
    }

    private static void bindIcon(AdaptiveIconImageView view, LauncherIcon icon) {
        view.setImageResource(icon.background);
        view.setForeground(icon.foreground);
        view.setBackgroundOuterPadding(icon.foreground == 0 ? 0 : dp(42));
    }

    private void applySelection() {
        if (selectedIcon != activeIcon) {
            try {
                changeSetting("app_icon", () -> LauncherIconController.setIcon(selectedIcon));
            } catch (RuntimeException error) {
                FileLog.e(error);
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.UnknownError)).show();
                return;
            }
        }
        finishFragment();
    }
}
