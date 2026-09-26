package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ActionBar.Theme.multAlpha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BygramConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;

import java.io.File;

public class ProfileMusicView extends View {

    public static final int ACTIVITY_CARD_HEIGHT = 56;
    private static final int MUSIC_CARD_HEIGHT = 100;

    private final Theme.ResourcesProvider resourcesProvider;
    private final int account;
    private final PorterDuffColorFilter filterColorWhite = new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
    private final PorterDuffColorFilter filterColorBlack = new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);

    private Text author, title, cardAuthor, cardTitle, activity;
    private String activityValue;
    private boolean hasMusicDocument;
    private final Paint iconPaint = new Paint();
    private final Paint arrowPaint = new Paint();
    private final Path arrowPath = new Path();
    private final Drawable icon;
    private final ImageReceiver coverImage;
    private long coverDocumentId;
    private final Drawable activityIcon;

    private final RectF rect = new RectF();
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();

    private final ButtonBounce bounce = new ButtonBounce(this);

    public ProfileMusicView(Context context, Theme.ResourcesProvider resourcesProvider, int account) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.account = account;

        icon = context.getResources().getDrawable(R.drawable.files_music).mutate();
        coverImage = new ImageReceiver(this);
        coverImage.setRoundRadius(dp(16));
        activityIcon = context.getResources().getDrawable(R.drawable.msg_message).mutate();

        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPath.moveTo(0, -dpf2(3.33f));
        arrowPath.lineTo(dpf2(3.16f), 0);
        arrowPath.lineTo(0, dpf2(3.33f));

        setColor(null);
        setText("Author", " - Title");
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.EXACTLY
            ),
            MeasureSpec.makeMeasureSpec(
                getContentHeight(),
                MeasureSpec.EXACTLY
            )
        );
    }

    public int getContentHeight() {
        int height = hasMusicDocument ? (BygramConfig.getBoolean(BygramConfig.CUSTOM_PROFILE_MUSIC) ? MUSIC_CARD_HEIGHT : 37) : 0;
        if (activity != null) height += (height == 0 ? 0 : 4) + ACTIVITY_CARD_HEIGHT;
        return dp(height);
    }

    public int getHeaderHeight() {
        // The first profile section already supplies the lower gap. Reserve less header space
        // while keeping the activity card's measured height and position intact.
        return getContentHeight() - (activity != null && !hasMusicDocument ? dp(24) : 0);
    }

    private int textColor = Color.WHITE;
    private float parentExpanded;
    private int backgroundColor;
    private boolean withShadows;

    public void setColor(MessagesController.PeerColor peerColor) {
        int color1, color2;
        if (peerColor == null) {
            color1 = color2 = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
        } else {
            color1 = peerColor.getBgColor1(Theme.isCurrentThemeDark());
            color2 = peerColor.getBgColor2(Theme.isCurrentThemeDark());
        }

        if (peerColor == null) {
            backgroundColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            withShadows = true;
        } else {
            backgroundColor = Theme.adaptHSV(ColorUtils.blendARGB(color1, color2, .15f), +.04f, -.09f);
            withShadows = false;
        }
        backgroundPaint.setColor(backgroundColor);
        checkTextColor();
    }

    private void checkTextColor() {
        final boolean useBlackText = parentExpanded < 0.8f && AndroidUtilities.computePerceivedBrightness(backgroundColor) > 0.85f;
        textColor = useBlackText ? Color.BLACK : Color.WHITE;
        icon.setColorFilter(useBlackText ? filterColorBlack : filterColorWhite);
        activityIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
        iconPaint.setColor(textColor);
        arrowPaint.setColor(Theme.multAlpha(textColor, 0.85f));
        invalidate();
    }

    public void setParentExpanded(float expanded) {
        if (parentExpanded != expanded) {
            parentExpanded = expanded;
            checkTextColor();
            invalidate();
        }
    }



    public void setMusicDocument(TLRPC.Document document) {
        hasMusicDocument = document != null;
        long documentId = document == null ? 0 : document.id;
        TLRPC.PhotoSize cover = document == null ? null : FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 144);
        if (coverDocumentId != documentId || (cover == null && !coverImage.hasImageLoaded())) {
            coverDocumentId = documentId;
            if (cover instanceof TLRPC.TL_photoSize || cover instanceof TLRPC.TL_photoSizeProgressive) {
                coverImage.setImage(ImageLocation.getForDocument(cover, document), "72_72", null, null, document, 0);
            } else {
                coverImage.setImageBitmap((Drawable) null);
                if (document != null) {
                    File file = FileLoader.getInstance(account).getPathToAttach(document);
                    if (file.isFile()) {
                        Utilities.globalQueue.postRunnable(() -> {
                            AudioInfo info = AudioInfo.getAudioInfo(file);
                            Bitmap bitmap = info == null ? null : info.getSmallCover();
                            if (bitmap != null) AndroidUtilities.runOnUIThread(() -> {
                                if (coverDocumentId == documentId) coverImage.setImageBitmap(bitmap);
                            });
                        });
                    }
                }
            }
        }
        CharSequence author = getAuthor(document);
        CharSequence title = getTitle(document);
        cardAuthor = new Text(TextUtils.isEmpty(author) ? getString(R.string.AudioUnknownArtist) : author, 14);
        cardTitle = new Text(TextUtils.isEmpty(title) ? getString(R.string.AudioUnknownTitle) : title, 18, AndroidUtilities.bold());
        if (TextUtils.isEmpty(author)) {
            if (TextUtils.isEmpty(title)) {
                author = getString(R.string.AudioUnknownArtist);
                title = " - " + getString(R.string.AudioUnknownTitle);
            } else {
                author = "";
            }
        } else if (!TextUtils.isEmpty(title)) {
            title = " - " + title;
        } else {
            title = "";
        }
        setText(author, title);
        setVisibility(hasMusicDocument || activity != null ? VISIBLE : GONE);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        coverImage.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        coverImage.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }

    public void setActivityText(CharSequence value) {
        String text = TextUtils.isEmpty(value) ? null : value.toString();
        if (TextUtils.equals(activityValue, text)) return;
        activityValue = text;
        activity = text == null ? null : new Text(text, 11, AndroidUtilities.bold());
        setVisibility(hasMusicDocument || activity != null ? VISIBLE : GONE);
        requestLayout();
        invalidate();
    }

    public static CharSequence getTitle(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                String title = attribute.title;
                if (title == null || title.length() == 0) {
                    title = FileLoader.getDocumentFileName(document);
                }
                return title;
            }
        }
        String fileName = FileLoader.getDocumentFileName(document);
        if (!TextUtils.isEmpty(fileName)) {
            return fileName;
        }
