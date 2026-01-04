package com.fishit.player.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.data.model.ContentType
import com.fishit.player.data.model.Episode
import com.fishit.player.data.model.Season
import com.fishit.player.data.model.SeriesInfo
import com.fishit.player.data.model.StreamInfo
import com.fishit.player.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class UnifiedDetailViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _selectedSeason = MutableStateFlow<Season?>(null)
    val selectedSeason: StateFlow<Season?> = _selectedSeason.asStateFlow()

    private val _selectedEpisode = MutableStateFlow<Episode?>(null)
    val selectedEpisode: StateFlow<Episode?> = _selectedEpisode.asStateFlow()

    private var currentContentId: String? = null
    private var currentContentType: ContentType? = null

    fun loadContent(contentId: String, contentType: ContentType) {
        if (currentContentId == contentId && currentContentType == contentType) {
            Timber.d("Content already loaded: id=$contentId, type=$contentType")
            return
        }

        currentContentId = contentId
        currentContentType = contentType

        Timber.i("Loading content: id=$contentId, type=$contentType")

        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading

            try {
                when (contentType) {
                    ContentType.MOVIE -> loadMovieDetails(contentId)
                    ContentType.SERIES -> loadSeriesDetails(contentId)
                    ContentType.LIVE -> loadLiveDetails(contentId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading content: id=$contentId, type=$contentType")
                _uiState.value = DetailUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    private suspend fun loadMovieDetails(movieId: String) {
        Timber.d("Fetching movie details: id=$movieId")
        val streamInfo = contentRepository.getStreamInfo(movieId, ContentType.MOVIE)
        _uiState.value = DetailUiState.MovieContent(streamInfo)
        Timber.i("Movie details loaded successfully: id=$movieId, title=${streamInfo.name}")
    }

    private suspend fun loadSeriesDetails(seriesId: String) {
        Timber.d("Fetching series info: id=$seriesId")
        val seriesInfo = contentRepository.getSeriesInfo(seriesId)
        
        if (seriesInfo.seasons.isEmpty()) {
            Timber.w("No seasons found for series: id=$seriesId")
            _uiState.value = DetailUiState.Error("No seasons available")
            return
        }

        val firstSeason = seriesInfo.seasons.first()
        _selectedSeason.value = firstSeason
        Timber.d("Series info loaded: id=$seriesId, title=${seriesInfo.name}, seasons=${seriesInfo.seasons.size}, episodes=${firstSeason.episodes.size}")

        if (firstSeason.episodes.isNotEmpty()) {
            val firstEpisode = firstSeason.episodes.first()
            _selectedEpisode.value = firstEpisode
            Timber.d("Auto-selected first episode: seasonNum=${firstSeason.seasonNumber}, episodeNum=${firstEpisode.episodeNum}")
            loadEpisodeStream(firstEpisode.id)
        } else {
            Timber.w("First season has no episodes: seriesId=$seriesId, seasonNum=${firstSeason.seasonNumber}")
            _uiState.value = DetailUiState.SeriesContent(seriesInfo, null)
        }
    }

    private suspend fun loadLiveDetails(streamId: String) {
        Timber.d("Fetching live stream details: id=$streamId")
        val streamInfo = contentRepository.getStreamInfo(streamId, ContentType.LIVE)
        _uiState.value = DetailUiState.LiveContent(streamInfo)
        Timber.i("Live stream details loaded successfully: id=$streamId, title=${streamInfo.name}")
    }

    private suspend fun loadEpisodeStream(episodeId: String) {
        Timber.d("Fetching episode stream: episodeId=$episodeId")
        
        // Fix #1: Add .take(1) to prevent memory leak from infinite retries
        contentRepository.getStreamInfo(episodeId, ContentType.SERIES)
            .take(1)
            .retryWhen { cause, attempt ->
                if (attempt < 3) {
                    Timber.w(cause, "Retry attempt ${attempt + 1} for episode stream: episodeId=$episodeId")
                    // Fix #2: Use exponential backoff with delay for race condition handling
                    delay(1000L * (attempt + 1))
                    true
                } else {
                    Timber.e(cause, "Failed to load episode stream after 3 attempts: episodeId=$episodeId")
                    false
                }
            }
            .catch { e ->
                Timber.e(e, "Error loading episode stream: episodeId=$episodeId")
                // Fix #6: Update error state properly
                _uiState.value = DetailUiState.Error("Failed to load episode: ${e.message}")
            }
            .collect { streamInfo ->
                val currentState = _uiState.value
                if (currentState is DetailUiState.SeriesContent) {
                    _uiState.value = currentState.copy(currentEpisodeStream = streamInfo)
                    Timber.i("Episode stream loaded: episodeId=$episodeId, title=${streamInfo.name}")
                } else {
                    Timber.w("Unexpected state when loading episode stream: state=${currentState::class.simpleName}")
                }
            }
    }

    fun selectSeason(season: Season) {
        Timber.d("Season selected: seasonNum=${season.seasonNumber}, episodes=${season.episodes.size}")
        _selectedSeason.value = season

        if (season.episodes.isNotEmpty()) {
            val firstEpisode = season.episodes.first()
            selectEpisode(firstEpisode)
        } else {
            Timber.w("Selected season has no episodes: seasonNum=${season.seasonNumber}")
            _selectedEpisode.value = null
            val currentState = _uiState.value
            if (currentState is DetailUiState.SeriesContent) {
                _uiState.value = currentState.copy(currentEpisodeStream = null)
            }
        }
    }

    fun selectEpisode(episode: Episode) {
        Timber.d("Episode selected: episodeNum=${episode.episodeNum}, title=${episode.title}")
        _selectedEpisode.value = episode

        viewModelScope.launch {
            try {
                loadEpisodeStream(episode.id)
            } catch (e: Exception) {
                Timber.e(e, "Error selecting episode: episodeNum=${episode.episodeNum}")
                // Fix #6: Ensure error state is updated
                _uiState.value = DetailUiState.Error("Failed to load episode: ${e.message}")
            }
        }
    }

    fun getStreamUrl(): String? {
        return when (val state = _uiState.value) {
            is DetailUiState.MovieContent -> {
                // Fix #3: Extract stream ID correctly from the full URL path
                val streamId = state.streamInfo.streamUrl.substringAfterLast("/")
                    .substringBefore(".")
                val url = "${contentRepository.getBaseUrl()}/movie/${contentRepository.getUsername()}/${contentRepository.getPassword()}/$streamId.${state.streamInfo.containerExtension}"
                Timber.d("Generated movie stream URL: $url")
                url
            }
            is DetailUiState.SeriesContent -> {
                state.currentEpisodeStream?.let { episodeStream ->
                    // Fix #3: Extract stream ID correctly from the full URL path
                    val streamId = episodeStream.streamUrl.substringAfterLast("/")
                        .substringBefore(".")
                    val url = "${contentRepository.getBaseUrl()}/series/${contentRepository.getUsername()}/${contentRepository.getPassword()}/$streamId.${episodeStream.containerExtension}"
                    Timber.d("Generated series stream URL: episodeId=${_selectedEpisode.value?.id}, url=$url")
                    url
                } ?: run {
                    Timber.w("No episode stream available for URL generation")
                    null
                }
            }
            is DetailUiState.LiveContent -> {
                // Fix #3: Extract stream ID correctly from the full URL path
                val streamId = state.streamInfo.streamUrl.substringAfterLast("/")
                    .substringBefore(".")
                val url = "${contentRepository.getBaseUrl()}/live/${contentRepository.getUsername()}/${contentRepository.getPassword()}/$streamId.${state.streamInfo.containerExtension}"
                Timber.d("Generated live stream URL: $url")
                url
            }
            else -> {
                Timber.w("Cannot generate stream URL for current state: ${state::class.simpleName}")
                null
            }
        }
    }

    fun getThumbnailUrl(): String? {
        val thumbnailPath = when (val state = _uiState.value) {
            is DetailUiState.MovieContent -> state.streamInfo.streamIcon
            is DetailUiState.SeriesContent -> {
                state.currentEpisodeStream?.streamIcon ?: state.seriesInfo.cover
            }
            is DetailUiState.LiveContent -> state.streamInfo.streamIcon
            else -> null
        }

        // Fix #4: Validate thumbnail URL before returning
        return thumbnailPath?.takeIf { it.isNotBlank() }?.let { path ->
            val url = if (path.startsWith("http")) {
                path
            } else {
                "${contentRepository.getBaseUrl()}/$path"
            }
            Timber.d("Generated thumbnail URL: $url")
            url
        } ?: run {
            Timber.w("No valid thumbnail available for current state")
            null
        }
    }

    fun getContentTitle(): String {
        return when (val state = _uiState.value) {
            is DetailUiState.MovieContent -> state.streamInfo.name
            is DetailUiState.SeriesContent -> state.seriesInfo.name
            is DetailUiState.LiveContent -> state.streamInfo.name
            else -> ""
        }
    }

    fun getContentDescription(): String {
        return when (val state = _uiState.value) {
            is DetailUiState.MovieContent -> state.streamInfo.plot ?: ""
            is DetailUiState.SeriesContent -> {
                _selectedEpisode.value?.plot ?: state.seriesInfo.plot ?: ""
            }
            is DetailUiState.LiveContent -> state.streamInfo.plot ?: ""
            else -> ""
        }
    }
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Error(val message: String) : DetailUiState()
    data class MovieContent(val streamInfo: StreamInfo) : DetailUiState()
    data class SeriesContent(
        val seriesInfo: SeriesInfo,
        val currentEpisodeStream: StreamInfo?
    ) : DetailUiState()
    data class LiveContent(val streamInfo: StreamInfo) : DetailUiState()
}