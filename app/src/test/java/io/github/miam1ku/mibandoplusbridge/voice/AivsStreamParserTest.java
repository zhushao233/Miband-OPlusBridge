// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class AivsStreamParserTest {
    @Test public void parsesCapturedStartAndAudioBlocks() {
        byte[] start = {0x00, 0x01, 0x00, 0x01, 0x00};
        byte[] audio = new byte[404];
        audio[0] = 0x00;
        audio[1] = 0x02;
        audio[2] = 0x01;
        audio[3] = (byte) 0x90;
        Arrays.fill(audio, 4, audio.length, (byte) 0x4b);

        AivsStreamParser parser = new AivsStreamParser();
        List<AivsStreamParser.Command> commands =
                parser.feed(concat(start, audio));

        assertEquals(2, commands.size());
        assertEquals(AivsStreamParser.TYPE_START, commands.get(0).type());
        assertEquals(0, commands.get(0).sessionId());
        assertEquals(AivsStreamParser.TYPE_AUDIO, commands.get(1).type());
        assertEquals(400, commands.get(1).data().length);
    }

    @Test public void dropsAShortTailInsteadOfStitchingTheNextSession() {
        AivsStreamParser parser = new AivsStreamParser();
        assertTrue(parser.feed(new byte[] {0x00, 0x01, 0x00, 0x02}).isEmpty());
        assertTrue(parser.takeMalformed().contains("truncated"));

        List<AivsStreamParser.Command> commands =
                parser.feed(new byte[] {0x00, 0x01, 0x00, 0x02, 0x07, 0x01});
        assertEquals(1, commands.size());
        assertEquals(AivsStreamParser.TYPE_START, commands.get(0).type());
        assertArrayEquals(new byte[] {0x07, 0x01}, commands.get(0).data());
        assertTrue(commands.get(0).ttsEnabled());
    }

    @Test public void reportsTheDiscardedBlockAfterADesync() {
        AivsStreamParser parser = new AivsStreamParser();
        assertTrue(parser.feed(new byte[] {0x00, 0x09, 0x00, 0x01, 0x00}).isEmpty());
        assertTrue(parser.takeMalformed().contains("type=9"));
        assertEquals("", parser.takeMalformed());
    }

    @Test public void newSessionParsesCleanlyAfterATruncatedAudioTail() {
        AivsStreamParser parser = new AivsStreamParser();
        byte[] truncatedAudio = new byte[4 + 399];
        truncatedAudio[0] = 0x00;
        truncatedAudio[1] = 0x02;
        truncatedAudio[2] = 0x01;
        truncatedAudio[3] = (byte) 0x90;
        assertTrue(parser.feed(truncatedAudio).isEmpty());
        assertTrue(parser.takeMalformed().contains("truncated"));

        byte[] start = {0x00, 0x01, 0x00, 0x02, 0x00, 0x00};
        byte[] audio = new byte[404];
        audio[0] = 0x00;
        audio[1] = 0x02;
        audio[2] = 0x01;
        audio[3] = (byte) 0x90;
        List<AivsStreamParser.Command> commands = parser.feed(concat(start, audio));

        assertEquals(2, commands.size());
        assertEquals(AivsStreamParser.TYPE_START, commands.get(0).type());
        assertEquals(0, commands.get(0).sessionId());
        assertEquals(AivsStreamParser.TYPE_AUDIO, commands.get(1).type());
        assertEquals(400, commands.get(1).data().length);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
