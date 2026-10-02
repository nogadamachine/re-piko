/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 * See the included NOTICE file for GPLv3 section 7(b) terms.
 */
package app.morphe.extension.instagram.patches.reels;

import android.os.Handler;
import android.os.Looper;
import androidx.viewpager2.widget.ViewPager2;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.instagram.patches.focusLock.FocusLock;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.utils.Pref;

/** Restores only the input state that this feature changed on a Reels pager. */
public final class ReelsScrolling {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<ViewPager2, Boolean> DISABLED = new WeakHashMap<>();
    private static boolean recheckScheduled;

    private ReelsScrolling() { }

    public static void update(ViewPager2 pager) {
        if (pager == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> update(pager));
            return;
        }
        if (Pref.disableReelsScrolling()) {
            if (!DISABLED.containsKey(pager)) {
                DISABLED.put(pager, pager.A0A);
            }
            pager.setUserInputEnabled(false);
            if (FocusLock.isForced(Settings.DISABLE_REELS_SCROLLING)) scheduleRecheck();
        } else {
            Boolean original = DISABLED.remove(pager);
            if (original != null) pager.setUserInputEnabled(original);
        }
    }

    private static void scheduleRecheck() {
        if (recheckScheduled || DISABLED.isEmpty()) return;
        recheckScheduled = true;
        // One main-thread callback while a timed lock holds live pagers. No wake lock.
        MAIN.postDelayed(ReelsScrolling::recheck, 1000);
    }

    private static void recheck() {
        recheckScheduled = false;
        if (!Pref.disableReelsScrolling()) {
            for (Map.Entry<ViewPager2, Boolean> entry : DISABLED.entrySet()) {
                ViewPager2 pager = entry.getKey();
                if (pager != null) pager.setUserInputEnabled(entry.getValue());
            }
            DISABLED.clear();
        } else if (FocusLock.isForced(Settings.DISABLE_REELS_SCROLLING)) {
            scheduleRecheck();
        }
    }
}
