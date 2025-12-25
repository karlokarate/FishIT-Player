package com.fishit.player.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.core.model.repository.CanonicalResumeInfo
import com.fishit.player.feature.detail.ui.helper.DetailEpisodeItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for unified detail screen with cross-pipeline source selection.
 *
 * Features:
 * - Load canonical media with all available sources
 * - Show source badges and version info
 * - Handle source selection for playback
 * - Sync resume across all sources
 */
@HiltViewModel
class UnifiedDetailViewModel
@Inject
constructor(
        private val useCases: UnifiedDetailUseCases,
) : ViewModel() {

    private val _state = MutableStateFlow(UnifiedDetailState())
    val state: StateFlow<UnifiedDetailState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UnifiedDetailEvent>()
    val events = _events.asSharedFlow()

    /**
     * Load canonical media by smart ID detection.
     *
     * This is the PRIMARY method for loading media in the unified detail screen. It intelligently
     * detects whether the provided ID is:
     * - A canonical key (e.g., `movie:inception:2010`)
     * - A source ID (e.g., `msg:123:456`, `xtream:vod:123`)
     *
     * And routes to the appropriate lookup method.
     *
     * @param mediaId Either a canonical key or source ID string
     */
    fun loadByMediaId(mediaId: String) {
        viewModelScope.launch {
            useCases.loadBySmartId(mediaId).collect { mediaState -> handleMediaState(mediaState) }
        }
    }

    /** Load canonical media by canonical ID. */
    fun loadByCanonicalId(canonicalId: CanonicalMediaId) {
        viewModelScope.launch {
            useCases.loadCanonicalMedia(canonicalId).collect { mediaState ->
                handleMediaState(mediaState)
            }
        }
    }

    /**
     * Load canonical media by source ID (reverse lookup).
     *
     * Use when navigating from a pipeline-specific item to unified detail.
     */
    fun loadBySourceId(sourceId: PipelineItemId) {
        viewModelScope.launch {
            useCases.findBySourceId(sourceId).collect { mediaState -> handleMediaState(mediaState) }
        }
    }

    /** Handle media state update from any load method. */
    private fun handleMediaState(mediaState: UnifiedMediaState) {
        when (mediaState) {
            is UnifiedMediaState.Loading -> {
                _state.update { it.copy(isLoading = true, error = null) }
            }
            is UnifiedMediaState.NotFound -> {
                _state.update { it.copy(isLoading = false, error = "Media not found") }
            }
            is UnifiedMediaState.Error -> {
                _state.update { it.copy(isLoading = false, error = mediaState.message) }
            }
            is UnifiedMediaState.Success -> {
                _state.update {
                    it.copy(
                            isLoading = false,
                            error = null,
                            media = mediaState.media,
                            resume = mediaState.resume,
                            selectedSource = mediaState.selectedSource,
                            sourceGroups = useCases.sortSourcesForDisplay(mediaState.media.sources),
                    )
                }
            }
        }
    }

    /** Select a source for playback. */
    fun selectSource(source: MediaSourceRef) {
        _state.update { it.copy(selectedSource = source) }
    }

    /** Start playback with the selected source. */
    fun play() {
        val currentState = _state.value
        val source = currentState.selectedSource ?: return
        val media = currentState.media ?: return

        viewModelScope.launch {
            _events.emit(
                    UnifiedDetailEvent.StartPlayback(
                            canonicalId = media.canonicalId,
                            source = source,
                            resumePositionMs = currentState.resume?.positionMs ?: 0,
                    )
            )
        }
    }

    /** Start playback from the beginning (ignoring resume). */
    fun playFromStart() {
        val currentState = _state.value
        val source = currentState.selectedSource ?: return
        val media = currentState.media ?: return

        viewModelScope.launch {
            _events.emit(
                    UnifiedDetailEvent.StartPlayback(
                            canonicalId = media.canonicalId,
                            source = source,
                            resumePositionMs = 0,
                    )
            )
        }
    }

    /** Resume playback at stored position. */
    fun resume() {
        val currentState = _state.value
        val resume = currentState.resume ?: return
        val media = currentState.media ?: return

        // Prefer selected source, or fall back to last used source
        val source =
                currentState.selectedSource
                        ?: media.sources.find { it.sourceId == resume.lastSourceId } ?: return

        viewModelScope.launch {
            // Calculate resume position for this specific source
            // IMPORTANT: Different sources have different durations!
            val sourceDuration = source.durationMs ?: resume.durationMs
            val resumePosition = resume.calculatePositionForSource(source.sourceId, sourceDuration)

            _events.emit(
                    UnifiedDetailEvent.StartPlayback(
                            canonicalId = media.canonicalId,
                            source = source,
                            resumePositionMs = resumePosition.positionMs,
                            isExactPosition = resumePosition.isExact,
                            approximationNote = resumePosition.note,
                    )
            )
        }
    }

    /**
     * Get the calculated resume position for a specific source.
     *
     * IMPORTANT: Different sources of the same movie have different durations. This method
     * calculates the appropriate resume position based on percentage.
     *
     * @param source The source to calculate resume for
     * @return Calculated position with exact/approximated flag
     */
    fun getResumePositionForSource(source: MediaSourceRef): ResumeCalculation? {
        val resume = _state.value.resume ?: return null

        val sourceDuration = source.durationMs ?: return null
        val position = resume.calculatePositionForSource(source.sourceId, sourceDuration)

        return ResumeCalculation(
                sourceId = source.sourceId,
                positionMs = position.positionMs,
                durationMs = sourceDuration,
                isExact = position.isExact,
                approximationNote = position.note,
                wasLastPlayed = source.sourceId == resume.lastSourceId,
        )
    }

    /** Open source picker dialog. */
    fun showSourcePicker() {
        _state.update { it.copy(showSourcePicker = true) }
    }

    /** Close source picker dialog. */
    fun hideSourcePicker() {
        _state.update { it.copy(showSourcePicker = false) }
    }

    /** Clear resume position. */
    fun clearResume() {
        val media = _state.value.media ?: return

        viewModelScope.launch {
            useCases.markCompleted(media.canonicalId)
            _state.update { it.copy(resume = null) }
        }
    }

    /** Check for better quality versions. */
    fun checkForBetterQuality(): MediaSourceRef? {
        val currentState = _state.value
        val selected = currentState.selectedSource ?: return null
        val media = currentState.media ?: return null

        return useCases.findBetterQualitySource(selected, media.sources)
    }

    /** Filter sources by language. */
    fun filterByLanguage(language: String): List<MediaSourceRef> {
        val media = _state.value.media ?: return emptyList()
        return useCases.findSourcesWithLanguage(media.sources, language)
    }

    // ========== Series-specific methods ==========

    /** Select a season for episode display. */
    fun selectSeason(season: Int) {
        _state.update { it.copy(selectedSeason = season) }
    }

    /** Play a specific episode. */
    fun playEpisode(episode: DetailEpisodeItem) {
        val source = episode.sources.firstOrNull() ?: return
        viewModelScope.launch {
            _events.emit(
                UnifiedDetailEvent.StartPlayback(
                    canonicalId = episode.canonicalId,
                    source = source,
                    resumePositionMs = _state.value.episodeResumes[episode.id]?.positionMs ?: 0,
                )
            )
        }
    }

    // ========== Live-specific methods ==========

    /** Start live stream playback (no resume for live content). */
    fun playLive() {
        val currentState = _state.value
        val source = currentState.selectedSource ?: return
        val media = currentState.media ?: return

        viewModelScope.launch {
            _events.emit(
                UnifiedDetailEvent.StartPlayback(
                    canonicalId = media.canonicalId,
                    source = source,
                    resumePositionMs = 0, // Live always starts "now"
                )
            )
        }
    }
}

