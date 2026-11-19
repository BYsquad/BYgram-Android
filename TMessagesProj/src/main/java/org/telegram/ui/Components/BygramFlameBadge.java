package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public final class BygramFlameBadge extends ReplacementSpan {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable flame;
    private final String count;
    private final int width;

    public BygramFlameBadge(Context context, int days, boolean active) {
        int color = active
                ? ColorUtils.blendARGB(0xffd76a1d, 0xffd12237, Math.min(days - 1, 29) / 29f)
                : 0xff8b8e93;
        flame = context.getDrawable(R.drawable.filled_fire).mutate();
        flame.setTint(color);
        flame.setBounds(0, 0, dp(12), dp(12));
        count = LocaleController.formatNumber(days, ',');
        paint.setTypeface(AndroidUtilities.bold());
        paint.setTextSize(dp(11));
        width = dp(23) + (int) Math.ceil(paint.measureText(count));
        paint.setStrokeWidth(dp(1));
        paint.setColor(color);
    }

    public CharSequence text() {
        SpannableString text = new SpannableString("🔥 " + count);
        text.setSpan(this, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    public int width() {
        return width;
    }

    public void draw(Canvas canvas, float left, float centerY, int textColor) {
        int border = paint.getColor();
        float halfStroke = paint.getStrokeWidth() / 2f;
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(left + halfStroke, centerY - dp(8) + halfStroke,
                left + width - halfStroke, centerY + dp(8) - halfStroke, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.translate(left + dp(5), centerY - dp(6));
        flame.draw(canvas);
        canvas.restore();
        paint.setColor(textColor);
        canvas.drawText(count, left + dp(18), centerY - (paint.ascent() + paint.descent()) / 2f, paint);
        paint.setColor(border);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return width;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        draw(canvas, x, y + (paint.ascent() + paint.descent()) / 2f, paint.getColor());
    }
}
