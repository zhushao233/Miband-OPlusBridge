// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal encoder for the AIVS protobuf used by Xiaomi Health's Bluetooth assistant.
 *
 * <pre>
 * oyt { field 1 = 14; field 2 = 0; field 16 = p21 }
 * p21 { field 1 = s21.a }
 * s21.a { repeated field 1 = s21 }
 * s21 { field 1 = code; field 2 = a41; field 3 = n41; field 11 = c41; field 13 = sessionId }
 * </pre>
 *
 * <p>{@code field 1 = 14} is the Xiaomi protobuf command type, not an accidental collision.
 * These outbound instructions are intentionally a valid {@code XiaomiProto.Command(type=14)}
 * envelope on the encrypted protobuf channel. The band's microphone stream is the opposite
 * direction and arrives on the voice channel as AIVS v0 blocks. Do not use "parses as a
 * Command" as a routing signal in either direction; {@code AivsInstructionCodecTest} pins this
 * ambiguity.</p>
 */
public final class AivsInstructionCodec {
    public static final int CODE_ASR_RESULT = 2;
    public static final int CODE_STOP_CAPTURE = 3;
    public static final int CODE_DIALOG_FINISH = 4;
    public static final int CODE_TOAST = 256;
    public static final int CODE_OPEN_APP = 266;
    public static final int CODE_TTS = 264;

    private AivsInstructionCodec() {}

    public static byte[] asr(int sessionId, String text, boolean isFinal) {
        byte[] b41 = stringMessage(1, text);
        byte[] candidates = repeatedMessage(1, List.of(b41));
        ByteArrayOutputStream a41 = new ByteArrayOutputStream();
        fieldVarint(a41, 1, isFinal ? 1 : 0);
        fieldMessage(a41, 2, candidates);
        ByteArrayOutputStream s21 = new ByteArrayOutputStream();
        fieldVarint(s21, 1, CODE_ASR_RESULT);
        fieldMessage(s21, 2, a41.toByteArray());
        fieldVarint(s21, 13, sessionId);
        return wrapper(List.of(s21.toByteArray()));
    }

    /** A plain answer toast. The official path sends this before DialogFinish. */
    public static byte[] toast(int sessionId, String text) {
        byte[] n41 = stringMessage(1, text);
        ByteArrayOutputStream toast = new ByteArrayOutputStream();
        fieldVarint(toast, 1, CODE_TOAST);
        fieldMessage(toast, 3, n41);
        fieldVarint(toast, 13, sessionId);
        return wrapper(List.of(toast.toByteArray()));
    }

    /**
     * Opens a built-in watch app through the official Launcher.LaunchApp instruction. The band
     * reads the package name and optional deep-link URI from s21 field 14.
     */
    public static byte[] openApp(int sessionId, String packageName, String uri) {
        ByteArrayOutputStream intent = new ByteArrayOutputStream();
        intent.writeBytes(stringMessage(1, packageName));
        intent.writeBytes(stringMessage(2, uri == null ? "" : uri));
        ByteArrayOutputStream launch = new ByteArrayOutputStream();
        fieldMessage(launch, 1, intent.toByteArray());
        ByteArrayOutputStream open = new ByteArrayOutputStream();
        fieldVarint(open, 1, CODE_OPEN_APP);
        fieldMessage(open, 14, launch.toByteArray());
        fieldVarint(open, 13, sessionId);
        return wrapper(List.of(open.toByteArray()));
    }

    /** Asks the band to stop recording after the local voice activity detector ends a turn. */
    public static byte[] stopCapture(int sessionId) {
        ByteArrayOutputStream s21 = new ByteArrayOutputStream();
        fieldVarint(s21, 1, CODE_STOP_CAPTURE);
        fieldVarint(s21, 13, sessionId);
        return wrapper(List.of(s21.toByteArray()));
    }

    public static byte[] tts(int sessionId, String text) {
        byte[] c41 = stringMessage(1, text);
        ByteArrayOutputStream speak = new ByteArrayOutputStream();
        fieldVarint(speak, 1, CODE_TTS);
        fieldMessage(speak, 11, c41);
        fieldVarint(speak, 13, sessionId);
        return wrapper(List.of(speak.toByteArray()));
    }

    public static byte[] finish(int sessionId) {
        return wrapper(List.of(finishInstruction(sessionId)));
    }

    public static byte[] error(int sessionId, int code) {
        ByteArrayOutputStream s21 = new ByteArrayOutputStream();
        fieldVarint(s21, 1, code);
        fieldVarint(s21, 13, sessionId);
        return wrapper(List.of(s21.toByteArray()));
    }

    private static byte[] finishInstruction(int sessionId) {
        ByteArrayOutputStream s21 = new ByteArrayOutputStream();
        fieldVarint(s21, 1, CODE_DIALOG_FINISH);
        fieldVarint(s21, 13, sessionId);
        return s21.toByteArray();
    }

    private static byte[] wrapper(List<byte[]> instructions) {
        ByteArrayOutputStream list = new ByteArrayOutputStream();
        for (byte[] instruction : instructions) fieldMessage(list, 1, instruction);
        ByteArrayOutputStream p21 = new ByteArrayOutputStream();
        fieldMessage(p21, 1, list.toByteArray());
        ByteArrayOutputStream oyt = new ByteArrayOutputStream();
        fieldVarint(oyt, 1, 14);
        fieldVarint(oyt, 2, 0);
        fieldMessage(oyt, 16, p21.toByteArray());
        return oyt.toByteArray();
    }

    private static byte[] stringMessage(int field, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream message = new ByteArrayOutputStream(bytes.length + 4);
        fieldBytes(message, field, bytes);
        return message.toByteArray();
    }

    private static byte[] repeatedMessage(int field, List<byte[]> messages) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] message : messages) fieldMessage(out, field, message);
        return out.toByteArray();
    }

    private static void fieldMessage(ByteArrayOutputStream out, int field, byte[] value) {
        fieldBytes(out, field, value);
    }

    private static void fieldBytes(ByteArrayOutputStream out, int field, byte[] value) {
        writeVarint(out, (field << 3) | 2);
        writeVarint(out, value.length);
        out.writeBytes(value);
    }

    private static void fieldVarint(ByteArrayOutputStream out, int field, int value) {
        writeVarint(out, field << 3);
        writeVarint(out, value);
    }

    private static void writeVarint(ByteArrayOutputStream out, int value) {
        long remaining = value & 0xffffffffL;
        while ((remaining & ~0x7fL) != 0) {
            out.write((int) (remaining & 0x7f) | 0x80);
            remaining >>>= 7;
        }
        out.write((int) remaining);
    }
}
