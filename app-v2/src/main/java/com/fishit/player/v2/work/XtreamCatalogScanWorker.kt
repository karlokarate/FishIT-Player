package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fishit.player.core.catalogsync.CatalogSyncService
import com.fishit.player.core.catalogsync.SyncCheckpointStore
import com.fishit.player.core.catalogsync.SyncConfig
import com.fishit.player.core.catalogsync.SyncStatus
import com.fishit.player.core.catalogsync.XtreamSyncCheckpoint
import com.fishit.player.core.catalogsync.XtreamSyncPhase
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.persistence.cache.HomeCacheInvalidator
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope

/**
 * Xtream Catalog Scan Worker with resumable checkpoint support.
 *
 * Executes Xtream catalog synchronization via CatalogSyncService ONLY. Supports multi-phase
 * scanning with persistent checkpoints:
 *
 * **Phases (in order):**
 * 1. VOD_LIST - Scan VOD items
 * 2. SERIES_LIST - Scan series containers
 * 3. SERIES_EPISODES - Scan episodes for each series
 * 4. LIVE_LIST - Scan live channels
 * 5. VOD_INFO - Backfill VOD details (plot/cast/director)
 * 6. SERIES_INFO - Backfill series details
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-2: All scanning MUST go through CatalogSyncService
 * - W-17: FireTV Safety (bounded batches, frequent checkpoints)
 * - W-18: Proper backoff on retry
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
        private val checkpointStore: SyncCheckpointStore,
        private val catalogRepository: XtreamCatalogRepository,
        private val liveRepository: XtreamLiveRepository,
        private val xtreamApiClient: XtreamApiClient,
        private val homeCacheInvalidator: HomeCacheInvalidator, // ✅ Phase 2: Cache invalidation
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "XtreamCatalogScanWorker"
            private const val INFO_BACKFILL_BATCH_SIZE = 25 // Tuned Dec 2025: HTTP can parallelize more
            private const val INFO_BACKFILL_THROTTLE_MS =
                100L // Tuned Dec 2025: Most providers allow this
            private const val INFO_BACKFILL_RETRY_MAX_ATTEMPTS = 3 // Max retry attempts for 429/5xx
            private const val INFO_BACKFILL_RETRY_INITIAL_DELAY_MS = 500L // Initial retry delay
            private const val INFO_BACKFILL_RETRY_MAX_DELAY_MS = 5000L // Max retry delay
        }

        override suspend fun doWork(): Result {
            val input = WorkerInputData.from(inputData)
            val startTimeMs = System.currentTimeMillis()
            var itemsPersisted = 0L

            // Load or create checkpoint
            val initialCheckpoint =
                if (input.syncMode == WorkerConstants.SYNC_MODE_FORCE_RESCAN) {
                    // Force rescan: clear checkpoint and start fresh
                    checkpointStore.clearXtreamCheckpoint()
                    XtreamSyncCheckpoint.INITIAL
                } else {
                    val saved = checkpointStore.getXtreamCheckpoint()
                    XtreamSyncCheckpoint.decode(saved)
                }
            var currentCheckpoint = initialCheckpoint

            // Log runtime budget for debugging (W-17: no secrets in logs)
            UnifiedLog.i(TAG) {
                "START sync_run_id=${input.syncRunId} mode=${input.syncMode} source=XTREAM " +
                    "scope=${input.xtreamSyncScope} runtimeBudgetMs=${input.maxRuntimeMs} " +
                    "checkpoint=${currentCheckpoint.encode()}"
            }

            // Check runtime guards (respects sync mode - manual syncs skip battery guards)
            val guardReason = RuntimeGuards.checkGuards(applicationContext, input.syncMode)
            if (guardReason != null) {
                UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason mode=${input.syncMode}" }
                return Result.retry()
            }

            // Helper to check if budget is exceeded
            fun isBudgetExceeded(): Boolean {
                val elapsed = System.currentTimeMillis() - startTimeMs
                return elapsed > input.maxRuntimeMs
            }

            // Helper to save checkpoint and return retry
            suspend fun saveAndRetry(checkpoint: XtreamSyncCheckpoint): Result {
                val encoded = checkpoint.encode()
                checkpointStore.saveXtreamCheckpoint(encoded)
                val elapsed = System.currentTimeMillis() - startTimeMs
                UnifiedLog.i(TAG) {
                    "CHECKPOINT_SAVED cursor=$encoded elapsed=${elapsed}ms persisted=$itemsPersisted"
                }
                return Result.retry()
            }

            try {
                // Process phases based on checkpoint
                when (currentCheckpoint.phase) {
                    XtreamSyncPhase.VOD_LIST,
                    XtreamSyncPhase.SERIES_LIST,
                    XtreamSyncPhase.SERIES_EPISODES,
                    XtreamSyncPhase.LIVE_LIST,
                    -> {
                        // Run catalog sync for list phases
                        val result = runCatalogSync(input, currentCheckpoint, startTimeMs)
                        itemsPersisted = result.itemsPersisted
                        currentCheckpoint = result.checkpoint

                        if (result.budgetExceeded) {
                            return saveAndRetry(currentCheckpoint)
                        }
                    }
                    XtreamSyncPhase.VOD_INFO -> {
                        val result = runVodInfoBackfill(input, currentCheckpoint, startTimeMs)
                        currentCheckpoint = result.checkpoint

                        if (result.budgetExceeded) {
                            return saveAndRetry(currentCheckpoint)
                        }
                    }
                    XtreamSyncPhase.SERIES_INFO -> {
                        val result = runSeriesInfoBackfill(input, currentCheckpoint, startTimeMs)
                        currentCheckpoint = result.checkpoint

                        if (result.budgetExceeded) {
                            return saveAndRetry(currentCheckpoint)
                        }
                    }
                    XtreamSyncPhase.COMPLETED -> {
                        // Already completed - nothing to do
                        UnifiedLog.i(TAG) { "Sync already completed, nothing to do" }
                    }
                }

                // All phases completed successfully
                val durationMs = System.currentTimeMillis() - startTimeMs

                // Log summary with counts
                val vodCount = catalogRepository.count(MediaType.MOVIE)
                val seriesCount = catalogRepository.count(MediaType.SERIES)
                val episodeCount = catalogRepository.count(MediaType.SERIES_EPISODE)
                val liveCount = liveRepository.count()
                val vodBackfillRemaining = catalogRepository.countVodNeedingInfoBackfill()
                val seriesBackfillRemaining = catalogRepository.countSeriesNeedingInfoBackfill()

                UnifiedLog.i(TAG) {
                    "SUCCESS duration_ms=$durationMs | " +
                        "vod=$vodCount series=$seriesCount episodes=$episodeCount live=$liveCount | " +
                        "backfill_remaining: vod=$vodBackfillRemaining series=$seriesBackfillRemaining"
                }

                // ✅ Phase 2: Invalidate Home cache after successful sync
                homeCacheInvalidator.invalidateAllAfterSync(
                    source = "XTREAM",
                    syncRunId = input.syncRunId,
                )

                // Clear checkpoint on full completion
                checkpointStore.clearXtreamCheckpoint()

                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = itemsPersisted,
                        durationMs = durationMs,
                        checkpointCursor = null, // Cleared on success
                    ),
                )
            } catch (e: CancellationException) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                val encoded = currentCheckpoint.encode()
                checkpointStore.saveXtreamCheckpoint(encoded)
                UnifiedLog.w(TAG) { "Cancelled after ${durationMs}ms, checkpoint=$encoded" }
                // Return success so checkpoint is preserved (not a failure)
                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = itemsPersisted,
                        durationMs = durationMs,
                        checkpointCursor = encoded,
                    ),
                )
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                val encoded = currentCheckpoint.encode()
                // HARDENED: Save checkpoint defensively before anything else
                runCatching { checkpointStore.saveXtreamCheckpoint(encoded) }
                UnifiedLog.e(TAG, e) {
                    "FAILURE reason=${e.javaClass.simpleName} duration_ms=$durationMs checkpoint=$encoded"
                }
                // Transient error - retry with backoff
                return Result.retry()
            } catch (t: Throwable) {
                // HARDENED: Catch truly unexpected errors (OOM, StackOverflow, etc.)
                // These should not cause infinite retry loops
                val durationMs = System.currentTimeMillis() - startTimeMs
                val encoded = currentCheckpoint.encode()
                runCatching { checkpointStore.saveXtreamCheckpoint(encoded) }
                UnifiedLog.e(TAG, Exception("Unexpected Throwable: ${t.javaClass.simpleName}", t)) {
                    "FATAL reason=${t.javaClass.simpleName} duration_ms=$durationMs checkpoint=$encoded"
                }
                // Fatal error - fail without retry to prevent infinite loops
                return Result.failure(
                    WorkerOutputData.failure(
                        reason = "Fatal error: ${t.javaClass.simpleName}",
                    ),
                )
            }
        }

        /** Run catalog sync for list phases (VOD_LIST, SERIES_LIST, SERIES_EPISODES, LIVE_LIST). */
        private suspend fun runCatalogSync(
            input: WorkerInputData,
            checkpoint: XtreamSyncCheckpoint,
            startTimeMs: Long,
        ): SyncPhaseResult {
            var itemsPersisted = 0L
            var currentCheckpoint = checkpoint
            var budgetExceeded = false

            // Determine what to include based on checkpoint phase
            val includeVod = checkpoint.phase == XtreamSyncPhase.VOD_LIST
            val includeSeries =
                checkpoint.phase in
                    listOf(
                        XtreamSyncPhase.VOD_LIST,
                        XtreamSyncPhase.SERIES_LIST,
                    )
            // PLATINUM FIX: Episodes are now ALWAYS included in normal runs
            // Previously they were gated behind FULL/FORCE_RESCAN, leaving Episode count = 0
            //
            // New behavior:
            // - INCREMENTAL: Ingest episodes (with checkpoint + bounded runtime)
            // - FULL: Same as INCREMENTAL (episodes always included)
            // - FORCE_RESCAN: Same, but clears checkpoint first
            //
            // The runtime budget (maxRuntimeMs) ensures we don't overrun in INCREMENTAL mode.
            // If budget is exceeded during episode ingest, checkpoint is saved and resumed next run.
            val includeEpisodes =
                checkpoint.phase in
                    listOf(
                        XtreamSyncPhase.VOD_LIST,
                        XtreamSyncPhase.SERIES_LIST,
                        XtreamSyncPhase.SERIES_EPISODES,
                    )
            val includeLive = true // Always include live in list phases

            UnifiedLog.d(TAG) {
                "Catalog sync: includeVod=$includeVod includeSeries=$includeSeries includeEpisodes=$includeEpisodes includeLive=$includeLive scope=${input.xtreamSyncScope} enhanced=${input.xtreamUseEnhancedSync}"
            }

            // PLATINUM: Pass already-processed series IDs for checkpoint resume
            val excludeSeriesIds = checkpoint.processedSeriesIds

            if (excludeSeriesIds.isNotEmpty()) {
                UnifiedLog.i(TAG) {
                    "Resuming episode scan: skipping ${excludeSeriesIds.size} already-processed series"
                }
            }

            // Select sync method: Enhanced (progressive UI) vs. Standard
            if (input.xtreamUseEnhancedSync) {
                // *** TASK 1: Wire up Enhanced Sync ***
                // Use EnhancedSyncConfig for progressive UI and phase-based batching
                val enhancedConfig = selectEnhancedConfig(input)

                UnifiedLog.i(TAG) {
                    "Using ENHANCED sync: live=${enhancedConfig.liveConfig.batchSize} " +
                        "movies=${enhancedConfig.moviesConfig.batchSize} " +
                        "series=${enhancedConfig.seriesConfig.batchSize} " +
                        "timeFlush=${enhancedConfig.enableTimeBasedFlush}"
                }

                catalogSyncService
                    .syncXtreamEnhanced(
                        includeVod = includeVod,
                        includeSeries = includeSeries,
                        includeEpisodes = includeEpisodes,
                        includeLive = includeLive,
                        excludeSeriesIds = excludeSeriesIds,
                        episodeParallelism = 4, // Default parallelism
                        config = enhancedConfig,
                    ).collect { status ->
                        // Check if worker is cancelled
                        if (!currentCoroutineContext().isActive) {
                            throw CancellationException("Worker cancelled")
                        }

                        // Check max runtime
                        val elapsedMs = System.currentTimeMillis() - startTimeMs
                        if (elapsedMs > input.maxRuntimeMs) {
                            budgetExceeded = true
                            return@collect
                        }

                        when (status) {
                            is SyncStatus.Started -> {
                                UnifiedLog.d(TAG) { "Enhanced catalog sync started" }
                            }
                            is SyncStatus.InProgress -> {
                                itemsPersisted = status.itemsPersisted

                                // Update checkpoint based on current phase
                                val phase = parsePhaseFromStatus(status.currentPhase)
                                if (phase != null) {
                                    currentCheckpoint =
                                        XtreamSyncCheckpoint(
                                            phase = phase,
                                            offset = status.itemsPersisted.toInt(),
                                        )
                                }

                                UnifiedLog.d(TAG) {
                                    "PROGRESS discovered=${status.itemsDiscovered} " +
                                        "persisted=$itemsPersisted phase=${status.currentPhase}"
                                }
                            }
                            is SyncStatus.Completed -> {
                                itemsPersisted = status.totalItems
                                // Advance to info backfill phase
                                currentCheckpoint =
                                    XtreamSyncCheckpoint(
                                        phase = XtreamSyncPhase.VOD_INFO,
                                    )
                                UnifiedLog.i(TAG) {
                                    "Enhanced catalog sync completed: ${status.totalItems} items, " +
                                        "advancing to VOD_INFO phase"
                                }
                            }
                            is SyncStatus.Cancelled -> {
                                itemsPersisted = status.itemsPersisted
                                UnifiedLog.w(TAG) { "Enhanced catalog sync cancelled" }
                            }
                            is SyncStatus.Error -> {
                                throw status.throwable ?: RuntimeException(status.message)
                            }
                            is SyncStatus.SeriesEpisodeComplete -> {
                                // PLATINUM: Track completed series for checkpoint resume
                                currentCheckpoint =
                                    currentCheckpoint.withProcessedSeries(status.seriesId)
                                UnifiedLog.v(TAG) {
                                    "Series ${status.seriesId} episodes complete: ${status.episodeCount} eps, " +
                                        "processed=${currentCheckpoint.processedSeriesCount}"
                                }
                            }
                            is SyncStatus.TelegramChatComplete -> {
                                // N/A for Xtream - ignore
                            }
                        }
                    }
            } else {
                // Standard sync (backward compatibility)
                val syncConfig =
                    SyncConfig(
                        batchSize = input.batchSize,
                        enableNormalization = true,
                        emitProgressEvery = input.batchSize,
                    )

                catalogSyncService
                    .syncXtream(
                        includeVod = includeVod,
                        includeSeries = includeSeries,
                        includeEpisodes = includeEpisodes,
                        includeLive = includeLive,
                        excludeSeriesIds = excludeSeriesIds,
                        syncConfig = syncConfig,
                    ).collect { status ->
                    // Check if worker is cancelled
                    if (!currentCoroutineContext().isActive) {
                        throw CancellationException("Worker cancelled")
                    }

                    // Check max runtime
                    val elapsedMs = System.currentTimeMillis() - startTimeMs
                    if (elapsedMs > input.maxRuntimeMs) {
                        budgetExceeded = true
                        return@collect
                    }

                    when (status) {
                        is SyncStatus.Started -> {
                            UnifiedLog.d(TAG) { "Catalog sync started" }
                        }
                        is SyncStatus.InProgress -> {
                            itemsPersisted = status.itemsPersisted

                            // Update checkpoint based on current phase
                            val phase = parsePhaseFromStatus(status.currentPhase)
                            if (phase != null) {
                                currentCheckpoint =
                                    XtreamSyncCheckpoint(
                                        phase = phase,
                                        offset = status.itemsPersisted.toInt(),
                                    )
                            }

                            UnifiedLog.d(TAG) {
                                "PROGRESS discovered=${status.itemsDiscovered} " +
                                    "persisted=$itemsPersisted phase=${status.currentPhase}"
                            }
                        }
                        is SyncStatus.Completed -> {
                            itemsPersisted = status.totalItems
                            // Advance to info backfill phase
                            currentCheckpoint =
                                XtreamSyncCheckpoint(
                                    phase = XtreamSyncPhase.VOD_INFO,
                                )
                            UnifiedLog.i(TAG) {
                                "Catalog sync completed: ${status.totalItems} items, " +
                                    "advancing to VOD_INFO phase"
                            }
                        }
                        is SyncStatus.Cancelled -> {
                            itemsPersisted = status.itemsPersisted
                            UnifiedLog.w(TAG) { "Catalog sync cancelled" }
                        }
                        is SyncStatus.Error -> {
                            throw status.throwable ?: RuntimeException(status.message)
                        }
                        is SyncStatus.SeriesEpisodeComplete -> {
                            // PLATINUM: Track completed series for checkpoint resume
                            currentCheckpoint =
                                currentCheckpoint.withProcessedSeries(status.seriesId)
                            UnifiedLog.v(TAG) {
                                "Series ${status.seriesId} episodes complete: ${status.episodeCount} eps, " +
                                    "processed=${currentCheckpoint.processedSeriesCount}"
                            }
                        }
                        is SyncStatus.TelegramChatComplete -> {
                            // N/A for Xtream - ignore
                        }
                    }
                }
            }

            return SyncPhaseResult(
                itemsPersisted = itemsPersisted,
                checkpoint = currentCheckpoint,
                budgetExceeded = budgetExceeded,
            )
        }

        /**
         * Select EnhancedSyncConfig based on device class and sync mode.
         *
         * Per PLATIN guidelines (app-work.instructions.md):
         * - FireTV Low-RAM: Use FIRETV_SAFE (35-item cap across all phases)
         * - Normal devices: Use PROGRESSIVE_UI (optimized for UI-first loading)
         * - Force rescan: Larger batches for throughput (600/400/200)
         */
        private fun selectEnhancedConfig(input: WorkerInputData): com.fishit.player.core.catalogsync.EnhancedSyncConfig {
            return when {
                // FireTV: Use predefined FIRETV_SAFE config (global 35-item cap to prevent OOM)
                input.isFireTvLowRam -> com.fishit.player.core.catalogsync.EnhancedSyncConfig.FIRETV_SAFE
                
                // Force rescan: Maximize throughput with larger batches
                input.syncMode == WorkerConstants.SYNC_MODE_FORCE_RESCAN -> {
                    com.fishit.player.core.catalogsync.EnhancedSyncConfig(
                        liveConfig =
                            com.fishit.player.core.catalogsync.SyncPhaseConfig.LIVE.copy(
                                batchSize = 600, // Larger than default 400
                            ),
                        moviesConfig =
                            com.fishit.player.core.catalogsync.SyncPhaseConfig.MOVIES.copy(
                                batchSize = 400, // Larger than default 250
                            ),
                        seriesConfig =
                            com.fishit.player.core.catalogsync.SyncPhaseConfig.SERIES.copy(
                                batchSize = 200, // Larger than default 150
                            ),
                        enableTimeBasedFlush = false, // Prioritize throughput over UI
                        enableCanonicalLinking = false, // Disable canonical linking for throughput-optimized rescan
                    )
                }
                // Default: Use PROGRESSIVE_UI for maximum UI-first loading speed
                else -> com.fishit.player.core.catalogsync.EnhancedSyncConfig.PROGRESSIVE_UI
            }
        }

        /** Run VOD info backfill phase. */
        private suspend fun runVodInfoBackfill(
            input: WorkerInputData,
            checkpoint: XtreamSyncCheckpoint,
            startTimeMs: Long,
        ): SyncPhaseResult {
            var currentCheckpoint = checkpoint
            var processedCount = 0

            UnifiedLog.i(TAG) {
                "Starting VOD_INFO backfill from offset=${checkpoint.lastVodInfoId ?: 0}"
            }

            while (currentCoroutineContext().isActive) {
                // Check budget
                val elapsedMs = System.currentTimeMillis() - startTimeMs
                if (elapsedMs > input.maxRuntimeMs) {
                    return SyncPhaseResult(
                        itemsPersisted = 0,
                        checkpoint = currentCheckpoint,
                        budgetExceeded = true,
                    )
                }

                // Get next batch of VOD IDs needing info
                val vodIds =
                    catalogRepository.getVodIdsNeedingInfoBackfill(
                        limit = INFO_BACKFILL_BATCH_SIZE,
                        afterId = currentCheckpoint.lastVodInfoId ?: 0,
                    )

                if (vodIds.isEmpty()) {
                    // No more VODs to process, advance to next phase
                    currentCheckpoint = currentCheckpoint.advancePhase()
                    UnifiedLog.i(TAG) {
                        "VOD_INFO complete, processed=$processedCount, advancing to SERIES_INFO"
                    }
                    break
                }

                // *** TASK 4: Parallel Info Backfill ***
                // Process VODs in parallel with bounded concurrency
                val batchStartTimeMs = System.currentTimeMillis()
                val vodResults =
                    processVodInfoBatchParallel(
                        vodIds = vodIds,
                        concurrency = input.xtreamInfoBackfillConcurrency,
                    )

                // Bulk persist results
                val successful = vodResults.filter { it.success }
                if (successful.isNotEmpty()) {
                    val batchPersistStart = System.currentTimeMillis()
                    // Batch update all successful results
                    successful.forEach { result ->
                        catalogRepository.updateVodInfo(
                            vodId = result.vodId,
                            plot = result.plot,
                            director = result.director,
                            cast = result.cast,
                            genre = result.genre,
                            rating = result.rating,
                            durationSecs = result.durationSecs,
                            trailer = result.trailer,
                            tmdbId = result.tmdbId,
                        )
                    }
                    val persistDuration = System.currentTimeMillis() - batchPersistStart
                    processedCount += successful.size

                    UnifiedLog.d(TAG) {
                        "VOD batch: ${successful.size}/${vodIds.size} successful in ${persistDuration}ms"
                    }
                }

                val batchDuration = System.currentTimeMillis() - batchStartTimeMs
                val itemsPerSec = if (batchDuration > 0) successful.size * 1000 / batchDuration else 0
                UnifiedLog.i(TAG) {
                    "VOD backfill batch complete: ${successful.size} items in ${batchDuration}ms " +
                        "(${itemsPerSec} items/sec, failed=${vodIds.size - successful.size})"
                }

                // Update checkpoint to last processed ID
                currentCheckpoint = currentCheckpoint.withLastVodInfoId(vodIds.last())

                // Brief delay between batches
                delay(INFO_BACKFILL_THROTTLE_MS)
            }

            return SyncPhaseResult(
                itemsPersisted = 0,
                checkpoint = currentCheckpoint,
                budgetExceeded = false,
            )
        }

        /** Run Series info backfill phase. */
        private suspend fun runSeriesInfoBackfill(
            input: WorkerInputData,
            checkpoint: XtreamSyncCheckpoint,
            startTimeMs: Long,
        ): SyncPhaseResult {
            var currentCheckpoint = checkpoint
            var processedCount = 0

            UnifiedLog.i(TAG) {
                "Starting SERIES_INFO backfill from offset=${checkpoint.lastSeriesInfoId ?: 0}"
            }

            while (currentCoroutineContext().isActive) {
                // Check budget
                val elapsedMs = System.currentTimeMillis() - startTimeMs
                if (elapsedMs > input.maxRuntimeMs) {
                    return SyncPhaseResult(
                        itemsPersisted = 0,
                        checkpoint = currentCheckpoint,
                        budgetExceeded = true,
                    )
                }

                // Get next batch of series IDs needing info
                val seriesIds =
                    catalogRepository.getSeriesIdsNeedingInfoBackfill(
                        limit = INFO_BACKFILL_BATCH_SIZE,
                        afterId = currentCheckpoint.lastSeriesInfoId ?: 0,
                    )

                if (seriesIds.isEmpty()) {
                    // No more series to process, mark as completed
                    currentCheckpoint = currentCheckpoint.advancePhase()
                    UnifiedLog.i(TAG) {
                        "SERIES_INFO complete, processed=$processedCount, sync COMPLETED"
                    }
                    break
                }

                // *** TASK 4: Parallel Info Backfill ***
                // Process series in parallel with bounded concurrency
                val batchStartTimeMs = System.currentTimeMillis()
                val seriesResults =
                    processSeriesInfoBatchParallel(
                        seriesIds = seriesIds,
                        concurrency = input.xtreamInfoBackfillConcurrency,
                    )

                // Bulk persist results
                val successful = seriesResults.filter { it.success }
                if (successful.isNotEmpty()) {
                    val batchPersistStart = System.currentTimeMillis()
                    // Batch update all successful results
                    successful.forEach { result ->
                        catalogRepository.updateSeriesInfo(
                            seriesId = result.seriesId,
                            plot = result.plot,
                            director = result.director,
                            cast = result.cast,
                            genre = result.genre,
                            rating = result.rating,
                            trailer = result.trailer,
                            tmdbId = result.tmdbId,
                        )
                    }
                    val persistDuration = System.currentTimeMillis() - batchPersistStart
                    processedCount += successful.size

                    UnifiedLog.d(TAG) {
                        "Series batch: ${successful.size}/${seriesIds.size} successful in ${persistDuration}ms"
                    }
                }

                val batchDuration = System.currentTimeMillis() - batchStartTimeMs
                val itemsPerSec = if (batchDuration > 0) successful.size * 1000 / batchDuration else 0
                UnifiedLog.i(TAG) {
                    "Series backfill batch complete: ${successful.size} items in ${batchDuration}ms " +
                        "(${itemsPerSec} items/sec, failed=${seriesIds.size - successful.size})"
                }

                // Update checkpoint to last processed ID
                currentCheckpoint = currentCheckpoint.withLastSeriesInfoId(seriesIds.last())

                // Brief delay between batches
                delay(INFO_BACKFILL_THROTTLE_MS)
            }

            return SyncPhaseResult(
                itemsPersisted = 0,
                checkpoint = currentCheckpoint,
                budgetExceeded = false,
            )
        }

        /** Parse XtreamSyncPhase from status phase string. */
        private fun parsePhaseFromStatus(phaseString: String?): XtreamSyncPhase? {
            if (phaseString == null) return null
            return when {
                phaseString.contains("VOD", ignoreCase = true) -> XtreamSyncPhase.VOD_LIST
                phaseString.contains("SERIES", ignoreCase = true) &&
                    phaseString.contains("EPISODE", ignoreCase = true) ->
                    XtreamSyncPhase.SERIES_EPISODES
                phaseString.contains("SERIES", ignoreCase = true) -> XtreamSyncPhase.SERIES_LIST
                phaseString.contains("LIVE", ignoreCase = true) -> XtreamSyncPhase.LIVE_LIST
                else -> null
            }
        }

        /** Result from a sync phase. */
        private data class SyncPhaseResult(
            val itemsPersisted: Long,
            val checkpoint: XtreamSyncCheckpoint,
            val budgetExceeded: Boolean,
        )

        // ========================================================================
        // TASK 4: Parallel Info Backfill Helper Methods
        // ========================================================================

        /** Result from VOD info fetch with retry. */
        private data class VodInfoResult(
            val vodId: Int,
            val success: Boolean,
            val plot: String? = null,
            val director: String? = null,
            val cast: String? = null,
            val genre: String? = null,
            val rating: Double? = null,
            val durationSecs: Int? = null,
            val trailer: String? = null,
            val tmdbId: String? = null,
        )

        /** Result from Series info fetch with retry. */
        private data class SeriesInfoResult(
            val seriesId: Int,
            val success: Boolean,
            val plot: String? = null,
            val director: String? = null,
            val cast: String? = null,
            val genre: String? = null,
            val rating: Double? = null,
            val trailer: String? = null,
            val tmdbId: String? = null,
        )

        /**
         * Process VOD info batch in parallel with bounded concurrency.
         *
         * Features:
         * - Bounded concurrency (6-12 normal, 2-4 FireTV)
         * - Exponential backoff retry for 429/5xx errors
         * - Graceful failure handling (continues on error)
         */
        private suspend fun processVodInfoBatchParallel(
            vodIds: List<Int>,
            concurrency: Int,
        ): List<VodInfoResult> =
            coroutineScope {
                // Create a semaphore-like chunking to limit concurrency
                vodIds.chunked(concurrency).flatMap { chunk ->
                    chunk.map { vodId ->
                        async {
                            fetchVodInfoWithRetry(vodId)
                        }
                    }.awaitAll()
                }
            }

        /**
         * Fetch VOD info with exponential backoff retry.
         */
        private suspend fun fetchVodInfoWithRetry(vodId: Int): VodInfoResult {
            var attempt = 0
            var lastException: Exception? = null

            while (attempt < INFO_BACKFILL_RETRY_MAX_ATTEMPTS) {
                try {
                    val vodInfo = xtreamApiClient.getVodInfo(vodId)
                    if (vodInfo != null) {
                        val info = vodInfo.info
                        return VodInfoResult(
                            vodId = vodId,
                            success = true,
                            plot = info?.resolvedPlot,
                            director = info?.director,
                            cast = info?.resolvedCast,
                            genre = info?.resolvedGenre,
                            rating = info?.rating?.toDoubleOrNull(),
                            durationSecs = info?.resolvedDurationMins?.let { it * 60 },
                            trailer = info?.resolvedTrailer,
                            tmdbId = info?.tmdbId?.takeIf { it.isNotBlank() && it != "0" },
                        )
                    } else {
                        // Null response - no retry needed
                        return VodInfoResult(vodId = vodId, success = false)
                    }
                } catch (e: Exception) {
                    lastException = e
                    val isRetryable = isRetryableError(e)

                    if (!isRetryable || attempt >= INFO_BACKFILL_RETRY_MAX_ATTEMPTS - 1) {
                        // Non-retryable or max attempts reached
                        UnifiedLog.w(TAG) {
                            "VOD info fetch failed for vodId=$vodId after ${attempt + 1} attempts: ${e.message}"
                        }
                        return VodInfoResult(vodId = vodId, success = false)
                    }

                    // Exponential backoff
                    val delayMs =
                        minOf(
                            INFO_BACKFILL_RETRY_INITIAL_DELAY_MS * (1 shl attempt),
                            INFO_BACKFILL_RETRY_MAX_DELAY_MS,
                        )
                    UnifiedLog.d(TAG) {
                        "VOD info fetch failed for vodId=$vodId (attempt ${attempt + 1}), retrying in ${delayMs}ms"
                    }
                    delay(delayMs)
                    attempt++
                }
            }

            // Should not reach here, but handle gracefully
            return VodInfoResult(vodId = vodId, success = false)
        }

        /**
         * Process Series info batch in parallel with bounded concurrency.
         */
        private suspend fun processSeriesInfoBatchParallel(
            seriesIds: List<Int>,
            concurrency: Int,
        ): List<SeriesInfoResult> =
            coroutineScope {
                seriesIds.chunked(concurrency).flatMap { chunk ->
                    chunk.map { seriesId ->
                        async {
                            fetchSeriesInfoWithRetry(seriesId)
                        }
                    }.awaitAll()
                }
            }

        /**
         * Fetch Series info with exponential backoff retry.
         */
        private suspend fun fetchSeriesInfoWithRetry(seriesId: Int): SeriesInfoResult {
            var attempt = 0
            var lastException: Exception? = null

            while (attempt < INFO_BACKFILL_RETRY_MAX_ATTEMPTS) {
                try {
                    val seriesInfo = xtreamApiClient.getSeriesInfo(seriesId)
                    if (seriesInfo != null) {
                        val info = seriesInfo.info
                        return SeriesInfoResult(
                            seriesId = seriesId,
                            success = true,
                            plot = info?.resolvedPlot,
                            director = info?.director,
                            cast = info?.resolvedCast,
                            genre = info?.resolvedGenre,
                            rating = info?.rating?.toDoubleOrNull(),
                            trailer = info?.resolvedTrailer,
                            tmdbId = info?.tmdbId?.takeIf { it.isNotBlank() && it != "0" },
                        )
                    } else {
                        return SeriesInfoResult(seriesId = seriesId, success = false)
                    }
                } catch (e: Exception) {
                    lastException = e
                    val isRetryable = isRetryableError(e)

                    if (!isRetryable || attempt >= INFO_BACKFILL_RETRY_MAX_ATTEMPTS - 1) {
                        UnifiedLog.w(TAG) {
                            "Series info fetch failed for seriesId=$seriesId after ${attempt + 1} attempts: ${e.message}"
                        }
                        return SeriesInfoResult(seriesId = seriesId, success = false)
                    }

                    val delayMs =
                        minOf(
                            INFO_BACKFILL_RETRY_INITIAL_DELAY_MS * (1 shl attempt),
                            INFO_BACKFILL_RETRY_MAX_DELAY_MS,
                        )
                    UnifiedLog.d(TAG) {
                        "Series info fetch failed for seriesId=$seriesId (attempt ${attempt + 1}), retrying in ${delayMs}ms"
                    }
                    delay(delayMs)
                    attempt++
                }
            }

            return SeriesInfoResult(seriesId = seriesId, success = false)
        }

        /**
         * Check if an exception is retryable (429 rate limit or 5xx server error).
         */
        private fun isRetryableError(e: Exception): Boolean {
            val message = e.message?.lowercase() ?: ""
            return message.contains("429") || // Too Many Requests
                message.contains("rate limit") ||
                message.contains("500") || // Internal Server Error
                message.contains("502") || // Bad Gateway
                message.contains("503") || // Service Unavailable
                message.contains("504") || // Gateway Timeout
                message.contains("timeout") ||
                message.contains("connection")
        }
    }
