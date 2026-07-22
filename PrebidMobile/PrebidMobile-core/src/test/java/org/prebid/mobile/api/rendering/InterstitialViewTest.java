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

package org.prebid.mobile.api.rendering;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.core.R;
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse;
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialViewListener;
import org.prebid.mobile.rendering.models.internal.InternalFriendlyObstruction;
import org.prebid.mobile.rendering.views.AdViewManager;
import org.prebid.mobile.rendering.views.interstitial.DaroFullscreenChromeView;
import org.prebid.mobile.rendering.views.interstitial.InterstitialManager;
import org.prebid.mobile.rendering.views.interstitial.InterstitialVideo;
import org.prebid.mobile.test.utils.WhiteBox;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class InterstitialViewTest {

    private InterstitialView spyBidInterstitialView;
    private Context context;
    @Mock private AdViewManager mockAdViewManager;
    @Mock private InterstitialManager mockInterstitialManager;

    @Before
    public void setup() throws AdException, IllegalAccessException {
        MockitoAnnotations.initMocks(this);

        context = Robolectric.buildActivity(Activity.class).create().get();

        spyBidInterstitialView = spy(new InterstitialView(context));

        when(mockAdViewManager.getAdConfiguration()).thenReturn(mock(AdUnitConfiguration.class));
        WhiteBox.field(InterstitialView.class, "adViewManager").set(spyBidInterstitialView, mockAdViewManager);
        WhiteBox.field(InterstitialView.class, "interstitialManager").set(spyBidInterstitialView, mockInterstitialManager);
    }

    @Test
    public void loadAd_ExecuteBidTransactionLoad() {
        AdUnitConfiguration mockAdUnitConfiguration = mock(AdUnitConfiguration.class);
        BidResponse mockBidResponse = mock(BidResponse.class);

        spyBidInterstitialView.loadAd(mockAdUnitConfiguration, mockBidResponse);

        verify(mockAdViewManager, times(1)).loadBidTransaction(eq(mockAdUnitConfiguration), eq(mockBidResponse));
    }

    @Test
    public void setInterstitialViewListener_ExecuteAddEventListener() {
        final InterstitialViewListener mockInterstitialViewListener = mock(InterstitialViewListener.class);

        spyBidInterstitialView.setInterstitialViewListener(mockInterstitialViewListener);

        verify(spyBidInterstitialView, times(1)).setInterstitialViewListener(eq(mockInterstitialViewListener));
    }

    @Test
    public void formInterstitialObstructionsArray_RewardProgressUsesVideoControlsPurpose() {
        addChildView(R.id.iv_close_interstitial);
        addChildView(R.id.iv_skip);
        addChildView(R.id.rl_count_down);
        addChildView(R.id.tv_learn_more);
        addChildView(R.id.iv_sound_interstitial);
        addChildView(R.id.daro_reward_toast);
        addChildView(R.id.daro_ad_badge);
        addChildView(R.id.daro_ad_choice);

        InternalFriendlyObstruction[] obstructions = spyBidInterstitialView.formInterstitialObstructionsArray();

        assertEquals(9, obstructions.length);
        assertEquals(InternalFriendlyObstruction.Purpose.VIDEO_CONTROLS, obstructions[2].getPurpose());
        assertEquals("Reward progress", obstructions[2].getDetailedDescription());
        assertEquals(InternalFriendlyObstruction.Purpose.VIDEO_CONTROLS, obstructions[5].getPurpose());
        assertEquals("Sound control", obstructions[5].getDetailedDescription());
        assertEquals(InternalFriendlyObstruction.Purpose.VIDEO_CONTROLS, obstructions[6].getPurpose());
        assertEquals("Reward status", obstructions[6].getDetailedDescription());
        assertEquals(InternalFriendlyObstruction.Purpose.OTHER, obstructions[7].getPurpose());
        assertEquals("Daro ad badge", obstructions[7].getDetailedDescription());
        assertEquals(InternalFriendlyObstruction.Purpose.OTHER, obstructions[8].getPurpose());
        assertEquals("AdChoices", obstructions[8].getDetailedDescription());
    }

    @Test
    public void formInterstitialObstructionsArray_FindsDaroDialogOverlayControls() throws IllegalAccessException {
        DaroFullscreenChromeView chromeView = new DaroFullscreenChromeView(context);
        InterstitialVideo interstitialVideo = mock(InterstitialVideo.class);
        when(interstitialVideo.getDaroFullscreenChromeView()).thenReturn(chromeView);
        WhiteBox.field(InterstitialView.class, "interstitialVideo").set(spyBidInterstitialView, interstitialVideo);

        InternalFriendlyObstruction[] obstructions = spyBidInterstitialView.formInterstitialObstructionsArray();

        assertEquals(chromeView.getSkipButton(), obstructions[1].getView());
        assertEquals(chromeView.findViewById(R.id.rl_count_down), obstructions[2].getView());
        assertEquals(chromeView.getCallToActionButton(), obstructions[3].getView());
        assertEquals(chromeView.getSoundButton(), obstructions[5].getView());
        assertEquals(chromeView.getRewardToast(), obstructions[6].getView());
        assertEquals(chromeView.getFooterBadge(), obstructions[7].getView());
        assertEquals(chromeView.getAdChoiceButton(), obstructions[8].getView());
    }

    @Test
    public void hideInterstitialVideo_HidesShowingVideoWithoutClose() throws IllegalAccessException {
        InterstitialVideo interstitialVideo = mock(InterstitialVideo.class);
        when(interstitialVideo.isShowing()).thenReturn(true);
        WhiteBox.field(InterstitialView.class, "interstitialVideo").set(spyBidInterstitialView, interstitialVideo);

        spyBidInterstitialView.hideInterstitialVideo();

        verify(interstitialVideo).hide();
        verify(interstitialVideo, never()).close();
        assertEquals(null, WhiteBox.field(InterstitialView.class, "interstitialVideo").get(spyBidInterstitialView));
    }

    @Test
    public void dismissInterstitialAfterFailure_ForceDismissesVideoAndHtmlWithoutEndCardHandoff() throws IllegalAccessException {
        InterstitialVideo interstitialVideo = mock(InterstitialVideo.class);
        WhiteBox.field(InterstitialView.class, "interstitialVideo").set(spyBidInterstitialView, interstitialVideo);
        doAnswer(invocation -> {
            assertEquals(null, WhiteBox.field(InterstitialView.class, "interstitialVideo").get(spyBidInterstitialView));
            return null;
        }).when(interstitialVideo).cancel();

        spyBidInterstitialView.dismissInterstitialAfterFailure();

        verify(interstitialVideo).hide();
        verify(interstitialVideo).cancel();
        verify(interstitialVideo).removeViews();
        verify(interstitialVideo, never()).close();
        verify(mockInterstitialManager).dismissInterstitialAfterFailure();
        assertEquals(null, WhiteBox.field(InterstitialView.class, "interstitialVideo").get(spyBidInterstitialView));
    }

    @Test
    public void showHtmlAsInterstitial_UsesProvidedActivity() {
        Activity showActivity = Robolectric.buildActivity(Activity.class).create().get();
        InterstitialViewListener listener = mock(InterstitialViewListener.class);
        spyBidInterstitialView.setInterstitialViewListener(listener);

        spyBidInterstitialView.showHtmlAsInterstitial(showActivity);

        verify(mockInterstitialManager).displayAdViewInInterstitial(showActivity, spyBidInterstitialView);
        verify(listener).onAdDisplayed(spyBidInterstitialView);
    }

    private void addChildView(int id) {
        View view = new View(context);
        view.setId(id);
        spyBidInterstitialView.addView(view);
    }

}
