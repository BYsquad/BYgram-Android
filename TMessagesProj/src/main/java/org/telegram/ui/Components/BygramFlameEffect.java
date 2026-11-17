package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

import java.util.Random;

public final class BygramFlameEffect extends View {
    private static final int[] COLORS = {0xffff592e, 0xffff842f, 0xffffb552, 0xffffd18c};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable flame;
    private final Spark[] sparks = new Spark[84];
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private float progress;

    public BygramFlameEffect(FrameLayout chat) {
        super(chat.getContext());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setTranslationZ(AndroidUtilities.dp(24));
        flame = ContextCompat.getDrawable(getContext(), R.drawable.filled_fire).mutate();
        Random random = new Random();
        for (int i = 0; i < sparks.length; i++) {
            sparks[i] = new Spark(
                    random.nextFloat(), random.nextFloat() * .37f, .45f + random.nextFloat() * .55f,
                    random.nextFloat() - .5f, AndroidUtilities.dpf2(2 + random.nextFloat() * 12),
                    COLORS[random.nextInt(COLORS.length)]);
        }
        chat.addView(this, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        animator.setDuration(1800);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (getParent() == chat) chat.removeView(BygramFlameEffect.this);
            }
        });
        animator.start();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (height > 0) paint.setShader(new LinearGradient(0, height * .30f, 0, height,
                new int[] {0x00ff662e, 0x50ff6328, 0xccff531c}, new float[] {0, .65f, 1}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float glow = Math.min(1, Math.min(progress * 6f, (1f - progress) * 2.4f));
        paint.setAlpha((int) (150 * glow));
        canvas.drawRect(0, 0, width, height, paint);
        for (Spark spark : sparks) {
            float age = (progress - spark.delay) / (1 - spark.delay);
            if (age <= 0 || age >= 1) continue;
            float rise = (float) Math.pow(age, .78f);
            float x = width * (spark.x + spark.drift * rise * .28f);
            float y = height * (1.04f - spark.rise * rise);
            float radius = spark.radius * (1.22f - age * .6f);
            float alpha = Math.min(1, age * 9) * (1 - age);
            flame.setTint(spark.color);
            flame.setAlpha((int) (255 * alpha));
            flame.setBounds((int) (x - radius), (int) (y - radius), (int) (x + radius), (int) (y + radius));
            flame.draw(canvas);
        }
    }

    public void stop() {
        animator.cancel();
    }

    private static final class Spark {
        final float x, delay, rise, drift, radius;
        final int color;

        Spark(float x, float delay, float rise, float drift, float radius, int color) {
            this.x = x;
            this.delay = delay;
            this.rise = rise;
            this.drift = drift;
            this.radius = radius;
            this.color = color;
        }
    }
}
