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
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Xtream Catalog Scan Worker - Slim Orchestrator.
 *
 * **REFACTORED (Feb 2026):** Reduced from 1337 → ~250 lines by delegating ALL sync logic
 * to [XtreamSyncService]. This worker is now a thin orchestration layer that handles:
 *
 * 1. Input parsing & validation
 * 2. Runtime guards (battery, network, etc.)
 * 3. Config building from WorkerInputData
 * 4. Service delegation: xtreamSyncService.sync(config)
 * 5. Status collection & progress logging
 * 6. Cache invalidation on success
 *
 * **What was removed (now in XtreamSyncService):**
 * - Phase management (VOD_LIST, SERIES_LIST, etc.)
 * - Info backfill logic (VOD/Series)
 * - Retry logic with exponential backoff
 * - Error classification (retryable vs fatal)
 * - Incremental sync count comparison
 * - Channel buffer setup & consumer management
 * - Checkpoint encoding/decoding logic
 *
 * **Architecture:**
 * Worker (orchestration) → XtreamSyncService (sync logic) → Pipeline → Transport
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-2: All scanning MUST go through unified service
 * - W-17: FireTV Safety via DeviceProfile in config
 * - W-18: Retry handled by WorkManager backoff
 *
 * @see XtreamSyncService Unified sync service
 * @see XtreamSyncConfig Config builder
 */
@HiltWorker
class XtreamCatalogScanWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val xtreamSyncService: XtreamSyncService,
        private val homeCacheInvalidator: HomeCacheInvalidator,
        private val categorySelectionRepository: NxCategorySelectionRepository,
        private val xtreamApiClient: XtreamApiClient,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "XtreamCatalogScanWorker"
        }

        override suspend fun doWork(): Result {
            val input = WorkerInputData.from(inputData)
            val startTimeMs = System.currentTimeMillis()
            var itemsPersisted = 0L

            UnifiedLog.i(TAG) {
                "START sync_run_id=${input.syncRunId} mode=${input.syncMode} source=XTREAM"
            }

            // Step 1: Runtime guards check
            val guardReason = RuntimeGuards.checkGuards(applicationContext, input.syncMode)
            if (guardReason != null) {
                UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason mode=${input.syncMode}" }
                return Result.retry()
            }

            try {
                // Step 2: Build sync configuration
                val config = buildSyncConfig(input)

                UnifiedLog.d(TAG) {
                    "Sync config: vod=${config.syncVod} series=${config.syncSeries} " +
                        "live=${config.syncLive} profile=${config.deviceProfile} " +
                        "forceFullSync=${config.forceFullSync}"
                }

                // Step 3: Execute sync via service
                xtreamSyncService.sync(config).collect { status ->
                    // Check for cancellation
                    if (!currentCoroutineContext().isActive) {
                        UnifiedLog.w(TAG) { "Worker cancelled during sync" }
                        throw CancellationException("Worker cancelled")
                    }

                    // Check runtime budget
                    val elapsedMs = System.currentTimeMillis() - startTimeMs
                    if (elapsedMs > input.maxRuntimeMs) {
                        UnifiedLog.w(TAG) {
                            "Max runtime exceeded (${elapsedMs}ms > ${input.maxRuntimeMs}ms)"
                        }
                        xtreamSyncService.cancel()
                        return@collect
                    }

                    // Handle status
                    when (status) {
                        is SyncStatus.Started -> {
                            UnifiedLog.d(TAG) {
                                "Sync started: fullSync=${status.isFullSync} " +
                                    "phases=${status.estimatedPhases.size}"
                            }
                        }
                        is SyncStatus.InProgress -> {
                            itemsPersisted = status.processedItems.toLong()
                            UnifiedLog.d(TAG) {
                                "PROGRESS: persisted=${status.processedItems} " +
                                    "phase=${status.phase}"
                            }
                        }
                        is SyncStatus.Completed -> {
                            val durationMs = System.currentTimeMillis() - startTimeMs
                            val vodCount = status.itemCounts.vodMovies
                            val seriesCount = status.itemCounts.seriesShows
                            val liveCount = status.itemCounts.liveChannels

                            UnifiedLog.i(TAG) {
                                "SUCCESS duration_ms=$durationMs | " +
                                    "vod=$vodCount series=$seriesCount live=$liveCount | " +
                                    "incremental=${status.wasIncremental}"
                            }

                            // Step 4: Invalidate cache on success
                            homeCacheInvalidator.invalidateAllAfterSync(
                                source = "XTREAM",
                                syncRunId = input.syncRunId,
                            )
                        }
                        is SyncStatus.Error -> {
                            UnifiedLog.e(TAG) {
                                "Sync error: ${status.message}"
                            }
                            throw status.exception ?: RuntimeException(status.message)
                        }
                        is SyncStatus.Cancelled -> {
                            itemsPersisted = status.processedItems.toLong()
                            UnifiedLog.w(TAG) { "Sync cancelled: $itemsPersisted items persisted" }
                        }
                        else -> {
                            // Ignore other status types (Telegram-specific, etc.)
                        }
                    }
                }

                val durationMs = System.currentTimeMillis() - startTimeMs
                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = itemsPersisted,
                        durationMs = durationMs,
                    ),
                )
            } catch (_: CancellationException) {
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.w(TAG) { "Cancelled after ${durationMs}ms, persisted $itemsPersisted items" }
                // Return success to preserve state
                return Result.success(
                    WorkerOutputData.success(
                        itemsPersisted = itemsPersisted,
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
         * Build XtreamSyncConfig from WorkerInputData.
         *
         * Handles:
         * - Device profile detection (FireTV vs Phone/Tablet)
         * - Sync mode mapping (INCREMENTAL vs FULL/FORCE)
         * - Category filters from user selections
         */
        private suspend fun buildSyncConfig(input: WorkerInputData): XtreamSyncConfig {
            // Detect device profile
            val deviceProfile = when {
                input.isFireTvLowRam -> DeviceProfile.FIRETV_STICK
                input.deviceClass.contains("FIRETV", ignoreCase = true) -> DeviceProfile.FIRETV_CUBE
                input.deviceClass.contains("SHIELD", ignoreCase = true) -> DeviceProfile.SHIELD_TV
                input.deviceClass.contains("CHROMECAST", ignoreCase = true) -> DeviceProfile.CHROMECAST_GTV
                input.deviceClass.contains("TV", ignoreCase = true) -> DeviceProfile.ANDROID_TV_GENERIC
                else -> DeviceProfile.PHONE_HIGH_RAM
            }

            // Get accountKey from capabilities
            val capabilities = xtreamApiClient.capabilities
            val accountKey = if (capabilities != null) {
                NxKeyGenerator.xtreamAccountKey(
                    serverUrl = capabilities.baseUrl,
                    username = capabilities.username,
                )
            } else {
                // Fallback - should not happen if preflight passed
                "xtream_unknown"
            }

            // Load category filters (Issue #669)
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

            // Build config based on sync mode
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
                    // AUTO or EXPERT_NOW: use full sync with category filters
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
    }
