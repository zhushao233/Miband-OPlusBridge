// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local-only voice Q&A history. It never stores audio, requests, prompts, or API keys. */
public final class VoiceConversationHistory {
    public static final int MAX_ENTRIES = 200;
    private static final String FILE_NAME = "voice-conversations.json";
    private static final long MAX_FILE_BYTES = 1024 * 1024L;
    private static final int MAX_USER_CHARS = 2_000;
    private static final int MAX_REPLY_CHARS = 4_000;
    private static final Object LOCK = new Object();

    public enum Kind {
        MODEL, WATCH, WEATHER, LOCAL;

        public static Kind fromWire(String value) {
            if (value != null) {
                try {
                    return valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) { }
            }
            return MODEL;
        }
    }

    public record Entry(long timeMs, String user, String reply, Kind kind) {
        public Entry {
            if (timeMs <= 0) throw new IllegalArgumentException("VOICE_HISTORY_TIME");
            user = clean(user, MAX_USER_CHARS);
            reply = clean(reply, MAX_REPLY_CHARS);
            if (user.isEmpty() || reply.isEmpty()) {
                throw new IllegalArgumentException("VOICE_HISTORY_TEXT");
            }
            if (kind == null) kind = Kind.MODEL;
        }
    }

    private final File file;

    public VoiceConversationHistory(Context context) {
        Context app = context.getApplicationContext();
        if (app != null) context = app;
        file = new File(context.getNoBackupFilesDir(), FILE_NAME);
    }

    VoiceConversationHistory(File file) {
        this.file = file;
    }

    public List<Entry> recent() {
        synchronized (LOCK) {
            return read();
        }
    }

    public boolean append(Entry entry) {
        if (entry == null) return false;
        synchronized (LOCK) {
            List<Entry> entries = new ArrayList<>(read());
            entries.add(entry);
            entries.sort(Comparator.comparingLong(Entry::timeMs).reversed());
            if (entries.size() > MAX_ENTRIES) {
                entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
            }
            return write(entries);
        }
    }

    public boolean clear() {
        synchronized (LOCK) {
            File temporary = new File(file.getPath() + ".tmp");
            temporary.delete();
            return !file.exists() || file.delete();
        }
    }

    private List<Entry> read() {
        if (!file.isFile() || file.length() == 0 || file.length() > MAX_FILE_BYTES) {
            return List.of();
        }
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JSONArray stored = new JSONObject(text).optJSONArray("entries");
            if (stored == null) return List.of();
            List<Entry> entries = new ArrayList<>();
            for (int index = 0; index < stored.length(); index++) {
                JSONObject item = stored.optJSONObject(index);
                if (item == null) continue;
                try {
                    entries.add(new Entry(item.getLong("timeMs"),
                            item.getString("user"), item.getString("reply"),
                            Kind.fromWire(item.optString("kind", "MODEL"))));
                } catch (RuntimeException invalid) {
                    // One damaged row must not hide the remaining local history.
                }
            }
            entries.sort(Comparator.comparingLong(Entry::timeMs).reversed());
            if (entries.size() > MAX_ENTRIES) {
                return List.copyOf(entries.subList(0, MAX_ENTRIES));
            }
            return List.copyOf(entries);
        } catch (Exception invalid) {
            return List.of();
        }
    }

    private boolean write(List<Entry> entries) {
        JSONArray stored = new JSONArray();
        for (Entry entry : entries) {
            try {
                stored.put(new JSONObject()
                        .put("timeMs", entry.timeMs())
                        .put("user", entry.user())
                        .put("reply", entry.reply())
                        .put("kind", entry.kind().name()));
            } catch (Exception invalid) {
                return false;
            }
        }
        try {
            JSONObject root = new JSONObject().put("version", 1).put("entries", stored);
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
            File temporary = new File(file.getPath() + ".tmp");
            Files.writeString(temporary.toPath(), root.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception failed) {
            new File(file.getPath() + ".tmp").delete();
            return false;
        }
    }

    private static String clean(String value, int limit) {
        if (value == null) return "";
        String cleaned = value.replace('\u0000', ' ').trim();
        int characters = cleaned.codePointCount(0, cleaned.length());
        if (characters <= limit || limit <= 0) return limit <= 0 ? "" : cleaned;
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, limit));
    }
}
