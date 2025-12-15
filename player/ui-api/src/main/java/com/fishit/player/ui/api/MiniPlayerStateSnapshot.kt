package com.fishit.player.ui.api

/**
 * Public mini-player state snapshot.
 *
 * Exposes minimal playback state for the mini-player overlay.
 * This is a stable contract that does not expose internal engine details.
 */
data class MiniPlayerStateSnapshot(
    val title: String?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val progress: Float, // 0f..1f
)
