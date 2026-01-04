package com.fishit.player.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.data.CanonicalMediaWithSources
import com.fishit.player.core.domain.usecases.EnsureEpisodePlaybackReadyUseCase
import com.fishit.player.core.domain.usecases.LoadSeasonEpisodesUseCase
import com.fishit.player.core.domain.usecases.LoadSeriesSeasonsUseCase
import com.fishit.player.core.domain.usecases.ObserveCanonicalMediaByIdUseCase
import com.fishit.player.core.logging.UnifiedLog
import com.fishit.player.core.model.CanonicalId
import com.fishit.player.core.model.CanonicalMedia
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PlaybackHints
import com.fishit.player.core.model.xtream.XtreamIdExtensions.toXtreamMovieId
import com.fishit.player.core.model.xtream.XtreamIdExtensions.toXtreamSeriesId
import com.fishit.player.core.navigation.NavigationService
import com.fishit.player.core.playback.PlaybackCoordinator
import com.fishit.player.feature.detail.model.DetailEpisodeItem
import com.fishit.player.feature.detail.model.UnifiedDetailEvent
import com.fishit.player.feature.detail.model.UnifiedDetailState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Unified ViewModel for all media detail screens (Movies, Series, Live TV).
 * Supports both direct canonical ID and navigation-based initialization.
 */
