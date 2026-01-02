package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fishit.player.core.catalogsync.CatalogSyncService
import com.fishit.player.core.catalogsync.SyncConfig
import com.fishit.player.core.catalogsync.SyncStatus
import com.fishit.player.infra.logging.UnifiedLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Telegram Incremental Scan Worker.
 *
 * Executes incremental Telegram synchronization via CatalogSyncService ONLY.
 *
 * **Incremental Sync Strategy (Platinum Solution - Dec 2025):**
 * - CatalogSyncService reads checkpoint from SyncCheckpointStore
 * - If checkpoint exists with high-water marks → INCREMENTAL mode
 *   - Only fetches messages NEWER than last sync per chat
 *   - TDLib returns newest-first, stops when reaching known messageId
 * - If no checkpoint (empty catalog) → FULL mode
 *   - Scans all chats, all messages
 *   - Records high-water marks for next sync
 * - Checkpoint is persisted by CatalogSyncService, not by this worker
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-2: All scanning MUST go through CatalogSyncService
 * - W-5: Telegram pipelines may emit multiple RawMediaMetadata items per bundle
 * - W-17: FireTV Safety (bounded batches, runtime budget)
 *
 * @see TelegramAuthPreflightWorker for auth validation
 * @see TelegramFullHistoryScanWorker for force full history scanning
 */
@HiltWorker
class TelegramIncrementalScanWorker
@AssistedInject
constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val catalogSyncService: CatalogSyncService,
) : CoroutineWorker(context, workerParams) {
    companion object {
        private const val TAG = "TelegramIncrementalScanWorker"
    }

    override suspend fun doWork(): Result {
        val input = WorkerInputData.from(inputData)
        val startTimeMs = System.currentTimeMillis()
        var itemsPersisted = 0L
        var syncCompleted = false

        UnifiedLog.i(TAG) {
            "START sync_run_id=${input.syncRunId} mode=${input.syncMode} source=TELEGRAM kind=INCREMENTAL"
        }

        // Check runtime guards (respects sync mode - manual syncs skip battery guards)
        val guardReason = RuntimeGuards.checkGuards(applicationContext, input.syncMode)
        if (guardReason != null) {
            UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason mode=${input.syncMode}" }
            return Result.retry()
        }

        // Build sync config based on device class (W-17)
        val syncConfig =
                SyncConfig(
                        batchSize = input.batchSize,
                        enableNormalization = true,
                        emitProgressEvery = input.batchSize,
                )

        try {
            // W-2: Call ONLY CatalogSyncService
            // CatalogSyncService handles checkpoint reading/writing internally
            catalogSyncService.syncTelegram(
                            chatIds = null, // null = all chats, incremental via internal checkpoint
                            syncConfig = syncConfig,
                    )
                    .collect { status ->
                        // Check if worker is cancelled
                        if (!currentCoroutineContext().isActive) {
                            UnifiedLog.w(TAG) { "Worker cancelled, stopping sync" }
                            throw CancellationException("Worker cancelled")
                        }

                        // Check max runtime - throw to exit collect loop properly
                        val elapsedMs = System.currentTimeMillis() - startTimeMs
                        if (elapsedMs > input.maxRuntimeMs) {
                            UnifiedLog.w(TAG) {
                                "Max runtime exceeded (${elapsedMs}ms > ${input.maxRuntimeMs}ms)"
                            }
                            // CatalogSyncService saves partial checkpoint on cancellation
                            throw CancellationException("Max runtime exceeded")
                        }

                        when (status) {
                            is SyncStatus.Started -> {
                                UnifiedLog.d(TAG) { "Sync started for source: ${status.source}" }
                            }
                            is SyncStatus.InProgress -> {
                                itemsPersisted = status.itemsPersisted
                                UnifiedLog.d(TAG) {
                                    "PROGRESS discovered=${status.itemsDiscovered} persisted=$itemsPersisted phase=${status.currentPhase}"
                                }
                            }
                            is SyncStatus.Completed -> {
                                itemsPersisted = status.totalItems
                                syncCompleted = true
                                UnifiedLog.i(TAG) {
                                    "Sync completed: ${status.totalItems} items in ${status.durationMs}ms"
                                }
                            }
                            is SyncStatus.Cancelled -> {
                                itemsPersisted = status.itemsPersisted
                                UnifiedLog.w(TAG) {
                                    "Sync cancelled: $itemsPersisted items persisted (checkpoint saved by service)"
                                }
                            }
                            is SyncStatus.Error -> {
                                UnifiedLog.e(TAG) {
                                    "Sync error: ${status.reason} - ${status.message}"
                                }
                                throw status.throwable ?: RuntimeException(status.message)
                            }
                            is SyncStatus.TelegramChatComplete -> {
                                // PLATINUM: Track completed chat for checkpoint resume
                                UnifiedLog.v(TAG) {
                                    "Chat ${status.chatId} complete: ${status.itemCount} items, hwm=${status.newHighWaterMark}"
                                }
                            }
                            is SyncStatus.SeriesEpisodeComplete -> {
                                // N/A for Telegram - ignore
                            }
                        }
                    }

            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.i(TAG) {
                "✅ SUCCESS duration_ms=$durationMs persisted_count=$itemsPersisted " +
                        "source=TELEGRAM kind=INCREMENTAL completed=$syncCompleted"
            }

            return Result.success(
                    WorkerOutputData.success(
                            itemsPersisted = itemsPersisted,
                            durationMs = durationMs,
                    ),
            )
        } catch (e: CancellationException) {
            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.w(TAG) {
                "⏸️ CANCELLED after ${durationMs}ms, persisted $itemsPersisted items source=TELEGRAM"
            }
            // Checkpoint is saved by CatalogSyncService, not here
            // Return success so WorkManager doesn't retry immediately
            return Result.success(
                    WorkerOutputData.success(
                            itemsPersisted = itemsPersisted,
                            durationMs = durationMs,
                    ),
            )
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.e(TAG, e) {
                "❌ FAILURE reason=${e.javaClass.simpleName} duration_ms=$durationMs source=TELEGRAM"
            }
            // Transient error - retry with backoff
            return Result.retry()
        }
    }
}
