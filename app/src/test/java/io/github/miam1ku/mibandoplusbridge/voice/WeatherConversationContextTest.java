// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import io.github.miam1ku.mibandoplusbridge.protocol.BandWeatherEncoder;
import java.time.Instant;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WeatherConversationContextTest {
    private static final long PUBLISHED =
            Instant.parse("2026-11-25T10:00:00+08:00").toEpochMilli();

    @Test public void formatsCurrentMetricsAndTwoForecastDays() {
        var text = WeatherConversationContext.format(sample("c", 21, 1_012, 3, 90));
        assertTrue(text.contains("地点：杭州（西湖区）"));
        assertTrue(text.contains("现在：多云，21°C"));
        assertTrue(text.contains("湿度 68%"));
        assertTrue(text.contains("空气质量指数 42（优）"));
        assertTrue(text.contains("风力 3 级（东风）"));
        assertTrue(text.contains("气压 1012 hPa"));
        assertTrue(text.contains("11月25日 星期三 多云转小雨，12°C~21°C"));
        assertTrue(text.contains("11月26日 星期四 晴，10°C~22°C"));
    }

    @Test public void convertsFahrenheitAndPascalPressure() {
        var text = WeatherConversationContext.format(sample("f", 69.8, 101_200, 2, 45));
        assertTrue(text.contains("现在：多云，21°C"));
        assertTrue(text.contains("气压 1012 hPa"));
        assertTrue(text.contains("12°C~21°C"));
    }

    @Test public void unknownConditionNeverThrows() {
        assertEquals("天气状况待确认", WeatherConversationContext.conditionName(71));
        assertEquals("天气状况待确认", WeatherConversationContext.conditionName(-1));
    }

    private static BandWeatherEncoder.Sample sample(String unit, double temperature,
                                                     double pressure, int windPower,
                                                     int windDegree) {
        long tomorrow = PUBLISHED + 86_400_000L;
        boolean fahrenheit = "f".equals(unit);
        return new BandWeatherEncoder.Sample("weathercn:101210101", "杭州", "西湖区",
                "Asia/Shanghai", unit, PUBLISHED, 56, temperature,
                68, 42, windPower, windDegree, 5, pressure,
                List.of(new BandWeatherEncoder.Day(PUBLISHED, 56, 7,
                                fahrenheit ? 53.6 : 12, fahrenheit ? 69.8 : 21, null, null),
                        new BandWeatherEncoder.Day(tomorrow, 54, 54,
                                fahrenheit ? 50 : 10, fahrenheit ? 71.6 : 22, null, null)),
                List.of());
    }
}
