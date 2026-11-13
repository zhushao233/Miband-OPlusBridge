// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BandLiveServiceSleepPolicyTest {
    @Test public void nightWindowCoversTwentyTwoThroughSeven() {
        for (int hour = 22; hour <= 23; hour++) assertTrue(BandLiveService.isNightWindow(hour));
        for (int hour = 0; hour < 8; hour++) assertTrue(BandLiveService.isNightWindow(hour));
        for (int hour = 8; hour < 22; hour++) assertFalse(BandLiveService.isNightWindow(hour));
    }

    @Test public void historyPollIsTwoHoursAtNightAndThirtyMinutesByDay() {
        assertEquals(120, BandLiveService.historyPeriodMinutes(23));
        assertEquals(120, BandLiveService.historyPeriodMinutes(3));
        assertEquals(30, BandLiveService.historyPeriodMinutes(8));
        assertEquals(30, BandLiveService.historyPeriodMinutes(15));
    }
}
