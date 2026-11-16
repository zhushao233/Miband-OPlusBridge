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

    public static String themeLabel(Context context) {
        return material(context) ? "Material" : "Miuix";
    }

    public static String summary(Context context) {
        return themeLabel(context) + " 主题 · COUI 凝光视效";
    }
}
