package com.fishit.player.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home screen state
 */
data class HomeState(
    val isLoading: Boolean = true,
    val continueWatchingItems: List<HomeMediaItem> = emptyList(),
    val recentlyAddedItems: List<HomeMediaItem> = emptyList(),
    val telegramMediaItems: List<HomeMediaItem> = emptyList(),
    val xtreamLiveItems: List<HomeMediaItem> = emptyList(),
    val xtreamVodItems: List<HomeMediaItem> = emptyList(),
    val xtreamSeriesItems: List<HomeMediaItem> = emptyList(),
    val error: String? = null,
    val hasTelegramSource: Boolean = false,
    val hasXtreamSource: Boolean = false
)

/**
 * Simplified media item for Home display
 * Maps from RawMediaMetadata for UI consumption
 */
data class HomeMediaItem(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val mediaType: MediaType,
    val sourceType: SourceType,
    val resumePosition: Long = 0L,
    val duration: Long = 0L,
    val isNew: Boolean = false,
    val year: Int? = null,
    val rating: Float? = null,
    // Navigation data
    val navigationId: String,
    val navigationSource: SourceType
) {
    val resumeFraction: Float?
        get() = if (duration > 0 && resumePosition > 0) {
            (resumePosition.toFloat() / duration).coerceIn(0f, 1f)
        } else null
}

/**
 * HomeViewModel - Manages Home screen state
 *
 * Aggregates media from multiple pipelines:
 * - Telegram media
 * - Xtream VOD/Series/Live
 *
 * Creates unified rows for the Home UI.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    // TODO: Inject repositories when available
    // private val telegramCatalogRepository: TelegramCatalogRepository,
    // private val xtreamCatalogRepository: XtreamCatalogRepository,
    // private val watchHistoryRepository: WatchHistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        loadHomeContent()
    }

    private fun loadHomeContent() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // TODO: Load from actual repositories
                // For now, create demo content to validate UI

                // Simulate loading delay
                kotlinx.coroutines.delay(500)

                _state.update {
                    it.copy(
                        isLoading = false,
                        continueWatchingItems = generateDemoItems(
                            count = 5,
                            mediaType = MediaType.MOVIE,
                            withResume = true
                        ),
                        recentlyAddedItems = generateDemoItems(
                            count = 10,
                            mediaType = MediaType.MOVIE,
                            isNew = true
                        ),
                        telegramMediaItems = generateDemoItems(
                            count = 8,
                            mediaType = MediaType.MOVIE,
                            sourceType = SourceType.TELEGRAM
                        ),
                        xtreamVodItems = generateDemoItems(
                            count = 12,
                            mediaType = MediaType.MOVIE,
                            sourceType = SourceType.XTREAM
                        ),
                        xtreamSeriesItems = generateDemoItems(
                            count = 6,
                            mediaType = MediaType.SERIES,
                            sourceType = SourceType.XTREAM
                        ),
                        xtreamLiveItems = generateDemoItems(
                            count = 15,
                            mediaType = MediaType.LIVE,
                            sourceType = SourceType.XTREAM
                        ),
                        hasTelegramSource = true,
                        hasXtreamSource = true
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error loading content"
                    )
                }
            }
        }
    }

    fun refresh() {
        loadHomeContent()
    }

    fun onItemClicked(item: HomeMediaItem) {
        // TODO: Navigate to detail screen
        // Will be handled by navigation callback from UI
    }

    /**
     * Generate demo items for UI validation
     * Will be replaced with real data from repositories
     */
    private fun generateDemoItems(
        count: Int,
        mediaType: MediaType,
        sourceType: SourceType = SourceType.UNKNOWN,
        withResume: Boolean = false,
        isNew: Boolean = false
    ): List<HomeMediaItem> {
        val typeLabel = when (mediaType) {
            MediaType.MOVIE -> "Movie"
            MediaType.SERIES -> "Series"
            MediaType.LIVE -> "Channel"
            else -> "Media"
        }

        return (1..count).map { index ->
            HomeMediaItem(
                id = "${sourceType.name.lowercase()}_${mediaType.name.lowercase()}_$index",
                title = "$typeLabel $index",
                posterUrl = "https://picsum.photos/seed/${sourceType.name}$index/200/300",
                backdropUrl = "https://picsum.photos/seed/${sourceType.name}${index}bg/800/450",
                mediaType = mediaType,
                sourceType = when (sourceType) {
                    SourceType.UNKNOWN -> listOf(
                        SourceType.TELEGRAM,
                        SourceType.XTREAM
                    ).random()
                    else -> sourceType
                },
                resumePosition = if (withResume) (1000L..50000L).random() else 0L,
                duration = if (withResume) 100000L else 0L,
                isNew = isNew,
                year = (2020..2024).random(),
                rating = (5..9).random() + (0..9).random() / 10f,
                navigationId = "${sourceType.name.lowercase()}_${mediaType.name.lowercase()}_$index",
                navigationSource = sourceType
            )
        }
    }
}
