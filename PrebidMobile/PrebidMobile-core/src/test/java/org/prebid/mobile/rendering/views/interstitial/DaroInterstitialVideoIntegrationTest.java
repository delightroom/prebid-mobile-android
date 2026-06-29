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
