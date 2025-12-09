package com.fishit.player.playback.domain.defaults

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.playback.domain.KidsPlaybackGate

/**
 * Default KidsPlaybackGate that always allows playback.
 *
 * This is a stub implementation for Phase 1.
 * Real screen-time tracking will be added in Phase 9.
 */
class DefaultKidsPlaybackGate : KidsPlaybackGate {

    override suspend fun tick(
        context: PlaybackContext,
        elapsedMs: Long
    ): KidsPlaybackGate.GateResult {
        // Always allow in Phase 1
        return KidsPlaybackGate.GateResult.Allowed
    }

    override suspend fun reset() {
        // No-op in Phase 1
    }

    override suspend fun isActive(): Boolean {
        // Not active in Phase 1
        return false
    }
}
