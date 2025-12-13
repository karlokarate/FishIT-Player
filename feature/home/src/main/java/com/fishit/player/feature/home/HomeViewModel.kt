package com.fishit.player.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.feature.home.domain.HomeContentRepository
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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
    val poster: ImageRef?,
    val placeholderThumbnail: ImageRef? = null,
    val backdrop: ImageRef?,
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
    private val homeContentRepository: HomeContentRepository
) : ViewModel() {

    private val errorState = MutableStateFlow<String?>(null)

    private val telegramItems: Flow<List<HomeMediaItem>> =
        homeContentRepository.observeTelegramMedia().toHomeItems()

    private val xtreamLiveItems: Flow<List<HomeMediaItem>> =
        homeContentRepository.observeXtreamLive().toHomeItems()

    private val xtreamVodItems: Flow<List<HomeMediaItem>> =
        homeContentRepository.observeXtreamVod().toHomeItems()

    private val xtreamSeriesItems: Flow<List<HomeMediaItem>> =
        homeContentRepository.observeXtreamSeries().toHomeItems()

    val state: StateFlow<HomeState> = combine(
        telegramItems,
        xtreamLiveItems,
        xtreamVodItems,
        xtreamSeriesItems,
        errorState
    ) { telegram, live, vod, series, error ->
        HomeState(
            isLoading = false,
            continueWatchingItems = emptyList(),
            recentlyAddedItems = emptyList(),
            telegramMediaItems = telegram,
            xtreamLiveItems = live,
            xtreamVodItems = vod,
            xtreamSeriesItems = series,
            error = error,
            hasTelegramSource = telegram.isNotEmpty(),
            hasXtreamSource = listOf(live, vod, series).any { it.isNotEmpty() }
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeState()
        )

    fun refresh() {
        viewModelScope.launch {
            // Clears UI error state only.
            // Data reload is handled by background CatalogSync, not by Home.
            errorState.emit(null)
        }
    }

    fun onItemClicked(item: HomeMediaItem) {
        // TODO: Navigate to detail screen
        // Will be handled by navigation callback from UI
    }

    private fun Flow<List<RawMediaMetadata>>.toHomeItems(): Flow<List<HomeMediaItem>> = this
        .map { items ->
            items
                .take(HOME_ROW_LIMIT)
                .map { raw -> raw.toHomeMediaItem() }
        }
        .distinctUntilChanged()
        .onStart { emit(emptyList()) }
        .catch { throwable ->
            UnifiedLog.e(TAG, throwable) { "Error loading home content" }
            errorState.emit(throwable.message ?: "Unknown error loading content")
            emit(emptyList())
        }

    private fun RawMediaMetadata.toHomeMediaItem(): HomeMediaItem {
        val bestPoster = poster ?: thumbnail
        val bestBackdrop = backdrop ?: thumbnail
        return HomeMediaItem(
            id = sourceId,
            title = originalTitle.ifBlank { sourceLabel },
            poster = bestPoster,
            placeholderThumbnail = placeholderThumbnail,
            backdrop = bestBackdrop,
            mediaType = mediaType,
            sourceType = sourceType,
            duration = durationMinutes?.let { it * 60_000L } ?: 0L,
            year = year,
            navigationId = sourceId,
            navigationSource = sourceType
        )
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val HOME_ROW_LIMIT = 20
    }
}
