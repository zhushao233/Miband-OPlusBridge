// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.net.URI;

/** Known API layouts used by the experimental voice assistant. */
public enum VoiceAssistantPreset {
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1",
            "whisper-1", "gpt-4.1-mini", "audio/transcriptions", true),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/v1",
            "", "deepseek-flash", "tools/asr", false);

    private final String id;
    private final String displayName;
    private final String baseUrl;
    private final String transcriptionModel;
    private final String chatModel;
    private final String transcriptionPath;
    private final boolean usesTranscriptionModel;

    VoiceAssistantPreset(String id, String displayName, String baseUrl,
                         String transcriptionModel, String chatModel,
                         String transcriptionPath, boolean usesTranscriptionModel) {
        this.id = id;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.transcriptionModel = transcriptionModel;
        this.chatModel = chatModel;
        this.transcriptionPath = transcriptionPath;
        this.usesTranscriptionModel = usesTranscriptionModel;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String transcriptionModel() {
        return transcriptionModel;
    }

    public String chatModel() {
        return chatModel;
    }

    public String transcriptionPath() {
        return transcriptionPath;
    }

    public boolean usesTranscriptionModel() {
        return usesTranscriptionModel;
    }

    public static VoiceAssistantPreset fromId(String id) {
        if (id != null) {
            for (VoiceAssistantPreset preset : values()) {
                if (preset.id.equalsIgnoreCase(id.trim())) return preset;
            }
        }
        return OPENAI;
    }

    /**
     * Older installs stored no preset id, so their endpoint shape is inferred from the saved URL.
     */
    public static VoiceAssistantPreset fromSettings(String id, String baseUrl) {
        if (id != null && !id.isBlank()) return fromId(id);
        try {
            String host = URI.create(baseUrl == null ? "" : baseUrl.trim()).getHost();
            if (host != null && host.equalsIgnoreCase("api.deepseek.com")) return DEEPSEEK;
        } catch (IllegalArgumentException ignored) {
        }
        return OPENAI;
    }
}
