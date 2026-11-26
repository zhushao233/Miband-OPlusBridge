// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Answers clock and calendar questions locally.
 *
 * <p>These replies are deterministic and must not depend on the chat model. Keeping them in a
 * separate class also avoids confusing an alarm question such as "设置七点的闹钟" with a plain
 * "现在几点" question.</p>
 */
public final class LocalTimeQuestion {
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");

    /** 2026-2040 Chinese New Year dates. */
    private static final LocalDate[] SPRING_FESTIVALS = {
            LocalDate.of(2026, 2, 17),
            LocalDate.of(2027, 2, 6),
            LocalDate.of(2028, 1, 26),
            LocalDate.of(2029, 2, 13),
            LocalDate.of(2030, 2, 3),
            LocalDate.of(2031, 1, 23),
            LocalDate.of(2032, 2, 11),
            LocalDate.of(2033, 1, 31),
            LocalDate.of(2034, 2, 19),
            LocalDate.of(2035, 2, 8),
            LocalDate.of(2036, 1, 28),
            LocalDate.of(2037, 2, 15),
            LocalDate.of(2038, 2, 4),
            LocalDate.of(2039, 1, 24),
            LocalDate.of(2040, 2, 12)
    };

    private LocalTimeQuestion() {}

    public static String answer(String raw) {
        return answer(raw, Clock.systemDefaultZone());
    }

    public static String answer(String raw, Clock clock) {
        if (raw == null || raw.isBlank() || clock == null) return null;
        String text = normalize(raw);
        if (asksSpringFestival(text)) return springFestivalAnswer(text, clock);
        if (asksUnitedStatesTime(text)) return unitedStatesAnswer(text, clock);
        if (asksLocalTime(text)) return localAnswer(clock);
        return null;
    }

    private static boolean asksSpringFestival(String text) {
        if (!text.contains("春节")) return false;
        return containsAny(text, "还有", "多久", "几天", "距离", "离", "倒计时", "哪一天", "哪天");
    }

    private static boolean asksUnitedStatesTime(String text) {
        if (!containsAny(text, "美国", "纽约", "华盛顿", "波士顿", "芝加哥",
                "洛杉矶", "加州", "旧金山", "西雅图", "东部时间", "西部时间", "太平洋时间")) {
            return false;
        }
        return containsAny(text, "几点", "时间", "现在", "now", "what time");
    }

    private static boolean asksLocalTime(String text) {
        if (containsAny(text, "闹钟", "提醒", "计时", "倒计时", "定时", "叫我", "叫醒")) {
            return false;
        }
        return containsAny(text, "现在几点", "几点了", "现在时间", "当前时间",
                "现在是什么时间", "what time", "current time");
    }

    private static String localAnswer(Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        return "现在是 " + clockText(now) + "。";
    }

    private static String unitedStatesAnswer(String text, Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        if (containsAny(text, "洛杉矶", "加州", "旧金山", "西雅图", "太平洋时间", "西部时间")) {
            return "美国太平洋时间现在是 " + clockText(now.withZoneSameInstant(PACIFIC)) + "。";
        }
        if (containsAny(text, "芝加哥", "中部时间")) {
            return "美国中部时间现在是 " + clockText(now.withZoneSameInstant(CENTRAL)) + "。";
        }
        if (containsAny(text, "纽约", "华盛顿", "波士顿", "东部时间")) {
            return "美国东部时间现在是 " + clockText(now.withZoneSameInstant(EASTERN)) + "。";
        }
        return "美国东部时间现在是 " + clockText(now.withZoneSameInstant(EASTERN))
                + "；太平洋时间现在是 " + clockText(now.withZoneSameInstant(PACIFIC)) + "。";
    }

    private static String springFestivalAnswer(String text, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate target = nextSpringFestival(today);
        if (target == null) {
            return "内置的春节日期只到 2040 年。";
        }
        if (text.contains("哪一天") || text.contains("哪天")) {
            return target.getYear() + " 年春节是 " + monthDay(target) + "，"
                    + weekday(target.getDayOfWeek().getValue()) + "。";
        }
        long days = ChronoUnit.DAYS.between(today, target);
        if (days == 0) return "今天是春节，祝你新年快乐！";
        if (days == 1) return "明天就是春节（" + monthDay(target) + "）。";
        return "距离 " + target.getYear() + " 年春节（" + monthDay(target)
                + "）还有 " + days + " 天。";
    }

    private static LocalDate nextSpringFestival(LocalDate today) {
        for (LocalDate date : SPRING_FESTIVALS) {
            if (!date.isBefore(today)) return date;
        }
        return null;
    }

    private static String clockText(ZonedDateTime time) {
        return String.format(Locale.CHINA, "%02d:%02d，%d月%d日 %s",
                time.getHour(), time.getMinute(), time.getMonthValue(),
                time.getDayOfMonth(), weekday(time.getDayOfWeek().getValue()));
    }

    private static String monthDay(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    private static String weekday(int day) {
        return switch (day) {
            case 1 -> "星期一";
            case 2 -> "星期二";
            case 3 -> "星期三";
            case 4 -> "星期四";
            case 5 -> "星期五";
            case 6 -> "星期六";
            default -> "星期日";
        };
    }

    private static String normalize(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length(); index++) {
            char value = raw.charAt(index);
            if (value >= '０' && value <= '９') value = (char) ('0' + value - '０');
            out.append(switch (value) {
                case '：' -> ':';
                case '？' -> '?';
                case '，' -> ',';
                default -> value;
            });
        }
        return out.toString().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }
}
