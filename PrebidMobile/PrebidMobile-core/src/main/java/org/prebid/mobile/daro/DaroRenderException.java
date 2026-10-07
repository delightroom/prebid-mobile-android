package org.prebid.mobile.daro;

import org.prebid.mobile.api.exceptions.AdException;

/** Internal origin metadata; messages are never used to infer a stage. */
public final class DaroRenderException extends AdException {
    private final String reason, stage, errorDomain;
    private final Integer errorCode, httpStatus;
    public DaroRenderException(String reason, String stage, String message, Throwable cause,
            Integer errorCode, Integer httpStatus, String errorDomain) {
        super(AdException.INTERNAL_ERROR, message);
        this.reason = reason; this.stage = stage; this.errorCode = errorCode;
        this.httpStatus = httpStatus; this.errorDomain = errorDomain;
        if (cause != null) initCause(cause);
    }
    public String getReason() { return reason; }
    public String getStage() { return stage; }
    public Integer getErrorCode() { return errorCode; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getErrorDomain() { return errorDomain; }
}
