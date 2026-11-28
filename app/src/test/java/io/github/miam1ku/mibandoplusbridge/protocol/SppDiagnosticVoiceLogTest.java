// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class SppDiagnosticVoiceLogTest {
    @Test public void unsupportedFrameLogsAreFirstAndEveryHundredthFrame() {
        int logged = 0;
        for (int count = 1; count <= 250; count++) {
            if (SppDiagnosticClient.shouldLogUnsupportedFrame(count)) logged++;
        }
        assertEquals(3, logged);
        assertTrue(SppDiagnosticClient.shouldLogUnsupportedFrame(1));
        assertTrue(SppDiagnosticClient.shouldLogUnsupportedFrame(100));
        assertTrue(SppDiagnosticClient.shouldLogUnsupportedFrame(200));
        assertFalse(SppDiagnosticClient.shouldLogUnsupportedFrame(2));
        assertFalse(SppDiagnosticClient.shouldLogUnsupportedFrame(99));
        assertFalse(SppDiagnosticClient.shouldLogUnsupportedFrame(101));
    }

    @Test public void strictProtocolRejectsEncryptionStateMismatch() {
        SppDiagnosticClient.Failure thrown = assertThrows(SppDiagnosticClient.Failure.class,
                () -> SppDiagnosticClient.enforceProtocol(true, "ENCRYPTION_STATE_MISMATCH"));
        assertEquals("ENCRYPTION_STATE_MISMATCH", thrown.getMessage());
    }

    @Test public void strictProtocolRejectsUnsupportedChannel() {
        SppDiagnosticClient.Failure thrown = assertThrows(SppDiagnosticClient.Failure.class,
                () -> SppDiagnosticClient.enforceProtocol(true, "UNSUPPORTED_DATA_CHANNEL"));
        assertEquals("UNSUPPORTED_DATA_CHANNEL", thrown.getMessage());
    }

    @Test public void permissiveProtocolObservesInsteadOfThrowing() throws Exception {
        SppDiagnosticClient.enforceProtocol(false, "ENCRYPTION_STATE_MISMATCH");
        SppDiagnosticClient.enforceProtocol(false, "UNSUPPORTED_DATA_CHANNEL");
    }
}
