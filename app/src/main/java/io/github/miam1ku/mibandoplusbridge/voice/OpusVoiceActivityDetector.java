// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import io.github.miam1ku.mibandoplusbridge.data.SessionLog;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.concentus.OpusException;

/**
 * Decodes the band's raw Opus packets only far enough to detect the end of speech.
 *
 * <p>The watch keeps streaming microphone frames even after the user stops talking, so packet
 * arrival alone cannot identify the end of a request. RMS is evaluated on decoded PCM instead.</p>
 */
final class OpusVoiceActivityDetector implements AutoCloseable {
    private static final int MAX_QUEUED_PACKETS = 2_000;
    private static final int MAX_CONSECUTIVE_DECODE_FAILURES = 100;
    /** Measured on the target band; a fixed threshold keeps this PR free of a new setting. */
    private static final double MIN_VOICE_RMS = 200.0;
    private static final int METRICS_FRAME_INTERVAL = 250;

    private final android.content.Context context;
    private final Runnable onSpeechStart;
    private final Runnable onSpeechEnd;
    private final Consumer<Boolean> onAvailability;
    private final Object lock = new Object();
    private final ArrayDeque<byte[]> packets = new ArrayDeque<>();
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OplusBandVoiceVad");
        thread.setDaemon(true);
        return thread;
    });
    private final VoiceActivityState state = new VoiceActivityState();
    private final short[] pcm = new short[OpusPcmDecoder.MAX_FRAME_SAMPLES];
    private boolean resetRequested = true;
    private boolean closed;
    private long decodedFrames;
    private long decodeFailures;
    private int consecutiveDecodeFailures;
    private long nextMetricsFrame = METRICS_FRAME_INTERVAL;
    private double rmsMin = Double.MAX_VALUE;
    private double rmsMax;
    private int nearThresholdFrames;
    private boolean frameMismatchLogged;

    OpusVoiceActivityDetector(android.content.Context context, Runnable onSpeechStart,
                              Runnable onSpeechEnd, Consumer<Boolean> onAvailability) {
        this.context = context == null ? null : context.getApplicationContext();
        this.onSpeechStart = onSpeechStart;
        this.onSpeechEnd = onSpeechEnd;
        this.onAvailability = onAvailability;
        decoder.execute(this::decodeLoopSafely);
    }

    void accept(List<byte[]> frames) {
        if (frames == null || frames.isEmpty()) return;
        synchronized (lock) {
            if (closed) return;
            for (byte[] frame : frames) {
                if (frame == null || frame.length == 0) continue;
                packets.addLast(frame.clone());
            }
            while (packets.size() > MAX_QUEUED_PACKETS) {
                wipe(packets.removeFirst());
            }
            lock.notifyAll();
        }
    }

    void reset() {
        synchronized (lock) {
            if (closed) return;
            resetRequested = true;
            state.reset();
            while (!packets.isEmpty()) wipe(packets.removeFirst());
            lock.notifyAll();
        }
    }

    private void decodeLoopSafely() {
        try {
            decodeLoop();
        } catch (RuntimeException failure) {
            SessionLog.line(context, "VOICE_VAD stopped "
                    + failure.getClass().getSimpleName());
            reportAvailability(false);
        } finally {
            synchronized (lock) {
                while (!packets.isEmpty()) wipe(packets.removeFirst());
            }
        }
    }

    private void decodeLoop() {
        OpusPcmDecoder decoder = null;
        boolean reportedUnavailable = false;
        try {
            while (true) {
                byte[] packet = null;
                boolean reset;
                synchronized (lock) {
                    while (!closed && !resetRequested && packets.isEmpty()) {
                        try {
                            lock.wait();
                        } catch (InterruptedException stopped) {
                            return;
                        }
                    }
                    if (closed) return;
                    reset = resetRequested;
                    resetRequested = false;
                    if (!reset) packet = packets.removeFirst();
                }

                if (reset) {
                    releaseDecoder(decoder);
                    decoder = null;
                    decodedFrames = 0;
                    decodeFailures = 0;
                    consecutiveDecodeFailures = 0;
                    nextMetricsFrame = METRICS_FRAME_INTERVAL;
                    rmsMin = Double.MAX_VALUE;
                    rmsMax = 0;
                    nearThresholdFrames = 0;
                    frameMismatchLogged = false;
                    if (packet != null) wipe(packet);
                    continue;
                }
                if (reportedUnavailable) {
                    if (packet != null) wipe(packet);
                    synchronized (lock) {
                        while (!packets.isEmpty()) wipe(packets.removeFirst());
                        if (!closed) {
                            try {
                                lock.wait(1_000L);
                            } catch (InterruptedException stopped) {
                                return;
                            }
                        }
                    }
                    continue;
                }
                if (decoder == null) {
                    decoder = createDecoder();
                    if (decoder == null) {
                        reportedUnavailable = true;
                        if (packet != null) wipe(packet);
                        continue;
                    }
                }
                if (packet == null) continue;
                boolean healthy = feed(decoder, packet);
                wipe(packet);
                if (!healthy) {
                    releaseDecoder(decoder);
                    decoder = null;
                    reportedUnavailable = true;
                    SessionLog.line(context, "VOICE_VAD stopped decoder-state");
                    reportAvailability(false);
                }
            }
        } finally {
            releaseDecoder(decoder);
            synchronized (lock) {
                while (!packets.isEmpty()) wipe(packets.removeFirst());
            }
        }
    }

    private OpusPcmDecoder createDecoder() {
        OpusPcmDecoder decoder = null;
        try {
            decoder = new OpusPcmDecoder();
            SessionLog.line(context, "VOICE_VAD ready rate="
                    + OpusPcmDecoder.SAMPLE_RATE_HZ
                    + " channels=" + OpusPcmDecoder.CHANNELS + " decoder=concentus");
            reportAvailability(true);
            return decoder;
        } catch (Exception unavailable) {
            SessionLog.line(context, "VOICE_VAD unavailable "
                    + unavailable.getClass().getSimpleName() + " "
                    + String.valueOf(unavailable.getMessage()));
            releaseDecoder(decoder);
            reportAvailability(false);
            return null;
        }
    }

    private boolean feed(OpusPcmDecoder decoder, byte[] packet) {
        try {
            int samples = decoder.decode(packet, pcm);
            if (samples <= 0) return true;
            checkFrameLength(samples);
            consecutiveDecodeFailures = 0;
            double rms = rms(pcm, samples);
            int durationMillis = Math.max(1,
                    Math.round(samples * 1_000f / OpusPcmDecoder.SAMPLE_RATE_HZ));
            boolean voiced = rms >= MIN_VOICE_RMS;
            VoiceActivityState.Event event;
            long voicedMillis;
            synchronized (lock) {
                event = state.observe(voiced, durationMillis);
                voicedMillis = state.voicedMillis();
            }
            recordMetrics(rms);
            if (event == VoiceActivityState.Event.START) {
                SessionLog.line(context, "VOICE_VAD start voicedMs="
                        + voicedMillis);
                runCallback(onSpeechStart, "start");
            } else if (event == VoiceActivityState.Event.END) {
                SessionLog.line(context, "VOICE_VAD end=silence nearThreshold="
                        + nearThresholdFrames + " rmsThreshold=" + Math.round(MIN_VOICE_RMS));
                nearThresholdFrames = 0;
                runCallback(onSpeechEnd, "end");
            }
            return true;
        } catch (OpusException | RuntimeException failed) {
            decodeFailures++;
            consecutiveDecodeFailures++;
            if (decodeFailures == 1 || decodeFailures % 100 == 0) {
                SessionLog.line(context, "VOICE_VAD decode-failed packet="
                        + decodeFailures + " consecutive=" + consecutiveDecodeFailures
                        + " type=" + failed.getClass().getSimpleName());
            }
            if (consecutiveDecodeFailures < MAX_CONSECUTIVE_DECODE_FAILURES) return true;
            SessionLog.line(context, "VOICE_VAD decode-failed limit="
                    + consecutiveDecodeFailures + " total=" + decodeFailures);
            return false;
        }
    }

    /** The firmware is assumed to send 20 ms frames; a different length silently stretches time. */
    private void checkFrameLength(int samples) {
        if (frameMismatchLogged) return;
        int tolerance = Math.max(1, VoiceOpusFrames.FRAME_GRANULE / 20);
        if (Math.abs(samples - VoiceOpusFrames.FRAME_GRANULE) <= tolerance) return;
        frameMismatchLogged = true;
        SessionLog.line(context, "VOICE_FRAME_MISMATCH samples=" + samples
                + " expected=" + VoiceOpusFrames.FRAME_GRANULE);
    }

    private void runCallback(Runnable callback, String label) {
        try {
            callback.run();
        } catch (RuntimeException failure) {
            SessionLog.line(context, "VOICE_VAD callback-failed " + label + " "
                    + failure.getClass().getSimpleName());
        }
    }

    private void reportAvailability(boolean available) {
        try {
            onAvailability.accept(available);
        } catch (RuntimeException ignored) { }
    }

    private static void releaseDecoder(OpusPcmDecoder decoder) {
        if (decoder == null) return;
        try { decoder.close(); } catch (RuntimeException ignored) { }
    }

    private void recordMetrics(double rms) {
        decodedFrames++;
        rmsMin = Math.min(rmsMin, rms);
        rmsMax = Math.max(rmsMax, rms);
        if (Math.abs(rms - MIN_VOICE_RMS) <= MIN_VOICE_RMS * 0.2) nearThresholdFrames++;
        while (decodedFrames >= nextMetricsFrame) {
            SessionLog.line(context, "VOICE_VAD pcm frames=" + decodedFrames
                    + " rate=" + OpusPcmDecoder.SAMPLE_RATE_HZ
                    + " channels=" + OpusPcmDecoder.CHANNELS
                    + " rmsMin=" + Math.round(rmsMin)
                    + " rmsMax=" + Math.round(rmsMax)
                    + " rmsThreshold=" + Math.round(MIN_VOICE_RMS)
                    + " decodeFailures=" + decodeFailures);
            nextMetricsFrame += METRICS_FRAME_INTERVAL;
        }
    }

    private static double rms(short[] samples, int count) {
        if (samples == null || count <= 0) return 0;
        double sum = 0;
        int limit = Math.min(samples.length, count);
        for (int index = 0; index < limit; index++) {
            double sample = samples[index];
            sum += sample * sample;
        }
        return limit == 0 ? 0 : Math.sqrt(sum / limit);
    }

    @Override public void close() {
        synchronized (lock) {
            closed = true;
            while (!packets.isEmpty()) wipe(packets.removeFirst());
            lock.notifyAll();
        }
        decoder.shutdownNow();
    }

    private static void wipe(byte[] bytes) {
        if (bytes != null) Arrays.fill(bytes, (byte) 0);
    }
}
