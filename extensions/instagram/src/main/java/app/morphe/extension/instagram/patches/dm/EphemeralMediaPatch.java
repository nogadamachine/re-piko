/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.dm;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;

@SuppressWarnings("unused")
public class EphemeralMediaPatch {
    private static final String PERMA_KEY = "permanent";

    public static String makeEphemeralMediaPermanent(Long expireAt, String viewMode) {
        try {
            // Read the current preference for every newly parsed message. A static
            // snapshot kept converting messages after the user disabled the switch.
            if (expireAt == null || viewMode == null || !Pref.makeEphemeralMediaPermanent()) {
                return viewMode;
            }
            if (System.currentTimeMillis() / 1000 <= expireAt && !PERMA_KEY.equals(viewMode)) {
                return PERMA_KEY;
            }

        } catch (Exception e) {
            Logger.printException(() -> "error makeEphemeralMediaPermanent: " + e);
        }
        return viewMode;
    }

}
