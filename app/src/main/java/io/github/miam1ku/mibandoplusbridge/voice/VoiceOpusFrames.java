// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Length-prefixed Opus extraction and a small Ogg Opus container writer.
 *
 * <p>The band uses 20 ms Opus frames. Ogg granule positions are always expressed on the
 * 48 kHz Opus timeline, so each packet advances by 960 samples even though microphone input
 * is 16 kHz.</p>
 */
public final class VoiceOpusFrames {
    private static final int VOICE_BLOCK_HEADER_BYTES = 4;
    private static final int VOICE_OPUS_PACKET_BYTES = 80;
    private static final int OPUS_TIMELINE_RATE = 48_000;
    private static final int FRAME_MILLIS = 20;
    private static final int FRAME_GRANULE = OPUS_TIMELINE_RATE * FRAME_MILLIS / 1_000;
    private static final int OGG_VERSION = 0;
    private static final int OGG_BOS = 2;
    private static final int OGG_EOS = 4;

    private VoiceOpusFrames() {}

    /**
     * The voice page has historically emitted a one-byte packet length followed by Opus data.
     * A small search window tolerates an optional channel/sequence prefix before that stream.
     */
    public static List<byte[]> extract(byte[] stream) {
        if (stream == null || stream.length < 2) return List.of();
        List<byte[]> fixedRecords = extractFixedRecords(stream);
        if (!fixedRecords.isEmpty()) return fixedRecords;
        List<byte[]> best = List.of();
        int bestScore = Integer.MIN_VALUE;
        int maxOffset = Math.min(16, stream.length - 2);
        for (int offset = 0; offset <= maxOffset; offset++) {
            List<byte[]> parsed = parseFrom(stream, offset);
            if (parsed.isEmpty()) continue;
            int payloadBytes = parsed.stream().mapToInt(packet -> packet.length).sum();
            // Keep short silence frames, but reject a lone two-byte fragment that commonly
            // appears in the control marker emitted before the voice stream.
            if (payloadBytes < 4) continue;
            int score = parsed.size() * 10_000 + payloadBytes - offset;
            if (score > bestScore) {
                bestScore = score;
                best = parsed;
            }
        }
        return best;
    }

    /**
     * Decrypted voice SPP blocks contain one four-byte envelope followed by fixed 80-byte Opus
     * packets. Each packet represents 20 ms, so the Ogg timeline advances by 960 samples.
     */
    private static List<byte[]> extractFixedRecords(byte[] stream) {
        if (stream.length <= VOICE_BLOCK_HEADER_BYTES
                || (stream.length - VOICE_BLOCK_HEADER_BYTES) % VOICE_OPUS_PACKET_BYTES != 0) {
            return List.of();
        }
        List<byte[]> packets = new ArrayList<>();
        for (int offset = VOICE_BLOCK_HEADER_BYTES;
                offset < stream.length; offset += VOICE_OPUS_PACKET_BYTES) {
            byte[] packet = Arrays.copyOfRange(stream, offset, offset + VOICE_OPUS_PACKET_BYTES);
            if (!plausibleOpusPacket(packet)) return List.of();
            packets.add(packet);
        }
        return packets;
    }

    private static boolean plausibleOpusPacket(byte[] packet) {
        if (packet.length == 0) return false;
        int toc = packet[0] & 0xff;
        int frameCode = toc & 3;
        if (frameCode != 3) return true;
        if (packet.length < 2) return false;
        int frameCount = packet[1] & 0x3f;
        return frameCount == 0 || frameCount <= 48;
    }

    public static byte[] toOggOpus(List<byte[]> packets) {
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
            granule += FRAME_GRANULE;
            int headerType = index + 1 == packets.size() ? OGG_EOS : 0;
            writePage(ogg, packets.get(index), granule, serial, sequence++, headerType);
        }
        return ogg.toByteArray();
    }

    private static List<byte[]> parseFrom(byte[] stream, int start) {
        List<byte[]> packets = new ArrayList<>();
        int position = start;
        while (position < stream.length) {
            // Some SPP voice chunks carry a short control header and/or metadata after the
            // length-prefixed Opus frames. Keep every complete packet from the best offset.
            if (position + 1 >= stream.length) break;
            int length = stream[position] & 0xff;
            if (length < 2 || position + 1 + length > stream.length) break;
            packets.add(Arrays.copyOfRange(stream, position + 1, position + 1 + length));
            position += length + 1;
        }
        return packets;
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

    private static int oggCrc(byte[] data) {
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
