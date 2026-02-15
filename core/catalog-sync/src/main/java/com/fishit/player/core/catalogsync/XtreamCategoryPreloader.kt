package com.fishit.player.core.catalogsync

import com.fishit.player.core.model.repository.NxCategorySelectionRepository
import com.fishit.player.core.model.repository.NxCategorySelectionRepository.CategorySelection
import com.fishit.player.core.model.repository.NxCategorySelectionRepository.XtreamCategoryType
import com.fishit.player.core.model.repository.NxSourceAccountRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogConfig
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogPipeline
import com.fishit.player.pipeline.xtream.catalog.XtreamCategoryInfo
import com.fishit.player.pipeline.xtream.catalog.XtreamCategoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preloads Xtream categories for UI display in category selection screens.
 *
 * **Purpose:**
 * - Fetches categories from Xtream server ONCE after source activation
 * - Caches categories in memory for instant UI access
 * - **Persists categories to [NxCategorySelectionRepository]** for UI consumption
 * - Provides reactive StateFlow for preload status
 *
 * **Data Flow (Issue #669):**
 * ```
 * XtreamCatalogPipeline.fetchCategories()
 *         ↓
 * XtreamCategoryPreloader (this class)
 *         ↓
 * NxCategorySelectionRepository (for UI)
 *         ↓
 * CategorySelectionScreen (feature:settings)
 * ```
 *
 * **When to call [preloadCategories]:**
 * - After Xtream source is activated (credentials validated)
 * - When user manually requests category refresh
 *
 * Contract: Part of Issue #669 - Sync by Category implementation.
 */
@Singleton
class XtreamCategoryPreloader
    @Inject
    constructor(
        private val xtreamCatalogPipeline: XtreamCatalogPipeline,
        private val categoryRepository: NxCategorySelectionRepository,
        private val sourceAccountRepository: NxSourceAccountRepository,
    ) {
        private val _state = MutableStateFlow<XtreamCategoryPreloadState>(XtreamCategoryPreloadState.Idle)

        /**
         * Current preload state as a reactive [StateFlow].
         *
         * Observe this in UI to show loading/error states.
         * For actual category data, observe [NxCategorySelectionRepository] instead.
         */
        val state: StateFlow<XtreamCategoryPreloadState> = _state.asStateFlow()

        /**
         * Get current state synchronously.
         */
        fun getCurrentState(): XtreamCategoryPreloadState = _state.value

        /**
         * Get cached categories if available.
         *
         * @return Cached categories or null if not loaded/error
         */
        fun getCachedCategories(): XtreamCachedCategories? {
            val current = _state.value
            return if (current is XtreamCategoryPreloadState.Success) {
                current.categories
            } else {
                null
            }
        }

        /**
         * Preload categories from Xtream server and persist to repository.
         *
         * Updates [state] flow with Loading → Success/Error transitions.
         * On success, categories are persisted to [NxCategorySelectionRepository].
         *
         * **Precondition:** XtreamApiClient must be initialized with valid credentials
         * before calling this method. Call this AFTER [XtreamSessionBootstrap.start()]
         * completes successfully.
         *
         * @param forceRefresh If true, fetches even if categories are already cached
         */
        suspend fun preloadCategories(forceRefresh: Boolean = false) {
            val currentState = _state.value

            // Skip if already loaded and not forcing refresh
            if (!forceRefresh && currentState is XtreamCategoryPreloadState.Success) {
                UnifiedLog.d(TAG) { "Categories already cached, skipping preload" }
                return
            }

            // Skip if already loading
            if (currentState is XtreamCategoryPreloadState.Loading) {
                UnifiedLog.d(TAG) { "Category preload already in progress" }
                return
            }

            // Get Xtream account key for repository storage
            val accountKey = getXtreamAccountKey()
            if (accountKey == null) {
                val errorMsg = "No Xtream account found - cannot preload categories"
                UnifiedLog.w(TAG) { errorMsg }
                _state.value = XtreamCategoryPreloadState.Error(message = errorMsg)
                return
            }

            UnifiedLog.i(TAG) { "Starting category preload (forceRefresh=$forceRefresh, accountKey=${accountKey.take(8)}...)" }
            _state.value = XtreamCategoryPreloadState.Loading

            // Minimal config - categories don't need scan params
            val config = XtreamCatalogConfig.DEFAULT

            when (val result = xtreamCatalogPipeline.fetchCategories(config)) {
                is XtreamCategoryResult.Success -> {
                    val cached =
                        XtreamCachedCategories(
                            vodCategories = result.vodCategories,
                            seriesCategories = result.seriesCategories,
                            liveCategories = result.liveCategories,
                            timestampMs = System.currentTimeMillis(),
                        )
                    _state.value = XtreamCategoryPreloadState.Success(cached)

                    // Persist to repository for UI consumption
                    persistCategoriesToRepository(accountKey, result)

                    UnifiedLog.i(TAG) {
                        "Categories preloaded & persisted: vod=${result.vodCategories.size}, " +
                            "series=${result.seriesCategories.size}, live=${result.liveCategories.size}"
                    }
                }
                is XtreamCategoryResult.Error -> {
                    _state.value =
                        XtreamCategoryPreloadState.Error(
                            message = result.message,
                            cause = result.cause,
                        )
                    UnifiedLog.e(TAG) { "Category preload failed: ${result.message}" }
                }
            }
        }

        /**
         * Clear cached categories.
         *
         * Call this when:
         * - User logs out of Xtream
         * - Credentials are invalidated
         */
        fun clearCache() {
            _state.value = XtreamCategoryPreloadState.Idle
            UnifiedLog.d(TAG) { "Category cache cleared" }
        }

        // =========================================================================
        // Private Helpers
        // =========================================================================

        private suspend fun getXtreamAccountKey(): String? {
            val accounts = sourceAccountRepository.observeAll().first()
            return accounts.firstOrNull { it.sourceType == SourceType.XTREAM }?.accountKey
        }

        /**
         * Persist fetched categories to the repository.
         *
         * New categories are added with `isSelected = true` by default.
         * Existing categories preserve their selection state.
         */
        private suspend fun persistCategoriesToRepository(
            accountKey: String,
            result: XtreamCategoryResult.Success,
        ) {
            // Get existing selections to preserve user choices
            val existingSelections =
                categoryRepository
                    .observeForAccount(accountKey)
                    .first()
                    .associateBy { it.selectionKey }

            val allCategories =
                buildList {
                    addAll(
                        result.vodCategories.mapIndexed { index, cat ->
                            cat.toCategorySelection(accountKey, XtreamCategoryType.VOD, index, existingSelections)
                        },
                    )
                    addAll(
                        result.seriesCategories.mapIndexed { index, cat ->
                            cat.toCategorySelection(accountKey, XtreamCategoryType.SERIES, index, existingSelections)
                        },
                    )
                    addAll(
                        result.liveCategories.mapIndexed { index, cat ->
                            cat.toCategorySelection(accountKey, XtreamCategoryType.LIVE, index, existingSelections)
                        },
                    )
                }

            categoryRepository.upsertAll(allCategories)
            UnifiedLog.d(TAG) { "Persisted ${allCategories.size} categories to repository" }
        }

        /**
         * Convert pipeline category to domain category selection.
         *
         * Preserves existing selection state if category was previously stored.
         */
        private fun XtreamCategoryInfo.toCategorySelection(
            accountKey: String,
            categoryType: XtreamCategoryType,
            sortIndex: Int,
            existingSelections: Map<String, CategorySelection>,
        ): CategorySelection {
            val key =
                NxCategorySelectionRepository.buildSelectionKey(
                    accountKey = accountKey,
                    categoryType = categoryType,
                    sourceCategoryId = categoryId,
                )
            val existing = existingSelections[key]

            return CategorySelection(
                accountKey = accountKey,
                categoryType = categoryType,
                sourceCategoryId = categoryId,
                categoryName = categoryName,
                isSelected = existing?.isSelected ?: true, // Preserve or default to selected
                parentId = parentId,
                sortOrder = sortIndex,
            )
        }

        companion object {
            private const val TAG = "XtreamCategoryPreloader"
        }
    }

/**
 * State of category preloading.
 */
sealed interface XtreamCategoryPreloadState {
    /** Initial state - no categories loaded yet. */
    data object Idle : XtreamCategoryPreloadState

    /** Loading categories from server. */
    data object Loading : XtreamCategoryPreloadState

    /** Categories loaded successfully. */
    data class Success(
        val categories: XtreamCachedCategories,
    ) : XtreamCategoryPreloadState

    /** Category loading failed. */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : XtreamCategoryPreloadState
}

/**
 * Cached category data with timestamp.
 */
data class XtreamCachedCategories(
    val vodCategories: List<XtreamCategoryInfo>,
    val seriesCategories: List<XtreamCategoryInfo>,
    val liveCategories: List<XtreamCategoryInfo>,
    val timestampMs: Long,
) {
    /** Total number of all categories. */
    val totalCount: Int
        get() = vodCategories.size + seriesCategories.size + liveCategories.size

    /** Whether any categories were loaded. */
    val isNotEmpty: Boolean
        get() = totalCount > 0
}
