// Module: core/catalog-sync/sources/xtream
// Unified Xtream sync service interface

package com.fishit.player.core.catalogsync.sources.xtream

import com.fishit.player.core.model.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Unified Xtream synchronization service.
 *
 * **Replaces the 6-method pattern with 1 unified entry point.**
 *
 * This interface defines the single sync method that handles all
 * Xtream content types (VOD, Series, Live, Episodes) through
 * configuration rather than method proliferation.
 *
 * **Usage:**
 * ```kotlin
 * // Full sync
 * xtreamSyncService.sync(XtreamSyncConfig.fullSync(accountKey))
 *     .collect { status -> handleStatus(status) }
 *
 * // Live-only quick sync
 * xtreamSyncService.sync(XtreamSyncConfig.liveOnly(accountKey))
 *     .collect { ... }
 *
 * // Category-filtered sync
 * xtreamSyncService.sync(XtreamSyncConfig.withCategories(
 *     accountKey = accountKey,
 *     vodIds = setOf("1", "2"),
 *     liveIds = setOf("3", "4")
 * )).collect { ... }
 * ```
 *
 * **Architecture:**
 * - Uses Channel-based producer/consumer pattern
 * - Supports checkpoint-based resumption
 * - Emits granular progress via Flow<SyncStatus>
 * - Device profile aware (FireTV, Shield, etc.)
 *
 * @see XtreamSyncConfig Configuration options
 * @see SyncStatus Progress/result status
 */
interface XtreamSyncService {
    /**
     * Execute Xtream catalog synchronization.
     *
     * This is the **single entry point** for all Xtream sync operations.
     * The behavior is controlled entirely by [XtreamSyncConfig].
     *
     * **Flow Emissions:**
     * - [SyncStatus.Idle] - Before sync starts
     * - [SyncStatus.Checking] - Incremental check in progress
     * - [SyncStatus.Starting] - Sync beginning
     * - [SyncStatus.InProgress] - Items being processed (with counts)
     * - [SyncStatus.Completed] - Sync finished successfully
     * - [SyncStatus.Error] - Sync failed with error
     * - [SyncStatus.Cancelled] - Sync was cancelled
     *
     * **Checkpoint Behavior:**
     * If [XtreamSyncConfig.enableCheckpoints] is true, the sync will:
     * 1. Check for existing checkpoint from previous incomplete sync
     * 2. Resume from checkpoint if found
     * 3. Save checkpoints at phase boundaries
     * 4. Clear checkpoint on successful completion
     *
     * **Incremental Behavior:**
     * If [XtreamSyncConfig.forceFullSync] is false:
     * 1. Check ETag/304 response if available
     * 2. Check item count changes
     * 3. Check timestamp changes
     * 4. Compute fingerprint if needed
     * 5. Skip sync if no changes detected
     *
     * @param config Sync configuration (content selection, categories, options)
     * @return Flow of sync status updates
     */
    fun sync(config: XtreamSyncConfig): Flow<SyncStatus>

    /**
     * Cancel any active sync operation.
     *
     * The cancellation is cooperative - the sync will complete its
     * current batch before stopping and emitting [SyncStatus.Cancelled].
     *
     * If checkpoints are enabled, a checkpoint will be saved at the
     * cancellation point for resumption.
     */
    fun cancel()

    /**
     * Check if a sync operation is currently active.
     *
     * @return true if sync is in progress
     */
    val isActive: Boolean

    /**
     * Load Xtream categories for the given account.
     *
     * This is a utility method for UI category selection.
     * Does not perform full catalog sync.
     *
     * @param accountKey The Xtream account identifier
     * @return Category listings for VOD, Series, and Live
     */
    suspend fun loadCategories(accountKey: String): XtreamCategories

    /**
     * Clear any stored checkpoint for the given account.
     *
     * Use this when the user wants to force a clean sync
     * without resuming from previous state.
     *
     * @param accountKey The Xtream account identifier
     */
    suspend fun clearCheckpoint(accountKey: String)

    /**
     * Get the last sync timestamp for the given account.
     *
     * @param accountKey The Xtream account identifier
     * @return Epoch millis of last successful sync, or null if never synced
     */
    suspend fun getLastSyncTime(accountKey: String): Long?
}

/**
 * Xtream category listings for UI display.
 *
 * @property vodCategories VOD/Movies categories
 * @property seriesCategories TV Series categories
 * @property liveCategories Live TV channel categories
 */
data class XtreamCategories(
    val vodCategories: List<XtreamCategory> = emptyList(),
    val seriesCategories: List<XtreamCategory> = emptyList(),
    val liveCategories: List<XtreamCategory> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            vodCategories.isEmpty() &&
                seriesCategories.isEmpty() &&
                liveCategories.isEmpty()

    val totalCount: Int
        get() = vodCategories.size + seriesCategories.size + liveCategories.size
}

/**
 * Single Xtream category.
 *
 * @property id Category ID from Xtream API
 * @property name Display name
 * @property parentId Optional parent category for nested structure
 */
data class XtreamCategory(
    val id: String,
    val name: String,
    val parentId: String? = null,
)
