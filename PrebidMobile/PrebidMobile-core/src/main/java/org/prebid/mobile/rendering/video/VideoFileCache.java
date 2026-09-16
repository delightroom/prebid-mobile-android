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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Stores only complete video files. Ad responses, players and tracking are never shared. */
final class VideoFileCache {
    static final long LOCAL_TTL = 24 * 60 * 60 * 1000L;
    static final long MAX_FILE_SIZE = 25 * 1024 * 1024L;

    interface Clock {
        long now();
    }

    interface Cancelled {
        boolean get();
    }

    interface Validator {
        void validate(File file) throws IOException;
    }

    private final File directory;
    private final File fallbackDirectory;
    private final Clock clock;
    private final Validator validator;
    private final Executor executor;
    private final Map<String, Job> jobs = new HashMap<>();
    private final Map<File, Integer> readers = new HashMap<>();

    VideoFileCache(File directory, File fallbackDirectory, Clock clock, Validator validator) {
        this(directory, fallbackDirectory, clock, validator, Executors.newFixedThreadPool(3));
    }

    VideoFileCache(
            File directory,
            File fallbackDirectory,
            Clock clock,
            Validator validator,
            Executor executor) {
        this.directory = directory;
        this.fallbackDirectory = fallbackDirectory;
        this.clock = clock;
        this.validator = validator;
        this.executor = executor;
        // Only this cache owns these directories. No jobs or leases exist at initialization.
        deleteAbandonedParts(directory);
        deleteAbandonedParts(fallbackDirectory);
    }

    final class Lease implements AutoCloseable {
        final File file;
        final boolean hit;
        private final boolean persistent;
        private boolean closed;

        Lease(File file, boolean hit, boolean persistent) {
            this.file = file;
            this.hit = hit;
            this.persistent = persistent;
            readers.put(file, (readers.containsKey(file) ? readers.get(file) : 0) + 1);
        }

        @Override
        public void close() {
            synchronized (VideoFileCache.this) {
                if (closed) return;
                closed = true;
                int count = readers.get(file) - 1;
                if (count == 0) {
                    readers.remove(file);
                    if (!persistent) file.delete();
                } else readers.put(file, count);
            }
        }
    }

    private static final class Job {
        int consumers;
        boolean done;
        volatile boolean cancelled;
        volatile HttpURLConnection connection;
        File file;
        boolean persistent;
        IOException error;
    }

