// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.data;

import android.content.Context;
import android.text.format.DateFormat;
import java.util.Locale;

/** Per-device display preferences applied whenever the bridge owns the band session. */
public final class BandDisplaySettings {
    public static final String CHINESE = "zh_CN";
    public static final String TRADITIONAL_CHINESE = "zh_TW";
    public static final String ENGLISH = "en_US";

    private static final String PREFS = "band-display-settings";
    private static final String KEY_USE_12_HOUR = "use12Hour";
    private static final String KEY_LANGUAGE = "language";

    private BandDisplaySettings() {}

    public static boolean use12Hour(Context context) {
        LocalPrefs prefs = LocalPrefs.open(context, PREFS);
        String stored = prefs.getString(KEY_USE_12_HOUR, "");
        return stored.isEmpty() ? !DateFormat.is24HourFormat(context) : prefs.getBoolean(KEY_USE_12_HOUR, false);
    }

    public static boolean saveUse12Hour(Context context, boolean enabled) {
        return LocalPrefs.open(context, PREFS).edit().putBoolean(KEY_USE_12_HOUR, enabled).commit();
    }

    public static String language(Context context) {
        String stored = savedLanguage(context);
        return stored.isEmpty() ? defaultLanguage(Locale.getDefault()) : stored;
    }

    public static String savedLanguage(Context context) {
        return canonicalLanguage(LocalPrefs.open(context, PREFS).getString(KEY_LANGUAGE, ""));
    }

    public static boolean saveLanguage(Context context, String locale) {
        String canonical = canonicalLanguage(locale);
        if (canonical.isEmpty()) return false;
        return LocalPrefs.open(context, PREFS).edit().putString(KEY_LANGUAGE, canonical).commit();
    }

    public static String languageLabel(String locale) {
        return switch (canonicalLanguage(locale)) {
            case CHINESE -> "简体中文";
            case TRADITIONAL_CHINESE -> "繁體中文";
            case ENGLISH -> "English";
            default -> "—";
        };
    }

    public static String canonicalLanguage(String locale) {
        if (locale == null) return "";
        String normalized = locale.trim().replace('-', '_');
        if (CHINESE.equalsIgnoreCase(normalized)) return CHINESE;
        if (TRADITIONAL_CHINESE.equalsIgnoreCase(normalized)) return TRADITIONAL_CHINESE;
        if (ENGLISH.equalsIgnoreCase(normalized)) return ENGLISH;
        return "";
    }

    public static String defaultLanguage(Locale locale) {
        if (locale == null || !"zh".equalsIgnoreCase(locale.getLanguage())) return ENGLISH;
        String country = locale.getCountry();
        boolean traditional = "Hant".equalsIgnoreCase(locale.getScript())
                || "TW".equalsIgnoreCase(country)
                || "HK".equalsIgnoreCase(country)
                || "MO".equalsIgnoreCase(country);
        return traditional ? TRADITIONAL_CHINESE : CHINESE;
    }
}
