package com.fishit.player.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.library.domain.LibraryCategory
import com.fishit.player.core.library.domain.LibraryContentRepository
import com.fishit.player.core.library.domain.LibraryFilterConfig
import com.fishit.player.core.library.domain.LibraryMediaItem
import com.fishit.player.core.library.domain.LibraryQueryOptions
import com.fishit.player.core.library.domain.LibrarySortDirection
import com.fishit.player.core.library.domain.LibrarySortField
import com.fishit.player.core.library.domain.LibrarySortOption
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    // Sort & Filter state
    val vodSortOption: LibrarySortOption = LibrarySortOption.DEFAULT,
    val seriesSortOption: LibrarySortOption = LibrarySortOption.DEFAULT,
    val vodFilterConfig: LibraryFilterConfig = LibraryFilterConfig.DEFAULT,
    val seriesFilterConfig: LibraryFilterConfig = LibraryFilterConfig.DEFAULT,
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

    /** Current sort option for active tab */
    val currentSortOption: LibrarySortOption
        get() = if (currentTab == LibraryTab.VOD) vodSortOption else seriesSortOption

    /** Current filter config for active tab */
    val currentFilterConfig: LibraryFilterConfig
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
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val libraryContentRepository: LibraryContentRepository,
    ) : ViewModel() {
        companion object {
            private const val TAG = "LibraryViewModel"
        }

        private val _selectedVodCategory = MutableStateFlow<String?>(null)
        private val _selectedSeriesCategory = MutableStateFlow<String?>(null)
        private val _currentTab = MutableStateFlow(LibraryTab.VOD)
        private val _searchQuery = MutableStateFlow("")
        private val _isSearchActive = MutableStateFlow(false)
        private val _searchResults = MutableStateFlow<List<LibraryMediaItem>>(emptyList())

        // Sort & Filter state
        private val _vodSortOption = MutableStateFlow(LibrarySortOption.DEFAULT)
        private val _seriesSortOption = MutableStateFlow(LibrarySortOption.DEFAULT)
        private val _vodFilterConfig = MutableStateFlow(LibraryFilterConfig.DEFAULT)
        private val _seriesFilterConfig = MutableStateFlow(LibraryFilterConfig.DEFAULT)
        private val _availableGenres = MutableStateFlow<Set<String>>(emptySet())
        private val _yearRange = MutableStateFlow<Pair<Int, Int>?>(null)

        init {
            loadMetadata()
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

        // ===== Sort Actions =====

        /** Update sort option for VOD */
        fun updateVodSort(sort: LibrarySortOption) {
            _vodSortOption.value = sort
        }

        /** Update sort option for Series */
        fun updateSeriesSort(sort: LibrarySortOption) {
            _seriesSortOption.value = sort
        }

        /** Update sort for current tab */
        fun updateSort(sort: LibrarySortOption) {
            when (_currentTab.value) {
                LibraryTab.VOD -> updateVodSort(sort)
                LibraryTab.SERIES -> updateSeriesSort(sort)
            }
        }

        // ===== Filter Actions =====

        /** Update filter config for VOD */
        fun updateVodFilter(filter: LibraryFilterConfig) {
            _vodFilterConfig.value = filter
        }

        /** Update filter config for Series */
        fun updateSeriesFilter(filter: LibraryFilterConfig) {
            _seriesFilterConfig.value = filter
        }

        /** Update filter for current tab */
        fun updateFilter(filter: LibraryFilterConfig) {
            when (_currentTab.value) {
                LibraryTab.VOD -> updateVodFilter(filter)
                LibraryTab.SERIES -> updateSeriesFilter(filter)
            }
        }

        /** Reset filters for current tab */
        fun resetFilters() {
            when (_currentTab.value) {
                LibraryTab.VOD -> _vodFilterConfig.value = LibraryFilterConfig.DEFAULT
                LibraryTab.SERIES -> _seriesFilterConfig.value = LibraryFilterConfig.DEFAULT
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

        /** Perform search */
        fun search(query: String) {
            _searchQuery.value = query
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                return
            }

            viewModelScope.launch {
                try {
                    val results = libraryContentRepository.search(query)
                    _searchResults.value = results
                } catch (e: Exception) {
                    UnifiedLog.e(TAG) { "Search error: ${e.message}" }
                    _searchResults.value = emptyList()
                }
            }
        }
    }

/**
 * Intermediate data class for combine chain
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
    val searchResults: List<LibraryMediaItem> = emptyList(),
    val vodSortOption: LibrarySortOption = LibrarySortOption.DEFAULT,
    val seriesSortOption: LibrarySortOption = LibrarySortOption.DEFAULT,
    val vodFilterConfig: LibraryFilterConfig = LibraryFilterConfig.DEFAULT,
    val seriesFilterConfig: LibraryFilterConfig = LibraryFilterConfig.DEFAULT,
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
            searchResults = searchResults,
            vodSortOption = vodSortOption,
            seriesSortOption = seriesSortOption,
            vodFilterConfig = vodFilterConfig,
            seriesFilterConfig = seriesFilterConfig,
            availableGenres = availableGenres,
            yearRange = yearRange,
        )
}
