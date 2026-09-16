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
import android.media.MediaMetadataRetriever;

import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.rendering.loading.FileDownloadListener;
import org.prebid.mobile.rendering.networking.BaseNetworkTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/** A consumer of the process-wide file cache; cancellation never cancels another ad's consumer. */
public class VideoDownloadTask extends BaseNetworkTask {
    private static VideoFileCache cache;
    private final VideoFileCache fileCache;
    private final FileDownloadListener listener;
    private VideoFileCache.Lease lease;
    private boolean released;

    public VideoDownloadTask(Context context, FileDownloadListener listener) {
        this(cache(context.getApplicationContext()), listener);
    }

    VideoDownloadTask(VideoFileCache fileCache, FileDownloadListener listener) {
        super(listener);
        this.listener = listener;
        this.fileCache = fileCache;
    }

    private static synchronized VideoFileCache cache(Context context) {
        if (cache == null) {
            cache =
                    new VideoFileCache(
                            new File(context.getCacheDir(), "prebid-video-v1"),
                            new File(context.getNoBackupFilesDir(), "prebid-video-temporary"),
                            System::currentTimeMillis,
                            file -> {
                                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                try {
                                    retriever.setDataSource(file.getAbsolutePath());
                                    if (!"yes"
                                            .equals(
                                                    retriever.extractMetadata(
                                                            MediaMetadataRetriever
                                                                    .METADATA_KEY_HAS_VIDEO))) {
                                        throw new IOException("Downloaded file has no video");
                                    }
                                } catch (RuntimeException e) {
                                    throw new IOException("Invalid downloaded video", e);
                                } finally {
                                    retriever.release();
                                }
                            });
        }
        return cache;
    }

    @Override
    public GetUrlResult sendRequest(GetUrlParams params) {
        GetUrlResult result = new GetUrlResult();
        try {
            VideoFileCache.Lease acquired =
                    fileCache.acquire(
                            params.url,
                            params.userAgent,
                            new HashMap<>(PrebidMobile.getCustomHeaders()),
                            PrebidMobile.getTimeoutMillis(),
                            this::isCancelled);
            synchronized (this) {
                if (released || isCancelled()) acquired.close();
                else lease = acquired;
            }
        } catch (IOException e) {
            result.setException(e);
        }
        return result;
    }

    @Override
    protected void onPostExecute(GetUrlResult result) {
        String path;
        synchronized (this) {
            if (released || isCancelled()) return;
            path = lease == null ? null : lease.file.getAbsolutePath();
        }
        if (result == null || result.getException() != null || path == null) {
            listener.onFileDownloadError(
                    result == null || result.getException() == null
                            ? "Video unavailable"
                            : result.getException().getMessage());
        } else listener.onFileDownloaded(path);
    }

    @Override
    protected void onCancelled(GetUrlResult result) {
        release();
    }

    @Override
    public void destroy() {
        cancel(true);
        release();
        super.destroy();
    }

    /** Called after the player releases the file, including cancellation before delivery. */
    synchronized void release() {
        released = true;
        if (lease != null) {
            lease.close();
            lease = null;
        }
    }
}