    Lease acquire(
            String url,
            String userAgent,
            Map<String, String> headers,
            int timeout,
            Cancelled cancelled)
            throws IOException {
        // Custom request headers can vary the representation without a Vary response.
        boolean reusable = headers.isEmpty();
        String key = hash((url + "\n" + userAgent).getBytes(StandardCharsets.UTF_8));
        String jobKey = reusable ? key : UUID.randomUUID().toString();
        Job job;
        synchronized (this) {
            if (cancelled.get()) throw new InterruptedIOException("Video request cancelled");
            cleanUp();
            if (reusable) {
                File cached = read(key);
                if (cached != null) return new Lease(cached, true, true);
            }
            job = jobs.get(jobKey);
            while (job != null && job.done) {
                // Let existing consumers take their leases. A completed no-store/error
                // response must not be reused by a new request.
                if (cancelled.get()) throw new InterruptedIOException("Video request cancelled");
                try {
                    wait(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Video request interrupted");
                }
                job = jobs.get(jobKey);
            }
            if (job == null) {
                job = new Job();
                jobs.put(jobKey, job);
                Job work = job;
                executor.execute(
                        () -> download(work, key, url, userAgent, headers, timeout, reusable));
            }
            job.consumers++;
        }
        try {
            synchronized (this) {
                while (!job.done) {
                    if (cancelled.get())
                        throw new InterruptedIOException("Video request cancelled");
                    try {
                        wait(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("Video request interrupted");
                    }
                }
                if (cancelled.get()) throw new InterruptedIOException("Video request cancelled");
                if (job.error != null) throw job.error;
                return new Lease(job.file, false, job.persistent);
            }
        } finally {
            synchronized (this) {
                if (--job.consumers == 0) {
                    jobs.remove(jobKey);
                    notifyAll();
                    if (!job.done) {
                        job.cancelled = true;
                        HttpURLConnection connection = job.connection;
                        if (connection != null) connection.disconnect();
                    } else if (!job.persistent
                            && !readers.containsKey(job.file)
                            && job.file != null) {
                        job.file.delete();
                    }
                }
            }
        }
    }

    private void download(
            Job job,
            String key,
            String address,
            String userAgent,
            Map<String, String> headers,
            int timeout,
            boolean reusable) {
        File temporary = null;
        try {
            temporary = temporaryFile();
            URL url = new URL(address);
            long expires = Long.MAX_VALUE;
            HttpURLConnection connection = null;
            long requestTime = 0;
            long responseTime = 0;
            for (int redirects = 0; ; redirects++) {
                if (job.cancelled) throw new InterruptedIOException("Video request cancelled");
                if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) {
                    throw new IOException("Unsupported video URL scheme");
                }
                connection = (HttpURLConnection) url.openConnection();
                job.connection = connection;
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (redirects == 0) {
                    for (Map.Entry<String, String> h : headers.entrySet())
                        connection.setRequestProperty(h.getKey(), h.getValue());
                }
                requestTime = clock.now();
                int status = connection.getResponseCode();
                responseTime = clock.now();
                if (status == 200) break;
                if ((status == 301
                                || status == 302
                                || status == 303
                                || status == 307
                                || status == 308)
                        && redirects < 5
                        && connection.getHeaderField("Location") != null) {
                    url = new URL(url, connection.getHeaderField("Location"));
                    // Do not retain a representation through an unvalidated redirect mapping.
                    reusable = false;
                    connection.disconnect();
                } else throw new IOException("Video HTTP status " + status);
            }
            expires = serverExpiry(connection.getHeaderFields(), requestTime, responseTime);
            long expected = connection.getContentLength();
            if (expected <= 0 || expected > MAX_FILE_SIZE)
                throw new IOException("Invalid video content length");
            long received = 0;
            try (InputStream in = connection.getInputStream();
                    OutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (job.cancelled) throw new InterruptedIOException("Video request cancelled");
                    received += count;
                    if (received > expected || received > MAX_FILE_SIZE)
                        throw new IOException("Video exceeds declared length");
                    out.write(buffer, 0, count);
                }
            }
            if (received != expected) throw new IOException("Truncated video");
            validator.validate(temporary);
            synchronized (this) {
                if (job.cancelled) throw new InterruptedIOException("Video request cancelled");
                if (reusable && expires > clock.now()) {
                    File published = publish(job, key, temporary, expires);
                    if (published != null) {
                        temporary = published;
                    }
                }
                job.file = temporary;
                temporary = null;
            }
        } catch (IOException | RuntimeException e) {
            job.error = new IOException("Video download failed", e);
        } finally {
            if (job.connection != null) job.connection.disconnect();
            if (temporary != null) temporary.delete();
            synchronized (this) {
                // The last consumer can cancel after the file handoff but before
                // completion is published. No lease will own that temporary file.
                if (job.consumers == 0 && !job.persistent && job.file != null
                        && !readers.containsKey(job.file)) {
                    job.file.delete();
                }
                job.done = true;
                notifyAll();
            }
        }
    }

    private File temporaryFile() throws IOException {
        // Download independently of cache storage, so cache write/publication errors cannot
        // discard a complete video needed by the current ad.
        if (!fallbackDirectory.isDirectory() && !fallbackDirectory.mkdirs()) {
            throw new IOException("Video temporary storage unavailable");
        }
        return File.createTempFile("video-", ".part", fallbackDirectory);
    }

