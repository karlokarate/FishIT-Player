package com.fishit.player.core.playermodel

/**
 * Represents the internal player's playback state.
 *
 * These states map to ExoPlayer's Player.STATE_* constants
 * but are decoupled from the Media3 library.
 */
enum class PlaybackState {
    /** Initial state before playback starts. */
    IDLE,

    /** Loading/buffering content. */
    BUFFERING,

    /** Actively playing content. */
    PLAYING,

    /** Playback is paused. */
    PAUSED,

    /** Playback ended (content finished). */
    ENDED,

    /** An error occurred. */
    ERROR,
}
