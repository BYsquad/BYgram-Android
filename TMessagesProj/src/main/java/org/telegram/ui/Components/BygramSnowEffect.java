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
