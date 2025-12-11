package com.fishit.player.core.playermodel

/**
 * Represents the current subtitle selection state in the player.
 *
 * This is an immutable snapshot of the subtitle subsystem state, emitted as a StateFlow from the
 * player session.
 *
 * @property availableTracks List of all available subtitle tracks.
 * @property selectedTrackId Currently selected track ID, or [SubtitleTrackId.OFF] if disabled.
 * @property isEnabled Whether subtitles are currently enabled (not OFF).
 * @property isLoading Whether subtitle tracks are still being discovered.
 * @property error Optional error message if subtitle loading failed.
 */
data class SubtitleSelectionState(
        val availableTracks: List<SubtitleTrack> = emptyList(),
        val selectedTrackId: SubtitleTrackId = SubtitleTrackId.OFF,
        val isEnabled: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null
) {
    /** The currently selected track, or null if subtitles are disabled. */
    val selectedTrack: SubtitleTrack?
        get() =
                if (selectedTrackId == SubtitleTrackId.OFF) null
                else availableTracks.find { it.id == selectedTrackId }

    /** Whether any subtitle tracks are available. */
    val hasAvailableTracks: Boolean
        get() = availableTracks.isNotEmpty()

    /** Number of available tracks. */
    val trackCount: Int
        get() = availableTracks.size

    companion object {
        /** Initial state before track discovery. */
        val INITIAL = SubtitleSelectionState(isLoading = true)

        /** State when no tracks are available. */
        val EMPTY = SubtitleSelectionState(isLoading = false)

        /** State when subtitles are explicitly disabled (e.g., Kids Mode). */
        val DISABLED = SubtitleSelectionState(isLoading = false, isEnabled = false)
    }
}