@HiltViewModel
class UnifiedDetailViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val observeCanonicalMediaByIdUseCase: ObserveCanonicalMediaByIdUseCase,
        private val loadSeriesSeasonsUseCase: LoadSeriesSeasonsUseCase,
        private val loadSeasonEpisodesUseCase: LoadSeasonEpisodesUseCase,
        private val ensureEpisodePlaybackReadyUseCase: EnsureEpisodePlaybackReadyUseCase,
        private val playbackCoordinator: PlaybackCoordinator,
        private val navigationService: NavigationService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(UnifiedDetailState())
        val state: StateFlow<UnifiedDetailState> = _state.asStateFlow()

        private val _events = Channel<UnifiedDetailEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        companion object {
            private const val TAG = "UnifiedDetailVM"
            
            // Retry/Timeout constants
            private const val ENRICHMENT_RETRY_DELAY_MS = 500L
            private const val ENRICHMENT_MAX_RETRIES = 3
        }

        init {
            loadMediaDetails()
        }

        // ========== Public API ==========

        /**
         * Called when user selects a different season
         */
        fun onSeasonSelected(seasonNumber: Int) {
            viewModelScope.launch {
                _state.update { it.copy(selectedSeason = seasonNumber) }
                val seriesId = extractSeriesId(_state.value.media?.canonicalId) ?: return@launch
                loadEpisodesForSeason(seriesId, seasonNumber)
            }
        }

        /**
         * Called when user clicks play on a movie or live channel
         */
        fun onPlayClicked() {
            viewModelScope.launch {
                val media = _state.value.media
                if (media == null) {
                    UnifiedLog.e(TAG) { "Cannot play: media is null" }
                    _events.send(UnifiedDetailEvent.ShowError("Wiedergabe nicht möglich"))
                    return@launch
                }

                UnifiedLog.d(TAG) { "Play clicked for ${media.canonicalId}" }

                when (media.type) {
                    MediaType.MOVIE -> playMovie(media)
                    MediaType.LIVE -> playLiveChannel(media)
                    MediaType.SERIES -> {
                        // For series, user should select an episode
                        UnifiedLog.w(TAG) { "Play clicked on series, but user should select episode" }
                    }
                }
            }
        }

        /**
         * Called when user clicks an episode
         */
        fun onEpisodeClicked(episode: DetailEpisodeItem) {
            viewModelScope.launch {
                playEpisode(episode)
            }
        }

        /**
         * Called when user clicks back
         */
        fun onBackClicked() {
            navigationService.navigateBack()
        }

        // ========== Internal: Loading ==========

        private fun loadMediaDetails() {
            viewModelScope.launch {
                try {
                    // Get canonical ID from SavedStateHandle (passed by navigation)
                    val canonicalIdKey: String? = savedStateHandle["canonicalId"]

                    if (canonicalIdKey == null) {
                        UnifiedLog.e(TAG) { "No canonicalId provided in navigation arguments" }
                        _state.update { it.copy(error = "Medien-ID fehlt") }
                        return@launch
                    }

                    val canonicalId = CanonicalId.Key(canonicalIdKey).toCanonicalId()
                    UnifiedLog.d(TAG) { "Loading media details for $canonicalId" }

                    _state.update { it.copy(loading = true) }

                    // Observe media from repository
                    observeCanonicalMediaByIdUseCase(canonicalId)
                        .collect { media ->
                            if (media == null) {
                                UnifiedLog.w(TAG) { "Media not found for $canonicalId" }
                                _state.update {
                                    it.copy(
                                        loading = false,
                                        error = "Medium nicht gefunden",
                                    )
                                }
                                return@collect
                            }

                            UnifiedLog.d(TAG) { "Media loaded: ${media.canonicalId}, type=${media.type}" }

                            _state.update {
                                it.copy(
                                    media = media,
                                    loading = false,
                                    error = null,
                                )
                            }

                            // If series, load seasons
                            if (media.type == MediaType.SERIES) {
                                loadSeriesDetails(media)
                            }
                        }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Failed to load media details" }
                    _state.update {
                        it.copy(
                            loading = false,
                            error = "Fehler beim Laden der Details",
                        )
                    }
                }
            }
        }

        private suspend fun loadSeriesDetails(media: CanonicalMediaWithSources) {
            // Extract Xtream series ID from canonical ID first (before try block for catch access)
            val seriesId =
                extractSeriesId(media.canonicalId) ?: run {
                    UnifiedLog.w(TAG) { "Cannot load series details: unable to extract series ID from ${media.canonicalId.key.value}" }
                    return
                }

            try {
                UnifiedLog.d(TAG) { "Loading series details for seriesId=$seriesId" }

                // Load seasons
                loadSeriesSeasonsUseCase.ensureSeasonsLoaded(seriesId)
                loadSeriesSeasonsUseCase.observeSeasons(seriesId)
                    .take(1)
                    .cancellable()
                    .onEach { seasonItems ->
                        if (!isActive) return@onEach
                        
                        val seasons = seasonItems.map { it.seasonNumber }.sorted()

                        UnifiedLog.d(TAG) { "Loaded ${seasons.size} seasons for series $seriesId" }

                        _state.update {
                            it.copy(
                                seasons = seasons,
                                selectedSeason = it.selectedSeason ?: seasons.firstOrNull() ?: 1,
                            )
                        }

                        // Load first season episodes
                        val firstSeason = _state.value.selectedSeason ?: seasons.firstOrNull()
                        if (firstSeason != null) {
                            loadEpisodesForSeason(seriesId, firstSeason)
                        }
                    }
                    .launchIn(viewModelScope)
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to load series details for seriesId=$seriesId" }
                _state.update { it.copy(error = "Staffeln konnten nicht geladen werden", seasons = emptyList()) }
            }
        }

        private suspend fun loadEpisodesForSeason(
            seriesId: Int,
            seasonNumber: Int,
        ) {
            try {
                UnifiedLog.d(TAG) { "Loading episodes for series $seriesId season $seasonNumber" }

                _state.update { it.copy(episodesLoading = true) }

                loadSeasonEpisodesUseCase.ensureEpisodesLoaded(seriesId, seasonNumber)
                loadSeasonEpisodesUseCase.observeEpisodes(seriesId, seasonNumber)
                    .take(1)
                    .cancellable()
                    .onEach { episodeItems ->
                        if (!isActive) return@onEach
                        
                        val episodes = episodeItems.map { it.toDetailEpisodeItem() }

                        UnifiedLog.d(TAG) { "Loaded ${episodes.size} episodes for season $seasonNumber" }

                        _state.update {
                            it.copy(
                                episodes = episodes,
                                episodesLoading = false,
                            )
                        }
                    }
                    .launchIn(viewModelScope)
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to load episodes for seriesId=$seriesId, season=$seasonNumber" }
                _state.update { it.copy(episodesLoading = false, error = "Episoden konnten nicht geladen werden") }
            }
        }

        // ========== Internal: Playback ==========

        private suspend fun playMovie(media: CanonicalMediaWithSources) {
            try {
                val movieId = extractMovieId(media.canonicalId)
                if (movieId == null) {
                    UnifiedLog.e(TAG) { "Cannot play movie: unable to extract movie ID from ${media.canonicalId}" }
                    _events.send(UnifiedDetailEvent.ShowError("Film-ID konnte nicht ermittelt werden"))
                    return
                }

                UnifiedLog.i(TAG) { "Playing movie: $movieId" }

                val hints = PlaybackHints(canonicalId = media.canonicalId)
                playbackCoordinator.playMovie(movieId, hints)
                _events.send(UnifiedDetailEvent.NavigateToPlayer)
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to play movie" }
                _events.send(UnifiedDetailEvent.ShowError("Film konnte nicht abgespielt werden"))
            }
        }

        private suspend fun playLiveChannel(media: CanonicalMediaWithSources) {
            try {
                UnifiedLog.i(TAG) { "Playing live channel: ${media.canonicalId}" }

                val hints = PlaybackHints(canonicalId = media.canonicalId)
                playbackCoordinator.playLiveChannel(media.canonicalId, hints)
                _events.send(UnifiedDetailEvent.NavigateToPlayer)
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to play live channel" }
                _events.send(UnifiedDetailEvent.ShowError("Kanal konnte nicht abgespielt werden"))
            }
        }

        private suspend fun playEpisode(episode: DetailEpisodeItem) {
            try {
                UnifiedLog.i(TAG) { "Playing episode: ${episode.id}" }

                // Ensure episode is enriched
                val result = ensureEpisodePlaybackReadyUseCase.invoke(episode.id)

                when (result) {
                    is EnsureEpisodePlaybackReadyUseCase.Result.Ready -> {
                        playEpisodeWithHints(episode, result.hints)
                    }

                    is EnsureEpisodePlaybackReadyUseCase.Result.Enriching -> {
                        UnifiedLog.i(TAG) { "Episode enrichment in progress: ${episode.id}, waiting..." }
                        
                        // Show loading state
                        _state.update { it.copy(episodesLoading = true) }
                        
                        // Exponential backoff retry
                        var retryCount = 0
                        var retryResult: EnsureEpisodePlaybackReadyUseCase.Result? = null

                        while (retryCount < ENRICHMENT_MAX_RETRIES) {
                            delay(ENRICHMENT_RETRY_DELAY_MS * (retryCount + 1))
                            retryResult = ensureEpisodePlaybackReadyUseCase.invoke(episode.id)
                            
                            if (retryResult is EnsureEpisodePlaybackReadyUseCase.Result.Ready) {
                                break
                            }
                            retryCount++
                            UnifiedLog.d(TAG) { "Episode enrichment retry $retryCount/$ENRICHMENT_MAX_RETRIES: ${episode.id}" }
                        }

                        _state.update { it.copy(episodesLoading = false) }

                        if (retryResult is EnsureEpisodePlaybackReadyUseCase.Result.Ready) {
                            playEpisodeWithHints(episode, retryResult.hints)
                        } else {
                            UnifiedLog.w(TAG) { "Episode enrichment timeout after $ENRICHMENT_MAX_RETRIES retries: ${episode.id}" }
                            _events.emit(UnifiedDetailEvent.ShowError("Episode wird vorbereitet, bitte später erneut versuchen"))
                        }
                    }

                    is EnsureEpisodePlaybackReadyUseCase.Result.Error -> {
                        UnifiedLog.e(TAG) { "Episode enrichment error: ${episode.id}, error=${result.message}" }
                        _events.emit(UnifiedDetailEvent.ShowError("Episode konnte nicht vorbereitet werden"))
                    }
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to play episode" }
                _events.send(UnifiedDetailEvent.ShowError("Episode konnte nicht abgespielt werden"))
            }
        }

        private suspend fun playEpisodeWithHints(
            episode: DetailEpisodeItem,
            hints: PlaybackHints,
        ) {
            try {
                UnifiedLog.i(TAG) { "Playing episode with hints: ${episode.id}" }
                playbackCoordinator.playEpisode(episode.id, hints)
                _events.send(UnifiedDetailEvent.NavigateToPlayer)
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to play episode with hints" }
                _events.send(UnifiedDetailEvent.ShowError("Episode konnte nicht abgespielt werden"))
            }
        }

        // ========== Internal: Helpers ==========

        private fun extractSeriesId(canonicalId: CanonicalId?): Int? {
            if (canonicalId == null) return null
            return try {
                canonicalId.toXtreamSeriesId()
            } catch (e: Exception) {
                UnifiedLog.w(TAG, e) { "Failed to extract series ID from $canonicalId" }
                null
            }
        }

        private fun extractMovieId(canonicalId: CanonicalId?): Int? {
            if (canonicalId == null) return null
            return try {
                canonicalId.toXtreamMovieId()
            } catch (e: Exception) {
                UnifiedLog.w(TAG, e) { "Failed to extract movie ID from $canonicalId" }
                null
            }
        }

        private fun com.fishit.player.core.model.SeriesEpisode.toDetailEpisodeItem(): DetailEpisodeItem =
            DetailEpisodeItem(
                id = this.id,
                title = this.title,
                episodeNumber = this.episodeNum,
                duration = this.duration,
                plot = this.info?.plot,
                rating = this.info?.rating?.toFloatOrNull(),
                releaseDate = this.info?.releaseDate,
            )
    }
