package com.fishit.player.playback.domain

import com.fishit.player.core.playermodel.PlaybackContext

/**
 * Controls playback access for kids profiles with screen-time limits.
 *
 * The gate tracks elapsed playback time and can block continued viewing
 * when limits are reached.
 *
 * Uses [PlaybackContext] from core:player-model.
 */
interface KidsPlaybackGate {

    /**
     * Result of a gate check.
     */
    sealed class GateResult {
        /** Playback is allowed to continue. */
        data object Allowed : GateResult()

        /** Playback should be paused due to time limit. */
        data class Blocked(val reason: String, val remainingMinutes: Int = 0) : GateResult()

        /** A warning that time is almost up. */
        data class Warning(val remainingMinutes: Int) : GateResult()
    }

    /**
     * Called periodically during playback to check if the gate allows continuation.
     *
     * @param context Current playback context.
     * @param elapsedMs Time elapsed in this session (milliseconds).
     * @return Gate result indicating whether playback should continue.
     */
    suspend fun tick(context: PlaybackContext, elapsedMs: Long): GateResult

    /**
     * Resets the gate (e.g., when switching profiles or on new day).
     */
    suspend fun reset()

    /**
     * Whether the gate is currently active (kids profile with limits).
     */
    suspend fun isActive(): Boolean
}
