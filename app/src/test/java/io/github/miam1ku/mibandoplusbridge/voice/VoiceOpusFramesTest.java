// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VoiceOpusFramesTest {
    /**
     * First 400-byte audio body of voice-1795629664387.raw from the 2026-11-27 band capture.
     * TOCs are 0x4b, 0x4b, 0x4b, 0x4b, 0x48 at offsets 0, 80, 160, 240 and 320.
     */
    private static final String CAPTURED_AUDIO_BODY =
            "4b410481a7a349c4b2c82a2c2f3761056f61958956bb12576f4900d97f428682"
            + "515dff491d6d7a301df4a86f23f6784c1fa4511676e86808654359a02e65fd66"
            + "60e4e5f3ec3a494c21a5a380000000004b41269e1e601be477885ee45e38c5ea"
            + "fd33f7e76daf1534edb480bc0a7c7caa9bbfc57fad0131fa52c0000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "4b410498dddf3c691e362596a5e45a097326cfbe34317b160131b8e6e8402d95"
            + "b7e4e8a6aaeb2f915b252e008c393e13198cf5bb3c8bbdfd4eaf7d80f6779c9c"
            + "bd8d798740c7f4125820f328000000004b0100fbf60fab2228f0ba7ccbec6736"
            + "373ae6b16b397c7dfb835ac3a92e6039777a657fb98e9cf85a37e2ea4a6c9fba"
            + "c32fb6d115328cccef4a8e92d4d50a5e533b5ae676d7d4da95311d60f839d7c7"
            + "4800ff6acfb55088689af7c770b7b9a9ea7a57b61496e094d0e0cdb6af649c9d"
            + "76b5e4cdd4fba61c7f262794753f88aca5d26ec30582346e34172ca65411d48b"
            + "8f5348b16c34bcf81193be44d3209a80";

    @Test public void extractsFiveFixedPacketsFromCapturedAivsBody() {
        byte[] body = hex(CAPTURED_AUDIO_BODY);

        List<byte[]> packets = VoiceOpusFrames.extract(body);

        assertEquals(5, packets.size());
        for (byte[] packet : packets) {
            assertEquals(VoiceOpusFrames.VOICE_OPUS_PACKET_BYTES, packet.length);
            assertTrue(VoiceOpusFrames.plausibleOpusPacket(packet));
        }
        assertEquals(0x4b, packets.get(0)[0] & 0xff);
        assertEquals(0x4b, packets.get(1)[0] & 0xff);
        assertEquals(0x4b, packets.get(2)[0] & 0xff);
        assertEquals(0x4b, packets.get(3)[0] & 0xff);
        assertEquals(0x48, packets.get(4)[0] & 0xff);
        assertArrayEquals(java.util.Arrays.copyOfRange(body, 80, 160), packets.get(1));
    }

    @Test public void splitterCarriesAPacketAcrossTwoAivsBodies() {
        byte[] body = hex(CAPTURED_AUDIO_BODY);
        VoiceOpusFrames.FixedPacketSplitter splitter =
                new VoiceOpusFrames.FixedPacketSplitter();

        assertTrue(splitter.accept(java.util.Arrays.copyOfRange(body, 0, 200)).size() == 2);
        List<byte[]> packets = splitter.accept(java.util.Arrays.copyOfRange(body, 200, 400));

        assertEquals(3, packets.size());
        assertArrayEquals(java.util.Arrays.copyOfRange(body, 160, 240), packets.get(0));
        assertArrayEquals(java.util.Arrays.copyOfRange(body, 240, 320), packets.get(1));
        assertArrayEquals(java.util.Arrays.copyOfRange(body, 320, 400), packets.get(2));
    }

    @Test public void splitterDropsAnIncompletePacketAtSessionEnd() {
        VoiceOpusFrames.FixedPacketSplitter splitter =
                new VoiceOpusFrames.FixedPacketSplitter();

        assertTrue(splitter.accept(new byte[] {0x4b, 0x41, 0x00}).isEmpty());
        assertEquals(3, splitter.takeRemainder().length);
        assertEquals(0, splitter.takeRemainder().length);
    }

    @Test public void rejectsRandomControlPayload() {
        assertTrue(VoiceOpusFrames.extract(new byte[] {1, 2, 3, 4, 5}).isEmpty());
    }

    @Test public void splitterSkipsAnInvalidPacketAndKeepsTheNextOneAligned() {
        java.util.List<String> rejected = new java.util.ArrayList<>();
        VoiceOpusFrames.FixedPacketSplitter splitter =
                new VoiceOpusFrames.FixedPacketSplitter(rejected::add);
        byte[] block = hex(CAPTURED_AUDIO_BODY);
        block[0] = 0x03;
        block[1] = 0x3f;

        List<byte[]> packets = splitter.accept(block);

        assertEquals(4, packets.size());
        assertEquals(1, rejected.size());
        assertTrue(rejected.get(0).startsWith("VOICE_PACKET_INVALID"));
        assertEquals(0x4b, packets.get(0)[0] & 0xff);
    }

    @Test public void splitterJoinsThirtyAndFiftyByteChunksIntoOnePacket() {
        byte[] body = hex(CAPTURED_AUDIO_BODY);
        VoiceOpusFrames.FixedPacketSplitter splitter =
                new VoiceOpusFrames.FixedPacketSplitter();

        assertTrue(splitter.accept(java.util.Arrays.copyOfRange(body, 0, 30)).isEmpty());
        List<byte[]> packets = splitter.accept(java.util.Arrays.copyOfRange(body, 30, 80));

        assertEquals(1, packets.size());
        assertArrayEquals(java.util.Arrays.copyOfRange(body, 0, 80), packets.get(0));
    }

    @Test public void rejectsATocWhoseFrameCountExceedsTheSpecifiedLimit() {
        byte[] packet = new byte[VoiceOpusFrames.VOICE_OPUS_PACKET_BYTES];
        packet[0] = 0x03;
        packet[1] = 0x3f;
        assertFalse(VoiceOpusFrames.plausibleOpusPacket(packet));
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

    @Test public void perPacketSamplesDriveTheGranuleTimeline() {
        byte[] ogg = VoiceOpusFrames.toOggOpus(
                List.of(new byte[] {0x08, 0x78}, new byte[] {0x08, 0x79}),
                new int[] {480, 480});

        assertEquals(4, countPages(ogg));
        assertEquals(480L, granuleOfPage(ogg, 2));
        assertEquals(960L, granuleOfPage(ogg, 3));
    }

    private static long granuleOfPage(byte[] ogg, int pageIndex) {
        int page = 0;
        for (int i = 0; i + 3 < ogg.length; i++) {
            if (ogg[i] != 'O' || ogg[i + 1] != 'g' || ogg[i + 2] != 'g'
                    || ogg[i + 3] != 'S') continue;
            if (page++ != pageIndex) continue;
            long value = 0;
            for (int b = 0; b < 8; b++) {
                value |= (long) (ogg[i + 6 + b] & 0xff) << (b * 8);
            }
            return value;
        }
        return -1;
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

    private static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) Integer.parseInt(
                    value.substring(index * 2, index * 2 + 2), 16);
        }
        return bytes;
    }
}
