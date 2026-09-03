package org.prebid.mobile;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.rendering.session.manager.OmAdSessionManager;
import org.prebid.mobile.reflection.Reflection;
import org.prebid.mobile.test.utils.ResourceUtils;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class PrebidNativeAdTest {

    @Test
    public void registerView_withAllTrackers() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/Full.json");

        assertEquals("https://prebid.qa.openx.net//event?t=win&b=5f6bec03-a3ae-4084-b2ae-dedfb0ac01ff&a=b4eb1475-4e3d-4186-97b7-25b6a6cf8618&bidder=openx&ts=1643899069308", nativeAd.getWinEvent());
        assertEquals("https://prebid.qa.openx.net//event?t=imp&b=5f6bec03-a3ae-4084-b2ae-dedfb0ac01ff&a=b4eb1475-4e3d-4186-97b7-25b6a6cf8618&bidder=openx&ts=1643899069308", nativeAd.getImpEvent());

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertNull(admImpressionTrackers);

        ArrayList<OmAdSessionManager.NativeDisplayVerificationResource> nativeOmidResources =
                reflectNativeOmidVerificationResources(nativeAd);
        assertNotNull(nativeOmidResources);
        assertEquals(1, nativeOmidResources.size());
        assertEquals(
                "https://s3-us-west-2.amazonaws.com/omsdk-files/compliance-js/omid-validation-verification-script-v1.js",
                Reflection.getFieldOf(nativeOmidResources.get(0), "omidJsUrl")
        );


        nativeAd.registerView(createViewMock(), mock(List.class), mock(PrebidNativeAdEventListener.class));


        ArrayList<ImpressionTracker> trackerObjects = reflectImpressionTrackerObjects(nativeAd);
        assertEquals(1, trackerObjects.size());
        assertEquals("https://prebid.qa.openx.net//event?t=imp&b=5f6bec03-a3ae-4084-b2ae-dedfb0ac01ff&a=b4eb1475-4e3d-4186-97b7-25b6a6cf8618&bidder=openx&ts=1643899069308", reflectImpressionTrackerUrl(trackerObjects.get(0)));
    }

    @Test
    public void registerView_withoutTrackers() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/WithoutTrackers.json");

        assertNull(nativeAd.getWinEvent());
        assertNull(nativeAd.getImpEvent());
        assertNull(reflectAdmImpressionTrackers(nativeAd));


        nativeAd.registerView(createViewMock(), mock(List.class), mock(PrebidNativeAdEventListener.class));


        ArrayList<ImpressionTracker> trackerObjects = reflectImpressionTrackerObjects(nativeAd);
        assertEquals(0, trackerObjects.size());
    }

    @Test
    public void nativeAdParser() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/Full.json");

        assertNotNull(nativeAd);

        assertEquals("OpenX (Title)", nativeAd.getTitle());
        assertEquals("https://www.saashub.com/images/app/service_logos/5/1df363c9a850/large.png?1525414023", nativeAd.getIconUrl());
        assertEquals("https://ssl-i.cdn.openx.com/mobile/demo-creatives/mobile-demo-banner-640x100.png", nativeAd.getImageUrl());
        assertEquals("Click here to visit our site!", nativeAd.getCallToAction());
        assertEquals("Learn all about this awesome story of someone using out OpenX SDK.", nativeAd.getDescription());
        assertEquals("OpenX (Brand)", nativeAd.getSponsoredBy());
        assertEquals("https://www.openx.com/", nativeAd.getClickUrl());

        ArrayList<NativeData> dataList = nativeAd.getDataList();
        assertEquals(5, dataList.size());
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.SPONSORED_BY, "OpenX (Brand)")));
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.DESCRIPTION, "Learn all about this awesome story of someone using out OpenX SDK.")));
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.CALL_TO_ACTION, "Click here to visit our site!")));
        assertThat(dataList, hasItem(new NativeData(500, "Sample value")));
        assertThat(dataList, hasItem(new NativeData(0, "Sample value 2")));

        ArrayList<NativeTitle> titlesList = nativeAd.getTitles();
        assertEquals(1, titlesList.size());
        assertThat(titlesList, hasItem(new NativeTitle("OpenX (Title)")));

        ArrayList<NativeImage> imagesList = nativeAd.getImages();
        assertEquals(4, imagesList.size());
        assertThat(imagesList, hasItem(new NativeImage(NativeImage.Type.ICON, "https://www.saashub.com/images/app/service_logos/5/1df363c9a850/large.png?1525414023")));
        assertThat(imagesList, hasItem(new NativeImage(NativeImage.Type.MAIN_IMAGE, "https://ssl-i.cdn.openx.com/mobile/demo-creatives/mobile-demo-banner-640x100.png")));
        assertThat(imagesList, hasItem(new NativeImage(500, "https://test.com/test.png")));
        assertThat(imagesList, hasItem(new NativeImage(0, "https://test2.com/test.png")));

        for (NativeImage image : imagesList) {
            if (image.getType() == NativeImage.Type.CUSTOM) {
                if (image.getUrl().equals("https://test.com/test.png")) {
                    assertEquals(500, image.getTypeNumber());
                } else if (image.getUrl().equals("https://test2.com/test.png")) {
                    assertEquals(0, image.getTypeNumber());
                }
            }
        }
    }

    @Test
    public void nativeAdWithWrapperParser() {
        PrebidNativeAd nativeAd = nativeAdFromFile("PrebidNativeAdTest/FullWithNativeWrapper.json");

        assertNotNull(nativeAd);

        assertEquals("OpenX (Title)", nativeAd.getTitle());
        assertEquals("https://www.saashub.com/images/app/service_logos/5/1df363c9a850/large.png?1525414023", nativeAd.getIconUrl());
        assertEquals("https://ssl-i.cdn.openx.com/mobile/demo-creatives/mobile-demo-banner-640x100.png", nativeAd.getImageUrl());
        assertEquals("Click here to visit our site!", nativeAd.getCallToAction());
        assertEquals("Learn all about this awesome story of someone using out OpenX SDK.", nativeAd.getDescription());
        assertEquals("OpenX (Brand)", nativeAd.getSponsoredBy());
        assertEquals("https://www.openx.com/", nativeAd.getClickUrl());

        ArrayList<NativeData> dataList = nativeAd.getDataList();
        assertEquals(5, dataList.size());
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.SPONSORED_BY, "OpenX (Brand)")));
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.DESCRIPTION, "Learn all about this awesome story of someone using out OpenX SDK.")));
        assertThat(dataList, hasItem(new NativeData(NativeData.Type.CALL_TO_ACTION, "Click here to visit our site!")));
        assertThat(dataList, hasItem(new NativeData(500, "Sample value")));
        assertThat(dataList, hasItem(new NativeData(0, "Sample value 2")));

        ArrayList<NativeTitle> titlesList = nativeAd.getTitles();
        assertEquals(1, titlesList.size());
        assertThat(titlesList, hasItem(new NativeTitle("OpenX (Title)")));

        ArrayList<NativeImage> imagesList = nativeAd.getImages();
        assertEquals(4, imagesList.size());
        assertThat(imagesList, hasItem(new NativeImage(NativeImage.Type.ICON, "https://www.saashub.com/images/app/service_logos/5/1df363c9a850/large.png?1525414023")));
        assertThat(imagesList, hasItem(new NativeImage(NativeImage.Type.MAIN_IMAGE, "https://ssl-i.cdn.openx.com/mobile/demo-creatives/mobile-demo-banner-640x100.png")));
        assertThat(imagesList, hasItem(new NativeImage(500, "https://test.com/test.png")));
        assertThat(imagesList, hasItem(new NativeImage(0, "https://test2.com/test.png")));

        for (NativeImage image : imagesList) {
            if (image.getType() == NativeImage.Type.CUSTOM) {
                if (image.getUrl().equals("https://test.com/test.png")) {
                    assertEquals(500, image.getTypeNumber());
                } else if (image.getUrl().equals("https://test2.com/test.png")) {
                    assertEquals(0, image.getTypeNumber());
                }
            }
        }
    }

    @Test
    public void createForExternalOwner_parsesBuyerTrackers_andSuppressesPbsServerNotices() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(externalOwnerAdm(true));

        assertNotNull(nativeAd);
        assertEquals("External Title", nativeAd.getTitle());
        assertEquals("https://example.com/icon.png", nativeAd.getIconUrl());
        assertEquals("https://example.com/main.png", nativeAd.getImageUrl());
        assertEquals("Install", nativeAd.getCallToAction());
        assertEquals("External body", nativeAd.getDescription());
        assertEquals("Daro", nativeAd.getSponsoredBy());
        assertEquals("https://example.com/click", nativeAd.getClickUrl());
        assertEquals("https://example.com/privacy", nativeAd.getPrivacyUrl());
        assertNull(nativeAd.getWinEvent());
        assertNull(nativeAd.getImpEvent());

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        ArrayList<String> clickTrackers = reflectClickTrackers(nativeAd);
        assertNotNull(admImpressionTrackers);
        assertNotNull(clickTrackers);
        assertEquals(1, admImpressionTrackers.size());
        assertEquals(1, clickTrackers.size());
        assertEquals("https://buyer.example/imp", admImpressionTrackers.get(0));
        assertEquals("https://buyer.example/click", clickTrackers.get(0));
    }

    @Test
    public void createForExternalOwner_registerView_buildsOnlyBuyerImpressionTrackers() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(externalOwnerAdm(true));
        PrebidNativeAdEventListener listener = mock(PrebidNativeAdEventListener.class);

        assertTrue(nativeAd.registerView(createViewMock(), mock(List.class), listener));

        ArrayList<ImpressionTracker> trackerObjects = reflectImpressionTrackerObjects(nativeAd);
        assertEquals(1, trackerObjects.size());
        assertEquals("https://buyer.example/imp", reflectImpressionTrackerUrl(trackerObjects.get(0)));
    }

    @Test
    public void createForExternalOwner_separatesNativeOmidVerificationTrackers() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(externalOwnerAdmWithNativeOmidTracker());

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        ArrayList<OmAdSessionManager.NativeDisplayVerificationResource> nativeOmidResources =
                reflectNativeOmidVerificationResources(nativeAd);

        assertNotNull(admImpressionTrackers);
        assertEquals(1, admImpressionTrackers.size());
        assertEquals("https://buyer.example/imp", admImpressionTrackers.get(0));
        assertNotNull(nativeOmidResources);
        assertEquals(1, nativeOmidResources.size());
        assertEquals("https://measurement.example/omid.js", Reflection.getFieldOf(nativeOmidResources.get(0), "omidJsUrl"));
        assertEquals("measurement-vendor", Reflection.getFieldOf(nativeOmidResources.get(0), "vendorKey"));
        assertEquals("verification-data", Reflection.getFieldOf(nativeOmidResources.get(0), "verificationParameters"));
    }

    @Test
    public void createForExternalOwner_supportsNativeWrapper() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner("{\"native\":" + externalOwnerAdm(true) + "}");

        assertNotNull(nativeAd);
        assertEquals("External Title", nativeAd.getTitle());
        assertNull(nativeAd.getWinEvent());
        assertNull(nativeAd.getImpEvent());
    }

    @Test
    public void enableDaroViewabilityImpression_trackerFree_firesViewableOnce_neverImpression() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(externalOwnerAdm(false));
        PrebidNativeAdEventListener listener = mock(PrebidNativeAdEventListener.class);

        assertTrue(nativeAd.registerView(createViewMock(), mock(List.class), listener));
        assertEquals(0, reflectImpressionTrackerObjects(nativeAd).size());

        nativeAd.enableDaroViewabilityImpression();

        VisibilityDetector.VisibilityListener daroListener = reflectDaroVisibilityListener(nativeAd);
        for (int i = 0; i < 6; i++) {
            daroListener.onVisibilityChanged(true);
        }
        daroListener.onVisibilityChanged(true);

        verify(listener, times(1)).onAdBecameViewable();
        verify(listener, never()).onAdImpression();
    }

    @Test
    public void enableDaroViewabilityImpression_resetsOnHidden() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(externalOwnerAdm(false));
        PrebidNativeAdEventListener listener = mock(PrebidNativeAdEventListener.class);

        assertTrue(nativeAd.registerView(createViewMock(), mock(List.class), listener));
        nativeAd.enableDaroViewabilityImpression();

        VisibilityDetector.VisibilityListener daroListener = reflectDaroVisibilityListener(nativeAd);
        for (int i = 0; i < 3; i++) {
            daroListener.onVisibilityChanged(true);
        }
        daroListener.onVisibilityChanged(false);
        for (int i = 0; i < 3; i++) {
            daroListener.onVisibilityChanged(true);
        }
        verify(listener, never()).onAdBecameViewable();

        daroListener.onVisibilityChanged(true);

        verify(listener, times(1)).onAdBecameViewable();
    }

    @Test
    public void createForExternalOwner_withAuctionPrice_substitutesMacro() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(externalOwnerAdmWithPriceMacro(), "1.23");

        assertEquals("https://example.com/click?price=1.23", nativeAd.getClickUrl());
        assertEquals("https://buyer.example/click?price=1.23", reflectClickTrackers(nativeAd).get(0));
        assertEquals("https://buyer.example/imp?price=1.23", reflectAdmImpressionTrackers(nativeAd).get(0));
    }

    @Test
    public void createForExternalOwner_withoutAuctionPrice_leavesMacroRaw() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(externalOwnerAdmWithPriceMacro());

        assertEquals("https://example.com/click?price={AUCTION_PRICE}", nativeAd.getClickUrl());
        assertEquals("https://buyer.example/click?price={AUCTION_PRICE}", reflectClickTrackers(nativeAd).get(0));
        assertEquals("https://buyer.example/imp?price={AUCTION_PRICE}", reflectAdmImpressionTrackers(nativeAd).get(0));
    }

    @Test
    public void createForExternalOwner_imptrackersOnly_registersImpressionTrackers() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(
                null,
                "[\"https://legacy.example/imp1\",\"https://legacy.example/imp2\"]"
        ));

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertNotNull(admImpressionTrackers);
        assertEquals(2, admImpressionTrackers.size());
        assertEquals("https://legacy.example/imp1", admImpressionTrackers.get(0));
        assertEquals("https://legacy.example/imp2", admImpressionTrackers.get(1));

        assertTrue(nativeAd.registerView(createViewMock(), mock(List.class), mock(PrebidNativeAdEventListener.class)));

        ArrayList<ImpressionTracker> trackerObjects = reflectImpressionTrackerObjects(nativeAd);
        assertEquals(2, trackerObjects.size());
        assertEquals("https://legacy.example/imp1", reflectImpressionTrackerUrl(trackerObjects.get(0)));
        assertEquals("https://legacy.example/imp2", reflectImpressionTrackerUrl(trackerObjects.get(1)));
    }

    @Test
    public void createForExternalOwner_sameUrlInEventtrackersAndImptrackers_registersOnce() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(
                "[{\"event\":1,\"method\":1,\"url\":\"https://buyer.example/imp\"}]",
                "[\"https://buyer.example/imp\"]"
        ));

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertEquals(1, admImpressionTrackers.size());
        assertEquals("https://buyer.example/imp", admImpressionTrackers.get(0));

        assertTrue(nativeAd.registerView(createViewMock(), mock(List.class), mock(PrebidNativeAdEventListener.class)));
        assertEquals(1, reflectImpressionTrackerObjects(nativeAd).size());
    }

    @Test
    public void createForExternalOwner_impressionEventtrackerPresent_ignoresImptrackers() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(
                "[{\"event\":1,\"method\":1,\"url\":\"https://buyer.example/imp\"}]",
                "[\"https://legacy.example/imp\"]"
        ));

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertEquals(1, admImpressionTrackers.size());
        assertEquals("https://buyer.example/imp", admImpressionTrackers.get(0));
    }

    @Test
    public void createForExternalOwner_nonImpressionEventtrackersOnly_registersImptrackers() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(
                "[{\"event\":2,\"method\":1,\"url\":\"https://buyer.example/viewable\"}]",
                "[\"https://legacy.example/imp\"]"
        ));

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertEquals(2, admImpressionTrackers.size());
        assertEquals("https://buyer.example/viewable", admImpressionTrackers.get(0));
        assertEquals("https://legacy.example/imp", admImpressionTrackers.get(1));
    }

    @Test
    public void createForExternalOwner_duplicateUrlsWithinImptrackers_registersOnce() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(
                null,
                "[\"https://legacy.example/imp\",\"https://legacy.example/imp\"]"
        ));

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertEquals(1, admImpressionTrackers.size());
        assertEquals("https://legacy.example/imp", admImpressionTrackers.get(0));
    }

    @Test
    public void createForExternalOwner_imptrackers_withAuctionPrice_substitutesMacro() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(
                null,
                "[\"https://legacy.example/imp?price={AUCTION_PRICE}\"]"
        ), "1.23");

        assertEquals("https://legacy.example/imp?price=1.23", reflectAdmImpressionTrackers(nativeAd).get(0));
    }

    @Test
    public void createForExternalOwner_imptrackers_withoutAuctionPrice_leavesMacroRaw() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(
                null,
                "[\"https://legacy.example/imp?price={AUCTION_PRICE}\"]"
        ));

        assertEquals("https://legacy.example/imp?price={AUCTION_PRICE}", reflectAdmImpressionTrackers(nativeAd).get(0));
    }

    @Test
    public void createForExternalOwner_impressionEventtrackerPresent_ignoresImptrackers_withMacro() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(
                "[{\"event\":1,\"method\":1,\"url\":\"https://buyer.example/imp?price=1.23\"}]",
                "[\"https://legacy.example/imp?price={AUCTION_PRICE}\"]"
        ), "1.23");

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertEquals(1, admImpressionTrackers.size());
        assertEquals("https://buyer.example/imp?price=1.23", admImpressionTrackers.get(0));
    }

    @Test
    public void createForExternalOwner_imptrackersEmptyArray_leavesImpressionTrackersNull() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(null, "[]"));

        assertNotNull(nativeAd);
        assertNull(reflectAdmImpressionTrackers(nativeAd));
    }

    @Test
    public void createForExternalOwner_imptrackersNotArray_ignored() {
        PrebidNativeAd asString = PrebidNativeAd.createForExternalOwner(admWithTrackers(null, "\"https://legacy.example/imp\""));
        PrebidNativeAd asObject = PrebidNativeAd.createForExternalOwner(admWithTrackers(null, "{\"url\":\"https://legacy.example/imp\"}"));

        assertNotNull(asString);
        assertNotNull(asObject);
        assertNull(reflectAdmImpressionTrackers(asString));
        assertNull(reflectAdmImpressionTrackers(asObject));
    }

    @Test
    public void createForExternalOwner_imptrackersNonStringElements_skipped() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(
                null,
                "[123,null,{\"url\":\"https://legacy.example/object\"},\"https://legacy.example/imp\"]"
        ));

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertEquals(1, admImpressionTrackers.size());
        assertEquals("https://legacy.example/imp", admImpressionTrackers.get(0));
    }

    @Test
    public void createForExternalOwner_omidVerificationTracker_notMergedWithImptrackers() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(
                "[{\"event\":1,\"method\":1,\"url\":\"https://buyer.example/imp\"},"
                        + "{\"event\":555,\"method\":2,\"url\":\"https://measurement.example/omid.js\","
                        + "\"ext\":{\"vendorKey\":\"measurement-vendor\",\"verification_parameters\":\"verification-data\"}}]",
                "[\"https://legacy.example/imp\"]"
        ));

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertEquals(1, admImpressionTrackers.size());
        assertEquals("https://buyer.example/imp", admImpressionTrackers.get(0));

        ArrayList<OmAdSessionManager.NativeDisplayVerificationResource> nativeOmidResources =
                reflectNativeOmidVerificationResources(nativeAd);
        assertEquals(1, nativeOmidResources.size());
        assertEquals("https://measurement.example/omid.js", Reflection.getFieldOf(nativeOmidResources.get(0), "omidJsUrl"));
    }

    @Test
    public void createForExternalOwner_omidVerificationTrackerOnly_registersImptrackers() {
        PrebidNativeAd nativeAd = PrebidNativeAd.createForExternalOwner(admWithTrackers(
                "[{\"event\":555,\"method\":2,\"url\":\"https://measurement.example/omid.js\","
                        + "\"ext\":{\"vendorKey\":\"measurement-vendor\",\"verification_parameters\":\"verification-data\"}}]",
                "[\"https://legacy.example/imp\"]"
        ));

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertEquals(1, admImpressionTrackers.size());
        assertEquals("https://legacy.example/imp", admImpressionTrackers.get(0));
    }

    @Test
    public void create_imptrackersOnly_registersImpressionTrackers() throws JSONException {
        PrebidNativeAd nativeAd = nativeAdFromBid(admWithTrackers(null, "[\"https://legacy.example/imp\"]"), 1.23);

        assertNotNull(nativeAd);
        assertNull(nativeAd.getImpEvent());

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertEquals(1, admImpressionTrackers.size());
        assertEquals("https://legacy.example/imp", admImpressionTrackers.get(0));

        assertTrue(nativeAd.registerView(createViewMock(), mock(List.class), mock(PrebidNativeAdEventListener.class)));

        ArrayList<ImpressionTracker> trackerObjects = reflectImpressionTrackerObjects(nativeAd);
        assertEquals(1, trackerObjects.size());
        assertEquals("https://legacy.example/imp", reflectImpressionTrackerUrl(trackerObjects.get(0)));
    }

    @Test
    public void create_impressionEventtrackerPresent_ignoresImptrackers() throws JSONException {
        PrebidNativeAd nativeAd = nativeAdFromBid(admWithTrackers(
                "[{\"event\":1,\"method\":1,\"url\":\"https://buyer.example/imp?price={AUCTION_PRICE}\"}]",
                "[\"https://legacy.example/imp?price={AUCTION_PRICE}\"]"
        ), 1.23);

        ArrayList<String> admImpressionTrackers = reflectAdmImpressionTrackers(nativeAd);
        assertEquals(1, admImpressionTrackers.size());
        assertEquals("https://buyer.example/imp?price=1.23", admImpressionTrackers.get(0));
    }

    private PrebidNativeAd nativeAdFromFile(String path) {
        String resource = ResourceUtils.convertResourceToString(path);
        String cacheId = CacheManager.save(resource);
        return PrebidNativeAd.create(cacheId);
    }

    private PrebidNativeAd nativeAdFromBid(String adm, double price) throws JSONException {
        String bid = new JSONObject().put("price", price).put("adm", adm).toString();
        return PrebidNativeAd.create(CacheManager.save(bid));
    }

    private View createViewMock() {
        Context contextMock = mock(Context.class);
        when(contextMock.getApplicationContext()).thenReturn(mock(Application.class));

        View mainMock = mock(View.class);
        when(mainMock.getContext()).thenReturn(contextMock);
        return mainMock;
    }

    private ArrayList<String> reflectAdmImpressionTrackers(PrebidNativeAd ad) {
        return Reflection.getFieldOf(ad, "imp_trackers");
    }

    private ArrayList<ImpressionTracker> reflectImpressionTrackerObjects(PrebidNativeAd ad) {
        return Reflection.getFieldOf(ad, "impressionTrackers");
    }

    private String reflectImpressionTrackerUrl(ImpressionTracker tracker) {
        return Reflection.getFieldOf(tracker, "url");
    }

    private ArrayList<String> reflectClickTrackers(PrebidNativeAd ad) {
        return Reflection.getFieldOf(ad, "click_trackers");
    }

    private ArrayList<OmAdSessionManager.NativeDisplayVerificationResource> reflectNativeOmidVerificationResources(PrebidNativeAd ad) {
        return Reflection.getFieldOf(ad, "nativeOmidVerificationResources");
    }

    private VisibilityDetector.VisibilityListener reflectDaroVisibilityListener(PrebidNativeAd ad) {
        VisibilityDetector visibilityDetector = Reflection.getFieldOf(ad, "visibilityDetector");
        ArrayList<VisibilityDetector.VisibilityListener> listeners = Reflection.getFieldOf(visibilityDetector, "listeners");
        for (VisibilityDetector.VisibilityListener listener : listeners) {
            if (listener.getClass().getSimpleName().contains("DaroViewabilityListener")) {
                return listener;
            }
        }
        return null;
    }

    private String externalOwnerAdm(boolean includeTrackers) {
        String trackers = "";
        if (includeTrackers) {
            trackers = ",\"eventtrackers\":[{\"url\":\"https://buyer.example/imp\"}]"
                    + ",\"link\":{\"url\":\"https://example.com/click\",\"clicktrackers\":[\"https://buyer.example/click\"]}";
        } else {
            trackers = ",\"link\":{\"url\":\"https://example.com/click\"}";
        }
        return "{\"assets\":["
                + "{\"title\":{\"text\":\"External Title\"}},"
                + "{\"img\":{\"type\":1,\"url\":\"https://example.com/icon.png\"}},"
                + "{\"img\":{\"type\":3,\"url\":\"https://example.com/main.png\"}},"
                + "{\"data\":{\"type\":1,\"value\":\"Daro\"}},"
                + "{\"data\":{\"type\":2,\"value\":\"External body\"}},"
                + "{\"data\":{\"type\":12,\"value\":\"Install\"}}"
                + "]"
                + trackers
                + ",\"privacy\":\"https://example.com/privacy\"}";
    }

    private String externalOwnerAdmWithPriceMacro() {
        return "{\"assets\":[{\"title\":{\"text\":\"External Title\"}}],"
                + "\"eventtrackers\":[{\"url\":\"https://buyer.example/imp?price={AUCTION_PRICE}\"}],"
                + "\"link\":{\"url\":\"https://example.com/click?price={AUCTION_PRICE}\","
                + "\"clicktrackers\":[\"https://buyer.example/click?price={AUCTION_PRICE}\"]}}";
    }

    private String externalOwnerAdmWithNativeOmidTracker() {
        return "{\"assets\":[{\"title\":{\"text\":\"External Title\"}}],"
                + "\"eventtrackers\":["
                + "{\"event\":1,\"method\":1,\"url\":\"https://buyer.example/imp\"},"
                + "{\"event\":555,\"method\":2,\"url\":\"https://measurement.example/omid.js\","
                + "\"ext\":{\"vendorKey\":\"measurement-vendor\",\"verification_parameters\":\"verification-data\"}}"
                + "],"
                + "\"link\":{\"url\":\"https://example.com/click\"}}";
    }

    private String admWithTrackers(String eventtrackersJson, String imptrackersJson) {
        StringBuilder adm = new StringBuilder("{\"assets\":[{\"title\":{\"text\":\"External Title\"}}],")
                .append("\"link\":{\"url\":\"https://example.com/click\"}");
        if (eventtrackersJson != null) {
            adm.append(",\"eventtrackers\":").append(eventtrackersJson);
        }
        if (imptrackersJson != null) {
            adm.append(",\"imptrackers\":").append(imptrackersJson);
        }
        return adm.append("}").toString();
    }
}
