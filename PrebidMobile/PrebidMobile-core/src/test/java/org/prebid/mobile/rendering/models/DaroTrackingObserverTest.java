package org.prebid.mobile.rendering.models;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.daro.DaroPrebidTrackingEvent;
import org.prebid.mobile.rendering.networking.tracking.TrackingManager;
import org.prebid.mobile.rendering.video.OmEventTracker;
import org.prebid.mobile.rendering.video.VideoCreativeModel;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(JUnit4.class)
@Config(sdk = 19)
public class DaroTrackingObserverTest {

    @Test
    public void displayTrackingEventNotifiesDaroObserverAndKeepsPrebidTrackingOwner() {
        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        List<DaroPrebidTrackingEvent> observedEvents = new ArrayList<>();
        adConfiguration.setDaroTrackingObserver(observedEvents::add);
        TrackingManager trackingManager = mock(TrackingManager.class);

        CreativeModel model = new CreativeModel(trackingManager, mock(OmEventTracker.class), adConfiguration);
        ArrayList<String> urls = new ArrayList<>();
        urls.add("https://tracker.example/click");
        model.registerTrackingEvent(TrackingEvent.Events.CLICK, urls);

        model.trackEventNamed(TrackingEvent.Events.CLICK);

        assertEquals(1, observedEvents.size());
        assertEquals("click", observedEvents.get(0).getName());
        assertEquals(DaroPrebidTrackingEvent.SOURCE_DISPLAY, observedEvents.get(0).getSource());
        assertEquals(1, observedEvents.get(0).getUrlCount());
        verify(trackingManager).fireEventTrackingURLs(urls);
    }

    @Test
    public void endCardTrackingEventUsesCompanionSource() {
        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        List<DaroPrebidTrackingEvent> observedEvents = new ArrayList<>();
        adConfiguration.setDaroTrackingObserver(observedEvents::add);
        TrackingManager trackingManager = mock(TrackingManager.class);

        CreativeModel model = new CreativeModel(trackingManager, mock(OmEventTracker.class), adConfiguration);
        model.setName(CreativeModelsMakerVast.HTML_CREATIVE_TAG);
        model.setHasEndCard(true);
        ArrayList<String> urls = new ArrayList<>();
        urls.add("https://tracker.example/end-card-impression");
        model.registerTrackingEvent(TrackingEvent.Events.IMPRESSION, urls);

        model.trackEventNamed(TrackingEvent.Events.IMPRESSION);

        assertEquals(1, observedEvents.size());
        assertEquals("impression", observedEvents.get(0).getName());
        assertEquals(DaroPrebidTrackingEvent.SOURCE_COMPANION, observedEvents.get(0).getSource());
        assertEquals(1, observedEvents.get(0).getUrlCount());
        verify(trackingManager).fireEventTrackingImpressionURLs(urls);
    }

    @Test
    public void videoModelDisplayTrackingEventDoesNotUseCompanionSourceWhenItHasEndCard() {
        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        List<DaroPrebidTrackingEvent> observedEvents = new ArrayList<>();
        adConfiguration.setDaroTrackingObserver(observedEvents::add);
        TrackingManager trackingManager = mock(TrackingManager.class);

        VideoCreativeModel model = new VideoCreativeModel(trackingManager, mock(OmEventTracker.class), adConfiguration);
        model.setName("Video");
        model.setHasEndCard(true);
        ArrayList<String> urls = new ArrayList<>();
        urls.add("https://tracker.example/video-impression");
        model.registerTrackingEvent(TrackingEvent.Events.IMPRESSION, urls);

        model.trackEventNamed(TrackingEvent.Events.IMPRESSION);

        assertEquals(1, observedEvents.size());
        assertEquals("impression", observedEvents.get(0).getName());
        assertEquals(DaroPrebidTrackingEvent.SOURCE_DISPLAY, observedEvents.get(0).getSource());
        assertEquals(1, observedEvents.get(0).getUrlCount());
        verify(trackingManager).fireEventTrackingImpressionURLs(urls);
    }
}
