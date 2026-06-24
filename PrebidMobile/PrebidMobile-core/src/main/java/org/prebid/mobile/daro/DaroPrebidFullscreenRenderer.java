package org.prebid.mobile.daro;

import android.content.Context;
import androidx.annotation.NonNull;
import org.prebid.mobile.AdSize;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.api.rendering.InterstitialView;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialViewListener;
import org.prebid.mobile.rendering.models.AdDetails;
import org.prebid.mobile.rendering.models.AdPosition;
import org.prebid.mobile.rendering.models.PlacementType;

import java.util.EnumSet;

public final class DaroPrebidFullscreenRenderer implements DaroPrebidRenderHandle {
    private final InterstitialView interstitialView;
    private final DaroPrebidRenderListener listener;
    private boolean destroyed;
    private boolean loaded;
    private boolean impressionSent;

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
                loaded = false;
                DaroPrebidFullscreenRenderer.this.listener.renderFailed(error);
            }

            @Override
            public void onAdDisplayed(InterstitialView interstitialView) {
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
                if (!destroyed) {
                    DaroPrebidFullscreenRenderer.this.listener.closed();
                }
            }
        });
        this.interstitialView.setPubBackGroundOpacity(1.0f);
    }

    public void renderVast(@NonNull String vastXml, int width, int height, boolean rewarded) {
        if (destroyed) {
            return;
        }
        loaded = false;
        impressionSent = false;

        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        adConfiguration.setAdFormats(EnumSet.of(AdFormat.INTERSTITIAL, AdFormat.VAST));
        adConfiguration.setAdFormat(AdFormat.VAST);
        adConfiguration.setRewarded(rewarded);
        adConfiguration.setAdPosition(AdPosition.FULLSCREEN);
        adConfiguration.setPlacementType(PlacementType.INTERSTITIAL);
        adConfiguration.addSize(new AdSize(width, height));
        adConfiguration.setInterstitialSize(width, height);
        adConfiguration.setAutoRefreshDelay(0);

        interstitialView.loadVastAd(adConfiguration, vastXml);
    }

    public void show() {
        if (destroyed) {
            return;
        }
        listener.renderStarted();
        if (!loaded) {
            listener.renderFailed(new AdException(
                AdException.INTERNAL_ERROR,
                "Fullscreen ad is not loaded"
            ));
            return;
        }
        interstitialView.showVideoAsInterstitial();
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
}
