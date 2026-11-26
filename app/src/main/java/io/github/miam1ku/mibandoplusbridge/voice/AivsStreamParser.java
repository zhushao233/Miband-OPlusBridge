// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits the decrypted SPP voice channel into the band's AIVS commands.
 *
 * <p>Wire format: {@code version(1) | type(1) | length(2, big-endian) | data}.
 * Version is 0 on this firmware. Audio data is five fixed 80-byte Opus packets per
 * 400-byte body; the block header is not part of the audio body.</p>
 */
public final class AivsStreamParser {
    public static final int TYPE_START = 1;
    public static final int TYPE_AUDIO = 2;
    public static final int TYPE_END = 3;
    public static final int TYPE_QUIT = 4;
    private static final int HEADER_BYTES = 4;
    private static final int MAX_BODY_BYTES = 16 * 1024;

    public record Command(int type, byte[] data) {
        public Command {
            data = data == null ? new byte[0] : data.clone();
        }

        @Override public byte[] data() {
            return data.clone();
        }

        public int sessionId() {
            return data.length == 0 ? 0 : data[0] & 0xff;
        }

        public boolean ttsEnabled() {
            return data.length > 1 && data[1] == 1;
        }
    }

    private String malformed = "";

    public List<Command> feed(byte[] chunk) {
        if (chunk == null || chunk.length == 0) return List.of();
        List<Command> commands = new ArrayList<>();
        int offset = 0;
        while (chunk.length - offset >= HEADER_BYTES) {
            int version = chunk[offset] & 0xff;
            int type = chunk[offset + 1] & 0xff;
            int length = ((chunk[offset + 2] & 0xff) << 8) | (chunk[offset + 3] & 0xff);
            if (version != 0 || type < TYPE_START || type > TYPE_QUIT
                    || length > MAX_BODY_BYTES) {
                malformed = "version=" + version + " type=" + type + " length=" + length
                        + " available=" + (chunk.length - offset);
                return commands;
            }
            if (chunk.length - offset - HEADER_BYTES < length) {
                // The band emits whole AIVS blocks per SPP message. A short tail belongs to the
                // closing session and must not be stitched onto the next session's START block.
                malformed = "truncated version=" + version + " type=" + type
                        + " length=" + length + " available=" + (chunk.length - offset);
                return commands;
            }
            byte[] body = Arrays.copyOfRange(chunk, offset + HEADER_BYTES,
                    offset + HEADER_BYTES + length);
            commands.add(new Command(type, body));
            offset += HEADER_BYTES + length;
        }
        if (offset < chunk.length) {
            malformed = "tail bytes=" + (chunk.length - offset);
        }
        return commands;
    }

    public void reset() {
        malformed = "";
    }

    /** Returns and clears the last discarded block, for a single low-frequency diagnostic line. */
    public String takeMalformed() {
        String value = malformed;
        malformed = "";
        return value;
    }
}
