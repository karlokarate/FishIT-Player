package com.fishit.player.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fishit.player.core.library.domain.LibraryCategory
import com.fishit.player.core.library.domain.LibraryContentRepository
import com.fishit.player.core.library.domain.LibraryMediaItem
import com.fishit.player.core.library.domain.LibraryPagingConfig
import com.fishit.player.core.library.domain.LibraryQueryOptions
import com.fishit.player.core.model.filter.FilterConfig
import com.fishit.player.core.model.filter.FilterCriterion
import com.fishit.player.core.model.sort.SortDirection
import com.fishit.player.core.model.sort.SortField
import com.fishit.player.core.model.sort.SortOption
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Library tab selection.
 */
enum class LibraryTab {
    VOD,
    SERIES,
}

/**
 * Library screen UI state.
 */
data class LibraryState(
    val isLoading: Boolean = true,
    val currentTab: LibraryTab = LibraryTab.VOD,
    val vodItems: List<LibraryMediaItem> = emptyList(),
    val seriesItems: List<LibraryMediaItem> = emptyList(),
    val vodCategories: List<LibraryCategory> = emptyList(),
    val seriesCategories: List<LibraryCategory> = emptyList(),
    val selectedVodCategory: String? = null,
    val selectedSeriesCategory: String? = null,
    val searchQuery: String = "",
    val searchResults: List<LibraryMediaItem> = emptyList(),
    val isSearchActive: Boolean = false,
    val error: String? = null,
    // Sort & Filter state (using unified core/model types)
    val vodSortOption: SortOption = SortOption.DEFAULT,
    val seriesSortOption: SortOption = SortOption.DEFAULT,
    val vodFilterConfig: FilterConfig = FilterConfig.DEFAULT,
    val seriesFilterConfig: FilterConfig = FilterConfig.DEFAULT,
    val availableGenres: Set<String> = emptySet(),
    val yearRange: Pair<Int, Int>? = null,
) {
    /** Items to display based on current tab */
    val currentItems: List<LibraryMediaItem>
        get() =
            when {
                isSearchActive -> searchResults
                currentTab == LibraryTab.VOD -> vodItems
                else -> seriesItems
            }

    /** Categories for current tab */
    val currentCategories: List<LibraryCategory>
        get() = if (currentTab == LibraryTab.VOD) vodCategories else seriesCategories

    /** Selected category for current tab */
    val currentSelectedCategory: String?
        get() = if (currentTab == LibraryTab.VOD) selectedVodCategory else selectedSeriesCategory

    /** Current sort option for active tab (unified core/model type) */
    val currentSortOption: SortOption
        get() = if (currentTab == LibraryTab.VOD) vodSortOption else seriesSortOption

    /** Current filter config for active tab (unified core/model type) */
    val currentFilterConfig: FilterConfig
        get() = if (currentTab == LibraryTab.VOD) vodFilterConfig else seriesFilterConfig

    /** True if there is content to display */
    val hasContent: Boolean
        get() = vodItems.isNotEmpty() || seriesItems.isNotEmpty()
}

