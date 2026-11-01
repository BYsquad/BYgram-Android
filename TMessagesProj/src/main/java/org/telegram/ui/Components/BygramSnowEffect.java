package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import org.telegram.messenger.BygramConfig;
import org.telegram.messenger.LiteMode;

/** Reuses Telegram's particle renderer without adding a touch-intercepting overlay. */
public final class BygramSnowEffect {
    private SnowflakesEffect effect;
    private final Path clip = new Path();

    public static android.graphics.drawable.Drawable overlay(View view, String key) {
        return new android.graphics.drawable.Drawable() {
            private final BygramSnowEffect snow = new BygramSnowEffect();
            private final RectF bounds = new RectF();
            @Override public void draw(Canvas canvas) {
                setBounds(0, 0, view.getWidth(), view.getHeight());
                bounds.set(0, 0, view.getWidth(), view.getHeight());
                snow.draw(view, canvas, key, bounds, 0);
                if (snow.effect != null) invalidateSelf();
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    public void draw(View view, Canvas canvas, String key, RectF bounds, float radius) {
        if (!BygramConfig.getBoolean(key) || !LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)
                || Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()
                || !view.isShown() || bounds.isEmpty()) {
            effect = null;
            return;
        }
        if (effect == null) {
            effect = new SnowflakesEffect(1);
            effect.setColorKey(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText);
        }
        effect.updateColors();
        canvas.save();
        clip.rewind();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        effect.onDraw(view, canvas);
        canvas.restore();
    }
}
