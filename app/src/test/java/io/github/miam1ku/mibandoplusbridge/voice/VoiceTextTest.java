// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VoiceTextTest {
    @Test public void keepsShortTextWithoutChangingItsShape() {
        assertEquals("你好 小爱", VoiceText.compact(" 你好\n小爱 ", 300));
    }

    @Test public void answerFitsTheThreeHundredCharacterBandLimit() {
        String compact = VoiceText.compact("字".repeat(301), 300);

        assertEquals(300, compact.length());
        assertTrue(compact.endsWith("…"));
    }

    @Test public void genericAnswerFitsTheFiveHundredCharacterLimit() {
        String compact = VoiceText.compact("字".repeat(501), 500);

        assertEquals(500, compact.length());
        assertTrue(compact.endsWith("…"));
    }

    @Test public void truncationDoesNotSplitSupplementaryCharacters() {
        String compact = VoiceText.compact("😀".repeat(10), 5);

        assertEquals(5, compact.codePointCount(0, compact.length()));
        assertEquals("😀😀😀😀…", compact);
    }
}
