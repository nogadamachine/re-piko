/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.utils;

/** Helpers for the CDN urls piko stores and re-opens later. */
public final class MediaUrls {

    private MediaUrls() {
    }

    /**
     * When a CDN url dies, as epoch millis, or 0 when it carries no expiry and so never does.
     * fbcdn links hold it in their own "oe" parameter as a hex epoch; permalinks have none.
     */
    public static long expiresAt(String url) {
        if (url == null) return 0L;
        try {
            int query = url.indexOf('?');
            if (query < 0) return 0L;
            String rawQuery = url.substring(query + 1).split("#", 2)[0];
            for (String parameter : rawQuery.split("&")) {
                if (!parameter.startsWith("oe=")) continue;
                String value = parameter.substring(3);
                if (!value.matches("[0-9a-fA-F]+")) return 0L;
                return Math.multiplyExact(Long.parseLong(value, 16), 1000L);
            }
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** True once {@link #expiresAt} has passed. Urls without an expiry never report expired. */
    public static boolean isExpired(String url) {
        long expiry = expiresAt(url);
        return expiry > 0 && expiry <= System.currentTimeMillis();
    }
}
