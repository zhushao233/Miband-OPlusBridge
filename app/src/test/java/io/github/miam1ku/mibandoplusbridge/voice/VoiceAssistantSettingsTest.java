// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class VoiceAssistantSettingsTest {
    @Test public void legacyConfigsDeriveTheSpeechUrlFromTheirPreset() {
        assertEquals("https://api.openai.com/v1/audio/transcriptions",
                VoiceAssistantSettings.defaultAsrUrl(
                        VoiceAssistantPreset.OPENAI, "https://api.openai.com/v1"));
        assertEquals("https://api.deepseek.com/v1/tools/asr",
                VoiceAssistantSettings.defaultAsrUrl(
                        VoiceAssistantPreset.DEEPSEEK, "https://api.deepseek.com/v1"));
    }

    @Test public void customOpenAiCompatibleUrlsKeepTheOpenAiSpeechLayout() {
        assertEquals("https://proxy.example/v1/audio/transcriptions",
                VoiceAssistantSettings.defaultAsrUrl(
                        VoiceAssistantPreset.OPENAI, "https://proxy.example/v1/"));
    }

    @Test public void chatOnlyProvidersDoNotInventASpeechUrl() {
        assertEquals("", VoiceAssistantSettings.defaultAsrUrl(
                VoiceAssistantPreset.KIMI, "https://api.moonshot.cn/v1"));
        assertEquals("", VoiceAssistantSettings.defaultAsrUrl(
                VoiceAssistantPreset.QWEN,
                "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        assertEquals("", VoiceAssistantSettings.defaultAsrUrl(
                VoiceAssistantPreset.OPENROUTER, "https://openrouter.ai/api/v1"));
    }

    @Test public void anEmptyStoredModelFallsBackToThePresetDefault() {
        assertEquals("whisper-1", VoiceAssistantSettings.effectiveTranscriptionModel(
                "", VoiceAssistantPreset.OPENAI));
        assertEquals("whisper-1", VoiceAssistantSettings.effectiveTranscriptionModel(
                "   ", VoiceAssistantPreset.OPENAI));
        assertEquals("custom-asr", VoiceAssistantSettings.effectiveTranscriptionModel(
                " custom-asr ", VoiceAssistantPreset.OPENAI));
        assertEquals("", VoiceAssistantSettings.effectiveTranscriptionModel(
                "", VoiceAssistantPreset.KIMI));
    }
}
