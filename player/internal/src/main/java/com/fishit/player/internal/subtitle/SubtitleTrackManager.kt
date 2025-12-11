package com.fishit.player.internal.subtitle

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.fishit.player.core.playermodel.SubtitleSelectionState
import com.fishit.player.core.playermodel.SubtitleSourceType
import com.fishit.player.core.playermodel.SubtitleTrack
import com.fishit.player.core.playermodel.SubtitleTrackId
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages subtitle track discovery, selection, and state for the internal player.
 *
 * This component:
 * - Listens to Media3 track changes and maps them to [SubtitleTrack] models
 * - Exposes current subtitle state via [StateFlow]
 * - Provides APIs to select/disable subtitle tracks
 * - Honors default and forced track flags
 * - Integrates with [SubtitleSelectionPolicy] for automatic selection
 *
 * **Architecture:**
 * - Uses types from core:player-model only
 * - No pipeline or data layer imports
 * - Logging via UnifiedLog
 */
class SubtitleTrackManager {

    companion object {
        private const val TAG = "SubtitleTrackManager"
    }

    private var player: Player? = null
    private var isKidMode: Boolean = false

    private val _state = MutableStateFlow(SubtitleSelectionState.INITIAL)
    val state: StateFlow<SubtitleSelectionState> = _state.asStateFlow()

    // Mapping from our SubtitleTrackId to Media3 TrackGroup for selection
    private val trackGroupMap = mutableMapOf<SubtitleTrackId, Pair<TrackGroup, Int>>()

