package org.prebid.mobile.daro;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class DaroPrebidOmidVerificationResource {
    private final String url;
    private final String vendorKey;
    private final String verificationParameters;

    public DaroPrebidOmidVerificationResource(
        @NonNull String url,
        @Nullable String vendorKey,
        @Nullable String verificationParameters
    ) {
        this.url = url;
        this.vendorKey = vendorKey;
        this.verificationParameters = verificationParameters;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @Nullable
    public String getVendorKey() {
        return vendorKey;
    }

    @Nullable
    public String getVerificationParameters() {
        return verificationParameters;
    }
}
