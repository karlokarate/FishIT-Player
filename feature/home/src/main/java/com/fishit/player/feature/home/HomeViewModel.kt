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
import com.fishit.player.core.persistence.cache.CacheKey
import com.fishit.player.core.persistence.cache.HomeContentCache
import com.fishit.player.core.sourceactivation.SourceActivationSnapshot
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.flatMapLatest
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
    // NOTE: Movies, Series, Clips, Live use PAGING (not Flow-based lists)
    // Large catalogs (40K+ items) require PagingData for memory efficiency.
    // Use the *PagingFlow properties in HomeViewModel instead of state fields.
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
    /** Search results from DB query (when searchQuery is not blank) */
    val searchResults: List<HomeMediaItem> = emptyList(),
    /** True when search is loading */
    val isSearchLoading: Boolean = false,
    // === Row-level loading states (for special rows only) ===
    /** True while continue watching row is still loading */
    val isContinueWatchingLoading: Boolean = true,
    /** True while recently added row is still loading */
    val isRecentlyAddedLoading: Boolean = true,
    // NOTE: Movies/Series/Clips/Live use Paging - loading state is in LazyPagingItems
) {
    /** True if special rows have content (paging rows always considered "has content") */
    val hasSpecialRowContent: Boolean
        get() = continueWatchingItems.isNotEmpty() || recentlyAddedItems.isNotEmpty()

    /** True if search or filter is active */
    val isFilterActive: Boolean
        get() = searchQuery.isNotBlank() || selectedGenre != PresetGenreFilter.ALL
    
    /** True if there are search results to show */
    val hasSearchResults: Boolean
        get() = searchResults.isNotEmpty()
    
    /** True if special rows are still loading */
    val isSpecialRowsLoading: Boolean
        get() = isContinueWatchingLoading || isRecentlyAddedLoading
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
 * **Paging Invalidation:**
 * When sync completes or refresh() is called, the _pagingInvalidationTrigger increments.
 * This causes all Paging flows to re-fetch fresh data from the repository via flatMapLatest.
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
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val homeContentRepository: HomeContentRepository,
        private val syncStateObserver: SyncStateObserver,
        private val sourceActivationStore: SourceActivationStore,
        private val homeContentCache: HomeContentCache,
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
        
        // ==================== Paging Invalidation ====================
        
        /**
         * Trigger for Paging flow invalidation.
         * 
         * **Pattern:** When this value changes, flatMapLatest re-emits the inner flow,
         * causing PagingSource to re-fetch fresh data from ObjectBox.
         * 
         * **Triggers:**
         * - refresh() button clicked
         * - Cache invalidation after sync completion (via HomeContentCache.observeInvalidations)
         */
        private val _pagingInvalidationTrigger = MutableStateFlow(0)
        
        // ==================== Paging Flows (Horizontal Infinite Scroll) ====================
        
        /**
         * Movies row with horizontal paging.
         * Use with collectAsLazyPagingItems() in LazyRow.
         * 
         * **Invalidation:** Recreated when _pagingInvalidationTrigger changes.
         */
        val moviesPagingFlow: Flow<PagingData<HomeMediaItem>> =
            _pagingInvalidationTrigger.flatMapLatest {
                UnifiedLog.d(TAG) { "🔄 Movies PagingFlow recreated (trigger=$it)" }
                homeContentRepository.getMoviesPagingData()
            }.cachedIn(viewModelScope)

        /**
         * Series row with horizontal paging.
         * Use with collectAsLazyPagingItems() in LazyRow.
         * 
         * **Invalidation:** Recreated when _pagingInvalidationTrigger changes.
         */
        val seriesPagingFlow: Flow<PagingData<HomeMediaItem>> =
            _pagingInvalidationTrigger.flatMapLatest {
                UnifiedLog.d(TAG) { "🔄 Series PagingFlow recreated (trigger=$it)" }
                homeContentRepository.getSeriesPagingData()
            }.cachedIn(viewModelScope)

        /**
         * Clips row with horizontal paging.
         * Use with collectAsLazyPagingItems() in LazyRow.
         * 
         * **Invalidation:** Recreated when _pagingInvalidationTrigger changes.
         */
        val clipsPagingFlow: Flow<PagingData<HomeMediaItem>> =
            _pagingInvalidationTrigger.flatMapLatest {
                UnifiedLog.d(TAG) { "🔄 Clips PagingFlow recreated (trigger=$it)" }
                homeContentRepository.getClipsPagingData()
            }.cachedIn(viewModelScope)

        /**
         * Live TV row with horizontal paging.
         * Use with collectAsLazyPagingItems() in LazyRow.
         * 
         * **Invalidation:** Recreated when _pagingInvalidationTrigger changes.
         */
        val livePagingFlow: Flow<PagingData<HomeMediaItem>> =
            _pagingInvalidationTrigger.flatMapLatest {
                UnifiedLog.d(TAG) { "🔄 Live PagingFlow recreated (trigger=$it)" }
                homeContentRepository.getLivePagingData()
            }.cachedIn(viewModelScope)

        /**
         * Recently Added row with horizontal paging.
         * Use with collectAsLazyPagingItems() in LazyRow.
         * 
         * **Invalidation:** Recreated when _pagingInvalidationTrigger changes.
         */
        val recentlyAddedPagingFlow: Flow<PagingData<HomeMediaItem>> =
            _pagingInvalidationTrigger.flatMapLatest {
                UnifiedLog.d(TAG) { "🔄 RecentlyAdded PagingFlow recreated (trigger=$it)" }
                homeContentRepository.getRecentlyAddedPagingData()
            }.cachedIn(viewModelScope)

        // ==================== Search & Filter State ====================

        private val _searchQuery = MutableStateFlow("")
        private val _selectedGenre = MutableStateFlow(PresetGenreFilter.ALL)
        private val _isSearchVisible = MutableStateFlow(false)

        companion object {
            private const val TAG = "HomeViewModel"
            /** Debounce delay for search input (ms) - wait for user to stop typing */
            private const val SEARCH_DEBOUNCE_MS = 500L
            /** Minimum characters required before search is triggered */
            private const val MIN_SEARCH_LENGTH = 2
        }

        /**
         * Debounced search query to prevent excessive filtering on every keystroke.
         * Only triggers search when query is at least MIN_SEARCH_LENGTH characters.
         */
        private val debouncedSearchQuery =
            _searchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = "",
                )

        init {
            UnifiedLog.i(TAG) { "🏠 HomeViewModel INIT - Creating content flows and paging sources" }

            // ==================== Cache Invalidation Observer ====================
            // When sync completes, HomeContentCache emits invalidation events.
            // We increment the trigger to cause Paging flows to refresh.
            
            homeContentCache.observeInvalidations()
                .onEach { cacheKey ->
                    UnifiedLog.i(TAG) { "📢 Cache invalidation received: ${cacheKey.name} - triggering paging refresh" }
                    _pagingInvalidationTrigger.value = _pagingInvalidationTrigger.value + 1
                }
                .catch { e ->
                    UnifiedLog.e(TAG, e) { "Error observing cache invalidations" }
                }
                .launchIn(viewModelScope)

            // ==================== Special Rows (Flow-based, small limits) ====================
            // Continue Watching and Recently Added use Flow-based loading with limits.
            // They are small, bounded rows (max 30 and 100 items respectively).
            
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
            
            // ==================== Large Catalog Rows use PAGING ====================
            // Movies, Series, Clips, Live are loaded via PagingData flows.
            // See: moviesPagingFlow, seriesPagingFlow, clipsPagingFlow, livePagingFlow
            // Paging handles memory efficiently for catalogs with 40K+ items.
            
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
            
            // Perform actual search when debounced query changes
            // Requires minimum length to prevent single-character noise searches
            debouncedSearchQuery
                .onEach { query ->
                    _state.update { it.copy(searchQuery = query) }
                    if (query.length >= MIN_SEARCH_LENGTH) {
                        _state.update { it.copy(isSearchLoading = true) }
                        try {
                            val results = homeContentRepository.search(query)
                            _state.update { it.copy(searchResults = results, isSearchLoading = false) }
                        } catch (e: Exception) {
                            UnifiedLog.e(TAG) { "Search failed: ${e.message}" }
                            _state.update { it.copy(searchResults = emptyList(), isSearchLoading = false) }
                        }
                    } else {
                        // Clear results when query is too short
                        _state.update { it.copy(searchResults = emptyList(), isSearchLoading = false) }
                    }
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
         * When search is active with results, searchResults contains DB-queried items.
         */
        val filteredState: StateFlow<HomeState> =
            combine(
                state,
                debouncedSearchQuery,
                _selectedGenre,
                _isSearchVisible,
            ) { currentState, query, genre, isSearchVisible ->
                // When search is active, use searchResults from state (populated by DB query)
                // Only special rows (Continue Watching, Recently Added) can be filtered in-memory.
                val baseState = if (query.isBlank() && genre == PresetGenreFilter.ALL) {
                    currentState
                } else {
                    currentState.copy(
                        continueWatchingItems = filterItems(currentState.continueWatchingItems, query, genre),
                        recentlyAddedItems = filterItems(currentState.recentlyAddedItems, query, genre),
                        // searchResults comes from state (DB query)
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

        /**
         * Manually refresh all content rows.
         * 
         * **Behavior:**
         * 1. Resets loading states for special rows (Continue Watching, Recently Added)
         * 2. Increments _pagingInvalidationTrigger to force Paging flows to re-fetch
         * 
         * **Flow-based rows:** Will automatically re-emit from their source flows.
         * **Paging rows:** flatMapLatest catches the trigger change and recreates PagingSource.
         */
        fun refresh() {
            UnifiedLog.i(TAG) { "🔄 REFRESH triggered - invalidating all Paging flows" }
            
            // Reset loading states for special rows
            _state.update { it.copy(
                isContinueWatchingLoading = true,
                isRecentlyAddedLoading = true,
                error = null,
            ) }
            
            // Trigger Paging invalidation - this causes all flatMapLatest flows to re-emit
            _pagingInvalidationTrigger.value = _pagingInvalidationTrigger.value + 1
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
         * NOTE: Only uses special rows. Large catalog genres should be queried from DB.
         */
        private fun updateAvailableGenres() {
            val currentState = _state.value
            // Only use special rows that are in-memory
            // TODO: For full genre list, query DB directly
            val allItems = currentState.continueWatchingItems +
                currentState.recentlyAddedItems
            
            val genres = allItems
                .mapNotNull { it.genres }
                .flatMap { it.split(",", ";") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
            
            _state.update { it.copy(availableGenres = genres) }
        }
    }