    /**
     * Attaches the manager to a player instance.
     *
     * Call this after the player is created but before playback starts.
     */
    fun attach(player: Player, isKidMode: Boolean = false) {
        this.player = player
        this.isKidMode = isKidMode

        if (isKidMode) {
            UnifiedLog.d(TAG, "Kid mode active - subtitles disabled")
            _state.value = SubtitleSelectionState.DISABLED
            disableSubtitlesInternal(player)
            return
        }

        // Listen for track changes
        player.addListener(
                object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        discoverTracks(tracks)
                    }
                }
        )

        // Initial track discovery if tracks already available
        discoverTracks(player.currentTracks)

        UnifiedLog.d(TAG, "Attached to player")
    }

    /** Detaches the manager from the current player. */
    fun detach() {
        player = null
        trackGroupMap.clear()
        _state.value = SubtitleSelectionState.INITIAL
        UnifiedLog.d(TAG, "Detached from player")
    }

    /**
     * Selects a subtitle track by ID.
     *
     * @param trackId The track to select, or [SubtitleTrackId.OFF] to disable.
     * @return true if the track was successfully selected.
     */
    fun selectTrack(trackId: SubtitleTrackId): Boolean {
        val currentPlayer =
                player
                        ?: run {
                            UnifiedLog.w(TAG, "Cannot select track: no player attached")
                            return false
                        }

        if (isKidMode) {
            UnifiedLog.d(TAG, "Cannot select track: kid mode active")
            return false
        }

        if (trackId == SubtitleTrackId.OFF) {
            return disableSubtitles()
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
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .addOverride(override)
                            .build()

            currentPlayer.trackSelectionParameters = newParams

            _state.update { it.copy(selectedTrackId = trackId, isEnabled = true, error = null) }

            UnifiedLog.i(TAG, "Subtitle track selected: ${trackId.value}")
            return true
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Failed to select track: ${trackId.value}", e)
            _state.update { it.copy(error = "Failed to select subtitle track") }
            return false
        }
    }

    /**
     * Disables all subtitle tracks.
     *
     * @return true if subtitles were successfully disabled.
     */
    fun disableSubtitles(): Boolean {
        val currentPlayer =
                player
                        ?: run {
                            UnifiedLog.w(TAG, "Cannot disable subtitles: no player attached")
                            return false
                        }

        return try {
            disableSubtitlesInternal(currentPlayer)

            _state.update {
                it.copy(selectedTrackId = SubtitleTrackId.OFF, isEnabled = false, error = null)
            }

            UnifiedLog.i(TAG, "Subtitles disabled")
            true
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Failed to disable subtitles", e)
            _state.update { it.copy(error = "Failed to disable subtitles") }
            false
        }
    }

    /**
     * Selects the best default track based on available tracks and preferences.
     *
     * Selection priority:
     * 1. Track marked as default
     * 2. Track marked as forced (for foreign language content)
     * 3. No subtitles
     */
    fun selectDefaultTrack() {
        if (isKidMode) {
            UnifiedLog.d(TAG, "Skipping default selection: kid mode active")
            return
        }

        val currentState = _state.value
        if (currentState.availableTracks.isEmpty()) {
            UnifiedLog.d(TAG, "No tracks available for default selection")
            return
        }

        // Priority 1: Default track
        val defaultTrack = currentState.availableTracks.find { it.isDefault }
        if (defaultTrack != null) {
            UnifiedLog.d(TAG, "Selecting default track: ${defaultTrack.label}")
            selectTrack(defaultTrack.id)
            return
        }

        // Priority 2: Forced track
        val forcedTrack = currentState.availableTracks.find { it.isForced }
        if (forcedTrack != null) {
            UnifiedLog.d(TAG, "Selecting forced track: ${forcedTrack.label}")
            selectTrack(forcedTrack.id)
            return
        }

        // Default: No subtitles
        UnifiedLog.d(TAG, "No default or forced track found, keeping subtitles off")
    }

    /**
     * Selects a track by language code.
     *
     * @param languageCode BCP-47 language code (e.g., "en", "de").
     * @return true if a matching track was found and selected.
     */
    fun selectTrackByLanguage(languageCode: String): Boolean {
        val track =
                _state.value.availableTracks.find {
                    it.language?.startsWith(languageCode, ignoreCase = true) == true
                }

        return if (track != null) {
            selectTrack(track.id)
        } else {
            UnifiedLog.d(TAG, "No track found for language: $languageCode")
            false
        }
    }

    // ========== Private Methods ==========

    private fun discoverTracks(tracks: Tracks) {
        if (isKidMode) {
            return
        }

        trackGroupMap.clear()
        val subtitleTracks = mutableListOf<SubtitleTrack>()

        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT) continue

            val trackGroup = group.mediaTrackGroup

            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue

                val format = trackGroup.getFormat(trackIndex)
                val track = mapFormatToSubtitleTrack(trackGroup, trackIndex, format)

                subtitleTracks.add(track)
                trackGroupMap[track.id] = Pair(trackGroup, trackIndex)
            }
        }

        _state.update { it.copy(availableTracks = subtitleTracks, isLoading = false) }

        UnifiedLog.i(TAG, "Discovered ${subtitleTracks.size} subtitle tracks")

        // Auto-select default track after discovery
        if (subtitleTracks.isNotEmpty() && _state.value.selectedTrackId == SubtitleTrackId.OFF) {
            selectDefaultTrack()
        }
    }

    private fun mapFormatToSubtitleTrack(
            trackGroup: TrackGroup,
            trackIndex: Int,
            format: Format
    ): SubtitleTrack {
        val groupIndex = trackGroup.hashCode() // Use hash as stable group identifier

        return SubtitleTrack(
                id = SubtitleTrackId("$groupIndex:$trackIndex"),
                language = format.language,
                label = format.label ?: format.language ?: "Track ${trackIndex + 1}",
                isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
                isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
                isClosedCaption = format.roleFlags and C.ROLE_FLAG_CAPTION != 0,
                sourceType = determineSourceType(format),
                mimeType = format.sampleMimeType
        )
    }

    private fun determineSourceType(format: Format): SubtitleSourceType {
        return when {
            format.containerMimeType?.contains("mpegurl", ignoreCase = true) == true ->
                    SubtitleSourceType.MANIFEST
            format.containerMimeType?.contains("dash", ignoreCase = true) == true ->
                    SubtitleSourceType.MANIFEST
            format.sampleMimeType?.startsWith("application/x-subrip") == true ->
                    SubtitleSourceType.SIDECAR
            format.sampleMimeType?.startsWith("text/vtt") == true -> SubtitleSourceType.SIDECAR
            else -> SubtitleSourceType.EMBEDDED
        }
    }

    private fun disableSubtitlesInternal(player: Player) {
        val newParams =
                player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()

        player.trackSelectionParameters = newParams
    }
}
