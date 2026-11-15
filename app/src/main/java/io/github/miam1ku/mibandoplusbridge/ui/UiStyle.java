// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

import android.content.Context;
import io.github.miam1ku.mibandoplusbridge.data.LocalPrefs;

/** User-selectable component language and bottom-bar placement. */
public final class UiStyle {
    public static final String MIUIX = "miuix";
    public static final String MATERIAL = "material";
    public static final String FIXED = "fixed";
    public static final String FLOATING = "floating";

    private static final String PREFS = "appearance";
    private static final String THEME_KEY = "uiTheme";
    private static final String DOCK_KEY = "bottomBar";
    private static final String GLASS_KEY = "liquidGlass";

    private UiStyle() {}

    public static String theme(Context context) {
        String value = LocalPrefs.open(context, PREFS).getString(THEME_KEY, MIUIX);
        return MATERIAL.equals(value) ? MATERIAL : MIUIX;
    }

    public static String dock(Context context) {
        String value = LocalPrefs.open(context, PREFS).getString(DOCK_KEY, FIXED);
        return FLOATING.equals(value) ? FLOATING : FIXED;
    }

    public static boolean material(Context context) {
        return MATERIAL.equals(theme(context));
    }

    public static boolean floating(Context context) {
        return FLOATING.equals(dock(context));
    }

    public static boolean glass(Context context) {
        return LocalPrefs.open(context, PREFS).getBoolean(GLASS_KEY, true);
    }

    public static boolean setTheme(Context context, String value) {
        String selected = MATERIAL.equals(value) ? MATERIAL : MIUIX;
        return LocalPrefs.open(context, PREFS).edit().putString(THEME_KEY, selected).commit();
    }

    public static boolean setDock(Context context, String value) {
        String selected = FLOATING.equals(value) ? FLOATING : FIXED;
        return LocalPrefs.open(context, PREFS).edit().putString(DOCK_KEY, selected).commit();
    }

    public static boolean setGlass(Context context, boolean enabled) {
        return LocalPrefs.open(context, PREFS).edit().putBoolean(GLASS_KEY, enabled).commit();
    }

    public static String signature(Context context) {
        return theme(context) + "/" + dock(context) + "/" + glass(context);
    }

    public static String themeLabel(Context context) {
        return material(context) ? "Material" : "Miuix";
    }

    public static String dockLabel(Context context) {
        return floating(context) ? "悬浮底栏" : "固定底栏";
    }

    public static String summary(Context context) {
        String dock = dockLabel(context);
        if (floating(context)) dock += glass(context) ? " · 液态玻璃" : " · 实色";
        return themeLabel(context) + " 主题 · " + dock;
    }
}
