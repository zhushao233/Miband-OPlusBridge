// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits the decrypted SPP voice channel into the band's AIVS commands.
 *
 * <p>Wire format: {@code version(1) | type(1) | length(2, big-endian) | data}.
 * Version is 0 on this firmware. Audio data is five 80-byte Opus packets per 400-byte body.</p>
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

    private byte[] pending = new byte[0];

    public List<Command> feed(byte[] chunk) {
        if (chunk == null || chunk.length == 0) return List.of();
        byte[] merged = new byte[pending.length + chunk.length];
        System.arraycopy(pending, 0, merged, 0, pending.length);
        System.arraycopy(chunk, 0, merged, pending.length, chunk.length);
        List<Command> commands = new ArrayList<>();
        int offset = 0;
        while (merged.length - offset >= HEADER_BYTES) {
            int version = merged[offset] & 0xff;
            int type = merged[offset + 1] & 0xff;
            int length = ((merged[offset + 2] & 0xff) << 8) | (merged[offset + 3] & 0xff);
            if (version != 0 || type < TYPE_START || type > TYPE_QUIT
                    || length > MAX_BODY_BYTES) {
                // A malformed block cannot be resynchronised reliably inside an encrypted stream.
                pending = new byte[0];
                return commands;
            }
            if (merged.length - offset - HEADER_BYTES < length) break;
            byte[] body = Arrays.copyOfRange(merged, offset + HEADER_BYTES,
                    offset + HEADER_BYTES + length);
            commands.add(new Command(type, body));
            offset += HEADER_BYTES + length;
        }
        pending = offset == 0 ? merged : Arrays.copyOfRange(merged, offset, merged.length);
        return commands;
    }

    public void reset() {
        pending = new byte[0];
    }
}
