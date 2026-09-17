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

package org.prebid.mobile.rendering.video;

import android.content.Context;
import android.net.Uri;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.common.util.Util;
import org.prebid.mobile.LogUtil;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.listeners.VideoCreativeViewListener;
import org.prebid.mobile.rendering.video.vast.VASTErrorCodes;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class ExoPlayerView extends PlayerView implements VideoPlayerView {

    private static final String TAG = "ExoPlayerView";
    public static final float DEFAULT_INITIAL_VIDEO_VOLUME = 1.0f;

    @NonNull private final VideoCreativeViewListener videoCreativeViewListener;
    private AdViewProgressUpdateTask adViewProgressUpdateTask;
    private AdUnitConfiguration config;
    private ExoPlayer player;

    private Uri videoUri;

    private long vastVideoDuration = -1;
    private boolean preparingStillFrame;
    private boolean stillFrameMode;
    private boolean playbackRequested;
    private Runnable stillFrameReady;

    public ExoPlayerView(
            Context context,
            @NonNull VideoCreativeViewListener videoCreativeViewListener
    ) {
        super(context);
        this.videoCreativeViewListener = videoCreativeViewListener;
    }

    private final Player.Listener eventListener = new Player.Listener() {

        @Override
        public void onPlayerError(PlaybackException error) {
            stillFrameReady = null;
            videoCreativeViewListener.onFailure(new AdException(
                    AdException.INTERNAL_ERROR,
                    VASTErrorCodes.MEDIA_DISPLAY_ERROR.toString()
            ));
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (player == null) {
                LogUtil.debug(TAG, "onPlayerStateChanged(): Skipping state handling. Player is null");
                return;
            }
            switch (playbackState) {
                case Player.STATE_READY:
                    if (preparingStillFrame || (stillFrameMode && !playbackRequested)) {
                        player.setPlayWhenReady(false);
                        return;
                    }
                    player.setPlayWhenReady(true);
                    initUpdateTask();
                    break;
                case Player.STATE_ENDED:
                    videoCreativeViewListener.onDisplayCompleted();
                    break;
            }
        }

        @Override
        public void onRenderedFirstFrame() {
            Runnable callback = stillFrameReady;
            stillFrameReady = null;
            if (preparingStillFrame && callback != null) {
                callback.run();
            }
        }
    };

    @Override
    public void mute() {
        setVolume(0);
    }

    @Override
    public boolean isPlaying() {
        return player != null && player.getPlayWhenReady();
    }

    @Override
    public void unMute() {
        setVolume(DEFAULT_INITIAL_VIDEO_VOLUME);
    }

    @Override
    public void start(float initialVolume) {
        LogUtil.debug(TAG, "Called start");
        preparingStillFrame = false;
        playbackRequested = true;
        stillFrameReady = null;
        initLayout();
        initPlayer(initialVolume);
        preparePlayer(true);
        trackInitialStartEvent();
    }

    /** Prepares a visible first frame without starting playback or VAST playback tracking. */
    public void prepareStillFrame(@NonNull Runnable onReady) {
        if (videoUri == null) {
            videoCreativeViewListener.onFailure(new AdException(
                    AdException.INTERNAL_ERROR,
                    VASTErrorCodes.MEDIA_DISPLAY_ERROR.toString()
            ));
            return;
        }
        preparingStillFrame = true;
        stillFrameMode = true;
        stillFrameReady = onReady;
        initLayout();
        initPlayer(0);
        player.setVolume(0);
        player.setPlayWhenReady(false);
        preparePlayer(true);
    }

    @Override
    public void setVastVideoDuration(long duration) {
        vastVideoDuration = duration;
    }

    @Override
    public long getCurrentPosition() {
        if (player == null) {
            return -1;
        }
        return player.getContentPosition();
    }

    @Override
    public void setVideoUri(Uri uri) {
        videoUri = uri;
    }

    @Override
    public int getDuration() {
        return (int) player.getDuration();
    }

    @Override
    public float getVolume() {
        return player.getVolume();
    }

    void setAdUnitConfiguration(AdUnitConfiguration config) {
        this.config = config;
    }

    @Override
    public void resume() {
        LogUtil.debug(TAG, "Called resume");
        if (preparingStillFrame) {
            return;
        }
        if (stillFrameMode && player != null) {
            playbackRequested = true;
            player.setVolume(0);
            player.setPlayWhenReady(true);
            videoCreativeViewListener.onEvent(VideoAdEvent.Event.AD_RESUME);
            return;
        }
        preparePlayer(false);
        videoCreativeViewListener.onEvent(VideoAdEvent.Event.AD_RESUME);
    }

    @Override
    public void pause() {
        LogUtil.debug(TAG, "Called pause");
        if (preparingStillFrame) {
            return;
        }
        if (player != null) {
            if (stillFrameMode) {
                playbackRequested = false;
                player.setPlayWhenReady(false);
            } else {
                player.stop();
            }
            videoCreativeViewListener.onEvent(VideoAdEvent.Event.AD_PAUSE);
        }
    }

    @Override
    public void forceStop() {
        destroy();
        videoCreativeViewListener.onDisplayCompleted();
    }

    @Override
    public void destroy() {
        LogUtil.debug(TAG, "Called destroy");
        stillFrameReady = null;
        killUpdateTask();
        if (player != null) {
            player.stop();
            player.removeListener(eventListener);
            setPlayer(null);
            player.release();
            player = null;
        }
    }

    @VisibleForTesting
    void setVolume(float volume) {
        if (player != null && volume >= 0.0f) {
            videoCreativeViewListener.onVolumeChanged(volume);
            player.setVolume(volume);
        }
    }

    @Override
    public void stop() {
        stillFrameReady = null;
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
    }

    private void initLayout() {
        RelativeLayout.LayoutParams playerLayoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        playerLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        setLayoutParams(playerLayoutParams);
    }

    private void initPlayer(float initialVolume) {
        if (player != null) {
            LogUtil.debug(TAG, "Skipping initPlayer(): Player is already initialized.");
            return;
        }
        player = new ExoPlayer.Builder(getContext()).build();
        player.addListener(eventListener);
        setPlayer(this.player);
        setUseController(false);
        player.setVolume(initialVolume);
    }

    private void initUpdateTask() {
        if (adViewProgressUpdateTask != null) {
            LogUtil.debug(TAG, "initUpdateTask: AdViewProgressUpdateTask is already initialized. Skipping.");
            return;
        }

        try {
            adViewProgressUpdateTask = new AdViewProgressUpdateTask(
                    videoCreativeViewListener,
                    (int) player.getDuration(),
                    config
            );
            adViewProgressUpdateTask.setVastTagDuration(vastVideoDuration);
            adViewProgressUpdateTask.execute();
        }
        catch (AdException e) {
            e.printStackTrace();
        }
    }

    @VisibleForTesting
    void preparePlayer(boolean resetPosition) {
        ProgressiveMediaSource extractorMediaSource = buildMediaSource(videoUri);
        if (extractorMediaSource == null || player == null) {
            LogUtil.debug(TAG, "preparePlayer(): ExtractorMediaSource or ExoPlayer is null. Skipping prepare.");
            return;
        }
        player.setMediaSource(extractorMediaSource, resetPosition);
        player.prepare();
    }

    private ProgressiveMediaSource buildMediaSource(Uri uri) {
        if (uri == null) {
            return null;
        }
        MediaItem mediaItem = new MediaItem.Builder().setUri(uri).build();
        return new ProgressiveMediaSource.Factory(
                new DefaultDataSource.Factory(getContext(), new DefaultHttpDataSource.Factory()
                        .setUserAgent(Util.getUserAgent(getContext(), "PrebidRenderingSDK"))))
                .createMediaSource(mediaItem);
    }

    private void killUpdateTask() {
        if (adViewProgressUpdateTask != null) {
            adViewProgressUpdateTask.cancel(true);
            adViewProgressUpdateTask = null;
        }
    }

    private void trackInitialStartEvent() {
        if (videoUri != null && player != null && player.getCurrentPosition() == 0) {
            videoCreativeViewListener.onEvent(VideoAdEvent.Event.AD_CREATIVEVIEW);
            videoCreativeViewListener.onEvent(VideoAdEvent.Event.AD_START);
        }
    }
}
