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
    // Cross-pipeline unified rows
    val moviesItems: List<HomeMediaItem> = emptyList(),
    val seriesItems: List<HomeMediaItem> = emptyList(),
    val clipsItems: List<HomeMediaItem> = emptyList(),
    // Source-specific rows
    val xtreamLiveItems: List<HomeMediaItem> = emptyList(),
    // Legacy rows (for backward compatibility)
    val telegramMediaItems: List<HomeMediaItem> = emptyList(),
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
                moviesItems.isNotEmpty() ||
                seriesItems.isNotEmpty() ||
                clipsItems.isNotEmpty() ||
                xtreamLiveItems.isNotEmpty()
}

/**
 * Type-safe container for all home content streams.
 * 
 * This ensures that adding/removing a stream later cannot silently break index order.
 * Each field is strongly typed - no Array<Any?> or index-based access needed.
 * 
 * @property continueWatching Items the user has started watching
 * @property recentlyAdded Recently added items across all sources
 * @property movies Cross-pipeline movies (Xtream + Telegram)
 * @property series Cross-pipeline series (Xtream + Telegram)
 * @property clips Telegram clips (short videos without TMDB match)
 * @property xtreamLive Xtream live channel items
 */
data class HomeContentStreams(
    val continueWatching: List<HomeMediaItem> = emptyList(),
    val recentlyAdded: List<HomeMediaItem> = emptyList(),
    val movies: List<HomeMediaItem> = emptyList(),
    val series: List<HomeMediaItem> = emptyList(),
    val clips: List<HomeMediaItem> = emptyList(),
    val xtreamLive: List<HomeMediaItem> = emptyList()
) {
    /** True if any content stream has items */
    val hasContent: Boolean
        get() = continueWatching.isNotEmpty() ||
                recentlyAdded.isNotEmpty() ||
                movies.isNotEmpty() ||
                series.isNotEmpty() ||
                clips.isNotEmpty() ||
                xtreamLive.isNotEmpty()
}

/**
 * Intermediate type-safe holder for first stage of content aggregation.
 * 
 * Used internally by HomeViewModel to combine the first 4 flows type-safely,
 * then combined with remaining flows in stage 2 to produce HomeContentStreams.
 * 
 * This 2-stage approach allows combining all 6 flows without exceeding the
 * 4-parameter type-safe combine overload limit.
 */
internal data class HomeContentPartial(
    val continueWatching: List<HomeMediaItem>,
    val recentlyAdded: List<HomeMediaItem>,
    val movies: List<HomeMediaItem>,
    val xtreamLive: List<HomeMediaItem>
)

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

    // ==================== Content Flows ====================

    private val continueWatchingItems: Flow<List<HomeMediaItem>> =
        homeContentRepository.observeContinueWatching().toHomeItems()

    private val recentlyAddedItems: Flow<List<HomeMediaItem>> =
        homeContentRepository.observeRecentlyAdded().toHomeItems()

    private val moviesItems: Flow<List<HomeMediaItem>> =
        homeContentRepository.observeMovies().toHomeItems()

    private val seriesItems: Flow<List<HomeMediaItem>> =
        homeContentRepository.observeSeries().toHomeItems()

    private val clipsItems: Flow<List<HomeMediaItem>> =
        homeContentRepository.observeClips().toHomeItems()

    private val xtreamLiveItems: Flow<List<HomeMediaItem>> =
        homeContentRepository.observeXtreamLive().toHomeItems()

    // ==================== Type-Safe Content Aggregation ====================

    /**
     * Stage 1: Combine first 4 flows into HomeContentPartial.
     * 
     * Uses the 4-parameter combine overload (type-safe, no casts needed).
     */
    private val contentPartial: Flow<HomeContentPartial> = combine(
        continueWatchingItems,
        recentlyAddedItems,
        moviesItems,
        xtreamLiveItems
    ) { continueWatching, recentlyAdded, movies, live ->
        HomeContentPartial(
            continueWatching = continueWatching,
            recentlyAdded = recentlyAdded,
            movies = movies,
            xtreamLive = live
        )
    }

    /**
     * Stage 2: Combine partial with remaining flows into HomeContentStreams.
     * 
     * Uses the 3-parameter combine overload (type-safe, no casts needed).
     * All 6 content flows are now aggregated without any Array<Any?> or index access.
     */
    private val contentStreams: Flow<HomeContentStreams> = combine(
        contentPartial,
        seriesItems,
        clipsItems
    ) { partial, series, clips ->
        HomeContentStreams(
            continueWatching = partial.continueWatching,
            recentlyAdded = partial.recentlyAdded,
            movies = partial.movies,
            xtreamLive = partial.xtreamLive,
            series = series,
            clips = clips
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
            moviesItems = content.movies,
            seriesItems = content.series,
            clipsItems = content.clips,
            xtreamLiveItems = content.xtreamLive,
            // Legacy fields for backward compatibility
            telegramMediaItems = emptyList(),
            xtreamVodItems = emptyList(),
            xtreamSeriesItems = emptyList(),
            error = error,
            hasTelegramSource = content.clips.isNotEmpty() || 
                              content.movies.any { it.sourceTypes.contains(SourceType.TELEGRAM) },
            hasXtreamSource = content.xtreamLive.isNotEmpty() || 
                              content.movies.any { it.sourceTypes.contains(SourceType.XTREAM) },
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
