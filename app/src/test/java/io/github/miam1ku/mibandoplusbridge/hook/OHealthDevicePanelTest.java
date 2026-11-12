// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.hook;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OHealthDevicePanelTest {
    @Test public void panelRecognisesMacAndBridgeIdOnOldAndNewHosts() {
        String mac = "A4:C1:38:12:34:56";
        assertTrue(OHealthDeviceHook.panelIdentityMatches(mac, "miband11_x", mac));
        assertTrue(OHealthDeviceHook.panelIdentityMatches(mac, "miband11_x",
                "a4c138123456"));
        assertTrue(OHealthDeviceHook.panelIdentityMatches(mac, "miband11_x", "miband11_x"));
    }

    @Test public void panelDoesNotConfuseAnotherDeviceNameOrModelWithItsIdentity() {
        assertFalse(OHealthDeviceHook.panelIdentityMatches(
                "A4:C1:38:12:34:56", "miband11_x", "Xiaomi Smart Band 10"));
        assertFalse(OHealthDeviceHook.panelIdentityMatches(
                "A4:C1:38:12:34:56", "miband11_x", "OB19B1"));
        assertFalse(OHealthDeviceHook.panelIdentityMatches(
                "A4:C1:38:12:34:56", "miband11_x", "miband11_other"));
    }

    @Test public void wearableLabelsHideOnlyUnsupportedRows() {
        assertTrue(OHealthDeviceHook.isDeviceRowHiddenLabel("我的表盘"));
        assertTrue(OHealthDeviceHook.isDeviceRowHiddenLabel("系统更新"));
        assertTrue(OHealthDeviceHook.isDeviceRowHiddenLabel("闹钟"));
        assertTrue(OHealthDeviceHook.isDeviceRowHiddenLabel("Alarm"));
        assertFalse(OHealthDeviceHook.isDeviceRowHiddenLabel("通知"));
        assertFalse(OHealthDeviceHook.isDeviceRowHiddenLabel("睡眠"));
        assertTrue(OHealthDeviceHook.isDeviceRowHiddenLabel("我的表盘 "));
    }
}
