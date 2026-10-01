package org.prebid.mobile.daro;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Owns the fullscreen window so a publisher Activity recreation cannot restart the creative. */
public final class DaroPrebidFullscreenActivity extends Activity {
    private static final String SESSION = "org.prebid.mobile.daro.FULLSCREEN_SESSION";
    private static final String ORIENTATION = "org.prebid.mobile.daro.FULLSCREEN_ORIENTATION";
    // Main-thread only. The renderer releases the entry on close, failure, or destroy.
    private static final Map<String, DaroPrebidFullscreenRenderer> sessions = new HashMap<>();
    @Nullable private DaroPrebidFullscreenRenderer renderer;
    @Nullable private OnBackInvokedCallback backCallback;

    static void launch(Activity host, DaroPrebidFullscreenRenderer renderer, String id) {
        sessions.put(id, renderer);
        try {
            host.startActivity(new Intent(host, DaroPrebidFullscreenActivity.class)
                .putExtra(SESSION, id)
                .putExtra(ORIENTATION, hostOrientation(host)));
        } catch (RuntimeException error) {
            sessions.remove(id);
            throw error;
        }
    }

    static int hostOrientation(Activity host) {
        int requested = host.getRequestedOrientation();
        if (requested != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return requested;
        try {
            return host.getPackageManager().getActivityInfo(host.getComponentName(), 0).screenOrientation;
        } catch (PackageManager.NameNotFoundException ignored) {
            return requested;
        }
    }

    static void release(@Nullable String id) {
        if (id != null) sessions.remove(id);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderer = sessions.get(getIntent().getStringExtra(SESSION));
        if (renderer == null) {
            finish();
            return;
        }
        // Preserve both manifest restrictions and a publisher's runtime orientation policy.
        setRequestedOrientation(getIntent().getIntExtra(ORIENTATION, getRequestedOrientation()));
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = () -> { };
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        renderer.present(this);
    }

    @Override
    public void onBackPressed() {
        // The creative's skip/close gate owns dismissal.
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }
        if (renderer != null) renderer.presentationDestroyed(this);
        renderer = null;
        super.onDestroy();
    }
}
