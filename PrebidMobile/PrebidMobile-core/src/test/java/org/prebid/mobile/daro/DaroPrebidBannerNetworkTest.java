package org.prebid.mobile.daro;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.prebid.mobile.api.rendering.VideoView;
import org.prebid.mobile.test.utils.WhiteBox;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Method;

import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 28})
public class DaroPrebidBannerNetworkTest {
    @Test
    public void lostDefaultNetworkStopsPlaybackEvenWhileActiveNetworkQueryIsStale() throws Exception {
        Context context = spy(RuntimeEnvironment.application);
        ConnectivityManager connectivity = mock(ConnectivityManager.class);
        Network network = mock(Network.class);
        NetworkCapabilities wifi = mock(NetworkCapabilities.class);
        when(wifi.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivity);
        when(connectivity.getActiveNetwork()).thenReturn(network);
        when(connectivity.getNetworkCapabilities(network)).thenReturn(wifi);
        DaroPrebidBannerRenderer renderer = new DaroPrebidBannerRenderer(
            context, new FrameLayout(context), mock(DaroPrebidRenderListener.class));
        VideoView video = mock(VideoView.class);
        WhiteBox.setInternalState(renderer, "videoView", video);
        Method observe = DaroPrebidBannerRenderer.class.getDeclaredMethod("observeNetwork");
        observe.setAccessible(true);
        observe.invoke(renderer);
        ArgumentCaptor<ConnectivityManager.NetworkCallback> callbacks =
            ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            verify(connectivity).registerDefaultNetworkCallback(callbacks.capture());
        } else {
            verify(connectivity).registerNetworkCallback(any(NetworkRequest.class), callbacks.capture());
        }
        verify(video).setPlaybackAllowed(true);
        clearInvocations(video);

        callbacks.getValue().onLost(network);
        ShadowLooper.idleMainLooper();
        verify(video).setPlaybackAllowed(false);
        verify(video, never()).setPlaybackAllowed(true);

        callbacks.getValue().onCapabilitiesChanged(network, wifi);
        ShadowLooper.idleMainLooper();
        verify(video).setPlaybackAllowed(true);
        renderer.destroy();
        verify(connectivity).unregisterNetworkCallback(callbacks.getValue());
    }
}
