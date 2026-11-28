// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import org.junit.Test;

import static io.github.miam1ku.mibandoplusbridge.voice.VoiceActivityState.Event.END;
import static io.github.miam1ku.mibandoplusbridge.voice.VoiceActivityState.Event.NONE;
import static io.github.miam1ku.mibandoplusbridge.voice.VoiceActivityState.Event.START;
import static org.junit.Assert.assertEquals;

public final class VoiceActivityStateTest {
    @Test public void noiseBeforeSpeechDoesNotEndCapture() {
        VoiceActivityState state = new VoiceActivityState();
        for (int i = 0; i < 200; i++) assertEquals(NONE, state.observe(false));
    }

    @Test public void shortNoiseCannotArmTheEndpoint() {
        VoiceActivityState state = new VoiceActivityState();
        for (int i = 0; i < 8; i++) assertEquals(NONE, state.observe(true));
        for (int i = 0; i < 100; i++) assertEquals(NONE, state.observe(false));
    }

    @Test public void endsAfterSpeechAndTwelveHundredMillisecondsOfSilence() {
        VoiceActivityState state = new VoiceActivityState();
        for (int i = 0; i < 8; i++) assertEquals(NONE, state.observe(true));
        assertEquals(START, state.observe(true));
        for (int i = 0; i < 59; i++) assertEquals(NONE, state.observe(false));
        assertEquals(END, state.observe(false));
        assertEquals(NONE, state.observe(false));
    }

    @Test public void accumulatesVoicedFramesAcrossNaturalPauses() {
        VoiceActivityState state = new VoiceActivityState();
        for (int i = 0; i < 4; i++) assertEquals(NONE, state.observe(true));
        for (int i = 0; i < 10; i++) assertEquals(NONE, state.observe(false));
        for (int i = 0; i < 4; i++) assertEquals(NONE, state.observe(true));
        assertEquals(START, state.observe(true));
        assertEquals(180, state.voicedMillis());
    }

    @Test public void longPauseBeforeSpeechDiscardsScatteredNoise() {
        VoiceActivityState state = new VoiceActivityState();
        for (int i = 0; i < 5; i++) state.observe(true);
        for (int i = 0; i < 30; i++) state.observe(false);
        for (int i = 0; i < 5; i++) state.observe(true);

        assertEquals(100, state.voicedMillis());
    }

    @Test public void continuousTailSilenceCannotRetriggerAfterEndpoint() {
        VoiceActivityState state = new VoiceActivityState();
        for (int i = 0; i < 9; i++) state.observe(true);
        VoiceActivityState.Event endpoint = NONE;
        for (int i = 0; i < 60; i++) endpoint = state.observe(false);
        assertEquals(END, endpoint);
        for (int i = 0; i < 1_000; i++) assertEquals(NONE, state.observe(false));
    }
}
