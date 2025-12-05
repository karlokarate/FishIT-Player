package com.fishit.player.playback.domain.defaults

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.playback.domain.MediaTrack
import com.fishit.player.playback.domain.SubtitleSelectionPolicy

/**
 * Default SubtitleSelectionPolicy with basic language preference.
 *
 * This is a stub implementation for Phase 1.
 * Full profile-aware selection will be added in Phase 6.
 */
class DefaultSubtitleSelectionPolicy : SubtitleSelectionPolicy {

    private val preferredLanguages = listOf("de", "deu", "ger", "en", "eng")

    override suspend fun selectSubtitleTrack(
        context: PlaybackContext,
        availableTracks: List<MediaTrack>
    ): MediaTrack? {
        // Try to find a track matching preferred languages
        for (lang in preferredLanguages) {
            val track = availableTracks.find {
                it.language?.lowercase()?.startsWith(lang) == true
            }
            if (track != null) return track
        }
        // Fall back to default or first track
        return availableTracks.find { it.isDefault } ?: availableTracks.firstOrNull()
    }

    override suspend fun selectAudioTrack(
        context: PlaybackContext,
        availableTracks: List<MediaTrack>
    ): MediaTrack? {
        // Try to find German audio first
        for (lang in preferredLanguages) {
            val track = availableTracks.find {
                it.language?.lowercase()?.startsWith(lang) == true
            }
            if (track != null) return track
        }
        return availableTracks.find { it.isDefault } ?: availableTracks.firstOrNull()
    }

    override suspend fun shouldDisableSubtitles(context: PlaybackContext): Boolean {
        // Don't disable subtitles in Phase 1
        return false
    }
}
