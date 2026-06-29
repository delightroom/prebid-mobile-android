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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import org.prebid.mobile.LogUtil;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.core.R;
import org.prebid.mobile.rendering.interstitial.AdBaseDialog;
import org.prebid.mobile.rendering.interstitial.DialogEventListener;
import org.prebid.mobile.rendering.interstitial.rewarded.RewardedCompletionRules;
import org.prebid.mobile.rendering.interstitial.rewarded.RewardedExt;
import org.prebid.mobile.rendering.models.InterstitialDisplayPropertiesInternal;
import org.prebid.mobile.rendering.sdk.PrebidContextHolder;
import org.prebid.mobile.rendering.utils.helpers.CustomInsets;
import org.prebid.mobile.rendering.utils.helpers.InsetsUtils;
import org.prebid.mobile.rendering.utils.helpers.Utils;
import org.prebid.mobile.rendering.views.base.BaseAdView;
import org.prebid.mobile.rendering.views.webview.mraid.Views;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

@SuppressLint("NewApi")
//Interstitial video
public class InterstitialVideo extends AdBaseDialog {

    private static final String TAG = InterstitialVideo.class.getSimpleName();

    private static final int CLOSE_DELAY_DEFAULT_IN_MS = 10 * 1000;
    private static final int CLOSE_DELAY_MAX_IN_MS = 30 * 1000;

    private boolean useSkipButton = false;
    private boolean hasEndCard = false;
    private boolean isRewarded = false;

    //Leaving context here for testing
    //Reason:
    // "If these are pure JVM unit tests (i.e. run on your computer's JVM and not on an Android emulator/device), then you have no real implementations of methods on any Android classes.
    // You are using a mockable jar which just contains empty classes and methods with "final" removed so you can mock them, but they don't really work like when running normal Android."
    private final WeakReference<Context> contextReference;
    private final AdUnitConfiguration config;

    private Handler handler;

    private Timer timer;
    private TimerTask showCloseButtonTask = null;
    private int currentTimerTaskHash = 0;

    // Flag used by caller to close manually; More intuitive and reliable way to show
    // close button at the end of the video versus trusting the duration from VAST
    private boolean showCloseBtnOnComplete;

    private CountDownTimer countDownTimer;
    private CountDownTimer skipCountDownTimer;
    @Nullable private RelativeLayout lytCountDownCircle;
    @Nullable private DaroFullscreenChromeView daroChromeView;
    @Nullable private View legacyCallToActionView;

    private int remainingTimeInMs = -1;
    private int remainingCloseDelayInMs = -1;
    private long closeButtonDelayInMs = -1;
    private long closeButtonTimerStartedAtMs = -1;
    private boolean videoPaused = true;

    public InterstitialVideo(
            Context context,
            FrameLayout adView,
            InterstitialManager interstitialManager,
            AdUnitConfiguration config
    ) {
        super(context, interstitialManager);
        contextReference = new WeakReference<>(context);
        this.config = config;
        isRewarded = this.config.isRewarded();
        adViewContainer = adView;
        init();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        stopSkipCountDownTimer();
        stopCountDownTimer();
    }

    @Override
    protected void handleCloseClick() {
        close();
    }

    @Override
    protected void handleDialogShow() {
        handleAdViewShow();
        ensureDaroChromeView();
        ensureDaroSoundControl();

        scheduleShowButtonTask();
    }

    @Override
    public void changeCloseViewVisibility(int visibility) {
        if (daroChromeView != null) {
            if (visibility == View.VISIBLE) {
                daroChromeView.showSkipAvailable();
            } else {
                daroChromeView.hideSkip();
            }
            keepDaroChromeOnTop();
            return;
        }

        super.changeCloseViewVisibility(visibility);
    }

    public boolean shouldShowCloseButtonOnComplete() {
        return showCloseBtnOnComplete;
    }

    public void setShowButtonOnComplete(boolean isEnabled) {
        showCloseBtnOnComplete = isEnabled;
    }

    public void setHasEndCard(boolean hasEndCard) {
        this.hasEndCard = hasEndCard;
    }

    public boolean isVideoPaused() {
        return videoPaused;
    }

    public void scheduleShowCloseBtnTask(View adView) {
        scheduleShowCloseBtnTask(adView, AdUnitConfiguration.SKIP_OFFSET_NOT_ASSIGNED);
    }

