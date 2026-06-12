package org.prebid.mobile.daro;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.prebid.mobile.PrebidNativeAd;
import org.prebid.mobile.PrebidNativeAdEventListener;
import org.prebid.mobile.api.exceptions.AdException;

import java.util.List;

public final class DaroPrebidNativeAd implements DaroPrebidRenderHandle {
    private final PrebidNativeAd nativeAd;
    private DaroPrebidRenderListener listener;
    private boolean destroyed;

    DaroPrebidNativeAd(@NonNull PrebidNativeAd nativeAd) {
        this.nativeAd = nativeAd;
    }

    @NonNull
    public String getTitle() {
        return nativeAd.getTitle();
    }

    @NonNull
    public String getDescription() {
        return nativeAd.getDescription();
    }

    @NonNull
    public String getCallToAction() {
        return nativeAd.getCallToAction();
    }

    @NonNull
    public String getSponsoredBy() {
        return nativeAd.getSponsoredBy();
    }

    @NonNull
    public String getIconUrl() {
        return nativeAd.getIconUrl();
    }

    @NonNull
    public String getImageUrl() {
        return nativeAd.getImageUrl();
    }

    public boolean bind(
        @NonNull View container,
        @NonNull List<View> clickableViews,
        @Nullable DaroPrebidRenderListener listener
    ) {
        if (destroyed) {
            return false;
        }
        this.listener = listener;
        if (listener != null) {
            listener.renderStarted();
        }

        boolean registered = nativeAd.registerView(
            container,
            clickableViews,
            new PrebidNativeAdEventListener() {
                @Override
                public void onAdClicked() {
                    if (!destroyed && DaroPrebidNativeAd.this.listener != null) {
                        DaroPrebidNativeAd.this.listener.click();
                    }
                }

                @Override
                public void onAdImpression() {
                    // Buyer native impression trackers are fired inside PrebidNativeAd.
                }

                @Override
                public void onAdBecameViewable() {
                    if (!destroyed && DaroPrebidNativeAd.this.listener != null) {
                        DaroPrebidNativeAd.this.listener.impression();
                    }
                }

                @Override
                public void onAdExpired() {
                    if (!destroyed && DaroPrebidNativeAd.this.listener != null) {
                        DaroPrebidNativeAd.this.listener.expired();
                    }
                }
            }
        );

        if (!registered) {
            if (listener != null) {
                listener.renderFailed(new AdException(AdException.INTERNAL_ERROR, "Native view registration failed"));
            }
            this.listener = null;
            return false;
        }

        nativeAd.enableDaroViewabilityImpression();
        if (listener != null) {
            listener.renderSuccess();
        }
        return true;
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        nativeAd.destroy();
        if (listener != null) {
            listener.destroyed();
            listener = null;
        }
    }
}
