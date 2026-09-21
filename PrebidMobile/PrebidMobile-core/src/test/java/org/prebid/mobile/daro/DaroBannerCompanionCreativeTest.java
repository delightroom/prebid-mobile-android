package org.prebid.mobile.daro;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.prebid.mobile.AdSize;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.listeners.CreativeResolutionListener;
import org.prebid.mobile.rendering.parser.AdResponseParserVast;
import org.prebid.mobile.rendering.views.webview.ActionUrl;
import org.prebid.mobile.rendering.views.webview.PrebidWebViewBase;
import org.prebid.mobile.rendering.views.webview.WebViewBase;
import org.prebid.mobile.api.exceptions.AdException;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DaroBannerCompanionCreativeTest {
    private DaroBannerCompanionCreative creative(CreativeResolutionListener listener) throws Exception {
        AdUnitConfiguration config = new AdUnitConfiguration();
        config.addSize(new AdSize(320, 50));
        AdResponseParserVast parser = new AdResponseParserVast("<VAST version='4.0'><Ad><InLine><Creatives><Creative><CompanionAds>"
                + "<Companion><StaticResource creativeType='image/png'>https://example.com/test.png</StaticResource>"
                + "</Companion></CompanionAds></Creative></Creatives></InLine></Ad></VAST>");
        DaroBannerCompanionCreative creative = spy(new DaroBannerCompanionCreative(RuntimeEnvironment.application,
                DaroBannerCompanionModel.fromVast(parser, config)));
        PrebidWebViewBase view = mock(PrebidWebViewBase.class);
        when(view.getWebView()).thenReturn(mock(WebViewBase.class));
        doReturn(view).when(creative).getCreativeView();
        creative.setResolutionListener(listener);
        return creative;
    }

    private Runnable readySignal(DaroBannerCompanionCreative creative) throws Exception {
        creative.webViewReadyToDisplay();
        ArgumentCaptor<ActionUrl> actions = ArgumentCaptor.forClass(ActionUrl.class);
        verify(creative.getCreativeView(), times(2)).setActionUrl(actions.capture());
        Field action = ActionUrl.class.getDeclaredField("action");
        action.setAccessible(true);
        return (Runnable) action.get(actions.getAllValues().get(0));
    }

    @Test public void htmlReadyDoesNotResolveUntilDecodedImageSignalAndOnlyOnce() throws Exception {
        CreativeResolutionListener listener = mock(CreativeResolutionListener.class);
        DaroBannerCompanionCreative creative = creative(listener);
        Runnable ready = readySignal(creative);
        verifyNoInteractions(listener);
        assertFalse(creative.isResolved());
        ready.run();
        ready.run();
        verify(listener).creativeReady(creative);
        assertTrue(creative.isResolved());
        creative.destroy();
    }

    @Test public void lateImageSignalAfterFailureOrDestroyIsIgnored() throws Exception {
        CreativeResolutionListener listener = mock(CreativeResolutionListener.class);
        DaroBannerCompanionCreative creative = creative(listener);
        Runnable ready = readySignal(creative);
        AdException error = new AdException(AdException.INTERNAL_ERROR, "failed");
        creative.webViewFailedToLoad(error);
        ready.run();
        verify(listener).creativeFailed(error);
        verify(listener, never()).creativeReady(any());
        creative.destroy();
        ready.run();
        assertFalse(creative.isResolved());
        verifyNoMoreInteractions(listener);
    }
}
