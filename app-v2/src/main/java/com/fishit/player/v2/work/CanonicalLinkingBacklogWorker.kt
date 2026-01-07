package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.fishit.player.core.metadata.MediaMetadataNormalizer
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.asPipelineItemId
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.infra.data.telegram.TelegramContentRepository
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.logging.UnifiedLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Canonical Linking Backlog Worker.
 *
 * **Purpose:** Process items that were persisted without canonical linking during fast initial sync.
 * This enables hot path relief by deferring expensive normalization and linking operations.
 *
 * **Task 2: Hot Path Entlastung**
 * When `SyncConfig.enableCanonicalLinking=false`, items are stored raw only.
 * This worker processes the backlog of unlinked items in batches to complete canonical unification.
 *
 * **Architecture:**
 * 1. Query pipeline-specific repositories for items without canonical links
 * 2. Normalize via MediaMetadataNormalizer
 * 3. Upsert to CanonicalMediaRepository
 * 4. Link source via addOrUpdateSourceRef
 *
 * **Contract:**
 * - W-16: Runtime Guards (MANDATORY)
 * - W-17: FireTV Safety - batch size clamping (MANDATORY)
 * - W-18: Exponential backoff (MANDATORY)
 * - Error isolation: Linking failures do not fail the entire batch
 *
 * @see DefaultCatalogSyncService.persistXtreamCatalogBatch for hot path with optional linking
 * @see CanonicalLinkingScheduler for backlog scheduling
 */
@HiltWorker
class CanonicalLinkingBacklogWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val normalizer: MediaMetadataNormalizer,
        private val canonicalMediaRepository: CanonicalMediaRepository,
        private val telegramRepository: TelegramContentRepository,
        private val xtreamCatalogRepository: XtreamCatalogRepository,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "CanonicalLinkingBacklogWorker"
        }

        override suspend fun doWork(): Result {
            val input = CanonicalLinkingInputData.from(inputData)
            val startTimeMs = System.currentTimeMillis()

            UnifiedLog.i(TAG) {
                "START run_id=${input.runId} source=${input.sourceType} batch_size=${input.batchSize}"
            }

            // W-16: Check runtime guards
            val guardReason = RuntimeGuards.checkGuards(applicationContext)
            if (guardReason != null) {
                UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason" }
                return Result.retry()
            }

            // Process batch based on source type
            val result =
                when (input.sourceType) {
                    SourceType.XTREAM -> processXtreamBacklog(input.batchSize, input.maxRuntimeMs, startTimeMs)
                    SourceType.TELEGRAM -> processTelegramBacklog(input.batchSize, input.maxRuntimeMs, startTimeMs)
                    else -> {
                        UnifiedLog.w(TAG) { "FAILURE reason=UNSUPPORTED_SOURCE source=${input.sourceType}" }
                        Result.failure(
                            Data.Builder()
                                .putString(WorkerConstants.KEY_FAILURE_REASON, "UNSUPPORTED_SOURCE")
                                .putString(WorkerConstants.KEY_FAILURE_DETAILS, "source=${input.sourceType}")
                                .build(),
                        )
                    }
                }

            return result
        }

        /**
         * Process Xtream backlog: Link unlinked items to canonical media.
         */
        private suspend fun processXtreamBacklog(
            batchSize: Int,
            maxRuntimeMs: Long,
            startTimeMs: Long,
        ): Result {
            var linkedCount = 0
            var failedCount = 0
            val budget = maxRuntimeMs - (System.currentTimeMillis() - startTimeMs)

            if (budget <= 0) {
                UnifiedLog.w(TAG) { "BUDGET_EXCEEDED before processing" }
                return Result.retry()
            }

            try {
                // TODO: CRITICAL - This currently queries ALL items and will reprocess them on every run.
                // Need to track which items are already linked to avoid duplicate work.
                // Possible solutions:
                // 1. Add repository methods: getUnlinkedItems(limit: Int)
                // 2. Track processed items in checkpoint store
                // 3. Query CanonicalMediaRepository for source refs and filter locally
                // For now, this is a placeholder implementation that demonstrates the worker pattern.
                val items = xtreamCatalogRepository.getAll(limit = batchSize)

                UnifiedLog.d(TAG) { "Processing Xtream backlog: ${items.size} items" }

                for (raw in items) {
                    try {
                        // Check budget on each iteration
                        val elapsed = System.currentTimeMillis() - startTimeMs
                        if (elapsed >= maxRuntimeMs) {
                            UnifiedLog.w(TAG) { "BUDGET_EXCEEDED after $linkedCount items" }
                            break
                        }

                        // Normalize and link
                        val normalized = normalizer.normalize(raw)
                        val canonicalId = canonicalMediaRepository.upsertCanonicalMedia(normalized)
                        val sourceRef = raw.toMediaSourceRef()
                        canonicalMediaRepository.addOrUpdateSourceRef(canonicalId, sourceRef)
                        linkedCount++
                    } catch (e: Exception) {
                        failedCount++
                        UnifiedLog.w(TAG) {
                            "Failed to link ${raw.sourceId} to canonical: ${e.message}"
                        }
                    }
                }

                val durationMs = System.currentTimeMillis() - startTimeMs

                UnifiedLog.i(TAG) {
                    "SUCCESS source=XTREAM linked=$linkedCount failed=$failedCount duration_ms=$durationMs"
                }

                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = linkedCount.toLong(),
                        durationMs = durationMs,
                    ),
                )
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Xtream backlog processing failed" }
                return Result.retry()
            }
        }

        /**
         * Process Telegram backlog: Link unlinked items to canonical media.
         */
        private suspend fun processTelegramBacklog(
            batchSize: Int,
            maxRuntimeMs: Long,
            startTimeMs: Long,
        ): Result {
            var linkedCount = 0
            var failedCount = 0
            val budget = maxRuntimeMs - (System.currentTimeMillis() - startTimeMs)

            if (budget <= 0) {
                UnifiedLog.w(TAG) { "BUDGET_EXCEEDED before processing" }
                return Result.retry()
            }

            try {
                // TODO: CRITICAL - This currently queries ALL items and will reprocess them on every run.
                // Need to track which items are already linked to avoid duplicate work.
                // Possible solutions:
                // 1. Add repository methods: getUnlinkedItems(limit: Int)
                // 2. Track processed items in checkpoint store
                // 3. Query CanonicalMediaRepository for source refs and filter locally
                // For now, this is a placeholder implementation that demonstrates the worker pattern.
                val items = telegramRepository.getAll(limit = batchSize)

                UnifiedLog.d(TAG) { "Processing Telegram backlog: ${items.size} items" }

                for (raw in items) {
                    try {
                        // Check budget on each iteration
                        val elapsed = System.currentTimeMillis() - startTimeMs
                        if (elapsed >= maxRuntimeMs) {
                            UnifiedLog.w(TAG) { "BUDGET_EXCEEDED after $linkedCount items" }
                            break
                        }

                        // Normalize and link
                        val normalized = normalizer.normalize(raw)
                        val canonicalId = canonicalMediaRepository.upsertCanonicalMedia(normalized)
                        val sourceRef = raw.toMediaSourceRef()
                        canonicalMediaRepository.addOrUpdateSourceRef(canonicalId, sourceRef)
                        linkedCount++
                    } catch (e: Exception) {
                        failedCount++
                        UnifiedLog.w(TAG) {
                            "Failed to link ${raw.sourceId} to canonical: ${e.message}"
                        }
                    }
                }

                val durationMs = System.currentTimeMillis() - startTimeMs

                UnifiedLog.i(TAG) {
                    "SUCCESS source=TELEGRAM linked=$linkedCount failed=$failedCount duration_ms=$durationMs"
                }

                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = linkedCount.toLong(),
                        durationMs = durationMs,
                    ),
                )
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Telegram backlog processing failed" }
                return Result.retry()
            }
        }

        /**
         * Convert RawMediaMetadata to MediaSourceRef for canonical linking.
         * 
         * TODO: Code duplication - this is duplicated from DefaultCatalogSyncService.
         * Should be extracted to a shared utility class in core/catalog-sync or core/model
         * to ensure SSOT for source reference creation logic.
         * Options:
         * 1. Extract to MediaSourceRefBuilder in core/catalog-sync
         * 2. Add extension function in core/model on RawMediaMetadata
         * 3. Add method to CanonicalMediaRepository that accepts RawMediaMetadata directly
         */
        private fun RawMediaMetadata.toMediaSourceRef(): MediaSourceRef =
            MediaSourceRef(
                sourceType = sourceType,
                sourceId = sourceId.asPipelineItemId(),
                sourceLabel = sourceLabel,
                quality = null,
                languages = null,
                format = null,
                sizeBytes = null,
                durationMs = durationMs,
                playbackHints = playbackHints,
                priority = calculateSourcePriority(),
            )

        /**
         * Calculate source priority for ordering in source selection.
         */
        private fun RawMediaMetadata.calculateSourcePriority(): Int =
            when (sourceType) {
                SourceType.XTREAM -> 100
                SourceType.TELEGRAM -> 50
                SourceType.IO -> 75
                SourceType.AUDIOBOOK -> 25
                else -> 0
            }
    }

