/*
 *    Copyright 2018-2021 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile.rendering.views.interstitial;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.VisibleForTesting;
import org.prebid.mobile.core.R;

import java.util.Locale;

/**
 * Daro-owned fullscreen controls layered over Prebid's fullscreen video renderer.
 *
 * This view intentionally keeps Prebid's existing control ids so tracking, OMID,
 * and exposure code can continue to find the expected views.
 */
public class DaroFullscreenChromeView extends FrameLayout {

    @VisibleForTesting static final int HORIZONTAL_MARGIN_DP = 16;
    @VisibleForTesting static final int TOP_CONTROL_OFFSET_DP = 16;
    @VisibleForTesting static final int PROGRESS_TOP_OFFSET_DP = 68;
    @VisibleForTesting static final int REWARD_TOAST_TOP_OFFSET_DP = 88;
    @VisibleForTesting static final int CTA_BOTTOM_OFFSET_DP = 114;
    @VisibleForTesting static final int FOOTER_BOTTOM_OFFSET_DP = 34;
    @VisibleForTesting static final int END_CARD_CTA_TOP_OFFSET_DP = 496;

    @VisibleForTesting static final int SOUND_BUTTON_SIZE_DP = 36;
    @VisibleForTesting static final int CLOSE_BUTTON_SIZE_DP = 36;
    @VisibleForTesting static final int SKIP_COUNTDOWN_SHORT_WIDTH_DP = 91;
    @VisibleForTesting static final int SKIP_COUNTDOWN_LONG_WIDTH_DP = 101;
    @VisibleForTesting static final int END_CARD_SKIP_COUNTDOWN_WIDTH_DP = 99;
    @VisibleForTesting static final int SKIP_AVAILABLE_WIDTH_DP = 93;
    @VisibleForTesting static final int SKIP_HEIGHT_DP = 36;
    @VisibleForTesting static final int PROGRESS_HEIGHT_DP = 4;
    @VisibleForTesting static final int CTA_WIDTH_DP = 151;
    @VisibleForTesting static final int CTA_HEIGHT_DP = 48;
    @VisibleForTesting static final int END_CARD_CTA_WIDTH_DP = 326;
    @VisibleForTesting static final int END_CARD_CTA_HEIGHT_DP = 60;
    @VisibleForTesting static final int FOOTER_HEIGHT_DP = 32;
    @VisibleForTesting static final int REWARD_TOAST_WIDTH_DP = 166;
    @VisibleForTesting static final int REWARD_TOAST_HEIGHT_DP = 36;
    @VisibleForTesting static final int REWARD_TOAST_ENTER_DURATION_MS = 180;
    @VisibleForTesting static final int REWARD_TOAST_INITIAL_TRANSLATION_DP = -8;
    @VisibleForTesting static final float REWARD_TOAST_INITIAL_SCALE = 0.96f;
    @VisibleForTesting static final int AD_CHOICE_SIZE_DP = 20;

    private final ImageView closeButton;
    private final ImageView soundButton;
    private final LinearLayout skipButton;
    private final TextView skipPrimaryText;
    private final TextView skipSecondaryText;
    private final ImageView skipChevron;
    private final FrameLayout progressTrack;
    private final View progressFill;
    private final LinearLayout ctaButton;
    private final LinearLayout footerBadge;
    private final ImageView adChoiceButton;
    private final LinearLayout rewardToast;

    private int safeTopPx;
    private int safeRightPx;
    private int safeBottomPx;
    private int safeLeftPx;
    private float progressFraction;
    private boolean isEndCardLayout;

