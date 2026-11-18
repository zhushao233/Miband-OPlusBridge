// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.notify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SleepProtectionScheduleTest {
    @Test public void defaultWindowMatchesTheExistingPreset() {
        var window = SleepProtectionSchedule.defaultWindow();
        assertEquals(10 * 60, window.durationMinutes());
        assertTrue(SleepProtectionSchedule.inCoreWindow(window, 23 * 60));
        assertTrue(SleepProtectionSchedule.inCoreWindow(window, 7 * 60));
        assertFalse(SleepProtectionSchedule.inCoreWindow(window, 8 * 60));
        assertTrue(SleepProtectionSchedule.inSafetyTail(window, 10 * 60));
        assertTrue(SleepProtectionSchedule.inProtectionWindow(window, 13 * 60 + 59));
        assertFalse(SleepProtectionSchedule.inProtectionWindow(window, 14 * 60));
    }

    @Test public void customCoreWindowUsesItsOwnStartAndEnd() {
        var window = new SleepProtectionSchedule.Window(23 * 60, 7 * 60);
        assertFalse(SleepProtectionSchedule.inCoreWindow(window, 22 * 60));
        assertTrue(SleepProtectionSchedule.inCoreWindow(window, 23 * 60 + 30));
        assertTrue(SleepProtectionSchedule.inCoreWindow(window, 6 * 60 + 59));
        assertFalse(SleepProtectionSchedule.inCoreWindow(window, 7 * 60));
    }

    @Test public void missingWakeEvidenceKeepsThePresetSafetyTailUntilFourteen() {
        var window = new SleepProtectionSchedule.Window(23 * 60, 7 * 60);
        assertTrue(SleepProtectionSchedule.inSafetyTail(window, 7 * 60));
        assertTrue(SleepProtectionSchedule.inSafetyTail(window, 13 * 60 + 59));
        assertFalse(SleepProtectionSchedule.inSafetyTail(window, 14 * 60));
        assertTrue(SleepProtectionSchedule.protectedWithoutWakeEvidence(window, 10 * 60, false));
    }

    @Test public void wakeEvidenceEndsProtectionAfterTheCoreWindow() {
        var window = new SleepProtectionSchedule.Window(23 * 60, 7 * 60);
        assertFalse(SleepProtectionSchedule.protectedWithoutWakeEvidence(window, 8 * 60, true));
        assertFalse(SleepProtectionSchedule.protectedWithoutWakeEvidence(window, 6 * 60, true));
    }

    @Test public void coreWindowEndingAfterFourteenNeedsNoExtraTail() {
        var window = new SleepProtectionSchedule.Window(23 * 60, 15 * 60);
        assertTrue(SleepProtectionSchedule.inCoreWindow(window, 14 * 60));
        assertFalse(SleepProtectionSchedule.inSafetyTail(window, 14 * 60));
        assertTrue(SleepProtectionSchedule.inProtectionWindow(window, 14 * 60 + 59));
        assertFalse(SleepProtectionSchedule.inProtectionWindow(window, 15 * 60));

        var endsAtHardBoundary = new SleepProtectionSchedule.Window(22 * 60, 14 * 60);
        assertTrue(SleepProtectionSchedule.inProtectionWindow(endsAtHardBoundary, 13 * 60 + 59));
        assertFalse(SleepProtectionSchedule.inSafetyTail(endsAtHardBoundary, 14 * 60));
        assertFalse(SleepProtectionSchedule.inProtectionWindow(endsAtHardBoundary, 14 * 60));
    }

    @Test public void morningSafetyTailKeepsTheExistingDefaultBehavior() {
        var window = new SleepProtectionSchedule.Window(22 * 60, 6 * 60);
        assertTrue(SleepProtectionSchedule.inSafetyTail(window, 10 * 60));
    }

    @Test public void customEndAfterThePresetMorningWindowHasNoTail() {
        var window = new SleepProtectionSchedule.Window(23 * 60, 10 * 60);
        assertFalse(SleepProtectionSchedule.inSafetyTail(window, 12 * 60));
        assertFalse(SleepProtectionSchedule.inProtectionWindow(window, 12 * 60));
    }

    @Test public void veryEarlyCrossMidnightEndDoesNotExtendIntoTheAfternoon() {
        var window = new SleepProtectionSchedule.Window(23 * 60, 2 * 60);
        assertFalse(SleepProtectionSchedule.inSafetyTail(window, 10 * 60));
        assertFalse(SleepProtectionSchedule.inProtectionWindow(window, 10 * 60));
    }

    @Test public void minimumWindowIsFourHours() {
        assertTrue(SleepProtectionSchedule.isValid(6 * 60, 10 * 60));
        assertFalse(SleepProtectionSchedule.isValid(6 * 60, 9 * 60 + 59));
        assertFalse(SleepProtectionSchedule.isValid(7 * 60, 7 * 60));
    }

    @Test public void closedSleepEvidenceCanBeCheckedAfterFourHours() {
        var window = new SleepProtectionSchedule.Window(22 * 60, 8 * 60);
        assertFalse(SleepProtectionSchedule.reachedMinimumSleep(window, 1 * 60 + 59));
        assertTrue(SleepProtectionSchedule.reachedMinimumSleep(window, 2 * 60));
    }

    @Test public void wholeHourParserFormatsAndRejectsPartialHours() {
        assertEquals(Integer.valueOf(23 * 60), SleepProtectionSchedule.parseHour("23:00"));
        assertEquals(Integer.valueOf(7 * 60), SleepProtectionSchedule.parseHour("7"));
        assertNull(SleepProtectionSchedule.parseHour("7:30"));
        assertNull(SleepProtectionSchedule.parseHour("24:00"));
        assertEquals("07:00", SleepProtectionSchedule.label(7 * 60));
    }
}
