package org.prebid.mobile.daro;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import org.prebid.mobile.AdSize;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.api.rendering.InterstitialView;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialViewListener;
import org.prebid.mobile.rendering.interstitial.rewarded.Reward;
import org.prebid.mobile.rendering.interstitial.rewarded.RewardedClosingRules;
import org.prebid.mobile.rendering.interstitial.rewarded.RewardedCompletionRules;
import org.prebid.mobile.rendering.interstitial.rewarded.RewardedExt;
import org.prebid.mobile.rendering.models.AdDetails;
import org.prebid.mobile.rendering.models.AdPosition;
import org.prebid.mobile.rendering.models.PlacementType;
import org.prebid.mobile.rendering.session.manager.OmAdSessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public final class DaroPrebidFullscreenRenderer implements DaroPrebidRenderHandle {
    @VisibleForTesting static final int DEFAULT_DARO_SKIP_DELAY_SECONDS = 5;
    private static final int DEFAULT_HTML_REWARD_SECONDS = 5;
    @VisibleForTesting static final long PRESENTATION_START_TIMEOUT_MS = 10_000;

    private final Handler presentationHandler = new Handler(Looper.getMainLooper());
    private final Runnable presentationStartTimeout = () -> failPresentationStart("Fullscreen Activity did not start");

    private final InterstitialView interstitialView;
    private final DaroPrebidRenderListener listener;
    private boolean destroyed;
    private boolean loaded;
    private boolean showing;
    private boolean startedSent;
    private boolean closedSent;
    private boolean impressionSent;
    private boolean failureSent;
    private boolean completedSent;
    private boolean rewardSent;
    @Nullable private String presentationId;
    @Nullable private Activity presentationActivity;
    private RenderMode renderMode = RenderMode.VIDEO;
    private int skipDelaySeconds = DEFAULT_DARO_SKIP_DELAY_SECONDS;
    private boolean initialMuted = false;
    @Nullable private String clickThroughUrl;

    public DaroPrebidFullscreenRenderer(
        @NonNull Context context,
        @NonNull DaroPrebidRenderListener listener
    ) throws AdException {
        this(new InterstitialView(requireActivity(context)), listener);
    }

    @VisibleForTesting
    DaroPrebidFullscreenRenderer(
        @NonNull InterstitialView interstitialView,
        @NonNull DaroPrebidRenderListener listener
    ) {
        this.listener = listener;
        this.interstitialView = interstitialView;
        this.interstitialView.setInterstitialViewListener(new InterstitialViewListener() {
            @Override
            public void onAdLoaded(InterstitialView interstitialView, AdDetails adDetails) {
                if (destroyed) {
                    return;
                }
                loaded = true;
                DaroPrebidFullscreenRenderer.this.listener.renderSuccess();
            }

            @Override
            public void onAdFailed(InterstitialView interstitialView, AdException error) {
                if (destroyed || failureSent || closedSent) {
                    return;
                }
                boolean failedAfterDisplay = startedSent;
                failureSent = true;
                loaded = false;
                if (!failedAfterDisplay) {
                    showing = false;
                    finishPresentation();
                }
                DaroPrebidFullscreenRenderer.this.listener.renderFailed(error);
                if (failedAfterDisplay) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (destroyed || closedSent) {
                            return;
                        }
                        notifyClosed();
                    });
                }
            }

            @Override
            public void onAdDisplayed(InterstitialView interstitialView) {
                if (failureSent) {
                    return;
                }
                notifyStarted();
                notifyImpression();
            }

            @Override
            public void onAdCompleted(InterstitialView interstitialView) {
                if (!destroyed && !failureSent && !completedSent) {
                    completedSent = true;
                    DaroPrebidFullscreenRenderer.this.listener.videoCompleted();
                }
            }

            @Override
            public void onAdClicked(InterstitialView interstitialView) {
                if (!destroyed && !failureSent) {
                    DaroPrebidFullscreenRenderer.this.listener.click();
                }
            }

            @Override
            public void onAdClickThroughClosed(InterstitialView interstitialView) {
            }

            @Override
            public void onAdClosed(InterstitialView interstitialView) {
                notifyClosed();
            }
        });
        this.interstitialView.setPubBackGroundOpacity(1.0f);
    }

    public void setSkipDelaySeconds(int seconds) {
        skipDelaySeconds = Math.max(0, seconds);
    }

    public void setInitialMuted(boolean muted) {
        initialMuted = muted;
    }

    public void setClickThroughUrl(@Nullable String url) {
        clickThroughUrl = url;
    }

    public void renderVast(@NonNull String vastXml, int width, int height, boolean rewarded) {
        if (destroyed) {
            return;
        }
        resetRenderState(RenderMode.VIDEO);

        AdUnitConfiguration adConfiguration = createVastAdConfiguration(width, height, rewarded, skipDelaySeconds, initialMuted);
        adConfiguration.setDaroClickThroughUrl(clickThroughUrl);
        attachTrackingObserver(adConfiguration);
        adConfiguration.getRewardManager().clear();
        if (rewarded) {
            adConfiguration.getRewardManager().setRewardedExt(defaultVideoRewardedExt());
            adConfiguration.getRewardManager().setRewardListener(() -> {
                if (destroyed || failureSent || rewardSent) {
                    return;
                }
                rewardSent = true;
                Reward reward = adConfiguration.getRewardManager().getRewardedExt().getReward();
                String type = reward != null ? reward.getType() : "reward";
                int amount = reward != null ? reward.getCount() : 1;
                listener.rewardEarned(type, amount);
            });
        }

        interstitialView.loadVastAd(adConfiguration, vastXml);
    }

    public void renderHtml(@NonNull String html, int width, int height, boolean rewarded) {
        renderHtml(html, width, height, rewarded, Collections.emptyList());
    }

    public void renderHtml(
        @NonNull String html,
        int width,
        int height,
        boolean rewarded,
        @NonNull List<DaroPrebidOmidVerificationResource> omidVerificationResources
    ) {
        if (destroyed) {
            return;
        }
        resetRenderState(RenderMode.HTML);

        AdUnitConfiguration adConfiguration = createHtmlAdConfiguration(width, height, rewarded, skipDelaySeconds, initialMuted);
        attachTrackingObserver(adConfiguration);
        adConfiguration.getRewardManager().clear();
        if (rewarded) {
            adConfiguration.getRewardManager().setRewardedExt(defaultHtmlRewardedExt());
            adConfiguration.getRewardManager().setRewardListener(() -> {
                if (destroyed || failureSent || rewardSent) {
                    return;
                }
                rewardSent = true;
                Reward reward = adConfiguration.getRewardManager().getRewardedExt().getReward();
                String type = reward != null ? reward.getType() : "reward";
                int amount = reward != null ? reward.getCount() : 1;
                listener.rewardEarned(type, amount);
            });
        }

        interstitialView.loadHtmlAd(
            adConfiguration,
            html,
            width,
            height,
            nativeDisplayVerificationResources(omidVerificationResources)
        );
    }

    @VisibleForTesting
    static AdUnitConfiguration createVastAdConfiguration(int width, int height, boolean rewarded) {
        return createVastAdConfiguration(width, height, rewarded, DEFAULT_DARO_SKIP_DELAY_SECONDS);
    }

    @VisibleForTesting
    static AdUnitConfiguration createVastAdConfiguration(int width, int height, boolean rewarded, int skipDelaySeconds) {
        return createVastAdConfiguration(width, height, rewarded, skipDelaySeconds, false);
    }

    @VisibleForTesting
    static AdUnitConfiguration createVastAdConfiguration(int width, int height, boolean rewarded, int skipDelaySeconds, boolean initialMuted) {
        AdUnitConfiguration adConfiguration = createBaseAdConfiguration(width, height, rewarded, skipDelaySeconds, initialMuted);
        adConfiguration.setAdFormats(EnumSet.of(AdFormat.INTERSTITIAL, AdFormat.VAST));
        adConfiguration.setAdFormat(AdFormat.VAST);
        return adConfiguration;
    }

    @VisibleForTesting
    static AdUnitConfiguration createHtmlAdConfiguration(int width, int height, boolean rewarded) {
        return createHtmlAdConfiguration(width, height, rewarded, DEFAULT_DARO_SKIP_DELAY_SECONDS);
    }

    @VisibleForTesting
    static AdUnitConfiguration createHtmlAdConfiguration(int width, int height, boolean rewarded, int skipDelaySeconds) {
        return createHtmlAdConfiguration(width, height, rewarded, skipDelaySeconds, false);
    }

    @VisibleForTesting
    static AdUnitConfiguration createHtmlAdConfiguration(int width, int height, boolean rewarded, int skipDelaySeconds, boolean initialMuted) {
        AdUnitConfiguration adConfiguration = createBaseAdConfiguration(width, height, rewarded, skipDelaySeconds, initialMuted);
        adConfiguration.setAdFormats(EnumSet.of(AdFormat.INTERSTITIAL));
        adConfiguration.setAdFormat(AdFormat.INTERSTITIAL);
        return adConfiguration;
    }

    private static AdUnitConfiguration createBaseAdConfiguration(int width, int height, boolean rewarded, int skipDelaySeconds, boolean initialMuted) {
        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        adConfiguration.setRewarded(rewarded);
        adConfiguration.setAdPosition(AdPosition.FULLSCREEN);
        adConfiguration.setPlacementType(PlacementType.INTERSTITIAL);
        adConfiguration.addSize(new AdSize(width, height));
        adConfiguration.setInterstitialSize(width, height);
        adConfiguration.setAutoRefreshDelay(0);
        adConfiguration.setSkipDelay(Math.max(0, skipDelaySeconds));
        adConfiguration.setIsMuted(initialMuted);
        adConfiguration.setDaroFullscreenRenderer(true);
        return adConfiguration;
    }

    private void attachTrackingObserver(@NonNull AdUnitConfiguration adConfiguration) {
        adConfiguration.setDaroTrackingObserver(event -> {
            if (!destroyed && !failureSent) {
                listener.trackingEvent(event);
            }
        });
    }

    @NonNull
    private List<OmAdSessionManager.NativeDisplayVerificationResource> nativeDisplayVerificationResources(
        @NonNull List<DaroPrebidOmidVerificationResource> resources
    ) {
        List<OmAdSessionManager.NativeDisplayVerificationResource> mapped = new ArrayList<>();
        for (DaroPrebidOmidVerificationResource resource : resources) {
            mapped.add(new OmAdSessionManager.NativeDisplayVerificationResource(
                resource.getUrl(),
                resource.getVendorKey(),
                resource.getVerificationParameters()
            ));
        }
        return mapped;
    }

    @NonNull
    private RewardedExt defaultVideoRewardedExt() {
        return new RewardedExt(
            new Reward("reward", 1, null),
            new RewardedCompletionRules(
                null,
                null,
                null,
                null,
                RewardedCompletionRules.PlaybackEvent.COMPLETE,
                null
            ),
            new RewardedClosingRules()
        );
    }

    @NonNull
    private RewardedExt defaultHtmlRewardedExt() {
        return new RewardedExt(
            new Reward("reward", 1, null),
            new RewardedCompletionRules(
                DEFAULT_HTML_REWARD_SECONDS,
                null,
                DEFAULT_HTML_REWARD_SECONDS,
                null,
                null,
                null
            ),
            new RewardedClosingRules(0, RewardedClosingRules.Action.CLOSE_BUTTON)
        );
    }

    public void show() {
        showInternal(null, false);
    }

    public void show(@NonNull Context context) {
        Activity activity = activityFromContext(context);
        showInternal(activity, true);
    }

    private void showInternal(@Nullable Activity activity, boolean requiresActivity) {
        if (destroyed) {
            return;
        }
        if (showing || failureSent || closedSent) {
            return;
        }
        if (!loaded) {
            listener.renderFailed(new AdException(
                AdException.INTERNAL_ERROR,
                "Fullscreen ad is not loaded"
            ));
            return;
        }
        if (activityFromContext(interstitialView.getContext()) == null) {
            loaded = false;
            listener.renderFailed(new AdException(
                AdException.INTERNAL_ERROR,
                "Fullscreen ad failed to show: load Activity is no longer active"
            ));
            return;
        }
        if (requiresActivity && activity == null) {
            loaded = false;
            listener.renderFailed(new AdException(
                AdException.INTERNAL_ERROR,
                "Fullscreen ad failed to show: context does not contain an active Activity"
            ));
            return;
        }
        showing = true;
        loaded = false;
        Activity host = activity != null ? activity : activityFromContext(interstitialView.getContext());
        // Assign before launch: a synchronous Activity creation may present or fail immediately.
        presentationId = UUID.randomUUID().toString();
        presentationHandler.postDelayed(presentationStartTimeout, PRESENTATION_START_TIMEOUT_MS);
        try {
            DaroPrebidFullscreenActivity.launch(host, this, presentationId);
        } catch (RuntimeException error) {
            failPresentationStart(error.getMessage());
        }
    }

    void present(@NonNull Activity activity) {
        if (destroyed || closedSent || failureSent || presentationActivity != null) {
            activity.finish();
            return;
        }
        presentationHandler.removeCallbacks(presentationStartTimeout);
        presentationActivity = activity;
        if (renderMode == RenderMode.HTML) {
            interstitialView.showHtmlAsInterstitial(activity);
        } else {
            interstitialView.showVideoAsInterstitial(activity);
        }
    }

    void presentationDestroyed(@NonNull Activity activity) {
        if (presentationActivity != activity) return;
        presentationActivity = null;
        if (!destroyed && !closedSent) {
            notifyClosed();
        }
    }

    private void failPresentationStart(@Nullable String message) {
        if (destroyed || closedSent || failureSent) return;
        showing = false;
        loaded = false;
        failureSent = true;
        finishPresentation();
        listener.renderFailed(new AdException(AdException.INTERNAL_ERROR, message));
    }

    private void finishPresentation() {
        presentationHandler.removeCallbacks(presentationStartTimeout);
        DaroPrebidFullscreenActivity.release(presentationId);
        presentationId = null;
        Activity activity = presentationActivity;
        presentationActivity = null;
        if (activity != null) activity.finish();
    }

    @NonNull
    private static Activity requireActivity(@NonNull Context context) throws AdException {
        Activity activity = activityFromContext(context);
        if (activity == null) {
            throw new AdException(
                AdException.INTERNAL_ERROR,
                "Fullscreen renderer requires an active Activity context"
            );
        }
        return activity;
    }

    @VisibleForTesting
    @Nullable
    static Activity activityFromContext(@Nullable Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                Activity activity = (Activity) current;
                return activity.isFinishing() || activity.isDestroyed() ? null : activity;
            }
            Context baseContext = ((ContextWrapper) current).getBaseContext();
            if (baseContext == current) {
                return null;
            }
            current = baseContext;
        }
        return null;
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        interstitialView.destroy();
        finishPresentation();
        listener.destroyed();
    }

    private void notifyImpression() {
        if (destroyed || impressionSent) {
            return;
        }
        impressionSent = true;
        listener.impression();
    }

    private void notifyStarted() {
        if (destroyed || startedSent) {
            return;
        }
        startedSent = true;
        listener.renderStarted();
    }

    private void notifyClosed() {
        if (destroyed || closedSent || (failureSent && !startedSent)) {
            return;
        }
        closedSent = true;
        showing = false;
        interstitialView.dismissInterstitialAfterFailure();
        finishPresentation();
        listener.closed();
    }

    private void resetRenderState(RenderMode renderMode) {
        finishPresentation();
        this.renderMode = renderMode;
        loaded = false;
        showing = false;
        startedSent = false;
        closedSent = false;
        impressionSent = false;
        failureSent = false;
        completedSent = false;
        rewardSent = false;
    }

    private enum RenderMode {
        VIDEO,
        HTML
    }
}
