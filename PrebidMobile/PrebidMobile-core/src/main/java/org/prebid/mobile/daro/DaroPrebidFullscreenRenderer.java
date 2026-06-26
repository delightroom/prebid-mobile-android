package org.prebid.mobile.daro;

import android.content.Context;
import androidx.annotation.NonNull;
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

public final class DaroPrebidFullscreenRenderer implements DaroPrebidRenderHandle {
    private static final int DEFAULT_HTML_REWARD_SECONDS = 5;

    private final InterstitialView interstitialView;
    private final DaroPrebidRenderListener listener;
    private boolean destroyed;
    private boolean loaded;
    private boolean showing;
    private boolean startedSent;
    private boolean closedSent;
    private boolean impressionSent;
    private RenderMode renderMode = RenderMode.VIDEO;

    public DaroPrebidFullscreenRenderer(
        @NonNull Context context,
        @NonNull DaroPrebidRenderListener listener
    ) throws AdException {
        this.listener = listener;
        this.interstitialView = new InterstitialView(context);
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
                if (destroyed) {
                    return;
                }
                if (startedSent) {
                    return;
                }
                loaded = false;
                showing = false;
                DaroPrebidFullscreenRenderer.this.listener.renderFailed(error);
            }

            @Override
            public void onAdDisplayed(InterstitialView interstitialView) {
                notifyStarted();
                notifyImpression();
            }

            @Override
            public void onAdCompleted(InterstitialView interstitialView) {
                if (!destroyed) {
                    DaroPrebidFullscreenRenderer.this.listener.videoCompleted();
                }
            }

            @Override
            public void onAdClicked(InterstitialView interstitialView) {
                if (!destroyed) {
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

    public void renderVast(@NonNull String vastXml, int width, int height, boolean rewarded) {
        if (destroyed) {
            return;
        }
        resetRenderState(RenderMode.VIDEO);

        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        adConfiguration.setAdFormats(EnumSet.of(AdFormat.INTERSTITIAL, AdFormat.VAST));
        adConfiguration.setAdFormat(AdFormat.VAST);
        adConfiguration.setRewarded(rewarded);
        adConfiguration.setAdPosition(AdPosition.FULLSCREEN);
        adConfiguration.setPlacementType(PlacementType.INTERSTITIAL);
        adConfiguration.addSize(new AdSize(width, height));
        adConfiguration.setInterstitialSize(width, height);
        adConfiguration.setAutoRefreshDelay(0);
        adConfiguration.getRewardManager().clear();
        if (rewarded) {
            adConfiguration.getRewardManager().setRewardedExt(defaultVideoRewardedExt());
            adConfiguration.getRewardManager().setRewardListener(() -> {
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

        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        adConfiguration.setAdFormats(EnumSet.of(AdFormat.INTERSTITIAL));
        adConfiguration.setAdFormat(AdFormat.INTERSTITIAL);
        adConfiguration.setRewarded(rewarded);
        adConfiguration.setAdPosition(AdPosition.FULLSCREEN);
        adConfiguration.setPlacementType(PlacementType.INTERSTITIAL);
        adConfiguration.addSize(new AdSize(width, height));
        adConfiguration.setInterstitialSize(width, height);
        adConfiguration.setAutoRefreshDelay(0);
        adConfiguration.getRewardManager().clear();
        if (rewarded) {
            adConfiguration.getRewardManager().setRewardedExt(defaultHtmlRewardedExt());
            adConfiguration.getRewardManager().setRewardListener(() -> {
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
        if (destroyed) {
            return;
        }
        if (showing) {
            return;
        }
        if (!loaded) {
            listener.renderFailed(new AdException(
                AdException.INTERNAL_ERROR,
                "Fullscreen ad is not loaded"
            ));
            return;
        }
        showing = true;
        loaded = false;
        if (renderMode == RenderMode.HTML) {
            interstitialView.showHtmlAsInterstitial();
        } else {
            interstitialView.showVideoAsInterstitial();
        }
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        interstitialView.destroy();
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
        if (destroyed || closedSent) {
            return;
        }
        closedSent = true;
        showing = false;
        listener.closed();
    }

    private void resetRenderState(RenderMode renderMode) {
        this.renderMode = renderMode;
        loaded = false;
        showing = false;
        startedSent = false;
        closedSent = false;
        impressionSent = false;
    }

    private enum RenderMode {
        VIDEO,
        HTML
    }
}
