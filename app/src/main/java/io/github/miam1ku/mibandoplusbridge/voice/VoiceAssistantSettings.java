// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Configuration for the experimental bridge voice assistant. API keys never enter the JSON logs. */
public final class VoiceAssistantSettings {
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_TRANSCRIPTION_MODEL = "whisper-1";
    public static final String DEFAULT_CHAT_MODEL = "gpt-4.1-mini";
    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是运行在小米手环上的语音助手。请用简洁、自然的中文回答，优先控制在 120 字以内。";

    private static final String PREFS = "voice-assistant";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_PRESET = "preset";
    private static final String KEY_BASE_URL = "baseUrl";
    private static final String KEY_TRANSCRIPTION_MODEL = "transcriptionModel";
    private static final String KEY_CHAT_MODEL = "chatModel";
    private static final String KEY_SYSTEM_PROMPT = "systemPrompt";
    private static final String KEY_CAPTURE_RAW = "captureRaw";
    private static final String KEY_API_KEY_CIPHERTEXT = "apiKeyCiphertext";
    private static final String KEY_ASR_URL = "asrUrl";
    private static final String KEY_ASR_API_KEY_CIPHERTEXT = "asrApiKeyCiphertext";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "miband_oplus_voice_api_key_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private VoiceAssistantSettings() {}

    public record Values(VoiceAssistantPreset preset, boolean enabled, String baseUrl,
                         String transcriptionModel, String chatModel, String systemPrompt,
                         String apiKey, boolean captureRaw, String asrUrl, String asrApiKey) {
        public boolean complete() {
            return enabled && configured();
        }

        public boolean configured() {
            return !baseUrl.isBlank() && !chatModel.isBlank() && !apiKey.isBlank()
                    && !asrUrl.isBlank() && !asrApiKey.isBlank()
                    && validHttpsUrl(baseUrl) && validHttpsUrl(asrUrl);
        }
    }

