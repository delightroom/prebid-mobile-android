package org.prebid.mobile.daro;

import androidx.annotation.NonNull;
import org.prebid.mobile.api.exceptions.AdException;

public interface DaroPrebidRenderListener {
    default void renderStarted() {}

    default void renderSuccess() {}

    default void renderFailed(@NonNull AdException error) {}

    default void impression() {}

    default void click() {}

    default void videoCompleted() {}

    default void expired() {}

    default void closed() {}

    default void destroyed() {}
}
