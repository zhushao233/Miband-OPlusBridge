// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.protocol;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class BandLanguageCommandTest {
    @Test public void languageUsesSystemSubtypeAndCode() {
        var command = BandLanguageCommand.setLanguage("en_US");
        assertEquals(2, command.getType());
        assertEquals(6, command.getSubtype());
        assertEquals("en_US", command.getSystem().getLanguage().getCode());
    }

    @Test public void blankLanguageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BandLanguageCommand.setLanguage(" "));
    }
}