//        return getString(R.string.AudioUnknownTitle);
        return null;
    }

    public static CharSequence getAuthor(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                if (!attribute.voice) {
                    return attribute.performer;
                }
            }
        }
        return null;
    }

    public void setText(CharSequence author, CharSequence title) {
        this.author = new Text(author, 11, AndroidUtilities.bold());
        this.title = new Text(title, 11);

        setContentDescription(getString(R.string.AccDescrProfileMusic) + " " + author + " — " + title);
    }

    private ProfileActivity.AvatarImageView avatarView;
    private boolean ignoreRect = false;
    private RenderNode renderNode;
    private float renderNodeScale;
    private float renderNodeTranslateY;

    public void drawingBlur(boolean drawing) {
        if (ignoreRect != drawing || renderNode != null) {
            ignoreRect = drawing;
            renderNode = null;
            avatarView = null;
            invalidate();
        }
    }

    public void drawingBlur(RenderNode renderNode, ProfileActivity.AvatarImageView avatarView, float scale, float dy) {
        this.ignoreRect = false;
        this.renderNode = renderNode;
        this.avatarView = avatarView;
        this.renderNodeScale = scale;
        this.renderNodeTranslateY = dy;
        invalidate();
    }

    private float currentHeight;

    public void updatePosition(float y, float newHeight) {
        currentHeight = newHeight;
        setTranslationY(y - dp(12));
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!hasMusicDocument) return false;
        final float alpha = Utilities.clamp01((currentHeight) / dp(21));
        if (alpha <= 0) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            bounce.setPressed(rect.contains(event.getX(), event.getY()));
        } else if (event.getAction() == MotionEvent.ACTION_MOVE && bounce.isPressed()) {
            if (!rect.contains(event.getX(), event.getY())) {
                bounce.setPressed(false);
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            bounce.setPressed(false);
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (bounce.isPressed()) {
                performClick();
            }
            bounce.setPressed(false);
        }
        return bounce.isPressed();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (this.author == null || this.title == null) return;

        // Match ProfileActionsView: the stack contracts with its available header height
        // and fades out before the height reaches zero. A layer fades every drawable too.
        float fraction = Utilities.clamp01(currentHeight / Math.max(1, getHeaderHeight()));
        float collapseAlpha = Utilities.clamp01((fraction - 0.2f) / 0.8f);
        if (collapseAlpha <= 0f) return;
        int saveCount = canvas.save();
        canvas.scale(1f, fraction, getWidth() / 2f, 0f);
        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (255 * collapseAlpha));

        if (!hasMusicDocument) {
            drawActivityCard(canvas);
            canvas.restoreToCount(saveCount);
            return;
        }

        if (BygramConfig.getBoolean(BygramConfig.CUSTOM_PROFILE_MUSIC)) {
            drawCard(canvas);
            drawActivityCard(canvas);
            canvas.restoreToCount(saveCount);
            return;
        }

        final float alpha = 1f;
        final float scale = bounce.getScale(0.02f);

        final int padding = dp(12);
        final int maxWidth = getWidth() - padding * 2;

        this.author.ellipsize((maxWidth - dp(35)) / 2f);
        this.title.ellipsize(maxWidth - this.author.getWidth() - dp(35));

        final float width = dp(16.6f) + this.author.getWidth() + this.title.getWidth() + dp(8);
        final float containerWidth = dp(16) + width;

        canvas.save();
        canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);

        rect.set(
            (getWidth() - containerWidth) / 2f,
            dp(10),
            (getWidth() + containerWidth) / 2f,
            dp(10) + dp(17) * alpha
        );
        if (withShadows && SharedConfig.shadowsInSections) {
            backgroundPaint.setShadowLayer(dpf2(2), 0, dpf2(0.33f), multAlpha(0x0a000000, alpha));
            strokePaint.setShadowLayer(dpf2(0.33f), 0, 0, multAlpha(0x0c000000, alpha));
            strokePaint.setColor(0);
        } else {
            backgroundPaint.setShadowLayer(0, 0, 0, 0);
        }
        int wasAlpha = backgroundPaint.getAlpha();
        backgroundPaint.setAlpha((int) (wasAlpha * alpha));
        if (withShadows && SharedConfig.shadowsInSections) {
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, strokePaint);
        }
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, backgroundPaint);
        backgroundPaint.setAlpha(wasAlpha);

        clipPath.rewind();
        clipPath.addRoundRect(rect, rect.height() / 2f, rect.height() / 2f, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        if (!ignoreRect && renderNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated()) {
            canvas.save();
            canvas.translate(0f, renderNodeTranslateY);
            canvas.scale(renderNodeScale, renderNodeScale);
            canvas.drawRenderNode(renderNode);
            canvas.restore();
        }

        canvas.translate((getWidth() - width) / 2f, 0);

//        final long now = System.currentTimeMillis();
        final float cy = dpf2(18.5f);

        final int iconSz = dp(13);
        icon.setBounds(0, (int) cy - iconSz / 2, iconSz, (int) cy + iconSz / 2);
        icon.draw(canvas);

        canvas.translate(dp(16.6f), 0);
        this.author.draw(canvas, 0, cy, textColor, alpha);
        canvas.translate(this.author.getWidth(), 0);
        this.title.draw(canvas, 0, cy, textColor, 0.85f * alpha);
        canvas.translate(this.title.getWidth(), 0);

        arrowPaint.setStrokeWidth(dpf2(1.16f));
        canvas.translate(dpf2(4.8f), cy);
        canvas.drawPath(arrowPath, arrowPaint);

        canvas.restore();

        canvas.restore();

        drawActivityCard(canvas);
        canvas.restoreToCount(saveCount);
    }

    private void drawActivityCard(Canvas canvas) {
        if (activity == null) return;
        float alpha = 1f;
        float top = hasMusicDocument ? dp(BygramConfig.getBoolean(BygramConfig.CUSTOM_PROFILE_MUSIC) ? 104 : 41) : dp(4);
        rect.set(dp(20), top, getWidth() - dp(20), top + dp(48));
        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        backgroundPaint.setAlpha((int) (255 * alpha));
        canvas.drawRoundRect(rect, dp(18), dp(18), backgroundPaint);

        RectF iconBackground = AndroidUtilities.rectTmp;
        iconBackground.set(dp(28), top + dp(8), dp(60), top + dp(40));
        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        canvas.drawRoundRect(iconBackground, dp(12), dp(12), backgroundPaint);
        activityIcon.setBounds(dp(36), (int) top + dp(16), dp(52), (int) top + dp(32));
        activityIcon.draw(canvas);

        int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        activity.ellipsize(getWidth() - dp(96)).draw(canvas, dp(72), top + dp(24), color, alpha);
    }

    private void drawCard(Canvas canvas) {
        final float alpha = 1f;
        if (cardTitle == null || cardAuthor == null) return;

        rect.set(dp(12), dp(4), getWidth() - dp(12), dp(96));
        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        backgroundPaint.setAlpha((int) (255 * alpha));
        canvas.drawRoundRect(rect, dp(22), dp(22), backgroundPaint);

        RectF cover = AndroidUtilities.rectTmp;
        cover.set(dp(22), dp(14), dp(94), dp(86));
        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        canvas.drawRoundRect(cover, dp(16), dp(16), backgroundPaint);
        if (coverImage.hasImageLoaded()) {
            coverImage.setImageCoords(cover);
            coverImage.draw(canvas);
        } else {
            icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
            icon.setBounds(dp(44), dp(36), dp(72), dp(64));
            icon.draw(canvas);
        }

        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        int subtextColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
        float maxWidth = getWidth() - dp(128);
        cardTitle.ellipsize(maxWidth).draw(canvas, dp(112), dp(39), textColor, alpha);
        cardAuthor.ellipsize(maxWidth).draw(canvas, dp(112), dp(65), subtextColor, alpha);
    }
}
