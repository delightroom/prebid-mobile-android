package org.prebid.mobile.daro;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.prebid.mobile.AdSize;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.api.rendering.VideoView;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.models.AdDetails;
import org.prebid.mobile.rendering.views.video.VideoViewListener;

final class DaroPrebidNativeMediaView extends FrameLayout {
    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 250;

    private final String vastXml;
    @Nullable
    private DaroPrebidNativeMedia.Listener listener;
    private VideoView videoView;
    private boolean loadStarted;
    private boolean destroyed;

    DaroPrebidNativeMediaView(
        @NonNull Context context,
        @NonNull String vastXml,
        @Nullable DaroPrebidNativeMedia.Listener listener
    ) {
        super(context);
        this.vastXml = vastXml;
        this.listener = listener;
        setVisibility(View.GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (loadStarted && videoView != null) {
            videoView.resume();
            return;
        }
        loadIfNeeded();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (videoView != null) {
            videoView.pause();
        }
        super.onDetachedFromWindow();
    }

    void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        listener = null;
        if (videoView != null) {
            videoView.destroy();
            videoView = null;
        }
        removeAllViews();
    }

    private void loadIfNeeded() {
        if (destroyed || loadStarted || vastXml.trim().isEmpty()) {
            return;
        }

        loadStarted = true;

        try {
            AdUnitConfiguration configuration = new AdUnitConfiguration();
            configuration.setAdFormat(AdFormat.VAST);
            configuration.addSize(resolveSize());

            VideoView view = new VideoView(getContext(), configuration);
            view.setVideoViewListener(new VideoViewListener() {
                @Override
                public void onLoaded(
                    @NonNull VideoView videoAdView,
                    AdDetails adDetails
                ) {
                    if (destroyed) {
                        return;
                    }
                    setVisibility(View.VISIBLE);
                    if (listener != null) {
                        listener.loaded();
                    }
                }

                @Override
                public void onLoadFailed(
                    @NonNull VideoView videoAdView,
                    AdException error
                ) {
                    if (destroyed) {
                        return;
                    }
                    setVisibility(View.GONE);
                    if (listener != null) {
                        listener.failed();
                    }
                }

                @Override
                public void onClickThroughOpened(
                    @NonNull VideoView videoAdView
                ) {
                    if (!destroyed && listener != null) {
                        listener.click();
                    }
                }
            });
            view.setAutoPlay(true);
            view.setVideoPlayerClick(true);
            view.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            ));

            removeAllViews();
            addView(view);
            videoView = view;
            view.loadAd(configuration, vastXml);
        } catch (AdException exception) {
            setVisibility(View.GONE);
            if (!destroyed && listener != null) {
                listener.failed();
            }
        }
    }

    private AdSize resolveSize() {
        int width = getWidth() > 0 ? getWidth() : DEFAULT_WIDTH;
        int height = getHeight() > 0 ? getHeight() : DEFAULT_HEIGHT;
        return new AdSize(width, height);
    }
}
