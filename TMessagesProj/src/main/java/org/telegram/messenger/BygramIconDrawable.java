package org.telegram.messenger;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.util.AttributeSet;

import androidx.annotation.Keep;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.WeakHashMap;

@Keep
public final class BygramIconDrawable extends DrawableWrapper {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String ORIGINAL_PREFIX = "bygram_original_";
    private static final WeakHashMap<BygramIconDrawable, Boolean> instances = new WeakHashMap<>();
    private String iconName;
    private Paint paint;
    private RectF destination;
    private ColorStateList tint;
    private PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;
    private PorterDuffColorFilter tintFilter;

    public BygramIconDrawable() {
        this(null, null);
    }

    private BygramIconDrawable(String iconName, Drawable original) {
        super(original);
        this.iconName = iconName;
        synchronized (instances) {
            instances.put(this, Boolean.TRUE);
        }
    }

    @Override
    public void inflate(Resources resources, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme)
            throws XmlPullParserException, IOException {
        super.inflate(resources, parser, attrs, theme);
        int source = attrs.getAttributeResourceValue(ANDROID, "drawable", 0);
        if (source == 0) throw new XmlPullParserException("Missing original icon");
        String name = resources.getResourceEntryName(source);
        if (!name.startsWith(ORIGINAL_PREFIX)) throw new XmlPullParserException("Invalid original icon");
        iconName = name.substring(ORIGINAL_PREFIX.length());
        setDrawable(resources.getDrawable(source, theme));
    }

    @Override
    public ConstantState getConstantState() {
        ConstantState original = getDrawable().getConstantState();
        if (original == null) return null;
        String name = iconName;
        int configurations = getChangingConfigurations();
        return new ConstantState() {
            @Override public Drawable newDrawable() {
                return new BygramIconDrawable(name, original.newDrawable());
            }

            @Override public Drawable newDrawable(Resources resources) {
                return new BygramIconDrawable(name, original.newDrawable(resources));
            }

            @Override public Drawable newDrawable(Resources resources, Resources.Theme theme) {
                return new BygramIconDrawable(name, original.newDrawable(resources, theme));
            }

            @Override public int getChangingConfigurations() {
                return configurations;
            }

            @Override public boolean canApplyTheme() {
                return original.canApplyTheme();
            }
        };
    }

    @Override
    public void draw(Canvas canvas) {
        Bitmap replacement = iconName == null ? null : BygramIconPacks.replacement(iconName);
        if (replacement == null) {
            super.draw(canvas);
            return;
        }
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            destination = new RectF();
        }
        Rect bounds = getBounds();
        float scale = Math.min(bounds.width() / (float) replacement.getWidth(), bounds.height() / (float) replacement.getHeight());
        float halfWidth = replacement.getWidth() * scale / 2f;
        float halfHeight = replacement.getHeight() * scale / 2f;
        destination.set(bounds.exactCenterX() - halfWidth, bounds.exactCenterY() - halfHeight,
                bounds.exactCenterX() + halfWidth, bounds.exactCenterY() + halfHeight);
        paint.setAlpha(getAlpha());
        ColorFilter filter = getColorFilter();
        paint.setColorFilter(BygramIconPacks.preservesColors() ? null : filter == null ? tintFilter : filter);
        canvas.drawBitmap(replacement, null, destination, paint);
    }

    @Override public void setTintList(ColorStateList tint) {
        super.setTintList(tint);
        this.tint = tint;
        updateTint();
    }

    @Override public void setTintMode(PorterDuff.Mode tintMode) {
        super.setTintMode(tintMode);
        this.tintMode = tintMode;
        updateTint();
    }

    @Override public boolean isStateful() {
        return super.isStateful() || tint != null && tint.isStateful();
    }

    @Override protected boolean onStateChange(int[] state) {
        boolean changed = super.onStateChange(state);
        if (tint == null) return changed;
        updateTint();
        return true;
    }

    private void updateTint() {
        tintFilter = tint == null || tintMode == null ? null :
                new PorterDuffColorFilter(tint.getColorForState(getState(), tint.getDefaultColor()), tintMode);
        invalidateSelf();
    }

    static void invalidateAll() {
        ArrayList<BygramIconDrawable> snapshot;
        synchronized (instances) {
            snapshot = new ArrayList<>(instances.keySet());
        }
        for (BygramIconDrawable icon : snapshot) icon.invalidateSelf();
    }
}
