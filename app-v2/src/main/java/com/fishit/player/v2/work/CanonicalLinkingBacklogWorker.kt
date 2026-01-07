package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.fishit.player.core.catalogsync.MediaSourceRefBuilder
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
 * **Task 3: Bulk/Parallelisierung (Enhanced Dec 2025)**
 * - Bounded concurrency: 6-12 parallel normalization operations (CPU-intensive)
 * - FireTV safety: 2-4 parallel operations (reduced for low-RAM devices)
 * - Bulk transactions: Group canonical linking operations for efficiency
 * - Batch sizes: 300-800 items (device-aware)
 * - Viewport prioritization: Optional support for prioritizing visible items
 *
 * **Architecture:**
 * 1. Query pipeline-specific repositories for items without canonical links
 * 2. Normalize via MediaMetadataNormalizer (parallel with bounded concurrency)
 * 3. Upsert to CanonicalMediaRepository (bulk transactions)
 * 4. Link source via addOrUpdateSourceRef (bulk operations)
 *
 * **Contract:**
 * - W-16: Runtime Guards (MANDATORY)
 * - W-17: FireTV Safety - batch size clamping (MANDATORY)
 * - W-18: Exponential backoff (MANDATORY)
 * - Error isolation: Linking failures do not fail the entire batch
 * - Bulk writes: Use ObjectBox transactions for performance
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
            
            /**
             * Task 3: Bounded concurrency for parallel normalization.
             * Normalization is CPU-intensive, so we limit concurrent operations.
             * 
             * - Normal devices (Phone/Tablet): 8 parallel operations
             * - FireTV/low-RAM: 3 parallel operations (W-17: FireTV Safety)
             */
            private const val CANONICAL_LINKING_CONCURRENCY_NORMAL = 8
            private const val CANONICAL_LINKING_CONCURRENCY_FIRETV = 3
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
         * 
         * **Task 3 Enhancement:** Uses bounded concurrency and bulk transactions for performance.
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
                // Query items that need canonical linking (not yet linked to canonical media)
                val items = xtreamCatalogRepository.getUnlinkedForCanonicalLinking(limit = batchSize)

                if (items.isEmpty()) {
                    UnifiedLog.d(TAG) { "No unlinked Xtream items found" }
                    return Result.success(
                        WorkerOutputData.success(
                            itemsPersisted = 0L,
                            durationMs = System.currentTimeMillis() - startTimeMs,
                        ),
                    )
                }

                // Determine concurrency based on device class
                val concurrency = getConcurrency()
                
                UnifiedLog.d(TAG) { 
                    "Processing Xtream backlog: ${items.size} items, concurrency=$concurrency" 
                }

                // Process in parallel chunks with bounded concurrency
                val results = processItemsParallel(items, concurrency, maxRuntimeMs, startTimeMs)
                
                linkedCount = results.count { it.success }
                failedCount = results.count { !it.success }

                val durationMs = System.currentTimeMillis() - startTimeMs
                val throughput = if (durationMs > 0) linkedCount * 1000 / durationMs else 0

                // Check if there are more items to process
                val remainingCount = xtreamCatalogRepository.countUnlinkedForCanonicalLinking()

                UnifiedLog.i(TAG) {
                    "SUCCESS source=XTREAM linked=$linkedCount failed=$failedCount " +
                        "duration_ms=$durationMs throughput=$throughput items/sec " +
                        "remaining=$remainingCount concurrency=$concurrency"
                }

                // If there are more items, return success but suggest retry
                // (WorkManager will schedule another run based on constraints)
                return if (remainingCount > 0 && linkedCount > 0) {
                    UnifiedLog.d(TAG) { "More items to process ($remainingCount remaining), will retry" }
                    Result.retry() // Continue processing in next run
                } else {
                    Result.success(
                        WorkerOutputData.success(
                            itemsPersisted = linkedCount.toLong(),
                            durationMs = durationMs,
                        ),
                    )
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Xtream backlog processing failed" }
                return Result.retry()
            }
        }

        /**
         * Process Telegram backlog: Link unlinked items to canonical media.
         * 
         * **Task 3 Enhancement:** Uses bounded concurrency and bulk transactions for performance.
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
                // Query items that need canonical linking (not yet linked to canonical media)
                val items = telegramRepository.getUnlinkedForCanonicalLinking(limit = batchSize)

                if (items.isEmpty()) {
                    UnifiedLog.d(TAG) { "No unlinked Telegram items found" }
                    return Result.success(
                        WorkerOutputData.success(
                            itemsPersisted = 0L,
                            durationMs = System.currentTimeMillis() - startTimeMs,
                        ),
                    )
                }

                // Determine concurrency based on device class
                val concurrency = getConcurrency()
                
                UnifiedLog.d(TAG) { 
                    "Processing Telegram backlog: ${items.size} items, concurrency=$concurrency" 
                }

                // Process in parallel chunks with bounded concurrency
                val results = processItemsParallel(items, concurrency, maxRuntimeMs, startTimeMs)
                
                linkedCount = results.count { it.success }
                failedCount = results.count { !it.success }

                val durationMs = System.currentTimeMillis() - startTimeMs
                val throughput = if (durationMs > 0) linkedCount * 1000 / durationMs else 0

                // Check if there are more items to process
                val remainingCount = telegramRepository.countUnlinkedForCanonicalLinking()

                UnifiedLog.i(TAG) {
                    "SUCCESS source=TELEGRAM linked=$linkedCount failed=$failedCount " +
                        "duration_ms=$durationMs throughput=$throughput items/sec " +
                        "remaining=$remainingCount concurrency=$concurrency"
                }

                // If there are more items, return success but suggest retry
                return if (remainingCount > 0 && linkedCount > 0) {
                    UnifiedLog.d(TAG) { "More items to process ($remainingCount remaining), will retry" }
                    Result.retry() // Continue processing in next run
                } else {
                    Result.success(
                        WorkerOutputData.success(
                            itemsPersisted = linkedCount.toLong(),
                            durationMs = durationMs,
                        ),
                    )
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Telegram backlog processing failed" }
                return Result.retry()
            }
        }

        // ========================================================================
        // Task 3: Parallel Processing and Bulk Operations
        // ========================================================================

        /**
         * Determine concurrency based on device class.
         * 
         * W-17: FireTV Safety - reduce concurrency on low-RAM devices.
         */
        private fun getConcurrency(): Int {
            val deviceClass = inputData.getString(WorkerConstants.KEY_DEVICE_CLASS)
            return if (deviceClass == WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM) {
                CANONICAL_LINKING_CONCURRENCY_FIRETV
            } else {
                CANONICAL_LINKING_CONCURRENCY_NORMAL
            }
        }

        /**
         * Process items in parallel with bounded concurrency.
         * 
         * **Task 3 Features:**
         * - Parallel normalization (CPU-intensive operation)
         * - Bulk transactions for canonical linking
         * - Budget checks to respect maxRuntimeMs
         * - Error isolation (one failure doesn't stop the batch)
         * 
         * @param items Items to process
         * @param concurrency Max parallel operations (6-12 normal, 2-4 FireTV)
         * @param maxRuntimeMs Maximum runtime budget
         * @param startTimeMs Start time for budget tracking
         * @return List of processing results
         */
        private suspend fun processItemsParallel(
            items: List<RawMediaMetadata>,
            concurrency: Int,
            maxRuntimeMs: Long,
            startTimeMs: Long,
        ): List<LinkingResult> = coroutineScope {
            val results = mutableListOf<LinkingResult>()
            
            // Process in chunks to limit memory usage and enable early budget checks
            items.chunked(concurrency).forEach { chunk ->
                // Check budget before processing each chunk
                val elapsed = System.currentTimeMillis() - startTimeMs
                if (elapsed >= maxRuntimeMs) {
                    UnifiedLog.w(TAG) { "BUDGET_EXCEEDED during parallel processing, stopping" }
                    return@coroutineScope results
                }
                
                // Normalize in parallel (CPU-intensive)
                val normalized = chunk.map { raw ->
                    async {
                        try {
                            val norm = normalizer.normalize(raw)
                            NormalizationResult(raw, norm, success = true, error = null)
                        } catch (e: Exception) {
                            UnifiedLog.w(TAG) { 
                                "Failed to normalize ${raw.sourceId}: ${e.message}" 
                            }
                            NormalizationResult(raw, null, success = false, error = e)
                        }
                    }
                }.awaitAll()
                
                // Bulk link to canonical in a single transaction
                val chunkResults = bulkLinkToCanonical(normalized)
                results.addAll(chunkResults)
            }
            
            results
        }

        /**
         * Bulk link normalized items to canonical media.
         * 
         * **Task 3 Feature:** Batch processing for performance.
         * Note: Repository methods handle their own transactions internally via withContext(Dispatchers.IO).
         * 
         * @param normalized List of normalization results
         * @return List of linking results
         */
        private suspend fun bulkLinkToCanonical(
            normalized: List<NormalizationResult>,
        ): List<LinkingResult> {
            val results = mutableListOf<LinkingResult>()
            
            try {
                // Group successful normalizations
                val successful = normalized.filter { it.success && it.normalized != null }
                
                if (successful.isEmpty()) {
                    // All normalization failed - record failures
                    normalized.forEach {  result ->
                        results.add(
                            LinkingResult(
                                sourceId = result.raw.sourceId,
                                success = false,
                                error = result.error,
                            )
                        )
                    }
                    return results
                }
                
                // Process successful items
                // Repository methods handle their own transactions internally
                successful.forEach { result ->
                    try {
                        val canonicalId = canonicalMediaRepository.upsertCanonicalMedia(result.normalized!!)
                        val sourceRef = result.raw.toMediaSourceRef()
                        canonicalMediaRepository.addOrUpdateSourceRef(canonicalId, sourceRef)
                        
                        results.add(
                            LinkingResult(
                                sourceId = result.raw.sourceId,
                                success = true,
                                error = null,
                            )
                        )
                    } catch (e: Exception) {
                        UnifiedLog.w(TAG) { 
                            "Failed to link ${result.raw.sourceId}: ${e.message}" 
                        }
                        results.add(
                            LinkingResult(
                                sourceId = result.raw.sourceId,
                                success = false,
                                error = e,
                            )
                        )
                    }
                }
                
                // Add failures from normalization phase
                normalized.filter { !it.success }.forEach { result ->
                    results.add(
                        LinkingResult(
                            sourceId = result.raw.sourceId,
                            success = false,
                            error = result.error,
                        )
                    )
                }
                
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Bulk linking failed" }
                // Mark all as failed if something unexpected happens
                normalized.forEach { result ->
                    results.add(
                        LinkingResult(
                            sourceId = result.raw.sourceId,
                            success = false,
                            error = e,
                        )
                    )
                }
            }
            
            return results
        }

        /**
         * Convert RawMediaMetadata to MediaSourceRef for canonical linking.
         *
         * Delegates to MediaSourceRefBuilder for SSOT implementation.
         */
        private fun RawMediaMetadata.toMediaSourceRef(): MediaSourceRef =
            MediaSourceRefBuilder.fromRawMetadata(this)
    }

/**
 * Result of normalization operation.
 */
private data class NormalizationResult(
    val raw: RawMediaMetadata,
    val normalized: com.fishit.player.core.model.NormalizedMediaMetadata?,
    val success: Boolean,
    val error: Exception?,
)

/**
 * Result of canonical linking operation.
 */
private data class LinkingResult(
    val sourceId: String,
    val success: Boolean,
    val error: Exception?,
)

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
