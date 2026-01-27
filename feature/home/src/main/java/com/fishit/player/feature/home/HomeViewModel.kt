package com.fishit.player.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fishit.player.core.catalogsync.SyncStateObserver
import com.fishit.player.core.catalogsync.SyncUiState
import com.fishit.player.core.home.domain.HomeContentRepository
import com.fishit.player.core.home.domain.HomeMediaItem
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.filter.PresetGenreFilter
import com.fishit.player.core.sourceactivation.SourceActivationSnapshot
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Home screen state
 *
 * Contract: STARTUP_TRIGGER_CONTRACT (U-1, O-1)
 * - syncState: Shows current catalog sync status for observability
 * - sourceActivation: Shows which sources are active for meaningful empty states
 *
 * **Progressive Loading:**
 * Each row loads independently. UI shows content as soon as any row is ready.
 * Row-level loading states enable skeleton placeholders per row.
 */
data class HomeState(
    val isLoading: Boolean = true,
    val continueWatchingItems: List<HomeMediaItem> = emptyList(),
    val recentlyAddedItems: List<HomeMediaItem> = emptyList(),
    // Cross-pipeline unified rows (canonical system)
    val moviesItems: List<HomeMediaItem> = emptyList(),
    val seriesItems: List<HomeMediaItem> = emptyList(),
    val clipsItems: List<HomeMediaItem> = emptyList(),
    // Source-specific rows (Live is intentionally outside canonical - ephemeral streams,
    // EPG-based)
    val xtreamLiveItems: List<HomeMediaItem> = emptyList(),
    val error: String? = null,
    val hasTelegramSource: Boolean = false,
    val hasXtreamSource: Boolean = false,
    /** Current catalog sync state: Idle, Running, Success, or Failed */
    val syncState: SyncUiState = SyncUiState.Idle,
    /** Current source activation state snapshot */
    val sourceActivation: SourceActivationSnapshot = SourceActivationSnapshot.EMPTY,
    // === Search & Filter ===
    /** Current search query (empty = no search active) */
    val searchQuery: String = "",
    /** Currently selected genre filter */
    val selectedGenre: PresetGenreFilter = PresetGenreFilter.ALL,
    /** Whether search/filter panel is visible */
    val isSearchVisible: Boolean = false,
    /** All available genres extracted from content (for dynamic filtering) */
    val availableGenres: List<String> = emptyList(),
    // === Row-level loading states (Progressive Loading) ===
    /** True while continue watching row is still loading */
    val isContinueWatchingLoading: Boolean = true,
    /** True while recently added row is still loading */
    val isRecentlyAddedLoading: Boolean = true,
    /** True while movies row is still loading */
    val isMoviesLoading: Boolean = true,
    /** True while series row is still loading */
    val isSeriesLoading: Boolean = true,
    /** True while clips row is still loading */
    val isClipsLoading: Boolean = true,
    /** True while live row is still loading */
    val isLiveLoading: Boolean = true,
) {
    /** True if there is any content to display */
    val hasContent: Boolean
        get() =
            continueWatchingItems.isNotEmpty() ||
                recentlyAddedItems.isNotEmpty() ||
                moviesItems.isNotEmpty() ||
                seriesItems.isNotEmpty() ||
                clipsItems.isNotEmpty() ||
                xtreamLiveItems.isNotEmpty()

    /** True if search or filter is active */
    val isFilterActive: Boolean
        get() = searchQuery.isNotBlank() || selectedGenre != PresetGenreFilter.ALL
    
    /** True if any row is still loading (for global skeleton) */
    val isAnyRowLoading: Boolean
        get() = isContinueWatchingLoading || isRecentlyAddedLoading || isMoviesLoading ||
                isSeriesLoading || isClipsLoading || isLiveLoading
}

