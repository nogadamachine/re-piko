/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.devFlags;

import java.util.Locale;

import app.morphe.extension.crimera.PikoUtils;

import static app.morphe.extension.instagram.utils.IgStr.str;

final class RecommendedFlagLocalization {

    private RecommendedFlagLocalization() {}

    static String name(String code, String fallback) {
        return localized(code, "name", fallback);
    }

    static String description(String code, String fallback) {
        return localized(code, "desc", fallback);
    }

    private static String localized(String code, String suffix, String fallback) {
        if (!isKoreanLocale() || code == null || code.isEmpty()) return fallback;

        String resourceName = resourceName(code, suffix);
        String value = str(resourceName);
        return resourceName.equals(value) ? fallback : value;
    }

    private static boolean isKoreanLocale() {
        try {
            Locale locale = PikoUtils.getContext()
                    .getResources()
                    .getConfiguration()
                    .getLocales()
                    .get(0);
            return locale != null && "ko".equals(locale.getLanguage());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String resourceName(String code, String suffix) {
        StringBuilder safeCode = new StringBuilder(code.length());
        for (int i = 0; i < code.length(); i++) {
            char value = code.charAt(i);
            if (Character.isLetterOrDigit(value)) {
                safeCode.append(value);
            } else if (safeCode.length() == 0 || safeCode.charAt(safeCode.length() - 1) != '_') {
                safeCode.append('_');
            }
        }
        return "piko_rec_flag_" + safeCode + "_" + suffix;
    }
}
