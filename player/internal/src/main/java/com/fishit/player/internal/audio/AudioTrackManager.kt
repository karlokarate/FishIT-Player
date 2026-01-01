package com.fishit.player.internal.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.fishit.player.core.playermodel.AudioChannelLayout
import com.fishit.player.core.playermodel.AudioCodecType
import com.fishit.player.core.playermodel.AudioSelectionState
import com.fishit.player.core.playermodel.AudioSourceType
import com.fishit.player.core.playermodel.AudioTrack
import com.fishit.player.core.playermodel.AudioTrackId
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages audio track discovery, selection, and state for the internal player.
 *
 * This component:
 * - Listens to Media3 track changes and maps them to [AudioTrack] models
 * - Exposes current audio state via [StateFlow]
 * - Provides APIs to select audio tracks
 * - Implements deterministic default selection policy
 * - Supports user preferences (language, surround sound)
 *
 * **Architecture:**
 * - Uses types from core:player-model only
 * - No pipeline or data layer imports
 * - Logging via UnifiedLog
 */
class AudioTrackManager {
    companion object {
        private const val TAG = "AudioTrackManager"
    }

    private var player: Player? = null
    private var preferredLanguage: String? = null
    private var preferSurroundSound: Boolean = true

    private val _state = MutableStateFlow(AudioSelectionState.EMPTY)
    val state: StateFlow<AudioSelectionState> = _state.asStateFlow()

    // Mapping from our AudioTrackId to Media3 TrackGroup for selection
    private val trackGroupMap = mutableMapOf<AudioTrackId, Pair<TrackGroup, Int>>()