    private File publish(Job job, String key, File temporary, long expires) {
        File video = new File(directory, UUID.randomUUID() + ".mp4");
        File metadataTemp = null;
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) return null;
            String digest = digest(temporary);
            metadataTemp = File.createTempFile("metadata-", ".part", directory);
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(metadataTemp))) {
                out.writeInt(1);
                out.writeUTF(video.getName());
                out.writeLong(expires);
                out.writeLong(temporary.length());
                out.writeUTF(digest);
            }
            if (!temporary.renameTo(video)) return null;
            if (!metadataTemp.renameTo(new File(directory, key + ".meta"))) {
                // Retain the complete download for this consumer even if cache publication fails.
                if (!video.renameTo(temporary)) return video;
                return null;
            }
            new File(directory, key + ".meta").setLastModified(clock.now());
            job.persistent = true;
            return video;
        } catch (IOException e) {
            return null;
        } finally {
            if (metadataTemp != null) metadataTemp.delete();
        }
    }

    private static final class Metadata {
        File file;
        long expires;
        long length;
        String digest;
    }

    private Metadata metadata(File path) throws IOException {
        Metadata value = new Metadata();
        try (DataInputStream in = new DataInputStream(new FileInputStream(path))) {
            if (in.readInt() != 1) throw new IOException("Unknown cache version");
            String name = in.readUTF();
            if (!name.matches("[a-f0-9-]+\\.mp4")) throw new IOException("Invalid cache filename");
            value.file = new File(directory, name);
            value.expires = in.readLong();
            value.length = in.readLong();
            value.digest = in.readUTF();
            if (value.length <= 0 || value.length > MAX_FILE_SIZE)
                throw new IOException("Invalid cached length");
        }
        return value;
    }

    private File read(String key) {
        File path = new File(directory, key + ".meta");
        try {
            Metadata data = metadata(path);
            if (!fresh(path, data)
                    || data.file.length() != data.length
                    || !digest(data.file).equals(data.digest)) {
                path.delete();
                return null;
            }
            if (!path.setLastModified(clock.now())) return null;
            return data.file;
        } catch (IOException | RuntimeException e) {
            path.delete();
            return null;
        }
    }

    private boolean fresh(File path, Metadata data) {
        long now = clock.now();
        return now >= path.lastModified()
                && now - path.lastModified() < LOCAL_TTL
                && now < data.expires;
    }

    synchronized void cleanUp() {
        File[] files = directory.listFiles();
        if (files == null) return;
        Set<File> retained = new HashSet<>(readers.keySet());
        for (Job job : jobs.values()) if (job.file != null) retained.add(job.file);
        for (File file : files) {
            if (!file.getName().endsWith(".meta")) continue;
            try {
                Metadata data = metadata(file);
                if (fresh(file, data)) retained.add(data.file);
                else file.delete();
            } catch (IOException | RuntimeException e) {
                file.delete();
            }
        }
        for (File file : files) {
            if (file.getName().endsWith(".mp4") && !retained.contains(file)) file.delete();
        }
    }

    private static void deleteAbandonedParts(File directory) {
        File[] files = directory.listFiles();
        if (files != null)
            for (File file : files) if (file.getName().endsWith(".part")) file.delete();
    }

    static long serverExpiry(Map<String, List<String>> headers, long request, long response) {
        try {
            String control = header(headers, "Cache-Control");
            String vary = header(headers, "Vary");
            if (vary != null && !vary.trim().isEmpty()) return 0;
            Long maxAge = null;
            if (control != null)
                for (String directive : control.split(",")) {
                    String[] parts = directive.trim().split("=", 2);
                    String name = parts[0].trim().toLowerCase(Locale.US);
                    if (name.equals("no-store") || name.equals("no-cache")) return 0;
                    if (name.equals("max-age")) {
                        if (parts.length != 2 || maxAge != null) return 0;
                        String value = parts[1].trim();
                        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        }
                        maxAge = seconds(value);
                    }
                }
            String dateHeader = header(headers, "Date");
            long date = dateHeader == null ? response : date(dateHeader);
            String ageHeader = header(headers, "Age");
            long age = ageHeader == null ? 0 : seconds(ageHeader.trim());
            long currentAge =
                    Math.max(
                            Math.max(0, response - date),
                            addExact(age, Math.max(0, response - request)));
            long lifetime;
            if (maxAge != null) lifetime = maxAge;
            else if (header(headers, "Expires") != null)
                lifetime = Math.max(0, date(header(headers, "Expires")) - date);
            else
                return Long
                        .MAX_VALUE; // Only the local idle TTL applies when no server expiry exists.
            return addExact(response, Math.max(0, lifetime - currentAge));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String header(Map<String, List<String>> headers, String name) {
        StringBuilder values = new StringBuilder();
        boolean found = false;
        for (Map.Entry<String, List<String>> h : headers.entrySet()) {
            if (name.equalsIgnoreCase(h.getKey()) && h.getValue() != null) {
                for (String value : h.getValue()) {
                    if (found) values.append(",");
                    values.append(value);
                    found = true;
                }
            }
        }
        return found ? values.toString() : null;
    }

    private static long seconds(String value) {
        if (!value.matches("[0-9]+")) throw new IllegalArgumentException("Invalid freshness");
        return milliseconds(Long.parseLong(value));
    }

    private static long addExact(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b)
            throw new IllegalArgumentException("Freshness overflow");
        return a + b;
    }

    private static long milliseconds(long seconds) {
        if (seconds > Long.MAX_VALUE / 1000)
            throw new IllegalArgumentException("Freshness overflow");
        return seconds * 1000;
    }

    private static long date(String value) {
        for (String format :
                new String[] {
                    "EEE, dd MMM yyyy HH:mm:ss zzz",
                    "EEEE, dd-MMM-yy HH:mm:ss zzz",
                    "EEE MMM d HH:mm:ss yyyy"
                }) {
            SimpleDateFormat parser = new SimpleDateFormat(format, Locale.US);
            parser.setLenient(false);
            parser.setTimeZone(TimeZone.getTimeZone("GMT"));
            ParsePosition position = new ParsePosition(0);
            Date parsed = parser.parse(value, position);
            if (parsed != null && position.getIndex() == value.length()) return parsed.getTime();
        }
        throw new IllegalArgumentException("Invalid HTTP date");
    }

    private static String digest(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = sha256();
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
            return hex(digest.digest());
        }
    }

    private static String hash(byte[] bytes) {
        return hex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) text.append(String.format(Locale.US, "%02x", b & 255));
        return text.toString();
    }
}
