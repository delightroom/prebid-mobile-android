package org.prebid.mobile.daro;

import android.content.Context;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.TargetingParams;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.rendering.sdk.SdkInitializer;

public final class DaroPrebidRendering {
    private DaroPrebidRendering() {}

    public static void initialize(
        @NonNull Context context,
        @NonNull String omidPartnerName,
        @NonNull String omidPartnerVersion
    ) {
        TargetingParams.setOmidPartnerName(omidPartnerName);
        TargetingParams.setOmidPartnerVersion(omidPartnerVersion);
        PrebidMobile.setDisableStatusCheck(true);
        SdkInitializer.init(context.getApplicationContext(), null);
    }

    @NonNull
    public static DaroPrebidBannerRenderer createBannerRenderer(
        @NonNull Context context,
        @NonNull ViewGroup container,
        @NonNull DaroPrebidRenderListener listener
    ) throws AdException {
        return new DaroPrebidBannerRenderer(context, container, listener);
    }

    @NonNull
    public static DaroPrebidRenderHandle renderBanner(
        @NonNull Context context,
        @NonNull ViewGroup container,
        @NonNull String html,
        int width,
        int height,
        @NonNull DaroPrebidRenderListener listener
    ) throws AdException {
        DaroPrebidBannerRenderer renderer = createBannerRenderer(context, container, listener);
        renderer.renderHtml(html, width, height);
        return renderer;
    }

    @NonNull
    public static DaroPrebidNativeRenderer createNativeRenderer() {
        return new DaroPrebidNativeRenderer();
    }

    @Nullable
    public static DaroPrebidNativeAd createNativeAd(
        @NonNull String adm,
        @Nullable String auctionPrice
    ) {
        return createNativeRenderer().createNativeAd(adm, auctionPrice);
    }
}
