package com.chris.m3usuite.playback

/**
 * Lifecycle state machine for PlaybackSession.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – SessionLifecycleState
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This enum defines the lifecycle states for the unified PlaybackSession.
 * The state machine ensures ExoPlayer is managed correctly across:
 * - Activity lifecycle events (foreground/background)
 * - Configuration changes (rotation)
 * - Navigation transitions (Full Player ↔ MiniPlayer)
 *
 * **State Transitions:**
 * ```
 * IDLE → PREPARED: media loaded
 * PREPARED → PLAYING: play() called when ready
 * PLAYING → PAUSED: pause() called or app paused
 * PLAYING/PAUSED → BACKGROUND: app backgrounded with active playback
 * BACKGROUND → PLAYING/PAUSED: app foregrounded
 * Any → STOPPED: stop() called, ExoPlayer retained
 * Any → RELEASED: release() called, ExoPlayer released
 * ```
 *
 * **Key Principles:**
 * - IDLE: Initial state, no media loaded
 * - PREPARED: Media loaded, ready to play (ExoPlayer prepared)
 * - PLAYING: Actively playing media
 * - PAUSED: Paused by user or system
 * - BACKGROUND: App backgrounded but playback may continue (e.g., audio)
 * - STOPPED: Playback stopped, ExoPlayer retained for potential restart
 * - RELEASED: ExoPlayer released, session cannot be reused without re-acquisition
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 4.2
 */
enum class SessionLifecycleState {
    /**
     * Initial state. No media is loaded.
     * ExoPlayer may or may not exist yet.
     */
    IDLE,

    /**
     * Media has been loaded and ExoPlayer is prepared.
     * Ready to begin playback when play() is called.
     */
    PREPARED,

    /**
     * Actively playing media.
     * ExoPlayer is running and rendering.
     */
    PLAYING,

    /**
     * Playback is paused but retained.
     * Can resume immediately with play().
     */
    PAUSED,

    /**
     * App is in background but playback may continue.
     * Used when audio playback continues without visible UI.
     * On return to foreground, transitions back to PLAYING or PAUSED.
     */
    BACKGROUND,

    /**
     * Playback has been stopped.
     * ExoPlayer is retained and can be restarted with new media.
     * Call release() to fully dispose of the player.
     */
    STOPPED,

    /**
     * ExoPlayer has been released.
     * Session is no longer usable and must be re-acquired.
     * This is the final state in the lifecycle.
     */
    RELEASED,
}