/** State for unified detail screen. */
data class UnifiedDetailState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val media: CanonicalMediaWithSources? = null,
        val resume: CanonicalResumeInfo? = null,
        val selectedSource: MediaSourceRef? = null,
        val sourceGroups: List<SourceGroup> = emptyList(),
        val showSourcePicker: Boolean = false,
        // Series-specific state
        val seasons: List<Int> = emptyList(),
        val selectedSeason: Int? = null,
        val episodes: List<DetailEpisodeItem> = emptyList(),
        val episodeResumes: Map<String, CanonicalResumeInfo> = emptyMap(),
        // Live-specific state
        val liveNowPlaying: LiveProgramInfo? = null,
) {
    /** Effective media type for UI rendering */
    val effectiveMediaType: MediaType
        get() = media?.mediaType ?: MediaType.UNKNOWN

    /** Whether this is a series with episodes */
    val isSeries: Boolean
        get() = effectiveMediaType == MediaType.SERIES

    /** Whether this is a series episode */
    val isSeriesEpisode: Boolean
        get() = effectiveMediaType == MediaType.SERIES_EPISODE

    /** Whether this is live content */
    val isLive: Boolean
        get() = effectiveMediaType == MediaType.LIVE

    /** Whether this is audio content (audiobook, podcast, music) */
    val isAudio: Boolean
        get() = effectiveMediaType in listOf(MediaType.AUDIOBOOK, MediaType.PODCAST, MediaType.MUSIC)

    /** Episodes for the currently selected season */
    val displayedEpisodes: List<DetailEpisodeItem>
        get() = if (selectedSeason != null) {
            episodes.filter { it.season == selectedSeason }
        } else {
            episodes
        }

    /** Whether media has multiple sources */
    val hasMultipleSources: Boolean
        get() = (media?.sources?.size ?: 0) > 1

    /** Available source types for badge display */
    val availableSourceTypes: List<SourceType>
        get() = sourceGroups.map { it.sourceType }

    /** Whether resume is available and significant */
    val canResume: Boolean
        get() = resume?.hasSignificantProgress == true

    /** Resume progress as percentage (0-100) */
    val resumeProgressPercent: Int
        get() = ((resume?.progressPercent ?: 0f) * 100).toInt()

    /** Quality label for selected source */
    val selectedQualityLabel: String?
        get() = selectedSource?.quality?.toDisplayLabel()
}

