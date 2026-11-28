// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

/** The two model calls used by the voice turn pipeline, injectable for JVM tests. */
public interface VoiceApiClient {
    String transcribe(VoiceAssistantSettings.Values settings, byte[] audio) throws Exception;

    String chat(VoiceAssistantSettings.Values settings, String prompt) throws Exception;

    String chat(VoiceAssistantSettings.Values settings, String prompt,
                String extraSystemContext) throws Exception;
}
