package org.prebid.mobile.daro;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.api.rendering.VideoView;
import org.prebid.mobile.rendering.models.internal.InternalFriendlyObstruction;
import org.prebid.mobile.rendering.views.AdViewManager;
import org.prebid.mobile.test.utils.WhiteBox;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
