package com.chris.m3usuite.player.internal.subtitles

import com.chris.m3usuite.player.internal.domain.PlaybackType

/**
 * Represents a subtitle track available in the media.
 *
 * @property groupIndex Track group index in ExoPlayer
 * @property trackIndex Track index within the group
 * @property language ISO 639 language code (e.g., "en", "de", "fr"), or null if unknown
 * @property label Human-readable label (e.g., "English", "German (SDH)")
 * @property isDefault Whether this track is marked as default by the media
 */
data class SubtitleTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String,
    val isDefault: Boolean,
)

/**
 * Policy for selecting subtitle tracks based on user preferences and media metadata.
 *
 * Contract Reference: `INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` Section 6
 *
 * **Selection Rules (Contract Section 6.2):**
 * 1. If Kid Mode → always "no subtitles" (returns null)
 * 2. Preferred order:
 *    - System language
 *    - Primary profile language
 *    - Secondary profile language
 *    - Track with `default` flag
 *    - Fallback: first usable track if "Always show subtitles" is enabled
 *    - Otherwise: no subtitles (returns null)
 * 3. User manual selection → becomes new preference
 *
 * **Persistence (Contract Section 6.3):**
 * - Per-profile subtitle language preference
 * - Optional: separate preferences for VOD vs LIVE
 */
interface SubtitleSelectionPolicy {
    /**
     * Selects the initial subtitle track when playback starts.
     *
     * @param availableTracks List of available subtitle tracks from Media3
     * @param preferredLanguages Ordered list of preferred language codes (system → profile primary → profile secondary)
     * @param playbackType Type of content being played (VOD, SERIES, LIVE)
     * @param isKidMode Whether the current profile is a kid profile
     * @return Selected track, or null if no subtitles should be shown
     */
    fun selectInitialTrack(
        availableTracks: List<SubtitleTrack>,
        preferredLanguages: List<String>,
        playbackType: PlaybackType,
        isKidMode: Boolean,
    ): SubtitleTrack?

    /**
     * Persists user's manual subtitle selection as a preference.
     *
     * @param track The track selected by the user, or null to disable subtitles
     * @param playbackType Type of content (allows separate VOD/LIVE preferences)
     */
    suspend fun persistSelection(
        track: SubtitleTrack?,
        playbackType: PlaybackType,
    )
}
