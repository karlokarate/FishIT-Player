package com.chris.m3usuite.player.internal.session

import com.chris.m3usuite.player.internal.domain.KidsGateState
import com.chris.m3usuite.player.internal.domain.KidsPlaybackGate
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.domain.ResumeManager

/**
 * Phase 2 integration hooks.
 *
 * These functions are used to mirror existing legacy behavior in a modular way.
 * They must NOT change runtime behavior while the legacy InternalPlayerScreen
 * remains active.
 *
 * ────────────────────────────────────────────────────────────────────────────
 * IMPORTANT: This file is NOT wired into the runtime player flow.
 * InternalPlayerEntry still delegates to the legacy InternalPlayerScreen.
 * ────────────────────────────────────────────────────────────────────────────
 *
 * Legacy Behavior Mapping (for future integration):
 *
 * Resume (legacy InternalPlayerScreen lines 572-608, 692-722, 798-806):
 * - On start: Load resume position, seek if > 10s
 * - Periodic (~3s): Save resume or clear if < 10s remaining
 * - On STATE_ENDED: Clear resume marker
 * - On ON_DESTROY: Final save/clear (handled by lifecycle, not this module)
 *
 * Kids Gate (legacy InternalPlayerScreen lines 547-569, 725-744, 2282-2290):
 * - On start: Check if kid profile, get remaining screen time, block if 0
 * - Periodic (~60s): Tick usage, block if limit reached
 * - UI: Show AlertDialog when blocked
 */
object Phase2Integration {

    /**
     * Load the initial resume position for a playback session.
     *
     * Mirrors legacy behavior at InternalPlayerScreen L572-608:
     * - VOD: Load from ResumeRepository.recentVod
     * - SERIES: Load from ResumeRepository.recentEpisodes
     * - LIVE: No resume (returns null)
     *
     * @return Resume position in milliseconds, or null if no resume available or < 10s
     */
    suspend fun loadInitialResumePosition(
        playbackContext: PlaybackContext,
        resumeManager: ResumeManager,
    ): Long? {
        // TODO(Phase 2): This function mirrors legacy resume loading behavior.
        // It should only be called from experimental code paths until
        // the modular session replaces the legacy InternalPlayerScreen.
        return resumeManager.loadResumePositionMs(playbackContext)
    }

    /**
     * Handle periodic tick for resume saving and kids gate updates.
     *
     * This function should be called approximately every 3 seconds during playback.
     *
     * Mirrors legacy behavior at:
     * - Resume: InternalPlayerScreen L692-722 (save/clear every ~3s)
     * - Kids: InternalPlayerScreen L725-744 (tick every ~60s)
     *
     * @param playbackContext The current playback context
     * @param positionMs Current playback position in milliseconds
     * @param durationMs Total media duration in milliseconds
     * @param resumeManager Resume manager instance
     * @param kidsGate Kids playback gate instance
     * @param currentKidsState Current kids gate state (for tick accumulation)
     * @param tickAccumSecs Accumulated seconds since last kids tick (caller maintains)
     * @return Updated KidsGateState if kid profile is active, null otherwise
     */
    suspend fun onPlaybackTick(
        playbackContext: PlaybackContext,
        positionMs: Long,
        durationMs: Long,
        resumeManager: ResumeManager,
        kidsGate: KidsPlaybackGate,
        currentKidsState: KidsGateState?,
        tickAccumSecs: Int,
    ): KidsGateState? {
        // TODO(Phase 2): Mirror legacy periodic tick:
        // - Save resume position periodically (VOD/SERIES)
        // - Apply kids/screentime gating every ~60s
        // This should be wired into the modular session, not the legacy screen.

        // Resume handling (every tick for VOD/Series)
        if (playbackContext.type != PlaybackType.LIVE) {
            resumeManager.handlePeriodicTick(playbackContext, positionMs, durationMs)
        }

        // Kids gate handling (every 60s when kid profile active)
        val kidsState = currentKidsState
        if (kidsState != null && kidsState.kidActive && tickAccumSecs >= 60) {
            return kidsGate.onPlaybackTick(kidsState, tickAccumSecs)
        }

        return kidsState
    }

    /**
     * Handle playback ended event.
     *
     * Mirrors legacy behavior at InternalPlayerScreen L798-806:
     * - Clear resume marker on STATE_ENDED for VOD/Series
     *
     * @param playbackContext The current playback context
     * @param resumeManager Resume manager instance
     */
    suspend fun onPlaybackEnded(
        playbackContext: PlaybackContext,
        resumeManager: ResumeManager,
    ) {
        // TODO(Phase 2): Mirror legacy STATE_ENDED handling.
        // Clear resume marker when playback completes.
        resumeManager.handleEnded(playbackContext)
    }

    /**
     * Evaluate kids gate state before starting playback.
     *
     * Mirrors legacy behavior at InternalPlayerScreen L547-569:
     * - Check if current profile is a kid profile
     * - Check remaining screen time
     * - Block playback if limit reached
     *
     * @param kidsGate Kids playback gate instance
     * @return KidsGateState with kidActive, kidBlocked, and kidProfileId
     */
    suspend fun evaluateKidsGateOnStart(
        kidsGate: KidsPlaybackGate,
    ): KidsGateState {
        // TODO(Phase 2): Mirror legacy kid gate start evaluation.
        // This determines if playback should be blocked before it begins.
        return kidsGate.evaluateStart()
    }
}
