// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import io.github.miam1ku.mibandoplusbridge.protocol.BandWeatherEncoder;
import io.github.miam1ku.mibandoplusbridge.protocol.WeatherConditionMap;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Converts one validated OHealth snapshot into a short, model-readable weather context. */
public final class WeatherConversationContext {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA);

    private WeatherConversationContext() {}

    public static String format(BandWeatherEncoder.Sample sample) {
        if (sample == null) return "";
        ZoneId zone = zone(sample.timezone());
        StringBuilder text = new StringBuilder();
        text.append("本轮本机天气快照（仅用于回答当前问题）：\n");
        text.append("地点：").append(sample.cityName());
        if (!sample.locationName().equals(sample.cityName())) {
            text.append("（").append(sample.locationName()).append("）");
        }
        text.append('\n');
        text.append("现在：").append(conditionName(sample.conditionCode()))
                .append("，").append(temperature(sample.temperature(), sample.unit()));
        if (sample.humidityPercent() != null) {
            text.append("，湿度 ").append(sample.humidityPercent()).append('%');
        }
        if (sample.aqi() != null) {
            text.append("，空气质量指数 ").append(sample.aqi())
                    .append("（").append(aqiName(sample.aqi())).append("）");
        }
        if (sample.windPower() != null) {
            text.append("，风力 ").append(sample.windPower()).append(" 级");
            if (sample.windDegree() != null) {
                text.append("（").append(windDirection(sample.windDegree()))
                        .append("风）");
            }
        }
        if (sample.uvIndex() != null) {
            text.append("，紫外线指数 ").append(sample.uvIndex());
        }
        if (sample.pressure() != null) {
            text.append("，气压 ").append(Math.round(pressureHpa(sample.pressure())))
                    .append(" hPa");
        }
        text.append("。\n");
        text.append("数据发布时间：")
                .append(TIME.format(Instant.ofEpochMilli(sample.publishedAtMs()).atZone(zone)))
                .append("。\n");

        LocalDate published = Instant.ofEpochMilli(sample.publishedAtMs())
                .atZone(zone).toLocalDate();
        int shown = 0;
        for (BandWeatherEncoder.Day day : sample.daily()) {
            LocalDate date = Instant.ofEpochMilli(day.timeMs()).atZone(zone).toLocalDate();
            if (date.isBefore(published)) continue;
            text.append(shown == 0 ? "预报：" : "；")
                    .append(DATE.format(date)).append(' ')
                    .append(conditionName(day.dayCode()));
            if (day.nightCode() != day.dayCode()) {
                text.append("转").append(conditionName(day.nightCode()));
            }
            text.append("，")
                    .append(temperature(day.minTemp(), sample.unit())).append('~')
                    .append(temperature(day.maxTemp(), sample.unit()));
            if (++shown == 2) break;
        }
        if (shown > 0) text.append("。\n");
        return text.toString().trim();
    }

    public static String conditionName(int ohealthCode) {
        int code = WeatherConditionMap.fromOHealth(ohealthCode);
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "多云";
            case 2 -> "阴";
            case 3 -> "阵雨";
            case 4 -> "雷阵雨";
            case 5 -> "冰雹";
            case 6 -> "雨夹雪";
            case 7 -> "小雨";
            case 8 -> "中雨";
            case 9 -> "大雨";
            case 10 -> "暴雨";
            case 11 -> "大暴雨";
            case 12 -> "特大暴雨";
            case 13 -> "阵雪";
            case 14 -> "小雪";
            case 15 -> "中雪";
            case 16 -> "大雪";
            case 17 -> "暴雪";
            case 18 -> "薄雾";
            case 19 -> "冻雨";
            case 20 -> "沙尘暴";
            case 21 -> "小到中雨";
            case 22 -> "中到大雨";
            case 23 -> "大雨到暴雨";
            case 24 -> "暴雨到大暴雨";
            case 25 -> "大暴雨到特大暴雨";
            case 26 -> "小到中雪";
            case 27 -> "中到大雪";
            case 28 -> "大到暴雪";
            case 29 -> "浮尘";
            case 30 -> "大风";
            case 31 -> "强沙尘暴";
            case 32 -> "大雾";
            case 33 -> "雪";
            default -> "天气状况待确认";
        };
    }

    static String temperature(double value, String unit) {
        double celsius = "f".equals(unit) ? (value - 32) * 5 / 9 : value;
        return Math.round(celsius) + "°C";
    }

    static double pressureHpa(double value) {
        return value >= 80_000 ? value / 100 : value;
    }

    private static String aqiName(int aqi) {
        if (aqi <= 50) return "优";
        if (aqi <= 100) return "良";
        if (aqi <= 150) return "轻度污染";
        if (aqi <= 200) return "中度污染";
        if (aqi <= 300) return "重度污染";
        return "严重污染";
    }

    private static String windDirection(int degree) {
        String[] names = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        return names[Math.floorMod(Math.round(degree / 45f), names.length)];
    }

    private static ZoneId zone(String raw) {
        if (raw == null || raw.isBlank()) return ZoneOffset.UTC;
        try {
            double hours = Double.parseDouble(raw);
            return ZoneOffset.ofTotalSeconds((int) Math.round(hours * 3600));
        } catch (NumberFormatException nonNumeric) {
            try {
                return ZoneId.of(raw);
            } catch (RuntimeException invalid) {
                return ZoneOffset.UTC;
            }
        } catch (RuntimeException invalid) {
            return ZoneOffset.UTC;
        }
    }
}
