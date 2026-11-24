// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.data;

import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class BandDisplaySettingsTest {
    @Test public void languageCodesNormalizeBeforeProtocolUse() {
        assertEquals("zh_CN", BandDisplaySettings.canonicalLanguage("zh-cn"));
        assertEquals("en_US", BandDisplaySettings.canonicalLanguage(" EN_us "));
        assertEquals("", BandDisplaySettings.canonicalLanguage("fr_FR"));
    }

    @Test public void defaultsFollowCommonBandFirmwareLocales() {
        assertEquals("zh_CN", BandDisplaySettings.defaultLanguage(Locale.SIMPLIFIED_CHINESE));
        assertEquals("en_US", BandDisplaySettings.defaultLanguage(Locale.US));
    }
}