    public void scheduleShowButtonTask() {
        if (hasEndCard) useSkipButton = true;

        int skipDelay = getSkipDelayMs();
        if (isDaroFullscreenRenderer()) {
            scheduleAllTimers(skipDelay);
            return;
        }

        long videoLength = getDuration(adViewContainer);
        if (videoLength <= skipDelay) {
            scheduleAllTimers(videoLength);
            showCloseBtnOnComplete = true;
        } else {
            scheduleAllTimers(skipDelay);
        }
    }

    public void scheduleShowCloseBtnTask(
            View adView,
            int closeDelayInMs
    ) {
        long delayInMs = getCloseDelayInMs(adView, closeDelayInMs);
        if (delayInMs == 0) {
            LogUtil.debug(TAG, "Delay is 0. Not scheduling skip button show.");
            return;
        }

        long videoLength = getDuration(adView);
        LogUtil.debug(TAG, "Video length: " + videoLength);
        if (videoLength <= delayInMs) {
            // Short video, show close at the end
            showCloseBtnOnComplete = true;
        } else {
            // Clamp close delay value
            long upperBound = Math.min(videoLength, CLOSE_DELAY_MAX_IN_MS);
            long closeDelayTimeInMs = Utils.clampInMillis((int) delayInMs, 0, (int) upperBound);
            scheduleAllTimers(closeDelayTimeInMs);
        }
    }

    public void pauseVideo() {
        LogUtil.debug(TAG, "Action: pauseVideo");
        videoPaused = true;
        stopTimer();
        stopSkipCountDownTimer();
        stopCountDownTimer();
    }

    public void resumeVideo() {
        LogUtil.debug(TAG, "Action: resumeVideo");
        videoPaused = false;

        int remainingTimerTimeInMs = getRemainingTimerTimeInMs();
        int closeDelayInMs = getRemainingCloseDelayInMs();
        if (isRewarded) {
            if (remainingTimerTimeInMs > 500L || closeDelayInMs >= 0) {
                scheduleRewardResumeTimers(
                    Math.max(0, remainingTimerTimeInMs),
                    closeDelayInMs >= 0 ? closeDelayInMs : Math.max(0, remainingTimerTimeInMs)
                );
            }
            return;
        }

        if (remainingTimerTimeInMs != AdUnitConfiguration.SKIP_OFFSET_NOT_ASSIGNED && remainingTimerTimeInMs > 500L) {
            scheduleShowCloseBtnTask(adViewContainer, remainingTimerTimeInMs);
        }
    }

    /**
     * Remove all views
     */
    public void removeViews() {
        if (adViewContainer != null) {
            adViewContainer.removeAllViews();
        }
    }

    /**
     * Queue new task that should be performed in UI thread.
     *
     * @param task that will perform in UI thread
     */
    public void queueUIThreadTask(Runnable task) {
        if (task != null && handler != null) {
            handler.post(task);
        }
    }

    public void close() {
        if (interstitialManager.handleVideoInterstitialClose(this::hide)) {
            stopTimer();
            stopSkipCountDownTimer();
            stopCountDownTimer();
            return;
        }

        cancel();

        //IMPORTANT: call interstitialClosed() so it sends back to the adViewContainer to reimplant after closing an ad.
        interstitialManager.interstitialAdClosed();
    }

    protected void init() {
        handler = new Handler(Looper.getMainLooper());
        timer = new Timer();

        Context context = contextReference.get();
        if (context == null) {
            return;
        }

        if (isRewarded) {
            lytCountDownCircle = (RelativeLayout) LayoutInflater.from(context)
                .inflate(R.layout.lyt_countdown_circle_overlay, null);
        }

        //remove it from parent, if any, before adding it to the new view
        Views.removeFromParent(adViewContainer);
        addContentView(
            adViewContainer,
            new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
        );
        // interstitialManager.setCountDownTimerView(lytCountDownCircle);
        setOnKeyListener((dialog, keyCode, event) -> keyCode == KeyEvent.KEYCODE_BACK);
    }

    @Override
    protected void addCloseView() {
        ensureDaroChromeView();
        if (daroChromeView == null) {
            super.addCloseView();
        }
    }

    @Override
    protected void addSkipView() {
        ensureDaroChromeView();
        if (daroChromeView == null) {
            super.addSkipView();
            return;
        }

        skipView = daroChromeView.getSkipButton();
        skipView.setOnClickListener(v -> {
            if (v.isEnabled()) {
                handleCloseClick();
            }
        });
    }

