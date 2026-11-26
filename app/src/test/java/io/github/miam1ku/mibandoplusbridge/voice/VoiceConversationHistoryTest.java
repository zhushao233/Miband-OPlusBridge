// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VoiceConversationHistoryTest {
    @Test public void keepsNewestEntriesAndTrimsToLimit() throws Exception {
        File directory = Files.createTempDirectory("voice-history").toFile();
        try {
            var history = new VoiceConversationHistory(new File(directory, "history.json"));
            for (int index = 1; index <= VoiceConversationHistory.MAX_ENTRIES + 5; index++) {
                assertTrue(history.append(new VoiceConversationHistory.Entry(
                        index, "问题 " + index, "回答 " + index,
                        VoiceConversationHistory.Kind.MODEL)));
            }
            var entries = history.recent();
            assertEquals(VoiceConversationHistory.MAX_ENTRIES, entries.size());
            assertEquals(VoiceConversationHistory.MAX_ENTRIES + 5, entries.get(0).timeMs());
            assertEquals(6, entries.get(entries.size() - 1).timeMs());
        } finally {
            for (File file : directory.listFiles()) file.delete();
            directory.delete();
        }
    }

    @Test public void malformedFileIsEmptyAndCanBeReplaced() throws Exception {
        File directory = Files.createTempDirectory("voice-history-invalid").toFile();
        File file = new File(directory, "history.json");
        try {
            Files.writeString(file.toPath(), "{not-json", StandardCharsets.UTF_8);
            var history = new VoiceConversationHistory(file);
            assertTrue(history.recent().isEmpty());
            assertTrue(history.append(new VoiceConversationHistory.Entry(
                    10, "你好", "你好呀", VoiceConversationHistory.Kind.WEATHER)));
            assertEquals(1, history.recent().size());
        } finally {
            file.delete();
            directory.delete();
        }
    }

    @Test public void storageContainsOnlyConversationFields() throws Exception {
        File directory = Files.createTempDirectory("voice-history-fields").toFile();
        File file = new File(directory, "history.json");
        try {
            var history = new VoiceConversationHistory(file);
            assertTrue(history.append(new VoiceConversationHistory.Entry(
                    100, "打开闹钟", "已打开闹钟", VoiceConversationHistory.Kind.WATCH)));
            String stored = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            assertTrue(stored.contains("\"user\":\"打开闹钟\""));
            assertTrue(stored.contains("\"kind\":\"WATCH\""));
            assertFalse(stored.contains("apiKey"));
            assertFalse(stored.contains("systemPrompt"));
            assertFalse(stored.contains("audio"));
            assertTrue(history.clear());
            assertTrue(history.recent().isEmpty());
        } finally {
            file.delete();
            directory.delete();
        }
    }
}
