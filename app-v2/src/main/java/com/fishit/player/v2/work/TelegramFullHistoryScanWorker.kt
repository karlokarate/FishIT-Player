package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fishit.player.core.catalogsync.DefaultCatalogSyncService
import com.fishit.player.core.catalogsync.SyncCheckpointStore
import com.fishit.player.core.catalogsync.SyncConfig
import com.fishit.player.core.catalogsync.SyncStatus
import com.fishit.player.core.catalogsync.TelegramSyncCheckpoint
import com.fishit.player.core.persistence.cache.HomeCacheInvalidator
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogConfig
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Telegram Full History Scan Worker - PLATINUM Edition.
 *
 * Executes full Telegram history synchronization via CatalogSyncService ONLY. Uses parallel chat
 * scanning with per-chat checkpointing for resumability.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-2: All scanning MUST go through CatalogSyncService
 * - W-5: Telegram pipelines may emit multiple RawMediaMetadata items per bundle
 * ```
 *        Worker treats every Raw emission as an independent variant
 * ```
 * - W-17: FireTV Safety (bounded batches, frequent checkpoints)
 * - No artificial message limit
 * - Checkpoint per chat + per page/batch
 * - Persists streaming (no in-memory growth)
 *
 * **PLATINUM Features:**
 * - Parallel chat scanning (default: 3 concurrent chats)
 * - Per-chat checkpointing via [SyncStatus.TelegramChatComplete]
 * - Resume from last successful chat on worker restart
 * - [TelegramSyncCheckpoint.processedChatIds] for cross-run continuity
 *
 * @see TelegramAuthPreflightWorker for auth validation
 * @see TelegramIncrementalScanWorker for incremental scanning
 */
