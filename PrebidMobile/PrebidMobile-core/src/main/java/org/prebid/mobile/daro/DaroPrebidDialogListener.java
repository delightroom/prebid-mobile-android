package org.prebid.mobile.daro;

import android.app.Dialog;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Notifies the host when a fullscreen creative dialog has been shown and its content is attached. */
public interface DaroPrebidDialogListener {
    /**
     * @return the host's AdChoices control installed in this dialog, or null when there is none. It is
     * registered as an OM friendly obstruction for the current session, so it must stay the same view
     * for the lifetime of the dialog.
     */
    @Nullable
    View dialogShown(@NonNull Dialog dialog);
}
