package com.fishit.player.v2.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fishit.player.core.catalogsync.sources.xtream.XtreamSyncConfig
import com.fishit.player.core.catalogsync.sources.xtream.XtreamSyncService
import com.fishit.player.core.model.repository.NxCategorySelectionRepository
import com.fishit.player.core.model.sync.DeviceProfile
import com.fishit.player.core.model.sync.SyncStatus
import com.fishit.player.core.persistence.cache.HomeCacheInvalidator
import com.fishit.player.core.persistence.obx.NxKeyGenerator
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceId
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Catalog Sync Orchestrator Worker - Unified Entry Point.
 *
 * **REFACTORED (Feb 2026):** From Worker-chain orchestrator to direct service caller.
 * Eliminates entire worker layer by calling sync services directly.
 *
 * **Old Architecture (6 Workers):**
 * ```
 * Orchestrator
 *   ├─> XtreamPreflightWorker → XtreamCatalogScanWorker
 *   ├─> TelegramAuthPreflightWorker → TelegramFullHistoryScanWorker
 *   └─> IoQuickScanWorker
 * ```
 *
 * **New Architecture (1 Worker):**
 * ```
 * Orchestrator (this)
 *   ├─> xtreamSyncService.sync()
 *   ├─> telegramSyncService.sync()
 *   └─> ioSyncService.sync()
 * ```
 *
 * **Benefits:**
 * - ~1500+ lines of worker code eliminated
 * - No redundant Guard checks (once, not per-worker)
 * - TRUE parallel execution (coroutines, not WorkManager chains)
 * - Simpler debugging (single stack trace)
 * - Faster startup (no WorkManager chain setup)
 *
 * **Responsibilities:**
 * 1. Check active sources
 * 2. Runtime guards (battery, network)
 * 3. Parallel sync execution via async/await
 * 4. Progress aggregation
 * 5. Cache invalidation
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-8: No-Source Behavior (MANDATORY)
 * - W-16: Runtime Guards (MANDATORY)
 * - W-17: FireTV Safety via DeviceProfile
 *
 * @see XtreamSyncService
 * @see TelegramSyncService (TODO)
 * @see IoSyncService (TODO)
 */
