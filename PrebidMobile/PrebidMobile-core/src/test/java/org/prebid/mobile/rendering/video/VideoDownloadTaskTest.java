package org.prebid.mobile.rendering.video;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.rendering.loading.FileDownloadListener;
import org.prebid.mobile.rendering.networking.BaseNetworkTask;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public class VideoDownloadTaskTest {
    @Test
    public void completeFileIsHeldUntilDestroyAndLateSuccessIsSuppressed() throws Exception {
        MockWebServer server = new MockWebServer();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        File directory = new File(RuntimeEnvironment.getApplication().getCacheDir(), "task-test");
        VideoFileCache cache =
                new VideoFileCache(
                        directory, directory, System::currentTimeMillis, file -> {}, executor);
        FileDownloadListener listener = mock(FileDownloadListener.class);
        try {
            server.enqueue(
                    new MockResponse().setBody("video").setHeader("Cache-Control", "no-store"));
            BaseNetworkTask.GetUrlParams params = new BaseNetworkTask.GetUrlParams();
            params.url = server.url("/video").toString();
            params.userAgent = "fixture";
            VideoDownloadTask task = new VideoDownloadTask(cache, listener);
            BaseNetworkTask.GetUrlResult result = task.sendRequest(params);
            assertNull(result.getException());
            task.onPostExecute(result);
            org.mockito.ArgumentCaptor<String> path =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            verify(listener).onFileDownloaded(path.capture());
            File file = new File(path.getValue());
            assertTrue(file.isAbsolute());
            assertTrue(file.exists());
            task.destroy();
            assertFalse(file.exists());
            reset(listener);
            task.onPostExecute(result);
            verifyNoInteractions(listener);
        } finally {
            executor.shutdownNow();
            server.shutdown();
        }
    }

    @Test
    public void releasedTaskCannotDeliverLateFailure() {
        FileDownloadListener listener = mock(FileDownloadListener.class);
        VideoDownloadTask task =
                new VideoDownloadTask(RuntimeEnvironment.getApplication(), listener);
        task.release();
        BaseNetworkTask.GetUrlResult result = new BaseNetworkTask.GetUrlResult();
        result.setException(new Exception("late"));
        task.onPostExecute(result);
        verifyNoInteractions(listener);
    }

    @Test
    public void downloadFailureIsDeliveredOnce() {
        FileDownloadListener listener = mock(FileDownloadListener.class);
        VideoDownloadTask task =
                new VideoDownloadTask(RuntimeEnvironment.getApplication(), listener);
        BaseNetworkTask.GetUrlResult result = new BaseNetworkTask.GetUrlResult();
        result.setException(new Exception("failed"));
        task.onPostExecute(result);
        verify(listener).onFileDownloadError("failed");
    }
}
