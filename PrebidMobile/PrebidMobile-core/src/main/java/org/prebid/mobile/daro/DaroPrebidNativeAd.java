package org.prebid.mobile.daro;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import org.prebid.mobile.PrebidNativeAd;
import org.prebid.mobile.PrebidNativeAdEventListener;
import org.prebid.mobile.api.exceptions.AdException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DaroPrebidNativeAd implements DaroPrebidRenderHandle {
    private final PrebidNativeAd nativeAd;
    @Nullable
    private final DaroPrebidNativeMedia media;
    private DaroPrebidRenderListener listener;
    @Nullable
    private WeakReference<View> boundContainer;
    private final List<WeakReference<View>> boundClickableViews = new ArrayList<>();
    private boolean destroyed;
    private boolean impressionTracked;

    DaroPrebidNativeAd(@NonNull PrebidNativeAd nativeAd, @Nullable DaroPrebidNativeMedia media) {
        this.nativeAd = nativeAd;
        this.media = media;
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

    @Nullable
    public DaroPrebidNativeMedia getMedia() {
        return media;
    }

    public boolean bind(
        @NonNull View container,
        @NonNull List<View> clickableViews,
        @Nullable DaroPrebidRenderListener listener
    ) {
        if (destroyed) {
            return false;
        }
        unbind();
        if (impressionTracked) {
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
                    impressionTracked = true;
                }

                @Override
                public void onAdBecameViewable() {
                    impressionTracked = true;
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

        rememberBoundViews(container, clickableViews);
        nativeAd.enableDaroViewabilityImpression();
        if (listener != null) {
            listener.renderSuccess();
        }
        return true;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void unbind() {
        clearBoundViewListeners();
        nativeAd.daroUnregisterViewFromTracking();
        listener = null;
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        DaroPrebidRenderListener currentListener = listener;
        clearBoundViewListeners();
        nativeAd.destroy();
        listener = null;
        if (currentListener != null) {
            currentListener.destroyed();
        }
    }

    private void rememberBoundViews(
        @NonNull View container,
        @NonNull List<View> clickableViews
    ) {
        boundContainer = new WeakReference<>(container);
        boundClickableViews.clear();
        for (View view : clickableViews) {
            if (view != null) {
                boundClickableViews.add(new WeakReference<>(view));
            }
        }
    }

    private void clearBoundViewListeners() {
        if (boundContainer != null) {
            View container = boundContainer.get();
            if (container != null) {
                container.setOnClickListener(null);
            }
            boundContainer = null;
        }

        Iterator<WeakReference<View>> iterator = boundClickableViews.iterator();
        while (iterator.hasNext()) {
            View view = iterator.next().get();
            if (view != null) {
                view.setOnClickListener(null);
            }
            iterator.remove();
        }
    }
}
