// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.notify;

import android.content.Context;
import io.github.miam1ku.mibandoplusbridge.data.LocalPrefs;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** User-selected core sleep window, with the existing hard safety tail as a fallback. */
public final class SleepProtectionSchedule {
    public static final int MINUTES_PER_DAY = 24 * 60;
    public static final int MIN_DURATION_MINUTES = 4 * 60;
    public static final int DEFAULT_START_MINUTE = 22 * 60;
    public static final int DEFAULT_END_MINUTE = 8 * 60;
    public static final int PRESET_SAFETY_TAIL_EARLIEST_END_MINUTE = 6 * 60;
    public static final int PRESET_HARD_END_MINUTE = 14 * 60;

    private static final String PREFS = "features";
    private static final String START_KEY = "sleepProtectionStartMinute";
    private static final String END_KEY = "sleepProtectionEndMinute";
    private static final Pattern HOUR = Pattern.compile("^(\\d{1,2})(?::00)?$");

    private SleepProtectionSchedule() {}

    public record Window(int startMinute, int endMinute) {
        public Window {
            startMinute = normalize(startMinute);
            endMinute = normalize(endMinute);
        }

        public int durationMinutes() {
            return forwardMinutes(startMinute, endMinute);
        }

        public String startLabel() {
            return label(startMinute);
        }

        public String endLabel() {
            return label(endMinute);
        }
    }

    public static Window defaultWindow() {
        return new Window(DEFAULT_START_MINUTE, DEFAULT_END_MINUTE);
    }

    public static Window load(Context context) {
        LocalPrefs prefs = LocalPrefs.open(context, PREFS);
        int start = prefs.getInt(START_KEY, DEFAULT_START_MINUTE);
        int end = prefs.getInt(END_KEY, DEFAULT_END_MINUTE);
        return isValid(start, end) ? new Window(start, end) : defaultWindow();
    }

    public static boolean save(Context context, Window window) {
        if (window == null || !isValid(window.startMinute, window.endMinute)) return false;
        return LocalPrefs.open(context, PREFS).edit()
                .putInt(START_KEY, normalize(window.startMinute))
                .putInt(END_KEY, normalize(window.endMinute))
                .commit();
    }

    public static boolean isValid(int startMinute, int endMinute) {
        return forwardMinutes(normalize(startMinute), normalize(endMinute)) >= MIN_DURATION_MINUTES;
    }

    public static int normalize(int minute) {
        return Math.floorMod(minute, MINUTES_PER_DAY);
    }

    public static int forwardMinutes(int fromMinute, int toMinute) {
        return Math.floorMod(normalize(toMinute) - normalize(fromMinute), MINUTES_PER_DAY);
    }

    public static int minuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    public static boolean inCoreWindow(Window window, int minuteOfDay) {
        return forwardMinutes(window.startMinute, minuteOfDay) < window.durationMinutes();
    }

    /**
     * The former morning safety tail remains active only when the core window ends between 06:00
     * and the preset 08:00 end. Later custom ends already provide morning protection; earlier ends
     * such as 02:00 must not stretch a short cross-midnight window into the afternoon.
     */
    public static boolean inSafetyTail(Window window, int minuteOfDay) {
        int end = normalize(window.endMinute);
        if (end < PRESET_SAFETY_TAIL_EARLIEST_END_MINUTE
                || end > DEFAULT_END_MINUTE) return false;
        int minute = normalize(minuteOfDay);
        return minute >= end && minute < PRESET_HARD_END_MINUTE;
    }

    public static boolean inProtectionWindow(Window window, int minuteOfDay) {
        return inCoreWindow(window, minuteOfDay) || inSafetyTail(window, minuteOfDay);
    }

    public static boolean protectedWithoutWakeEvidence(
            Window window, int minuteOfDay, boolean wakeEvidence) {
        return !wakeEvidence && inProtectionWindow(window, minuteOfDay);
    }

    public static boolean reachedMinimumSleep(Window window, int minuteOfDay) {
        return forwardMinutes(window.startMinute, minuteOfDay) >= MIN_DURATION_MINUTES;
    }

    public static String label(int minuteOfDay) {
        int minute = normalize(minuteOfDay);
        return String.format(Locale.ROOT, "%02d:00", minute / 60);
    }

    /** Accepts an hour such as 7, 07, or 07:00. Minutes inside the hour are intentionally excluded. */
    public static Integer parseHour(String value) {
        if (value == null) return null;
        Matcher match = HOUR.matcher(value.trim());
        if (!match.matches()) return null;
        int hour = Integer.parseInt(match.group(1));
        return hour >= 0 && hour < 24 ? hour * 60 : null;
    }
}
