package com.chris.m3usuite.player.internal.subtitles

import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Default implementation of [SubtitleSelectionPolicy].
 *
 * **Kid Mode Behavior:**
 * - Always returns null (no subtitles) when isKidMode is true (Contract Section 3.1)
 *
 * **Language Priority (Contract Section 6.2):**
 * 1. Match preferred languages in order
 * 2. Track with `default` flag
 * 3. First track if "always show subtitles" enabled
 * 4. Otherwise null
 *
 * **Persistence:**
 * - Stores last selected language per playback type (VOD/SERIES vs LIVE)
 * - Uses DataStore preferences via SettingsStore
 *
 * @param settingsStore DataStore-backed settings repository
 */
class DefaultSubtitleSelectionPolicy(
    private val settingsStore: SettingsStore,
) : SubtitleSelectionPolicy {

    override fun selectInitialTrack(
        availableTracks: List<SubtitleTrack>,
        preferredLanguages: List<String>,
        playbackType: PlaybackType,
        isKidMode: Boolean,
    ): SubtitleTrack? {
        // Kid Mode: No subtitles (Contract Section 3.1)
        if (isKidMode) {
            return null
        }

        // No tracks available
        if (availableTracks.isEmpty()) {
            return null
        }

        // Try to match preferred languages in order
        for (lang in preferredLanguages) {
            val match = availableTracks.firstOrNull { track ->
                track.language?.equals(lang, ignoreCase = true) == true
            }
            if (match != null) {
                return match
            }
        }

        // Fallback to track with default flag
        val defaultTrack = availableTracks.firstOrNull { it.isDefault }
        if (defaultTrack != null) {
            return defaultTrack
        }

        // TODO: Check "always show subtitles" setting
        // For now, return null (no subtitles by default)
        return null
    }

    override suspend fun persistSelection(
        track: SubtitleTrack?,
        playbackType: PlaybackType,
    ) {
        // TODO: Implement persistence to DataStore
        // Separate keys for VOD/SERIES vs LIVE if needed
        // For now, this is a no-op
        // Future: Keys.SUBTITLE_LANG_VOD, Keys.SUBTITLE_LANG_LIVE
    }
}