/**
 * LibraryViewModel - Manages Library screen state.
 *
 * Provides VOD and Series browsing with category filtering,
 * sorting, and content filtering.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val libraryContentRepository: LibraryContentRepository,
    ) : ViewModel() {
        companion object {
            private const val TAG = "LibraryViewModel"
            /** Debounce delay for search input (ms) - wait for user to stop typing */
            private const val SEARCH_DEBOUNCE_MS = 500L
            /** Minimum characters required before search is triggered */
            private const val MIN_SEARCH_LENGTH = 2
        }

        private val _selectedVodCategory = MutableStateFlow<String?>(null)
        private val _selectedSeriesCategory = MutableStateFlow<String?>(null)
        private val _currentTab = MutableStateFlow(LibraryTab.VOD)
        private val _searchQuery = MutableStateFlow("")
        private val _isSearchActive = MutableStateFlow(false)
        private val _searchResults = MutableStateFlow<List<LibraryMediaItem>>(emptyList())

        /**
         * Debounced search query - waits 300ms after typing stops.
         * Prevents UI blocking by not triggering search on every keystroke.
         */
        private val debouncedSearchQuery =
            _searchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()

        // Sort & Filter state (using unified core/model types)
        private val _vodSortOption = MutableStateFlow(SortOption.DEFAULT)
        private val _seriesSortOption = MutableStateFlow(SortOption.DEFAULT)
        private val _vodFilterConfig = MutableStateFlow(FilterConfig.DEFAULT)
        private val _seriesFilterConfig = MutableStateFlow(FilterConfig.DEFAULT)
        private val _availableGenres = MutableStateFlow<Set<String>>(emptySet())
        private val _yearRange = MutableStateFlow<Pair<Int, Int>?>(null)

        init {
            loadMetadata()
            // Start collecting debounced search queries
            collectDebouncedSearchQuery()
        }

        /** Load genres and year range for filter UI */
        private fun loadMetadata() {
            viewModelScope.launch {
                try {
                    _availableGenres.value = libraryContentRepository.getAllGenres()
                    _yearRange.value = libraryContentRepository.getYearRange()
                } catch (e: Exception) {
                    UnifiedLog.e(TAG) { "Error loading metadata: ${e.message}" }
                }
            }
        }

        /** VOD items with sort and filter options */
        private val vodItems =
            combine(
                _selectedVodCategory,
                _vodSortOption,
                _vodFilterConfig,
            ) { categoryId, sort, filter ->
                Triple(categoryId, sort, filter)
            }.flatMapLatest { (categoryId, sort, filter) ->
                val options = LibraryQueryOptions(sort = sort, filter = filter)
                libraryContentRepository
                    .observeVodWithOptions(categoryId, options)
                    .catch { e ->
                        UnifiedLog.e(TAG) { "Error loading VOD: ${e.message}" }
                        emit(emptyList())
                    }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        /** Series items with sort and filter options */
        private val seriesItems =
            combine(
                _selectedSeriesCategory,
                _seriesSortOption,
                _seriesFilterConfig,
            ) { categoryId, sort, filter ->
                Triple(categoryId, sort, filter)
            }.flatMapLatest { (categoryId, sort, filter) ->
                val options = LibraryQueryOptions(sort = sort, filter = filter)
                libraryContentRepository
                    .observeSeriesWithOptions(categoryId, options)
                    .catch { e ->
                        UnifiedLog.e(TAG) { "Error loading series: ${e.message}" }
                        emit(emptyList())
                    }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // ==================== Paging 3 (Infinite Scroll) ====================

        /**
         * Paginated VOD items for infinite scroll.
         * Use with collectAsLazyPagingItems() in Compose.
         *
         * Automatically re-emits when sort/filter changes.
         */
        val vodPagingFlow: Flow<PagingData<LibraryMediaItem>> =
            combine(
                _vodSortOption,
                _vodFilterConfig,
            ) { sort, filter ->
                Pair(sort, filter)
            }.flatMapLatest { (sort, filter) ->
                val options = LibraryQueryOptions(sort = sort, filter = filter)
                libraryContentRepository.getVodPagingData(
                    options = options,
                    config = LibraryPagingConfig.DEFAULT,
                )
            }.cachedIn(viewModelScope)

        /**
         * Paginated Series items for infinite scroll.
         * Use with collectAsLazyPagingItems() in Compose.
         */
        val seriesPagingFlow: Flow<PagingData<LibraryMediaItem>> =
            combine(
                _seriesSortOption,
                _seriesFilterConfig,
            ) { sort, filter ->
                Pair(sort, filter)
            }.flatMapLatest { (sort, filter) ->
                val options = LibraryQueryOptions(sort = sort, filter = filter)
                libraryContentRepository.getSeriesPagingData(
                    options = options,
                    config = LibraryPagingConfig.DEFAULT,
                )
            }.cachedIn(viewModelScope)

        /** VOD categories */
        private val vodCategories =
            libraryContentRepository
                .observeVodCategories()
                .catch { e ->
                    UnifiedLog.e(TAG) { "Error loading VOD categories: ${e.message}" }
                    emit(emptyList())
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        /** Series categories */
        private val seriesCategories =
            libraryContentRepository
                .observeSeriesCategories()
                .catch { e ->
                    UnifiedLog.e(TAG) { "Error loading series categories: ${e.message}" }
                    emit(emptyList())
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        /** Combined UI state */
        val state: StateFlow<LibraryState> =
            combine(
                vodItems,
                seriesItems,
                vodCategories,
                seriesCategories,
                _currentTab,
            ) { vodList, seriesList, vodCats, seriesCats, tab ->
                CombinedBase(vodList, seriesList, vodCats, seriesCats, tab)
            }.combine(_selectedVodCategory) { base, vodCat ->
                base.copy(selectedVodCategory = vodCat)
            }.combine(_selectedSeriesCategory) { base, seriesCat ->
                base.copy(selectedSeriesCategory = seriesCat)
            }.combine(_isSearchActive) { base, searching ->
                base.copy(isSearchActive = searching)
            }.combine(_searchQuery) { base, query ->
                base.copy(searchQuery = query)
            }.combine(_searchResults) { base, results ->
                base.copy(searchResults = results)
            }.combine(_vodSortOption) { base, sort ->
                base.copy(vodSortOption = sort)
            }.combine(_seriesSortOption) { base, sort ->
                base.copy(seriesSortOption = sort)
            }.combine(_vodFilterConfig) { base, filter ->
                base.copy(vodFilterConfig = filter)
            }.combine(_seriesFilterConfig) { base, filter ->
                base.copy(seriesFilterConfig = filter)
            }.combine(_availableGenres) { base, genres ->
                base.copy(availableGenres = genres)
            }.combine(_yearRange) { base, range ->
                base.toLibraryState(range)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                LibraryState(),
            )

        /** Switch between VOD and Series tabs */
        fun selectTab(tab: LibraryTab) {
            _currentTab.value = tab
            _isSearchActive.value = false
        }

        /** Select a category for filtering */
        fun selectCategory(categoryId: String?) {
            when (_currentTab.value) {
                LibraryTab.VOD -> _selectedVodCategory.value = categoryId
                LibraryTab.SERIES -> _selectedSeriesCategory.value = categoryId
            }
        }

        // ===== Sort Actions (using unified core/model types) =====

        /** Update sort option for VOD */
        fun updateVodSort(sort: SortOption) {
            _vodSortOption.value = sort
        }

        /** Update sort option for Series */
        fun updateSeriesSort(sort: SortOption) {
            _seriesSortOption.value = sort
        }

        /** Update sort for current tab */
        fun updateSort(sort: SortOption) {
            when (_currentTab.value) {
                LibraryTab.VOD -> updateVodSort(sort)
                LibraryTab.SERIES -> updateSeriesSort(sort)
            }
        }

        // ===== Filter Actions (using unified core/model types) =====

        /** Update filter config for VOD */
        fun updateVodFilter(filter: FilterConfig) {
            _vodFilterConfig.value = filter
        }

        /** Update filter config for Series */
        fun updateSeriesFilter(filter: FilterConfig) {
            _seriesFilterConfig.value = filter
        }

        /** Update filter for current tab */
        fun updateFilter(filter: FilterConfig) {
            when (_currentTab.value) {
                LibraryTab.VOD -> updateVodFilter(filter)
                LibraryTab.SERIES -> updateSeriesFilter(filter)
            }
        }

        /** Reset filters for current tab */
        fun resetFilters() {
            when (_currentTab.value) {
                LibraryTab.VOD -> _vodFilterConfig.value = FilterConfig.DEFAULT
                LibraryTab.SERIES -> _seriesFilterConfig.value = FilterConfig.DEFAULT
            }
        }

        // ===== Search Actions =====

        /** Start search mode */
        fun startSearch() {
            _isSearchActive.value = true
        }

        /** Cancel search and return to browsing */
        fun cancelSearch() {
            _isSearchActive.value = false
            _searchQuery.value = ""
            _searchResults.value = emptyList()
        }

        /** Perform search (debounced).
         * The actual search is triggered by [debouncedSearchQuery] after 300ms of inactivity.
         */
        fun search(query: String) {
            _searchQuery.value = query
        }

        /**
         * Collect debounced search queries and perform search.
         * Prevents UI blocking by waiting 500ms after user stops typing.
         * Requires minimum length to prevent single-character noise searches.
         */
        private fun collectDebouncedSearchQuery() {
            debouncedSearchQuery
                .onEach { query ->
                    if (query.length < MIN_SEARCH_LENGTH) {
                        // Clear results when query is too short
                        _searchResults.value = emptyList()
                    } else {
                        try {
                            val results = libraryContentRepository.search(query)
                            _searchResults.value = results
                        } catch (e: Exception) {
                            UnifiedLog.e(TAG) { "Search error: ${e.message}" }
                            _searchResults.value = emptyList()
                        }
                    }
                }
                .launchIn(viewModelScope)
        }
    }

/**
 * Intermediate data class for combine chain.
 * Uses unified core/model types for sort and filter.
 */
private data class CombinedBase(
    val vodItems: List<LibraryMediaItem> = emptyList(),
    val seriesItems: List<LibraryMediaItem> = emptyList(),
    val vodCategories: List<LibraryCategory> = emptyList(),
    val seriesCategories: List<LibraryCategory> = emptyList(),
    val currentTab: LibraryTab = LibraryTab.VOD,
    val selectedVodCategory: String? = null,
    val selectedSeriesCategory: String? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<LibraryMediaItem> = emptyList(),
    val vodSortOption: SortOption = SortOption.DEFAULT,
    val seriesSortOption: SortOption = SortOption.DEFAULT,
    val vodFilterConfig: FilterConfig = FilterConfig.DEFAULT,
    val seriesFilterConfig: FilterConfig = FilterConfig.DEFAULT,
    val availableGenres: Set<String> = emptySet(),
) {
    fun toLibraryState(yearRange: Pair<Int, Int>?) =
        LibraryState(
            isLoading = false,
            vodItems = vodItems,
            seriesItems = seriesItems,
            vodCategories = vodCategories,
            seriesCategories = seriesCategories,
            currentTab = currentTab,
            selectedVodCategory = selectedVodCategory,
            selectedSeriesCategory = selectedSeriesCategory,
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            searchResults = searchResults,
            vodSortOption = vodSortOption,
            seriesSortOption = seriesSortOption,
            vodFilterConfig = vodFilterConfig,
            seriesFilterConfig = seriesFilterConfig,
            availableGenres = availableGenres,
            yearRange = yearRange,
        )
}
