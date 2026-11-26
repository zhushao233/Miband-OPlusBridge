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
        assertEquals("https://api.openai.com/v1/audio/transcriptions",
                preset.transcriptionUrl());
    }

    @Test public void chatOnlyProvidersDoNotClaimASpeechEndpoint() {
        assertEquals("https://api.moonshot.cn/v1", VoiceAssistantPreset.KIMI.baseUrl());
        assertEquals("kimi-latest", VoiceAssistantPreset.KIMI.chatModel());
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1",
                VoiceAssistantPreset.QWEN.baseUrl());
        assertEquals("qwen-plus", VoiceAssistantPreset.QWEN.chatModel());
        assertEquals("https://openrouter.ai/api/v1",
                VoiceAssistantPreset.OPENROUTER.baseUrl());
        assertEquals("openai/gpt-4.1-mini", VoiceAssistantPreset.OPENROUTER.chatModel());

        for (VoiceAssistantPreset preset : new VoiceAssistantPreset[]{
                VoiceAssistantPreset.KIMI, VoiceAssistantPreset.QWEN,
                VoiceAssistantPreset.OPENROUTER, VoiceAssistantPreset.OTHER}) {
            assertFalse(preset.supportsTranscription());
            assertEquals("", preset.transcriptionUrl());
        }
    }

    @Test public void customPresetStartsWithEditableFields() {
        assertSame(VoiceAssistantPreset.OTHER, VoiceAssistantPreset.fromId("custom"));
        assertEquals("", VoiceAssistantPreset.OTHER.baseUrl());
        assertEquals("", VoiceAssistantPreset.OTHER.chatModel());
    }

    @Test public void oldSettingsInferDeepSeekFromItsOfficialHost() {
        assertSame(VoiceAssistantPreset.DEEPSEEK,
                VoiceAssistantPreset.fromSettings("", "https://api.deepseek.com/v1"));
        assertSame(VoiceAssistantPreset.OPENAI,
                VoiceAssistantPreset.fromSettings("", "https://proxy.example/v1"));
    }

    @Test public void oldSettingsInferTheOtherOfficialHosts() {
        assertSame(VoiceAssistantPreset.KIMI,
                VoiceAssistantPreset.fromSettings("", "https://api.moonshot.cn/v1"));
        assertSame(VoiceAssistantPreset.KIMI,
                VoiceAssistantPreset.fromSettings("", "https://api.moonshot.ai/v1"));
        assertSame(VoiceAssistantPreset.QWEN,
                VoiceAssistantPreset.fromSettings("",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        assertSame(VoiceAssistantPreset.QWEN,
                VoiceAssistantPreset.fromSettings("",
                        "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"));
        assertSame(VoiceAssistantPreset.OPENROUTER,
                VoiceAssistantPreset.fromSettings("", "https://openrouter.ai/api/v1"));
    }

    @Test public void storedPresetTakesPriorityOverHostDetection() {
        assertSame(VoiceAssistantPreset.DEEPSEEK,
                VoiceAssistantPreset.fromSettings("deepseek", "https://proxy.example/v1"));
        assertSame(VoiceAssistantPreset.OPENAI,
                VoiceAssistantPreset.fromSettings("openai", "https://api.deepseek.com/v1"));
    }
}
