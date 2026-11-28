// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.time.LocalTime;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WatchCommandParserTest {
    private static final LocalTime NOW = LocalTime.of(10, 15);

    @Test public void absoluteAlarmSupportsChineseHoursAndPeriods() {
        var morning = WatchCommandParser.parse("设置明早七点的闹钟", NOW);
        assertEquals(WatchCommand.Kind.SET_ALARM, morning.kind());
        assertEquals(7, morning.hour());
        assertEquals(0, morning.minute());

        var evening = WatchCommandParser.parse("晚上八点半叫我", NOW);
        assertEquals(20, evening.hour());
        assertEquals(30, evening.minute());

        var afternoon = WatchCommandParser.parse("下午三点一刻提醒我", NOW);
        assertEquals(15, afternoon.hour());
        assertEquals(15, afternoon.minute());
    }

    @Test public void colonTimeAndDailyRepeatAreRecognised() {
        var daily = WatchCommandParser.parse("每天 07:30 的闹钟", NOW);
        assertEquals(WatchCommand.Kind.SET_ALARM, daily.kind());
        assertEquals(7, daily.hour());
        assertEquals(30, daily.minute());
        assertEquals(true, daily.daily());
    }

    @Test public void durationBecomesAOneShotReminder() {
        var ten = WatchCommandParser.parse("计时十分钟", NOW);
        assertEquals(WatchCommand.Kind.SET_TIMER, ten.kind());
        assertEquals(10, ten.minutes());

        var half = WatchCommandParser.parse("半小时后提醒我", NOW);
        assertEquals(WatchCommand.Kind.SET_TIMER, half.kind());
        assertEquals(30, half.minutes());

        var ninety = WatchCommandParser.parse("一个半小时后叫我", NOW);
        assertEquals(90, ninety.minutes());
    }

    @Test public void deleteListAndAllCommandsAreSeparated() {
        assertEquals(WatchCommand.Kind.DELETE_NEXT_ALARM,
                WatchCommandParser.parse("关闭闹钟", NOW).kind());

        var atSeven = WatchCommandParser.parse("取消明早七点的闹钟", NOW);
        assertEquals(WatchCommand.Kind.DELETE_ALARM_AT, atSeven.kind());
        assertEquals(7, atSeven.hour());

        assertEquals(WatchCommand.Kind.DELETE_ALL_ALARMS,
                WatchCommandParser.parse("取消所有闹钟", NOW).kind());
        assertFalse(WatchCommandParser.parse("取消所有闹钟", NOW).confirmed());
        assertEquals(WatchCommand.Kind.LIST_ALARMS,
                WatchCommandParser.parse("查看闹钟", NOW).kind());
    }

    @Test public void destructiveCommandsRequireAnExplicitConfirmationKeyword() {
        var all = WatchCommandParser.parse("我确认取消所有闹钟", NOW);
        assertEquals(WatchCommand.Kind.DELETE_ALL_ALARMS, all.kind());
        assertTrue(all.confirmed());

        var next = WatchCommandParser.parse("确认取消闹钟", NOW);
        assertEquals(WatchCommand.Kind.DELETE_NEXT_ALARM, next.kind());
        assertTrue(next.confirmed());

        assertFalse(WatchCommandParser.parse("取消闹钟", NOW).confirmed());
    }

    @Test public void missingTimeOrDurationAsksForIt() {
        assertEquals(WatchCommand.Kind.ASK_ALARM_TIME,
                WatchCommandParser.parse("设个闹钟", NOW).kind());
        assertEquals(WatchCommand.Kind.ASK_DURATION,
                WatchCommandParser.parse("开始计时", NOW).kind());
    }

    @Test public void openAppCommandsUseFirmwarePackageIds() {
        var flashlight = WatchCommandParser.parse("打开手电筒", NOW);
        assertEquals(WatchCommand.Kind.OPEN_WATCH_APP, flashlight.kind());
        assertEquals(WatchCommand.WatchApp.FLASHLIGHT, flashlight.app());

        var alipay = WatchCommandParser.parse("打开支付宝", NOW);
        assertEquals(WatchCommand.WatchApp.ALIPAY, alipay.app());

        var card = WatchCommandParser.parse("打开卡包", NOW);
        assertEquals(WatchCommand.WatchApp.CARD_WALLET, card.app());

        var alarmApp = WatchCommandParser.parse("打开闹钟", NOW);
        assertEquals(WatchCommand.WatchApp.ALARM, alarmApp.app());
    }

    @Test public void dndOnAndOffAreSeparated() {
        var on = WatchCommandParser.parse("打开勿扰", NOW);
        assertEquals(WatchCommand.Kind.SET_DND, on.kind());
        assertEquals(true, on.enabled());
        assertFalse(on.confirmed());

        var off = WatchCommandParser.parse("关闭勿扰模式", NOW);
        assertEquals(WatchCommand.Kind.SET_DND, off.kind());
        assertEquals(false, off.enabled());
    }

    @Test public void phoneAndMusicCommandsDoNotFallThroughToTheModel() {
        assertEquals(WatchCommand.Kind.FIND_PHONE,
                WatchCommandParser.parse("我的手机在哪里", NOW).kind());
        assertEquals(WatchCommand.Kind.FIND_PHONE,
                WatchCommandParser.parse("查找我的手机", NOW).kind());
        assertEquals(WatchCommand.Kind.PAUSE_MUSIC,
                WatchCommandParser.parse("暂停音乐", NOW).kind());
    }

    @Test public void ordinaryQuestionsAreNotIntercepted() {
        assertNull(WatchCommandParser.parse("今天适合跑步吗", NOW));
        assertNull(WatchCommandParser.parse("给我讲个笑话", NOW));
    }

    @Test public void alarmTimesAcceptArabicChineseAndFullWidthDigits() {
        assertAlarm("设置7点的闹钟", 7, 0);
        assertAlarm("七点的闹钟", 7, 0);
        assertAlarm("两点的闹钟", 2, 0);
        assertAlarm("十点的闹钟", 10, 0);
        assertAlarm("二十三点的闹钟", 23, 0);
        assertAlarm("7:30的闹钟", 7, 30);
        assertAlarm("７：３０的闹钟", 7, 30);
    }

    @Test public void alarmTimeFractionsAreParsed() {
        assertAlarm("七点半的闹钟", 7, 30);
        assertAlarm("七点一刻的闹钟", 7, 15);
        assertAlarm("七点三刻的闹钟", 7, 45);
        assertAlarm("七点三十分的闹钟", 7, 30);
        assertAlarm("七点五分的闹钟", 7, 5);
    }

    @Test public void periodPrefixesShiftTheHour() {
        assertAlarm("明早七点的闹钟", 7, 0);
        assertAlarm("上午十点的闹钟", 10, 0);
        assertAlarm("下午三点的闹钟", 15, 0);
        assertAlarm("晚上八点的闹钟", 20, 0);
        assertAlarm("中午一点的闹钟", 13, 0);
        assertAlarm("中午十二点的闹钟", 12, 0);
        assertAlarm("凌晨十二点的闹钟", 0, 0);
        assertAlarm("凌晨两点的闹钟", 2, 0);
    }

    @Test public void intentVerbChoosesTheSameAlarmTarget() {
        assertAlarm("设置七点的闹钟", 7, 0);
        assertAlarm("设七点的闹钟", 7, 0);
        assertAlarm("定七点的闹钟", 7, 0);
        assertAlarm("七点的闹钟", 7, 0);

        assertEquals(WatchCommand.WatchApp.ALARM,
                WatchCommandParser.parse("打开闹钟", NOW).app());
        assertEquals(WatchCommand.Kind.LIST_ALARMS,
                WatchCommandParser.parse("看看闹钟", NOW).kind());

        assertEquals(WatchCommand.Kind.DELETE_ALARM_AT,
                WatchCommandParser.parse("取消七点的闹钟", NOW).kind());
        assertEquals(WatchCommand.Kind.DELETE_ALARM_AT,
                WatchCommandParser.parse("删除七点的闹钟", NOW).kind());
        assertEquals(WatchCommand.Kind.DELETE_ALARM_AT,
                WatchCommandParser.parse("关闭七点的闹钟", NOW).kind());
    }

    @Test public void repeatWordsSetTheDailyFlag() {
        assertTrue(WatchCommandParser.parse("每天七点的闹钟", NOW).daily());
        assertTrue(WatchCommandParser.parse("每日七点的闹钟", NOW).daily());
        assertFalse(WatchCommandParser.parse("七点的闹钟", NOW).daily());
    }

    @Test public void durationsCombineHoursMinutesAndFractions() {
        assertTimer("计时十分钟", 10);
        assertTimer("半小时后提醒我", 30);
        assertTimer("一个半小时后叫我", 90);
        assertTimer("一小时20分钟后提醒我", 80);
        assertTimer("两小时后叫我", 120);
        assertTimer("计时二十三分钟", 23);
    }

    @Test public void confirmedFlagOnlyFollowsTheConfirmationWord() {
        var all = WatchCommandParser.parse("确认取消所有闹钟", NOW);
        assertEquals(WatchCommand.Kind.DELETE_ALL_ALARMS, all.kind());
        assertTrue(all.confirmed());

        var next = WatchCommandParser.parse("取消闹钟", NOW);
        assertEquals(WatchCommand.Kind.DELETE_NEXT_ALARM, next.kind());
        assertFalse(next.confirmed());

        var nextConfirmed = WatchCommandParser.parse("确认取消最近一个闹钟", NOW);
        assertEquals(WatchCommand.Kind.DELETE_NEXT_ALARM, nextConfirmed.kind());
        assertTrue(nextConfirmed.confirmed());
    }

    @Test public void impossibleTimesAndQuestionsReachTheModel() {
        assertNull(WatchCommandParser.parse("25点的闹钟", NOW));
        assertNull(WatchCommandParser.parse("设置七点六十分", NOW));
        assertNull(WatchCommandParser.parse("现在几点了", NOW));
        assertNull(WatchCommandParser.parse("今天适合跑步吗", NOW));
    }

    private static void assertAlarm(String text, int hour, int minute) {
        WatchCommand command = WatchCommandParser.parse(text, NOW);
        assertEquals(text, WatchCommand.Kind.SET_ALARM, command.kind());
        assertEquals(text, hour, command.hour());
        assertEquals(text, minute, command.minute());
    }

    private static void assertTimer(String text, int minutes) {
        WatchCommand command = WatchCommandParser.parse(text, NOW);
        assertEquals(text, WatchCommand.Kind.SET_TIMER, command.kind());
        assertEquals(text, minutes, command.minutes());
    }
}
