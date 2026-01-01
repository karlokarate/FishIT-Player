package com.fishit.player.internal.state

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.PlaybackError
import com.fishit.player.core.playermodel.PlaybackState

/**
 * Complete state of the internal player.
 *
 * This is an immutable snapshot of all player state at a given moment.
 * Uses types from core:player-model for consistency across the player stack.
 */
data class InternalPlayerState(
    /** Current playback context (what is being played). */
    val context: PlaybackContext? = null,
    /** Current playback state. */
    val playbackState: PlaybackState = PlaybackState.IDLE,
    /** Current playback position in milliseconds. */
    val positionMs: Long = 0L,
    /** Total duration in milliseconds (0 if unknown or live). */
    val durationMs: Long = 0L,
    /** Current buffered position in milliseconds. */
    val bufferedPositionMs: Long = 0L,
    /** Whether the player is currently playing (not paused). */
    val isPlaying: Boolean = false,
    /** Playback speed (1.0 = normal). */
    val playbackSpeed: Float = 1.0f,
    /** Current volume (0.0 to 1.0). */
    val volume: Float = 1.0f,
    /** Whether the player is muted. */
    val isMuted: Boolean = false,
    /** Whether controls should be visible. */
    val areControlsVisible: Boolean = true,
    /** Error information if playbackState is ERROR. */
    val error: PlaybackError? = null,
    /** Elapsed session time for kids gate tracking. */
    val sessionElapsedMs: Long = 0L,
) {
    /**
     * Progress as a fraction (0.0 to 1.0).
     */
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /**
     * Buffered progress as a fraction (0.0 to 1.0).
     */
    val bufferedProgress: Float
        get() = if (durationMs > 0) (bufferedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /**
     * Whether the content is live (no fixed duration).
     */
    val isLive: Boolean
        get() = durationMs <= 0L

    /**
     * Remaining time in milliseconds.
     */
    val remainingMs: Long
        get() = if (durationMs > 0) (durationMs - positionMs).coerceAtLeast(0L) else 0L

    companion object {
        val INITIAL = InternalPlayerState()
    }
}
