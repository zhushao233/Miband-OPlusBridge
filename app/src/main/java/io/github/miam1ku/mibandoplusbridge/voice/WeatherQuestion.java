// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.util.Locale;

/** Keeps weather context off ordinary chat requests so their cached prefix stays unchanged. */
public final class WeatherQuestion {
    private static final String[] KEYWORDS = {
            "天气", "气温", "温度", "几度", "多少度", "下雨", "降雨", "雨伞", "带伞",
            "下雪", "雪天", "冷\u4e0d冷", "冷吗", "热\u4e0d热", "热吗", "穿什么",
            "空气质量", "空气怎么样", "雾霾", "风力", "风大", "紫外线"
    };

    private WeatherQuestion() {}

    public static boolean asks(String question) {
        if (question == null || question.isBlank()) return false;
        String normalized = question.toLowerCase(Locale.ROOT);
        for (String keyword : KEYWORDS) {
            if (normalized.contains(keyword)) return true;
        }
        return false;
    }
}
