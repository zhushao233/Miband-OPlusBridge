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
        assertTrue(OHealthDeviceHook.panelIdentityMatches(mac, "", "miband11_other"));
    }

    @Test public void panelDoesNotConfuseAnotherDeviceNameOrModelWithItsIdentity() {
        assertFalse(OHealthDeviceHook.panelIdentityMatches(
                "A4:C1:38:12:34:56", "miband11_x", "Xiaomi Smart Band 10"));
        assertFalse(OHealthDeviceHook.panelIdentityMatches(
                "A4:C1:38:12:34:56", "miband11_x", "OB19B1"));
    }
}
