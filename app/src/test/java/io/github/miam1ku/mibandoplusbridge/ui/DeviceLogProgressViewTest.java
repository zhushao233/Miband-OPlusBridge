// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.ui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class DeviceLogProgressViewTest {
    @Test public void determinateFillMatchesExactPercent() {
        assertEquals(0f, DeviceLogProgressView.fillWidth(1_000, 0), 0.001f);
        assertEquals(180f, DeviceLogProgressView.fillWidth(1_000, 18), 0.001f);
        assertEquals(500f, DeviceLogProgressView.fillWidth(1_000, 50), 0.001f);
        assertEquals(1_000f, DeviceLogProgressView.fillWidth(1_000, 100), 0.001f);
    }

    @Test public void invalidWidthOrOverRangeProgressIsClamped() {
        assertEquals(0f, DeviceLogProgressView.fillWidth(0, 50), 0.001f);
        assertEquals(1_000f, DeviceLogProgressView.fillWidth(1_000, 150), 0.001f);
        assertEquals(0f, DeviceLogProgressView.fillWidth(1_000, -20), 0.001f);
    }
}
