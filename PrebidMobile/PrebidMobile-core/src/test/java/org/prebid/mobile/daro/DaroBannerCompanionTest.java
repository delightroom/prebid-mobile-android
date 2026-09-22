package org.prebid.mobile.daro;

import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.AdSize;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.models.PlacementType;
import org.prebid.mobile.rendering.models.TrackingEvent;
import org.prebid.mobile.rendering.networking.tracking.TrackingManager;
import org.prebid.mobile.rendering.parser.AdResponseParserVast;
import org.prebid.mobile.rendering.video.VideoCreative;
import org.prebid.mobile.rendering.views.AdViewManager;
import org.prebid.mobile.rendering.views.AdViewManagerListener;
import org.prebid.mobile.rendering.views.interstitial.InterstitialManager;
import org.prebid.mobile.rendering.views.webview.PrebidWebViewBase;
import org.prebid.mobile.test.utils.WhiteBox;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DaroBannerCompanionTest {
    private AdUnitConfiguration config() {
        AdUnitConfiguration config = new AdUnitConfiguration();
        config.setAdFormat(AdFormat.VAST);
        config.setBuiltInVideo(true);
        config.setPlacementType(PlacementType.IN_BANNER);
        config.addSize(new AdSize(320, 50));
        return config;
    }

    private String companion(String attributes, String children) {
        return "<Companion " + attributes + ">" + children + "</Companion>";
    }

    private String resource(String name) {
        return "<StaticResource creativeType='image/jpeg'>https://example.com/" + name + "</StaticResource>";
    }

    private AdResponseParserVast parse(String type, String companions, String required) throws Exception {
        return new AdResponseParserVast("<VAST version='4.0'><Ad><" + type + "><AdSystem>test</AdSystem>"
                + "<Creatives><Creative><CompanionAds required='" + required + "'>" + companions
                + "</CompanionAds></Creative></Creatives></" + type + "></Ad></VAST>");
    }

    @Test public void explicitEndCardWinsAndStaticDoesNotLoseToHtml() throws Exception {
        String xml = companion("width='320' height='50'", "<HTMLResource>ignored</HTMLResource>")
                + companion("width='320' height='50'", resource("default.jpg"))
                + companion("width='1080' height='1920' renderingMode='end-card'", resource("end.jpg"));
        DaroBannerCompanionModel model = DaroBannerCompanionModel.fromVast(parse("InLine", xml, "none"), config());
        assertTrue(model.getHtml().contains("end.jpg"));
        assertEquals(320, model.getWidth());
        assertEquals(50, model.getHeight());
        assertTrue(model.getHtml().contains("object-fit:contain"));
        assertFalse(model.getHtml().contains("<a "));
        assertNull(model.getClickUrl());
    }

    @Test public void closestRatioWinsAndInvalidDimensionsDoNotFailVideo() throws Exception {
        String xml = companion("width='bad' height='0'", resource("invalid-size.jpg"))
                + companion("width='1080' height='1920'", resource("portrait.jpg"))
                + companion("width='320' height='50'", resource("padded.jpg"));
        assertTrue(DaroBannerCompanionModel.fromVast(parse("InLine", xml, "none"), config()).getHtml().contains("padded.jpg"));
        assertNotNull(DaroBannerCompanionModel.fromVast(parse("InLine", companion("", resource("no-size.jpg")), "none"), config()));
    }

    @Test public void concurrentAndUnsupportedResourceAreNotEndCards() throws Exception {
        String xml = companion("renderingMode='concurrent'", resource("concurrent.jpg"))
                + companion("", "<StaticResource creativeType='application/javascript'>https://example.com/ad.js</StaticResource>");
        assertNull(DaroBannerCompanionModel.fromVast(parse("InLine", xml, "none"), config()));
    }

    @Test public void missingInlineResourceUsesClosestWrapper() throws Exception {
        AdResponseParserVast outer = parse("Wrapper", companion("", resource("outer.jpg")), "none");
        AdResponseParserVast inner = parse("Wrapper", companion("", resource("inner.jpg")), "none");
        outer.setWrapper(inner);
        inner.setWrapper(parse("InLine", "", "none"));
        assertTrue(DaroBannerCompanionModel.fromVast(outer, config()).getHtml().contains("inner.jpg"));
    }

    @Test public void allInlineTrackersAndMatchingWrapperTrackersArePreserved() throws Exception {
        String tracks = "<TrackingEvents><Tracking event='creativeView'>https://example.com/view1</Tracking>"
                + "<Tracking event='creativeView'>https://example.com/view2</Tracking></TrackingEvents>"
                + "<CompanionClickTracking>https://example.com/click1</CompanionClickTracking>"
                + "<CompanionClickTracking>https://example.com/click2</CompanionClickTracking>";
        AdResponseParserVast root = parse("Wrapper", companion("id='same'", "<TrackingEvents><Tracking event='creativeView'>"
                + "https://example.com/wrapper</Tracking></TrackingEvents>")
                + companion("id='different'", "<TrackingEvents><Tracking event='creativeView'>https://example.com/wrong</Tracking></TrackingEvents>"), "none");
        root.setWrapper(parse("InLine", companion("id='same'", resource("image.jpg") + tracks), "none"));
        DaroBannerCompanionModel model = DaroBannerCompanionModel.fromVast(root, config());
        TrackingManager manager = mock(TrackingManager.class);
        WhiteBox.setInternalState(model, "trackingManager", manager);
        model.trackDisplayAdEvent(TrackingEvent.Events.IMPRESSION);
        model.trackDisplayAdEvent(TrackingEvent.Events.CLICK);
        verify(manager).fireEventTrackingImpressionURLs(new ArrayList<>(Arrays.asList("https://example.com/view1", "https://example.com/view2", "https://example.com/wrapper")));
        verify(manager).fireEventTrackingURLs(new ArrayList<>(Arrays.asList("https://example.com/click1", "https://example.com/click2")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiredAllCannotBeSilentlyReducedToOneImage() throws Exception {
        DaroBannerCompanionModel.fromVast(parse("InLine", companion("id='a'", resource("a.jpg"))
                + companion("id='b'", resource("b.jpg")), "all"), config());
    }

    @Test public void requiredGroupWinsOverBetterOptionalCandidate() throws Exception {
        for (String requirement : Arrays.asList("any", "all")) {
            for (String optionalMode : Arrays.asList("", " renderingMode='end-card'")) {
                String required = companion("width='1080' height='1920'", resource("required.jpg"));
                String optional = companion("width='320' height='50'" + optionalMode, resource("optional.jpg"));
                String groups = "<CompanionAds required='" + requirement + "'>" + required
                        + "</CompanionAds></Creative><Creative><CompanionAds required='none'>" + optional
                        + "</CompanionAds>";
                AdResponseParserVast parser = new AdResponseParserVast("<VAST version='4.0'><Ad><InLine>"
                        + "<Creatives><Creative>" + groups + "</Creative></Creatives></InLine></Ad></VAST>");
                DaroBannerCompanionModel model = DaroBannerCompanionModel.fromVast(parser, config());
                assertTrue(model.isRequired());
                assertTrue(model.getHtml().contains("required.jpg"));
            }
        }
    }

    @Test public void wrapperRequirementFiltersInlineCandidatesBeforeRanking() throws Exception {
        AdResponseParserVast root = parse("Wrapper", companion("id='required'", ""), "any");
        root.setWrapper(parse("InLine", companion("id='required'", resource("required.jpg"))
                + companion("id='optional' renderingMode='end-card'", resource("optional.jpg")), "none"));
        assertTrue(DaroBannerCompanionModel.fromVast(root, config()).getHtml().contains("required.jpg"));
    }

    @Test public void completionSwapsOnlyReadyImageAndNeverReportsAnotherImpression() throws Exception {
        AdViewManagerListener listener = mock(AdViewManagerListener.class);
        InterstitialManager interstitial = mock(InterstitialManager.class);
        AdViewManager manager = new AdViewManager(RuntimeEnvironment.application, listener,
                new FrameLayout(RuntimeEnvironment.application), interstitial);
        WhiteBox.setInternalState(manager, "adConfiguration", config());
        VideoCreative video = mock(VideoCreative.class);
        when(video.isVideo()).thenReturn(true);
        DaroBannerCompanionCreative image = mock(DaroBannerCompanionCreative.class);
        when(image.isResolved()).thenReturn(true);
        when(image.getCreativeView()).thenReturn(mock(PrebidWebViewBase.class));
        WhiteBox.setInternalState(manager, "currentCreative", video);
        WhiteBox.setInternalState(manager, "bannerCompanion", image);
        manager.creativeDidComplete(video);
        verify(listener).videoCreativePlaybackFinished();
        verify(listener).viewReadyForImmediateDisplay(image.getCreativeView());
        verify(image).display();
        verify(video).destroy();
        manager.creativeDidTrackImpression(image);
        verify(listener, never()).adDisplayed();
        verify(interstitial).setAdViewManagerInterstitialDelegate(any());
        verifyNoMoreInteractions(interstitial);
        assertFalse(manager.isNotShowingEndCard());
        manager.destroy();
    }

    @Test public void imageNotReadyKeepsEndedVideo() throws Exception {
        AdViewManagerListener listener = mock(AdViewManagerListener.class);
        AdViewManager manager = new AdViewManager(RuntimeEnvironment.application, listener,
                new FrameLayout(RuntimeEnvironment.application), mock(InterstitialManager.class));
        WhiteBox.setInternalState(manager, "adConfiguration", config());
        VideoCreative video = mock(VideoCreative.class);
        when(video.isVideo()).thenReturn(true);
        WhiteBox.setInternalState(manager, "currentCreative", video);
        manager.creativeDidComplete(video);
        verify(video, never()).destroy();
        verify(listener, never()).viewReadyForImmediateDisplay(any());
        assertTrue(manager.isNotShowingEndCard());
        manager.destroy();
    }

    @Test public void pngAndGifAreSupportedAndRequiredAnyAcceptsOneCandidate() throws Exception {
        for (String type : Arrays.asList("image/png", "image/gif")) {
            String xml = companion("id='a'", "<StaticResource creativeType='" + type + "'>https://example.com/image</StaticResource>")
                    + companion("id='unsupported'", "<HTMLResource>ignored</HTMLResource>");
            assertTrue(DaroBannerCompanionModel.fromVast(parse("InLine", xml, "any"), config()).isRequired());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiredAllDoesNotTreatSameSizeDifferentResourcesAsOne() throws Exception {
        DaroBannerCompanionModel.fromVast(parse("InLine",
                companion("width='320' height='50'", resource("a.jpg"))
                + companion("width='320' height='50'", resource("b.jpg")), "all"), config());
    }

    @Test public void clickCannotExecuteCodeInGeneratedDocumentButDeepLinksRemainSupported() {
        assertNull(DaroBannerCompanionModel.safeClick("javascript:alert(1)"));
        assertNull(DaroBannerCompanionModel.safeClick("data:text/html,anything"));
        assertNull(DaroBannerCompanionModel.safeClick("file:///anything"));
        assertEquals("myapp://open", DaroBannerCompanionModel.safeClick("myapp://open"));
    }

    @Test public void htmlEscapesUrlsAndDoesNotRewriteServerPadding() {
        String html = DaroBannerCompanionModel.html("https://example.com/tr:w-320,h-50,cm-pad_resize/a.jpg?q=\"&x=1", "https://example.com/click?a=1&b=2");
        assertTrue(html.contains("tr:w-320,h-50,cm-pad_resize"));
        assertTrue(html.contains("q=&quot;&amp;x=1"));
        assertTrue(html.contains("a=1&amp;b=2"));
    }
}
