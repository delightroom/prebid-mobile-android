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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

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
    public void renderVast_PassesClickThroughUrlToAdConfiguration() {
        InterstitialView interstitialView = mock(InterstitialView.class);
        DaroPrebidFullscreenRenderer renderer = renderer(
            interstitialView,
            mock(DaroPrebidRenderListener.class)
        );
        String clickThroughUrl = "deeplink+://navigate?primaryUrl=lazada%3A%2F%2Fproduct%2F1"
                                 + "&fallbackUrl=https%3A%2F%2Fexample.com%2Ffallback";

        renderer.setClickThroughUrl(clickThroughUrl);
        renderer.renderVast("<VAST></VAST>", 320, 480, false);

        ArgumentCaptor<AdUnitConfiguration> configurationCaptor =
            ArgumentCaptor.forClass(AdUnitConfiguration.class);
        verify(interstitialView).loadVastAd(configurationCaptor.capture(), eq("<VAST></VAST>"));
        assertEquals(clickThroughUrl, configurationCaptor.getValue().getDaroClickThroughUrl());
    }

    @Test
    public void showWithContext_ForwardsShowActivityToVideoDisplay() {
        InterstitialView interstitialView = mock(InterstitialView.class);
        DaroPrebidRenderListener renderListener = mock(DaroPrebidRenderListener.class);
        DaroPrebidFullscreenRenderer renderer = loadedRenderer(interstitialView, renderListener);
        Activity showActivity = Robolectric.buildActivity(Activity.class).create().get();

        renderer.show(new ContextWrapper(showActivity));

        Activity presentation = startPresentation(showActivity);
        verify(interstitialView).showVideoAsInterstitial(presentation);
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

        Activity presentation = startPresentation(showActivity);
        verify(interstitialView).showHtmlAsInterstitial(presentation);
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

        Activity presentation = startPresentation((Activity) interstitialView.getContext());
        verify(interstitialView).showVideoAsInterstitial(presentation);
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

    @Test
    public void portraitRestrictionAndRecreatedPublisherKeepSamePresentationAndCallbacks() {
        org.robolectric.android.controller.ActivityController<Activity> hostController =
            Robolectric.buildActivity(Activity.class).setup();
        Activity host = hostController.get();
        host.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        InterstitialView view = mock(InterstitialView.class);
        DaroPrebidRenderListener callbacks = mock(DaroPrebidRenderListener.class);
        when(view.getContext()).thenReturn(host);
        DaroPrebidFullscreenRenderer renderer = new DaroPrebidFullscreenRenderer(view, callbacks);
        InterstitialViewListener events = notifyLoaded(view);
        renderer.show(host);
        DaroPrebidFullscreenActivity presentation = startPresentation(host);
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, presentation.getRequestedOrientation());
        hostController.recreate();
        android.content.res.Configuration landscape = new android.content.res.Configuration(presentation.getResources().getConfiguration());
        landscape.orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        presentation.onConfigurationChanged(landscape);
        events.onAdDisplayed(view);
        events.onAdDisplayed(view);
        events.onAdCompleted(view);
        events.onAdCompleted(view);
        events.onAdClosed(view);
        events.onAdClosed(view);
        verify(view).showVideoAsInterstitial(presentation);
        verify(view, never()).destroy();
        verify(callbacks).renderStarted();
        verify(callbacks).impression();
        verify(callbacks).videoCompleted();
        verify(callbacks).closed();
        assertTrue(presentation.isFinishing());
    }

    @Test
    public void rewardEarnedBeforePublisherRecreationRemainsEarnedWithoutReplayingCallback() {
        org.robolectric.android.controller.ActivityController<Activity> hostController =
            Robolectric.buildActivity(Activity.class).setup();
        Activity host = hostController.get();
        InterstitialView view = mock(InterstitialView.class);
        DaroPrebidRenderListener callbacks = mock(DaroPrebidRenderListener.class);
        when(view.getContext()).thenReturn(host);
        DaroPrebidFullscreenRenderer renderer = new DaroPrebidFullscreenRenderer(view, callbacks);
        renderer.renderVast("<VAST></VAST>", 390, 844, true);
        ArgumentCaptor<AdUnitConfiguration> config = ArgumentCaptor.forClass(AdUnitConfiguration.class);
        verify(view).loadVastAd(config.capture(), eq("<VAST></VAST>"));
        notifyLoaded(view);
        renderer.show(host);
        Activity presentation = startPresentation(host);
        config.getValue().getRewardManager().notifyRewardListener();
        hostController.recreate();
        presentation.onConfigurationChanged(new android.content.res.Configuration(presentation.getResources().getConfiguration()));
        config.getValue().getRewardManager().notifyRewardListener();
        assertTrue(config.getValue().getRewardManager().getUserRewardedAlready());
        verify(callbacks).rewardEarned("reward", 1);
        verify(view).showVideoAsInterstitial(presentation);
        renderer.destroy();
    }

    @Test
    @org.robolectric.annotation.Config(sdk = 33)
    public void modernBackCallbackPreservesCreativeGateAndIsRemovedOnDestroy() throws Exception {
        InterstitialView view = mock(InterstitialView.class);
        DaroPrebidRenderListener callbacks = mock(DaroPrebidRenderListener.class);
        DaroPrebidFullscreenRenderer renderer = loadedRenderer(view, callbacks);
        Activity host = (Activity) view.getContext();
        renderer.show(host);
        android.content.Intent intent = shadowOf(host).getNextStartedActivity();
        org.robolectric.android.controller.ActivityController<DaroPrebidFullscreenActivity> controller =
            Robolectric.buildActivity(DaroPrebidFullscreenActivity.class, intent).setup();
        DaroPrebidFullscreenActivity presentation = controller.get();
        java.lang.reflect.Field field = DaroPrebidFullscreenActivity.class.getDeclaredField("backCallback");
        field.setAccessible(true);
        ((android.window.OnBackInvokedCallback) field.get(presentation)).onBackInvoked();
        presentation.onBackPressed();
        assertTrue(!presentation.isFinishing());
        verify(callbacks, never()).closed();
        controller.pause().stop().destroy();
        assertEquals(null, field.get(presentation));
        verify(callbacks).closed();
    }

    @Test
    public void unhandledPresentationRecreationReleasesSessionAndClosesExactlyOnce() {
        InterstitialView view = mock(InterstitialView.class);
        DaroPrebidRenderListener callbacks = mock(DaroPrebidRenderListener.class);
        DaroPrebidFullscreenRenderer renderer = loadedRenderer(view, callbacks);
        Activity host = (Activity) view.getContext();
        renderer.show(host);
        android.content.Intent intent = shadowOf(host).getNextStartedActivity();
        org.robolectric.android.controller.ActivityController<DaroPrebidFullscreenActivity> presentation =
            Robolectric.buildActivity(DaroPrebidFullscreenActivity.class, intent).setup();
        Activity original = presentation.get();
        presentation.recreate();
        verify(view).showVideoAsInterstitial(original);
        verify(view).dismissInterstitialAfterFailure();
        verify(callbacks).closed();
        assertTrue(presentation.get().isFinishing());
        DaroPrebidFullscreenActivity stale = Robolectric.buildActivity(DaroPrebidFullscreenActivity.class, intent).setup().get();
        assertTrue(stale.isFinishing());
        renderer.destroy();
        verify(callbacks).closed();
    }

    @Test
    public void unspecifiedRuntimeOrientationFallsBackToPortraitManifestRestriction() throws Exception {
        Activity host = mock(Activity.class);
        android.content.pm.PackageManager packages = mock(android.content.pm.PackageManager.class);
        android.content.ComponentName component = new android.content.ComponentName("publisher", "PublisherActivity");
        android.content.pm.ActivityInfo info = new android.content.pm.ActivityInfo();
        info.screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        when(host.getRequestedOrientation()).thenReturn(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        when(host.getPackageManager()).thenReturn(packages);
        when(host.getComponentName()).thenReturn(component);
        when(packages.getActivityInfo(component, 0)).thenReturn(info);
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            DaroPrebidFullscreenActivity.hostOrientation(host));
        when(host.getRequestedOrientation()).thenReturn(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            DaroPrebidFullscreenActivity.hostOrientation(host));
    }

    @Test
    public void presentationLaunchFailureReportsOnceAndSuppressesLateCompletion() {
        InterstitialView view = mock(InterstitialView.class);
        DaroPrebidRenderListener callbacks = mock(DaroPrebidRenderListener.class);
        DaroPrebidFullscreenRenderer renderer = loadedRenderer(view, callbacks);
        Activity host = mock(Activity.class);
        when(host.getRequestedOrientation()).thenReturn(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        org.mockito.Mockito.doThrow(new IllegalStateException("launch failed"))
            .when(host).startActivity(org.mockito.ArgumentMatchers.any(android.content.Intent.class));
        renderer.show(host);
        ArgumentCaptor<AdException> failure = ArgumentCaptor.forClass(AdException.class);
        verify(callbacks).renderFailed(failure.capture());
        assertTrue(failure.getValue().getMessage().contains("launch failed"));
        ArgumentCaptor<InterstitialViewListener> events = ArgumentCaptor.forClass(InterstitialViewListener.class);
        verify(view).setInterstitialViewListener(events.capture());
        events.getValue().onAdCompleted(view);
        verify(callbacks, never()).videoCompleted();
    }

    private DaroPrebidFullscreenActivity startPresentation(Activity host) {
        android.content.Intent intent = shadowOf(host).getNextStartedActivity();
        assertEquals(DaroPrebidFullscreenActivity.class.getName(), intent.getComponent().getClassName());
        return Robolectric.buildActivity(DaroPrebidFullscreenActivity.class, intent).setup().get();
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
