package org.prebid.mobile.daro;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.prebid.mobile.PrebidNativeAd;

public final class DaroPrebidNativeRenderer {
    @Nullable
    public DaroPrebidNativeAd createNativeAd(@NonNull String adm, @Nullable String auctionPrice) {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(adm, auctionPrice);
        if (nativeAd == null) {
            return null;
        }
        return new DaroPrebidNativeAd(nativeAd);
    }
}