    @Override
    protected void addSoundView(boolean isMutedOnStart) {
        ensureDaroChromeView();
        if (daroChromeView == null) {
            super.addSoundView(isMutedOnStart);
            return;
        }

        soundView = daroChromeView.getSoundButton();
        soundView.setVisibility(View.VISIBLE);
        daroChromeView.setSoundButtonVisible(true);
        daroChromeView.setSoundMuted(isMutedOnStart);
        soundView.setOnClickListener(view -> {
            ImageView imageView = (ImageView) view;
            String tag = (String) imageView.getTag();
            if ("off".equals(tag)) {
                notifyDialogEvent(DialogEventListener.EventType.MUTE);
                daroChromeView.setSoundMuted(true);
            } else {
                notifyDialogEvent(DialogEventListener.EventType.UNMUTE);
                daroChromeView.setSoundMuted(false);
            }
        });
    }

    private long getOffsetLong(View view) {
        return (view instanceof BaseAdView) ? ((BaseAdView) view).getMediaOffset() : AdUnitConfiguration.SKIP_OFFSET_NOT_ASSIGNED;
    }

    private long getCloseDelayInMs(
            View adView,
            int closeDelayInMs
    ) {
        long delayInMs = AdUnitConfiguration.SKIP_OFFSET_NOT_ASSIGNED;

        long offsetLong = getOffsetLong(adView);
        if (offsetLong >= 0) {
            delayInMs = offsetLong;
        }

        int remainingTime = getRemainingTimerTimeInMs();
        if (closeDelayInMs == remainingTime && remainingTime >= 0) {
            delayInMs = closeDelayInMs;
        }

        if (delayInMs == AdUnitConfiguration.SKIP_OFFSET_NOT_ASSIGNED) {
            delayInMs = CLOSE_DELAY_DEFAULT_IN_MS;
        }
        LogUtil.debug(TAG, "Picked skip offset: " + delayInMs + " ms.");
        return delayInMs;
    }

    private void createCurrentTimerTask() {
        showCloseButtonTask = new TimerTask() {
            @Override
            public void run() {
                if (currentTimerTaskHash != this.hashCode()) {
                    cancel();
                    return;
                }

                queueUIThreadTask(() -> {
                    try {
                        if (useSkipButton) {
                            remainingCloseDelayInMs = 0;
                            closeButtonDelayInMs = -1;
                            closeButtonTimerStartedAtMs = -1;
                            if (daroChromeView != null) {
                                daroChromeView.showSkipAvailable();
                                keepDaroChromeOnTop();
                            }
                            if (skipView != null) {
                                skipView.setVisibility(View.VISIBLE);
                            }
                        } else {
                            remainingCloseDelayInMs = 0;
                            closeButtonDelayInMs = -1;
                            closeButtonTimerStartedAtMs = -1;
                            changeCloseViewVisibility(View.VISIBLE);
                        }
                    } catch (Exception e) {
                        LogUtil.error(TAG, "Failed to render custom close icon: " + Log.getStackTraceString(e));
                    }
                });
            }
        };

        currentTimerTaskHash = showCloseButtonTask.hashCode();
    }

    protected long getDuration(View view) {
        return (view instanceof BaseAdView) ? ((BaseAdView) view).getMediaDuration() : 0;
    }

    private void stopTimer() {
        updateRemainingCloseDelay();

        if (timer != null) {
            if (showCloseButtonTask != null) {
                showCloseButtonTask.cancel();
                showCloseButtonTask = null;
            }

            timer.cancel();
            timer.purge();

            timer = null;
        }
    }

    private void stopCountDownTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private int getRemainingTimerTimeInMs() {
        return remainingTimeInMs;
    }

    private int getRemainingCloseDelayInMs() {
        return remainingCloseDelayInMs;
    }

    private void handleAdViewShow() {
        if (interstitialManager != null) {
            interstitialManager.show();
        }
    }

    @VisibleForTesting
    protected void scheduleAllTimers(long delayInMs) {
        LogUtil.debug(TAG, "Scheduling timer at: " + delayInMs);

        stopTimer();

        timer = new Timer();

        createCurrentTimerTask();

        if (delayInMs >= 0) {
            long delayToShowCloseButton = delayInMs;

            if (isRewarded && !isDaroFullscreenRenderer()) {
                delayToShowCloseButton = getDelayToShowCloseButton((int) getRewardTimelineDurationMs(delayInMs), config);
            }

            scheduleCloseButtonTask(delayToShowCloseButton);
        }

        // Show timer until close
        if (isRewarded) {
            if (isDaroFullscreenRenderer()) {
                showDaroSkipCountdownTimer(delayInMs);
            }
            showDurationTimer(getRewardProgressDurationMs(delayInMs));
        } else {
            startTimer(delayInMs);
        }
    }

