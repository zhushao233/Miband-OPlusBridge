// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WeatherQuestionTest {
    @Test public void recognizesWeatherRequests() {
        assertTrue(WeatherQuestion.asks("今天天气怎么样"));
        assertTrue(WeatherQuestion.asks("出门要带伞吗"));
        assertTrue(WeatherQuestion.asks("现在多少度"));
        assertTrue(WeatherQuestion.asks("空气质量如何"));
    }

    @Test public void ordinaryAdviceDoesNotPullInWeather() {
        assertFalse(WeatherQuestion.asks("今天适合跑步吗"));
        assertFalse(WeatherQuestion.asks("给我讲个笑话"));
        assertFalse(WeatherQuestion.asks(""));
        assertFalse(WeatherQuestion.asks(null));
    }
}
