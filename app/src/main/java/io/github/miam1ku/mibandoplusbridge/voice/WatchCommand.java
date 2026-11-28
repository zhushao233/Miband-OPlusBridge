// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

/** A locally recognised watch action. Unrecognised speech still goes to the chat model. */
public record WatchCommand(Kind kind, int hour, int minute, int minutes, boolean daily,
                           boolean all, boolean enabled, WatchApp app, boolean confirmed) {
    public enum Kind {
        SET_ALARM,
        SET_TIMER,
        DELETE_NEXT_ALARM,
        DELETE_ALL_ALARMS,
        DELETE_ALARM_AT,
        LIST_ALARMS,
        FIND_PHONE,
        PAUSE_MUSIC,
        SET_DND,
        OPEN_WATCH_APP,
        ASK_ALARM_TIME,
        ASK_DURATION
    }

    /** Built-in app ids exposed by the band firmware. */
    public enum WatchApp {
        FLASHLIGHT("watch.flashlight", "手电筒"),
        ALIPAY("watch.alipay", "支付宝"),
        CARD_WALLET("watch.cardbox", "卡包"),
        WEATHER("watch.weather", "天气"),
        ALARM("watch.alarm", "闹钟"),
        TIMER("watch.timer", "计时器"),
        STOPWATCH("watch.chronograph", "秒表"),
        SETTINGS("watch.settings", "设置"),
        CALENDAR("watch.calendar", "日历"),
        SLEEP("watch.sleepmonitor", "睡眠"),
        HEART_RATE("watch.heartrate", "心率"),
        BLOOD_OXYGEN("watch.bloodoxygen", "血氧"),
        STRESS("watch.stress", "压力"),
        BREATHE("watch.breathtraining", "呼吸训练"),
        CONTACTS("watch.contact", "联系人"),
        TODO("watch.todo", "待办");

        private final String packageName;
        private final String label;

        WatchApp(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }

        public String packageName() {
            return packageName;
        }

        public String label() {
            return label;
        }

        public String uri() {
            return "";
        }
    }

    public static WatchCommand alarm(int hour, int minute, boolean daily) {
        return new WatchCommand(Kind.SET_ALARM, hour, minute, 0, daily, false, false, null, false);
    }

    public static WatchCommand timer(int minutes) {
        return new WatchCommand(Kind.SET_TIMER, -1, -1, minutes, false, false, false, null, false);
    }

    public static WatchCommand deleteAt(int hour, int minute) {
        return new WatchCommand(Kind.DELETE_ALARM_AT, hour, minute, 0, false, false, false,
                null, false);
    }

    public static WatchCommand of(Kind kind) {
        return new WatchCommand(kind, -1, -1, 0, false, false, false, null, false);
    }

    public static WatchCommand deleteAll() {
        return new WatchCommand(Kind.DELETE_ALL_ALARMS, -1, -1, 0, false, true, false,
                null, false);
    }

    public static WatchCommand dnd(boolean enabled) {
        return new WatchCommand(Kind.SET_DND, -1, -1, 0, false, false, enabled, null, false);
    }

    public static WatchCommand app(WatchApp app) {
        return new WatchCommand(Kind.OPEN_WATCH_APP, -1, -1, 0, false, false, true, app, false);
    }

    public WatchCommand withConfirmed(boolean confirmed) {
        if (this.confirmed == confirmed) return this;
        return new WatchCommand(kind, hour, minute, minutes, daily, all, enabled, app, confirmed);
    }
}
