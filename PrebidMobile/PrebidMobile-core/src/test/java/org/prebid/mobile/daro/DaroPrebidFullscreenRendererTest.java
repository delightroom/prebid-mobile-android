package org.prebid.mobile.daro;

import org.junit.Test;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.configuration.AdUnitConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DaroPrebidFullscreenRendererTest {

    @Test
    public void createVastAdConfiguration_UsesDaroSkipDelayFallback() {
        AdUnitConfiguration configuration = DaroPrebidFullscreenRenderer.createVastAdConfiguration(320, 480, true);

        assertEquals(DaroPrebidFullscreenRenderer.DEFAULT_DARO_SKIP_DELAY_SECONDS, configuration.getSkipDelay());
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

}
