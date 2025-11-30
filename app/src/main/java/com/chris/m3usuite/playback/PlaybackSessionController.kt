package com.chris.m3usuite.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for controlling and observing playback state.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – Unified PlaybackSession
 * PHASE 8 – SessionLifecycleState added
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This interface defines the contract for the unified playback session that:
 * - Owns the shared ExoPlayer instance across the entire app
 * - Provides reactive state flows for UI observation
 * - Offers command methods for playback control
 * - Exposes lifecycle state for Activity/Fragment coordination (Phase 8)
 *
 * **Key Principles:**
 * - Single global session: One shared ExoPlayer per process
 * - No re-init on transitions: Player survives Full↔MiniPlayer navigation
 * - Thread-safe state updates via StateFlows
 * - Lifecycle state machine for warm resume support (Phase 8)
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 7
 * - INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 4
 *
 * @see PlaybackSession for the singleton implementation
 * @see SessionLifecycleState for lifecycle state enum
 */
interface PlaybackSessionController {
    // ══════════════════════════════════════════════════════════════════
    // STATE FLOWS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Current playback position in milliseconds.
     * Updates during playback (~1s intervals typically).
     * Value is 0 when no content is loaded.
     */
    val positionMs: StateFlow<Long>

    /**
     * Total duration in milliseconds.
     * Value is 0 when duration is unknown or content is live.
     */
    val durationMs: StateFlow<Long>

    /**
     * Whether playback is currently active (playing, not paused/stopped).
     */
    val isPlaying: StateFlow<Boolean>

    /**
     * Whether the player is currently buffering.
     */
    val buffering: StateFlow<Boolean>

    /**
     * Current playback error (raw ExoPlayer exception), if any.
     * Null when there is no error.
     *
     * **Prefer using [playbackError]** for structured error information.
     */
    val error: StateFlow<PlaybackException?>

    /**
     * Current playback error as a structured [PlaybackError], if any.
     * Null when there is no error.
     *
     * **Phase 8 – Task 6 Addition:**
     * This provides typed error information for:
     * - UI differentiation (Network vs Http vs Source vs Decoder vs Unknown)
     * - Kids-friendly generic messages
     * - Rich logging metadata (via AppLog)
     *
     * @see PlaybackError for error types
     */
    val playbackError: StateFlow<PlaybackError?>

    /**
     * Current video size (width x height).
     * May be null if video size is unknown or no video track is present.
     */
    val videoSize: StateFlow<VideoSize?>

    /**
     * Current ExoPlayer playback state.
     * One of: STATE_IDLE, STATE_BUFFERING, STATE_READY, STATE_ENDED
     */
    val playbackState: StateFlow<Int>

    /**
     * Whether a playback session is currently active.
     * True when a player is acquired and content is loaded.
     */
    val isSessionActive: StateFlow<Boolean>

    /**
     * Current lifecycle state of the playback session.
     * Tracks the session through IDLE → PREPARED → PLAYING → PAUSED → STOPPED → RELEASED states.
     *
     * **Phase 8 Addition:**
     * This state flow enables:
     * - Warm resume: Rebind UI without recreating ExoPlayer
     * - Lifecycle coordination: Proper handling of Activity lifecycle events
     * - Background playback: Track when playback continues in background
     *
     * @see SessionLifecycleState for state definitions
     */
    val lifecycleState: StateFlow<SessionLifecycleState>

    // ══════════════════════════════════════════════════════════════════
    // PLAYBACK COMMANDS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Start or resume playback.
     */
    fun play()

    /**
     * Pause playback.
     */
    fun pause()

    /**
     * Toggle between play and pause states.
     */
    fun togglePlayPause()

    /**
     * Seek to an absolute position.
     * @param positionMs Target position in milliseconds (will be coerced to valid range)
     */
    fun seekTo(positionMs: Long)

    /**
     * Seek relative to current position.
     * @param deltaMs Offset in milliseconds (positive = forward, negative = backward)
     */
    fun seekBy(deltaMs: Long)

    /**
     * Set playback speed.
     * @param speed Playback speed multiplier (1.0 = normal, 2.0 = 2x, etc.)
     */
    fun setSpeed(speed: Float)

    /**
     * Enable or disable trickplay mode at specified speed.
     * @param speed Trickplay speed (negative for rewind, positive for fast-forward)
     */
    fun enableTrickplay(speed: Float)

    /**
     * Stop playback and clear the current media item.
     * The player instance is retained for future use.
     */
    fun stop()

    /**
     * Release the player instance completely.
     * Should only be called when:
     * - The app is closing
     * - Playback is fully stopped by user
     * - Errors require player recreation
     */
    fun release()
}
