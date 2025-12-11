package com.fishit.player.core.playermodel

/**
 * Represents the current state of audio track selection.
 *
 * This immutable data class is exposed via StateFlow from the player session to allow UI and domain
 * layers to observe audio track changes.
 *
 * @property availableTracks All audio tracks discovered in the current media.
 * @property selectedTrackId ID of the currently selected audio track.
 * @property preferredLanguage User's preferred audio language (BCP-47 code).
 * @property preferSurroundSound Whether user prefers surround sound over stereo when available.
 */
data class AudioSelectionState(
        val availableTracks: List<AudioTrack> = emptyList(),
        val selectedTrackId: AudioTrackId = AudioTrackId.DEFAULT,
        val preferredLanguage: String? = null,
        val preferSurroundSound: Boolean = true
) {
    /**
     * Returns the currently selected audio track, or null if none selected or the selected ID
     * doesn't match any available track.
     */
    val selectedTrack: AudioTrack?
        get() = availableTracks.find { it.id == selectedTrackId }

    /** Returns whether audio tracks are available. */
    val hasMultipleTracks: Boolean
        get() = availableTracks.size > 1

    /** Returns the number of available audio tracks. */
    val trackCount: Int
        get() = availableTracks.size

    /** Returns audio tracks for a specific language. */
    fun tracksForLanguage(language: String): List<AudioTrack> =
            availableTracks.filter { it.language?.startsWith(language, ignoreCase = true) == true }

    /**
     * Returns the best track for user preferences.
     *
     * Selection priority:
     * 1. Preferred language + surround (if preferSurroundSound)
     * 2. Preferred language + stereo
     * 3. Default track with surround (if preferSurroundSound)
     * 4. Default track
     * 5. First surround track (if preferSurroundSound)
     * 6. First available track
     */
    fun selectBestTrack(): AudioTrack? {
        if (availableTracks.isEmpty()) return null

        // Try preferred language first
        preferredLanguage?.let { lang ->
            val langTracks = tracksForLanguage(lang)
            if (langTracks.isNotEmpty()) {
                // Prefer surround if enabled
                if (preferSurroundSound) {
                    langTracks.firstOrNull { it.channelLayout.isSurround }?.let {
                        return it
                    }
                }
                // Fall back to any track in preferred language
                return langTracks.first()
            }
        }

        // Try default track
        val defaultTrack = availableTracks.find { it.isDefault }
        if (defaultTrack != null) {
            // If prefer surround and default is not surround, try to find surround alternative
            if (preferSurroundSound && !defaultTrack.channelLayout.isSurround) {
                val sameLangSurround =
                        availableTracks.find {
                            it.language == defaultTrack.language && it.channelLayout.isSurround
                        }
                if (sameLangSurround != null) return sameLangSurround
            }
            return defaultTrack
        }

        // Prefer surround if enabled
        if (preferSurroundSound) {
            availableTracks.firstOrNull { it.channelLayout.isSurround }?.let {
                return it
            }
        }

        // Fall back to first track
        return availableTracks.first()
    }

    companion object {
        /** Empty state with no tracks. */
        val EMPTY = AudioSelectionState()
    }
}

/** Extension property to check if channel layout is surround sound. */
private val AudioChannelLayout.isSurround: Boolean
    get() =
            when (this) {
                AudioChannelLayout.SURROUND_5_1,
                AudioChannelLayout.SURROUND_7_1,
                AudioChannelLayout.ATMOS -> true
                else -> false
            }
