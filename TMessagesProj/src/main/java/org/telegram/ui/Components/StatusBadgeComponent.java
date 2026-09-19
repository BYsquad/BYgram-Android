package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BygramActivityController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.DialogObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Premium.PremiumGradient;

public class StatusBadgeComponent {

    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusDrawable;
    private final View parentView;
    private final BadgeDrawable badgeDrawable;
    private Drawable verifiedDrawable;

    public StatusBadgeComponent(View parentView) {
        this(parentView, 18);
    }

    public StatusBadgeComponent(View parentView, int sizeDp) {
        this.parentView = parentView;
        statusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(parentView, AndroidUtilities.dp(sizeDp));
        badgeDrawable = new BadgeDrawable(parentView, statusDrawable, sizeDp, false);
    }

    public Drawable updateDrawable(TLObject object, int colorFilter, boolean animated) {
        if (object instanceof TLRPC.User) {
            return updateDrawable((TLRPC.User) object, null, colorFilter, animated);
        } else if (object instanceof TLRPC.Chat) {
            return updateDrawable(null, (TLRPC.Chat) object, colorFilter, animated);
        }
        return updateDrawable(null, null, colorFilter, animated);
    }

    public Drawable updateDrawable(TLRPC.User user, TLRPC.Chat chat, int colorFilter, boolean animated) {
        if (chat != null && chat.verified) {
            statusDrawable.set(verifiedDrawable = (verifiedDrawable == null ? new CombinedDrawable(Theme.dialogs_verifiedDrawable, Theme.dialogs_verifiedCheckDrawable) : verifiedDrawable), animated);
            statusDrawable.setColor(null);
        } else if (chat != null && DialogObject.getEmojiStatusDocumentId(chat.emoji_status) != 0) {
            statusDrawable.set(DialogObject.getEmojiStatusDocumentId(chat.emoji_status), animated);
            statusDrawable.setColor(colorFilter);
        } else if (user != null && user.verified) {
            statusDrawable.set(verifiedDrawable = (verifiedDrawable == null ? new CombinedDrawable(Theme.dialogs_verifiedDrawable, Theme.dialogs_verifiedCheckDrawable) : verifiedDrawable), animated);
            statusDrawable.setColor(null);
        } else if (user != null && DialogObject.getEmojiStatusDocumentId(user.emoji_status) != 0) {
            statusDrawable.set(DialogObject.getEmojiStatusDocumentId(user.emoji_status), animated);
            statusDrawable.setColor(colorFilter);
        } else if (user != null && user.premium) {
            statusDrawable.set(PremiumGradient.getInstance().premiumStarDrawableMini, animated);
            statusDrawable.setColor(colorFilter);
        } else {
            statusDrawable.set((Drawable) null, animated);
            statusDrawable.setColor(null);
        }
        if (user != null) {
            badgeDrawable.userId = user.id;
            return badgeDrawable;
        }
        badgeDrawable.userId = 0;
        return statusDrawable;
    }

    public Drawable getDrawable() {
        return badgeDrawable.userId != 0 ? badgeDrawable : statusDrawable;
    }

    public static Drawable withBadge(View parent, Drawable status, long userId, int sizeDp) {
        return withBadge(parent, status, userId, sizeDp, false);
    }

    public static Drawable withBadge(View parent, Drawable status, long userId, int sizeDp, boolean includeSecondary) {
        BadgeDrawable result = new BadgeDrawable(parent, status, sizeDp, includeSecondary);
        result.userId = userId;
        return result;
    }

    public static Drawable profileBadges(View parent, long userId, int sizeDp) {
        return withBadge(parent, null, userId, sizeDp, true);
    }

    public static Drawable unwrap(Drawable drawable) {
        return drawable instanceof BadgeDrawable ? ((BadgeDrawable) drawable).status : drawable;
    }

    public void onAttachedToWindow() {
        statusDrawable.attach();
        badgeDrawable.extera.attach();
    }

    public void onDetachedFromWindow() {
        statusDrawable.detach();
        badgeDrawable.extera.detach();
    }

    private static class BadgeDrawable extends Drawable {
        private final View parent;
        private final Drawable status;
        private final Drawable logo;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable extera;
        private final int size;
        private final int gap;
        private final boolean includeSecondary;
        private long userId;
        private long exteraDocumentId;

        BadgeDrawable(View parent, Drawable status, int sizeDp, boolean includeSecondary) {
            this.parent = parent;
            this.status = status;
            this.size = AndroidUtilities.dp(sizeDp);
            this.gap = AndroidUtilities.dp(3);
            this.includeSecondary = includeSecondary;
            this.logo = parent.getContext().getDrawable(R.drawable.bygram_logo_new);
            this.extera = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(parent, size);
        }

        private BygramActivityController.ProfileBadgeInfo info() {
            return BygramActivityController.getProfileBadgeInfo(UserConfig.selectedAccount, userId, parent);
        }

        private boolean showLogo(BygramActivityController.ProfileBadgeInfo info) {
            return info.bygram;
        }

        private boolean showExtera(BygramActivityController.ProfileBadgeInfo info) {
            long documentId = info.extera ? BygramActivityController.getExteraBadgeDocumentId(userId, parent) : 0;
            if (documentId != exteraDocumentId) extera.set(exteraDocumentId = documentId, false);
            return documentId != 0;
        }

        private boolean showStatus() {
            return statusDrawablePresent();
        }

        private boolean statusDrawablePresent() {
            return status != null && (!(status instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable)
                    || ((AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) status).getDrawable() != null);
        }

        @Override public void draw(Canvas canvas) {
            int left = getBounds().left;
            BygramActivityController.ProfileBadgeInfo info = info();
            boolean bygram = showLogo(info);
            boolean exteraVisible = showExtera(info);
            if (showStatus()) {
                status.setBounds(left, getBounds().top, left + size, getBounds().top + size);
                status.draw(canvas);
                left += size;
            }
            boolean exteraFirst = exteraVisible && (!bygram || info.primary == BygramActivityController.BADGE_EXTERA);
            if (bygram || exteraVisible) {
                if (left > getBounds().left) left += gap;
                drawBadge(canvas, exteraFirst ? extera : logo, left);
                left += size;
                if (includeSecondary && bygram && exteraVisible) {
                    left += gap;
                    drawBadge(canvas, exteraFirst ? logo : extera, left);
                }
            }
        }

        private void drawBadge(Canvas canvas, Drawable badge, int left) {
            badge.setBounds(left, getBounds().top, left + size, getBounds().top + size);
            badge.draw(canvas);
        }

        @Override public int getIntrinsicWidth() {
            BygramActivityController.ProfileBadgeInfo info = info();
            int badges = (showLogo(info) || showExtera(info)) ? 1 : 0;
            if (includeSecondary && showLogo(info) && showExtera(info)) badges++;
            return (showStatus() ? size : 0) + (showStatus() && badges > 0 ? gap : 0)
                    + badges * size + Math.max(0, badges - 1) * gap;
        }

        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { if (status != null) status.setAlpha(alpha); logo.setAlpha(alpha); extera.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) { if (status != null) status.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
