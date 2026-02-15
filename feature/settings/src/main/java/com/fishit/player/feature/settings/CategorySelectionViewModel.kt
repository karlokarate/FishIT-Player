package com.fishit.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import com.fishit.player.core.catalogsync.XtreamCategoryPreloader
import com.fishit.player.core.model.repository.NxCategorySelectionRepository
import com.fishit.player.core.model.repository.NxCategorySelectionRepository.CategorySelection
import com.fishit.player.core.model.repository.NxCategorySelectionRepository.XtreamCategoryType
import com.fishit.player.core.model.repository.NxSourceAccountRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Category Selection screen.
 *
 * Part of Issue #669 - Sync by Category Implementation.
 *
 * **Architecture (AGENTS.md Section 4 compliant):**
 * - Uses [XtreamCategoryPreloader] to trigger category fetch (from `core:catalog-sync`)
 * - Reads categories from [NxCategorySelectionRepository] (domain repository)
 * - DOES NOT import pipeline types directly - layer boundary respected
 * - Categories displayed from repository, not from preloader state
 */
@HiltViewModel
class CategorySelectionViewModel
    @Inject
    constructor(
        private val categoryPreloader: XtreamCategoryPreloader,
        private val categoryRepository: NxCategorySelectionRepository,
        private val sourceAccountRepository: NxSourceAccountRepository,
        private val sourceActivationStore: SourceActivationStore,
        private val catalogSyncWorkScheduler: CatalogSyncWorkScheduler,
    ) : ViewModel() {
        companion object {
            private const val TAG = "CategorySelectionVM"
        }

        private val _state = MutableStateFlow(CategorySelectionUiState())
        val state: StateFlow<CategorySelectionUiState> = _state.asStateFlow()

        init {
            loadAccountKey()
        }

        // =========================================================================
        // Initialization
        // =========================================================================

        private fun loadAccountKey() {
            viewModelScope.launch {
                val accountKey = findXtreamAccountKey()
                if (accountKey != null) {
                    _state.update { it.copy(accountKey = accountKey) }
                    observeCategories(accountKey)
                    triggerPreload()
                } else {
                    _state.update { it.copy(error = "Kein Xtream-Konto aktiv") }
                }
            }
        }

        private fun observeCategories(accountKey: String) {
            // Observe VOD categories
            viewModelScope.launch {
                categoryRepository.observeByType(accountKey, XtreamCategoryType.VOD).collect { categories ->
                    _state.update { it.copy(vodCategories = categories) }
                }
            }
            // Observe Series categories
            viewModelScope.launch {
                categoryRepository.observeByType(accountKey, XtreamCategoryType.SERIES).collect { categories ->
                    _state.update { it.copy(seriesCategories = categories) }
                }
            }
            // Observe Live categories
            viewModelScope.launch {
                categoryRepository.observeByType(accountKey, XtreamCategoryType.LIVE).collect { categories ->
                    _state.update { it.copy(liveCategories = categories) }
                }
            }
        }

        // =========================================================================
        // Actions
        // =========================================================================

        private fun triggerPreload() {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                try {
                    categoryPreloader.preloadCategories(forceRefresh = false)
                    _state.update { it.copy(isLoading = false, error = null) }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG) { "Preload failed: ${e.message}" }
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
            }
        }

        fun refreshCategories() {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                try {
                    categoryPreloader.preloadCategories(forceRefresh = true)
                    _state.update { it.copy(isLoading = false, error = null) }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG) { "Refresh failed: ${e.message}" }
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
            }
        }

        fun toggleCategory(
            categoryId: String,
            categoryType: XtreamCategoryType,
            isSelected: Boolean,
        ) {
            val accountKey = _state.value.accountKey ?: return
            viewModelScope.launch {
                categoryRepository.setSelected(
                    accountKey = accountKey,
                    categoryType = categoryType,
                    sourceCategoryId = categoryId,
                    isSelected = isSelected,
                )
            }
        }

        fun selectAll(categoryType: XtreamCategoryType) {
            val accountKey = _state.value.accountKey ?: return
            viewModelScope.launch {
                categoryRepository.selectAll(accountKey, categoryType)
            }
        }

        fun deselectAll(categoryType: XtreamCategoryType) {
            val accountKey = _state.value.accountKey ?: return
            viewModelScope.launch {
                categoryRepository.deselectAll(accountKey, categoryType)
            }
        }

        fun setSelectedTab(tab: CategoryTab) {
            _state.update { it.copy(selectedTab = tab) }
        }

        /**
         * Mark category selection as complete and trigger a catalog re-sync.
         *
         * XOC-4: setCategorySelectionComplete(true) opens the gate for sync.
         * XOC-3: Worker checks isCategorySelectionComplete() before syncing.
         */
        fun saveAndSync() {
            val accountKey = _state.value.accountKey ?: return
            viewModelScope.launch {
                try {
                    categoryRepository.setCategorySelectionComplete(accountKey, true)
                    catalogSyncWorkScheduler.enqueueExpertSyncNow()
                    UnifiedLog.i(TAG) { "Category selection saved, sync triggered" }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG) { "saveAndSync failed: ${e.message}" }
                    _state.update { it.copy(error = "Sync konnte nicht gestartet werden") }
                }
            }
        }

        // =========================================================================
        // Private Helpers
        // =========================================================================

        private suspend fun findXtreamAccountKey(): String? {
            // First check if Xtream is active
            val snapshot = sourceActivationStore.getCurrentSnapshot()
            if (!snapshot.xtream.isActive) {
                return null
            }

            // Find the active Xtream account
            val accounts = sourceAccountRepository.observeAll().first()
            return accounts.firstOrNull { it.sourceType == SourceType.XTREAM }?.accountKey
        }
    }

/**
 * UI state for Category Selection screen.
 *
 * Uses [CategorySelection] from repository - no pipeline types.
 */
data class CategorySelectionUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val accountKey: String? = null,
    val vodCategories: List<CategorySelection> = emptyList(),
    val seriesCategories: List<CategorySelection> = emptyList(),
    val liveCategories: List<CategorySelection> = emptyList(),
    val selectedTab: CategoryTab = CategoryTab.VOD,
)

enum class CategoryTab {
    VOD,
    SERIES,
    LIVE,
}
