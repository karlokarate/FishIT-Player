package com.fishit.player.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.catalogsync.SourceActivationSnapshot
import com.fishit.player.core.catalogsync.SourceActivationStore
import com.fishit.player.core.catalogsync.SyncStateObserver
import com.fishit.player.core.catalogsync.SyncUiState
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.feature.home.domain.HomeContentRepository
import com.fishit.player.feature.home.domain.HomeMediaItem
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
 * 
 * Contract: STARTUP_TRIGGER_CONTRACT (U-1, O-1)
 * - syncState: Shows current catalog sync status for observability
 * - sourceActivation: Shows which sources are active for meaningful empty states
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
    val hasXtreamSource: Boolean = false,
    /** Current catalog sync state: Idle, Running, Success, or Failed */
    val syncState: SyncUiState = SyncUiState.Idle,
    /** Current source activation state snapshot */
    val sourceActivation: SourceActivationSnapshot = SourceActivationSnapshot.EMPTY
) {
    /** True if there is any content to display */
    val hasContent: Boolean
        get() = continueWatchingItems.isNotEmpty() ||
                recentlyAddedItems.isNotEmpty() ||
                telegramMediaItems.isNotEmpty() ||
                xtreamLiveItems.isNotEmpty() ||
                xtreamVodItems.isNotEmpty() ||
                xtreamSeriesItems.isNotEmpty()
}

/**
 * Type-safe container for all home content streams.
 * 
 * This ensures that adding/removing a stream later cannot silently break index order.
 * Each field is strongly typed - no Array<Any?> or index-based access needed.
 * 
 * @property continueWatching Items the user has started watching
 * @property recentlyAdded Recently added items across all sources
 * @property telegramMedia Telegram media items
 * @property xtreamVod Xtream VOD items
 * @property xtreamSeries Xtream series items
 * @property xtreamLive Xtream live channel items
 */
data class HomeContentStreams(
    val continueWatching: List<HomeMediaItem> = emptyList(),
    val recentlyAdded: List<HomeMediaItem> = emptyList(),
    val telegramMedia: List<HomeMediaItem> = emptyList(),
    val xtreamVod: List<HomeMediaItem> = emptyList(),
    val xtreamSeries: List<HomeMediaItem> = emptyList(),
    val xtreamLive: List<HomeMediaItem> = emptyList()
) {
    /** True if any content stream has items */
    val hasContent: Boolean
        get() = continueWatching.isNotEmpty() ||
                recentlyAdded.isNotEmpty() ||
                telegramMedia.isNotEmpty() ||
                xtreamVod.isNotEmpty() ||
                xtreamSeries.isNotEmpty() ||
                xtreamLive.isNotEmpty()
}

/**
 * HomeViewModel - Manages Home screen state
 *
 * Aggregates media from multiple pipelines:
 * - Telegram media
 * - Xtream VOD/Series/Live
 *
 * Creates unified rows for the Home UI.
 * 
 * Contract: STARTUP_TRIGGER_CONTRACT (U-1, O-1)
 * - Observes SyncStateObserver for sync status indicator
 * - Observes SourceActivationStore for meaningful empty states
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeContentRepository: HomeContentRepository,
    private val syncStateObserver: SyncStateObserver,
    private val sourceActivationStore: SourceActivationStore
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

    /**
     * Type-safe flow combining all content streams.
     * 
     * Uses the 4-parameter combine overload (which is type-safe) to aggregate content flows
     * into HomeContentStreams, preserving strong typing without index access or casts.
     */
    private val contentStreams: Flow<HomeContentStreams> = combine(
        telegramItems,
        xtreamLiveItems,
        xtreamVodItems,
        xtreamSeriesItems
    ) { telegram, live, vod, series ->
        HomeContentStreams(
            continueWatching = emptyList(),  // TODO: Wire up continue watching
            recentlyAdded = emptyList(),     // TODO: Wire up recently added
            telegramMedia = telegram,
            xtreamVod = vod,
            xtreamSeries = series,
            xtreamLive = live
        )
    }

    /**
     * Final home state combining content with metadata (errors, sync state, source activation).
     * 
     * Uses the 4-parameter combine overload to maintain type safety throughout.
     * No Array<Any?> values, no index access, no casts.
     */
    val state: StateFlow<HomeState> = combine(
        contentStreams,
        errorState,
        syncStateObserver.observeSyncState(),
        sourceActivationStore.observeStates()
    ) { content, error, syncState, sourceActivation ->
        HomeState(
            isLoading = false,
            continueWatchingItems = content.continueWatching,
            recentlyAddedItems = content.recentlyAdded,
            telegramMediaItems = content.telegramMedia,
            xtreamLiveItems = content.xtreamLive,
            xtreamVodItems = content.xtreamVod,
            xtreamSeriesItems = content.xtreamSeries,
            error = error,
            hasTelegramSource = content.telegramMedia.isNotEmpty(),
            hasXtreamSource = content.xtreamVod.isNotEmpty() || 
                              content.xtreamSeries.isNotEmpty() || 
                              content.xtreamLive.isNotEmpty(),
            syncState = syncState,
            sourceActivation = sourceActivation
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

    private fun Flow<List<HomeMediaItem>>.toHomeItems(): Flow<List<HomeMediaItem>> = this
        .map { items -> items.take(HOME_ROW_LIMIT) }
        .distinctUntilChanged()
        .onStart { emit(emptyList()) }
        .catch { throwable ->
            UnifiedLog.e(TAG, throwable) { "Error loading home content" }
            errorState.emit(throwable.message ?: "Unknown error loading content")
            emit(emptyList())
        }

    private companion object {
        const val TAG = "HomeViewModel"
        const val HOME_ROW_LIMIT = 20
    }
}
