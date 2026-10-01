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

package org.prebid.mobile.rendering.views.interstitial;

import android.app.Activity;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.core.R;
import org.prebid.mobile.rendering.interstitial.DialogEventListener;
import org.prebid.mobile.rendering.models.InterstitialDisplayPropertiesInternal;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class DaroInterstitialVideoIntegrationTest {

    @Mock private InterstitialManager interstitialManager;
    @Mock private AdUnitConfiguration adUnitConfiguration;
    @Mock private DialogEventListener dialogEventListener;

    private Activity activity;
    private FrameLayout adViewContainer;
    private InterstitialVideo interstitialVideo;
    private int legacyCallToActionClicks;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        legacyCallToActionClicks = 0;
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        adViewContainer = new FrameLayout(activity);
        when(adUnitConfiguration.isRewarded()).thenReturn(false);
        when(interstitialManager.getInterstitialDisplayProperties()).thenReturn(new InterstitialDisplayPropertiesInternal());
        interstitialVideo = new InterstitialVideo(activity, adViewContainer, interstitialManager, adUnitConfiguration);
        interstitialVideo.setDialogListener(dialogEventListener);
    }

    @Test
    public void focusPauseResumePreservesElapsedProgressMuteAndNearlyFinishedSkip() throws Exception {
        when(adUnitConfiguration.isDaroFullscreenRenderer()).thenReturn(true);
        interstitialVideo.addCloseView();
        interstitialVideo.addSoundView(true);
        interstitialVideo.scheduleAllTimers(5_000);
        android.os.CountDownTimer countdown = (android.os.CountDownTimer)
            org.prebid.mobile.test.utils.WhiteBox.field(InterstitialVideo.class, "countDownTimer").get(interstitialVideo);
        org.robolectric.Shadows.shadowOf(countdown).invokeTick(300);
        interstitialVideo.setRemainingCloseDelayInMs(300);
        DaroFullscreenChromeView chrome = interstitialVideo.getDaroFullscreenChromeView();
        float elapsedProgress = chrome.getProgressFraction();
        assertTrue(elapsedProgress > 0.8f);
        interstitialVideo.pauseVideo();
        interstitialVideo.resumeVideo();
        assertTrue(chrome.getProgressFraction() >= elapsedProgress);
        assertEquals("on", chrome.getSoundButton().getTag());
        countdown = (android.os.CountDownTimer)
            org.prebid.mobile.test.utils.WhiteBox.field(InterstitialVideo.class, "countDownTimer").get(interstitialVideo);
        org.robolectric.Shadows.shadowOf(countdown).invokeFinish();
        assertTrue(chrome.getSkipButton().isEnabled());
        assertEquals(1f, chrome.getProgressFraction(), 0f);
        interstitialVideo.pauseVideo();
    }

    @Test
    @org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
    public void queuedOnShowAfterDisposeCannotRestartTimersOrReportShown() throws Exception {
        when(adUnitConfiguration.isDaroFullscreenRenderer()).thenReturn(true);
        interstitialVideo.show();
        interstitialVideo.dispose();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        try {
            for (String field : new String[]{"timer", "countDownTimer", "skipCountDownTimer", "showCloseButtonTask"}) {
                assertEquals(null, org.prebid.mobile.test.utils.WhiteBox.field(InterstitialVideo.class, field).get(interstitialVideo));
            }
            assertEquals(null, interstitialVideo.getDaroFullscreenChromeView());
            org.mockito.Mockito.verify(interstitialManager, org.mockito.Mockito.never()).show();
            org.mockito.Mockito.verify(interstitialManager, org.mockito.Mockito.never()).interstitialDialogShown(org.mockito.Mockito.any());
            org.mockito.Mockito.verify(dialogEventListener, org.mockito.Mockito.never()).onEvent(DialogEventListener.EventType.SHOWN);
            org.mockito.Mockito.verify(dialogEventListener, org.mockito.Mockito.never()).onEvent(DialogEventListener.EventType.CLOSED);
        } finally {
            interstitialVideo.dispose();
        }
    }

    @Test
    @Config(sdk = 33)
    public void modernDialogBackCannotBypassDaroSkipGate() {
        when(adUnitConfiguration.isDaroFullscreenRenderer()).thenReturn(true);
        InterstitialVideo video = new InterstitialVideo(activity, adViewContainer, interstitialManager, adUnitConfiguration);
        video.setDialogListener(dialogEventListener);
        video.show();
        video.onBackPressed();
        assertTrue(video.isShowing());
        verify(dialogEventListener, org.mockito.Mockito.never()).onEvent(DialogEventListener.EventType.CLOSED);
        video.dispose();
    }

    @Test
    public void disposeAfterAutoCompleteReleasesWindowTimersWithoutClosingCompanion() throws Exception {
        when(adUnitConfiguration.isDaroFullscreenRenderer()).thenReturn(true);
        interstitialVideo.show();
        interstitialVideo.scheduleAllTimers(5_000);
        View companion = new View(activity);
        adViewContainer.addView(companion);
        interstitialVideo.dispose();
        assertTrue(!interstitialVideo.isShowing());
        assertSame(adViewContainer, companion.getParent());
        for (String field : new String[]{"timer", "countDownTimer", "skipCountDownTimer", "showCloseButtonTask"}) {
            assertEquals(null, org.prebid.mobile.test.utils.WhiteBox.field(InterstitialVideo.class, field).get(interstitialVideo));
        }
        verify(dialogEventListener, org.mockito.Mockito.never()).onEvent(DialogEventListener.EventType.CLOSED);
        verify(interstitialManager, org.mockito.Mockito.never()).interstitialAdClosed();
    }

    @Test
    @Config(sdk = 30)
    public void videoChromeUsesDispatchedNavigationInsetsAfterRotation() throws Exception {
        when(adUnitConfiguration.isDaroFullscreenRenderer()).thenReturn(true);
        interstitialVideo.addCloseView();
        DaroFullscreenChromeView chrome = interstitialVideo.getDaroFullscreenChromeView();
        chrome.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder()
            .setInsets(android.view.WindowInsets.Type.navigationBars(), android.graphics.Insets.of(0, 0, 0, 34)).build());
        assertEquals(34, org.prebid.mobile.test.utils.WhiteBox.field(DaroFullscreenChromeView.class, "safeBottomPx").get(chrome));
        chrome.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder()
            .setInsets(android.view.WindowInsets.Type.navigationBars(), android.graphics.Insets.of(0, 0, 48, 0)).build());
        assertEquals(48, org.prebid.mobile.test.utils.WhiteBox.field(DaroFullscreenChromeView.class, "safeRightPx").get(chrome));
        assertEquals(0, org.prebid.mobile.test.utils.WhiteBox.field(DaroFullscreenChromeView.class, "safeBottomPx").get(chrome));
    }

    @Test
    public void addFullscreenControls_AttachesDaroChromeOnceAndBindsControls() {
        interstitialVideo.addCloseView();
        interstitialVideo.addSoundView(true);
        interstitialVideo.addSkipView();

        DaroFullscreenChromeView chromeView = interstitialVideo.getDaroFullscreenChromeView();

        assertNotNull(chromeView);
        assertSame(chromeView.getSoundButton(), chromeView.findViewById(R.id.iv_sound_interstitial));
        assertSame(chromeView.getSkipButton(), chromeView.findViewById(R.id.iv_skip));
        assertTrue(chromeView.getSoundButton().performClick());
        verify(dialogEventListener).onEvent(DialogEventListener.EventType.UNMUTE);

        chromeView.showSkipAvailable();
        assertTrue(chromeView.getSkipButton().performClick());
        verify(interstitialManager).interstitialAdClosed();
    }

    @Test
    public void addFullscreenControls_DoesNotCreateLegacySiblingControls() {
        interstitialVideo.addCloseView();
        interstitialVideo.addSoundView(false);
        interstitialVideo.addSkipView();

        DaroFullscreenChromeView chromeView = interstitialVideo.getDaroFullscreenChromeView();

        assertNotNull(chromeView);
        assertEquals(null, adViewContainer.findViewById(R.id.daro_fullscreen_chrome));
        assertSame(chromeView.getSkipButton(), chromeView.findViewById(R.id.iv_skip));
        assertEquals(View.GONE, chromeView.getSkipButton().getVisibility());
    }

    @Test
    public void addFullscreenControls_AttachesDaroChromeAsDialogOverlay() {
        interstitialVideo.addCloseView();

        DaroFullscreenChromeView chromeView = interstitialVideo.getDaroFullscreenChromeView();
        ViewParent parent = chromeView.getParent();

        assertNotNull(parent);
        assertTrue(parent != adViewContainer);
        assertEquals(1000f, chromeView.getElevation(), 0.001f);
        assertEquals(1000f, chromeView.getTranslationZ(), 0.001f);
    }

    @Test
    public void addFullscreenControls_RebindsLegacyCallToActionToDaroButton() {
        View legacyCallToAction = new View(activity);
        legacyCallToAction.setId(R.id.tv_learn_more);
        legacyCallToAction.setVisibility(View.VISIBLE);
        legacyCallToAction.setOnClickListener(v -> legacyCallToActionClicks++);
        adViewContainer.addView(legacyCallToAction);

        interstitialVideo.addCloseView();

        DaroFullscreenChromeView chromeView = interstitialVideo.getDaroFullscreenChromeView();

        assertNotNull(chromeView);
        assertSame(chromeView.getCallToActionButton(), chromeView.findViewById(R.id.tv_learn_more));
        assertEquals(View.NO_ID, legacyCallToAction.getId());
        assertEquals(View.GONE, legacyCallToAction.getVisibility());
        assertEquals(View.VISIBLE, chromeView.getCallToActionButton().getVisibility());

        assertTrue(chromeView.getCallToActionButton().performClick());
        assertEquals(1, legacyCallToActionClicks);
    }

    @Test
    public void addFullscreenControls_DaroRewardedWithEndCardShowsLearnMore() {
        when(adUnitConfiguration.isRewarded()).thenReturn(true);
        when(adUnitConfiguration.isDaroFullscreenRenderer()).thenReturn(true);
        interstitialVideo = new InterstitialVideo(activity, adViewContainer, interstitialManager, adUnitConfiguration);
        interstitialVideo.setDialogListener(dialogEventListener);
        interstitialVideo.setHasEndCard(true);

        View legacyCallToAction = new View(activity);
        legacyCallToAction.setId(R.id.tv_learn_more);
        legacyCallToAction.setVisibility(View.GONE);
        legacyCallToAction.setOnClickListener(v -> legacyCallToActionClicks++);
        adViewContainer.addView(legacyCallToAction);

        interstitialVideo.addCloseView();

        DaroFullscreenChromeView chromeView = interstitialVideo.getDaroFullscreenChromeView();

        assertNotNull(chromeView);
        assertEquals(View.VISIBLE, chromeView.getCallToActionButton().getVisibility());
        assertEquals("Learn More", chromeView.getCallToActionLabel().getText().toString());
        assertTrue(chromeView.getCallToActionButton().performClick());
        assertEquals(1, legacyCallToActionClicks);
    }

    @Test
    public void handleDialogShow_BindsDaroSoundEvenWhenPrebidSoundFlagIsDisabled() {
        InterstitialDisplayPropertiesInternal properties = new InterstitialDisplayPropertiesInternal();
        properties.isSoundButtonVisible = false;
        properties.isMuted = true;
        when(interstitialManager.getInterstitialDisplayProperties()).thenReturn(properties);

        interstitialVideo.handleDialogShow();

        DaroFullscreenChromeView chromeView = interstitialVideo.getDaroFullscreenChromeView();

        assertNotNull(chromeView);
        assertEquals(View.VISIBLE, chromeView.getSoundButton().getVisibility());
        assertEquals("on", chromeView.getSoundButton().getTag());
        assertTrue(chromeView.getSoundButton().performClick());
        verify(dialogEventListener).onEvent(DialogEventListener.EventType.UNMUTE);
    }
}
