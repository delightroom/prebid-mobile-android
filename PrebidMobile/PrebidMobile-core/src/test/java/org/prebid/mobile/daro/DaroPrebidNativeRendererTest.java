package org.prebid.mobile.daro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;

import org.prebid.mobile.PrebidNativeAdEventListener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class DaroPrebidNativeRendererTest {

    @Test
    public void createNativeAd_extractsNativeVideoVastTag() {
        DaroPrebidNativeAd ad = new DaroPrebidNativeRenderer().createNativeAd(nativeVideoAdm(), "1.23");

        assertNotNull(ad);
        assertNotNull(ad.getMedia());
        assertEquals("<VAST price='1.23'></VAST>", ad.getMedia().getData());
    }

    @Test
    public void nativeMedia_createViewAndDestroyView() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        DaroPrebidNativeMedia media = new DaroPrebidNativeMedia("<VAST version='3.0'></VAST>");

        View view = media.createView(activity, () -> {});

        assertNotNull(view);
        assertTrue(view instanceof DaroPrebidNativeMediaView);

        media.destroyView(view);
        media.destroyView(view);
    }

    @Test
    public void nativeAd_unbindClearsViewListenersAndAllowsRebind() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        View container = new View(activity);
        View clickable = new View(activity);
        DaroPrebidNativeAd ad = new DaroPrebidNativeRenderer().createNativeAd(nativeVideoAdm(), "1.23");

        assertNotNull(ad);
        assertTrue(ad.bind(container, Arrays.asList(clickable), null));
        assertTrue(container.hasOnClickListeners());
        assertTrue(clickable.hasOnClickListeners());

        ad.unbind();

        assertTrue(!container.hasOnClickListeners());
        assertTrue(!clickable.hasOnClickListeners());
        assertTrue(ad.bind(container, Arrays.asList(clickable), null));
        assertTrue(container.hasOnClickListeners());
        assertTrue(clickable.hasOnClickListeners());

        ad.destroy();

        assertTrue(!container.hasOnClickListeners());
        assertTrue(!clickable.hasOnClickListeners());
    }

    @Test
    public void nativeAd_doesNotRebindAfterImpression() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        View container = new View(activity);
        View clickable = new View(activity);
        DaroPrebidNativeAd ad = new DaroPrebidNativeRenderer().createNativeAd(nativeVideoAdm(), "1.23");

        assertNotNull(ad);
        assertTrue(ad.bind(container, Arrays.asList(clickable), null));

        registeredListener(ad).onAdBecameViewable();

        assertFalse(ad.bind(container, Arrays.asList(clickable), null));
        assertFalse(container.hasOnClickListeners());
        assertFalse(clickable.hasOnClickListeners());
    }

    private PrebidNativeAdEventListener registeredListener(DaroPrebidNativeAd ad) throws Exception {
        Field nativeAdField = DaroPrebidNativeAd.class.getDeclaredField("nativeAd");
        nativeAdField.setAccessible(true);
        Object nativeAd = nativeAdField.get(ad);
        Field listenerField = nativeAd.getClass().getDeclaredField("listener");
        listenerField.setAccessible(true);
        return (PrebidNativeAdEventListener) listenerField.get(nativeAd);
    }

    private String nativeVideoAdm() {
        return "{"
                + "\"native\":{"
                + "\"assets\":["
                + "{\"id\":1,\"title\":{\"text\":\"Video Ad\"}},"
                + "{\"id\":5,\"video\":{\"vasttag\":\"<VAST price='{AUCTION_PRICE}'></VAST>\"}}"
                + "],"
                + "\"link\":{\"url\":\"https://example.com/click\"}"
                + "}"
                + "}";
    }
}
