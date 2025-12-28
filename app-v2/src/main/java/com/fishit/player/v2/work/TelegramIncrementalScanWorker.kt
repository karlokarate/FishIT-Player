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
 * Executes incremental Telegram synchronization via CatalogSyncService ONLY. Uses persisted cursors
 * to resume from last sync point.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-2: All scanning MUST go through CatalogSyncService
 * - W-5: Telegram pipelines may emit multiple RawMediaMetadata items per bundle
 * ```
 *        Worker treats every Raw emission as an independent variant
 * ```
 * - W-17: FireTV Safety (bounded batches, frequent checkpoints)
 * - Uses persisted cursors
 * - Persists streaming; checkpoint frequently
 *
 * @see TelegramAuthPreflightWorker for auth validation
 * @see TelegramFullHistoryScanWorker for full history scanning
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
            var lastCheckpoint: String? = null

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
                // Note: Incremental sync uses persisted cursors internally
                catalogSyncService
                    .syncTelegram(
                        chatIds = null, // Incremental - pipeline uses persisted cursors
                        syncConfig = syncConfig,
                    ).collect { status ->
                        // Check if worker is cancelled
                        if (!currentCoroutineContext().isActive) {
                            UnifiedLog.w(TAG) { "Worker cancelled, stopping sync" }
                            throw CancellationException("Worker cancelled")
                        }

                        // Check max runtime
                        val elapsedMs = System.currentTimeMillis() - startTimeMs
                        if (elapsedMs > input.maxRuntimeMs) {
                            UnifiedLog.w(TAG) {
                                "Max runtime exceeded (${elapsedMs}ms > ${input.maxRuntimeMs}ms), saving checkpoint"
                            }
                            UnifiedLog.i(TAG) { "CHECKPOINT_SAVED cursor=$lastCheckpoint" }
                            return@collect
                        }

                        when (status) {
                            is SyncStatus.Started -> {
                                UnifiedLog.d(TAG) { "Sync started for source: ${status.source}" }
                            }
                            is SyncStatus.InProgress -> {
                                itemsPersisted = status.itemsPersisted
                                lastCheckpoint = status.currentPhase

                                // Log progress (counts only, no payloads - W-17)
                                // Note: Each Raw emission is treated independently (W-5)
                                UnifiedLog.d(TAG) {
                                    "PROGRESS discovered=${status.itemsDiscovered} persisted=$itemsPersisted phase=${status.currentPhase}"
                                }
                            }
                            is SyncStatus.Completed -> {
                                itemsPersisted = status.totalItems
                                UnifiedLog.i(TAG) {
                                    "Sync completed: ${status.totalItems} items in ${status.durationMs}ms"
                                }
                            }
                            is SyncStatus.Cancelled -> {
                                itemsPersisted = status.itemsPersisted
                                UnifiedLog.w(TAG) {
                                    "Sync cancelled: $itemsPersisted items persisted"
                                }
                            }
                            is SyncStatus.Error -> {
                                UnifiedLog.e(TAG) {
                                    "Sync error: ${status.reason} - ${status.message}"
                                }
                                throw status.throwable ?: RuntimeException(status.message)
                            }
                        }
                    }

                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.i(TAG) { "SUCCESS duration_ms=$durationMs persisted_count=$itemsPersisted" }

                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = itemsPersisted,
                        durationMs = durationMs,
                        checkpointCursor = lastCheckpoint,
                    ),
                )
            } catch (e: CancellationException) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.w(TAG) { "Cancelled after ${durationMs}ms, persisted $itemsPersisted items" }
                UnifiedLog.i(TAG) { "CHECKPOINT_SAVED cursor=$lastCheckpoint" }

                // Return success so checkpoint is preserved
                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = itemsPersisted,
                        durationMs = durationMs,
                        checkpointCursor = lastCheckpoint,
                    ),
                )
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.e(TAG, e) {
                    "FAILURE reason=${e.javaClass.simpleName} duration_ms=$durationMs retry=true"
                }

                // Transient error - retry
                return Result.retry()
            }
        }
    }
