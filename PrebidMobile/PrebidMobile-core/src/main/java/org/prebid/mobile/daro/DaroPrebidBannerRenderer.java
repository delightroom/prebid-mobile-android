package org.prebid.mobile.daro;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import org.prebid.mobile.AdSize;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.api.rendering.VideoView;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.models.AdDetails;
import org.prebid.mobile.rendering.models.CreativeModel;
import org.prebid.mobile.rendering.models.CreativeModelsMaker;
import org.prebid.mobile.rendering.models.PlacementType;
import org.prebid.mobile.rendering.networking.tracking.TrackingManager;
import org.prebid.mobile.rendering.video.OmEventTracker;
import org.prebid.mobile.rendering.views.AdViewManager;
import org.prebid.mobile.rendering.views.AdViewManagerListener;
import org.prebid.mobile.rendering.views.interstitial.InterstitialManager;
import org.prebid.mobile.rendering.views.video.VideoViewListener;

import java.util.Collections;

public final class DaroPrebidBannerRenderer implements DaroPrebidRenderHandle {
    private final Context context;
    private final ViewGroup container;
    private final DaroPrebidRenderListener listener;
    private final InterstitialManager interstitialManager = new InterstitialManager();
    private final AdViewManager adViewManager;
    private VideoView videoView;
    private boolean destroyed;
    private boolean impressionSent;
    private boolean videoFailed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Runnable preparationTimeout = this::failPreparation;

    private void failPreparation() {
        failVideo(new AdException(AdException.INTERNAL_ERROR, "Video first frame timed out"));
    }

    private void failVideo(AdException error) {
        if (destroyed || videoFailed) return;
        videoFailed = true;
        handler.removeCallbacks(preparationTimeout);
        stopObservingNetwork();
        if (videoView != null) {
            videoView.destroy();
            container.removeView(videoView);
            videoView = null;
        }
        listener.renderFailed(error);
    }

    public DaroPrebidBannerRenderer(
        @NonNull Context context,
        @NonNull ViewGroup container,
        @NonNull DaroPrebidRenderListener listener
    ) throws AdException {
        this.context = context;
        this.container = container;
        this.listener = listener;
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
                }

                @Override
                public void failedToLoad(AdException error) {
                    if (!destroyed) {
                        listener.renderFailed(error);
                    }
                }

                @Override
                public void adDisplayed() {
                    notifyImpression();
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

    public void renderVast(@NonNull String vastXml, int width, int height) {
        if (destroyed) {
            return;
        }
        listener.renderStarted();

        try {
            AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
            adConfiguration.setAdFormat(AdFormat.VAST);
            adConfiguration.setPlacementType(PlacementType.IN_BANNER);
            adConfiguration.addSize(new AdSize(width, height));
            adConfiguration.setInterstitialSize(width, height);

            VideoView view = new VideoView(context, adConfiguration);
            view.setVideoViewListener(new VideoViewListener() {
                @Override
                public void onLoaded(@NonNull VideoView videoAdView, AdDetails adDetails) {
                    if (!destroyed && !videoFailed) {
                        handler.removeCallbacks(preparationTimeout);
                        listener.renderSuccess();
                    }
                }

                @Override
                public void onLoadFailed(@NonNull VideoView videoAdView, AdException error) {
                    failVideo(error);
                }

                @Override
                public void onDisplayed(@NonNull VideoView videoAdView) {
                    notifyImpression();
                }

                @Override
                public void onClickThroughOpened(@NonNull VideoView videoAdView) {
                    if (!destroyed) {
                        listener.click();
                    }
                }
            });
            view.setAutoPlay(true);
            view.setPrepareStillFrame(true);
            view.setPlaybackAllowed(false);
            view.setVideoPlayerClick(true);
            view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));

            container.removeAllViews();
            container.addView(view);
            videoView = view;
            observeNetwork();
            handler.postDelayed(preparationTimeout, 30000);
            view.loadAd(adConfiguration, vastXml);
        } catch (AdException exception) {
            failVideo(exception);
        }
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        stopObservingNetwork();
        if (videoView != null) {
            videoView.destroy();
            videoView = null;
        }
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

    private void observeNetwork() {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                handler.post(() -> updateNetwork());
            }
            @Override public void onLost(Network network) {
                handler.post(() -> updateNetwork());
            }
        };
        try {
            connectivityManager.registerNetworkCallback(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback);
            updateNetwork();
        } catch (SecurityException exception) {
            networkCallback = null;
            // Without network-state permission the creative remains a still frame.
        }
    }

    private void stopObservingNetwork() {
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }
    }

    private void updateNetwork() {
        if (destroyed || videoView == null) return;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        boolean wifi = capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        videoView.setPlaybackAllowed(wifi);
    }
}
