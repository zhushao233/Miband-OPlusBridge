// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.protocol;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Chooses the next ringing alarm without ever selecting a disabled schedule. */
public final class BandAlarmSelection {
    private BandAlarmSelection() {}

    public static BandScheduleCommand.Alarm nearest(
            List<BandScheduleCommand.Alarm> entries, LocalDateTime now) {
        if (entries == null || entries.isEmpty() || now == null) return null;
        BandScheduleCommand.Alarm best = null;
        long bestMinutes = Long.MAX_VALUE;
        for (BandScheduleCommand.Alarm alarm : entries) {
            if (!alarm.enabled()) continue;
            long minutes = minutesUntil(now, alarm);
            if (minutes < bestMinutes) {
                bestMinutes = minutes;
                best = alarm;
            }
        }
        return best;
    }

    static long minutesUntil(LocalDateTime now, BandScheduleCommand.Alarm alarm) {
        int targetDay = now.getDayOfWeek().getValue();
        for (int delta = 0; delta <= 7; delta++) {
            int day = ((targetDay - 1 + delta) % 7) + 1;
            if (alarm.repeatMode() == BandScheduleCommand.REPEAT_WEEKLY
                    && (alarm.repeatFlags() & (1 << (day - 1))) == 0) {
                continue;
            }
            LocalDateTime target = now.toLocalDate().plusDays(delta)
                    .atTime(alarm.hour(), alarm.minute());
            if (!target.isAfter(now)) continue;
            return Duration.between(now, target).toMinutes();
        }
        return Long.MAX_VALUE;
    }
}
