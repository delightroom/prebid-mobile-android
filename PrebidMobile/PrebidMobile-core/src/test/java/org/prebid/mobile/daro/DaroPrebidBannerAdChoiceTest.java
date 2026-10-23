package org.prebid.mobile.daro;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.api.rendering.VideoView;
import org.prebid.mobile.rendering.models.internal.InternalFriendlyObstruction;
import org.prebid.mobile.rendering.views.AdViewManager;
import org.prebid.mobile.test.utils.WhiteBox;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DaroPrebidBannerAdChoiceTest {
    private final Context context = RuntimeEnvironment.application;

    private DaroPrebidBannerRenderer renderer() throws Exception {
        return new DaroPrebidBannerRenderer(context, new FrameLayout(context), mock(DaroPrebidRenderListener.class));
    }

    private static InternalFriendlyObstruction outerObstruction(DaroPrebidBannerRenderer renderer) {
        AdViewManager manager = WhiteBox.getInternalState(renderer, "adViewManager");
        return WhiteBox.getInternalState(manager, "daroPersistentObstruction");
    }

    private static InternalFriendlyObstruction obstructionOf(Object holderOfAdViewManager) {
        AdViewManager manager = WhiteBox.getInternalState(holderOfAdViewManager, "adViewManager");
        return WhiteBox.getInternalState(manager, "daroPersistentObstruction");
    }

    // Minimal valid inline VAST: it passes the extractor's wrapper/ad checks synchronously, so production
    // keeps the video. The media URL is loopback only; its download is asynchronous and never observed here.
    private static final String LOCAL_INLINE_VAST =
        "<VAST version=\"3.0\"><Ad id=\"adchoice-test\"><InLine>"
            + "<AdSystem>test</AdSystem><AdTitle>adchoice</AdTitle>"
            + "<Impression><![CDATA[http://127.0.0.1:1/impression]]></Impression>"
            + "<Creatives><Creative id=\"1\"><Linear><Duration>00:00:05</Duration>"
            + "<VideoClicks><ClickThrough><![CDATA[http://127.0.0.1:1/click]]></ClickThrough></VideoClicks>"
            + "<MediaFiles><MediaFile delivery=\"progressive\" type=\"video/mp4\" width=\"320\" height=\"50\">"
            + "<![CDATA[http://127.0.0.1:1/adchoice-test.mp4]]></MediaFile></MediaFiles>"
            + "</Linear></Creative></Creatives></InLine></Ad></VAST>";

    // Runs the real renderVast and returns the VideoView production created. When production took the
    // failure path instead, the captured renderFailed error is reported rather than a bare null.
    private static VideoView renderVastAndGetVideo(
        DaroPrebidBannerRenderer renderer,
        DaroPrebidRenderListener listener
    ) {
        renderer.renderVast(LOCAL_INLINE_VAST, 320, 50);
        VideoView video = WhiteBox.getInternalState(renderer, "videoView");
        if (video == null) {
            ArgumentCaptor<AdException> error = ArgumentCaptor.forClass(AdException.class);
            verify(listener, atLeast(0)).renderFailed(error.capture());
            StringBuilder report = new StringBuilder("renderVast did not keep a video; renderFailed calls=")
                .append(error.getAllValues().size());
            for (AdException failure : error.getAllValues()) {
                report.append("\n").append(Log.getStackTraceString(failure));
            }
            fail(report.toString());
        }
        return video;
    }

    // Drives the real renderVast with the real VideoView. Nothing is idled, so the state is read before any
    // asynchronous media download, load or playback callback can run.
    @Test
    public void actualRenderVastRegistersTheExactButtonOnTheInnerVideoManagerOnly() throws Exception {
        FrameLayout container = new FrameLayout(context);
        DaroPrebidRenderListener listener = mock(DaroPrebidRenderListener.class);
        DaroPrebidBannerRenderer renderer = new DaroPrebidBannerRenderer(context, container, listener);
        View button = new View(context);
        renderer.setAdChoiceObstruction(button);
        assertNull(outerObstruction(renderer));

        VideoView video = renderVastAndGetVideo(renderer, listener);

        assertSame(video, container.getChildAt(0));
        InternalFriendlyObstruction inner = obstructionOf(video);
        assertNotNull(inner);
        assertSame(button, inner.getView());
        assertEquals("AdChoices", inner.getDetailedDescription());
        assertNull("the outer HTML manager is not the video session", outerObstruction(renderer));
        renderer.destroy();
    }

    @Test
    public void actualRenderVastThenLateButtonReachesTheInnerVideoManager() throws Exception {
        FrameLayout container = new FrameLayout(context);
        DaroPrebidRenderListener listener = mock(DaroPrebidRenderListener.class);
        DaroPrebidBannerRenderer renderer = new DaroPrebidBannerRenderer(context, container, listener);
        VideoView video = renderVastAndGetVideo(renderer, listener);
        assertNull(obstructionOf(video));
        View late = new View(context);

        renderer.setAdChoiceObstruction(late);

        assertSame(late, obstructionOf(video).getView());
        assertNull(outerObstruction(renderer));
        renderer.destroy();
    }

    @Test
    public void vastRouteHandsTheButtonToTheInnerVideoSessionNotTheOuterHtmlManager() throws Exception {
        DaroPrebidBannerRenderer renderer = renderer();
        VideoView video = mock(VideoView.class);
        WhiteBox.setInternalState(renderer, "videoView", video);
        View button = new View(context);
        View replacement = new View(context);

        renderer.setAdChoiceObstruction(button);
        renderer.setAdChoiceObstruction(replacement);

        verify(video).setDaroPersistentObstruction(button, "AdChoices");
        verify(video).setDaroPersistentObstruction(replacement, "AdChoices");
        assertNull(outerObstruction(renderer));
    }

    @Test
    public void htmlRouteHandsTheButtonToTheRenderersOwnManager() throws Exception {
        DaroPrebidBannerRenderer renderer = renderer();
        WhiteBox.setInternalState(renderer, "htmlRendering", true);
        View button = new View(context);

        renderer.setAdChoiceObstruction(button);

        assertSame(button, outerObstruction(renderer).getView());
    }

    @Test
    public void nothingIsRegisteredBeforeRenderingAfterVideoFailureOrAfterDestroy() throws Exception {
        View button = new View(context);
        DaroPrebidBannerRenderer idle = renderer();
        idle.setAdChoiceObstruction(button);
        assertNull(outerObstruction(idle));

        DaroPrebidBannerRenderer destroyed = renderer();
        VideoView video = mock(VideoView.class);
        WhiteBox.setInternalState(destroyed, "videoView", video);
        destroyed.destroy();
        destroyed.setAdChoiceObstruction(button);
        verify(video, never()).setDaroPersistentObstruction(any(), any());
        assertNull(outerObstruction(destroyed));
    }
}