    @VisibleForTesting
    protected void scheduleCloseButtonTask(long delayInMs) {
        remainingCloseDelayInMs = (int) delayInMs;
        closeButtonDelayInMs = delayInMs;
        closeButtonTimerStartedAtMs = System.currentTimeMillis();
        timer.schedule(showCloseButtonTask, delayInMs);
    }

    @VisibleForTesting
    protected void scheduleRewardResumeTimers(
        long progressRemainingMs,
        long closeDelayRemainingMs
    ) {
        stopTimer();

        timer = new Timer();
        createCurrentTimerTask();
        scheduleCloseButtonTask(Math.max(0, closeDelayRemainingMs));
        if (isDaroFullscreenRenderer()) {
            showDaroSkipCountdownTimer(Math.max(0, closeDelayRemainingMs));
        }
        showDurationTimer(Math.max(0, progressRemainingMs));
    }

    @VisibleForTesting
    protected long getRewardTimelineDurationMs(long fallbackDurationMs) {
        long mediaDurationMs = getDuration(adViewContainer);
        return mediaDurationMs > 0 ? mediaDurationMs : fallbackDurationMs;
    }

    @VisibleForTesting
    protected long getRewardProgressDurationMs(long fallbackDurationMs) {
        long rewardBaseDurationMs = getRewardTimelineDurationMs(fallbackDurationMs);

        Integer completionTime = getTimeToReward((int) rewardBaseDurationMs, config);
        if (completionTime == null) {
            return rewardBaseDurationMs;
        }

        return Utils.clampInMillis(completionTime, 0, (int) rewardBaseDurationMs);
    }

    protected void startTimer(long durationInMillis) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        countDownTimer = new CountDownTimer(durationInMillis, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int roundedMillis = Math.round((float) millisUntilFinished / 1000f);
                remainingTimeInMs = (int) millisUntilFinished;
                updateDaroSkipCountdown(roundedMillis);
                updateDaroProgress(durationInMillis, millisUntilFinished);
            }

