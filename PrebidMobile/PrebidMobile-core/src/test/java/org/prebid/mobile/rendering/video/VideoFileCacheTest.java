package org.prebid.mobile.rendering.video;

import static org.junit.Assert.*;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class VideoFileCacheTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private MockWebServer server;
    private ExecutorService downloads;
    private ExecutorService callers;
    private AtomicLong time;
    private File directory;
    private File fallback;
    private VideoFileCache cache;
    private final String body = "complete-video-fixture";

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        downloads = Executors.newFixedThreadPool(3);
        callers = Executors.newFixedThreadPool(3);
        time = new AtomicLong(1800000000000L);
        directory = folder.newFolder("cache");
        fallback = folder.newFolder("fallback");
        cache = newCache(directory);
    }

    @After
    public void tearDown() throws Exception {
        callers.shutdownNow();
        downloads.shutdownNow();
        callers.awaitTermination(3, TimeUnit.SECONDS);
        downloads.awaitTermination(3, TimeUnit.SECONDS);
        server.shutdown();
    }

    private VideoFileCache newCache(File path) {
        return new VideoFileCache(
                path,
                fallback,
                time::get,
                file -> {
                    if (!new String(Files.readAllBytes(file.toPath()), "UTF-8").equals(body))
                        throw new IOException("invalid video");
                },
                downloads);
    }

    private MockResponse response() {
        return new MockResponse().setBody(body);
    }

    private VideoFileCache.Lease acquire(String path) throws IOException {
        return cache.acquire(
                server.url(path).toString(), "fixture", Collections.emptyMap(), 2000, () -> false);
    }

    private Future<VideoFileCache.Lease> pending(AtomicBoolean cancel) {
        return callers.submit(
                () ->
                        cache.acquire(
                                server.url("/video").toString(),
                                "fixture",
                                Collections.emptyMap(),
                                2000,
                                cancel::get));
    }

    @Test
    public void lastCancellationAfterFileHandoffDeletesUnleasedTemporaryFile() throws Exception {
        downloads.shutdownNow();
        downloads = Executors.newSingleThreadExecutor();
        CountDownLatch disconnectEntered = new CountDownLatch(1);
        CountDownLatch finishDisconnect = new CountDownLatch(1);
        AtomicInteger disconnectCalls = new AtomicInteger();
        java.net.HttpURLConnection connection = org.mockito.Mockito.mock(java.net.HttpURLConnection.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            if (disconnectCalls.incrementAndGet() == 1) {
                disconnectEntered.countDown();
                assertTrue(finishDisconnect.await(3, TimeUnit.SECONDS));
            }
            return null;
        }).when(connection).disconnect();
        cache = new VideoFileCache(directory, fallback, time::get, file -> {
            // Hold the completion boundary after the file is handed to the job but
            // before waiters are notified, without changing the network payload.
            try {
                java.lang.reflect.Field jobsField = VideoFileCache.class.getDeclaredField("jobs");
                jobsField.setAccessible(true);
                synchronized (cache) {
                    Object job = ((Map<?, ?>) jobsField.get(cache)).values().iterator().next();
                    java.lang.reflect.Field connectionField = job.getClass().getDeclaredField("connection");
                    connectionField.setAccessible(true);
                    connectionField.set(job, connection);
                }
            } catch (ReflectiveOperationException e) {
                throw new IOException(e);
            }
        }, downloads);
        server.enqueue(response().setHeader("Cache-Control", "no-store"));
        AtomicBoolean cancel = new AtomicBoolean();
        Future<VideoFileCache.Lease> consumer = pending(cancel);
        try {
            assertTrue(disconnectEntered.await(3, TimeUnit.SECONDS));
            assertEquals(1, fallback.listFiles().length);
            cancel.set(true);
            try {
                consumer.get(3, TimeUnit.SECONDS);
                fail("Cancelled consumer must not receive a lease");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof InterruptedIOException);
            }
        } finally {
            finishDisconnect.countDown();
        }
        downloads.submit(() -> {}).get(3, TimeUnit.SECONDS);
        assertEquals(0, fallback.listFiles().length);
    }

    @Test
    public void diskHitSurvivesNewInstanceAndKeepsFullUrlIdentity() throws Exception {
        server.enqueue(response());
        server.enqueue(response());
        server.enqueue(response());
        try (VideoFileCache.Lease first = acquire("/a/video?signature=1")) {
            assertFalse(first.hit);
        }
        cache = newCache(directory);
        try (VideoFileCache.Lease hit = acquire("/a/video?signature=1")) {
            assertTrue(hit.hit);
        }
        try (VideoFileCache.Lease other = acquire("/b/video?signature=1")) {
            assertFalse(other.hit);
        }
        try (VideoFileCache.Lease other = acquire("/a/video?signature=2")) {
            assertFalse(other.hit);
        }
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void localTtlSlidesFromLastAccess() throws Exception {
        server.enqueue(response());
        server.enqueue(response());
        acquire("/video").close();
        time.addAndGet(VideoFileCache.LOCAL_TTL - 1);
        try (VideoFileCache.Lease hit = acquire("/video")) {
            assertTrue(hit.hit);
        }
        time.addAndGet(VideoFileCache.LOCAL_TTL - 1);
        try (VideoFileCache.Lease hit = acquire("/video")) {
            assertTrue(hit.hit);
        }
        time.addAndGet(VideoFileCache.LOCAL_TTL);
        try (VideoFileCache.Lease miss = acquire("/video")) {
            assertFalse(miss.hit);
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void serverExpiryNeverSlidesWithLocalAccess() throws Exception {
        server.enqueue(response().setHeader("Cache-Control", "max-age=10"));
        server.enqueue(response());
        acquire("/video").close();
        time.addAndGet(9000);
        acquire("/video").close();
        time.addAndGet(1000);
        try (VideoFileCache.Lease miss = acquire("/video")) {
            assertFalse(miss.hit);
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void noStoreAndNoCacheAreTemporaryAndNeverReused() throws Exception {
        for (String policy :
                new String[] {"no-store", "no-cache", "max-age=0", "no-cache=\"Set-Cookie\""}) {
            server.enqueue(response().setHeader("Cache-Control", policy));
            server.enqueue(response().setHeader("Cache-Control", policy));
            File file;
            try (VideoFileCache.Lease first = acquire("/" + policy.hashCode())) {
                file = first.file;
                assertTrue(file.exists());
            }
            assertFalse(file.exists());
            try (VideoFileCache.Lease second = acquire("/" + policy.hashCode())) {
                assertFalse(second.hit);
            }
        }
        assertEquals(8, server.getRequestCount());
    }

    @Test
    public void corruptionIsMissEvenWhenLengthIsUnchanged() throws Exception {
        server.enqueue(response());
        server.enqueue(response());
        File file;
        try (VideoFileCache.Lease first = acquire("/video")) {
            file = first.file;
        }
        Files.write(file.toPath(), new byte[body.length()]);
        try (VideoFileCache.Lease second = acquire("/video")) {
            assertFalse(second.hit);
            assertNotEquals(file, second.file);
        }
    }

    @Test
    public void cleanupAndReplacementDoNotDeleteActivePlayback() throws Exception {
        server.enqueue(response());
        server.enqueue(response());
        VideoFileCache.Lease playing = acquire("/video");
        time.addAndGet(VideoFileCache.LOCAL_TTL);
        cache.cleanUp();
        assertTrue(playing.file.exists());
        try (VideoFileCache.Lease replacement = acquire("/video")) {
            assertNotEquals(playing.file, replacement.file);
            assertTrue(playing.file.exists());
        }
        playing.close();
        cache.cleanUp();
        assertFalse(playing.file.exists());
    }

    @Test
    public void concurrentDownloadsShareOneGetAndIndependentLeases() throws Exception {
        server.enqueue(response().setBodyDelay(400, TimeUnit.MILLISECONDS));
        Future<VideoFileCache.Lease> a = pending(new AtomicBoolean());
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS));
        Future<VideoFileCache.Lease> b = pending(new AtomicBoolean());
        VideoFileCache.Lease one = a.get(3, TimeUnit.SECONDS), two = b.get(3, TimeUnit.SECONDS);
        assertEquals(one.file, two.file);
        one.close();
        assertTrue(two.file.exists());
        two.close();
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void cancellingOneConsumerPreservesTheOther() throws Exception {
        server.enqueue(response().setBodyDelay(500, TimeUnit.MILLISECONDS));
        AtomicBoolean cancel = new AtomicBoolean();
        Future<VideoFileCache.Lease> a = pending(cancel);
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS));
        Future<VideoFileCache.Lease> b = pending(new AtomicBoolean());
        // Await the second registered consumer rather than relying on download completion timing.
        awaitConsumers(2);
        cancel.set(true);
        try {
            a.get(3, TimeUnit.SECONDS);
            fail();
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause() instanceof InterruptedIOException);
        }
        b.get(3, TimeUnit.SECONDS).close();
        assertEquals(1, server.getRequestCount());
    }

    private void awaitConsumers(int count) throws Exception {
        java.lang.reflect.Field jobs = VideoFileCache.class.getDeclaredField("jobs");
        jobs.setAccessible(true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            synchronized (cache) {
                Map<?, ?> map = (Map<?, ?>) jobs.get(cache);
                if (!map.isEmpty()) {
                    Object job = map.values().iterator().next();
                    java.lang.reflect.Field consumers =
                            job.getClass().getDeclaredField("consumers");
                    consumers.setAccessible(true);
                    if (consumers.getInt(job) == count) return;
                }
            }
            Thread.sleep(5);
        }
        fail("Consumers not registered");
    }

    @Test
    public void cancellingLastConsumerAllowsFreshRequest() throws Exception {
        server.enqueue(response().setBodyDelay(1, TimeUnit.SECONDS));
        server.enqueue(response());
        AtomicBoolean cancel = new AtomicBoolean();
        Future<VideoFileCache.Lease> a = pending(cancel);
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS));
        cancel.set(true);
        try {
            a.get(3, TimeUnit.SECONDS);
            fail();
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause() instanceof InterruptedIOException);
        }
        try (VideoFileCache.Lease next = acquire("/video")) {
            assertFalse(next.hit);
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void truncatedAndInvalidVideoCannotBecomeHits() throws Exception {
        server.enqueue(response().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        server.enqueue(new MockResponse().setBody("not a video"));
        server.enqueue(response());
        for (int i = 0; i < 2; i++) {
            try {
                acquire("/video");
                fail();
            } catch (IOException expected) {
            }
        }
        try (VideoFileCache.Lease next = acquire("/video")) {
            assertFalse(next.hit);
        }
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void failedCacheDirectoryFallsBackToDownloadFile() throws Exception {
        File blocked = folder.newFile("blocked");
        cache = newCache(blocked);
        server.enqueue(response());
        File file;
        try (VideoFileCache.Lease result = acquire("/video")) {
            file = result.file;
            assertEquals(fallback, file.getParentFile());
            assertTrue(file.exists());
        }
        assertFalse(file.exists());
    }

    @Test
    public void absentFileOrMetadataIsMiss() throws Exception {
        server.enqueue(response());
        server.enqueue(response());
        server.enqueue(response());
        try (VideoFileCache.Lease result = acquire("/video")) {
            result.file.delete();
        }
        acquire("/video").close();
        for (File file : directory.listFiles())
            if (file.getName().endsWith(".meta")) Files.write(file.toPath(), new byte[] {1, 2});
        try (VideoFileCache.Lease result = acquire("/video")) {
            assertFalse(result.hit);
        }
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void startupRemovesOnlyAbandonedTemporaryFiles() throws Exception {
        File orphan = new File(directory, "orphan.part");
        assertTrue(orphan.createNewFile());
        cache = newCache(directory);
        assertFalse(orphan.exists());
    }

    @Test
    public void oversizedResponseIsRejectedWithoutCacheEntry() throws Exception {
        server.enqueue(response().setHeader("Content-Length", VideoFileCache.MAX_FILE_SIZE + 1));
        try {
            acquire("/video");
            fail();
        } catch (IOException expected) {
        }
        for (File file : directory.listFiles()) assertFalse(file.getName().endsWith(".meta"));
    }

    @Test
    public void metadataPublicationFailureStillDeliversAndReleasesTheDownload() throws Exception {
        server.enqueue(response());
        String identity = server.url("/video").toString() + "\nfixture";
        byte[] digest =
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(identity.getBytes("UTF-8"));
        StringBuilder key = new StringBuilder();
        for (byte b : digest) key.append(String.format("%02x", b & 255));
        File blocked = new File(directory, key + ".meta");
        assertTrue(blocked.mkdir());
        assertTrue(new File(blocked, "prevent-delete").createNewFile());
        File file;
        try (VideoFileCache.Lease result = acquire("/video")) {
            file = result.file;
            assertTrue(file.exists());
            assertFalse(result.hit);
        }
        assertFalse(file.exists());
    }

    @Test
    public void varyAndCustomRequestHeadersDoNotReuseARepresentation() throws Exception {
        server.enqueue(response().setHeader("Vary", "User-Agent"));
        server.enqueue(response().setHeader("Vary", "User-Agent"));
        acquire("/video").close();
        try (VideoFileCache.Lease result = acquire("/video")) {
            assertFalse(result.hit);
        }
        Map<String, String> headers = Collections.singletonMap("Authorization", "fixture");
        server.enqueue(response());
        server.enqueue(response());
        cache.acquire(server.url("/private").toString(), "fixture", headers, 2000, () -> false)
                .close();
        try (VideoFileCache.Lease result =
                cache.acquire(
                        server.url("/private").toString(), "fixture", headers, 2000, () -> false)) {
            assertFalse(result.hit);
        }
        assertEquals(4, server.getRequestCount());
    }

    private Map<String, List<String>> headers(String... pairs) {
        Map<String, List<String>> h = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            h.put(pairs[i], Collections.singletonList(pairs[i + 1]));
        return h;
    }

    @Test
    public void freshnessAccountsForDateAgeAndRequestDelay() {
        long response = 1445412480000L; // Wed, 21 Oct 2015 07:28:00 GMT
        assertEquals(
                response + 30000,
                VideoFileCache.serverExpiry(
                        headers(
                                "Cache-Control",
                                "max-age=60",
                                "Date",
                                "Wed, 21 Oct 2015 07:27:30 GMT"),
                        response - 2000,
                        response));
        assertEquals(
                response + 18000,
                VideoFileCache.serverExpiry(
                        headers("Cache-Control", "max-age=60", "Age", "40"),
                        response - 2000,
                        response));
    }

    @Test
    public void maxAgeWinsExpiresAndMalformedFreshnessDoesNotPersist() {
        long now = 1445412480000L;
        assertEquals(
                now + 60000,
                VideoFileCache.serverExpiry(
                        headers("Cache-Control", "max-age=60", "Expires", "bad"), now, now));
        for (Map<String, List<String>> bad :
                Arrays.asList(
                        headers("Expires", "bad"),
                        headers("Cache-Control", "max-age=bad"),
                        headers("Cache-Control", "max-age=\"6\"0"),
                        headers("Cache-Control", "max-age=1,max-age=2"),
                        headers("Age", "bad"),
                        headers("Date", "bad"),
                        headers("Vary", "User-Agent"))) {
            assertEquals(0, VideoFileCache.serverExpiry(bad, now, now));
        }
        assertEquals(
                now,
                VideoFileCache.serverExpiry(
                        headers("Expires", "Wed, 21 Oct 2015 07:27:00 GMT"), now, now));
        assertEquals(
                now + 60000,
                VideoFileCache.serverExpiry(
                        headers("Expires", "Wed, 21 Oct 2015 07:29:00 GMT"), now, now));
    }

    @Test
    public void downloadTimeCannotExtendServerDeadline() throws Exception {
        cache =
                new VideoFileCache(
                        directory, fallback, time::get, file -> time.addAndGet(2000), downloads);
        server.enqueue(response().setHeader("Cache-Control", "max-age=1"));
        server.enqueue(response());
        acquire("/video").close();
        try (VideoFileCache.Lease next = acquire("/video")) {
            assertFalse(next.hit);
        }
        assertEquals(2, server.getRequestCount());
    }
}
