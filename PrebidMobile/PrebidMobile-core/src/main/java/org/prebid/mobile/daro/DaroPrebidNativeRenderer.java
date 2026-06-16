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
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(adm, auctionPrice);
        if (nativeAd == null) {
            return null;
        }
        return new DaroPrebidNativeAd(nativeAd, extractMedia(adm, auctionPrice));
    }

    @Nullable
    private DaroPrebidNativeMedia extractMedia(@NonNull String adm, @Nullable String auctionPrice) {
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

        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }

            JSONObject video = asset.optJSONObject("video");
            if (video == null) {
                continue;
            }

            String vastTag = video.optString("vasttag", "").trim();
            if (!vastTag.isEmpty()) {
                return new DaroPrebidNativeMedia(vastTag);
            }
        }

        return null;
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