    /**
     * Attaches the manager to a player instance.
     *
     * @param player The Media3 player instance.
     * @param preferredLanguage User's preferred audio language (BCP-47 code).
     * @param preferSurroundSound Whether to prefer surround sound over stereo.
     */
    fun attach(
        player: Player,
        preferredLanguage: String? = null,
        preferSurroundSound: Boolean = true,
    ) {
        this.player = player
        this.preferredLanguage = preferredLanguage
        this.preferSurroundSound = preferSurroundSound

        _state.value =
            AudioSelectionState(
                preferredLanguage = preferredLanguage,
                preferSurroundSound = preferSurroundSound,
            )

        // Listen for track changes
        player.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    discoverTracks(tracks)
                }
            },
        )

        // Initial track discovery if tracks already available
        discoverTracks(player.currentTracks)

        UnifiedLog.d(
            TAG,
            "Attached to player (preferredLang=$preferredLanguage, surround=$preferSurroundSound)",
        )
    }

    /** Detaches the manager from the current player. */
    fun detach() {
        player = null
        trackGroupMap.clear()
        _state.value = AudioSelectionState.EMPTY
        UnifiedLog.d(TAG, "Detached from player")
    }

    /**
     * Updates user preferences for audio track selection.
     *
     * @param preferredLanguage New preferred language.
     * @param preferSurroundSound New surround sound preference.
     */
    fun updatePreferences(
        preferredLanguage: String? = this.preferredLanguage,
        preferSurroundSound: Boolean = this.preferSurroundSound,
    ) {
        this.preferredLanguage = preferredLanguage
        this.preferSurroundSound = preferSurroundSound

        _state.update {
            it.copy(
                preferredLanguage = preferredLanguage,
                preferSurroundSound = preferSurroundSound,
            )
        }

        UnifiedLog.d(
            TAG,
            "Preferences updated: lang=$preferredLanguage, surround=$preferSurroundSound",
        )
    }

    /**
     * Selects an audio track by ID.
     *
     * @param trackId The track to select.
     * @return true if the track was successfully selected.
     */
    fun selectTrack(trackId: AudioTrackId): Boolean {
        val currentPlayer =
            player
                ?: run {
                    UnifiedLog.w(TAG, "Cannot select track: no player attached")
                    return false
                }

        val trackData = trackGroupMap[trackId]
        if (trackData == null) {
            UnifiedLog.w(TAG, "Track not found: ${trackId.value}")
            return false
        }

        val (trackGroup, trackIndex) = trackData

        try {
            val override = TrackSelectionOverride(trackGroup, listOf(trackIndex))
            val newParams =
                currentPlayer
                    .trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .addOverride(override)
                    .build()

            currentPlayer.trackSelectionParameters = newParams

            _state.update { it.copy(selectedTrackId = trackId) }

            val track = _state.value.selectedTrack
            UnifiedLog.i(TAG, "Audio track selected: ${track?.label ?: trackId.value}")
            return true
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Failed to select audio track: ${trackId.value}", e)
            return false
        }
    }

    /**
     * Selects the best audio track based on preferences and available tracks.
     *
     * Uses the selection policy from [AudioSelectionState.selectBestTrack].
     */
    fun selectBestTrack() {
        val currentState = _state.value
        if (currentState.availableTracks.isEmpty()) {
            UnifiedLog.d(TAG, "No tracks available for selection")
            return
        }

        val bestTrack = currentState.selectBestTrack()
        if (bestTrack != null) {
            UnifiedLog.d(TAG, "Selecting best track: ${bestTrack.label}")
            selectTrack(bestTrack.id)
        } else {
            UnifiedLog.d(TAG, "No suitable track found")
        }
    }

    /**
     * Selects a track by language code.
     *
     * If multiple tracks exist for the language, prefers surround sound based on user preferences.
     *
     * @param languageCode BCP-47 language code (e.g., "en", "de").
     * @return true if a matching track was found and selected.
     */
    fun selectTrackByLanguage(languageCode: String): Boolean {
        val langTracks = _state.value.tracksForLanguage(languageCode)

        if (langTracks.isEmpty()) {
            UnifiedLog.d(TAG, "No track found for language: $languageCode")
            return false
        }

        // Prefer surround if enabled
        val track =
            if (preferSurroundSound) {
                langTracks.firstOrNull { it.channelLayout.isSurround } ?: langTracks.first()
            } else {
                langTracks.first()
            }

        return selectTrack(track.id)
    }

    /**
     * Selects the next audio track in the list (for cycling through tracks).
     *
     * @return The newly selected track, or null if cycling failed.
     */
    fun selectNextTrack(): AudioTrack? {
        val currentState = _state.value
        if (currentState.availableTracks.size <= 1) {
            UnifiedLog.d(TAG, "Not enough tracks to cycle")
            return null
        }

        val currentIndex =
            currentState.availableTracks.indexOfFirst { it.id == currentState.selectedTrackId }

        val nextIndex =
            if (currentIndex < 0) {
                0
            } else {
                (currentIndex + 1) % currentState.availableTracks.size
            }

        val nextTrack = currentState.availableTracks[nextIndex]
        if (selectTrack(nextTrack.id)) {
            return nextTrack
        }
        return null
    }

    // ========== Private Methods ==========

    private fun discoverTracks(tracks: Tracks) {
        trackGroupMap.clear()
        val audioTracks = mutableListOf<AudioTrack>()

        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue

            val trackGroup = group.mediaTrackGroup

            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue

                val format = trackGroup.getFormat(trackIndex)
                val track = mapFormatToAudioTrack(trackGroup, trackIndex, format)

                audioTracks.add(track)
                trackGroupMap[track.id] = Pair(trackGroup, trackIndex)
            }
        }

        // Determine currently selected track
        val selectedTrackId = findCurrentlySelectedTrack(tracks, audioTracks)

        _state.update { it.copy(availableTracks = audioTracks, selectedTrackId = selectedTrackId) }

        UnifiedLog.i(TAG, "Discovered ${audioTracks.size} audio tracks")

        // If no track is selected yet, select best track
        if (audioTracks.isNotEmpty() && selectedTrackId == AudioTrackId.DEFAULT) {
            selectBestTrack()
        }
    }

    private fun findCurrentlySelectedTrack(
        tracks: Tracks,
        audioTracks: List<AudioTrack>,
    ): AudioTrackId {
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue

            for (trackIndex in 0 until group.length) {
                if (group.isTrackSelected(trackIndex)) {
                    // Find matching AudioTrack by group/index
                    val trackGroup = group.mediaTrackGroup
                    val groupHash = trackGroup.hashCode()
                    val candidateId = AudioTrackId("$groupHash:$trackIndex")

                    val matchingTrack = audioTracks.find { it.id == candidateId }
                    if (matchingTrack != null) {
                        return matchingTrack.id
                    }
                }
            }
        }
        return AudioTrackId.DEFAULT
    }

    private fun mapFormatToAudioTrack(
        trackGroup: TrackGroup,
        trackIndex: Int,
        format: Format,
    ): AudioTrack {
        val groupHash = trackGroup.hashCode()
        val channelCount = format.channelCount.takeIf { it > 0 } ?: 2

        return AudioTrack(
            id = AudioTrackId("$groupHash:$trackIndex"),
            language = format.language,
            label = format.label ?: buildLabel(format),
            channelLayout = AudioChannelLayout.fromChannelCount(channelCount),
            codecType = AudioCodecType.fromMimeType(format.sampleMimeType),
            isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
            isDescriptive = format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0,
            sourceType = determineSourceType(format),
            bitrate = format.bitrate.takeIf { it > 0 },
            sampleRate = format.sampleRate.takeIf { it > 0 },
            mimeType = format.sampleMimeType,
        )
    }

    private fun buildLabel(format: Format): String {
        val parts = mutableListOf<String>()

        // Language
        format.language?.let { lang -> parts.add(getLanguageDisplayName(lang)) }
            ?: parts.add("Audio ${trackGroupMap.size + 1}")

        // Channel layout
        val channelLayout = AudioChannelLayout.fromChannelCount(format.channelCount)
        when (channelLayout) {
            AudioChannelLayout.MONO -> parts.add("Mono")
            AudioChannelLayout.STEREO -> parts.add("Stereo")
            AudioChannelLayout.SURROUND_5_1 -> parts.add("5.1")
            AudioChannelLayout.SURROUND_7_1 -> parts.add("7.1")
            AudioChannelLayout.ATMOS -> parts.add("Atmos")
            AudioChannelLayout.UNKNOWN -> {
                // skip
            }
        }

        // Audio description indicator
        if (format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) {
            parts.add("(AD)")
        }

        return parts.joinToString(" ")
    }

    private fun determineSourceType(format: Format): AudioSourceType =
        when {
            format.containerMimeType?.contains("mpegurl", ignoreCase = true) == true ->
                AudioSourceType.MANIFEST
            format.containerMimeType?.contains("dash", ignoreCase = true) == true ->
                AudioSourceType.MANIFEST
            else -> AudioSourceType.EMBEDDED
        }

    private fun getLanguageDisplayName(languageCode: String): String =
        when (languageCode.lowercase().take(2)) {
            "en" -> "English"
            "de" -> "Deutsch"
            "es" -> "Español"
            "fr" -> "Français"
            "it" -> "Italiano"
            "pt" -> "Português"
            "ru" -> "Русский"
            "ja" -> "日本語"
            "ko" -> "한국어"
            "zh" -> "中文"
            "ar" -> "العربية"
            "hi" -> "हिन्दी"
            "tr" -> "Türkçe"
            "pl" -> "Polski"
            "nl" -> "Nederlands"
            "und" -> "Unknown"
            else -> languageCode.uppercase()
        }
}

/** Extension property to check if channel layout is surround sound. */
private val AudioChannelLayout.isSurround: Boolean
    get() =
        when (this) {
            AudioChannelLayout.SURROUND_5_1,
            AudioChannelLayout.SURROUND_7_1,
            AudioChannelLayout.ATMOS,
            -> true
            else -> false
        }
