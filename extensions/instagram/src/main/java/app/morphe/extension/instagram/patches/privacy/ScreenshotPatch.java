/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 section 7(b) terms that apply to this code.
 */
package app.morphe.extension.instagram.patches.privacy;

import android.view.Window;
import android.view.WindowManager;

import app.morphe.extension.instagram.utils.Pref;

@SuppressWarnings("unused")
public final class ScreenshotPatch {
    private ScreenshotPatch() {}

    public static void setWindowFlags(Window window, int flags, int mask) {
        if (Pref.disableScreenshotDetection()) {
            flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
        }
        // Preserve the mask so enabling this setting also clears a stale secure
        // bit. All unrelated window flags keep their original behavior.
        window.setFlags(flags, mask);
    }
}
