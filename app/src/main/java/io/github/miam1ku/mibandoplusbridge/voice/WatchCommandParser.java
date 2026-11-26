// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses short Chinese watch commands before the chat request.
 *
 * <p>Only explicit device intents are captured, so ordinary questions still reach the model.
 * Relative durations are represented as a one-shot alarm because the current Xiaomi SPP
 * schedule service exposes alarms but no native countdown start command.</p>
 */
public final class WatchCommandParser {
    private static final Pattern CLOCK = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3]):([0-5]?\\d)(?!\\d)");
    private static final Pattern HOUR = Pattern.compile(
            "(?<!\\d)([0-9]{1,2}|[一二两三四五六七八九十]{1,3})\\s*[点时]"
                    + "(?:\\s*(半|一刻|三刻|([0-9]{1,2}|[一二三四五六七八九十]{1,3})\\s*分?))?");
    private static final Pattern HOURS = Pattern.compile(
            "([0-9]+|[一二两三四五六七八九十]{1,3})\\s*个?\\s*(半)?\\s*(?:小时|钟头)");
    private static final Pattern MINUTES = Pattern.compile(
            "([0-9]+|[一二两三四五六七八九十]{1,3})\\s*分钟");
    private static final Pattern HALF_HOUR = Pattern.compile("半\\s*(?:个)?\\s*(?:小时|钟头)");
    private static final AppWord[] APP_WORDS = {
            new AppWord("呼吸训练", WatchCommand.WatchApp.BREATHE),
            new AppWord("呼吸", WatchCommand.WatchApp.BREATHE),
            new AppWord("手电筒", WatchCommand.WatchApp.FLASHLIGHT),
            new AppWord("手电", WatchCommand.WatchApp.FLASHLIGHT),
            new AppWord("照明", WatchCommand.WatchApp.FLASHLIGHT),
            new AppWord("支付宝", WatchCommand.WatchApp.ALIPAY),
            new AppWord("卡包", WatchCommand.WatchApp.CARD_WALLET),
            new AppWord("钱包", WatchCommand.WatchApp.CARD_WALLET),
            new AppWord("计时器", WatchCommand.WatchApp.TIMER),
            new AppWord("秒表", WatchCommand.WatchApp.STOPWATCH),
            new AppWord("闹钟", WatchCommand.WatchApp.ALARM),
            new AppWord("天气", WatchCommand.WatchApp.WEATHER),
            new AppWord("设置", WatchCommand.WatchApp.SETTINGS),
            new AppWord("日历", WatchCommand.WatchApp.CALENDAR),
            new AppWord("睡眠", WatchCommand.WatchApp.SLEEP),
            new AppWord("心率", WatchCommand.WatchApp.HEART_RATE),
            new AppWord("血氧", WatchCommand.WatchApp.BLOOD_OXYGEN),
            new AppWord("压力", WatchCommand.WatchApp.STRESS),
            new AppWord("联系人", WatchCommand.WatchApp.CONTACTS),
            new AppWord("通讯录", WatchCommand.WatchApp.CONTACTS),
            new AppWord("待办", WatchCommand.WatchApp.TODO)
    };

    private WatchCommandParser() {}

    public static WatchCommand parse(String raw, LocalTime now) {
        if (raw == null) return null;
        String text = normalize(raw);
        if (text.isBlank()) return null;

        if (containsAny(text, "找手机", "查找手机", "查找我的手机", "找我的手机",
                "找一下手机", "手机在哪", "手机在哪里")) {
            return WatchCommand.of(WatchCommand.Kind.FIND_PHONE);
        }
        if (containsAny(text, "暂停音乐", "暂停播放", "停止播放", "把音乐暂停")) {
            return WatchCommand.of(WatchCommand.Kind.PAUSE_MUSIC);
        }
        if (containsAny(text, "勿扰", "免打扰")) {
            if (containsAny(text, "关闭", "关掉", "退出", "取消", "解除")) {
                return WatchCommand.dnd(false);
            }
            if (containsAny(text, "打开", "开启", "进入", "设置")) {
                return WatchCommand.dnd(true);
            }
        }
        WatchCommand.WatchApp app = watchApp(text);
        if (app != null) return WatchCommand.app(app);

        boolean alarm = containsAny(text, "闹钟", "闹鈴", "闹铃");
        boolean timer = containsAny(text, "计时", "倒计时", "定时");
        boolean reminder = containsAny(text, "提醒我", "叫我", "叫醒我", "喊我");

        if (alarm && containsAny(text, "查看", "看看", "有哪些", "列表", "还有几个", "几个闹钟")) {
            return WatchCommand.of(WatchCommand.Kind.LIST_ALARMS);
        }

        boolean cancel = containsAny(text, "取消", "关闭", "关掉", "删除", "删掉", "停止");
        if ((alarm || timer) && cancel) {
            if (containsAny(text, "所有", "全部", "都取消", "都关")) {
                return WatchCommand.deleteAll();
            }
            TimeTarget at = absoluteTime(text, now);
            return at != null && !at.relative()
                    ? WatchCommand.deleteAt(at.hour(), at.minute())
                    : WatchCommand.of(WatchCommand.Kind.DELETE_NEXT_ALARM);
        }

        TimeTarget target = anyTime(text, now);
        if (timer || (target != null && target.relative())) {
            if (target == null || !target.relative()) {
                return WatchCommand.of(WatchCommand.Kind.ASK_DURATION);
            }
            return WatchCommand.timer(target.minutes());
        }

        if (alarm || reminder) {
            if (target == null) return WatchCommand.of(WatchCommand.Kind.ASK_ALARM_TIME);
            if (target.relative()) return WatchCommand.timer(target.minutes());
            boolean daily = containsAny(text, "每天", "每日", "天天");
            return WatchCommand.alarm(target.hour(), target.minute(), daily);
        }
        return null;
    }

    private static WatchCommand.WatchApp watchApp(String text) {
        boolean explicitOpen = containsAny(text, "打开", "开启", "进入", "启动", "开一下");
        if (!explicitOpen) {
            if (text.startsWith("开手电")) return WatchCommand.WatchApp.FLASHLIGHT;
            if (text.startsWith("开支付宝")) return WatchCommand.WatchApp.ALIPAY;
            if (text.startsWith("开卡包")) return WatchCommand.WatchApp.CARD_WALLET;
            if (text.startsWith("开钱包")) return WatchCommand.WatchApp.CARD_WALLET;
            return null;
        }
        for (AppWord word : APP_WORDS) {
            if (text.contains(word.keyword())) return word.app();
        }
        return null;
    }

    private static String normalize(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length(); index++) {
            char value = raw.charAt(index);
            if (value >= '０' && value <= '９') value = (char) ('0' + value - '０');
            out.append(switch (value) {
                case '：' -> ':';
                case '點' -> '点';
                case '時' -> '时';
                case '鬧' -> '闹';
                case '鐘' -> '钟';
                case '鈴', '鐺' -> '铃';
                case '計' -> '计';
                default -> value;
            });
        }
        return out.toString().replace("分鐘", "分钟").replace("小時", "小时")
                .replace("鐘頭", "钟头").replace("鬧鈴", "闹铃").trim();
    }

    private static TimeTarget anyTime(String text, LocalTime now) {
        TimeTarget relative = relativeTime(text);
        if (relative != null) return relative;
        return absoluteTime(text, now);
    }

    private static TimeTarget relativeTime(String text) {
        int total = 0;
        Matcher hours = HOURS.matcher(text);
        if (hours.find()) {
            total += number(hours.group(1)) * 60;
            if (hours.group(2) != null) total += 30;
        } else if (HALF_HOUR.matcher(text).find()) {
            total += 30;
        }
        Matcher minutes = MINUTES.matcher(text);
        if (minutes.find()) total += number(minutes.group(1));
        return total > 0 ? new TimeTarget(-1, -1, total, true) : null;
    }

    private static TimeTarget absoluteTime(String text, LocalTime now) {
        Matcher clock = CLOCK.matcher(text);
        if (clock.find()) {
            return new TimeTarget(Integer.parseInt(clock.group(1)),
                    Integer.parseInt(clock.group(2)), 0, false);
        }
        Matcher hour = HOUR.matcher(text);
        if (!hour.find()) return null;
        int value = number(hour.group(1));
        int minute = 0;
        String fraction = hour.group(2);
        if (fraction != null) {
            minute = switch (fraction) {
                case "半" -> 30;
                case "一刻" -> 15;
                case "三刻" -> 45;
                default -> number(hour.group(3));
            };
        }
        if (value < 0 || value > 23 || minute < 0 || minute > 59) return null;
        String prefix = text.substring(0, hour.start());
        if (containsAny(prefix, "下午", "晚上", "傍晚") && value < 12) value += 12;
        if (containsAny(prefix, "中午") && value < 11) value += 12;
        if (containsAny(prefix, "凌晨") && value == 12) value = 0;
        return new TimeTarget(value, minute, 0, false);
    }

    private static int number(String value) {
        if (value == null || value.isBlank()) return -1;
        if (value.matches("\\d+")) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException tooLarge) {
                return -1;
            }
        }
        int ten = value.indexOf('十');
        if (ten < 0) {
            int single = digit(value);
            return single;
        }
        int high = ten == 0 ? 1 : digit(value.substring(0, ten));
        int low = ten == value.length() - 1 ? 0 : digit(value.substring(ten + 1));
        if (high < 0 || low < 0) return -1;
        return high * 10 + low;
    }

    private static int digit(String value) {
        if (value == null || value.length() != 1) return -1;
        return switch (value.charAt(0)) {
            case '零' -> 0;
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    private record TimeTarget(int hour, int minute, int minutes, boolean relative) {
        @Override public String toString() {
            return relative ? minutes + "m"
                    : String.format(Locale.ROOT, "%02d:%02d", hour, minute);
        }
    }

    private record AppWord(String keyword, WatchCommand.WatchApp app) {}
}
