// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

/** Small deterministic state machine around decoded 20 ms Opus frames. */
final class VoiceActivityState {
    static final int FRAME_MILLIS = 20;
    static final int MIN_SPEECH_MILLIS = 180;
    static final int MAX_NATURAL_PAUSE_MILLIS = 500;
    static final int END_SILENCE_MILLIS = 1_200;

    enum Event {
        NONE,
        START,
        END
    }

    private int voicedMillis;
    private int silentMillis;
    private boolean speechSeen;
    private boolean triggered;

    void reset() {
        voicedMillis = 0;
        silentMillis = 0;
        speechSeen = false;
        triggered = false;
    }

    Event observe(boolean voiced) {
        return observe(voiced, FRAME_MILLIS);
    }

    /**
     * Speech is accumulated across natural pauses. It does not need to occupy one continuous
     * run of frames; the band's Opus stream commonly alternates short voiced and unvoiced frames.
     */
    Event observe(boolean voiced, int durationMillis) {
        if (triggered) return Event.NONE;
        int duration = Math.max(1, durationMillis);
        if (voiced) {
            voicedMillis += duration;
            silentMillis = 0;
            if (!speechSeen && voicedMillis >= MIN_SPEECH_MILLIS) {
                speechSeen = true;
                return Event.START;
            }
            return Event.NONE;
        }
        silentMillis += duration;
        if (!speechSeen) {
            if (silentMillis >= MAX_NATURAL_PAUSE_MILLIS) {
                voicedMillis = 0;
                silentMillis = 0;
            }
            return Event.NONE;
        }
        if (silentMillis < END_SILENCE_MILLIS) return Event.NONE;
        triggered = true;
        return Event.END;
    }

    int voicedMillis() {
        return voicedMillis;
    }

    int silentMillis() {
        return silentMillis;
    }
}