    public DaroFullscreenChromeView(Context context) {
        super(context);
        setId(R.id.daro_fullscreen_chrome);
        setClickable(false);
        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        closeButton = createCloseButton(context);
        soundButton = createSoundButton(context);
        skipButton = createSkipButton(context);
        skipPrimaryText = createSkipText(context, false);
        skipSecondaryText = createSkipText(context, true);
        skipChevron = createSkipChevron(context);
        progressTrack = createProgressTrack(context);
        progressFill = createProgressFill(context);
        ctaButton = createCtaButton(context);
        footerBadge = createFooterBadge(context);
        adChoiceButton = createAdChoiceButton(context);
        rewardToast = createRewardToast(context);

        skipButton.addView(skipPrimaryText);
        skipButton.addView(skipSecondaryText);
        skipButton.addView(skipChevron);
        progressTrack.addView(progressFill);
        footerBadge.addView(createLogoImage(context));
        footerBadge.addView(createAdBadge(context));

        addView(closeButton);
        addView(soundButton);
        addView(skipButton);
        addView(progressTrack);
        addView(ctaButton);
        addView(footerBadge);
        addView(adChoiceButton);
        addView(rewardToast);

        hideSkip();
        closeButton.setVisibility(View.GONE);
        showRewardUnlocked(false);
        setCallToActionVisible(false);
        setProgressFraction(0f);
        applyLayout();
    }

    public void setSafeAreaInsets(
        int topPx,
        int bottomPx
    ) {
        setSafeAreaInsets(topPx, 0, bottomPx, 0);
    }

    public void setSafeAreaInsets(
        int topPx,
        int rightPx,
        int bottomPx,
        int leftPx
    ) {
        safeTopPx = Math.max(0, topPx);
        safeRightPx = Math.max(0, rightPx);
        safeBottomPx = Math.max(0, bottomPx);
        safeLeftPx = Math.max(0, leftPx);
        applyLayout();
    }

    public void setSoundMuted(boolean isMuted) {
        soundButton.setImageResource(isMuted ? R.drawable.ic_volume_on : R.drawable.ic_volume_off);
        soundButton.setTag(isMuted ? "on" : "off");
    }

    public void showSkipCountdown(int remainingSeconds) {
        useVideoLayout();
        skipButton.setVisibility(View.VISIBLE);
        skipButton.setEnabled(false);
        skipButton.setPadding(dp(10), 0, dp(10), 0);
        skipPrimaryText.setText("Skip in");
        skipPrimaryText.setAlpha(0.5f);
        skipSecondaryText.setText(String.format(Locale.US, "%ds", Math.max(0, remainingSeconds)));
        skipSecondaryText.setVisibility(View.VISIBLE);
        skipChevron.setVisibility(View.GONE);
        setSkipWidth(remainingSeconds >= 10 ? SKIP_COUNTDOWN_LONG_WIDTH_DP : SKIP_COUNTDOWN_SHORT_WIDTH_DP);
    }

    public void showSkipAvailable() {
        useVideoLayout();
        skipButton.setVisibility(View.VISIBLE);
        skipButton.setEnabled(true);
        skipButton.setPadding(dp(10), 0, dp(10), 0);
        skipPrimaryText.setText("Skip Ad");
        skipPrimaryText.setAlpha(1f);
        skipSecondaryText.setVisibility(View.GONE);
        skipChevron.setImageResource(R.drawable.daro_chevron_right);
        skipChevron.setVisibility(View.VISIBLE);
        setSkipWidth(SKIP_AVAILABLE_WIDTH_DP);
    }

    public void hideSkip() {
        skipButton.setVisibility(View.GONE);
        skipButton.setEnabled(false);
    }

    public void showEndCardLayout() {
        isEndCardLayout = true;
        closeButton.setVisibility(View.VISIBLE);
        soundButton.setVisibility(View.GONE);
        showRewardUnlocked(false);
        skipButton.setVisibility(View.GONE);
        progressTrack.setVisibility(View.GONE);
        setCallToActionVisible(false);
        applyLayout();
    }

