package org.prebid.mobile.rendering.video.vast;

import org.prebid.mobile.rendering.networking.tracking.TrackingManager;
import java.util.LinkedHashSet;
import java.util.List;

/** Error URLs belong to one attempted VAST chain, not the enclosing SDK request. */
public final class VastErrorTracker {
    private VastErrorTracker() {}

    public static void fire(List<String> urls, int code) {
        if (urls == null) return;
        for (String url : new LinkedHashSet<>(urls)) {
            if (url == null || url.trim().isEmpty()) continue;
            String resolved = url.replace("[ERRORCODE]", String.valueOf(code))
                    .replace("%5BERRORCODE%5D", String.valueOf(code))
                    .replace("%5bERRORCODE%5d", String.valueOf(code));
            TrackingManager.getInstance().fireEventTrackingURL(resolved);
        }
    }
}
