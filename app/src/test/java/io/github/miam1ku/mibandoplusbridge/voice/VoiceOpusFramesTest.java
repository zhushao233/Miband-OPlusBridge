// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VoiceOpusFramesTest {
    @Test public void extractsOneByteLengthPrefixedPackets() {
        byte[] stream = new byte[] {
                3, 0x0b, 0x21, 0x45,
                2, 0x08, 0x78,
                4, 0x0a, 0x0b, 0x0c, 0x0d
        };

        List<byte[]> packets = VoiceOpusFrames.extract(stream);

        assertEquals(3, packets.size());
        assertArrayEquals(new byte[] {0x0b, 0x21, 0x45}, packets.get(0));
        assertArrayEquals(new byte[] {0x08, 0x78}, packets.get(1));
        assertArrayEquals(new byte[] {0x0a, 0x0b, 0x0c, 0x0d}, packets.get(2));
    }

    @Test public void toleratesOptionalPrefixBeforeFrameStream() {
        byte[] stream = new byte[] {
                0x07, 0x01, 0x00,
                2, 0x08, 0x78,
                2, 0x08, 0x79
        };

        List<byte[]> packets = VoiceOpusFrames.extract(stream);

        assertEquals(2, packets.size());
        assertArrayEquals(new byte[] {0x08, 0x78}, packets.get(0));
        assertArrayEquals(new byte[] {0x08, 0x79}, packets.get(1));
    }

    @Test public void extractsPacketsFromOneSppVoiceBlock() {
        byte[] block = new byte[] {
                0x13, 0x04, 0x00,
                3, 0x0b, 0x21, 0x45,
                2, 0x08, 0x78,
                4, 0x0a, 0x0b, 0x0c, 0x0d,
                0x00, 0x01
        };

        List<byte[]> packets = VoiceOpusFrames.extract(block);

        assertEquals(3, packets.size());
        assertArrayEquals(new byte[] {0x0b, 0x21, 0x45}, packets.get(0));
        assertArrayEquals(new byte[] {0x08, 0x78}, packets.get(1));
        assertArrayEquals(new byte[] {0x0a, 0x0b, 0x0c, 0x0d}, packets.get(2));
    }

    @Test public void extractsFiveFixedOpusRecordsFromDecryptedVoiceBlock() {
        byte[] block = new byte[4 + 5 * 80];
        block[0] = 0x00;
        block[1] = 0x02;
        block[2] = 0x01;
        block[3] = (byte) 0x90;
        for (int frame = 0; frame < 5; frame++) {
            int offset = 4 + frame * 80;
            block[offset] = 0x4b;
            block[offset + 1] = 0x41;
            block[offset + 79] = (byte) frame;
        }

        List<byte[]> packets = VoiceOpusFrames.extract(block);

        assertEquals(5, packets.size());
        assertEquals(80, packets.get(0).length);
        assertEquals(0x4b, packets.get(4)[0] & 0xff);
        assertEquals(4, packets.get(4)[79] & 0xff);
    }

    @Test public void rejectsRandomControlPayload() {
        assertTrue(VoiceOpusFrames.extract(new byte[] {1, 2, 3, 4, 5}).isEmpty());
    }

    @Test public void buildsOggOpusPagesWithCorrectTimeline() {
        byte[] ogg = VoiceOpusFrames.toOggOpus(List.of(
                new byte[] {0x08, 0x78}, new byte[] {0x08, 0x79}));

        assertTrue(ogg.length > 80);
        assertEquals('O', ogg[0]);
        assertEquals('g', ogg[1]);
        assertEquals('g', ogg[2]);
        assertEquals('S', ogg[3]);
        assertFalse(indexOf(ogg, "OpusHead") < 0);
        assertFalse(indexOf(ogg, "OpusTags") < 0);
        assertEquals(4, countPages(ogg));
    }

    private static int countPages(byte[] bytes) {
        int count = 0;
        for (int i = 0; i + 3 < bytes.length; i++) {
            if (bytes[i] == 'O' && bytes[i + 1] == 'g' && bytes[i + 2] == 'g'
                    && bytes[i + 3] == 'S') count++;
        }
        return count;
    }

    private static int indexOf(byte[] bytes, String text) {
        for (int i = 0; i <= bytes.length - text.length(); i++) {
            boolean match = true;
            for (int j = 0; j < text.length(); j++) {
                if (bytes[i + j] != text.charAt(j)) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }
}
