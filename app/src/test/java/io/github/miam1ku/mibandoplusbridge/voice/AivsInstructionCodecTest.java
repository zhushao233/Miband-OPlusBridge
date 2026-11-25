// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public final class AivsInstructionCodecTest {
    @Test public void encodesFinalAsrResultWithSessionId() {
        assertArrayEquals(new byte[] {
                0x08, 0x0e, 0x10, 0x00, (byte) 0x82, 0x01, 0x18,
                0x0a, 0x16,
                0x0a, 0x14,
                0x08, 0x02,
                0x12, 0x0e,
                0x08, 0x01,
                0x12, 0x0a,
                0x0a, 0x08,
                0x0a, 0x06, (byte) 0xe4, (byte) 0xbd, (byte) 0xa0,
                (byte) 0xe5, (byte) 0xa5, (byte) 0xbd,
                0x68, 0x00
        }, AivsInstructionCodec.asr(0, "你好", true));
    }

    @Test public void encodesToastAndDialogFinishInOneWrapper() {
        assertArrayEquals(new byte[] {
                0x08, 0x0e, 0x10, 0x00, (byte) 0x82, 0x01, 0x15,
                0x0a, 0x13,
                0x0a, 0x0b,
                0x08, (byte) 0x80, 0x02,
                0x1a, 0x04, 0x0a, 0x02, 'h', 'i',
                0x68, 0x00,
                0x0a, 0x04,
                0x08, 0x04, 0x68, 0x00
        }, AivsInstructionCodec.answer(0, "hi"));
    }

    @Test public void encodesTtsAsItsOwnInstruction() {
        assertArrayEquals(new byte[] {
                0x08, 0x0e, 0x10, 0x00, (byte) 0x82, 0x01, 0x0f,
                0x0a, 0x0d,
                0x0a, 0x0b,
                0x08, (byte) 0x88, 0x02,
                0x5a, 0x04, 0x0a, 0x02, 'h', 'i',
                0x68, 0x00
        }, AivsInstructionCodec.tts(0, "hi"));
    }
}
