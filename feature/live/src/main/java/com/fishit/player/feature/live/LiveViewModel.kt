package com.fishit.player.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.live.domain.LiveCategory
import com.fishit.player.core.live.domain.LiveChannel
import com.fishit.player.core.live.domain.LiveContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Live TV feature screen.
 *
 * Provides:
 * - All channels / filtered by category
 * - Category list for filtering
 * - Recent channels section
 * - Favorite channels section
 * - Search functionality with debouncing
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class LiveViewModel
    @Inject
    constructor(
        private val repository: LiveContentRepository,
    ) : ViewModel() {
        companion object {
            /** Debounce delay for search input (ms) - wait for user to stop typing */
            private const val SEARCH_DEBOUNCE_MS = 500L
            /** Minimum characters required before search is triggered */
            private const val MIN_SEARCH_LENGTH = 2
        }

        /** Selected category filter (null = all) */
        private val _selectedCategoryId = MutableStateFlow<String?>(null)
        val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

        /** Search query */
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        /**
         * Debounced search query - waits 300ms after typing stops.
         * Prevents UI blocking by not triggering search on every keystroke.
         */
        private val debouncedSearchQuery =
            _searchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()

        /** Search results */
        private val _searchResults = MutableStateFlow<List<LiveChannel>>(emptyList())
        val searchResults: StateFlow<List<LiveChannel>> = _searchResults.asStateFlow()

        /** Whether search mode is active */
        private val _isSearchActive = MutableStateFlow(false)
        val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

        /** Current view mode */
        private val _viewMode = MutableStateFlow(ViewMode.ALL)
        val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

        /** Categories */
        val categories: StateFlow<List<LiveCategory>> =
            repository
                .observeCategories()
                .catch { emit(emptyList()) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /** Channels filtered by selected category */
        @OptIn(ExperimentalCoroutinesApi::class)
        val channels: StateFlow<List<LiveChannel>> =
            combine(
                _selectedCategoryId,
                _viewMode,
            ) { categoryId, mode ->
                categoryId to mode
            }.flatMapLatest { (categoryId, mode) ->
                when (mode) {
                    ViewMode.ALL -> repository.observeChannels(categoryId)
                    ViewMode.FAVORITES -> repository.observeFavorites()
                }
            }.catch {
                emit(emptyList())
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        /** Recent channels */
        private val _recentChannels = MutableStateFlow<List<LiveChannel>>(emptyList())
        val recentChannels: StateFlow<List<LiveChannel>> = _recentChannels.asStateFlow()

        init {
            loadRecentChannels()
            // Start collecting debounced search queries
            collectDebouncedSearchQuery()
        }

        private fun loadRecentChannels() {
            viewModelScope.launch {
                try {
                    _recentChannels.value = repository.getRecentChannels()
                } catch (e: Exception) {
                    _recentChannels.value = emptyList()
                }
            }
        }

        /**
         * Select a category to filter channels.
         *
         * @param categoryId Category ID or null for all
         */
        fun selectCategory(categoryId: String?) {
            _selectedCategoryId.value = categoryId
        }

        /**
         * Switch view mode.
         */
        fun setViewMode(mode: ViewMode) {
            _viewMode.value = mode
        }

        /**
         * Update search query (debounced).
         * The actual search is triggered by [debouncedSearchQuery] after 500ms of inactivity.
         * Requires at least [MIN_SEARCH_LENGTH] characters.
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
                            _searchResults.value = repository.search(query)
                        } catch (e: Exception) {
                            _searchResults.value = emptyList()
                        }
                    }
                }
                .launchIn(viewModelScope)
        }

        /**
         * Toggle search mode.
         */
        fun setSearchActive(active: Boolean) {
            _isSearchActive.value = active
            if (!active) {
                _searchQuery.value = ""
                _searchResults.value = emptyList()
            }
        }

        /**
         * Toggle favorite status for a channel.
         */
        fun toggleFavorite(channel: LiveChannel) {
            viewModelScope.launch {
                repository.setFavorite(channel.id, !channel.isFavorite)
            }
        }

        /**
         * Called when a channel is played (for recent channels tracking).
         */
        fun onChannelPlayed(channel: LiveChannel) {
            viewModelScope.launch {
                repository.recordWatched(channel.id)
                loadRecentChannels()
            }
        }

        enum class ViewMode {
            ALL,
            FAVORITES,
        }
    }
