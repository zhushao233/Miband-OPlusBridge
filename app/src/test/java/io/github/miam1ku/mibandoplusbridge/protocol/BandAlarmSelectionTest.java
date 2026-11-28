// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.protocol;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class BandAlarmSelectionTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, Month.NOVEMBER, 27, 8, 0);

    @Test public void nearestAlarmSkipsADisabledEarlierSchedule() {
        var disabled = alarm(1, 8, 5, false, BandScheduleCommand.REPEAT_ONCE, 0);
        var enabled = alarm(2, 9, 0, true, BandScheduleCommand.REPEAT_ONCE, 0);

        assertEquals(enabled, BandAlarmSelection.nearest(List.of(disabled, enabled), NOW));
    }

    @Test public void onlyDisabledAlarmsHaveNoDeletableNearestEntry() {
        var disabled = alarm(1, 9, 0, false, BandScheduleCommand.REPEAT_ONCE, 0);

        assertNull(BandAlarmSelection.nearest(List.of(disabled), NOW));
    }

    @Test public void weeklyAlarmUsesCapturedRepeatModeAndWeekdayMask() {
        var everyDay = alarm(1, 7, 0, true, BandScheduleCommand.REPEAT_WEEKLY, 0b1111111);
        var fridayOnly = alarm(2, 7, 5, true, BandScheduleCommand.REPEAT_WEEKLY, 1 << 4);

        assertEquals(everyDay, BandAlarmSelection.nearest(List.of(fridayOnly, everyDay), NOW));
        assertEquals(23L * 60, BandAlarmSelection.minutesUntil(NOW, everyDay));
    }

    private static BandScheduleCommand.Alarm alarm(
            int id, int hour, int minute, boolean enabled, int repeatMode, int repeatFlags) {
        return new BandScheduleCommand.Alarm(
                id, hour, minute, enabled, repeatMode, repeatFlags);
    }
}