/** Events from unified detail screen. */
sealed class UnifiedDetailEvent {
    /**
     * Start playback with the specified source.
     *
     * @property canonicalId The canonical media identity
     * @property source The source to play
     * @property resumePositionMs Position to resume at (may be approximated for cross-source)
     * @property isExactPosition True if resuming on the same source (frame-accurate)
     * @property approximationNote UI note when position is approximated
     */
    data class StartPlayback(
            val canonicalId: CanonicalMediaId,
            val source: MediaSourceRef,
            val resumePositionMs: Long,
            val isExactPosition: Boolean = true,
            val approximationNote: String? = null,
    ) : UnifiedDetailEvent()

    data class NavigateToSeries(
            val seriesCanonicalId: CanonicalMediaId,
    ) : UnifiedDetailEvent()

    data class ShowError(
            val message: String,
    ) : UnifiedDetailEvent()
}

/**
 * Calculated resume position for a specific source.
 *
 * IMPORTANT: Different sources of the same media have different durations! This data class carries
 * the calculated position and metadata about whether it's exact (same source) or approximated
 * (different source).
 */
data class ResumeCalculation(
        val sourceId: PipelineItemId,
        val positionMs: Long,
        val durationMs: Long,
        val isExact: Boolean,
        val approximationNote: String?,
        val wasLastPlayed: Boolean,
) {
    val progressPercent: Float
        get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    val progressPercentInt: Int
        get() = (progressPercent * 100).toInt()
}

/**
 * Live TV program info for EPG display.
 */
data class LiveProgramInfo(
    val title: String,
    val description: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val isLive: Boolean = true,
)