@HiltWorker
class CatalogSyncOrchestratorWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val sourceActivationStore: SourceActivationStore,
        private val xtreamSyncService: XtreamSyncService,
        // TODO: Add TelegramSyncService when available
        // TODO: Add IoSyncService when available
        private val homeCacheInvalidator: HomeCacheInvalidator,
        private val categorySelectionRepository: NxCategorySelectionRepository,
        private val xtreamApiClient: XtreamApiClient,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "CatalogSyncOrchestrator"
        }

        override suspend fun doWork(): Result {
            val input = WorkerInputData.from(inputData)
         val startTimeMs = System.currentTimeMillis()

            UnifiedLog.i(TAG) {
                "START sync_run_id=${input.syncRunId} mode=${input.syncMode}"
            }

            // Step 1: Runtime guards (ONCE for all sources)
            val guardReason = RuntimeGuards.checkGuards(applicationContext, input.syncMode)
            if (guardReason != null) {
                UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason mode=${input.syncMode}" }
                return Result.retry()
            }

            // Step 2: Check active sources
            val activeSources = sourceActivationStore.getActiveSources()

            UnifiedLog.i(TAG) {
                "Active sources: $activeSources (isEmpty=${activeSources.isEmpty()})"
            }

            if (activeSources.isEmpty()) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.i(TAG) {
                    "SUCCESS duration_ms=$durationMs (no active sources)"
                }
                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = 0,
                        durationMs = durationMs,
                    ),
                )
            }

            // Step 3: Execute parallel syncs
            try {
                val results = coroutineScope {
                    val xtreamJob = if (SourceId.XTREAM in activeSources) {
                        async { syncXtream(input, startTimeMs) }
                    } else null

                    val telegramJob = if (SourceId.TELEGRAM in activeSources) {
                        async { syncTelegram(input, startTimeMs) }
                    } else null

                    val ioJob = if (SourceId.IO in activeSources) {
                        async { syncIo(input, startTimeMs) }
                    } else null

                    // Await all
                    listOfNotNull(xtreamJob, telegramJob, ioJob).awaitAll()
                }

                // Aggregate results
                val totalItems = results.sumOf { it.itemsPersisted }
                val durationMs = System.currentTimeMillis() - startTimeMs

                UnifiedLog.i(TAG) {
                    "SUCCESS duration_ms=$durationMs total_items=$totalItems " +
                        "sources=${results.size}"
                }

                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = totalItems,
                        durationMs = durationMs,
                    ),
                )
            } catch (_: CancellationException) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.w(TAG) { "Cancelled after ${durationMs}ms" }
                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = 0,
                        durationMs = durationMs,
                    ),
                )
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.e(TAG, e) {
                    "FAILURE reason=${e.javaClass.simpleName} duration_ms=$durationMs"
                }
                return Result.retry()
            }
        }

        /**
         * Sync Xtream source.
         */
        private suspend fun syncXtream(
            input: WorkerInputData,
            startTimeMs: Long,
        ): SyncResult {
            var itemsPersisted = 0L

            UnifiedLog.i(TAG) { "Xtream sync starting..." }

            try {
                // Build config
                val config = buildXtreamSyncConfig(input)

                // Execute sync
                xtreamSyncService.sync(config).collect { status ->
                    // Check cancellation
                    if (!currentCoroutineContext().isActive) {
                        throw CancellationException("Worker cancelled")
                    }

                    // Check runtime budget
                    val elapsedMs = System.currentTimeMillis() - startTimeMs
                    if (elapsedMs > input.maxRuntimeMs) {
                        UnifiedLog.w(TAG) { "Xtream: Max runtime exceeded" }
                        xtreamSyncService.cancel()
                        return@collect
                    }

                    // Track progress
                    when (status) {
                        is SyncStatus.InProgress -> {
                            itemsPersisted = status.processedItems.toLong()
                        }
                        is SyncStatus.Completed -> {
                            val vodCount = status.itemCounts.vodMovies
                            val seriesCount = status.itemCounts.seriesShows
                            val liveCount = status.itemCounts.liveChannels

                            UnifiedLog.i(TAG) {
                                "Xtream SUCCESS: vod=$vodCount series=$seriesCount live=$liveCount"
                            }

                            homeCacheInvalidator.invalidateAllAfterSync(
                                source = "XTREAM",
                                syncRunId = input.syncRunId,
                            )
                        }
                        is SyncStatus.Error -> {
                            throw status.exception ?: RuntimeException(status.message)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Xtream sync failed: ${e.message}" }
                throw e
            }

            return SyncResult(
                source = "XTREAM",
                itemsPersisted = itemsPersisted,
            )
        }

        /**
         * Sync Telegram source.
         *
         * TODO: Implement when TelegramSyncService is available.
         * For now, logs placeholder.
         */
        private suspend fun syncTelegram(
            input: WorkerInputData,
            startTimeMs: Long,
        ): SyncResult {
            UnifiedLog.i(TAG) { "Telegram sync: TODO - awaiting TelegramSyncService" }

            // Placeholder - return empty result
            return SyncResult(
                source = "TELEGRAM",
                itemsPersisted = 0,
            )
        }

        /**
         * Sync IO source.
         *
         * TODO: Implement when IoSyncService is available.
         * For now, logs placeholder.
         */
        private suspend fun syncIo(
            input: WorkerInputData,
            startTimeMs: Long,
        ): SyncResult {
            UnifiedLog.i(TAG) { "IO sync: TODO - awaiting IoSyncService" }

            // Placeholder - return empty result
            return SyncResult(
                source = "IO",
                itemsPersisted = 0,
            )
        }

        /**
         * Build XtreamSyncConfig from input.
         */
        private suspend fun buildXtreamSyncConfig(input: WorkerInputData): XtreamSyncConfig {
            val deviceProfile = when {
                input.isFireTvLowRam -> DeviceProfile.FIRETV_STICK
                input.deviceClass.contains("FIRETV", ignoreCase = true) -> DeviceProfile.FIRETV_CUBE
                input.deviceClass.contains("SHIELD", ignoreCase = true) -> DeviceProfile.SHIELD_TV
                input.deviceClass.contains("CHROMECAST", ignoreCase = true) -> DeviceProfile.CHROMECAST_GTV
                input.deviceClass.contains("TV", ignoreCase = true) -> DeviceProfile.ANDROID_TV_GENERIC
                else -> DeviceProfile.PHONE_HIGH_RAM
            }

            val capabilities = xtreamApiClient.capabilities
            val accountKey = if (capabilities != null) {
                NxKeyGenerator.xtreamAccountKey(
                    serverUrl = capabilities.baseUrl,
                    username = capabilities.username,
                )
            } else {
                "xtream_unknown"
            }

            val (vodCategoryIds, seriesCategoryIds, liveCategoryIds) = if (capabilities != null) {
                Triple(
                    categorySelectionRepository.getSelectedCategoryIds(
                        accountKey,
                        NxCategorySelectionRepository.XtreamCategoryType.VOD,
                    ).toSet(),
                    categorySelectionRepository.getSelectedCategoryIds(
                        accountKey,
                        NxCategorySelectionRepository.XtreamCategoryType.SERIES,
                    ).toSet(),
                    categorySelectionRepository.getSelectedCategoryIds(
                        accountKey,
                        NxCategorySelectionRepository.XtreamCategoryType.LIVE,
                    ).toSet(),
                )
            } else {
                Triple(emptySet(), emptySet(), emptySet())
            }

            return when (input.syncMode) {
                WorkerConstants.SYNC_MODE_INCREMENTAL -> {
                    XtreamSyncConfig.incremental(accountKey).copy(
                        deviceProfile = deviceProfile,
                        vodCategoryIds = vodCategoryIds,
                        seriesCategoryIds = seriesCategoryIds,
                        liveCategoryIds = liveCategoryIds,
                    )
                }
                WorkerConstants.SYNC_MODE_FORCE_RESCAN -> {
                    XtreamSyncConfig.fullSync(accountKey).copy(
                        deviceProfile = deviceProfile,
                        vodCategoryIds = vodCategoryIds,
                        seriesCategoryIds = seriesCategoryIds,
                        liveCategoryIds = liveCategoryIds,
                    )
                }
                else -> {
                    XtreamSyncConfig(
                        accountKey = accountKey,
                        deviceProfile = deviceProfile,
                        forceFullSync = input.syncMode == WorkerConstants.SYNC_MODE_FORCE_RESCAN,
                        syncVod = true,
                        syncSeries = true,
                        syncLive = true,
                        syncEpisodes = false,
                        vodCategoryIds = vodCategoryIds,
                        seriesCategoryIds = seriesCategoryIds,
                        liveCategoryIds = liveCategoryIds,
                    )
                }
            }
        }

        private data class SyncResult(
            val source: String,
            val itemsPersisted: Long,
        )
    }
