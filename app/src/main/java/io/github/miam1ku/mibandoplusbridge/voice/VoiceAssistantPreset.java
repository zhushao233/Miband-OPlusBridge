// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.net.URI;

/** Known API layouts used by the experimental voice assistant. */
public enum VoiceAssistantPreset {
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1",
            "gpt-4.1-mini", "whisper-1", "audio/transcriptions"),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/v1",
            "deepseek-flash", "", "tools/asr"),
    KIMI("kimi", "Kimi（Moonshot）", "https://api.moonshot.cn/v1",
            "kimi-latest", "", ""),
    QWEN("qwen", "通义千问（Qwen）", "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "qwen-plus", "", ""),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1",
            "openai/gpt-4.1-mini", "", ""),
    OTHER("other", "其他 / 自定义", "", "", "", "");

    private final String id;
    private final String displayName;
    private final String baseUrl;
    private final String chatModel;
    private final String transcriptionModel;
    private final String transcriptionPath;

    VoiceAssistantPreset(String id, String displayName, String baseUrl,
                         String chatModel, String transcriptionModel,
                         String transcriptionPath) {
        this.id = id;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.chatModel = chatModel;
        this.transcriptionModel = transcriptionModel;
        this.transcriptionPath = transcriptionPath;
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
        return !transcriptionModel.isBlank();
    }

    /** Whether this provider also offers an OpenAI-style speech endpoint. */
    public boolean supportsTranscription() {
        return !transcriptionPath.isBlank();
    }

    /** Full transcription URL when this provider ships its own speech endpoint. */
    public String transcriptionUrl() {
        if (!supportsTranscription()) return "";
        return baseUrl + "/" + transcriptionPath;
    }

    public static VoiceAssistantPreset fromId(String id) {
        if (id != null) {
            String normalized = id.trim();
            for (VoiceAssistantPreset preset : values()) {
                if (preset.id.equalsIgnoreCase(normalized)) return preset;
            }
            if ("moonshot".equalsIgnoreCase(normalized)) return KIMI;
            if ("dashscope".equalsIgnoreCase(normalized)) return QWEN;
            if ("custom".equalsIgnoreCase(normalized)) return OTHER;
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
            if (host != null && (host.equalsIgnoreCase("api.moonshot.cn")
                    || host.equalsIgnoreCase("api.moonshot.ai"))) return KIMI;
            if (host != null && (host.equalsIgnoreCase("dashscope.aliyuncs.com")
                    || host.equalsIgnoreCase("dashscope-intl.aliyuncs.com"))) return QWEN;
            if (host != null && host.equalsIgnoreCase("openrouter.ai")) return OPENROUTER;
        } catch (IllegalArgumentException ignored) {
        }
        return OPENAI;
    }
}
