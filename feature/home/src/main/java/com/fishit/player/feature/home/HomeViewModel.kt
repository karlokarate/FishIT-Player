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

    val state: StateFlow<HomeState> = combine(
        telegramItems,
        xtreamLiveItems,
        xtreamVodItems,
        xtreamSeriesItems,
        errorState,
        syncStateObserver.observeSyncState(),
        sourceActivationStore.observeStates()
    ) { values ->
        // Destructure the array of values from combine
        @Suppress("UNCHECKED_CAST")
        val telegram = values[0] as List<HomeMediaItem>
        @Suppress("UNCHECKED_CAST")
        val live = values[1] as List<HomeMediaItem>
        @Suppress("UNCHECKED_CAST")
        val vod = values[2] as List<HomeMediaItem>
        @Suppress("UNCHECKED_CAST")
        val series = values[3] as List<HomeMediaItem>
        val error = values[4] as String?
        val syncState = values[5] as SyncUiState
        val sourceActivation = values[6] as SourceActivationSnapshot
        
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
            hasXtreamSource = listOf(live, vod, series).any { it.isNotEmpty() },
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
