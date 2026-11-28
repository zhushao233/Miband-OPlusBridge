// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Length-prefixed Opus extraction and a small Ogg Opus container writer.
 *
 * <p>Ogg granule positions always use the 48 kHz Opus timeline. The current band sends 20 ms,
 * 80-byte packets (960 samples each), but the duration is read from each packet's TOC so a
 * firmware that changes frame sizes does not silently stretch the timeline.</p>
 */
public final class VoiceOpusFrames {
    /** One 20 ms Opus packet in the band's AIVS audio body. */
    public static final int VOICE_OPUS_PACKET_BYTES = 80;
    private static final int OPUS_TIMELINE_RATE = 48_000;
    private static final int FRAME_MILLIS = 20;
    /** Samples per 20 ms frame on the 48 kHz Opus timeline. */
    public static final int FRAME_GRANULE = OPUS_TIMELINE_RATE * FRAME_MILLIS / 1_000;
    /** Longest duration one Opus packet may carry: 120 ms at 48 kHz. */
    private static final int MAX_PACKET_GRANULE = 5_760;
    private static final int[] SILK_FRAME_SAMPLES = {480, 960, 1_920, 2_880};
    private static final int OGG_VERSION = 0;
    private static final int OGG_BOS = 2;
    private static final int OGG_EOS = 4;

    private VoiceOpusFrames() {}

    /** Splits one or more complete AIVS audio bodies into fixed 80-byte Opus packets. */
    public static List<byte[]> extract(byte[] stream) {
        FixedPacketSplitter splitter = new FixedPacketSplitter();
        List<byte[]> packets = splitter.accept(stream);
        splitter.reset();
        return packets;
    }

    public static boolean plausibleOpusPacket(byte[] packet) {
        if (packet.length == 0) return false;
        int toc = packet[0] & 0xff;
        int frameCode = toc & 3;
        if (frameCode != 3) return true;
        if (packet.length < 2) return false;
        int frameCount = packet[1] & 0x3f;
        return frameCount == 0 || frameCount <= 48;
    }

    /**
     * Carries a partial packet across AIVS audio bodies. A production body is 400 bytes
     * ({@code 5 * 80}); the remainder is kept only for a short final chunk.
     */
    public static final class FixedPacketSplitter {
        private final byte[] pending = new byte[VOICE_OPUS_PACKET_BYTES];
        private final Consumer<String> invalidSink;
        private int pendingBytes;

        public FixedPacketSplitter() {
            this(ignored -> { });
        }

        public FixedPacketSplitter(Consumer<String> invalidSink) {
            this.invalidSink = invalidSink == null ? ignored -> { } : invalidSink;
        }

        public List<byte[]> accept(byte[] block) {
            if (block == null || block.length == 0) return List.of();
            List<byte[]> packets = new ArrayList<>((pendingBytes + block.length)
                    / VOICE_OPUS_PACKET_BYTES);
            int offset = 0;
            if (pendingBytes > 0) {
                int needed = VOICE_OPUS_PACKET_BYTES - pendingBytes;
                int copied = Math.min(needed, block.length);
                System.arraycopy(block, 0, pending, pendingBytes, copied);
                pendingBytes += copied;
                offset += copied;
                if (pendingBytes == VOICE_OPUS_PACKET_BYTES) {
                    addIfPlausible(packets, pending.clone());
                    Arrays.fill(pending, (byte) 0);
                    pendingBytes = 0;
                }
            }
            while (block.length - offset >= VOICE_OPUS_PACKET_BYTES) {
                addIfPlausible(packets, Arrays.copyOfRange(block, offset,
                        offset + VOICE_OPUS_PACKET_BYTES));
                offset += VOICE_OPUS_PACKET_BYTES;
            }
            if (offset < block.length) {
                pendingBytes = block.length - offset;
                System.arraycopy(block, offset, pending, 0, pendingBytes);
            }
            return packets;
        }

        private void addIfPlausible(List<byte[]> packets, byte[] packet) {
            if (plausibleOpusPacket(packet)) {
                packets.add(packet);
                return;
            }
            invalidSink.accept("VOICE_PACKET_INVALID bytes=" + packet.length
                    + " toc=" + String.format(java.util.Locale.ROOT, "%02x", packet[0] & 0xff));
        }

        public byte[] takeRemainder() {
            if (pendingBytes == 0) return new byte[0];
            byte[] remainder = Arrays.copyOf(pending, pendingBytes);
            reset();
            return remainder;
        }

        public void reset() {
            Arrays.fill(pending, (byte) 0);
            pendingBytes = 0;
        }
    }

    public static byte[] toOggOpus(List<byte[]> packets) {
        return toOggOpus(packets, null);
    }

    /**
     * Granule advance for every packet on the 48 kHz Opus timeline.
     *
     * <p>Durations come from each packet's TOC instead of the VAD decoder: the decoder runs on
     * its own thread, drops queued packets under load and can lag behind capture when a session
     * ends, so its sample counts cannot be aligned 1:1 with {@code packets}. The TOC is
     * authoritative and stays aligned even when the splitter drops an invalid packet.</p>
     */
    public static int[] samplesPerPacket(List<byte[]> packets) {
        if (packets == null || packets.isEmpty()) return new int[0];
        int[] samples = new int[packets.size()];
        for (int index = 0; index < packets.size(); index++) {
            samples[index] = samplesForPacket(packets.get(index));
        }
        return samples;
    }

