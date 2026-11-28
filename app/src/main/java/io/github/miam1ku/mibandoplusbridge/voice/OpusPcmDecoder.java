// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import org.concentus.OpusDecoder;
import org.concentus.OpusException;

/** Controllable Opus-to-PCM decoder used only by the local voice activity detector. */
final class OpusPcmDecoder implements AutoCloseable {
    static final int SAMPLE_RATE_HZ = 48_000;
    static final int CHANNELS = 1;
    static final int MAX_FRAME_SAMPLES = 5_760;

    private OpusDecoder decoder;

    OpusPcmDecoder() throws OpusException {
        decoder = new OpusDecoder(SAMPLE_RATE_HZ, CHANNELS);
    }

    int decode(byte[] packet, short[] pcm) throws OpusException {
        if (decoder == null) throw new OpusException("decoder-closed");
        return decoder.decode(packet, 0, packet.length, pcm, 0,
                Math.min(pcm.length, MAX_FRAME_SAMPLES), false);
    }

    void reset() {
        if (decoder != null) decoder.resetState();
    }

    @Override public void close() {
        decoder = null;
    }
}
