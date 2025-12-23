package com.fishit.player.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.feature.library.domain.LibraryCategory
import com.fishit.player.feature.library.domain.LibraryContentRepository
import com.fishit.player.feature.library.domain.LibraryMediaItem
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Library tab selection.
 */
enum class LibraryTab {
    VOD, SERIES
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
    val error: String? = null
) {
    /** Items to display based on current tab */
    val currentItems: List<LibraryMediaItem>
        get() = when {
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

    /** True if there is content to display */
    val hasContent: Boolean
        get() = vodItems.isNotEmpty() || seriesItems.isNotEmpty()
}

/**
 * LibraryViewModel - Manages Library screen state.
 *
 * Provides VOD and Series browsing with category filtering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryContentRepository: LibraryContentRepository
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

    /** VOD items filtered by selected category */
    private val vodItems = _selectedVodCategory.flatMapLatest { categoryId ->
        libraryContentRepository.observeVod(categoryId)
            .catch { e ->
                UnifiedLog.e(TAG) { "Error loading VOD: ${e.message}" }
                emit(emptyList())
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Series items filtered by selected category */
    private val seriesItems = _selectedSeriesCategory.flatMapLatest { categoryId ->
        libraryContentRepository.observeSeries(categoryId)
            .catch { e ->
                UnifiedLog.e(TAG) { "Error loading series: ${e.message}" }
                emit(emptyList())
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** VOD categories */
    private val vodCategories = libraryContentRepository.observeVodCategories()
        .catch { e ->
            UnifiedLog.e(TAG) { "Error loading VOD categories: ${e.message}" }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Series categories */
    private val seriesCategories = libraryContentRepository.observeSeriesCategories()
        .catch { e ->
            UnifiedLog.e(TAG) { "Error loading series categories: ${e.message}" }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Combined UI state */
    val state: StateFlow<LibraryState> = combine(
        vodItems,
        seriesItems,
        vodCategories,
        seriesCategories,
        _currentTab,
        _selectedVodCategory,
        _selectedSeriesCategory,
        _isSearchActive,
        _searchResults
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        LibraryState(
            isLoading = false,
            vodItems = values[0] as List<LibraryMediaItem>,
            seriesItems = values[1] as List<LibraryMediaItem>,
            vodCategories = values[2] as List<LibraryCategory>,
            seriesCategories = values[3] as List<LibraryCategory>,
            currentTab = values[4] as LibraryTab,
            selectedVodCategory = values[5] as String?,
            selectedSeriesCategory = values[6] as String?,
            isSearchActive = values[7] as Boolean,
            searchResults = values[8] as List<LibraryMediaItem>
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LibraryState()
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
