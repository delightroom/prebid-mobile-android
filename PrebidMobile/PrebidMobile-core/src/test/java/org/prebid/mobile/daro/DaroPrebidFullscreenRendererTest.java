package org.prebid.mobile.daro;

import org.junit.Test;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.configuration.AdUnitConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

}