@HiltWorker
class TelegramFullHistoryScanWorker
@AssistedInject
constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val catalogSyncService: DefaultCatalogSyncService,
        private val checkpointStore: SyncCheckpointStore,
        private val homeCacheInvalidator: HomeCacheInvalidator, // ✅ Phase 2: Cache invalidation
) : CoroutineWorker(context, workerParams) {
    companion object {
        private const val TAG = "TelegramFullHistoryScanWorker"

        /** Default parallel chat count (lower than Xtream due to TDLib rate limits) */
        private const val DEFAULT_CHAT_PARALLELISM = TelegramCatalogConfig.DEFAULT_CHAT_PARALLELISM
    }

    override suspend fun doWork(): Result {
        val input = WorkerInputData.from(inputData)
        val startTimeMs = System.currentTimeMillis()
        var itemsPersisted = 0L
        var lastCheckpoint: String? = null

        // PLATINUM: Track processed chats for checkpoint resume
        val processedChatIds = mutableSetOf<Long>()
        val newHighWaterMarks = mutableMapOf<Long, Long>()

        UnifiedLog.i(TAG) {
            "START sync_run_id=${input.syncRunId} mode=${input.syncMode} source=TELEGRAM kind=FULL_HISTORY_PLATINUM"
        }

        // Check runtime guards (respects sync mode - manual syncs skip battery guards)
        val guardReason = RuntimeGuards.checkGuards(applicationContext, input.syncMode)
        if (guardReason != null) {
            UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason mode=${input.syncMode}" }
            return Result.retry()
        }

        // PLATINUM: Load existing checkpoint for resume
        val existingCheckpoint =
                checkpointStore.getTelegramCheckpoint()?.let { TelegramSyncCheckpoint.decode(it) }
                        ?: TelegramSyncCheckpoint.INITIAL

        // Add previously processed chats to our tracking set (for HWM preservation)
        processedChatIds.addAll(existingCheckpoint.processedChatIds)

        UnifiedLog.i(TAG) {
            "PLATINUM checkpoint: already_processed=${existingCheckpoint.processedChatIds.size} chats"
        }

        // Build sync config based on device class (W-17)
        val syncConfig =
                SyncConfig(
                        batchSize = input.batchSize,
                        enableNormalization = true,
                        emitProgressEvery = input.batchSize,
                )

        try {
            // W-2: Call CatalogSyncService with PLATINUM parameters
            // Note: chatIds = null means scan all chats (full history)
            catalogSyncService.syncTelegramPlatinum(
                            chatIds = null, // Full history - no artificial limits
                            syncConfig = syncConfig,
                            excludeChatIds =
                                    existingCheckpoint.processedChatIds, // Resume from checkpoint
                            chatParallelism = DEFAULT_CHAT_PARALLELISM,
                    )
                    .collect { status ->
                        // Check if worker is cancelled
                        if (!currentCoroutineContext().isActive) {
                            UnifiedLog.w(TAG) { "Worker cancelled, saving checkpoint" }
                            saveCheckpoint(existingCheckpoint, processedChatIds, newHighWaterMarks)
                            throw CancellationException("Worker cancelled")
                        }

                        // Check max runtime
                        val elapsedMs = System.currentTimeMillis() - startTimeMs
                        if (elapsedMs > input.maxRuntimeMs) {
                            UnifiedLog.w(TAG) {
                                "Max runtime exceeded (${elapsedMs}ms > ${input.maxRuntimeMs}ms), saving checkpoint"
                            }
                            saveCheckpoint(existingCheckpoint, processedChatIds, newHighWaterMarks)
                            UnifiedLog.i(TAG) {
                                "CHECKPOINT_SAVED processed_chats=${processedChatIds.size}"
                            }
                            return@collect
                        }

                        when (status) {
                            is SyncStatus.Started -> {
                                UnifiedLog.d(TAG) {
                                    "PLATINUM sync started for source: ${status.source}"
                                }
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
                            is SyncStatus.TelegramChatComplete -> {
                                // PLATINUM: Track completed chat for checkpoint
                                processedChatIds.add(status.chatId)
                                status.newHighWaterMark?.let { hwm ->
                                    newHighWaterMarks[status.chatId] = hwm
                                }

                                UnifiedLog.v(TAG) {
                                    "CHAT_COMPLETE chatId=${status.chatId} items=${status.itemCount} total_processed=${processedChatIds.size}"
                                }

                                // Save checkpoint periodically (every 5 chats)
                                if (processedChatIds.size % 5 == 0) {
                                    saveCheckpoint(
                                            existingCheckpoint,
                                            processedChatIds,
                                            newHighWaterMarks
                                    )
                                }
                            }
                            is SyncStatus.Completed -> {
                                itemsPersisted = status.totalItems

                                // Final checkpoint save - use merged HWMs from existingCheckpoint +
                                // newHighWaterMarks
                                // Clear processedChatIds since sync is complete (no resume needed)
                                val finalCheckpoint =
                                        existingCheckpoint
                                                .updateHighWaterMarks(
                                                        newHighWaterMarks
                                                ) // Merge new HWMs
                                                .clearProcessedChatIds() // Reset for next sync
                                                .markComplete() // Update timestamp
                                checkpointStore.saveTelegramCheckpoint(finalCheckpoint.encode())

                                UnifiedLog.i(TAG) {
                                    "PLATINUM sync completed: ${status.totalItems} items in ${status.durationMs}ms, " +
                                            "${newHighWaterMarks.size} HWMs updated"
                                }
                            }
                            is SyncStatus.Cancelled -> {
                                itemsPersisted = status.itemsPersisted

                                // Save checkpoint for resume
                                saveCheckpoint(
                                        existingCheckpoint,
                                        processedChatIds,
                                        newHighWaterMarks
                                )

                                UnifiedLog.w(TAG) {
                                    "PLATINUM sync cancelled: $itemsPersisted items persisted, ${processedChatIds.size} chats checkpointed"
                                }
                            }
                            is SyncStatus.Error -> {
                                // Save checkpoint before error propagation
                                saveCheckpoint(
                                        existingCheckpoint,
                                        processedChatIds,
                                        newHighWaterMarks
                                )

                                UnifiedLog.e(TAG) {
                                    "PLATINUM sync error: ${status.reason} - ${status.message}"
                                }
                                throw status.throwable ?: RuntimeException(status.message)
                            }
                            // Ignore Xtream-specific events
                            is SyncStatus.SeriesEpisodeComplete -> {
                                /* not applicable */
                            }
                        }
                    }

            val durationMs = System.currentTimeMillis() - startTimeMs
            UnifiedLog.i(TAG) {
                "✅ SUCCESS duration_ms=$durationMs persisted_count=$itemsPersisted chats_processed=${processedChatIds.size} source=TELEGRAM kind=FULL_HISTORY_PLATINUM"
            }

            // ✅ Phase 2: Invalidate Home cache after successful sync
            homeCacheInvalidator.invalidateAllAfterSync(
                    source = "TELEGRAM",
                    syncRunId = input.syncRunId
            )

            return Result.success(
                    WorkerOutputData.success(
                            itemsPersisted = itemsPersisted,
                            durationMs = durationMs,
                            checkpointCursor = lastCheckpoint,
                    ),
            )
        } catch (e: CancellationException) {
            val durationMs = System.currentTimeMillis() - startTimeMs

            // Save checkpoint for resume
            saveCheckpoint(existingCheckpoint, processedChatIds, newHighWaterMarks)

            UnifiedLog.w(TAG) {
                "⏸️ CANCELLED after ${durationMs}ms, persisted $itemsPersisted items, checkpointed ${processedChatIds.size} chats"
            }

            // Return success so checkpoint is preserved
            return Result.success(
                    WorkerOutputData.success(
                            itemsPersisted = itemsPersisted,
                            durationMs = durationMs,
                            checkpointCursor = "processed_chats=${processedChatIds.size}",
                    ),
            )
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTimeMs

            // Save checkpoint for resume
            saveCheckpoint(existingCheckpoint, processedChatIds, newHighWaterMarks)

            UnifiedLog.e(TAG, e) {
                "❌ FAILURE reason=${e.javaClass.simpleName} duration_ms=$durationMs chats_checkpointed=${processedChatIds.size} source=TELEGRAM"
            }

            // Transient error - retry (bounded by WorkManager backoff)
            return Result.retry()
        }
    }

    /** Save checkpoint with processed chat IDs and high-water marks. */
    private suspend fun saveCheckpoint(
            existingCheckpoint: TelegramSyncCheckpoint,
            processedChatIds: Set<Long>,
            newHighWaterMarks: Map<Long, Long>,
    ) {
        val checkpoint =
                existingCheckpoint
                        .addProcessedChatIds(processedChatIds)
                        .updateHighWaterMarks(newHighWaterMarks)
        checkpointStore.saveTelegramCheckpoint(checkpoint.encode())
        UnifiedLog.d(TAG) {
            "Checkpoint saved: ${processedChatIds.size} chats, ${newHighWaterMarks.size} HWMs"
        }
    }
}
