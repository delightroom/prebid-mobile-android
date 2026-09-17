package org.prebid.mobile.daro;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class DaroPrebidNativeMedia {
    private final String data;
    @Nullable private String clickThroughUrl;

    void setClickThroughUrl(@Nullable String url) { clickThroughUrl = url; }

    public interface Listener {
        default void loaded() {}
        default void failed() {}
        void click();
    }

    public DaroPrebidNativeMedia(@NonNull String data) {
        this.data = data;
    }

    @NonNull
    public String getData() {
        return data;
    }

    @NonNull
    public View createView(@NonNull Context context) {
        return createView(context, null);
    }

    @NonNull
    public View createView(
        @NonNull Context context,
        @Nullable Listener listener
    ) {
        return new DaroPrebidNativeMediaView(context, data, listener, clickThroughUrl);
    }

    public void destroyView(@NonNull View view) {
        if (view instanceof DaroPrebidNativeMediaView) {
            ((DaroPrebidNativeMediaView) view).destroy();
        }
    }
}
