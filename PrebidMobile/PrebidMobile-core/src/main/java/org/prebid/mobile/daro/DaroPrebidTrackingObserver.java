package org.prebid.mobile.daro;

import androidx.annotation.NonNull;

public interface DaroPrebidTrackingObserver {
    void onTrackingEvent(@NonNull DaroPrebidTrackingEvent event);
}
