// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

/** Keeps band-facing text within the AIVS instruction bounds. */
final class VoiceText {
    private VoiceText() {}

    static String compact(String value, int maximum) {
        String oneLine = value.replace('\r', ' ').replace('\n', ' ')
                .replaceAll("\\s+", " ").trim();
        int characters = oneLine.codePointCount(0, oneLine.length());
        if (characters <= maximum) return oneLine;
        if (maximum <= 0) return "";
        if (maximum == 1) return "…";
        int end = oneLine.offsetByCodePoints(0, maximum - 1);
        return oneLine.substring(0, end) + "…";
    }
}
