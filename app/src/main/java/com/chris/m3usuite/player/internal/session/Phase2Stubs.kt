package com.chris.m3usuite.player.internal.session

import com.chris.m3usuite.player.internal.domain.KidsPlaybackGate
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.ResumeManager

/**
 * Phase 2 preparation stubs.
 * These are NOT used by the runtime player yet.
 * They allow later phases to progressively replace legacy behavior.
 */
object Phase2Stubs {
    fun prepareResume(
        context: PlaybackContext,
        manager: ResumeManager,
    ) {
        // TODO(Phase 2): integrate default resume logic into modular session
    }

    fun prepareKidsGate(
        context: PlaybackContext,
        gate: KidsPlaybackGate,
    ) {
        // TODO(Phase 2): integrate kids gate logic into modular session
    }
}
