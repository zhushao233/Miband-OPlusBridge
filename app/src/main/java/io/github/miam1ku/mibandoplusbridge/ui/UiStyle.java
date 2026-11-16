// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

import android.content.Context;
import io.github.miam1ku.mibandoplusbridge.data.LocalPrefs;

/** User-selectable component language. */
public final class UiStyle {
    public static final String MIUIX = "miuix";
    public static final String MATERIAL = "material";

    private static final String PREFS = "appearance";
    private static final String THEME_KEY = "uiTheme";
    private static final String CONTOUR_GLOW_KEY = "couiContourGlow";
    private static final String SYNC_ACTION_KEY = "syncActionVisible";

    private UiStyle() {}

    public static String theme(Context context) {
        String value = LocalPrefs.open(context, PREFS).getString(THEME_KEY, MIUIX);
        return MATERIAL.equals(value) ? MATERIAL : MIUIX;
    }

    public static boolean material(Context context) {
        return MATERIAL.equals(theme(context));
    }

    public static boolean setTheme(Context context, String value) {
        String selected = MATERIAL.equals(value) ? MATERIAL : MIUIX;
        return LocalPrefs.open(context, PREFS).edit().putString(THEME_KEY, selected).commit();
    }

    public static String signature(Context context) {
        return theme(context);
    }

    public static boolean contourGlow(Context context) {
        return LocalPrefs.open(context, PREFS).getBoolean(CONTOUR_GLOW_KEY, true);
    }

    public static boolean setContourGlow(Context context, boolean enabled) {
        return LocalPrefs.open(context, PREFS).edit()
                .putBoolean(CONTOUR_GLOW_KEY, enabled).commit();
    }

    public static boolean syncActionVisible(Context context) {
        return LocalPrefs.open(context, PREFS).getBoolean(SYNC_ACTION_KEY, true);
    }

    public static boolean setSyncActionVisible(Context context, boolean visible) {
        return LocalPrefs.open(context, PREFS).edit()
                .putBoolean(SYNC_ACTION_KEY, visible).commit();
    }

    public static String themeLabel(Context context) {
        return material(context) ? "Material" : "Miuix";
    }

    public static String summary(Context context) {
        return (contourGlow(context) ? "轮廓光开" : "轮廓光关")
                + " · 同步手环" + (syncActionVisible(context) ? "显示" : "隐藏")
                + " · COUI 凝光视效";
    }
}
