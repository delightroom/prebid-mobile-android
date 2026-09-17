package org.prebid.mobile.rendering.parser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.prebid.mobile.rendering.video.vast.*;
import org.prebid.mobile.rendering.video.VideoCreativeModel;
import org.prebid.mobile.rendering.video.OmEventTracker;
import org.prebid.mobile.rendering.networking.tracking.TrackingManager;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.session.manager.OmAdSessionManager;
import org.prebid.mobile.rendering.sdk.JSLibraryManager;
import java.lang.reflect.*;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class Vast40CapabilityTest {
    private AdResponseParserVastHelper parse(String verification) throws Exception {
        return new AdResponseParserVastHelper("<VAST version=\"4.0\"><Ad><InLine>" + verification
            + "<Creatives><Creative><UniversalAdId idRegistry=\"registry\" idValue=\"registered-id\">creative-id</UniversalAdId>"
            + "<Linear><Duration>00:00:30</Duration><MediaFiles><MediaFile delivery=\"progressive\" type=\"video/mp4\" width=\"300\" height=\"250\">https://test.invalid/ad.mp4</MediaFile>"
            + "<Mezzanine>https://test.invalid/master.mp4</Mezzanine><InteractiveCreativeFile apiFramework=\"VPAID\">https://test.invalid/interactive.js</InteractiveCreativeFile>"
            + "</MediaFiles></Linear></Creative><Creative><Linear><Duration>00:00:30</Duration></Linear></Creative></Creatives></InLine></Ad></VAST>");
    }

    @Test public void advisoryErrorsDoNotSuppressTerminalErrorsAndAreDeduplicated() throws Exception {
        TrackingManager tracker = mock(TrackingManager.class);
        Field singleton = TrackingManager.class.getDeclaredField("sInstance");
        singleton.setAccessible(true);
        Object original = singleton.get(null);
        singleton.set(null, tracker);
        try {
            VideoCreativeModel model = new VideoCreativeModel(tracker, mock(OmEventTracker.class), new AdUnitConfiguration());
            model.registerVideoEvent(org.prebid.mobile.rendering.video.VideoAdEvent.Event.AD_ERROR,
                new java.util.ArrayList<>(java.util.Arrays.asList("https://test.invalid/error?code=[ERRORCODE]", "https://test.invalid/error?code=[ERRORCODE]")));
            model.trackVastFeatureError(409);
            model.trackVastFeatureError(409);
            model.trackVastFeatureError(410);
            model.trackVastFeatureError(410);
            model.trackVastError(405);
            model.trackVastError(400);
            verify(tracker).fireEventTrackingURL("https://test.invalid/error?code=409");
            verify(tracker).fireEventTrackingURL("https://test.invalid/error?code=410");
            verify(tracker).fireEventTrackingURL("https://test.invalid/error?code=405");
            verifyNoMoreInteractions(tracker);
        } finally { singleton.set(null, original); }
    }

    @Test public void preservesCreativeIdentityAndSeparatesInteractiveFromMedia() throws Exception {
        AdResponseParserVastHelper parser = parse("");
        Creative creative = parser.getVast().getAds().get(0).getInline().getCreatives().get(0);
        VideoCreativeModel model = new VideoCreativeModel(mock(TrackingManager.class), mock(OmEventTracker.class), new AdUnitConfiguration());
        model.setVastCreative(creative);
        assertEquals("creative-id", model.getUniversalAdId());
        assertEquals("registry", model.getUniversalAdIdRegistry());
        assertEquals("registered-id", model.getUniversalAdIdValue());
        assertTrue(model.hasInteractiveCreativeFile());
        assertEquals(1, creative.getLinear().getMediaFiles().size());
        assertNull(parser.getVast().getAds().get(0).getInline().getCreatives().get(1).getUniversalAdId());
    }

    @Test public void omidWithoutParametersCreatesAResourceAndUnsupportedFrameworkIsExcluded() throws Exception {
        AdResponseParserVastHelper parser = parse("<AdVerifications><Verification vendor=\"test\"><JavaScriptResource apiFramework=\"omid\">https://test.invalid/verify.js</JavaScriptResource></Verification>"
                + "<Verification><FlashResource apiFramework=\"VPAID\">https://test.invalid/verify.swf</FlashResource></Verification></AdVerifications>");
        AdVerifications verifications = parser.getAdVerification(parser, 0);
        assertTrue(verifications.getVerifications().get(0).isSupportedOmidResource());
        assertFalse(verifications.getVerifications().get(1).isSupportedOmidResource());
        Constructor<OmAdSessionManager> constructor = OmAdSessionManager.class.getDeclaredConstructor(JSLibraryManager.class);
        constructor.setAccessible(true);
        OmAdSessionManager manager = constructor.newInstance(mock(JSLibraryManager.class));
        Method method = OmAdSessionManager.class.getDeclaredMethod("createVerificationScriptResources", AdVerifications.class);
        method.setAccessible(true);
        assertEquals(1, ((List<?>) method.invoke(manager, verifications)).size());
    }
}
