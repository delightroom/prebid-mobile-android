/*
 *    Copyright 2020-2021 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.prebid.mobile.rendering.bidding.events.EventsNotifier;
import org.prebid.mobile.rendering.sdk.JSLibraryManager;
import org.prebid.mobile.rendering.session.manager.OmAdSessionManager;
import org.prebid.mobile.rendering.utils.helpers.ExternalViewerUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Response native ad object for all assets.
 */
public class PrebidNativeAd {

    private static final String TAG = "PrebidNativeAd";
    private static final int NATIVE_EVENT_OMID = 555;
    private static final int NATIVE_METHOD_JS = 2;
    private static final String EXT_VENDOR_KEY = "vendorKey";
    private static final String EXT_VERIFICATION_PARAMETERS = "verification_parameters";
    private static final String EXT_VERIFICATION_PARAMETERS_CAMEL_CASE = "verificationParameters";

    private boolean impressionIsNotNotified = true;

    private final ArrayList<NativeTitle> titles = new ArrayList<>();
    private final ArrayList<NativeImage> images = new ArrayList<>();
    private final ArrayList<NativeData> dataList = new ArrayList<>();
    private String clickUrl;
    @Nullable
    private ArrayList<String> imp_trackers;
    @Nullable
    private ArrayList<String> click_trackers;
    private VisibilityDetector visibilityDetector;
    private boolean expired;
    private WeakReference<View> registeredView;
    private PrebidNativeAdEventListener listener;
    private ArrayList<ImpressionTracker> impressionTrackers;
    private ArrayList<ClickTracker> clickTrackers;
    private DaroViewabilityListener daroViewabilityListener;
    private String winEvent;
    private String impEvent;
    @Nullable
    private ArrayList<OmAdSessionManager.NativeDisplayVerificationResource> nativeOmidVerificationResources;
    @Nullable
    private OmAdSessionManager nativeOmidSessionManager;
    private boolean nativeOmidImpressionRegistered;
    @Nullable
    private String privacyUrl;