    public void setCloseButtonVisible(boolean visible) {
        closeButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setProgressFraction(float fraction) {
        progressFraction = Math.max(0f, Math.min(1f, fraction));
        updateProgressFill();
    }

    public void showRewardUnlocked(boolean visible) {
        rewardToast.animate().cancel();
        if (!visible) {
            resetRewardToastHiddenState();
            return;
        }

        if (rewardToast.getVisibility() == View.VISIBLE && rewardToast.getAlpha() == 1f) {
            return;
        }

        rewardToast.setVisibility(View.VISIBLE);
        rewardToast.setAlpha(0f);
        rewardToast.setTranslationY(dp(REWARD_TOAST_INITIAL_TRANSLATION_DP));
        rewardToast.setScaleX(REWARD_TOAST_INITIAL_SCALE);
        rewardToast.setScaleY(REWARD_TOAST_INITIAL_SCALE);
        rewardToast.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(REWARD_TOAST_ENTER_DURATION_MS)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    public void setSoundButtonVisible(boolean visible) {
        soundButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setCallToActionVisible(boolean visible) {
        ctaButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public View getCloseButton() {
        return closeButton;
    }

    public View getSoundButton() {
        return soundButton;
    }

    public View getSkipButton() {
        return skipButton;
    }

    public View getCallToActionButton() {
        return ctaButton;
    }

    public View getRewardToast() {
        return rewardToast;
    }

    public View getFooterBadge() {
        return footerBadge;
    }

    public View getAdChoiceButton() {
        return adChoiceButton;
    }

    @VisibleForTesting
    public float getProgressFraction() {
        return progressFraction;
    }

    @VisibleForTesting
    public TextView getSkipPrimaryText() {
        return skipPrimaryText;
    }

    @VisibleForTesting
    public TextView getSkipSecondaryText() {
        return skipSecondaryText;
    }

    @VisibleForTesting
    public ImageView getSkipChevron() {
        return skipChevron;
    }

    @VisibleForTesting
    public TextView getCallToActionLabel() {
        return (TextView) ctaButton.getChildAt(0);
    }

    @Override
    protected void onSizeChanged(
        int w,
        int h,
        int oldw,
        int oldh
    ) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateProgressFill();
    }

    private void applyLayout() {
        LayoutParams closeParams = new LayoutParams(dp(CLOSE_BUTTON_SIZE_DP), dp(CLOSE_BUTTON_SIZE_DP));
        closeParams.gravity = Gravity.START | Gravity.TOP;
        closeParams.leftMargin = safeLeftPx + dp(HORIZONTAL_MARGIN_DP);
        closeParams.topMargin = safeTopPx + dp(TOP_CONTROL_OFFSET_DP);
        closeButton.setLayoutParams(closeParams);

        LayoutParams soundParams = new LayoutParams(dp(SOUND_BUTTON_SIZE_DP), dp(SOUND_BUTTON_SIZE_DP));
        soundParams.gravity = Gravity.START | Gravity.TOP;
        soundParams.leftMargin = safeLeftPx + dp(HORIZONTAL_MARGIN_DP);
        soundParams.topMargin = safeTopPx + dp(TOP_CONTROL_OFFSET_DP);
        soundButton.setLayoutParams(soundParams);

        LayoutParams skipParams = getOrCreateLayoutParams(skipButton);
        skipParams.gravity = Gravity.END | Gravity.TOP;
        skipParams.rightMargin = safeRightPx + dp(HORIZONTAL_MARGIN_DP);
        skipParams.topMargin = safeTopPx + dp(TOP_CONTROL_OFFSET_DP);
        skipParams.height = dp(SKIP_HEIGHT_DP);
        skipButton.setLayoutParams(skipParams);

        LayoutParams progressParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(PROGRESS_HEIGHT_DP));
        progressParams.gravity = Gravity.TOP;
        progressParams.leftMargin = safeLeftPx + dp(HORIZONTAL_MARGIN_DP);
        progressParams.rightMargin = safeRightPx + dp(HORIZONTAL_MARGIN_DP);
        progressParams.topMargin = safeTopPx + dp(PROGRESS_TOP_OFFSET_DP);
        progressTrack.setLayoutParams(progressParams);

        LayoutParams ctaParams = new LayoutParams(
            dp(isEndCardLayout ? END_CARD_CTA_WIDTH_DP : CTA_WIDTH_DP),
            dp(isEndCardLayout ? END_CARD_CTA_HEIGHT_DP : CTA_HEIGHT_DP)
        );
        ctaParams.gravity = (isEndCardLayout ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        if (isEndCardLayout) {
            ctaParams.topMargin = dp(END_CARD_CTA_TOP_OFFSET_DP);
        } else {
            ctaParams.bottomMargin = safeBottomPx + dp(CTA_BOTTOM_OFFSET_DP);
        }
        ctaButton.setLayoutParams(ctaParams);

        int footerBottomMargin = Math.max(safeBottomPx, dp(FOOTER_BOTTOM_OFFSET_DP));

        LayoutParams footerParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(FOOTER_HEIGHT_DP));
        footerParams.gravity = Gravity.BOTTOM;
        footerParams.leftMargin = safeLeftPx;
        footerParams.rightMargin = safeRightPx;
        footerParams.bottomMargin = footerBottomMargin;
        footerBadge.setLayoutParams(footerParams);

        LayoutParams adChoiceParams = new LayoutParams(dp(AD_CHOICE_SIZE_DP), dp(AD_CHOICE_SIZE_DP));
        adChoiceParams.gravity = Gravity.BOTTOM | Gravity.END;
        adChoiceParams.rightMargin = safeRightPx;
        adChoiceParams.bottomMargin = footerBottomMargin;
        adChoiceButton.setLayoutParams(adChoiceParams);

        LayoutParams rewardParams = new LayoutParams(dp(REWARD_TOAST_WIDTH_DP), dp(REWARD_TOAST_HEIGHT_DP));
        rewardParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        rewardParams.topMargin = safeTopPx + dp(REWARD_TOAST_TOP_OFFSET_DP);
        rewardToast.setLayoutParams(rewardParams);
    }

    private void setSkipWidth(int widthDp) {
        LayoutParams params = getOrCreateLayoutParams(skipButton);
        params.width = dp(widthDp);
        params.height = dp(SKIP_HEIGHT_DP);
        skipButton.setLayoutParams(params);
    }

    private void setCallToActionLabel(String label) {
        getCallToActionLabel().setText(label);
    }

    private void useVideoLayout() {
        if (!isEndCardLayout) {
            return;
        }

        isEndCardLayout = false;
        closeButton.setVisibility(View.GONE);
        progressTrack.setVisibility(View.VISIBLE);
        setCallToActionLabel("Learn More");
        applyLayout();
    }

    private LayoutParams getOrCreateLayoutParams(View view) {
        LayoutParams params = view.getLayoutParams() instanceof LayoutParams
                              ? (LayoutParams) view.getLayoutParams()
                              : new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        return params;
    }

    private ImageView createCloseButton(Context context) {
        ImageView view = new ImageView(context);
        view.setId(R.id.iv_close_interstitial);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageResource(R.drawable.daro_close);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setBackground(pill(Color.argb(128, 0, 0, 0)));
        return view;
    }

    private ImageView createSoundButton(Context context) {
        ImageView view = new ImageView(context);
        view.setId(R.id.iv_sound_interstitial);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setImageResource(R.drawable.ic_volume_off);
        view.setPadding(dp(6), dp(6), dp(6), dp(6));
        view.setBackground(pill(Color.argb(128, 0, 0, 0)));
        view.setTag("off");
        return view;
    }

    private LinearLayout createSkipButton(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setId(R.id.iv_skip);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setBackground(pill(Color.argb(128, 0, 0, 0)));
        return view;
    }

    private TextView createSkipText(
        Context context,
        boolean bold
    ) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
        );
        if (bold) {
            params.leftMargin = dp(4);
        }
        view.setLayoutParams(params);
        return view;
    }

