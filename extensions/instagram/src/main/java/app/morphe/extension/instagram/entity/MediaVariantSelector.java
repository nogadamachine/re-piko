/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.instagram.entity;

import java.util.List;

public final class MediaVariantSelector {
    private MediaVariantSelector() {}

    public static MediaInterface highestResolution(List<? extends MediaInterface> variants) throws Exception {
        MediaInterface best = null;
        long bestArea = -1;
        if (variants == null) return null;
        for (MediaInterface candidate : variants) {
            if (candidate == null) continue;
            String url = candidate.getUrl();
            if (url == null || url.isEmpty()) continue;
            long area = 0;
            try {
                Integer width = candidate.getWidth(), height = candidate.getHeight();
                if (width != null && height != null && width > 0 && height > 0) area = (long) width * height;
            } catch (Exception ignored) {}
            // Keep the server's priority when dimensions tie, rather than guessing codec quality.
            if (best == null || area > bestArea) {
                best = candidate;
                bestArea = area;
            }
        }
        return best;
    }
}
