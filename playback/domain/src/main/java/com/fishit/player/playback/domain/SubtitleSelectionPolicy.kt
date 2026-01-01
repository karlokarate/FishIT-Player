package com.fishit.player.playback.domain

import com.fishit.player.core.playermodel.PlaybackContext

/**
 * Represents a selectable subtitle/audio track.
 */
data class MediaTrack(
    val id: String,
    val language: String?,
    val label: String?,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
)

/**
 * Policy for automatically selecting subtitle and audio tracks.
 *
 * Implementations define rules based on user preferences, profile settings,
 * and content metadata.
 */
interface SubtitleSelectionPolicy {
    /**
     * Selects the preferred subtitle track from available options.
     *
     * @param context Current playback context.
     * @param availableTracks List of available subtitle tracks.
     * @return The selected track, or null for no subtitles.
     */
    suspend fun selectSubtitleTrack(
        context: PlaybackContext,
        availableTracks: List<MediaTrack>,
    ): MediaTrack?

    /**
     * Selects the preferred audio track from available options.
     *
     * @param context Current playback context.
     * @param availableTracks List of available audio tracks.
     * @return The selected track.
     */
    suspend fun selectAudioTrack(
        context: PlaybackContext,
        availableTracks: List<MediaTrack>,
    ): MediaTrack?

    /**
     * Whether subtitles should be disabled entirely (e.g., for kids profiles).
     */
    suspend fun shouldDisableSubtitles(context: PlaybackContext): Boolean
}
