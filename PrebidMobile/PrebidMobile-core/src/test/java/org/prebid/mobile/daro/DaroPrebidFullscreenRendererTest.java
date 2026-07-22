package org.prebid.mobile.daro;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.api.rendering.InterstitialView;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialViewListener;
import org.prebid.mobile.rendering.models.AdDetails;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class DaroPrebidFullscreenRendererTest {

    @Test
    public void createVastAdConfiguration_UsesDaroSkipDelayFallback() {
        AdUnitConfiguration configuration = DaroPrebidFullscreenRenderer.createVastAdConfiguration(320, 480, true);

        assertEquals(DaroPrebidFullscreenRenderer.DEFAULT_DARO_SKIP_DELAY_SECONDS, configuration.getSkipDelay());
        assertFalse(configuration.isMuted());
        assertTrue(configuration.isRewarded());
        assertTrue(configuration.isDaroFullscreenRenderer());
        assertTrue(configuration.getAdFormats().contains(AdFormat.VAST));
    }

    @Test
    public void createHtmlAdConfiguration_UsesDaroSkipDelayFallback() {
        AdUnitConfiguration configuration = DaroPrebidFullscreenRenderer.createHtmlAdConfiguration(320, 480, false);

        assertEquals(DaroPrebidFullscreenRenderer.DEFAULT_DARO_SKIP_DELAY_SECONDS, configuration.getSkipDelay());
        assertTrue(configuration.isDaroFullscreenRenderer());
        assertTrue(configuration.getAdFormats().contains(AdFormat.INTERSTITIAL));
    }

    @Test
    public void createVastAdConfiguration_UsesCustomSkipDelay() {
        AdUnitConfiguration configuration = DaroPrebidFullscreenRenderer.createVastAdConfiguration(320, 480, true, 8);

        assertEquals(8, configuration.getSkipDelay());
        assertTrue(configuration.isDaroFullscreenRenderer());
    }

    @Test
    public void createVastAdConfiguration_UsesInitialMuted() {
        AdUnitConfiguration configuration = DaroPrebidFullscreenRenderer.createVastAdConfiguration(320, 480, true, 8, true);

        assertTrue(configuration.isMuted());
        assertEquals(8, configuration.getSkipDelay());
    }

    @Test
    public void createHtmlAdConfiguration_UsesCustomSkipDelay() {
        AdUnitConfiguration configuration = DaroPrebidFullscreenRenderer.createHtmlAdConfiguration(320, 480, false, 9);

        assertEquals(9, configuration.getSkipDelay());
        assertTrue(configuration.isDaroFullscreenRenderer());
    }

    @Test
    public void createHtmlAdConfiguration_UsesInitialMuted() {
        AdUnitConfiguration configuration = DaroPrebidFullscreenRenderer.createHtmlAdConfiguration(320, 480, false, 9, true);

        assertTrue(configuration.isMuted());
        assertEquals(9, configuration.getSkipDelay());
    }

    @Test
    public void showWithContext_ForwardsShowActivityToVideoDisplay() {
        InterstitialView interstitialView = mock(InterstitialView.class);
        DaroPrebidRenderListener renderListener = mock(DaroPrebidRenderListener.class);
        DaroPrebidFullscreenRenderer renderer = loadedRenderer(interstitialView, renderListener);
        Activity showActivity = Robolectric.buildActivity(Activity.class).create().get();

        renderer.show(new ContextWrapper(showActivity));

        verify(interstitialView).showVideoAsInterstitial(showActivity);
        verify(interstitialView, never()).showVideoAsInterstitial();
    }

    @Test
    public void showWithContext_ForwardsShowActivityToHtmlDisplay() {
        InterstitialView interstitialView = mock(InterstitialView.class);
        DaroPrebidRenderListener renderListener = mock(DaroPrebidRenderListener.class);
        DaroPrebidFullscreenRenderer renderer = renderer(interstitialView, renderListener);
        renderer.renderHtml("<html></html>", 320, 480, false);
        notifyLoaded(interstitialView);
        Activity showActivity = Robolectric.buildActivity(Activity.class).create().get();

        renderer.show(showActivity);

        verify(interstitialView).showHtmlAsInterstitial(showActivity);
        verify(interstitialView, never()).showHtmlAsInterstitial();
    }

    @Test
    public void showWithContext_RejectsNonActivityContext() {
        InterstitialView interstitialView = mock(InterstitialView.class);
        DaroPrebidRenderListener renderListener = mock(DaroPrebidRenderListener.class);
        DaroPrebidFullscreenRenderer renderer = loadedRenderer(interstitialView, renderListener);
        Context applicationContext = RuntimeEnvironment.getApplication();

        renderer.show(applicationContext);

        ArgumentCaptor<AdException> errorCaptor = ArgumentCaptor.forClass(AdException.class);
        verify(renderListener).renderFailed(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("active Activity"));
        verify(interstitialView, never()).showVideoAsInterstitial();
    }

    @Test
    public void showWithContext_RejectsDestroyedLoadActivityBeforeDisplay() {
        InterstitialView interstitialView = mock(InterstitialView.class);
        DaroPrebidRenderListener renderListener = mock(DaroPrebidRenderListener.class);
        org.robolectric.android.controller.ActivityController<Activity> loadController =
            Robolectric.buildActivity(Activity.class).create();
        Activity loadActivity = loadController.get();
        when(interstitialView.getContext()).thenReturn(loadActivity);
        DaroPrebidFullscreenRenderer renderer = new DaroPrebidFullscreenRenderer(interstitialView, renderListener);
        notifyLoaded(interstitialView);
        loadController.destroy();
        assertTrue(loadActivity.isDestroyed());
        Activity showActivity = Robolectric.buildActivity(Activity.class).create().get();

        renderer.show(showActivity);

        ArgumentCaptor<AdException> errorCaptor = ArgumentCaptor.forClass(AdException.class);
        verify(renderListener).renderFailed(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("load Activity is no longer active"));
        verify(interstitialView, never()).showVideoAsInterstitial(showActivity);
        verify(interstitialView, never()).showVideoAsInterstitial();
    }

    @Test
    public void activityFromContext_UnwrapsActiveActivity() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();

        Activity resolved = DaroPrebidFullscreenRenderer.activityFromContext(new ContextWrapper(activity));

        assertSame(activity, resolved);
    }

    @Test
    public void activityFromContext_RejectsFinishingActivity() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.finish();

        assertNull(DaroPrebidFullscreenRenderer.activityFromContext(activity));
    }

    @Test
    public void constructor_RejectsApplicationContext() {
        try {
            new DaroPrebidFullscreenRenderer(
                RuntimeEnvironment.getApplication(),
                mock(DaroPrebidRenderListener.class)
            );
            fail("Expected an AdException");
        } catch (AdException error) {
            assertTrue(error.getMessage().contains("active Activity context"));
        }
    }

    @Test
    public void showWithoutContext_PreservesOriginalDisplayPath() {
        InterstitialView interstitialView = mock(InterstitialView.class);
        DaroPrebidRenderListener renderListener = mock(DaroPrebidRenderListener.class);
        DaroPrebidFullscreenRenderer renderer = loadedRenderer(interstitialView, renderListener);

        renderer.show();

        verify(interstitialView).showVideoAsInterstitial();
    }

    @Test
    public void postDisplayFailure_ReportsFailureClosesVideoAndSuppressesLateCallbacks() {
        InterstitialView interstitialView = mock(InterstitialView.class);
        DaroPrebidRenderListener renderListener = mock(DaroPrebidRenderListener.class);
        DaroPrebidFullscreenRenderer renderer = renderer(interstitialView, renderListener);
        InterstitialViewListener prebidListener = notifyLoaded(interstitialView);
        AdException error = new AdException(AdException.INTERNAL_ERROR, "playback failed");

        prebidListener.onAdDisplayed(interstitialView);
        prebidListener.onAdFailed(interstitialView, error);
        prebidListener.onAdFailed(interstitialView, error);
        prebidListener.onAdCompleted(interstitialView);
        prebidListener.onAdClicked(interstitialView);
        shadowOf(Looper.getMainLooper()).idle();

        verify(renderListener).renderStarted();
        verify(renderListener).impression();
        verify(renderListener).renderFailed(error);
        verify(interstitialView).dismissInterstitialAfterFailure();
        verify(renderListener).closed();
        verify(renderListener, never()).videoCompleted();
        verify(renderListener, never()).click();
    }

    @Test
    public void postDisplayHtmlFailure_DismissesInterstitialAndCloses() {
        InterstitialView interstitialView = mock(InterstitialView.class);
        DaroPrebidRenderListener renderListener = mock(DaroPrebidRenderListener.class);
        DaroPrebidFullscreenRenderer renderer = renderer(interstitialView, renderListener);
        renderer.renderHtml("<html></html>", 320, 480, false);
        InterstitialViewListener prebidListener = notifyLoaded(interstitialView);
        Activity showActivity = Robolectric.buildActivity(Activity.class).create().get();
        AdException error = new AdException(AdException.INTERNAL_ERROR, "web view failed");

        renderer.show(showActivity);
        prebidListener.onAdDisplayed(interstitialView);
        prebidListener.onAdFailed(interstitialView, error);
        shadowOf(Looper.getMainLooper()).idle();

        verify(renderListener).renderFailed(error);
        verify(interstitialView).dismissInterstitialAfterFailure();
        verify(renderListener).closed();
    }

    private DaroPrebidFullscreenRenderer loadedRenderer(
        InterstitialView interstitialView,
        DaroPrebidRenderListener renderListener
    ) {
        DaroPrebidFullscreenRenderer renderer = renderer(interstitialView, renderListener);
        notifyLoaded(interstitialView);
        return renderer;
    }

    private DaroPrebidFullscreenRenderer renderer(
        InterstitialView interstitialView,
        DaroPrebidRenderListener renderListener
    ) {
        Activity loadActivity = Robolectric.buildActivity(Activity.class).create().get();
        when(interstitialView.getContext()).thenReturn(loadActivity);
        return new DaroPrebidFullscreenRenderer(interstitialView, renderListener);
    }

    private InterstitialViewListener notifyLoaded(InterstitialView interstitialView) {
        ArgumentCaptor<InterstitialViewListener> listenerCaptor = ArgumentCaptor.forClass(InterstitialViewListener.class);
        verify(interstitialView).setInterstitialViewListener(listenerCaptor.capture());
        listenerCaptor.getValue().onAdLoaded(interstitialView, mock(AdDetails.class));
        return listenerCaptor.getValue();
    }

}
