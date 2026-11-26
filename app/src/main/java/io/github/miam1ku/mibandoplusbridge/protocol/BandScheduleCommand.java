// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.protocol;

import java.util.ArrayList;
import java.util.List;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;

/** Type 17 alarm schedule commands used by the band's native alarm app. */
public final class BandScheduleCommand {
    public static final int TYPE = 17;
    public static final int SUBTYPE_ALARMS_GET = 0;
    public static final int SUBTYPE_ALARM_CREATE = 1;
    public static final int SUBTYPE_ALARM_EDIT = 2;
    public static final int SUBTYPE_ALARM_DELETE = 4;

    public static final int REPEAT_ONCE = 0;
    public static final int REPEAT_DAILY = 1;
    public static final int ALARM_SMART = 1;
    public static final int ALARM_NORMAL = 2;

    private BandScheduleCommand() {}

    public static XiaomiProto.Command getAlarms() {
        return XiaomiProto.Command.newBuilder().setType(TYPE).setSubtype(SUBTYPE_ALARMS_GET).build();
    }

    public static XiaomiProto.Command createAlarm(int hour, int minute, boolean daily) {
        return command(SUBTYPE_ALARM_CREATE,
                XiaomiProto.Schedule.newBuilder().setCreateAlarm(
                        alarmDetails(hour, minute, daily).toBuilder()));
    }

    public static XiaomiProto.Command editAlarm(int id, int hour, int minute, boolean enabled,
                                                boolean daily) {
        if (id <= 0) throw new IllegalArgumentException("ALARM_ID");
        var alarm = XiaomiProto.Alarm.newBuilder().setId(id)
                .setAlarmDetails(alarmDetails(hour, minute, daily).toBuilder().setEnabled(enabled));
        return command(SUBTYPE_ALARM_EDIT, XiaomiProto.Schedule.newBuilder().setEditAlarm(alarm));
    }

    public static XiaomiProto.Command deleteAlarms(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("ALARM_IDS");
        var delete = XiaomiProto.AlarmDelete.newBuilder();
        for (Integer id : ids) {
            if (id == null || id <= 0) throw new IllegalArgumentException("ALARM_ID");
            delete.addId(id);
        }
        return command(SUBTYPE_ALARM_DELETE,
                XiaomiProto.Schedule.newBuilder().setDeleteAlarm(delete));
    }

    public static List<Alarm> parseAlarms(XiaomiProto.Alarms alarms) {
        if (alarms == null) return List.of();
        List<Alarm> parsed = new ArrayList<>(alarms.getAlarmCount());
        for (var alarm : alarms.getAlarmList()) {
            if (!alarm.hasAlarmDetails()) continue;
            var details = alarm.getAlarmDetails();
            if (!details.hasTime()) continue;
            parsed.add(new Alarm(alarm.getId(), details.getTime().getHour(),
                    details.getTime().getMinute(), details.getEnabled(),
                    details.getRepeatMode(), details.getRepeatFlags()));
        }
        return List.copyOf(parsed);
    }

    public record Alarm(int id, int hour, int minute, boolean enabled, int repeatMode,
                        int repeatFlags) {
        public boolean daily() {
            return repeatMode == REPEAT_DAILY;
        }
    }

    private static XiaomiProto.AlarmDetails alarmDetails(int hour, int minute, boolean daily) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("ALARM_TIME");
        }
        return XiaomiProto.AlarmDetails.newBuilder()
                .setTime(XiaomiProto.HourMinute.newBuilder().setHour(hour).setMinute(minute))
                .setRepeatMode(daily ? REPEAT_DAILY : REPEAT_ONCE)
                .setEnabled(true)
                .setSmart(ALARM_NORMAL)
                .build();
    }

    private static XiaomiProto.Command command(int subtype, XiaomiProto.Schedule.Builder schedule) {
        return XiaomiProto.Command.newBuilder().setType(TYPE).setSubtype(subtype)
                .setSchedule(schedule).build();
    }
}
