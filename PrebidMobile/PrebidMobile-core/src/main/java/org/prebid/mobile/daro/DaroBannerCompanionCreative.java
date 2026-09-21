package org.prebid.mobile.daro;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.rendering.models.HTMLCreative;
import org.prebid.mobile.rendering.views.interstitial.InterstitialManager;
import org.prebid.mobile.rendering.views.webview.ActionUrl;

/** Loads independently of the video; a finished HTML document is not proof the image decoded. */
public final class DaroBannerCompanionCreative extends HTMLCreative {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DaroBannerCompanionModel companionModel;
    private boolean settled;
    private boolean ready;
    private boolean destroyed;
    private boolean checkingImage;
    private final Runnable timeout = () -> webViewFailedToLoad(new AdException(
            AdException.INTERNAL_ERROR, "Banner companion image timed out"));

    public DaroBannerCompanionCreative(Context context, DaroBannerCompanionModel model) throws AdException {
        // The video's OM session remains the ad's session. Do not create a second ad impression/session.
        super(context, model, null, new InterstitialManager());
        companionModel = model;
    }

    @Override public void load() throws AdException {
        handler.postDelayed(timeout, PrebidMobile.getCreativeFactoryTimeout());
        super.load();
    }

    @Override public void webViewReadyToDisplay() {
        if (destroyed || settled || checkingImage) return;
        checkingImage = true;
        getCreativeView().setActionUrl(new ActionUrl("daro-companion://ready", this::imageReady));
        getCreativeView().setActionUrl(new ActionUrl("daro-companion://failed", () -> webViewFailedToLoad(
                new AdException(AdException.INTERNAL_ERROR, "Banner companion image failed"))));
        getCreativeView().getWebView().evaluateJavascript(
                "(function(){var i=document.getElementById('companion');"
                + "function done(){location.href='daro-companion://'+(i&&i.naturalWidth>0?'ready':'failed');}"
                + "if(!i||i.complete){done();}else{i.onload=done;i.onerror=done;}})();", null);
    }

    private void imageReady() {
        if (destroyed || settled) return;
        settled = true;
        ready = true;
        handler.removeCallbacks(timeout);
        getResolutionListener().creativeReady(this);
    }

    @Override public void webViewFailedToLoad(AdException error) {
        if (destroyed || settled) return;
        settled = true;
        handler.removeCallbacks(timeout);
        companionModel.trackLoadFailure();
        getResolutionListener().creativeFailed(error);
    }

    @Override public boolean isResolved() { return ready && !destroyed; }

    @Override public void destroy() {
        if (destroyed) return;
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        super.destroy();
        interstitialManager.destroy();
    }
}
