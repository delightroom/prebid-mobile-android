package org.prebid.mobile.daro;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import org.prebid.mobile.AdSize;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.models.AdDetails;
import org.prebid.mobile.rendering.models.CreativeModel;
import org.prebid.mobile.rendering.models.CreativeModelsMaker;
import org.prebid.mobile.rendering.networking.tracking.TrackingManager;
import org.prebid.mobile.rendering.video.OmEventTracker;
import org.prebid.mobile.rendering.views.AdViewManager;
import org.prebid.mobile.rendering.views.AdViewManagerListener;
import org.prebid.mobile.rendering.views.interstitial.InterstitialManager;

import java.util.Collections;

public final class DaroPrebidBannerRenderer implements DaroPrebidRenderHandle {
    private static final long VISIBILITY_CHECK_INTERVAL_MS = 100L;
    private static final long IMPRESSION_VISIBLE_PERIOD_MS = 1000L;
    private static final float IMPRESSION_VISIBLE_RATIO = 0.5f;

    private final Context context;
    private final ViewGroup container;
    private final DaroPrebidRenderListener listener;
    private final InterstitialManager interstitialManager = new InterstitialManager();
    private final AdViewManager adViewManager;
    private final BannerViewabilityTracker viewabilityTracker;
    private boolean destroyed;
    private boolean impressionSent;

    public DaroPrebidBannerRenderer(
        @NonNull Context context,
        @NonNull ViewGroup container,
        @NonNull DaroPrebidRenderListener listener
    ) throws AdException {
        this.context = context;
        this.container = container;
        this.listener = listener;
        this.viewabilityTracker = new BannerViewabilityTracker(container, this::notifyImpression);
        this.adViewManager = new AdViewManager(
            context,
            new AdViewManagerListener() {
                @Override
                public void adLoaded(AdDetails adDetails) {
                    // Rendering succeeds when Prebid gives us a creative view to attach.
                }

                @Override
                public void viewReadyForImmediateDisplay(View creative) {
                    if (destroyed) {
                        return;
                    }
                    container.removeAllViews();
                    container.addView(
                        creative,
                        new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    );
                    listener.renderSuccess();
                    viewabilityTracker.start();
                }

                @Override
                public void failedToLoad(AdException error) {
                    if (!destroyed) {
                        viewabilityTracker.stop();
                        listener.renderFailed(error);
                    }
                }

                @Override
                public void creativeClicked(String url) {
                    if (!destroyed) {
                        listener.click();
                    }
                }

                @Override
                public void creativeInterstitialClosed() {
                    if (!destroyed) {
                        listener.closed();
                    }
                }

                @Override
                public void creativeCollapsed() {
                    if (!destroyed) {
                        listener.closed();
                    }
                }
            },
            container,
            interstitialManager
        );
    }

    public void renderHtml(@NonNull String html, int width, int height) {
        if (destroyed) {
            return;
        }
        listener.renderStarted();

        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        adConfiguration.setAdFormat(AdFormat.BANNER);
        adConfiguration.addSize(new AdSize(width, height));
        adConfiguration.setInterstitialSize(width, height);

        CreativeModel model = new CreativeModel(
            TrackingManager.getInstance(),
            new OmEventTracker(),
            adConfiguration
        );
        model.setName("HTML");
        model.setHtml(html);
        model.setWidth(width);
        model.setHeight(height);
        model.setRequireImpressionUrl(false);

        CreativeModelsMaker.Result result = new CreativeModelsMaker.Result();
        result.transactionState = "daro";
        result.loaderIdentifier = "daro-display";
        result.creativeModels = Collections.singletonList(model);

        adViewManager.loadCreativeModels(adConfiguration, result);
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        viewabilityTracker.stop();
        adViewManager.destroy();
        interstitialManager.destroy();
        container.removeAllViews();
        listener.destroyed();
    }

    private void notifyImpression() {
        if (destroyed || impressionSent) {
            return;
        }
        impressionSent = true;
        listener.impression();
    }

    private static final class BannerViewabilityTracker {
        private final View view;
        private final Runnable onImpression;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private long visibleMs;
        private boolean running;

        private final Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }

                if (isViewable(view)) {
                    visibleMs += VISIBILITY_CHECK_INTERVAL_MS;
                    if (visibleMs >= IMPRESSION_VISIBLE_PERIOD_MS) {
                        running = false;
                        onImpression.run();
                        return;
                    }
                } else {
                    visibleMs = 0L;
                }

                handler.postDelayed(this, VISIBILITY_CHECK_INTERVAL_MS);
            }
        };

        BannerViewabilityTracker(@NonNull View view, @NonNull Runnable onImpression) {
            this.view = view;
            this.onImpression = onImpression;
        }

        void start() {
            stop();
            running = true;
            handler.post(checkRunnable);
        }

        void stop() {
            running = false;
            visibleMs = 0L;
            handler.removeCallbacks(checkRunnable);
        }

        private static boolean isViewable(View view) {
            if (!view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) {
                return false;
            }

            Rect visibleRect = new Rect();
            if (!view.getGlobalVisibleRect(visibleRect)) {
                return false;
            }

            int visibleArea = visibleRect.width() * visibleRect.height();
            int totalArea = view.getWidth() * view.getHeight();
            return totalArea > 0 && visibleArea >= totalArea * IMPRESSION_VISIBLE_RATIO;
        }
    }
}
