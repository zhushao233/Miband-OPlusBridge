// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import org.json.JSONArray;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class OpenAiVoiceClientTest {
    @Test public void ordinaryChatKeepsTheOriginalTwoMessagePrefix() throws Exception {
        JSONArray messages = OpenAiVoiceClient.chatMessages(settings(), "你好", "");
        assertEquals(2, messages.length());
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertEquals("固定系统提示词", messages.getJSONObject(0).getString("content"));
        assertEquals("user", messages.getJSONObject(1).getString("role"));
        assertEquals("你好", messages.getJSONObject(1).getString("content"));
    }

    @Test public void weatherContextSitsBetweenTheUnchangedSystemAndUserTurns() throws Exception {
        JSONArray messages = OpenAiVoiceClient.chatMessages(settings(), "天气怎么样", "本轮天气快照");
        assertEquals(3, messages.length());
        assertEquals("固定系统提示词", messages.getJSONObject(0).getString("content"));
        assertEquals("system", messages.getJSONObject(1).getString("role"));
        assertEquals("本轮天气快照", messages.getJSONObject(1).getString("content"));
        assertEquals("user", messages.getJSONObject(2).getString("role"));
        assertEquals("天气怎么样", messages.getJSONObject(2).getString("content"));
    }

    private static VoiceAssistantSettings.Values settings() {
        return new VoiceAssistantSettings.Values(VoiceAssistantPreset.OTHER, true,
                "https://example.com/v1", "", "model", "固定系统提示词",
                "secret", false, "https://example.com/v1/audio/transcriptions", "secret",
                VoiceAssistantSettings.KeyState.OK);
    }
}
