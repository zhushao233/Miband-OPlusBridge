// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import android.content.Context;
import io.github.miam1ku.mibandoplusbridge.data.SessionLog;
import io.github.miam1ku.mibandoplusbridge.protocol.SppV2Codec;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Replaces the Xiaomi cloud behind the band's native voice page.
 *
 * <p>The band streams AIVS commands on SPP channel 3. This controller parses those commands,
 * turns the Opus payload into incremental transcripts, asks the configured model, and sends
 * the AIVS ASR/reply/finish instructions back through the same live session.</p>
 */
public final class VoiceAssistantController implements AutoCloseable {
    public interface WeatherContextProvider {
        String weatherContext(String question);
    }

    private static final int MAX_AUDIO_BYTES = 768 * 1024;
    private static final int MAX_OPUS_PACKETS = 1_600;
    private static final long END_SILENCE_MS = 2_500L;
    private static final long MAX_CAPTURE_MS = 30_000L;
    private static final long PARTIAL_INTERVAL_MS = 1_200L;
    private static final long DIALOG_FINISH_DELAY_MS = 120L;
    private static final int PARTIAL_WINDOW_PACKETS = 500;
    private static final int MIN_PARTIAL_PACKETS = 10;
    private static final int MAX_DEBUG_SAMPLE_BYTES = 64 * 1024;
    private static final int PROBE_BYTES = 16;

