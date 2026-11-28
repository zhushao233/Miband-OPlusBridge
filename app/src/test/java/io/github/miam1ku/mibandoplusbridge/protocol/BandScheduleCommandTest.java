// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.protocol;

import java.util.List;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BandScheduleCommandTest {
    @Test public void createAlarmCarriesTimeRepeatAndNormalSmartMode() {
        var command = BandScheduleCommand.createAlarm(7, 30, true);
        var details = command.getSchedule().getCreateAlarm();
        assertEquals(17, command.getType());
        assertEquals(1, command.getSubtype());
        assertEquals(7, details.getTime().getHour());
        assertEquals(30, details.getTime().getMinute());
        assertEquals(BandScheduleCommand.REPEAT_DAILY, details.getRepeatMode());
        assertEquals(BandScheduleCommand.ALARM_NORMAL, details.getSmart());
        assertTrue(details.getEnabled());
    }

    @Test public void deleteCarriesEveryIdAndRejectsEmptyInput() {
        var delete = BandScheduleCommand.deleteAlarms(List.of(2, 5))
                .getSchedule().getDeleteAlarm();
        assertEquals(List.of(2, 5), delete.getIdList());
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> BandScheduleCommand.deleteAlarms(List.of()));
    }

    @Test public void parseAlarmsSkipsEntriesWithoutTime() {
        var valid = XiaomiProto.Alarm.newBuilder().setId(3)
                .setAlarmDetails(XiaomiProto.AlarmDetails.newBuilder()
                        .setTime(XiaomiProto.HourMinute.newBuilder().setHour(6).setMinute(45))
                        .setEnabled(true).setRepeatMode(BandScheduleCommand.REPEAT_ONCE)
                        .setRepeatFlags(0))
                .build();
        var invalid = XiaomiProto.Alarm.newBuilder().setId(4)
                .setAlarmDetails(XiaomiProto.AlarmDetails.newBuilder().setEnabled(true)).build();
        var parsed = BandScheduleCommand.parseAlarms(XiaomiProto.Alarms.newBuilder()
                .addAlarm(valid).addAlarm(invalid).build());
        assertEquals(1, parsed.size());
        assertEquals(3, parsed.get(0).id());
        assertEquals(6, parsed.get(0).hour());
        assertEquals(45, parsed.get(0).minute());
        assertFalse(parsed.get(0).daily());
    }
}
