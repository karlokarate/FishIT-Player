// Module: core/catalog-sync/sources/telegram
// Unified Telegram sync service interface

package com.fishit.player.core.catalogsync.sources.telegram

import com.fishit.player.core.catalogsync.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Unified Telegram synchronization service.
 *
 * Mirrors [XtreamSyncService] pattern: single [sync] entry point
 * configured via [TelegramSyncConfig].
 *
 * **Architecture:**
 * ```
 * Worker → TelegramSyncService.sync(config)
 *            ↓
 *          TelegramCatalogPipeline.scanCatalog()
 *            ↓
 *          TelethonProxyClient → Python → Telegram MTProto
 *            ↓
 *          NxCatalogWriter.ingestBatchOptimized()
 * ```
 *
 * **Flow Emissions:**
 * - [SyncStatus.Started] — Sync beginning
 * - [SyncStatus.InProgress] — Items being processed (with counts + phase)
 * - [SyncStatus.TelegramChatComplete] — Per-chat completion for checkpoint tracking
 * - [SyncStatus.Completed] — Sync finished successfully
 * - [SyncStatus.Cancelled] — Sync was cancelled (partial checkpoint saved)
 * - [SyncStatus.Error] — Sync failed with error
 *
 * **Checkpoint Behavior:**
 * If [TelegramSyncConfig.enableCheckpoints] is true, the sync will:
 * 1. Load existing checkpoint (high-water marks per chat)
 * 2. Skip already-completed chats (from excludeChatIds)
 * 3. Use high-water marks for incremental message fetching
 * 4. Save checkpoint on completion/cancellation for resume
 *
 * **Account Switch Detection:**
 * If the current userId differs from the checkpoint's userId,
 * forced full scan is triggered automatically.
 *
 * @see TelegramSyncConfig Configuration options
 * @see SyncStatus Progress/result status
 * @see XtreamSyncService Xtream counterpart (same pattern)
 */
interface TelegramSyncService {

    /**
     * Execute Telegram catalog synchronization.
     *
     * This is the **single entry point** for all Telegram sync operations.
     * Behavior is controlled entirely by [TelegramSyncConfig].
     *
     * @param config Sync configuration (chat selection, checkpoint, parallelism)
     * @return Flow of sync status updates
     */
    fun sync(config: TelegramSyncConfig): Flow<SyncStatus>

    /**
     * Cancel any active sync operation.
     *
     * Cancellation is cooperative — the sync completes its current batch
     * before stopping and emitting [SyncStatus.Cancelled].
     * A partial checkpoint is saved for resumption.
     */
    fun cancel()

    /**
     * Check if a sync operation is currently active.
     */
    val isActive: Boolean

    /**
     * Clear any stored checkpoint for the given account.
     *
     * Use when the user wants to force a clean sync
     * without resuming from previous state.
     *
     * @param accountKey The Telegram account identifier
     */
    suspend fun clearCheckpoint(accountKey: String)

    /**
     * Get the last sync timestamp for the given account.
     *
     * @param accountKey The Telegram account identifier
     * @return Epoch millis of last successful sync, or null if never synced
     */
    suspend fun getLastSyncTime(accountKey: String): Long?
}
