package org.prebid.mobile;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.prebid.mobile.rendering.utils.helpers.ExternalViewerUtils;
import org.prebid.mobile.rendering.utils.url.action.DeepLinkPlusAction;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;
import java.lang.reflect.Method;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DirectNativeDeeplinkTest {
    @Test
    public void nativeClickUsesPrimaryAndNotOriginalAndReportsOnce() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        PrebidNativeAd ad = PrebidNativeAd.createForExternalOwner(
            "{\"native\":{\"assets\":[],\"link\":{\"url\":\"https://example.com/original\"}}}", null);
        assertNotNull(ad);
        ad.setDaroClickThroughUrl("deeplink+://navigate?primaryUrl=sampleapp%3A%2F%2Fitem");
        PrebidNativeAdEventListener listener = mock(PrebidNativeAdEventListener.class);
        try (MockedStatic<ExternalViewerUtils> viewer = mockStatic(ExternalViewerUtils.class)) {
            Method click = PrebidNativeAd.class.getDeclaredMethod("handleClick", View.class, PrebidNativeAdEventListener.class);
            click.setAccessible(true);
            click.invoke(ad, new View(context), listener);
            viewer.verify(() -> ExternalViewerUtils.launchApplicationUrl(context, Uri.parse("sampleapp://item")), times(1));
            viewer.verifyNoMoreInteractions();
            verify(listener, times(1)).onAdClicked();
            verifyNoMoreInteractions(listener);
        }
    }

    @Test
    public void primaryFailureUsesOriginalOnceAndUnbindSuppressesCompletion() throws Exception {
        for (boolean unbind : new boolean[] {false, true}) {
            Context context = RuntimeEnvironment.getApplication();
            PrebidNativeAd ad = PrebidNativeAd.createForExternalOwner(
                "{\"native\":{\"assets\":[],\"link\":{\"url\":\"https://example.com/original\"}}}", null);
            ad.setDaroClickThroughUrl("deeplink+://navigate?primaryUrl=sampleapp%3A%2F%2Fitem");
            PrebidNativeAdEventListener listener = mock(PrebidNativeAdEventListener.class);
            java.util.concurrent.atomic.AtomicReference<org.prebid.mobile.rendering.mraid.methods.network.UrlResolutionTask.UrlResolutionListener> pending = new java.util.concurrent.atomic.AtomicReference<>();
            try (MockedStatic<ExternalViewerUtils> viewer = mockStatic(ExternalViewerUtils.class);
                 org.mockito.MockedConstruction<org.prebid.mobile.rendering.mraid.methods.network.UrlResolutionTask> resolution = mockConstruction(
                     org.prebid.mobile.rendering.mraid.methods.network.UrlResolutionTask.class,
                     (mock, construction) -> pending.set((org.prebid.mobile.rendering.mraid.methods.network.UrlResolutionTask.UrlResolutionListener) construction.arguments().get(0)))) {
                viewer.when(() -> ExternalViewerUtils.launchApplicationUrl(context, Uri.parse("sampleapp://item")))
                    .thenThrow(new org.prebid.mobile.rendering.utils.url.ActionNotResolvedException("not installed"));
                Method click = PrebidNativeAd.class.getDeclaredMethod("handleClick", View.class, PrebidNativeAdEventListener.class);
                click.setAccessible(true);
                click.invoke(ad, new View(context), listener);
                verify(listener, never()).onAdClicked();
                assertNotNull(pending.get());
                if (unbind) ad.daroUnregisterViewFromTracking();
                pending.get().onSuccess("https://example.com/original");
                verify(listener, times(unbind ? 0 : 1)).onAdClicked();
                viewer.verify(() -> ExternalViewerUtils.startActivity(eq(context), argThat(intent ->
                    "https://example.com/original".equals(intent.getDataString()))), times(unbind ? 0 : 1));
            }
        }
    }

    @Test
    public void originalFallbackIsEncodedAndExplicitFallbackIsPreserved() {
        String primary = "deeplink+://navigate?primaryUrl=sampleapp%3A%2F%2Fitem";
        String original = "https://example.com/original?a=1&b=2";
        String result = DeepLinkPlusAction.withOriginalFallback(primary, original);
        assertEquals(original, Uri.parse(result).getQueryParameter("fallbackUrl"));
        assertEquals(result, DeepLinkPlusAction.withOriginalFallback(result, "https://other.example"));
        assertEquals(original, DeepLinkPlusAction.withOriginalFallback(null, original));
    }
}
