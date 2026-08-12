package org.prebid.mobile.daro;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.prebid.mobile.PrebidNativeAd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public final class DaroPrebidNativeRenderer {
    @Nullable
    public DaroPrebidNativeAd createNativeAd(@NonNull String adm, @Nullable String auctionPrice) {
        return createNativeAd(adm, auctionPrice, null);
    }

    @Nullable
    public DaroPrebidNativeAd createNativeAd(
            @NonNull String adm,
            @Nullable String auctionPrice,
            @Nullable Integer videoAssetId
    ) {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(adm, auctionPrice);
        if (nativeAd == null) {
            return null;
        }
        return new DaroPrebidNativeAd(nativeAd, extractMedia(adm, auctionPrice, videoAssetId));
    }

    @Nullable
    private DaroPrebidNativeMedia extractMedia(
            @NonNull String adm,
            @Nullable String auctionPrice,
            @Nullable Integer videoAssetId
    ) {
        if (videoAssetId == null) {
            return null;
        }

        String resolvedAdm = replaceAuctionPrice(adm, auctionPrice);
        JSONObject root = parseJsonObject(resolvedAdm);
        if (root == null) {
            return null;
        }

        JSONObject nativeObj = root.optJSONObject("native");
        if (nativeObj == null) {
            nativeObj = root;
        }

        JSONArray assets = nativeObj.optJSONArray("assets");
        if (assets == null) {
            return null;
        }

        JSONObject matchedAsset = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null || !asset.has("id") || asset.optInt("id") != videoAssetId) {
                continue;
            }
            if (matchedAsset != null) {
                return null;
            }
            matchedAsset = asset;
        }

        if (matchedAsset == null) {
            return null;
        }

        JSONObject video = matchedAsset.optJSONObject("video");
        if (video == null) {
            return null;
        }

        String vastTag = video.optString("vasttag", "").trim();
        return vastTag.isEmpty() ? null : new DaroPrebidNativeMedia(vastTag);
    }

    @Nullable
    private JSONObject parseJsonObject(@NonNull String json) {
        try {
            Object parsed = new JSONTokener(json).nextValue();
            if (parsed instanceof String) {
                parsed = new JSONTokener(((String) parsed).trim()).nextValue();
            }
            if (parsed instanceof JSONObject) {
                return (JSONObject) parsed;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String replaceAuctionPrice(@NonNull String value, @Nullable String auctionPrice) {
        if (auctionPrice != null && value.contains("{AUCTION_PRICE}")) {
            return value.replace("{AUCTION_PRICE}", auctionPrice);
        }
        return value;
    }
}
