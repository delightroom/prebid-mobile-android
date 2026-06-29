/*
 *    Copyright 2018-2021 Prebid.org, Inc.
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

package org.prebid.mobile.rendering.models;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import org.prebid.mobile.LogUtil;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.errors.VastParseError;
import org.prebid.mobile.rendering.loading.AdLoadListener;
import org.prebid.mobile.rendering.networking.tracking.TrackingManager;
import org.prebid.mobile.rendering.parser.AdResponseParserBase;
import org.prebid.mobile.rendering.parser.AdResponseParserVast;
import org.prebid.mobile.rendering.utils.helpers.Utils;
import org.prebid.mobile.rendering.video.OmEventTracker;
import org.prebid.mobile.rendering.video.VideoAdEvent;
import org.prebid.mobile.rendering.video.VideoCreativeModel;
import org.prebid.mobile.rendering.video.vast.*;
import org.prebid.mobile.rendering.video.vast.Tracking;

import java.util.ArrayList;

import static org.prebid.mobile.rendering.parser.AdResponseParserVast.*;

public class CreativeModelsMakerVast extends CreativeModelsMaker {

    private static final String TAG = CreativeModelsMakerVast.class.getSimpleName();
    public static final String HTML_CREATIVE_TAG = "HTML";

    static final String VIDEO_CREATIVE_TAG = "Video";

    @NonNull private final AdLoadListener listener;

    private AdUnitConfiguration adConfiguration;

    private AdResponseParserVast rootVastParser;
    private AdResponseParserVast latestVastWrapperParser;

    private String adLoaderIdentifier;
    private String viewableUrl;

    public CreativeModelsMakerVast(
            String adLoaderIdentifier,
            @NonNull AdLoadListener listener
    ) {
        this.listener = listener;
        this.adLoaderIdentifier = adLoaderIdentifier;
    }

    @Override
    public void makeModels(AdUnitConfiguration adConfiguration, AdResponseParserBase... parsers) {
        if (adConfiguration == null) {
            notifyErrorListener("Successful ad response but has a null config to continue ");
            return;
        }

        this.adConfiguration = adConfiguration;

        if (parsers == null) {
            notifyErrorListener("Parsers results are null.");
            return;
        }

        if (parsers.length != 2) {
            notifyErrorListener("2 VAST result parsers are required");
            return;
        }

        rootVastParser = (AdResponseParserVast) parsers[0];
        latestVastWrapperParser = (AdResponseParserVast) parsers[1];

        if (rootVastParser == null || latestVastWrapperParser == null) {
            notifyErrorListener("One of parsers is null.");
            return;
        }

        makeModelsContinued();
    }

    public void setViewableUrl(String url) {
        this.viewableUrl = url;
    }

    private void makeModelsContinued() {
        try {
            // TODO: If we want to support a VAST Buffet, we'll need to put the following in a
            // TODO: loop and make a model for each Ad object in the Buffet
            // TODO: Until then, we'll only make one model

            /***
             * We pre parse the impressions and trackings for faster reading at
             * video time. DO NOT REMOVE THESE LINES
             */
            rootVastParser.getAllTrackings(rootVastParser, 0);
            rootVastParser.getImpressions(rootVastParser, 0);
            rootVastParser.getClickTrackings(rootVastParser, 0);
            final String videoErrorUrl = rootVastParser.getError(rootVastParser, 0);
            final String vastClickThroughUrl = rootVastParser.getClickThroughUrl(rootVastParser, 0);
            final String videoDuration = latestVastWrapperParser.getVideoDuration(latestVastWrapperParser, 0);
            final String skipOffset = latestVastWrapperParser.getSkipOffset(latestVastWrapperParser, 0);
            final AdVerifications adVerifications = rootVastParser.getAdVerification(latestVastWrapperParser, 0);

            checkVideoDuration(Utils.getMsFrom(videoDuration));

            Result result = new Result();
            result.loaderIdentifier = adLoaderIdentifier;

            TrackingManager trackingManager = TrackingManager.getInstance();
            OmEventTracker omEventTracker = new OmEventTracker();

            VideoCreativeModel videoModel = new VideoCreativeModel(trackingManager, omEventTracker, adConfiguration);

            videoModel.setName(VIDEO_CREATIVE_TAG);

            videoModel.setMediaUrl(latestVastWrapperParser.getMediaFileUrl(latestVastWrapperParser, 0));
            videoModel.setMediaDuration(Utils.getMsFrom(videoDuration));
            videoModel.setSkipOffset(Utils.getMsFrom(skipOffset));
            videoModel.setAdVerifications(adVerifications);
            videoModel.setAuid(rootVastParser.getVast().getAds().get(0).getId());
            videoModel.setWidth(latestVastWrapperParser.getWidth());
            videoModel.setHeight(latestVastWrapperParser.getHeight());
            videoModel.setViewableUrl(viewableUrl);
            //put tracking urls into element.
            for (VideoAdEvent.Event videoEvent : VideoAdEvent.Event.values()) {
                videoModel.getVideoEventUrls().put(videoEvent, rootVastParser.getTrackingByType(videoEvent));
            }

            //put impression urls into element
            ArrayList<String> impUrls = new ArrayList<>();
            for (Impression impression : rootVastParser.getImpressions()) {
                impUrls.add(impression.getValue());
            }
            videoModel.getVideoEventUrls().put(VideoAdEvent.Event.AD_IMPRESSION, impUrls);

            //put click urls into element
            ArrayList<String> clickTrackingUrls = new ArrayList<>();
            for (ClickTracking clickTracking : rootVastParser.getClickTrackings()) {
                clickTrackingUrls.add(clickTracking.getValue());
            }
            videoModel.getVideoEventUrls().put(VideoAdEvent.Event.AD_CLICK, clickTrackingUrls);

            //put error vastURL into element
            ArrayList<String> errorUrls = new ArrayList<>();
            errorUrls.add(videoErrorUrl);
            videoModel.getVideoEventUrls().put(VideoAdEvent.Event.AD_ERROR, errorUrls);

            //put click through url into element
            videoModel.setVastClickthroughUrl(vastClickThroughUrl);

            result.creativeModels = new ArrayList<>();
            result.creativeModels.add(videoModel);

            CreativeModel endCardModel = new CreativeModel(trackingManager, omEventTracker, adConfiguration);
            endCardModel.setName(HTML_CREATIVE_TAG);
            endCardModel.setHasEndCard(true);

            InLine inline = latestVastWrapperParser.getVast()
                                                   .getAds()
                                                   .get(0)
                                                   .getInline();

            // Create CompanionAd object
            Companion companionAd = AdResponseParserVast.getCompanionAd(inline);
            if (companionAd != null) {
                switch (AdResponseParserVast.getCompanionResourceFormat(companionAd)) {
                    case RESOURCE_FORMAT_HTML:
                        endCardModel.setHtml(companionAd.getHtmlResource().getValue());
                        break;
                    case RESOURCE_FORMAT_IFRAME:
                        endCardModel.setHtml(companionAd.getIFrameResource().getValue());
                        break;
                    case RESOURCE_FORMAT_STATIC:
                        String clickThroughUrl = getCompanionClickThroughUrl(companionAd, vastClickThroughUrl);
                        endCardModel.setHtml(buildDaroStaticEndCardHtml(
                            clickThroughUrl,
                            companionAd.getStaticResource().getValue(),
                            getAdTitle(inline),
                            getAdSubtitle(inline)
                        ));
                        break;
                }

                endCardModel.setClickUrl(getCompanionClickThroughUrl(companionAd, vastClickThroughUrl));

                if (companionAd.getCompanionClickTracking() != null) {
                    String clickTrackingUrl = companionAd.getCompanionClickTracking().getValue();
                    endCardModel.setClickTrackingUrl(clickTrackingUrl);

                    clickTrackingUrls = new ArrayList<>();
                    clickTrackingUrls.add(clickTrackingUrl);
                    endCardModel.registerTrackingEvent(TrackingEvent.Events.CLICK, clickTrackingUrls);
                }

                Tracking creativeViewTracking = AdResponseParserVast.findTracking(companionAd.getTrackingEvents());
                if (creativeViewTracking != null && Utils.isNotBlank(creativeViewTracking.getValue())) {
                    ArrayList<String> creativeViewTrackingUrls = new ArrayList<>();
                    creativeViewTrackingUrls.add(creativeViewTracking.getValue());
                    endCardModel.registerTrackingEvent(TrackingEvent.Events.IMPRESSION, creativeViewTrackingUrls);
                }

                endCardModel.setWidth(Integer.parseInt(companionAd.getWidth()));
                endCardModel.setHeight(Integer.parseInt(companionAd.getHeight()));


                AdUnitConfiguration endCardConfig = new AdUnitConfiguration();
                endCardConfig.setRewardManager(adConfiguration.getRewardManager());
                endCardConfig.setAdFormat(AdFormat.INTERSTITIAL);
                endCardConfig.setRewarded(shouldEndCardUseRewardedFlow(adConfiguration));
                endCardConfig.getRewardManager().setRewardedExt(adConfiguration.getRewardManager().getRewardedExt());
                endCardConfig.setHasEndCard(true);
                endCardModel.setAdConfiguration(endCardConfig);


                endCardModel.setRequireImpressionUrl(false);
                result.creativeModels.add(endCardModel);

                // Flag that video creative has a corresponding end card
                videoModel.setHasEndCard(true);
                adConfiguration.setHasEndCard(true);
            }
            adConfiguration.setInterstitialSize(videoModel.getWidth() + "x" + videoModel.getHeight());
            listener.onCreativeModelReady(result);
        } catch (Exception e) {
            LogUtil.error(TAG, "Video failed with: " + e.getMessage());
            notifyErrorListener("Video failed: " + e.getMessage());
        }
    }

    private void notifyErrorListener(String msg) {
        listener.onFailedToLoadAd(new AdException(AdException.INTERNAL_ERROR, msg), adLoaderIdentifier);
    }

    private boolean shouldEndCardUseRewardedFlow(AdUnitConfiguration adConfiguration) {
        return adConfiguration.isRewarded() && !adConfiguration.isDaroFullscreenRenderer();
    }

    private void checkVideoDuration(long currentDuration) throws VastParseError {
        if (adConfiguration != null && adConfiguration.getMaxVideoDuration() != null) {
            long maxDuration = adConfiguration.getMaxVideoDuration() * 1000;
            if (currentDuration > maxDuration) {
                throw new VastParseError("Video duration can't be more then ad unit max video duration: " + maxDuration + " (current duration: " + currentDuration + ")");
            }
        }
    }

    private static String getCompanionClickThroughUrl(
        Companion companionAd,
        String vastClickThroughUrl
    ) {
        if (companionAd.getCompanionClickThrough() != null
            && Utils.isNotBlank(companionAd.getCompanionClickThrough().getValue())
        ) {
            return companionAd.getCompanionClickThrough().getValue();
        }
        return vastClickThroughUrl;
    }

    private static String getAdTitle(InLine inline) {
        if (inline.getAdTitle() != null && Utils.isNotBlank(inline.getAdTitle().getValue())) {
            return inline.getAdTitle().getValue();
        }
        return "Sponsored Ad";
    }

    private static String getAdSubtitle(InLine inline) {
        if (inline.getDescription() != null && Utils.isNotBlank(inline.getDescription().getValue())) {
            return inline.getDescription().getValue();
        }
        if (inline.getAdvertiser() != null && Utils.isNotBlank(inline.getAdvertiser().getValue())) {
            return inline.getAdvertiser().getValue();
        }
        return "Tap to learn more";
    }

    @VisibleForTesting
    static String buildDaroStaticEndCardHtml(
        String clickThroughUrl,
        String imageUrl,
        String title,
        String subtitle
    ) {
        String safeClickUrl = escapeHtmlAttribute(clickThroughUrl);
        String safeImageUrl = escapeHtmlAttribute(imageUrl);
        String safeTitle = escapeHtml(title);
        String safeSubtitle = escapeHtml(subtitle);

        return "<!doctype html>"
               + "<html>"
               + "<head>"
               + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
               + "<style>"
               + "html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#27272a;}"
               + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#fafafa;}"
               + ".daro-end-card{position:fixed;inset:0;background:#27272a;}"
               + ".daro-center{position:absolute;left:20px;right:20px;top:50%;transform:translateY(-50%);"
               + "box-sizing:border-box;padding:0 12px;text-align:center;}"
               + ".daro-icon-wrap{padding-bottom:24px;}"
               + ".daro-icon{width:96px;height:96px;border-radius:16px;object-fit:cover;display:block;margin:0 auto;}"
               + ".daro-title{margin:0 0 8px;font-size:36px;line-height:1;font-weight:700;letter-spacing:0;color:#fafafa;}"
               + ".daro-subtitle{margin:0 0 16px;font-size:16px;line-height:28px;font-weight:400;color:#9ca3af;}"
               + ".daro-cta{height:60px;width:100%;box-sizing:border-box;border-radius:16px;background:#3b82f6;"
               + "color:#fff;text-decoration:none;display:flex;align-items:center;justify-content:center;gap:4px;"
               + "font-size:18px;line-height:28px;font-weight:600;-webkit-tap-highlight-color:transparent;}"
               + ".daro-cta svg{width:16px;height:16px;stroke:currentColor;stroke-width:2;fill:none;stroke-linecap:round;stroke-linejoin:round;}"
               + "</style>"
               + "</head>"
               + "<body>"
               + "<main class=\"daro-end-card\">"
               + "<section class=\"daro-center\">"
               + "<div class=\"daro-icon-wrap\"><img class=\"daro-icon\" alt=\"\" src=\"" + safeImageUrl + "\"></div>"
               + "<h1 class=\"daro-title\">" + safeTitle + "</h1>"
               + "<p class=\"daro-subtitle\">" + safeSubtitle + "</p>"
               + "<a class=\"daro-cta\" href=\"" + safeClickUrl + "\">"
               + "<span>Install Now</span>"
               + "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\">"
               + "<path d=\"M15 3H21V9\"></path>"
               + "<path d=\"M10 14L21 3\"></path>"
               + "<path d=\"M18 13V19C18 20.1 17.1 21 16 21H5C3.9 21 3 20.1 3 19V8C3 6.9 3.9 6 5 6H11\"></path>"
               + "</svg>"
               + "</a>"
               + "</section>"
               + "</main>"
               + "</body>"
               + "</html>";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }

    private static String escapeHtmlAttribute(String value) {
        return escapeHtml(value).replace("\"", "&quot;")
                                .replace("'", "&#39;");
    }

}
