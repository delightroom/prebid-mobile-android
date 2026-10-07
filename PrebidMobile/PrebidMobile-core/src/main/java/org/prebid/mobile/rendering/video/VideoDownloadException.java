package org.prebid.mobile.rendering.video;

import java.io.IOException;

/** Typed failures from response validation; never inferred from the message. */
final class VideoDownloadException extends IOException {
    final String reason;
    final Integer httpStatus;
    VideoDownloadException(String reason, String message, Integer httpStatus) {
        super(message);
        this.reason = reason;
        this.httpStatus = httpStatus;
    }
    VideoDownloadException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.httpStatus = null;
    }
}
