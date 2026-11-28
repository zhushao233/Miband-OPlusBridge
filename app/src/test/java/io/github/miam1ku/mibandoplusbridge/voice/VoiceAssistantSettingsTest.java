// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test public void aLostKeystoreKeyIsUnreadableRatherThanAbsent() {
        assertEquals(VoiceAssistantSettings.KeyState.ABSENT,
                VoiceAssistantSettings.keyState("", ""));
        assertEquals(VoiceAssistantSettings.KeyState.ABSENT,
                VoiceAssistantSettings.keyState(null, ""));
        assertEquals(VoiceAssistantSettings.KeyState.OK,
                VoiceAssistantSettings.keyState("ciphertext", "sk-live"));
        assertEquals(VoiceAssistantSettings.KeyState.UNREADABLE,
                VoiceAssistantSettings.keyState("ciphertext",
                        VoiceAssistantSettings.DECRYPT_FAILED));
    }

    @Test public void anUnreadableKeyIsNotConfigured() {
        var values = new VoiceAssistantSettings.Values(VoiceAssistantPreset.OPENAI, true,
                "https://api.example.com/v1", "whisper-1", "model", "prompt",
                "", false, "https://api.example.com/v1/audio/transcriptions", "",
                VoiceAssistantSettings.KeyState.UNREADABLE);

        assertFalse(values.configured());
        assertFalse(values.complete());
    }

    @Test public void anUnreadableAsrKeyFallsBackToTheChatKey() {
        var keys = VoiceAssistantSettings.resolveKeys("chat-ciphertext", "sk-live",
                VoiceAssistantSettings.DECRYPT_FAILED);

        assertEquals("sk-live", keys.chatKey());
        assertEquals("sk-live", keys.asrKey());
        assertEquals(VoiceAssistantSettings.KeyState.OK, keys.keyState());
        assertTrue(keys.asrFellBack());

        var values = new VoiceAssistantSettings.Values(VoiceAssistantPreset.OPENAI, true,
                "https://api.example.com/v1", "whisper-1", "model", "prompt",
                keys.chatKey(), false, "https://api.example.com/v1/audio/transcriptions",
                keys.asrKey(), keys.keyState());
        assertTrue(values.configured());
        assertTrue(values.complete());
    }

    @Test public void unreadableAsrWithoutAUsableFallbackStaysUnreadable() {
        var keys = VoiceAssistantSettings.resolveKeys("chat-ciphertext",
                VoiceAssistantSettings.DECRYPT_FAILED,
                VoiceAssistantSettings.DECRYPT_FAILED);

        assertEquals(VoiceAssistantSettings.KeyState.UNREADABLE, keys.keyState());
        assertFalse(keys.asrFellBack());
        assertEquals("", keys.asrKey());
    }
}
