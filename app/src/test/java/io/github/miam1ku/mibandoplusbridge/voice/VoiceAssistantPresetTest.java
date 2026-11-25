// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VoiceAssistantPresetTest {
    @Test public void deepSeekUsesDedicatedSpeechEndpointWithoutAModel() {
        VoiceAssistantPreset preset = VoiceAssistantPreset.DEEPSEEK;

        assertEquals("https://api.deepseek.com/v1", preset.baseUrl());
        assertEquals("tools/asr", preset.transcriptionPath());
        assertEquals("", preset.transcriptionModel());
        assertEquals("deepseek-flash", preset.chatModel());
        assertFalse(preset.usesTranscriptionModel());
    }

    @Test public void openAiKeepsOpenAiTranscriptionShape() {
        VoiceAssistantPreset preset = VoiceAssistantPreset.OPENAI;

        assertEquals("audio/transcriptions", preset.transcriptionPath());
        assertEquals("whisper-1", preset.transcriptionModel());
        assertTrue(preset.usesTranscriptionModel());
    }

    @Test public void oldSettingsInferDeepSeekFromItsOfficialHost() {
        assertSame(VoiceAssistantPreset.DEEPSEEK,
                VoiceAssistantPreset.fromSettings("", "https://api.deepseek.com/v1"));
        assertSame(VoiceAssistantPreset.OPENAI,
                VoiceAssistantPreset.fromSettings("", "https://proxy.example/v1"));
    }

    @Test public void storedPresetTakesPriorityOverHostDetection() {
        assertSame(VoiceAssistantPreset.DEEPSEEK,
                VoiceAssistantPreset.fromSettings("deepseek", "https://proxy.example/v1"));
        assertSame(VoiceAssistantPreset.OPENAI,
                VoiceAssistantPreset.fromSettings("openai", "https://api.deepseek.com/v1"));
    }
}
