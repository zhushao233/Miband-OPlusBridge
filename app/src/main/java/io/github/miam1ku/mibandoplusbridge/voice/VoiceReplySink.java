// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

/** Delivers one encoded AIVS instruction through the existing live SPP session. */
@FunctionalInterface
public interface VoiceReplySink {
    boolean send(byte[] instruction);
}
