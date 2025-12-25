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
 * Xtream Catalog Scan Worker.
 *
 * Executes Xtream catalog synchronization via CatalogSyncService ONLY.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-2: All scanning MUST go through CatalogSyncService
 * - W-17: FireTV Safety (bounded batches, frequent checkpoints)
 * - Persists streaming (no accumulating lists)
 * - Honors cancel: save checkpoint then exit
 *
 * @see XtreamPreflightWorker for preflight validation
 */
@HiltWorker
class XtreamCatalogScanWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val catalogSyncService: CatalogSyncService,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "XtreamCatalogScanWorker"
        }

        override suspend fun doWork(): Result {
            val input = WorkerInputData.from(inputData)
            val startTimeMs = System.currentTimeMillis()
            var itemsPersisted = 0L
            var lastCheckpoint: String? = null

            UnifiedLog.i(TAG) {
                "START sync_run_id=${input.syncRunId} mode=${input.syncMode} source=XTREAM scope=${input.xtreamSyncScope}"
            }

            // Check runtime guards
            val guardReason = RuntimeGuards.checkGuards(applicationContext)
            if (guardReason != null) {
                UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason" }
                return Result.retry()
            }

            // Build sync config based on device class (W-17)
            val syncConfig =
                SyncConfig(
                    batchSize = input.batchSize,
                    enableNormalization = true,
                    emitProgressEvery = input.batchSize,
                )

            // Determine sync scope
            val includeVod = true
            val includeSeries = true
            val includeEpisodes =
                input.xtreamSyncScope == WorkerConstants.XTREAM_SCOPE_FULL ||
                    input.syncMode == WorkerConstants.SYNC_MODE_FORCE_RESCAN
            val includeLive = true

            try {
                // W-2: Call ONLY CatalogSyncService
                catalogSyncService
                    .syncXtream(
                        includeVod = includeVod,
                        includeSeries = includeSeries,
                        includeEpisodes = includeEpisodes,
                        includeLive = includeLive,
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
                            // Save checkpoint and return success for continuation
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
                UnifiedLog.i(TAG) {
                    "SUCCESS duration_ms=$durationMs persisted_count=$itemsPersisted"
                }

                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = itemsPersisted,
                        durationMs = durationMs,
                        checkpointCursor = lastCheckpoint,
                    ),
                )
            } catch (e: CancellationException) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.w(TAG) {
                    "Cancelled after ${durationMs}ms, persisted $itemsPersisted items"
                }
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