            @Override
            public void onFinish() {
                remainingTimeInMs = 0;
                updateDaroProgress(durationInMillis, 0);
                if (daroChromeView != null) {
                    daroChromeView.showSkipAvailable();
                    keepDaroChromeOnTop();
                }
            }
        };
        updateDaroSkipCountdown(Math.round((float) durationInMillis / 1000f));
        updateDaroProgress(durationInMillis, durationInMillis);
        countDownTimer.start();
    }

    /**
     * @param durationInMillis - duration to count down
     */
    @VisibleForTesting
    protected void showDurationTimer(long durationInMillis) {
        if (durationInMillis == 0) {
            remainingTimeInMs = 0;
            if (daroChromeView != null) {
                daroChromeView.setProgressFraction(1f);
                daroChromeView.showRewardUnlocked(true);
                showDaroCallToActionForReward();
            }
            return;
        }

        if (daroChromeView != null) {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            countDownTimer = new CountDownTimer(durationInMillis, 100) {
                @Override
                public void onTick(long millisUntilFinished) {
                    remainingTimeInMs = (int) millisUntilFinished;
                    updateDaroProgress(durationInMillis, millisUntilFinished);
                }

                @Override
                public void onFinish() {
                    remainingTimeInMs = 0;
                    updateDaroProgress(durationInMillis, 0);
                    daroChromeView.showRewardUnlocked(true);
                    showDaroCallToActionForReward();
                }
            };
            updateDaroProgress(durationInMillis, durationInMillis);
            countDownTimer.start();
            return;
        }

        int paddingTop = (int) (50 * PrebidContextHolder.getContext().getResources().getDisplayMetrics().density);
        lytCountDownCircle.setPadding(0, 0, 0, paddingTop);

        final ProgressBar pbProgress = lytCountDownCircle.findViewById(R.id.Progress);
        pbProgress.setMax((int) durationInMillis);

        // Turns progress bar ccw 90 degrees so progress starts from the top
        final Animation animation = new RotateAnimation(0.0f,
                -90.0f,
                Animation.RELATIVE_TO_PARENT,
                0.5f,
                Animation.RELATIVE_TO_PARENT,
                0.5f
        );
        animation.setFillAfter(true);
        pbProgress.startAnimation(animation);

        final TextView lblCountdown = lytCountDownCircle.findViewById(R.id.lblCountdown);
        final WeakReference<FrameLayout> weakAdViewContainer = new WeakReference<>(adViewContainer);
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        countDownTimer = new CountDownTimer(durationInMillis, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int roundedMillis = Math.round((float) millisUntilFinished / 1000f);
                remainingTimeInMs = (int) millisUntilFinished;
                pbProgress.setProgress((int) millisUntilFinished);
                lblCountdown.setText(String.format(Locale.US, "%d", roundedMillis));
            }

            @Override
            public void onFinish() {
                FrameLayout adViewContainer = weakAdViewContainer.get();
                if (adViewContainer == null) {
                    return;
                }
                adViewContainer.removeView(lytCountDownCircle);

                if (isRewarded && !hasEndCard) {
                    View learnMore = adViewContainer.findViewById(R.id.tv_learn_more);
                    if (learnMore != null) {
                        learnMore.setVisibility(View.VISIBLE);
                    }
                }
            }
        };
        countDownTimer.start();
        if (lytCountDownCircle.getParent() != null) {
            Views.removeFromParent(lytCountDownCircle);
        }
        adViewContainer.addView(lytCountDownCircle);
        InsetsUtils.addCutoutAndNavigationInsets(lytCountDownCircle);
    }

    @VisibleForTesting
    protected void showDaroSkipCountdownTimer(long durationInMillis) {
        stopSkipCountDownTimer();

        if (daroChromeView == null) {
            return;
        }

        if (durationInMillis <= 0) {
            remainingCloseDelayInMs = 0;
            daroChromeView.showSkipAvailable();
            keepDaroChromeOnTop();
            return;
        }

        skipCountDownTimer = new CountDownTimer(durationInMillis, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int roundedSeconds = Math.round((float) millisUntilFinished / 1000f);
                remainingCloseDelayInMs = (int) millisUntilFinished;
                updateDaroSkipCountdown(roundedSeconds);
            }

            @Override
            public void onFinish() {
                remainingCloseDelayInMs = 0;
                daroChromeView.showSkipAvailable();
                keepDaroChromeOnTop();
            }
        };
        updateDaroSkipCountdown(Math.round((float) durationInMillis / 1000f));
        skipCountDownTimer.start();
    }

    @VisibleForTesting
    protected void setRemainingTimeInMs(int value) {
        remainingTimeInMs = value;
    }

    @VisibleForTesting
    protected void setRemainingCloseDelayInMs(int value) {
        remainingCloseDelayInMs = value;
    }

    protected int getSkipDelayMs() {
        InterstitialDisplayPropertiesInternal properties = interstitialManager.getInterstitialDisplayProperties();
        if (properties != null) {
            int delay = properties.skipDelay * 1000;
            if (isDaroFullscreenRenderer()) {
                return Utils.clampInMillis(delay, 0, CLOSE_DELAY_MAX_IN_MS);
            }

            long videoDuration = getDuration(adViewContainer);
            long upperBound = Math.min(videoDuration, CLOSE_DELAY_MAX_IN_MS);
            return Utils.clampInMillis(delay, 0, (int) upperBound);
        }
        return CLOSE_DELAY_DEFAULT_IN_MS;
    }

    private boolean isDaroFullscreenRenderer() {
        return config != null && config.isDaroFullscreenRenderer();
    }

    @Nullable
    @VisibleForTesting
    protected static Integer getTimeToReward(int durationMs, AdUnitConfiguration config) {
        RewardedExt rewardedExt = config.getRewardManager().getRewardedExt();

        RewardedCompletionRules rules = rewardedExt.getCompletionRules();
        if (rules.getVideoEvent() != null) {
            return (int) rules.getVideoEvent().getCompletionTime(durationMs);
        }

        if (rules.getVideoTime() != null) {
            return rules.getVideoTime() * 1000;
        }

        return null;
    }

    @VisibleForTesting
    protected static Integer getDelayToShowCloseButton(int duration, AdUnitConfiguration config) {
        RewardedExt rewardedExt = config.getRewardManager().getRewardedExt();

        int completionTime = duration;
        Integer rewardedConfigTime = getTimeToReward(duration, config);
        if (rewardedConfigTime != null) {
            completionTime = rewardedConfigTime;
        }

        int postRewardTime = rewardedExt.getClosingRules().getPostRewardTime();
        return Math.min(duration, completionTime + (postRewardTime * 1000));
    }

    @VisibleForTesting
    @Nullable
    public DaroFullscreenChromeView getDaroFullscreenChromeView() {
        return daroChromeView;
    }

    @VisibleForTesting
    protected DaroFullscreenChromeView createDaroFullscreenChromeView(Context context) {
        return new DaroFullscreenChromeView(context);
    }

    private void ensureDaroChromeView() {
        if (daroChromeView != null || adViewContainer == null) {
            return;
        }

        Context context = contextReference.get();
        if (context == null) {
            return;
        }

        legacyCallToActionView = adViewContainer.findViewById(R.id.tv_learn_more);
        daroChromeView = createDaroFullscreenChromeView(context);
        daroChromeView.setSoundButtonVisible(false);
        bindLegacyCallToAction();
        applyDaroChromeInsets();
        Views.removeFromParent(daroChromeView);
        addContentView(
            daroChromeView,
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        );
        keepDaroChromeOnTop();
    }

    private void ensureDaroSoundControl() {
        if (daroChromeView == null) {
            return;
        }

        boolean isMuted = false;
        if (interstitialManager != null && interstitialManager.getInterstitialDisplayProperties() != null) {
            isMuted = interstitialManager.getInterstitialDisplayProperties().isMuted;
        }
        addSoundView(isMuted);
    }

    private void bindLegacyCallToAction() {
        if (daroChromeView == null) {
            return;
        }

        boolean showCallToAction = false;
        if (legacyCallToActionView != null) {
            showCallToAction = !isRewarded && legacyCallToActionView.getVisibility() == View.VISIBLE;
            legacyCallToActionView.setVisibility(View.GONE);
            legacyCallToActionView.setId(View.NO_ID);
            daroChromeView.getCallToActionButton().setOnClickListener(v -> legacyCallToActionView.performClick());
        }
        daroChromeView.setCallToActionVisible(showCallToAction);
    }

    private void showDaroCallToActionForReward() {
        if (daroChromeView != null && isRewarded && !hasEndCard && legacyCallToActionView != null) {
            legacyCallToActionView.setVisibility(View.GONE);
            daroChromeView.setCallToActionVisible(true);
            keepDaroChromeOnTop();
        }
    }

    private void applyDaroChromeInsets() {
        if (daroChromeView == null) {
            return;
        }

        Context context = daroChromeView.getContext();
        CustomInsets navigationInsets = InsetsUtils.getNavigationInsets(context);
        CustomInsets cutoutInsets = InsetsUtils.getCutoutInsets(context);
        daroChromeView.setSafeAreaInsets(
            navigationInsets.getTop() + cutoutInsets.getTop(),
            navigationInsets.getRight() + cutoutInsets.getRight(),
            navigationInsets.getBottom() + cutoutInsets.getBottom(),
            navigationInsets.getLeft() + cutoutInsets.getLeft()
        );
        keepDaroChromeOnTop();
    }

    private void updateDaroSkipCountdown(int roundedSeconds) {
        if (daroChromeView != null) {
            daroChromeView.showSkipCountdown(roundedSeconds);
            keepDaroChromeOnTop();
        }
    }

    private void updateDaroProgress(
        long durationInMillis,
        long millisUntilFinished
    ) {
        if (daroChromeView == null || durationInMillis <= 0) {
            return;
        }

        float progress = (durationInMillis - millisUntilFinished) / (float) durationInMillis;
        daroChromeView.setProgressFraction(progress);
        keepDaroChromeOnTop();
    }

    private void keepDaroChromeOnTop() {
        if (daroChromeView == null) {
            return;
        }

        daroChromeView.bringToFront();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            daroChromeView.setElevation(1000f);
            daroChromeView.setTranslationZ(1000f);
        }
    }

    private void stopSkipCountDownTimer() {
        if (skipCountDownTimer != null) {
            skipCountDownTimer.cancel();
            skipCountDownTimer = null;
        }
    }

    private void updateRemainingCloseDelay() {
        if (closeButtonDelayInMs < 0 || closeButtonTimerStartedAtMs < 0) {
            return;
        }

        long elapsedMs = System.currentTimeMillis() - closeButtonTimerStartedAtMs;
        remainingCloseDelayInMs = (int) Math.max(0, closeButtonDelayInMs - elapsedMs);
        closeButtonDelayInMs = -1;
        closeButtonTimerStartedAtMs = -1;
    }

}
