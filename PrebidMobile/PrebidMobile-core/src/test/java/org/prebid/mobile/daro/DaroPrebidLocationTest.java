package org.prebid.mobile.daro;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.reflection.sdk.ManagersResolverReflection;
import org.prebid.mobile.rendering.sdk.ManagersResolver;
import org.prebid.mobile.rendering.sdk.deviceData.managers.LastKnownLocationInfoManager;
import org.prebid.mobile.rendering.sdk.deviceData.managers.LocationInfoManager;
import org.prebid.mobile.rendering.utils.helpers.MraidUtils;
import org.prebid.mobile.rendering.views.webview.WebViewBase;
import org.prebid.mobile.rendering.views.webview.mraid.BaseJSInterface;
import org.prebid.mobile.rendering.views.webview.mraid.JsExecutor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLocationManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.prebid.mobile.rendering.views.webview.mraid.JSInterface.LOCATION_ERROR;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class DaroPrebidLocationTest {

    @After
    public void tearDown() {
        ManagersResolverReflection.resetManagers(ManagersResolver.getInstance());
    }

    @Test
    public void preparedManagersNeverExposeDeviceLocationToCreatives() {
        Application application = RuntimeEnvironment.getApplication();
        grantLocationWithLastKnownFix(application);
        // Control: the upstream manager reads this fix, so the setup is not vacuous.
        assertTrue(new LastKnownLocationInfoManager(application).isLocationAvailable());

        ManagersResolverReflection.resetManagers(ManagersResolver.getInstance());
        ManagersResolver.getInstance().prepare(application);
        LocationInfoManager locationManager = ManagersResolver.getInstance().getLocationManager();
        locationManager.resetLocation();

        assertFalse(locationManager.isLocationAvailable());
        assertNull(locationManager.getLatitude());
        assertNull(locationManager.getLongitude());
        BaseJSInterface jsInterface = new BaseJSInterface(
                application,
                mock(WebViewBase.class),
                mock(JsExecutor.class)
        );
        assertEquals(LOCATION_ERROR, jsInterface.getLocation());
        assertFalse(MraidUtils.isFeatureSupported("location"));
    }

    private static void grantLocationWithLastKnownFix(Application application) {
        shadowOf(application).grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        );
        LocationManager locationManager = (LocationManager) application.getSystemService(Context.LOCATION_SERVICE);
        ShadowLocationManager shadowLocationManager = shadowOf(locationManager);
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        Location fix = new Location(LocationManager.GPS_PROVIDER);
        fix.setLatitude(37.5665);
        fix.setLongitude(126.9780);
        fix.setAccuracy(5f);
        fix.setTime(System.currentTimeMillis());
        shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, fix);
    }
}