/**
 * HomeViewModel - Manages Home screen state with Progressive Loading
 *
 * **Progressive Loading Architecture:**
 * Instead of using combine() which waits for ALL flows, each content flow
 * independently updates the state. This means:
 * - Movies row appears as soon as movies are loaded
 * - Series row appears independently
 * - User sees content immediately, not after slowest row finishes
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
@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val homeContentRepository: HomeContentRepository,
        private val syncStateObserver: SyncStateObserver,
        private val sourceActivationStore: SourceActivationStore,
    ) : ViewModel() {
        
        // ==================== Mutable State (Progressive Loading) ====================
        
        private val _state = MutableStateFlow(HomeState())
        
        /**
         * Primary state flow for UI consumption.
         * 
         * **Progressive Loading:** Each row updates independently.
         * UI sees content as soon as any row emits data.
         */
        val state: StateFlow<HomeState> = _state.asStateFlow()
        
        // ==================== Paging Flows (Horizontal Infinite Scroll) ====================
        
        /**
         * Movies row with horizontal paging.
         * Use with collectAsLazyPagingItems() in LazyRow.
         */
        val moviesPagingFlow: Flow<PagingData<HomeMediaItem>> =
            homeContentRepository.getMoviesPagingData()
                .cachedIn(viewModelScope)
        
        /**
         * Series row with horizontal paging.
         * Use with collectAsLazyPagingItems() in LazyRow.
         */
        val seriesPagingFlow: Flow<PagingData<HomeMediaItem>> =
            homeContentRepository.getSeriesPagingData()
                .cachedIn(viewModelScope)
        
        /**
         * Clips row with horizontal paging.
         * Use with collectAsLazyPagingItems() in LazyRow.
         */
        val clipsPagingFlow: Flow<PagingData<HomeMediaItem>> =
            homeContentRepository.getClipsPagingData()
                .cachedIn(viewModelScope)
        
        /**
         * Live TV row with horizontal paging.
         * Use with collectAsLazyPagingItems() in LazyRow.
         */
        val livePagingFlow: Flow<PagingData<HomeMediaItem>> =
            homeContentRepository.getLivePagingData()
                .cachedIn(viewModelScope)
        
        /**
         * Recently Added row with horizontal paging.
         * Use with collectAsLazyPagingItems() in LazyRow.
         */
        val recentlyAddedPagingFlow: Flow<PagingData<HomeMediaItem>> =
            homeContentRepository.getRecentlyAddedPagingData()
                .cachedIn(viewModelScope)

        // ==================== Search & Filter State ====================

        private val _searchQuery = MutableStateFlow("")
        private val _selectedGenre = MutableStateFlow(PresetGenreFilter.ALL)
        private val _isSearchVisible = MutableStateFlow(false)

        /**
         * Debounced search query to prevent excessive filtering on every keystroke.
         */
        private val debouncedSearchQuery =
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = "",
                )

        init {
            // ==================== Progressive Content Loading ====================
            // Each flow updates state independently - no combine() blocking
            
            homeContentRepository.observeContinueWatching()
                .catch { e ->
                    UnifiedLog.e(TAG, e) { "Error loading continue watching" }
                }
                .onEach { items ->
                    _state.update { it.copy(
                        continueWatchingItems = items,
                        isContinueWatchingLoading = false,
                        isLoading = false, // At least one row loaded
                    ) }
                }
                .launchIn(viewModelScope)
            
            homeContentRepository.observeRecentlyAdded()
                .catch { e ->
                    UnifiedLog.e(TAG, e) { "Error loading recently added" }
                }
                .onEach { items ->
                    _state.update { it.copy(
                        recentlyAddedItems = items,
                        isRecentlyAddedLoading = false,
                        isLoading = false,
                    ) }
                }
                .launchIn(viewModelScope)
            
            homeContentRepository.observeMovies()
                .catch { e ->
                    UnifiedLog.e(TAG, e) { "Error loading movies" }
                }
                .onEach { items ->
                    _state.update { it.copy(
                        moviesItems = items,
                        isMoviesLoading = false,
                        isLoading = false,
                        hasXtreamSource = it.hasXtreamSource || items.any { item ->
                            item.sourceTypes.contains(SourceType.XTREAM)
                        },
                        hasTelegramSource = it.hasTelegramSource || items.any { item ->
                            item.sourceTypes.contains(SourceType.TELEGRAM)
                        },
                    ) }
                    updateAvailableGenres()
                }
                .launchIn(viewModelScope)
            
            homeContentRepository.observeSeries()
                .catch { e ->
                    UnifiedLog.e(TAG, e) { "Error loading series" }
                }
                .onEach { items ->
                    _state.update { it.copy(
                        seriesItems = items,
                        isSeriesLoading = false,
                        isLoading = false,
                    ) }
                    updateAvailableGenres()
                }
                .launchIn(viewModelScope)
            
            homeContentRepository.observeClips()
                .catch { e ->
                    UnifiedLog.e(TAG, e) { "Error loading clips" }
                }
                .onEach { items ->
                    _state.update { it.copy(
                        clipsItems = items,
                        isClipsLoading = false,
                        isLoading = false,
                        hasTelegramSource = it.hasTelegramSource || items.isNotEmpty(),
                    ) }
                }
                .launchIn(viewModelScope)
            
            homeContentRepository.observeXtreamLive()
                .catch { e ->
                    UnifiedLog.e(TAG, e) { "Error loading live channels" }
                }
                .onEach { items ->
                    _state.update { it.copy(
                        xtreamLiveItems = items,
                        isLiveLoading = false,
                        isLoading = false,
                        hasXtreamSource = it.hasXtreamSource || items.isNotEmpty(),
                    ) }
                }
                .launchIn(viewModelScope)
            
            // ==================== Metadata Flows ====================
            
            syncStateObserver.observeSyncState()
                .onEach { syncState ->
                    _state.update { it.copy(syncState = syncState) }
                }
                .launchIn(viewModelScope)
            
            sourceActivationStore.observeStates()
                .onEach { sourceActivation ->
                    _state.update { it.copy(sourceActivation = sourceActivation) }
                }
                .launchIn(viewModelScope)
            
            // ==================== Search/Filter State ====================
            
            debouncedSearchQuery
                .onEach { query ->
                    _state.update { it.copy(searchQuery = query) }
                }
                .launchIn(viewModelScope)
            
            _selectedGenre
                .onEach { genre ->
                    _state.update { it.copy(selectedGenre = genre) }
                }
                .launchIn(viewModelScope)
            
            _isSearchVisible
                .onEach { visible ->
                    _state.update { it.copy(isSearchVisible = visible) }
                }
                .launchIn(viewModelScope)
        }

        /**
         * Filtered state that applies search query, genre filter, and search visibility.
         *
         * This is the primary state to use in UI when filtering is desired.
         */
        val filteredState: StateFlow<HomeState> =
            combine(
                state,
                debouncedSearchQuery,
                _selectedGenre,
                _isSearchVisible,
            ) { currentState, query, genre, isSearchVisible ->
                val baseState =
                    if (query.isBlank() && genre == PresetGenreFilter.ALL) {
                        currentState
                    } else {
                        currentState.copy(
                            continueWatchingItems = filterItems(currentState.continueWatchingItems, query, genre),
                            recentlyAddedItems = filterItems(currentState.recentlyAddedItems, query, genre),
                            moviesItems = filterItems(currentState.moviesItems, query, genre),
                            seriesItems = filterItems(currentState.seriesItems, query, genre),
                            clipsItems = filterItems(currentState.clipsItems, query, genre),
                            xtreamLiveItems = filterItems(currentState.xtreamLiveItems, query, genre),
                        )
                    }
                baseState.copy(
                    searchQuery = query,
                    selectedGenre = genre,
                    isSearchVisible = isSearchVisible,
                )
            }.distinctUntilChanged()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = HomeState(),
                )

        // ==================== Public Actions ====================

        fun refresh() {
            // Reset loading states to trigger refresh
            _state.update { it.copy(
                isContinueWatchingLoading = true,
                isRecentlyAddedLoading = true,
                isMoviesLoading = true,
                isSeriesLoading = true,
                isClipsLoading = true,
                isLiveLoading = true,
                error = null,
            ) }
            // Note: Data reload is handled by background CatalogSync, not by Home.
            // The flows will re-emit when data changes.
        }

        fun onItemClicked(item: HomeMediaItem) {
            // TODO: Navigate to detail screen
            // Will be handled by navigation callback from UI
        }

        /** Toggle search panel visibility */
        fun toggleSearch() {
            _isSearchVisible.value = !_isSearchVisible.value
            if (!_isSearchVisible.value) {
                // Clear search when closing
                _searchQuery.value = ""
            }
        }

        /** Update search query */
        fun setSearchQuery(query: String) {
            _searchQuery.value = query
        }

        /** Update selected genre filter */
        fun setGenreFilter(genre: PresetGenreFilter) {
            _selectedGenre.value = genre
        }

        /** Clear all filters */
        fun clearFilters() {
            _searchQuery.value = ""
            _selectedGenre.value = PresetGenreFilter.ALL
        }

        // ==================== Private Helpers ====================

        /**
         * Filter items by search query and genre.
         *
         * Search matches:
         * - Title (case-insensitive)
         * - Year (if query is a 4-digit number)
         *
         * Genre matches:
         * - Uses PresetGenreFilter.matchesGenres() for consistent filtering
         */
        private fun filterItems(
            items: List<HomeMediaItem>,
            query: String,
            genre: PresetGenreFilter,
        ): List<HomeMediaItem> {
            val queryLower = query.lowercase().trim()
            val yearQuery = queryLower.toIntOrNull()?.takeIf { it in 1900..2100 }

            return items.filter { item ->
                val matchesQuery =
                    queryLower.isBlank() ||
                        item.title.lowercase().contains(queryLower) ||
                        (yearQuery != null && item.year == yearQuery)

                // Use PresetGenreFilter.matchesGenres() for consistent filtering across all screens
                val itemGenres = item.genres?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
                val matchesGenre = genre.matchesGenres(itemGenres)

                matchesQuery && matchesGenre
            }
        }

        /**
         * Update available genres from current state.
         * Called when movies or series data changes.
         */
        private fun updateAvailableGenres() {
            val currentState = _state.value
            val allItems = currentState.continueWatchingItems +
                currentState.recentlyAddedItems +
                currentState.moviesItems +
                currentState.seriesItems +
                currentState.clipsItems +
                currentState.xtreamLiveItems
            
            val genres = allItems
                .mapNotNull { it.genres }
                .flatMap { it.split(",", ";") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
            
            _state.update { it.copy(availableGenres = genres) }
        }

        private companion object {
            const val TAG = "HomeViewModel"
        }
    }
