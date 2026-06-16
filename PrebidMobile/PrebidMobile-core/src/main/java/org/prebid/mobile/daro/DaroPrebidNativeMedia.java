package org.prebid.mobile.daro;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class DaroPrebidNativeMedia {
    private final String data;

    public interface Listener {
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
        return new DaroPrebidNativeMediaView(context, data, listener);
    }

    public void destroyView(@NonNull View view) {
        if (view instanceof DaroPrebidNativeMediaView) {
            ((DaroPrebidNativeMediaView) view).destroy();
        }
    }
}
