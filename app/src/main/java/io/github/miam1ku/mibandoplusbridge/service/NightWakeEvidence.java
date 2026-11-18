// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.service;

import java.util.concurrent.atomic.AtomicReference;

/** Wake evidence and stored sleep intervals belong to the current sleep episode. */
final class NightWakeEvidence {
    private record Snapshot(boolean observed, long episodeStartedAtMs) {}

    private final AtomicReference<Snapshot> state =
            new AtomicReference<>(new Snapshot(false, 0L));

    void reset() {
        state.set(new Snapshot(false, 0L));
    }

    void beginEpisode(long nowMs) {
        state.set(new Snapshot(false, nowMs));
    }

    void observe() {
        state.updateAndGet(current -> new Snapshot(true, current.episodeStartedAtMs()));
    }

    boolean observed() {
        return state.get().observed();
    }

    boolean acceptsClosedInterval(long endMs) {
        Snapshot snapshot = state.get();
        return snapshot.episodeStartedAtMs() > 0 && endMs >= snapshot.episodeStartedAtMs();
    }
}
