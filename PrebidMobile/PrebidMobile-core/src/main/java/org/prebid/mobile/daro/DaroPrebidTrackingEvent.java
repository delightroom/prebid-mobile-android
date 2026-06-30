package org.prebid.mobile.daro;

import androidx.annotation.NonNull;

import org.prebid.mobile.rendering.models.TrackingEvent;
import org.prebid.mobile.rendering.parser.AdResponseParserVast;
import org.prebid.mobile.rendering.video.VideoAdEvent;

import java.util.Locale;

public final class DaroPrebidTrackingEvent {
    public static final String SOURCE_VAST_VIDEO = "vastVideo";
    public static final String SOURCE_DISPLAY = "display";
    public static final String SOURCE_COMPANION = "companion";

    private final String name;
    private final String source;
    private final int urlCount;

    public DaroPrebidTrackingEvent(
        @NonNull String name,
        @NonNull String source,
        int urlCount
    ) {
        this.name = name;
        this.source = source;
        this.urlCount = urlCount;
    }

    @NonNull
    public static DaroPrebidTrackingEvent fromVideoEvent(
        @NonNull VideoAdEvent.Event event,
        int urlCount
    ) {
        return new DaroPrebidTrackingEvent(
            videoEventName(event),
            SOURCE_VAST_VIDEO,
            urlCount
        );
    }

    @NonNull
    public static DaroPrebidTrackingEvent fromDisplayEvent(
        @NonNull TrackingEvent.Events event,
        @NonNull String source,
        int urlCount
    ) {
        return new DaroPrebidTrackingEvent(
            displayEventName(event),
            source,
            urlCount
        );
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getSource() {
        return source;
    }

    public int getUrlCount() {
        return urlCount;
    }

    @NonNull
    private static String videoEventName(@NonNull VideoAdEvent.Event event) {
        int index = event.ordinal();
        String[] eventMapping = AdResponseParserVast.Tracking.EVENT_MAPPING;
        if (index >= 0 && index < eventMapping.length) {
            return eventMapping[index];
        }
        return event.name().toLowerCase(Locale.US);
    }

    @NonNull
    private static String displayEventName(@NonNull TrackingEvent.Events event) {
        switch (event) {
            case IMPRESSION:
                return "impression";
            case CLICK:
                return "click";
            case LOADED:
                return "loaded";
            case DEFAULT:
            default:
                return event.name().toLowerCase(Locale.US);
        }
    }
}