    /** Samples carried by one Opus packet, per RFC 6716 section 3.1. */
    static int samplesForPacket(byte[] packet) {
        if (packet == null || packet.length == 0) return FRAME_GRANULE;
        int toc = packet[0] & 0xff;
        int frameSamples = frameSamplesForConfig(toc >> 3);
        int frameCode = toc & 3;
        int frames;
        if (frameCode == 0) {
            frames = 1;
        } else if (frameCode == 1 || frameCode == 2) {
            frames = 2;
        } else {
            if (packet.length < 2) return frameSamples;
            frames = packet[1] & 0x3f;
            if (frames == 0) return frameSamples;
        }
        long total = (long) frameSamples * frames;
        return (int) Math.max(frameSamples, Math.min(MAX_PACKET_GRANULE, total));
    }

    private static int frameSamplesForConfig(int config) {
        if (config < 12) return SILK_FRAME_SAMPLES[config & 3];
        if (config < 16) return 480 << (config & 1);
        return 120 << (config & 3);
    }

    /**
     * Builds an Ogg Opus stream. {@code samplesPerPacket} carries each packet's granule advance
     * (see {@link #samplesPerPacket}); when it is null or shorter than the packet list, 960
     * samples (20 ms at 48 kHz) is assumed. That fallback is a degenerate assumption for the
     * current 80-byte firmware, not a verified property of other firmwares.
     */
    public static byte[] toOggOpus(List<byte[]> packets, int[] samplesPerPacket) {
        if (packets == null || packets.isEmpty()) {
            throw new IllegalArgumentException("OPUS_PACKETS_REQUIRED");
        }
        ByteArrayOutputStream ogg = new ByteArrayOutputStream();
        int serial = java.util.concurrent.ThreadLocalRandom.current().nextInt();
        int sequence = 0;

        writePage(ogg, opusHead(), 0L, serial, sequence++, OGG_BOS);
        writePage(ogg, opusTags(), 0L, serial, sequence++, 0);
        long granule = 0;
        for (int index = 0; index < packets.size(); index++) {
            int samples = samplesPerPacket != null && index < samplesPerPacket.length
                    ? Math.max(0, samplesPerPacket[index]) : FRAME_GRANULE;
            granule += samples;
            int headerType = index + 1 == packets.size() ? OGG_EOS : 0;
            writePage(ogg, packets.get(index), granule, serial, sequence++, headerType);
        }
        return ogg.toByteArray();
    }

    private static byte[] opusTags() {
        byte[] vendor = "Miband OPlusBridge".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream tags = new ByteArrayOutputStream();
        writeAscii(tags, "OpusTags");
        writeIntLE(tags, vendor.length);
        tags.writeBytes(vendor);
        writeIntLE(tags, 0);
        return tags.toByteArray();
    }

    private static byte[] opusHead() {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        writeAscii(head, "OpusHead");
        head.write(1);
        head.write(1);
        writeShortLE(head, 312);
        writeIntLE(head, 16_000);
        writeShortLE(head, 0);
        head.write(0);
        return head.toByteArray();
    }

    private static void writePage(ByteArrayOutputStream out, byte[] packet, long granule,
                                  int serial, int sequence, int headerType) {
        if (packet.length == 0 || packet.length > 255) {
            throw new IllegalArgumentException("OPUS_PAGE_PACKET_SIZE");
        }
        byte[] page = new byte[27 + 1 + packet.length];
        int p = 0;
        page[p++] = 'O';
        page[p++] = 'g';
        page[p++] = 'g';
        page[p++] = 'S';
        page[p++] = (byte) OGG_VERSION;
        page[p++] = (byte) headerType;
        putLongLE(page, p, granule);
        p += 8;
        putIntLE(page, p, serial);
        p += 4;
        putIntLE(page, p, sequence);
        p += 4;
        p += 4; // CRC placeholder
        page[p++] = 1;
        page[p++] = (byte) packet.length;
        System.arraycopy(packet, 0, page, p, packet.length);
        int crc = oggCrc(page);
        putIntLE(page, 22, crc);
        out.writeBytes(page);
    }

    static int oggCrc(byte[] data) {
        int crc = 0;
        for (byte value : data) {
            crc ^= (value & 0xff) << 24;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80000000) != 0 ? (crc << 1) ^ 0x04c11db7 : crc << 1;
            }
        }
        return crc;
    }

    private static void putLongLE(byte[] target, int offset, long value) {
        for (int i = 0; i < 8; i++) target[offset + i] = (byte) (value >>> (i * 8));
    }

    private static void putIntLE(byte[] target, int offset, int value) {
        for (int i = 0; i < 4; i++) target[offset + i] = (byte) (value >>> (i * 8));
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void writeShortLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }
}
