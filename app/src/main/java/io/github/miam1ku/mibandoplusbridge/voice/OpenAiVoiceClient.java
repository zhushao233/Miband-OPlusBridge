// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Minimal OpenAI-compatible transcription and chat-completions client. */
public final class OpenAiVoiceClient implements VoiceApiClient {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int MAX_RESPONSE_BYTES = 128 * 1024;

    public record Answer(String transcript, String reply, String kind) {
        public Answer(String transcript, String reply) {
            this(transcript, reply, "MODEL");
        }
    }

    public Answer ask(VoiceAssistantSettings.Values settings, byte[] oggOpus) throws Exception {
        return ask(settings, oggOpus, ignored -> { });
    }

    public Answer ask(VoiceAssistantSettings.Values settings, byte[] oggOpus,
                      Consumer<String> onTranscript) throws Exception {
        String transcript = transcribe(settings, oggOpus).trim();
        if (transcript.isEmpty()) throw new IllegalStateException("VOICE_TRANSCRIPT_EMPTY");
        onTranscript.accept(transcript);
        String reply = chat(settings, transcript).trim();
        if (reply.isEmpty()) throw new IllegalStateException("VOICE_REPLY_EMPTY");
        return new Answer(transcript, VoiceText.compact(reply, 500));
    }

    public String transcribe(VoiceAssistantSettings.Values settings, byte[] audio)
            throws Exception {
        String boundary = "----MibandOPlusBridge" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream multipart = new ByteArrayOutputStream(audio.length + 2048);
        if (!settings.transcriptionModel().isBlank()) {
            field(multipart, boundary, "model", settings.transcriptionModel());
            field(multipart, boundary, "language", "zh");
            field(multipart, boundary, "response_format", "json");
        }
        writeAscii(multipart, "--" + boundary + "\r\n");
        writeAscii(multipart,
                "Content-Disposition: form-data; name=\"file\"; filename=\"voice.ogg\"\r\n");
        writeAscii(multipart, "Content-Type: audio/ogg\r\n\r\n");
        multipart.write(audio);
        writeAscii(multipart, "\r\n--" + boundary + "--\r\n");

        JSONObject response;
        try {
            response = requestJson(settings.asrUrl(),
                    "POST", "multipart/form-data; boundary=" + boundary,
                    multipart.toByteArray(), settings.asrApiKey());
        } catch (IllegalStateException failure) {
            throw stage(failure, "VOICE_ASR_");
        }
        String text = response.optString("text", "").trim();
        if (text.isEmpty()) throw new IllegalStateException("VOICE_TRANSCRIPTION_REJECTED");
        return text;
    }

    public String chat(VoiceAssistantSettings.Values settings, String prompt)
            throws Exception {
        return chat(settings, prompt, "");
    }

    /**
     * Extra context is a second system message immediately before the user turn. The original
     * system prompt and ordinary two-message layout stay byte-for-byte unchanged.
     */
    public String chat(VoiceAssistantSettings.Values settings, String prompt,
                       String extraSystemContext) throws Exception {
        JSONArray messages = chatMessages(settings, prompt, extraSystemContext);
        JSONObject request = new JSONObject()
                .put("model", settings.chatModel())
                .put("messages", messages)
                .put("max_tokens", 220)
                .put("temperature", 0.4);
        JSONObject response;
        try {
            response = requestJson(endpoint(settings.baseUrl(), "chat/completions"),
                    "POST", "application/json",
                    request.toString().getBytes(StandardCharsets.UTF_8), settings.apiKey());
        } catch (IllegalStateException failure) {
            throw stage(failure, "VOICE_CHAT_");
        }
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IllegalStateException("VOICE_CHAT_REJECTED");
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        String content = message == null ? "" : message.optString("content", "");
        if (content.isBlank()) throw new IllegalStateException("VOICE_CHAT_EMPTY");
        return content;
    }

    static JSONArray chatMessages(VoiceAssistantSettings.Values settings, String prompt,
                                  String extraSystemContext) throws Exception {
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system")
                .put("content", settings.systemPrompt()));
        if (extraSystemContext != null && !extraSystemContext.isBlank()) {
            messages.put(new JSONObject().put("role", "system")
                    .put("content", extraSystemContext.trim()));
        }
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        return messages;
    }

    private static JSONObject requestJson(String endpoint, String method, String contentType,
                                          byte[] body, String apiKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Accept", "application/json");
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            byte[] response = stream == null ? new byte[0] : readBounded(stream);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("VOICE_HTTP_" + status);
            }
            return new JSONObject(new String(response, StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private static String endpoint(String baseUrl, String suffix) {
        String normalized = VoiceAssistantSettings.normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/" + suffix)) return normalized;
        return normalized + "/" + suffix;
    }

    private static IllegalStateException stage(IllegalStateException failure, String prefix) {
        String message = failure.getMessage();
        if (message != null && message.startsWith("VOICE_HTTP_")) {
            return new IllegalStateException(prefix + message.substring("VOICE_".length()), failure);
        }
        return failure;
    }

    private static byte[] readBounded(InputStream input) throws Exception {
        try (input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int count; (count = input.read(buffer)) >= 0;) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("VOICE_RESPONSE_TOO_LARGE");
                }
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static void field(ByteArrayOutputStream out, String boundary,
                              String name, String value) {
        writeAscii(out, "--" + boundary + "\r\n");
        writeAscii(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writeAscii(out, value + "\r\n");
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

}