    private final Context context;
    private final VoiceReplySink replySink;
    private final WatchCommandExecutor commandExecutor;
    private final WeatherContextProvider weatherContextProvider;
    private final Consumer<String> onTranscript;
    private final Consumer<OpenAiVoiceClient.Answer> onAnswer;
    private final Consumer<String> onFailure;
    private final VoiceApiClient client;
    private final VoiceConversationHistory history;
    private final AivsStreamParser parser = new AivsStreamParser();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "OplusBandVoice");
                thread.setDaemon(true);
                return thread;
            });
    private final ExecutorService apiWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OplusBandVoiceApi");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService commandWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OplusBandVoiceCmd");
        thread.setDaemon(true);
        return thread;
    });
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final VoiceOpusFrames.FixedPacketSplitter packetSplitter;
    private final Set<String> loggedFrameGroups = new HashSet<>();
    private final List<byte[]> opusPackets = new ArrayList<>();
    private final OpusVoiceActivityDetector voiceActivity;
    private ScheduledFuture<?> idleFinish;
    private long captureStartedAt;
    private long lastAudioAt;
    private long lastPartialAt;
    private long generation;
    private int packets;
    private int desyncCount;
    private int lastSequence = -1;
    private byte[] lastPayload = new byte[0];
    private String firstChunkProbe = "";
    private String lastPartial = "";
    private VoiceAssistantSettings.Values sessionSettings;
    private int sessionId;
    private boolean ttsEnabled;
    private boolean sessionActive;
    private boolean finalizing;
    private boolean partialInFlight;
    private boolean speechSeen;
    private int audioCommands;
    private boolean closed;

    public VoiceAssistantController(Context context, VoiceReplySink replySink,
                                    WatchCommandExecutor commandExecutor,
                                    WeatherContextProvider weatherContextProvider,
                                    Consumer<String> onTranscript,
                                    Consumer<OpenAiVoiceClient.Answer> onAnswer,
                                    Consumer<String> onFailure) {
        this(context, replySink, commandExecutor, weatherContextProvider, onTranscript, onAnswer,
                onFailure, new OpenAiVoiceClient(), new VoiceConversationHistory(context));
    }

    VoiceAssistantController(Context context, VoiceReplySink replySink,
                             WatchCommandExecutor commandExecutor,
                             WeatherContextProvider weatherContextProvider,
                             Consumer<String> onTranscript,
                             Consumer<OpenAiVoiceClient.Answer> onAnswer,
                             Consumer<String> onFailure,
                             VoiceApiClient client,
                             VoiceConversationHistory history) {
        this.context = context == null ? null : context.getApplicationContext();
        this.packetSplitter = new VoiceOpusFrames.FixedPacketSplitter(
                message -> SessionLog.line(this.context, message));
        this.replySink = replySink;
        this.commandExecutor = commandExecutor;
        this.weatherContextProvider = weatherContextProvider;
        this.onTranscript = onTranscript;
        this.onAnswer = onAnswer;
        this.onFailure = onFailure;
        this.client = client == null ? new OpenAiVoiceClient() : client;
        this.history = history == null ? new VoiceConversationHistory(this.context) : history;
        this.voiceActivity = new OpusVoiceActivityDetector(this.context,
                this::voiceActivityStarted, this::voiceActivityEnded, ignored -> { });
    }

    public void onUnknownFrame(int sequence, boolean frx, int flags, int channel,
                               int opcode, byte[] payload) {
        if (closed || payload == null || payload.length == 0) return;
        if (channel == SppV2Codec.CHANNEL_MULTI_MODAL) {
            String group = "channel=" + channel + " opcode=" + opcode + " flags=" + flags;
            if (loggedFrameGroups.add(group)) {
                SessionLog.line(context, "VOICE_MULTI_MODAL " + group + " bytes=" + payload.length
                        + " frx=" + frx);
            }
            return;
        }
        if (channel != SppV2Codec.CHANNEL_VOICE
                || opcode != SppV2Codec.OPCODE_ENCRYPTED || flags != 0) return;
        synchronized (this) {
            if (sequence == lastSequence && Arrays.equals(payload, lastPayload)) return;
            lastSequence = sequence;
            lastPayload = payload.clone();
        }

        String group = "channel=" + channel + " opcode=" + opcode + " flags=" + flags;
        if (loggedFrameGroups.add(group)) {
            SessionLog.line(context, "VOICE_FRAME " + group + " bytes=" + payload.length
                    + " frx=" + frx);
        }
        List<AivsStreamParser.Command> commands = parser.feed(payload);
        AivsStreamParser.Malformed malformed = parser.takeMalformed();
        if (malformed != null) {
            if (AivsStreamParser.MALFORMED_DESYNC.equals(malformed.kind())) {
                desyncCount++;
                SessionLog.line(context, "VOICE_PARSE desync " + malformed.detail()
                        + " count=" + desyncCount);
            } else {
                SessionLog.line(context, "VOICE_PARSE truncated " + malformed.detail());
            }
        }
        for (AivsStreamParser.Command command : commands) {
            handle(command);
        }
    }

    /** A new SPP session must not inherit a split AIVS header or an old reply task. */
    public synchronized void resetTransport() {
        generation++;
        desyncCount = 0;
        sessionActive = false;
        finalizing = false;
        if (idleFinish != null) idleFinish.cancel(false);
        resetCaptureLocked();
        parser.reset();
        lastSequence = -1;
        Arrays.fill(lastPayload, (byte) 0);
        lastPayload = new byte[0];
    }

    private void handle(AivsStreamParser.Command command) {
        if (command.type() == AivsStreamParser.TYPE_START) {
            SessionLog.line(context, "VOICE_CMD type=start bytes=" + command.data().length
                    + " sid=" + command.sessionId() + " tts=" + command.ttsEnabled());
            VoiceAssistantSettings.Values loaded = VoiceAssistantSettings.load(context);
            SessionLog.setFrameTraceEnabled(loaded.captureRaw());
            if (!loaded.complete()) {
                SessionLog.line(context, "VOICE_SETTINGS waiting-for-config");
                return;
            }
            beginSession(command, loaded);
            return;
        }
        VoiceAssistantSettings.Values settings;
        synchronized (this) {
            settings = sessionSettings;
        }
        if (settings == null || !settings.complete()) return;
        switch (command.type()) {
            case AivsStreamParser.TYPE_AUDIO -> {
                synchronized (this) {
                    if (audioCommands == 0) {
                        SessionLog.line(context, "VOICE_CMD type=audio bytes="
                                + command.data().length + " gen=" + generation);
                    }
                    audioCommands++;
                }
                appendAudio(command.data(), settings);
            }
            case AivsStreamParser.TYPE_END -> {
                SessionLog.line(context, "VOICE_CMD type=end bytes=" + command.data().length
                        + " gen=" + generation);
                finishCapture("band-end", settings);
            }
            case AivsStreamParser.TYPE_QUIT -> {
                SessionLog.line(context, "VOICE_CMD type=quit bytes=" + command.data().length
                        + " gen=" + generation);
                abort("band-quit");
            }
            default -> { }
        }
    }

    synchronized void beginSession(AivsStreamParser.Command command,
                                   VoiceAssistantSettings.Values settings) {
        if (closed) return;
        resetCaptureLocked();
        generation++;
        desyncCount = 0;
        sessionSettings = settings;
        sessionId = command.sessionId();
        ttsEnabled = command.ttsEnabled();
        sessionActive = true;
        finalizing = false;
        speechSeen = false;
        audioCommands = 0;
        captureStartedAt = System.currentTimeMillis();
        lastAudioAt = captureStartedAt;
        lastPartialAt = captureStartedAt;
        lastPartial = "";
        firstChunkProbe = "";
        voiceActivity.reset();
        SessionLog.line(context, "VOICE_SESSION start id=" + sessionId + " tts=" + ttsEnabled
                + " gen=" + generation);
    }

    void appendAudio(byte[] data, VoiceAssistantSettings.Values settings) {
        if (data == null || data.length == 0) return;
        List<byte[]> ready;
        long now = System.currentTimeMillis();
        boolean tooLarge = false;
        synchronized (this) {
            if (closed || !sessionActive || finalizing) return;
            if (captured.size() == 0) firstChunkProbe = hexPrefix(data);
            captured.writeBytes(data);
            ready = packetSplitter.accept(data);
            opusPackets.addAll(ready);
            packets++;
            lastAudioAt = now;
            if (captured.size() > MAX_AUDIO_BYTES || opusPackets.size() > MAX_OPUS_PACKETS
                    || now - captureStartedAt > MAX_CAPTURE_MS) {
                tooLarge = true;
            } else {
                scheduleIdleLocked();
            }
        }
        if (!ready.isEmpty()) voiceActivity.accept(ready);
        if (tooLarge) {
            finishCapture("size-limit", settings);
            return;
        }
        maybeStartPartial(settings, now);
    }

    void maybeStartPartial(VoiceAssistantSettings.Values settings, long now) {
        List<byte[]> window;
        long currentGeneration;
        String previous;
        int currentSession;
        synchronized (this) {
            if (closed || !sessionActive || finalizing) return;
            if (partialInFlight) return;
            if (now - lastPartialAt < PARTIAL_INTERVAL_MS) return;
            if (opusPackets.size() < MIN_PARTIAL_PACKETS) return;
            lastPartialAt = now;
            partialInFlight = true;
            int from = Math.max(0, opusPackets.size() - PARTIAL_WINDOW_PACKETS);
            window = clonePackets(opusPackets, from);
            currentGeneration = generation;
            previous = lastPartial;
            currentSession = sessionId;
        }
        try {
            apiWorker.execute(() -> partial(settings, window, currentGeneration, previous,
                    currentSession));
        } catch (RuntimeException rejected) {
            synchronized (this) {
                partialInFlight = false;
            }
            wipe(window);
        }
    }

    private void partial(VoiceAssistantSettings.Values settings, List<byte[]> window,
                         long currentGeneration, String previous, int currentSession) {
        byte[] ogg = null;
        try {
            synchronized (this) {
                if (closed || currentGeneration != generation || finalizing) return;
            }
            ogg = VoiceOpusFrames.toOggOpus(window);
            String text = client.transcribe(settings, ogg).trim();
            if (text.isEmpty() || text.equals(previous)) return;
            synchronized (this) {
                if (closed || currentGeneration != generation || finalizing) return;
                lastPartial = text;
            }
            boolean sent = replySink.send(AivsInstructionCodec.asr(currentSession, text, false));
            SessionLog.line(context, "VOICE_PARTIAL chars=" + text.length() + " sent=" + sent);
        } catch (Exception ignored) {
            // A partial transcript is best effort; the final request is authoritative.
        } finally {
            wipe(window);
            if (ogg != null) Arrays.fill(ogg, (byte) 0);
            synchronized (this) {
                partialInFlight = false;
            }
        }
    }

    private void scheduleIdleLocked() {
        if (idleFinish != null) idleFinish.cancel(false);
        long remaining = Math.max(0, MAX_CAPTURE_MS - (System.currentTimeMillis() - captureStartedAt));
        idleFinish = worker.schedule(this::finishFromIdle,
                Math.min(END_SILENCE_MS, remaining), TimeUnit.MILLISECONDS);
    }

    private void finishFromIdle() {
        VoiceAssistantSettings.Values settings = VoiceAssistantSettings.load(context);
        synchronized (this) {
            if (closed || !sessionActive || finalizing) return;
        }
        if (!settings.complete()) {
            abort("settings-missing");
            return;
        }
        finishCapture("silence", settings);
    }

    private void voiceActivityStarted() {
        synchronized (this) {
            if (!closed) speechSeen = true;
        }
    }

    private void voiceActivityEnded() {
        VoiceAssistantSettings.Values settings;
        synchronized (this) {
            if (closed || !sessionActive || finalizing || !speechSeen) return;
            settings = sessionSettings;
        }
        if (settings == null || !settings.complete()) return;
        finishCapture("speech-end", settings);
    }

    void finishCapture(String reason, VoiceAssistantSettings.Values settings) {
        List<byte[]> opus;
        byte[] stream;
        int sid;
        boolean tts;
        long currentGeneration;
        int chunkCount;
        int commandCount;
        int droppedTail;
        String probe;
        boolean heard;
        synchronized (this) {
            if (closed || !sessionActive || finalizing) return;
            finalizing = true;
            generation++;
            currentGeneration = generation;
            if (idleFinish != null) idleFinish.cancel(false);
            idleFinish = null;
            byte[] remainder = packetSplitter.takeRemainder();
            droppedTail = remainder.length;
            Arrays.fill(remainder, (byte) 0);
            opus = clonePackets(opusPackets, 0);
            chunkCount = packets;
            commandCount = audioCommands;
            probe = firstChunkProbe;
            heard = speechSeen;
            stream = captured.toByteArray();
            sid = sessionId;
            tts = ttsEnabled;
            sessionActive = false;
            resetCaptureLocked();
            voiceActivity.reset();
        }
        if (droppedTail > 0) {
            SessionLog.line(context, "VOICE_AUDIO dropped-tail bytes=" + droppedTail);
        }
        if (!"band-end".equals(reason)) {
            send(sid, AivsInstructionCodec.stopCapture(sid));
        }
        logFrameDiagnostics("finish", stream.length, chunkCount, opus, probe);
        if (settings.captureRaw()) saveDebugSample(stream, "finish");
        if (opus.size() < 2) {
            wipe(opus);
            Arrays.fill(stream, (byte) 0);
            sendError(sid, "请再说一遍，我重新识别。", tts);
            onFailure.accept("VOICE_FORMAT");
            return;
        }
        byte[] ogg;
        try {
            ogg = VoiceOpusFrames.toOggOpus(opus);
        } catch (RuntimeException malformed) {
            wipe(opus);
            Arrays.fill(stream, (byte) 0);
            sendError(sid, "请再说一遍，我重新处理语音帧。", tts);
            onFailure.accept("VOICE_OGG");
            return;
        } finally {
            wipe(opus);
            Arrays.fill(stream, (byte) 0);
        }
        SessionLog.line(context, "VOICE_CAPTURE end=" + reason + " chunks=" + chunkCount
                + " opusPackets=" + opus.size() + " ogg=" + ogg.length
                + " audioCommands=" + commandCount
                + " probe=" + probe + " speechSeen=" + heard
                + " gen=" + currentGeneration);
        try {
            apiWorker.execute(() -> finalizeSession(settings, ogg, sid, tts, currentGeneration));
        } catch (RuntimeException rejected) {
            Arrays.fill(ogg, (byte) 0);
            SessionLog.line(context, "VOICE_API worker-rejected");
            if (isCurrent(currentGeneration)) {
                sendError(sid, "语音助手正在恢复，请稍后再试。", tts);
                onFailure.accept("VOICE_API_WORKER_REJECTED");
            }
        }
    }

    private void finalizeSession(VoiceAssistantSettings.Values settings, byte[] ogg,
                                 int sid, boolean tts, long currentGeneration) {
        try {
            String transcript;
            try {
                SessionLog.line(context, "VOICE_API request bytes=" + ogg.length);
                transcript = client.transcribe(settings, ogg).trim();
            } catch (Exception failed) {
                synchronized (this) {
                    if (closed || currentGeneration != generation) return;
                    transcript = lastPartial;
                }
                SessionLog.line(context, "VOICE_ASR final-failed use-partial="
                        + transcript.length());
            }
            if (!isCurrent(currentGeneration)) return;
            if (transcript.isEmpty()) {
                sendError(sid, "请再说一遍，我重新识别。", tts);
                if (isCurrent(currentGeneration)) onFailure.accept("VOICE_TRANSCRIPT_EMPTY");
                return;
            }
            if (!send(sid, AivsInstructionCodec.asr(sid, transcript, true))) {
                SessionLog.line(context, "VOICE_ASR send-failed");
            }
            onTranscript.accept(transcript);
            String localTimeReply = LocalTimeQuestion.answer(transcript);
            if (localTimeReply != null) {
                if (!isCurrent(currentGeneration)) return;
                String compact = VoiceText.compact(localTimeReply, 300);
                SessionLog.line(context, "VOICE_LOCAL_TIME replyChars=" + compact.length());
                if (!sendReply(sid, compact, tts)) {
                    SessionLog.line(context, "VOICE_LOCAL_TIME send-failed");
                }
                publishAnswer(transcript, compact, VoiceConversationHistory.Kind.LOCAL);
                return;
            }
            WatchCommand command = WatchCommandParser.parse(transcript,
                    java.time.LocalTime.now());
            if (command != null) {
                if (command.kind() == WatchCommand.Kind.OPEN_WATCH_APP) {
                    WatchCommand.WatchApp app = command.app();
                    boolean sent = app != null && send(sid,
                            AivsInstructionCodec.openApp(sid, app.packageName(), app.uri()));
                    SessionLog.line(context, "VOICE_WATCH app="
                            + (app == null ? "none" : app.name())
                            + " pkg=" + (app == null ? "" : app.packageName())
                            + " sent=" + sent);
                    if (sent) {
                        scheduleFinish(sid);
                    }
                    if (!isCurrent(currentGeneration)) return;
                    String reply = app == null
                            ? "请指定要打开的手环应用。"
                            : sent ? "正在打开" + app.label() + "。"
                            : "手环当前处于断开状态，请连接后再打开" + app.label() + "。";
                    publishAnswer(transcript, reply, VoiceConversationHistory.Kind.WATCH);
                    return;
                }
                dispatchWatchCommand(sid, tts, transcript, currentGeneration, command);
                return;
            }
            String reply;
            VoiceConversationHistory.Kind kind = VoiceConversationHistory.Kind.MODEL;
            try {
                if (WeatherQuestion.asks(transcript)) {
                    kind = VoiceConversationHistory.Kind.WEATHER;
                    String weather = "";
                    try {
                        if (weatherContextProvider != null) {
                            weather = weatherContextProvider.weatherContext(transcript);
                        }
                    } catch (Exception unavailable) {
                        SessionLog.line(context, "VOICE_WEATHER unavailable");
                    }
                    if (weather == null || weather.isBlank()) {
                        weather = "本轮实时天气快照为空。"
                                + "请告诉用户当前暂无实时天气数据，并只回答已有信息。";
                    }
                    reply = client.chat(settings, transcript, weather).trim();
                } else {
                    reply = client.chat(settings, transcript).trim();
                }
            } catch (Exception failed) {
                String code = failed.getMessage();
                if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,80}")) {
                    code = failed.getClass().getSimpleName();
                }
                if (isCurrent(currentGeneration)) {
                    sendError(sid, "AI 服务正在恢复，请稍后再试。", tts);
                    onFailure.accept(code);
                }
                return;
            }
            if (!isCurrent(currentGeneration)) return;
            if (reply.isEmpty()) {
                sendError(sid, "AI 回复正在准备，请再试一次。", tts);
                if (isCurrent(currentGeneration)) onFailure.accept("VOICE_REPLY_EMPTY");
                return;
            }
            String compact = VoiceText.compact(reply, 300);
            SessionLog.line(context, "VOICE_API complete replyChars=" + compact.length());
            if (!sendReply(sid, compact, tts)) {
                SessionLog.line(context, "VOICE_ANSWER send-failed");
            }
            publishAnswer(transcript, compact, kind);
        } catch (Exception failure) {
            String code = failure.getMessage();
            if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,80}")) {
                code = failure.getClass().getSimpleName();
            }
            SessionLog.line(context, "VOICE_API failed " + code);
            if (isCurrent(currentGeneration)) {
                sendError(sid, "语音助手正在恢复，请稍后再试。", tts);
                onFailure.accept(code);
            }
        } finally {
            Arrays.fill(ogg, (byte) 0);
        }
    }

    private void publishAnswer(String transcript, String reply,
                               VoiceConversationHistory.Kind kind) {
        try {
            history.append(new VoiceConversationHistory.Entry(
                    System.currentTimeMillis(), transcript, reply, kind));
        } catch (RuntimeException invalid) {
            SessionLog.line(context, "VOICE_HISTORY write-failed");
        }
        onAnswer.accept(new OpenAiVoiceClient.Answer(transcript, reply, kind.name()));
    }

    private boolean send(int sid, byte[] instruction) {
        if (closed) return false;
        boolean sent = replySink.send(instruction);
        SessionLog.line(context, "VOICE_AIVS send bytes=" + instruction.length
                + " session=" + sid + " ok=" + sent);
        return sent;
    }

    private synchronized boolean isCurrent(long expectedGeneration) {
        return !closed && generation == expectedGeneration;
    }

    /** Test hook: waits until every turn queued before this call has completed. */
    void awaitApiIdleForTest() {
        CountDownLatch idle = new CountDownLatch(1);
        try {
            apiWorker.execute(idle::countDown);
        } catch (RuntimeException rejected) {
            return;
        }
        try {
            if (!idle.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("VOICE_TEST_API_TIMEOUT");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("VOICE_TEST_INTERRUPTED", interrupted);
        }
    }

    synchronized boolean partialInFlightForTest() {
        return partialInFlight;
    }

    private void sendError(int sid, String text, boolean tts) {
        sendReply(sid, text, tts);
    }

    private boolean sendReply(int sid, String text, boolean tts) {
        if (tts) send(sid, AivsInstructionCodec.tts(sid, text));
        if (!send(sid, AivsInstructionCodec.toast(sid, text))) return false;
        if (!tts) {
            // The official app sends Toast and DialogFinish as two separate SPP messages.
            return scheduleFinish(sid);
        }
        return send(sid, AivsInstructionCodec.finish(sid));
    }

    private boolean scheduleFinish(int sid) {
        try {
            worker.schedule(() -> send(sid, AivsInstructionCodec.finish(sid)),
                    DIALOG_FINISH_DELAY_MS, TimeUnit.MILLISECONDS);
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    private void dispatchWatchCommand(int sid, boolean tts, String transcript,
                                      long currentGeneration,
                                      WatchCommand command) {
        try {
            commandWorker.execute(() -> executeWatchCommand(
                    sid, tts, transcript, currentGeneration, command));
        } catch (RuntimeException rejected) {
            SessionLog.line(context, "VOICE_WATCH worker-rejected");
            if (isCurrent(currentGeneration)) {
                sendError(sid, "手环指令发送中断，请确认手环已连接后再试一次。", tts);
                onFailure.accept("VOICE_WATCH_WORKER_REJECTED");
            }
        }
    }

    private void executeWatchCommand(int sid, boolean tts, String transcript,
                                     long currentGeneration,
                                     WatchCommand command) {
        if (!isCurrent(currentGeneration)) return;
        String reply;
        try {
            SessionLog.line(context, "VOICE_WATCH command=" + command.kind());
            reply = commandExecutor == null ? "" : commandExecutor.execute(command);
        } catch (Exception failed) {
            String code = failed.getMessage();
            if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,80}")) {
                code = failed.getClass().getSimpleName();
            }
            SessionLog.line(context, "VOICE_WATCH failed " + code);
            reply = "手环指令发送中断，请确认手环已连接后再试一次。";
        }
        if (!isCurrent(currentGeneration)) return;
        String compact = VoiceText.compact(reply, 300);
        if (!compact.isBlank()) sendReply(sid, compact, tts);
        publishAnswer(transcript, compact, VoiceConversationHistory.Kind.WATCH);
    }

    private synchronized void abort(String reason) {
        if (idleFinish != null) idleFinish.cancel(false);
        idleFinish = null;
        generation++;
        sessionActive = false;
        finalizing = false;
        resetCaptureLocked();
        voiceActivity.reset();
        SessionLog.line(context, "VOICE_SESSION abort=" + reason);
    }

    private void resetCaptureLocked() {
        captured.reset();
        packetSplitter.reset();
        wipe(opusPackets);
        opusPackets.clear();
        packets = 0;
        audioCommands = 0;
        captureStartedAt = 0;
        lastAudioAt = 0;
        firstChunkProbe = "";
        speechSeen = false;
        sessionSettings = null;
        if (idleFinish != null) {
            idleFinish.cancel(false);
            idleFinish = null;
        }
    }

    private void logFrameDiagnostics(String stage, int streamBytes, int chunks,
                                     List<byte[]> opus, String probe) {
        SessionLog.line(context, "VOICE_FRAMES stage=" + stage + " chunks=" + chunks
                + " extracted=" + opus.size() + " streamBytes=" + streamBytes
                + " probe=" + probe);
        if (!opus.isEmpty()) {
            int minimum = Integer.MAX_VALUE;
            int maximum = 0;
            int total = 0;
            for (byte[] packet : opus) {
                minimum = Math.min(minimum, packet.length);
                maximum = Math.max(maximum, packet.length);
                total += packet.length;
            }
            SessionLog.line(context, "VOICE_PACKET firstLen=" + opus.get(0).length
                    + " minLen=" + minimum + " maxLen=" + maximum + " bytes=" + total
                    + " firstToc=0x" + String.format(Locale.ROOT, "%02x",
                    opus.get(0)[0] & 0xff));
        }
    }

    private boolean saveDebugSample(byte[] stream, String reason) {
        try {
            File directory = new File(context.getNoBackupFilesDir(), "voice-samples");
            if (!directory.isDirectory() && !directory.mkdirs()) return false;
            File target = new File(directory, String.format(Locale.ROOT,
                    "voice-%d.raw", System.currentTimeMillis()));
            int length = Math.min(stream.length, MAX_DEBUG_SAMPLE_BYTES);
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(stream, 0, length);
            }
            try {
                Files.setPosixFilePermissions(target.toPath(),
                        EnumSet.of(PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE));
            } catch (Exception ignored) { }
            SessionLog.line(context, "VOICE_DEBUG_SAMPLE reason=" + reason
                    + " bytes=" + length + " captured=" + stream.length
                    + " truncated=" + (length < stream.length));
            return true;
        } catch (Exception storageFailed) {
            SessionLog.line(context, "VOICE_DEBUG_SAMPLE failed");
            return false;
        }
    }

    @Override public synchronized void close() {
        closed = true;
        generation++;
        sessionActive = false;
        finalizing = false;
        if (idleFinish != null) idleFinish.cancel(false);
        captured.reset();
        packetSplitter.reset();
        voiceActivity.close();
        wipe(opusPackets);
        opusPackets.clear();
        Arrays.fill(lastPayload, (byte) 0);
        lastPayload = new byte[0];
        sessionSettings = null;
        parser.reset();
        worker.shutdownNow();
        apiWorker.shutdownNow();
        commandWorker.shutdownNow();
        loggedFrameGroups.clear();
    }

    private static List<byte[]> clonePackets(List<byte[]> packets, int from) {
        List<byte[]> copy = new ArrayList<>(Math.max(0, packets.size() - from));
        for (int index = Math.max(0, from); index < packets.size(); index++) {
            copy.add(packets.get(index).clone());
        }
        return copy;
    }

    private static void wipe(List<byte[]> packets) {
        for (byte[] packet : packets) Arrays.fill(packet, (byte) 0);
    }

    private static String hexPrefix(byte[] payload) {
        int count = Math.min(PROBE_BYTES, payload.length);
        StringBuilder probe = new StringBuilder(count * 3);
        for (int index = 0; index < count; index++) {
            if (index > 0) probe.append(' ');
            int value = payload[index] & 0xff;
            if (value < 0x10) probe.append('0');
            probe.append(Integer.toHexString(value));
        }
        return probe.toString();
    }
}
