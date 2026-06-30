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

package org.prebid.mobile.rendering.video;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.daro.DaroPrebidTrackingEvent;
import org.prebid.mobile.rendering.models.internal.InternalPlayerState;
import org.prebid.mobile.rendering.networking.tracking.TrackingManager;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(JUnit4.class)
@Config(sdk = 19)
public class VideoCreativeModelTest {

    private VideoCreativeModel videoCreativeModel;

    private OmEventTracker mockOmEventTracker;
    private TrackingManager mockTrackingManager;

    @Before
    public void setup() {
        AdUnitConfiguration adConfiguration = mock(AdUnitConfiguration.class);
        mockOmEventTracker = mock(OmEventTracker.class);

        mockTrackingManager = mock(TrackingManager.class);

        videoCreativeModel = new VideoCreativeModel(mockTrackingManager, mockOmEventTracker, adConfiguration);
    }

    @Test
    public void trackVideoEventTest() {
        videoCreativeModel.getVideoEventUrls().put(VideoAdEvent.Event.AD_COLLAPSE, new ArrayList<String>());

        videoCreativeModel.trackVideoEvent(VideoAdEvent.Event.AD_COLLAPSE);
        verify(mockTrackingManager).fireEventTrackingURLs(any(ArrayList.class));
        verify(mockOmEventTracker).trackOmVideoAdEvent(VideoAdEvent.Event.AD_COLLAPSE);

        videoCreativeModel.trackNonSkippableStandaloneVideoLoaded(false);
        verify(mockOmEventTracker).trackNonSkippableStandaloneVideoLoaded(false);

        videoCreativeModel.trackPlayerStateChange(InternalPlayerState.FULLSCREEN);
        verify(mockOmEventTracker).trackOmPlayerStateChange(InternalPlayerState.FULLSCREEN);

        videoCreativeModel.trackVideoAdStarted(0, 0);
        verify(mockOmEventTracker).trackVideoAdStarted(0, 0);
    }

    @Test
    public void registerVideoEventTest() {
        assertEquals(0, videoCreativeModel.getVideoEventUrls().size());
        videoCreativeModel.registerVideoEvent(VideoAdEvent.Event.AD_COLLAPSE, new ArrayList<String>());
        assertEquals(1, videoCreativeModel.getVideoEventUrls().size());
    }

    @Test
    public void trackVideoEventNotifiesDaroObserverAndKeepsPrebidTrackingOwner() {
        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        List<DaroPrebidTrackingEvent> observedEvents = new ArrayList<>();
        adConfiguration.setDaroTrackingObserver(observedEvents::add);

        VideoCreativeModel model = new VideoCreativeModel(mockTrackingManager, mockOmEventTracker, adConfiguration);
        ArrayList<String> urls = new ArrayList<>();
        urls.add("https://tracker.example/complete");
        model.registerVideoEvent(VideoAdEvent.Event.AD_COMPLETE, urls);

        model.trackVideoEvent(VideoAdEvent.Event.AD_COMPLETE);

        assertEquals(1, observedEvents.size());
        assertEquals("complete", observedEvents.get(0).getName());
        assertEquals(DaroPrebidTrackingEvent.SOURCE_VAST_VIDEO, observedEvents.get(0).getSource());
        assertEquals(1, observedEvents.get(0).getUrlCount());
        verify(mockTrackingManager).fireEventTrackingURLs(urls);
        verify(mockOmEventTracker).trackOmVideoAdEvent(VideoAdEvent.Event.AD_COMPLETE);
    }

    @Test
    public void trackVideoEventNotifiesDaroObserverWhenTrackingUrlsAreMissing() {
        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        List<DaroPrebidTrackingEvent> observedEvents = new ArrayList<>();
        adConfiguration.setDaroTrackingObserver(observedEvents::add);

        VideoCreativeModel model = new VideoCreativeModel(mockTrackingManager, mockOmEventTracker, adConfiguration);

        model.trackVideoEvent(VideoAdEvent.Event.AD_SKIP);

        assertEquals(1, observedEvents.size());
        assertEquals("skip", observedEvents.get(0).getName());
        assertEquals(DaroPrebidTrackingEvent.SOURCE_VAST_VIDEO, observedEvents.get(0).getSource());
        assertEquals(0, observedEvents.get(0).getUrlCount());
        verify(mockTrackingManager, never()).fireEventTrackingURLs(any(ArrayList.class));
        verify(mockOmEventTracker).trackOmVideoAdEvent(VideoAdEvent.Event.AD_SKIP);
    }
}