    private ImageView createSkipChevron(Context context) {
        ImageView view = new ImageView(context);
        view.setImageResource(R.drawable.daro_chevron_right);
        view.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(16), dp(16));
        params.leftMargin = dp(2);
        view.setLayoutParams(params);
        return view;
    }

    private FrameLayout createProgressTrack(Context context) {
        FrameLayout view = new FrameLayout(context);
        view.setId(R.id.rl_count_down);
        view.setBackground(pill(Color.argb(51, 0, 0, 0)));
        return view;
    }

    private View createProgressFill(Context context) {
        View view = new View(context);
        view.setId(R.id.daro_progress_fill);
        view.setBackground(pill(Color.argb(230, 255, 255, 255)));
        view.setLayoutParams(new FrameLayout.LayoutParams(0, LayoutParams.MATCH_PARENT));
        return view;
    }

    private LinearLayout createCtaButton(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setId(R.id.tv_learn_more);
        view.setGravity(Gravity.CENTER);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setPadding(dp(20), dp(12), dp(20), dp(12));
        view.setBackground(rounded(Color.WHITE, dp(12)));
        view.setClickable(false);
        view.setFocusable(false);

        TextView label = new TextView(context);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        label.setText("Learn More");
        label.setTextColor(Color.BLACK);
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
        );
        labelParams.leftMargin = dp(4);
        labelParams.rightMargin = dp(4);
        label.setLayoutParams(labelParams);
        view.addView(label);

        ImageView trailingIcon = new ImageView(context);
        trailingIcon.setImageResource(R.drawable.daro_external_link);
        trailingIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(16), dp(16));
        iconParams.leftMargin = dp(4);
        trailingIcon.setLayoutParams(iconParams);
        view.addView(trailingIcon);

        return view;
    }

    private LinearLayout createFooterBadge(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setId(R.id.daro_ad_badge);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(12), 0, 0, 0);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    private ImageView createLogoImage(Context context) {
        ImageView view = new ImageView(context);
        view.setImageResource(R.drawable.daro_logo_alt);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(53), dp(16)));
        return view;
    }

    private ImageView createAdBadge(Context context) {
        ImageView view = new ImageView(context);
        view.setImageResource(R.drawable.daro_ad_badge);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(22), dp(16));
        params.leftMargin = dp(4);
        view.setLayoutParams(params);
        return view;
    }

    private ImageView createAdChoiceButton(Context context) {
        ImageView view = new ImageView(context);
        view.setId(R.id.daro_ad_choice);
        view.setImageResource(R.drawable.daro_ad_choice);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return view;
    }

    private LinearLayout createRewardToast(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setId(R.id.daro_reward_toast);
        view.setGravity(Gravity.CENTER);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setPadding(dp(12), 0, dp(14), 0);
        view.setBackground(pill(Color.argb(179, 0, 0, 0)));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.daro_reward_check);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        icon.setLayoutParams(iconParams);
        view.addView(icon);

        TextView label = new TextView(context);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        label.setText("Reward Unlocked!");
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
        );
        labelParams.leftMargin = dp(8);
        label.setLayoutParams(labelParams);
        view.addView(label);

        return view;
    }

    private void resetRewardToastHiddenState() {
        rewardToast.setVisibility(View.GONE);
        rewardToast.setAlpha(0f);
        rewardToast.setTranslationY(dp(REWARD_TOAST_INITIAL_TRANSLATION_DP));
        rewardToast.setScaleX(REWARD_TOAST_INITIAL_SCALE);
        rewardToast.setScaleY(REWARD_TOAST_INITIAL_SCALE);
    }

    private void updateProgressFill() {
        int width = progressTrack.getWidth();
        if (width <= 0 && progressTrack.getLayoutParams() instanceof MarginLayoutParams) {
            MarginLayoutParams params = (MarginLayoutParams) progressTrack.getLayoutParams();
            width = Math.max(0, getWidth() - params.leftMargin - params.rightMargin);
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            Math.round(width * progressFraction),
            LayoutParams.MATCH_PARENT
        );
        progressFill.setLayoutParams(params);
    }

    private GradientDrawable pill(int color) {
        return rounded(color, dp(9999));
    }

    private GradientDrawable rounded(
        int color,
        int radiusPx
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