/**
 * Input data for canonical linking backlog worker.
 */
data class CanonicalLinkingInputData(
    val runId: String,
    val sourceType: SourceType,
    val batchSize: Int,
    val maxRuntimeMs: Long,
) {
    fun toData(): Data =
        Data
            .Builder()
            .putString(WorkerConstants.KEY_SYNC_RUN_ID, runId)
            .putString(KEY_SOURCE_TYPE, sourceType.name)
            .putInt(KEY_BATCH_SIZE, batchSize)
            .putLong(WorkerConstants.KEY_MAX_RUNTIME_MS, maxRuntimeMs)
            .build()

    companion object {
        private const val KEY_SOURCE_TYPE = "source_type"
        private const val KEY_BATCH_SIZE = "batch_size"

        fun from(data: Data): CanonicalLinkingInputData {
            val runId = data.getString(WorkerConstants.KEY_SYNC_RUN_ID) ?: "unknown"
            val sourceTypeStr = data.getString(KEY_SOURCE_TYPE) ?: SourceType.UNKNOWN.name
            val sourceType =
                try {
                    SourceType.valueOf(sourceTypeStr)
                } catch (e: Exception) {
                    SourceType.UNKNOWN
                }
            val batchSize = data.getInt(KEY_BATCH_SIZE, WorkerConstants.NORMAL_BATCH_SIZE)
            val maxRuntimeMs = data.getLong(WorkerConstants.KEY_MAX_RUNTIME_MS, WorkerConstants.DEFAULT_MAX_RUNTIME_MS)

            return CanonicalLinkingInputData(
                runId = runId,
                sourceType = sourceType,
                batchSize = batchSize,
                maxRuntimeMs = maxRuntimeMs,
            )
        }
    }
}
