/**
 * Repository for managing Xtream category sync selections.
 *
 * Part of Issue #669 - Sync by Category Implementation.
 *
 * **Purpose:**
 * - Stores user selections for which categories to include in sync
 * - Provides reactive Flow for UI observation
 * - Supports bulk operations for efficient sync filtering
 *
 * See: NX_XtreamCategorySelection entity in core/persistence
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

/**
 * Manages Xtream category sync selections.
 *
 * Domain interface - implementation in infra/data-nx.
 */
interface NxCategorySelectionRepository {

    /**
     * Category type for Xtream sources.
     */
    enum class XtreamCategoryType {
        VOD,
        SERIES,
        LIVE
    }

    /**
     * Domain model for category selection.
     */
    data class CategorySelection(
        val accountKey: String,
        val categoryType: XtreamCategoryType,
        val sourceCategoryId: String,
        val categoryName: String,
        val isSelected: Boolean,
        val parentId: Int? = null,
        val sortOrder: Int = 0,
    ) {
        /**
         * Unique key for this selection.
         */
        val selectionKey: String
            get() = buildSelectionKey(accountKey, categoryType, sourceCategoryId)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Companion - Key generation
    // ──────────────────────────────────────────────────────────────────────────

    companion object {
        /**
         * Build a unique selection key from components.
         *
         * Key format: `xtream:<accountKey>:<categoryType>:<sourceCategoryId>`
         *
         * @param accountKey Xtream account key
         * @param categoryType VOD, SERIES, or LIVE
         * @param sourceCategoryId Category ID from server
         * @return Unique selection key
         */
        fun buildSelectionKey(
            accountKey: String,
            categoryType: XtreamCategoryType,
            sourceCategoryId: String,
        ): String = "xtream:$accountKey:${categoryType.name}:$sourceCategoryId"
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Observers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Observe all selections for an account.
     *
     * @param accountKey Xtream account key
     * @return Flow emitting list of selections
     */
    fun observeForAccount(accountKey: String): Flow<List<CategorySelection>>

    /**
     * Observe selections for a specific category type.
     *
     * @param accountKey Xtream account key
     * @param categoryType VOD, SERIES, or LIVE
     * @return Flow emitting list of selections
     */
    fun observeByType(accountKey: String, categoryType: XtreamCategoryType): Flow<List<CategorySelection>>

    /**
     * Observe only selected (enabled) categories for an account.
     *
     * @param accountKey Xtream account key
     * @return Flow emitting list of selected categories
     */
    fun observeSelected(accountKey: String): Flow<List<CategorySelection>>

    // ──────────────────────────────────────────────────────────────────────────
    // Queries
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Get all selected category IDs for a type.
     * Used by sync to filter categories.
     *
     * @param accountKey Xtream account key
     * @param categoryType VOD, SERIES, or LIVE
     * @return List of source category IDs that are selected
     */
    suspend fun getSelectedCategoryIds(accountKey: String, categoryType: XtreamCategoryType): List<String>

    /**
     * Check if a category is selected.
     *
     * @param accountKey Xtream account key
     * @param categoryType VOD, SERIES, or LIVE
     * @param sourceCategoryId Category ID from server
     * @return true if selected for sync
     */
    suspend fun isSelected(accountKey: String, categoryType: XtreamCategoryType, sourceCategoryId: String): Boolean

    // ──────────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Upsert a category selection.
     * Creates if not exists, updates if exists.
     *
     * @param selection Category selection to save
     * @return Saved selection
     */
    suspend fun upsert(selection: CategorySelection): CategorySelection

    /**
     * Bulk upsert for efficient category import.
     * Used when preloading categories from server.
     *
     * @param selections List of selections to save
     */
    suspend fun upsertAll(selections: List<CategorySelection>)

    /**
     * Toggle selection state for a category.
     *
     * @param accountKey Xtream account key
     * @param categoryType VOD, SERIES, or LIVE
     * @param sourceCategoryId Category ID from server
     * @param isSelected New selection state
     */
    suspend fun setSelected(
        accountKey: String,
        categoryType: XtreamCategoryType,
        sourceCategoryId: String,
        isSelected: Boolean
    )

    /**
     * Select all categories for a type.
     *
     * @param accountKey Xtream account key
     * @param categoryType VOD, SERIES, or LIVE
     */
    suspend fun selectAll(accountKey: String, categoryType: XtreamCategoryType)

    /**
     * Deselect all categories for a type.
     *
     * @param accountKey Xtream account key
     * @param categoryType VOD, SERIES, or LIVE
     */
    suspend fun deselectAll(accountKey: String, categoryType: XtreamCategoryType)

    // ──────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Delete all selections for an account.
     * Called when account is removed.
     *
     * @param accountKey Xtream account key
     * @return Number of deleted selections
     */
    suspend fun deleteForAccount(accountKey: String): Int
}