    public static PrebidNativeAd create(String cacheId) {
        String content = CacheManager.get(cacheId);
        if (!TextUtils.isEmpty(content)) {
            try {
                JSONObject details = new JSONObject(content);
                String admStr = details.getString("adm");
                JSONObject adm = new JSONObject(admStr);

                JSONObject nativeObj;
                if (adm.has("native")) {
                    nativeObj = adm.getJSONObject("native");
                } else {
                    nativeObj = adm;
                }

                JSONArray asset = nativeObj.getJSONArray("assets");
                final PrebidNativeAd ad = new PrebidNativeAd();
                CacheManager.registerCacheExpiryListener(cacheId, new CacheExpireListenerImpl(ad));
                for (int i = 0; i < asset.length(); i++) {
                    JSONObject adObject = asset.getJSONObject(i);
                    if (adObject.has("title")) {
                        JSONObject title = adObject.getJSONObject("title");
                        if (title.has("text")) {
                            String titleText = title.getString("text");
                            if (!titleText.isEmpty()) {
                                ad.addTitle(new NativeTitle(titleText));
                            }
                        } else {
                            LogUtil.warning(TAG, "Json title object doesn't have text field");
                        }
                    }
                    if (adObject.has("data")) {
                        JSONObject data = adObject.getJSONObject("data");

                        if (data.has("value")) {
                            int type = 0;
                            if (data.has("type")) {
                                type = data.optInt("type");
                            }
                            String value = data.getString("value");
                            ad.addData(new NativeData(type, value));
                        } else {
                            LogUtil.warning(TAG, "Json data object doesn't have type or value field");
                        }
                    }

                    if (adObject.has("img")) {
                        JSONObject img = adObject.getJSONObject("img");
                        if (img.has("url")) {
                            int type = 0;
                            if (img.has("type")) {
                                type = img.optInt("type");
                            }
                            String url = img.getString("url");
                            ad.addImage(new NativeImage(type, url));
                        } else {
                            LogUtil.warning(TAG, "Json image object doesn't have url or type field");
                        }
                    }
                }

                if (nativeObj.has("link")) {
                    JSONObject link = nativeObj.getJSONObject("link");
                    if (link.has("url")) {
                        String url = link.getString("url");
                        if (url.contains("{AUCTION_PRICE}") && details.has("price")) {
                            url = url.replace("{AUCTION_PRICE}", details.getString("price"));
                        }
                        ad.setClickUrl(url);
                    }

                    if (link.has("clicktrackers")) {
                        JSONArray clicktrackers = link.getJSONArray("clicktrackers");
                        if (clicktrackers.length() > 0) {
                            ad.click_trackers = new ArrayList<>();
                            for (int count = 0; count < clicktrackers.length(); count++) {
                                String clickTrackerUrl = clicktrackers.getString(count);
                                if (clickTrackerUrl.contains("{AUCTION_PRICE}") && details.has("price")) {
                                    clickTrackerUrl = clickTrackerUrl.replace("{AUCTION_PRICE}", details.getString("price"));
                                }
                                ad.click_trackers.add(clickTrackerUrl);
                            }
                        }
                    }
                }

                if (nativeObj.has("eventtrackers")) {
                    JSONArray eventtrackers = nativeObj.getJSONArray("eventtrackers");
                    if (eventtrackers.length() > 0) {
                        for (int count = 0; count < eventtrackers.length(); count++) {
                            JSONObject eventtracker = eventtrackers.getJSONObject(count);
                            String auctionPrice = details.has("price") ? details.getString("price") : null;
                            ad.addEventTracker(eventtracker, auctionPrice);
                        }
                    }
                }

                if (nativeObj.has("privacy")) {
                    String url = nativeObj.getString("privacy");
                    ad.setPrivacyUrl(url);
                }
                parseEvents(details, ad);
                return ad;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static PrebidNativeAd createForExternalOwner(String adm) {
        return createForExternalOwner(adm, null);
    }

    public static PrebidNativeAd createForExternalOwner(String adm, @Nullable String auctionPrice) {
        if (TextUtils.isEmpty(adm)) {
            return null;
        }

        try {
            JSONObject admJson = new JSONObject(adm);
            JSONObject nativeObj;
            if (admJson.has("native")) {
                nativeObj = admJson.getJSONObject("native");
            } else {
                nativeObj = admJson;
            }

            JSONArray asset = nativeObj.getJSONArray("assets");
            final PrebidNativeAd ad = new PrebidNativeAd();
            for (int i = 0; i < asset.length(); i++) {
                JSONObject adObject = asset.getJSONObject(i);
                if (adObject.has("title")) {
                    JSONObject title = adObject.getJSONObject("title");
                    if (title.has("text")) {
                        String titleText = title.getString("text");
                        if (!titleText.isEmpty()) {
                            ad.addTitle(new NativeTitle(titleText));
                        }
                    } else {
                        LogUtil.warning(TAG, "Json title object doesn't have text field");
                    }
                }
                if (adObject.has("data")) {
                    JSONObject data = adObject.getJSONObject("data");

                    if (data.has("value")) {
                        int type = 0;
                        if (data.has("type")) {
                            type = data.optInt("type");
                        }
                        String value = data.getString("value");
                        ad.addData(new NativeData(type, value));
                    } else {
                        LogUtil.warning(TAG, "Json data object doesn't have type or value field");
                    }
                }

                if (adObject.has("img")) {
                    JSONObject img = adObject.getJSONObject("img");
                    if (img.has("url")) {
                        int type = 0;
                        if (img.has("type")) {
                            type = img.optInt("type");
                        }
                        String url = img.getString("url");
                        ad.addImage(new NativeImage(type, url));
                    } else {
                        LogUtil.warning(TAG, "Json image object doesn't have url or type field");
                    }
                }
            }

            if (nativeObj.has("link")) {
                JSONObject link = nativeObj.getJSONObject("link");
                if (link.has("url")) {
                    ad.setClickUrl(replaceAuctionPrice(link.getString("url"), auctionPrice));
                }

                if (link.has("clicktrackers")) {
                    JSONArray clicktrackers = link.getJSONArray("clicktrackers");
                    if (clicktrackers.length() > 0) {
                        ad.click_trackers = new ArrayList<>();
                        for (int count = 0; count < clicktrackers.length(); count++) {
                            ad.click_trackers.add(replaceAuctionPrice(clicktrackers.getString(count), auctionPrice));
                        }
                    }
                }
            }

            if (nativeObj.has("eventtrackers")) {
                JSONArray eventtrackers = nativeObj.getJSONArray("eventtrackers");
                if (eventtrackers.length() > 0) {
                    for (int count = 0; count < eventtrackers.length(); count++) {
                        JSONObject eventtracker = eventtrackers.getJSONObject(count);
                        ad.addEventTracker(eventtracker, auctionPrice);
                    }
                }
            }

            if (nativeObj.has("privacy")) {
                ad.setPrivacyUrl(nativeObj.getString("privacy"));
            }

            return ad;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String replaceAuctionPrice(String url, @Nullable String auctionPrice) {
        if (auctionPrice != null && url.contains("{AUCTION_PRICE}")) {
            return url.replace("{AUCTION_PRICE}", auctionPrice);
        }
        return url;
    }

    private void addEventTracker(JSONObject eventtracker, @Nullable String auctionPrice) throws JSONException {
        if (isNativeOmidVerificationTracker(eventtracker)) {
            addNativeOmidVerificationResource(eventtracker, auctionPrice);
            return;
        }

        if (!eventtracker.has("url")) {
            return;
        }

        if (imp_trackers == null) {
            imp_trackers = new ArrayList<>();
        }
        imp_trackers.add(replaceAuctionPrice(eventtracker.getString("url"), auctionPrice));
    }

    private boolean isNativeOmidVerificationTracker(JSONObject eventtracker) {
        return eventtracker.optInt("event", -1) == NATIVE_EVENT_OMID
                && eventtracker.optInt("method", -1) == NATIVE_METHOD_JS;
    }

    private void addNativeOmidVerificationResource(
            JSONObject eventtracker,
            @Nullable String auctionPrice
    ) throws JSONException {
        if (!eventtracker.has("url")) {
            LogUtil.warning(TAG, "Native OMID eventtracker doesn't have url field");
            return;
        }

        JSONObject ext = eventtracker.optJSONObject("ext");
        if (ext == null) {
            LogUtil.warning(TAG, "Native OMID eventtracker doesn't have ext field");
            return;
        }

        String vendorKey = ext.optString(EXT_VENDOR_KEY, "");
        String verificationParameters = ext.optString(EXT_VERIFICATION_PARAMETERS, "");
        if (TextUtils.isEmpty(verificationParameters)) {
            verificationParameters = ext.optString(EXT_VERIFICATION_PARAMETERS_CAMEL_CASE, "");
        }

        if (TextUtils.isEmpty(vendorKey) || TextUtils.isEmpty(verificationParameters)) {
            LogUtil.warning(TAG, "Native OMID eventtracker has invalid verification resource");
            return;
        }

        if (nativeOmidVerificationResources == null) {
            nativeOmidVerificationResources = new ArrayList<>();
        }
        nativeOmidVerificationResources.add(
                new OmAdSessionManager.NativeDisplayVerificationResource(
                        replaceAuctionPrice(eventtracker.getString("url"), auctionPrice),
                        vendorKey,
                        replaceAuctionPrice(verificationParameters, auctionPrice)
                )
        );
    }

    private static void parseEvents(
            JSONObject bidJson,
            PrebidNativeAd ad
    ) {
        ad.winEvent = EventsNotifier.parseEvent("win", bidJson);
        ad.impEvent = EventsNotifier.parseEvent("imp", bidJson);
    }


    private PrebidNativeAd() {
    }

    public void addTitle(NativeTitle title) {
        titles.add(title);
    }

    public void addData(NativeData data) {
        dataList.add(data);
    }

    public void addImage(NativeImage image) {
        images.add(image);
    }

    @NonNull
    public ArrayList<NativeTitle> getTitles() {
        return titles;
    }

    @NonNull
    public ArrayList<NativeImage> getImages() {
        return images;
    }

    @NonNull
    public ArrayList<NativeData> getDataList() {
        return dataList;
    }

    public String getClickUrl() {
        return clickUrl;
    }

    private void setClickUrl(String clickUrl) {
        this.clickUrl = clickUrl;
    }

    /**
     * @return First title or empty string if it doesn't exist
     */
    @NonNull
    public String getTitle() {
        if (!titles.isEmpty()) {
            return titles.get(0).getText();
        }
        return "";
    }

    /**
     * @return First description data value or empty string if it doesn't exist
     */
    @NonNull
    public String getDescription() {
        for (NativeData data : dataList) {
            if (data.getType() == NativeData.Type.DESCRIPTION) {
                return data.getValue();
            }
        }
        return "";
    }

    /**
     * @return First icon url or empty string if it doesn't exist
     */
    @NonNull
    public String getIconUrl() {
        for (NativeImage image : images) {
            if (image.getType() == NativeImage.Type.ICON) {
                return image.getUrl();
            }
        }
        return "";
    }

    /**
     * @return First main image url or empty string if it doesn't exist
     */
    @NonNull
    public String getImageUrl() {
        for (NativeImage image : images) {
            if (image.getType() == NativeImage.Type.MAIN_IMAGE) {
                return image.getUrl();
            }
        }
        return "";
    }

    /**
     * @return First call to action data value or empty string if it doesn't exist
     */
    @NonNull
    public String getCallToAction() {
        for (NativeData data : dataList) {
            if (data.getType() == NativeData.Type.CALL_TO_ACTION) {
                return data.getValue();
            }
        }
        return "";
    }

    /**
     * @return First sponsored by data value or empty string if it doesn't exist
     */
    @NonNull
    public String getSponsoredBy() {
        for (NativeData data : dataList) {
            if (data.getType() == NativeData.Type.SPONSORED_BY) {
                return data.getValue();
            }
        }
        return "";
    }

    @Nullable
    public String getPrivacyUrl() {
        return privacyUrl;
    }

    private void setPrivacyUrl(@Nullable String url) {
        privacyUrl = url;
    }

    /**
     * This API is used to register the view for Ad Events (#onAdClicked(), #onAdImpression, #onAdExpired).
     *
     * @param container      the native ad container used to track impression
     * @param clickableViews list of views that should handle click
     * @param listener must not contain any references to View, Activity, because it can be in memory for a long time.
     *                 Should be class implementation and not anonymous object.
     *                 If it is anonymous class it can produce memory leak.
     * @return true if views registered successfully
     */
    public boolean registerView(View container, List<View> clickableViews, final PrebidNativeAdEventListener listener) {
        if (container == null || clickableViews == null || clickableViews.isEmpty()) {
            return false;
        }
        if (!expired && container != null) {
            this.listener = listener;
            visibilityDetector = VisibilityDetector.create(container);
            if (visibilityDetector == null) {
                return false;
            }

            createImpressionTrackers(container);
            startNativeOmidSession(container);

            registeredView = new WeakReference<>(container);

            container.setOnClickListener(v -> handleClick(v, listener));

            if (clickableViews != null && clickableViews.size() > 0) {
                for (View views : clickableViews) {
                    if (views != null) {
                        views.setOnClickListener(v -> handleClick(v, listener));
                    }
                }
            }
            return true;
        }
        return false;
    }

    public void enableDaroViewabilityImpression() {
        if (visibilityDetector == null || daroViewabilityListener != null) {
            return;
        }

        daroViewabilityListener = new DaroViewabilityListener();
        visibilityDetector.addVisibilityListener(daroViewabilityListener);
    }

    private void createImpressionTrackers(View view) {
        ArrayList<String> combinedImpTrackers = new ArrayList<>();
        if (imp_trackers != null) {
            combinedImpTrackers.addAll(imp_trackers);
        }
        if (impEvent != null) {
            combinedImpTrackers.add(impEvent);
        }

        impressionTrackers = new ArrayList<>();
        for (String url : combinedImpTrackers) {
            ImpressionTracker impressionTracker = ImpressionTracker.create(url, visibilityDetector, view.getContext(), new ImpressionTrackerListener() {
                @Override
                public void onImpressionTrackerFired() {
                    if (listener != null) {
                        listener.onAdImpression();
                    }
                }
            });
            impressionTrackers.add(impressionTracker);
        }
    }

    private void startNativeOmidSession(View container) {
        if (nativeOmidSessionManager != null
                || nativeOmidVerificationResources == null
                || nativeOmidVerificationResources.isEmpty()) {
            return;
        }

        Context context = container.getContext();
        if (context == null) {
            return;
        }

        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            applicationContext = context;
        }

        if (!OmAdSessionManager.activateOmSdk(applicationContext)) {
            return;
        }

        JSLibraryManager jsLibraryManager = JSLibraryManager.getInstance(context);
        boolean scriptsReady = jsLibraryManager.checkIfScriptsDownloadedAndStartDownloadingIfNot();
        if (!scriptsReady && TextUtils.isEmpty(jsLibraryManager.getOMSDKScript())) {
            LogUtil.warning(TAG, "Native OMID session skipped until OMSDK JS is available");
            return;
        }

        OmAdSessionManager sessionManager = OmAdSessionManager.createNewInstance(jsLibraryManager);
        if (sessionManager == null) {
            return;
        }

        if (!sessionManager.initNativeDisplayAdSession(container, nativeOmidVerificationResources, null)) {
            return;
        }

        sessionManager.startAdSession();
        sessionManager.displayAdLoaded();
        nativeOmidSessionManager = sessionManager;
    }

    private void registerNativeOmidImpression() {
        if (nativeOmidImpressionRegistered || nativeOmidSessionManager == null) {
            return;
        }

        nativeOmidImpressionRegistered = true;
        nativeOmidSessionManager.registerImpression();
    }

    private void stopNativeOmidSession() {
        if (nativeOmidSessionManager == null) {
            return;
        }

        nativeOmidSessionManager.stopAdSession();
        nativeOmidSessionManager = null;
    }

    private class DaroViewabilityListener implements VisibilityDetector.VisibilityListener {
        private long elapsedTime = 0;
        private boolean fired = false;

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (fired) {
                return;
            }

            if (visible) {
                elapsedTime += VisibilityDetector.VISIBILITY_THROTTLE_MILLIS;
            } else {
                elapsedTime = 0;
            }

            if (elapsedTime >= Util.NATIVE_AD_VISIBLE_PERIOD_MILLIS) {
                fired = true;
                registerNativeOmidImpression();
                if (listener != null) {
                    listener.onAdBecameViewable();
                }
                if (visibilityDetector != null) {
                    visibilityDetector.removeVisibilityListener(this);
                }
            }
        }
    }

    protected boolean registerPrebidNativeAdEventListener(PrebidNativeAdEventListener listener) {
        this.listener = listener;
        return true;
    }

    private boolean handleClick(View v, PrebidNativeAdEventListener listener) {
        if (clickUrl == null || clickUrl.isEmpty()) {
            return false;
        }

        // open browser
        if (openNativeIntent(clickUrl, v.getContext())) {
            if (listener != null) {
                listener.onAdClicked();
            }
            fireClickTrackers(v.getContext());
            return true;
        }
        return false;
    }

    private boolean openNativeIntent(
            String url,
            Context context
    ) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ExternalViewerUtils.startActivity(context, intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    public void destroy() {
        if (visibilityDetector != null) {
            visibilityDetector.destroy();
            visibilityDetector = null;
        }
        stopNativeOmidSession();
        daroViewabilityListener = null;
        registeredView = null;
        impressionTrackers = null;
        clickTrackers = null;
        listener = null;
    }

    public String getWinEvent() {
        return winEvent;
    }

    public String getImpEvent() {
        return impEvent;
    }


    private void notifyImpressionEvent() {
        if (impressionIsNotNotified) {
            impressionIsNotNotified = false;
            EventsNotifier.notify(impEvent);
        }
    }

    private void fireClickTrackers(Context context) {
        if (click_trackers == null) {
            return;
        }
        for (String url: click_trackers) {
            ClickTracker.createAndFire(url, context, null);
        }
    }

    static class CacheExpireListenerImpl implements CacheManager.CacheExpiryListener {

        private PrebidNativeAd ad;

        public CacheExpireListenerImpl(PrebidNativeAd ad) {
            this.ad = ad;
        }

        @Override
        public void onCacheExpired() {
            LogUtil.error(TAG, "Cache expired");
            WeakReference<View> weakReference = ad.registeredView;
            if (weakReference == null) return;

            View view = weakReference.get();
            if (view != null) return;

            if (ad.listener != null) {
                ad.listener.onAdExpired();
            }
            ad.expired = true;
            ad.destroy();
        }

    }

}
