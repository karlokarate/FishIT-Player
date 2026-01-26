package com.fishit.player.internal.state

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.PlaybackError
import com.fishit.player.core.playermodel.PlaybackState
import com.fishit.player.playback.domain.EpgEntry
import com.fishit.player.playback.domain.LiveChannelInfo

/**
 * Complete state of the internal player.
 *
 * This is an immutable snapshot of all player state at a given moment.
 * Uses types from core:player-model for consistency across the player stack.
 *
 * ## Live TV Support
 *
 * When playing live content ([isLive] = true):
 * - [liveChannelInfo] contains current channel with EPG data
 * - [epgOverlayVisible] controls EPG info overlay visibility
 * - Duration is typically 0 or unknown
 * - Seeking may be restricted
 *
 * @see LiveChannelInfo for EPG data structure
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
    // ────────────────────────────────────────────────────────────────────────
    // Live TV Fields
    // ────────────────────────────────────────────────────────────────────────
    /** Current live channel info with EPG (null if not live). */
    val liveChannelInfo: LiveChannelInfo? = null,
    /** Whether the EPG overlay is currently visible. */
    val epgOverlayVisible: Boolean = false,
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
     * Whether the content is live (explicitly marked or no fixed duration).
     *
     * Returns true if:
     * - [PlaybackContext.isLive] is true (explicitly marked as live), OR
     * - Duration is 0 or unknown (typical for live streams)
     */
    val isLive: Boolean
        get() = context?.isLive == true || durationMs <= 0L

    /**
     * Whether EPG overlay should be rendered.
     *
     * Combines [isLive] check with [epgOverlayVisible] flag.
     */
    val shouldShowEpgOverlay: Boolean
        get() = isLive && epgOverlayVisible && liveChannelInfo != null

    /**
     * Current program from EPG (convenience accessor).
     */
    val currentProgram: EpgEntry?
        get() = liveChannelInfo?.currentProgram

    /**
     * Next program from EPG (convenience accessor).
     */
    val nextProgram: EpgEntry?
        get() = liveChannelInfo?.nextProgram

    /**
     * Remaining time in milliseconds.
     */
    val remainingMs: Long
        get() = if (durationMs > 0) (durationMs - positionMs).coerceAtLeast(0L) else 0L

    companion object {
        val INITIAL = InternalPlayerState()
    }
}
