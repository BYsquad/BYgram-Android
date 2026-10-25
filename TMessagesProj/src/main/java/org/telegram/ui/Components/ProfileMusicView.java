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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BygramConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
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

public class ProfileMusicView extends View implements NotificationCenter.NotificationCenterDelegate {

    public static final int ACTIVITY_CARD_HEIGHT = 56;
    private static final int MUSIC_CARD_HEIGHT = 100;

    private final Theme.ResourcesProvider resourcesProvider;
    private final int account;
    private final boolean profileList;
    private final PorterDuffColorFilter filterColorWhite = new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
    private final PorterDuffColorFilter filterColorBlack = new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);

    private Text author, title, cardAuthor, cardTitle, activity;
    private String activityValue;
    protected boolean hasMusicDocument;
    private ProfileMusicView trailingView;
    private ProfileMusicView leadingView;

    // The two pills share a centered row and the same profile/blur animation state.
    public void setTrailingView(ProfileMusicView view) {
        trailingView = view;
        view.leadingView = this;
        invalidate();
        view.invalidate();
    }

    private final Paint iconPaint = new Paint();
    private final Paint arrowPaint = new Paint();
    private final Path arrowPath = new Path();
    private final Drawable icon;
    private final ImageReceiver coverImage;
    private long coverDocumentId;
    private TLRPC.Document musicDocument;
    private final Drawable activityIcon;

    private final RectF rect = new RectF();

    public void getButtonBoundsOnScreen(RectF bounds) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        bounds.set(rect);
        bounds.offset(location[0], location[1]);
    }

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final Path clipAvatarPath = new Path();

    private final ButtonBounce bounce = new ButtonBounce(this);

    public ProfileMusicView(Context context, Theme.ResourcesProvider resourcesProvider, int account) {
        this(context, resourcesProvider, account, false);
    }

    public ProfileMusicView(Context context, Theme.ResourcesProvider resourcesProvider, int account, boolean profileList) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.account = account;
        this.profileList = profileList;

        icon = context.getResources().getDrawable(R.drawable.files_music).mutate();
        coverImage = new ImageReceiver(this);
        coverImage.setCurrentAccount(account);
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
        int height = hasMusicDocument || hasVisibleCompanion() ? (profileList && BygramConfig.getBoolean(BygramConfig.CUSTOM_PROFILE_MUSIC) ? MUSIC_CARD_HEIGHT : 37) : 0;
        if (activity != null) height += (height == 0 ? 0 : 4) + ACTIVITY_CARD_HEIGHT;
        return dp(height);
    }

    public int getHeaderHeight() {
        // The first profile section already supplies the lower gap. Reserve less header space
        // while keeping the activity card's measured height and position intact.
        return getContentHeight() - (activity != null && !hasMusicDocument && !hasVisibleCompanion() ? dp(24) : 0);
    }

    protected int textColor = Color.WHITE;
    protected boolean hasProfileColor;
    private float parentExpanded;
    private int buttonColor;
    private boolean hasColorById;
    private RadialGradient radialGradient;
    private float radialGradientWidth;
    private final Paint shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();

    public void setColor(MessagesController.PeerColor peerColor) {
        hasProfileColor = peerColor != null;
        if (trailingView != null) trailingView.setColor(peerColor);
        invalidate();
    }

    // Same color as ProfileActionsView buttons.
    public void setButtonColor(int color, boolean hasColorById) {
        if (trailingView != null) trailingView.setButtonColor(color, hasColorById);
        if (buttonColor == color && this.hasColorById == hasColorById) return;
        buttonColor = color;
        this.hasColorById = hasColorById;
        radialGradient = null;
        backgroundPaint.setColor(color);
        checkTextColor();
    }

    private boolean isButtonColorLight() {
        return AndroidUtilities.computePerceivedBrightness(buttonColor) > 0.72f;
    }

    private void checkTextColor() {
        final boolean useBlackText = parentExpanded < 0.8f && isButtonColorLight();
        textColor = useBlackText ? Color.BLACK : Color.WHITE;
        icon.setColorFilter(useBlackText ? filterColorBlack : filterColorWhite);
        activityIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
        iconPaint.setColor(textColor);
        arrowPaint.setColor(Theme.multAlpha(textColor, 0.85f));
        invalidate();
    }

    public void setParentExpanded(float expanded) {
        if (trailingView != null) trailingView.setParentExpanded(expanded);
        if (parentExpanded != expanded) {
            parentExpanded = expanded;
            checkTextColor();
            invalidate();
        }
    }



    public void setMusicDocument(TLRPC.Document document) {
        hasMusicDocument = document != null && (profileList || !BygramConfig.getBoolean(BygramConfig.CUSTOM_PROFILE_MUSIC));
        musicDocument = document;
        updateCover();
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

    private void updateCover() {
        if (!profileList) return;
        final TLRPC.Document document = musicDocument;
        final long documentId = coverDocumentId = document == null ? 0 : document.id;
        if (document == null) {
            coverImage.setImageBitmap((Drawable) null);
            return;
        }
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        boolean sameTrack = playing != null && playing.currentAccount == account
                && playing.getDocument() != null && playing.getDocument().id == documentId;
        AudioInfo playingInfo = sameTrack ? MediaController.getInstance().getAudioInfo() : null;
        if (playingInfo != null && playingInfo.getCover() != null) {
            coverImage.setImageBitmap(playingInfo.getCover());
            return;
        }
        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 144);
        ImageLocation thumbLocation = thumb instanceof TLRPC.TL_photoSize || thumb instanceof TLRPC.TL_photoSizeProgressive
                ? ImageLocation.getForDocument(thumb, document) : null;
        String artworkUrl = MessageObject.getArtworkUrl(document, true);
        coverImage.setImage(artworkUrl == null ? null : ImageLocation.getForPath(artworkUrl), null,
                thumbLocation, "72_72", null, 0, null, document, 1);
        File file = sameTrack && !TextUtils.isEmpty(playing.messageOwner.attachPath)
                ? new File(playing.messageOwner.attachPath) : FileLoader.getInstance(account).getPathToAttach(document);
        if (!file.isFile()) file = FileLoader.getInstance(account).getPathToAttach(document);
        if (file.isFile()) {
            final File audioFile = file;
            Utilities.globalQueue.postRunnable(() -> {
                AudioInfo info = AudioInfo.getAudioInfo(audioFile);
                Bitmap bitmap = info == null ? null : info.getSmallCover();
                if (bitmap != null) AndroidUtilities.runOnUIThread(() -> {
                    if (coverDocumentId == documentId) coverImage.setImageBitmap(bitmap);
                });
            });
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.fileLoaded || musicDocument != null && args.length > 0
                && FileLoader.getAttachFileName(musicDocument).equals(args[0])) updateCover();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        coverImage.onAttachedToWindow();
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        updateCover();
    }

    @Override
    protected void onDetachedFromWindow() {
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
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
        if (trailingView != null) trailingView.drawingBlur(drawing);
        if (ignoreRect != drawing || renderNode != null) {
            ignoreRect = drawing;
            renderNode = null;
            avatarView = null;
            invalidate();
        }
    }

    public void drawingBlur(RenderNode renderNode, ProfileActivity.AvatarImageView avatarView, float scale, float dy) {
        if (trailingView != null) trailingView.drawingBlur(renderNode, avatarView, scale, dy);
        this.ignoreRect = false;
        this.renderNode = renderNode;
        this.avatarView = avatarView;
        this.renderNodeScale = scale;
        this.renderNodeTranslateY = dy;
        invalidate();
    }

    private float currentHeight;

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (trailingView != null) trailingView.setAlpha(alpha);
    }

    protected boolean isOnProfilePhoto() {
        return parentExpanded >= 0.8f;
    }

    public void updatePosition(float y, float newHeight) {
        if (trailingView != null) trailingView.updatePosition(y, newHeight);
        currentHeight = newHeight;
        setTranslationY(y - dp(12));
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!hasMusicDocument) return false;
        final float alpha = profileList ? 1f : Utilities.clamp01((currentHeight) / dp(21));
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

    protected float getContentWidth(float maxWidth) {
        final float textWidth = Math.max(0, maxWidth - dp(16) - dp(16.6f) - dp(8));
        this.author.ellipsize(textWidth / 2f);
        this.title.ellipsize(Math.max(0, textWidth - this.author.getWidth()));
        return dp(16.6f) + this.author.getWidth() + this.title.getWidth() + dp(8);
    }

    protected void drawContent(Canvas canvas, float alpha) {
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
    }

    private boolean hasVisibleCompanion() {
        ProfileMusicView companion = trailingView != null ? trailingView : leadingView;
        return companion != null && companion.getVisibility() == VISIBLE;
    }

    private float getPillWidth(float maxWidth) {
        return dp(16) + getContentWidth(maxWidth);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (this.author == null || this.title == null) return;

        // Match ProfileActionsView: the stack contracts with its available header height
        // and fades out before the height reaches zero. A layer fades every drawable too.
        float fraction = profileList ? 1f : Utilities.clamp01(currentHeight / Math.max(1, getHeaderHeight()));
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

        if (profileList && BygramConfig.getBoolean(BygramConfig.CUSTOM_PROFILE_MUSIC)) {
            drawCard(canvas);
            drawActivityCard(canvas);
            canvas.restoreToCount(saveCount);
            return;
        }

        final float alpha = 1f;
        backgroundPaint.setColor(buttonColor);
        final float scale = bounce.getScale(0.02f);

        final int padding = dp(12);
        final int maxWidth = getWidth() - padding * 2;

        float containerWidth;
        float left;
        if (hasVisibleCompanion()) {
            ProfileMusicView leading = leadingView != null ? leadingView : this;
            ProfileMusicView trailing = trailingView != null ? trailingView : this;
            // Reserve the balance first so long song titles cannot push it offscreen.
            float trailingWidth = trailing.getPillWidth(maxWidth / 2f);
            float leadingWidth = leading.getPillWidth(Math.max(0, maxWidth - trailingWidth - dp(6)));
            left = (getWidth() - leadingWidth - dp(6) - trailingWidth) / 2f;
            containerWidth = leadingView != null ? trailingWidth : leadingWidth;
            if (leadingView != null) left += leadingWidth + dp(6);
        } else {
            containerWidth = getPillWidth(maxWidth);
            left = (getWidth() - containerWidth) / 2f;
        }

        rect.set(left, dp(10), left + containerWidth, dp(10) + dp(17) * alpha);
        canvas.save();
        canvas.scale(scale, scale, rect.centerX(), getHeight() / 2f);

        final boolean withShadows = SharedConfig.shadowsInSections && isButtonColorLight() && parentExpanded < 0.5f;
        final float bgAlpha = hasColorById ? 0.1f : 1f;
        if (withShadows) {
            backgroundPaint.setShadowLayer(dpf2(1.5f), 0, 0, Theme.multAlpha(Color.BLACK & 0x20FFFFFF, alpha * bgAlpha));
        } else {
            backgroundPaint.setShadowLayer(0, 0, 0, 0);
        }
        int wasAlpha = backgroundPaint.getAlpha();
        backgroundPaint.setAlpha((int) (wasAlpha * alpha * bgAlpha));
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, backgroundPaint);
        backgroundPaint.setAlpha(wasAlpha);
        if (hasColorById && buttonColor != 0) {
            if (radialGradient == null || radialGradientWidth != rect.width()) {
                radialGradientWidth = rect.width();
                radialGradient = new RadialGradient(
                    rect.width() / 2f, dp(17) / 2f, rect.width() * 0.65f,
                    Theme.multAlpha(buttonColor, 0.8f), buttonColor,
                    Shader.TileMode.CLAMP
                );
                shaderPaint.setShader(radialGradient);
            }
            matrix.setTranslate(rect.left, rect.top);
            radialGradient.setLocalMatrix(matrix);
            shaderPaint.setAlpha((int) (0xFF * alpha));
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, shaderPaint);
        }

        clipPath.rewind();
        clipPath.addRoundRect(rect, rect.height() / 2f, rect.height() / 2f, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        if (!ignoreRect && renderNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && renderNode.hasDisplayList() && canvas.isHardwareAccelerated()) {
            canvas.save();
            if (avatarView != null) {
                View v = (View) avatarView.getParent();
                float vl = v.getX() - getX();
                float vt = v.getY() - getTranslationY();
                float vw = v.getWidth() * v.getScaleX();
                float vh = v.getHeight() * v.getScaleY();

                clipAvatarPath.rewind();
                clipAvatarPath.addRoundRect(
                    vl,
                    vt,
                    vl + vw,
                    vt + vh,
                    avatarView.getRoundRadiusForExpand() * v.getScaleX(),
                    avatarView.getRoundRadiusForExpand() * v.getScaleY(),
                    Path.Direction.CCW
                );
                canvas.clipPath(clipAvatarPath);
            }
            canvas.translate(0f, renderNodeTranslateY);
            canvas.scale(renderNodeScale, renderNodeScale);
            canvas.drawRenderNode(renderNode);
            canvas.restore();
        }

        canvas.translate(rect.left + dp(8), 0);
        drawContent(canvas, alpha);

        canvas.restore();

        canvas.restore();

        drawActivityCard(canvas);
        canvas.restoreToCount(saveCount);
    }

    private void drawActivityCard(Canvas canvas) {
        if (activity == null) return;
        float alpha = 1f;
        float top = hasMusicDocument || hasVisibleCompanion() ? dp(profileList && BygramConfig.getBoolean(BygramConfig.CUSTOM_PROFILE_MUSIC) ? 104 : 41) : dp(4);
        RectF activityRect = AndroidUtilities.rectTmp;
        activityRect.set(dp(20), top, getWidth() - dp(20), top + dp(48));
        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        backgroundPaint.setAlpha((int) (255 * alpha));
        canvas.drawRoundRect(activityRect, dp(18), dp(18), backgroundPaint);

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
        drawCardBackground(canvas, rect);

        RectF cover = AndroidUtilities.rectTmp;
        cover.set(dp(22), dp(14), dp(94), dp(86));
        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        canvas.drawRoundRect(cover, dp(16), dp(16), backgroundPaint);
        if (coverImage.hasBitmapImage()) {
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

    protected void drawCardBackground(Canvas canvas, RectF bounds) {
        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        backgroundPaint.setAlpha(255);
        canvas.drawRoundRect(bounds, dp(22), dp(22), backgroundPaint);
    }
}