    public static Values load(Context context) {
        SharedPreferences prefs = prefs(context);
        String baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
        VoiceAssistantPreset preset = VoiceAssistantPreset.fromSettings(
                prefs.getString(KEY_PRESET, ""), baseUrl);
        String chatKey = decrypt(prefs.getString(KEY_API_KEY_CIPHERTEXT, ""));
        String asrKey = decrypt(prefs.getString(KEY_ASR_API_KEY_CIPHERTEXT, ""));
        if (asrKey.isBlank()) asrKey = chatKey;
        String asrUrl = prefs.getString(KEY_ASR_URL, "");
        if (asrUrl.isBlank()) asrUrl = defaultAsrUrl(preset, baseUrl);
        String transcriptionModel = effectiveTranscriptionModel(
                prefs.getString(KEY_TRANSCRIPTION_MODEL, ""), preset);
        return new Values(
                preset,
                prefs.getBoolean(KEY_ENABLED, false),
                baseUrl,
                transcriptionModel,
                prefs.getString(KEY_CHAT_MODEL, preset.chatModel().isBlank()
                        ? DEFAULT_CHAT_MODEL : preset.chatModel()),
                prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT),
                chatKey,
                prefs.getBoolean(KEY_CAPTURE_RAW, false),
                asrUrl,
                asrKey);
    }

    /**
     * Empty API key fields keep the existing encrypted values, so opening and saving the page
     * does not require the user to enter them again.
     */
    public static boolean save(Context context, String presetId, boolean enabled, String baseUrl,
                               String transcriptionModel, String chatModel, String systemPrompt,
                               String replacementApiKey, boolean captureRaw, String asrUrl,
                               String replacementAsrApiKey) {
        VoiceAssistantPreset preset = VoiceAssistantPreset.fromId(presetId);
        String normalizedUrl = normalizeBaseUrl(baseUrl);
        String normalizedAsrUrl = normalizeBaseUrl(asrUrl);
        if (normalizedAsrUrl.isBlank()) normalizedAsrUrl = defaultAsrUrl(preset, normalizedUrl);
        String normalizedChatModel = blankFallback(chatModel,
                preset.chatModel().isBlank() ? DEFAULT_CHAT_MODEL : preset.chatModel());
        if (enabled && (!validHttpsUrl(normalizedUrl) || !validHttpsUrl(normalizedAsrUrl)
                || normalizedChatModel.isBlank())) {
            return false;
        }
        SharedPreferences prefs = prefs(context);
        String ciphertext = prefs.getString(KEY_API_KEY_CIPHERTEXT, "");
        String previousChatKey = decrypt(ciphertext);
        String effectiveChatKey = previousChatKey;
        if (replacementApiKey != null && !replacementApiKey.isBlank()) {
            effectiveChatKey = replacementApiKey.trim();
            ciphertext = encrypt(effectiveChatKey);
            if (ciphertext.isEmpty()) return false;
        }
        String asrCiphertext = prefs.getString(KEY_ASR_API_KEY_CIPHERTEXT, "");
        String effectiveAsrKey = decrypt(asrCiphertext);
        if (effectiveAsrKey.isBlank()) effectiveAsrKey = previousChatKey;
        if (effectiveAsrKey.isBlank()) effectiveAsrKey = effectiveChatKey;
        if (replacementAsrApiKey != null && !replacementAsrApiKey.isBlank()) {
            asrCiphertext = encrypt(replacementAsrApiKey.trim());
            if (asrCiphertext.isEmpty()) return false;
        } else if (asrCiphertext.isBlank() && !effectiveAsrKey.isBlank()) {
            // Pin the fallback on first save so switching chat providers keeps the working ASR key.
            asrCiphertext = encrypt(effectiveAsrKey);
            if (asrCiphertext.isEmpty()) return false;
        }
        if (enabled && (ciphertext.isEmpty() || asrCiphertext.isEmpty())) return false;
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_PRESET, preset.id())
                .putString(KEY_BASE_URL, normalizedUrl.isBlank() ? DEFAULT_BASE_URL : normalizedUrl)
                .putString(KEY_CHAT_MODEL, normalizedChatModel)
                .putString(KEY_SYSTEM_PROMPT, blankFallback(systemPrompt, DEFAULT_SYSTEM_PROMPT))
                .putString(KEY_API_KEY_CIPHERTEXT, ciphertext)
                .putString(KEY_ASR_URL, normalizedAsrUrl)
                .putString(KEY_ASR_API_KEY_CIPHERTEXT, asrCiphertext)
                .putBoolean(KEY_CAPTURE_RAW, captureRaw);
        String normalizedTranscription = transcriptionModel == null ? "" : transcriptionModel.trim();
        if (normalizedTranscription.isEmpty()) {
            editor.remove(KEY_TRANSCRIPTION_MODEL);
        } else {
            editor.putString(KEY_TRANSCRIPTION_MODEL, normalizedTranscription);
        }
        return editor.commit();
    }

    static String defaultAsrUrl(VoiceAssistantPreset preset, String baseUrl) {
        if (preset == null || !preset.supportsTranscription()) return "";
        return normalizeBaseUrl(baseUrl) + "/" + preset.transcriptionPath();
    }

    static String effectiveTranscriptionModel(String stored, VoiceAssistantPreset preset) {
        if (stored != null && !stored.isBlank()) return stored.trim();
        return preset == null ? "" : preset.transcriptionModel();
    }

    public static String maskedApiKey(Context context) {
        return mask(load(context).apiKey());
    }

    public static String maskedAsrApiKey(Context context) {
        return mask(load(context).asrApiKey());
    }

    private static String mask(String key) {
        if (key.isBlank()) return "";
        String tail = key.length() <= 4 ? "" : key.substring(key.length() - 4);
        return "已保存 ••••" + tail;
    }

    public static boolean validHttpsUrl(String value) {
        if (value == null) return false;
        try {
            java.net.URI uri = java.net.URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && !uri.getHost().isBlank() && uri.getUserInfo() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    static String normalizeBaseUrl(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static SharedPreferences prefs(Context context) {
        Context app = context.getApplicationContext();
        return (app == null ? context : app).getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return (SecretKey) store.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != IV_BYTES) {
                byte[] fresh = new byte[IV_BYTES];
                new SecureRandom().nextBytes(fresh);
                cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, fresh));
                iv = fresh;
            }
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            JSONObject envelope = new JSONObject();
            envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
            envelope.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
            return envelope.toString();
        } catch (Exception unavailable) {
            android.util.Log.w("OplusBandBridge", "VOICE_KEY_ENCRYPT_FAILED");
            return "";
        }
    }

    private static String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) return "";
        try {
            JSONObject envelope = new JSONObject(encoded);
            byte[] iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(envelope.getString("data"), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception unavailable) {
            android.util.Log.w("OplusBandBridge", "VOICE_KEY_DECRYPT_FAILED");
            return "";
        }
    }
}
