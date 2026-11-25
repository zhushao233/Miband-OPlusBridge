// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.util.Arrays;
import org.concentus.OpusApplication;
import org.concentus.OpusEncoder;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class OpusPcmDecoderTest {
    @Test public void decodesOpusPacketsToPcmAndResetsState() throws Exception {
        OpusEncoder encoder = new OpusEncoder(OpusPcmDecoder.SAMPLE_RATE_HZ,
                OpusPcmDecoder.CHANNELS, OpusApplication.OPUS_APPLICATION_VOIP);
        short[] input = new short[960];
        for (int index = 0; index < input.length; index++) {
            input[index] = (short) (Math.sin(2 * Math.PI * 440 * index
                    / OpusPcmDecoder.SAMPLE_RATE_HZ) * 4_000);
        }
        byte[] encoded = new byte[1_500];
        int encodedBytes = encoder.encode(input, 0, input.length, encoded, 0, encoded.length);
        byte[] packet = Arrays.copyOf(encoded, encodedBytes);

        OpusPcmDecoder decoder = new OpusPcmDecoder();
        short[] pcm = new short[OpusPcmDecoder.MAX_FRAME_SAMPLES];
        int firstSamples = decoder.decode(packet, pcm);
        double firstRms = rms(pcm, firstSamples);

        decoder.reset();
        int secondSamples = decoder.decode(packet, pcm);
        double secondRms = rms(pcm, secondSamples);
        decoder.close();

        assertTrue(firstSamples > 0);
        assertTrue(firstRms > 500);
        assertTrue(secondSamples > 0);
        assertTrue(secondRms > 500);
    }

    private static double rms(short[] samples, int count) {
        double sum = 0;
        for (int index = 0; index < count; index++) {
            sum += (double) samples[index] * samples[index];
        }
        return count == 0 ? 0 : Math.sqrt(sum / count);
    }
}
