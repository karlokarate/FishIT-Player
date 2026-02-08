// Module: core/catalog-sync/sources/xtream
// Unified Xtream sync service implementation

package com.fishit.player.core.catalogsync.sources.xtream

import com.fishit.player.core.catalogsync.IncrementalSyncDecider
import com.fishit.player.core.catalogsync.SyncStrategy
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.sync.DeviceProfile
import com.fishit.player.core.model.sync.SyncPhase
import com.fishit.player.core.model.sync.SyncStatus
import com.fishit.player.core.persistence.repository.FingerprintRepository
import com.fishit.player.core.persistence.repository.SyncCheckpointRepository
import com.fishit.player.core.synccommon.buffer.ChannelSyncBuffer
import com.fishit.player.core.synccommon.checkpoint.SyncCheckpoint
import com.fishit.player.core.synccommon.checkpoint.SyncCheckpointStore
import com.fishit.player.core.synccommon.device.DeviceProfileDetector
import com.fishit.player.core.synccommon.metrics.SyncPerfMetrics
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogConfig
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogPipeline
import com.fishit.player.pipeline.xtream.catalog.XtreamCategoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Default implementation of unified Xtream sync service.
 *
 * **Architecture:**
 * - Uses ChannelSyncBuffer for producer/consumer pattern
 * - Leverages IncrementalSyncDecider for change detection
 * - Persists checkpoints via SyncCheckpointStore
 * - Detects device profile for adaptive tuning
 *
 * **Target Complexity:**
 * - Cyclomatic Complexity ≤ 8 per method
 * - Total LOC ~ 350
 * - Single Responsibility: orchestration only
 *
 * @see XtreamSyncConfig Configuration options
 */
@Singleton
class DefaultXtreamSyncService @Inject constructor(
    private val pipeline: XtreamCatalogPipeline,
    private val catalogRepository: XtreamCatalogRepository,
    private val liveRepository: XtreamLiveRepository,
    private val checkpointStore: SyncCheckpointStore,
    private val incrementalSyncDecider: IncrementalSyncDecider,
    private val deviceProfileDetector: DeviceProfileDetector,
    private val syncPerfMetrics: SyncPerfMetrics,
    private val fingerprintRepository: FingerprintRepository,
    private val syncCheckpointRepository: SyncCheckpointRepository,
) : XtreamSyncService {

    private var activeJob: Job? = null
    private var activeScope: CoroutineScope? = null

    override val isActive: Boolean
        get() = activeJob?.isActive == true

    override fun sync(config: XtreamSyncConfig): Flow<SyncStatus> = flow {
        if (!config.hasContentToSync) {
            emit(SyncStatus.Completed(
                source = SYNC_SOURCE,
                totalDuration = 0.milliseconds,
                itemCounts = SyncStatus.Completed.ItemCounts(),
                wasIncremental = false,
            ))
            return@flow
        }

        // Resolve device profile
        val profile = resolveDeviceProfile(config)
        log("Starting Xtream sync: profile=$profile, forceFullSync=${config.forceFullSync}")

        // Check for incremental sync strategy (unless forced full)
        val syncStrategy = if (!config.forceFullSync) {
            checkSyncStrategy(config)
        } else {
            SyncStrategy.FullSync("User requested full sync")
        }

        when (syncStrategy) {
            is SyncStrategy.SkipSync -> {
                log("Sync skipped: ${syncStrategy.reason}")
                emit(SyncStatus.Completed(
                    source = SYNC_SOURCE,
                    totalDuration = 0.milliseconds,
                    itemCounts = SyncStatus.Completed.ItemCounts(),
                    wasIncremental = false,
                ))
                return@flow
            }
            is SyncStrategy.IncrementalSync,
            is SyncStrategy.FullSync -> {
                // Continue with sync
            }
        }

        // Emit started status
        val startTime = System.currentTimeMillis()
        val estimatedPhases = listOfNotNull(
            if (config.syncLive) SyncPhase.LIVE_CHANNELS else null,
            if (config.syncVod) SyncPhase.VOD_MOVIES else null,
            if (config.syncSeries) SyncPhase.SERIES_INDEX else null,
            if (config.syncEpisodes) SyncPhase.SERIES_EPISODES else null,
        )
        emit(SyncStatus.Started(
            source = SYNC_SOURCE,
            accountKey = config.accountKey,
            isFullSync = syncStrategy is SyncStrategy.FullSync,
            estimatedPhases = estimatedPhases,
        ))
        syncPerfMetrics.startSync()

        // Create cancellable scope
        val scope = CoroutineScope(SupervisorJob())
        activeScope = scope
        activeJob = scope.coroutineContext[Job]

        // Track total items - declared outside try for catch access
        var totalItems = 0L

        try {
            // Load or create checkpoint
            val checkpoint = if (config.enableCheckpoints) {
                checkpointStore.getCheckpoint(checkpointKey(config.accountKey))
            } else {
                null
            }
            val startPhase = checkpoint?.getStartPhase(SyncPhase.LIVE_CHANNELS) ?: SyncPhase.LIVE_CHANNELS
            log("Loaded checkpoint: startPhase=$startPhase")

            // Create buffer with profile-tuned sizing
            val bufferSize = config.getEffectiveBufferSize(profile)
            val buffer = ChannelSyncBuffer<XtreamSyncItem>(capacity = bufferSize)

            // Phase: Live
            if (config.syncLive && shouldRunPhase(startPhase, SyncPhase.LIVE_CHANNELS)) {
                syncPerfMetrics.startPhase(SyncPhase.LIVE_CHANNELS)
                emit(SyncStatus.InProgress(
                    source = SYNC_SOURCE,
                    phase = SyncPhase.LIVE_CHANNELS,
                    processedItems = 0,
                    totalItems = null,
                ))
                val lastSyncTimeMs = getLastSyncTimeForContentType(config.accountKey, "live")
                val result = executeLivePhase(config, buffer, scope, syncStrategy, lastSyncTimeMs)
                totalItems += result.processedCount
                saveCheckpointIfEnabled(config, SyncPhase.LIVE_CHANNELS, totalItems)
                syncPerfMetrics.endPhase(SyncPhase.LIVE_CHANNELS)
                log("Live phase: processed=${result.processedCount}, skippedByTimestamp=${result.skippedByTimestamp}, skippedByFingerprint=${result.skippedByFingerprint}")
                emit(SyncStatus.InProgress(
                    source = SYNC_SOURCE,
                    phase = SyncPhase.LIVE_CHANNELS,
                    processedItems = result.processedCount,
                    totalItems = result.processedCount,
                ))
            }

            // Phase: VOD
            if (config.syncVod && shouldRunPhase(startPhase, SyncPhase.VOD_MOVIES)) {
                syncPerfMetrics.startPhase(SyncPhase.VOD_MOVIES)
                emit(SyncStatus.InProgress(
                    source = SYNC_SOURCE,
                    phase = SyncPhase.VOD_MOVIES,
                    processedItems = 0,
                    totalItems = null,
                ))
                val lastSyncTimeMs = getLastSyncTimeForContentType(config.accountKey, "vod")
                val result = executeVodPhase(config, buffer, scope, syncStrategy, lastSyncTimeMs)
                totalItems += result.processedCount
                saveCheckpointIfEnabled(config, SyncPhase.VOD_MOVIES, totalItems)
                syncPerfMetrics.endPhase(SyncPhase.VOD_MOVIES)
                log("VOD phase: processed=${result.processedCount}, skippedByTimestamp=${result.skippedByTimestamp}, skippedByFingerprint=${result.skippedByFingerprint}")
                emit(SyncStatus.InProgress(
                    source = SYNC_SOURCE,
                    phase = SyncPhase.VOD_MOVIES,
                    processedItems = result.processedCount,
                    totalItems = result.processedCount,
                ))
            }

            // Phase: Series
            if (config.syncSeries && shouldRunPhase(startPhase, SyncPhase.SERIES_INDEX)) {
                syncPerfMetrics.startPhase(SyncPhase.SERIES_INDEX)
                emit(SyncStatus.InProgress(
                    source = SYNC_SOURCE,
                    phase = SyncPhase.SERIES_INDEX,
                    processedItems = 0,
                    totalItems = null,
                ))
                val lastSyncTimeMs = getLastSyncTimeForContentType(config.accountKey, "series")
                val result = executeSeriesPhase(config, buffer, scope, syncStrategy, lastSyncTimeMs)
                totalItems += result.processedCount
                saveCheckpointIfEnabled(config, SyncPhase.SERIES_INDEX, totalItems)
                syncPerfMetrics.endPhase(SyncPhase.SERIES_INDEX)
                log("Series phase: processed=${result.processedCount}, skippedByTimestamp=${result.skippedByTimestamp}, skippedByFingerprint=${result.skippedByFingerprint}")
                emit(SyncStatus.InProgress(
                    source = SYNC_SOURCE,
                    phase = SyncPhase.SERIES_INDEX,
                    processedItems = result.processedCount,
                    totalItems = result.processedCount,
                ))
            }

            // Phase: Episodes (optional, expensive)
            if (config.syncEpisodes && shouldRunPhase(startPhase, SyncPhase.SERIES_EPISODES)) {
                syncPerfMetrics.startPhase(SyncPhase.SERIES_EPISODES)
                emit(SyncStatus.InProgress(
                    source = SYNC_SOURCE,
                    phase = SyncPhase.SERIES_EPISODES,
                    processedItems = 0,
                    totalItems = null,
                ))
                val lastSyncTimeMs = getLastSyncTimeForContentType(config.accountKey, "episodes")
                val result = executeEpisodesPhase(config, buffer, scope, syncStrategy, lastSyncTimeMs)
                totalItems += result.processedCount
                saveCheckpointIfEnabled(config, SyncPhase.SERIES_EPISODES, totalItems)
                syncPerfMetrics.endPhase(SyncPhase.SERIES_EPISODES)
                log("Episodes phase: processed=${result.processedCount}, skippedByTimestamp=${result.skippedByTimestamp}, skippedByFingerprint=${result.skippedByFingerprint}")
                emit(SyncStatus.InProgress(
                    source = SYNC_SOURCE,
                    phase = SyncPhase.SERIES_EPISODES,
                    processedItems = result.processedCount,
                    totalItems = result.processedCount,
                ))
            }

            // Clear checkpoint on success
            if (config.enableCheckpoints) {
                checkpointStore.clearCheckpoint(checkpointKey(config.accountKey))
            }

            // Record success
            val durationMs = System.currentTimeMillis() - startTime
            syncPerfMetrics.endSync()
            log("Sync completed: $totalItems items in ${durationMs}ms")
            emit(SyncStatus.Completed(
                source = SYNC_SOURCE,
                totalDuration = durationMs.milliseconds,
                itemCounts = SyncStatus.Completed.ItemCounts(
                    liveChannels = if (config.syncLive) (totalItems / 4).toInt() else 0,
                    vodMovies = if (config.syncVod) (totalItems / 4).toInt() else 0,
                    seriesShows = if (config.syncSeries) (totalItems / 4).toInt() else 0,
                    seriesEpisodes = if (config.syncEpisodes) (totalItems / 4).toInt() else 0,
                ),
                wasIncremental = syncStrategy is SyncStrategy.IncrementalSync,
            ))

        } catch (e: CancellationException) {
            log("Sync cancelled")
            val durationMs = System.currentTimeMillis() - startTime
            emit(SyncStatus.Cancelled(
                source = SYNC_SOURCE,
                reason = SyncStatus.Cancelled.CancelReason.USER_REQUESTED,
                phase = SyncPhase.CANCELLED,
                processedItems = totalItems.toInt(),
                duration = durationMs.milliseconds,
                canResume = config.enableCheckpoints,
            ))
            throw e
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTime
            log("Sync failed: ${e.message}", e)
            emit(SyncStatus.Error(
                source = SYNC_SOURCE,
                errorType = SyncStatus.Error.ErrorType.UNKNOWN,
                message = e.message ?: "Unknown error",
                phase = SyncPhase.ERROR,
                processedItems = totalItems.toInt(),
                exception = e,
                canRetry = true,
            ))
        } finally {
            activeJob = null
            activeScope = null
        }
    }

    override fun cancel() {
        log("Cancel requested")
        activeScope?.cancel()
        activeJob = null
        activeScope = null
    }

    override suspend fun loadCategories(accountKey: String): XtreamCategories {
        log("Loading categories for account: $accountKey")
        return try {
            val config = XtreamCatalogConfig(accountName = accountKey)
            when (val response = pipeline.fetchCategories(config)) {
                is XtreamCategoryResult.Success -> XtreamCategories(
                    vodCategories = response.vodCategories.map { XtreamCategory(it.categoryId, it.categoryName, it.parentId?.toString()) },
                    seriesCategories = response.seriesCategories.map { XtreamCategory(it.categoryId, it.categoryName, it.parentId?.toString()) },
                    liveCategories = response.liveCategories.map { XtreamCategory(it.categoryId, it.categoryName, it.parentId?.toString()) },
                )
                is XtreamCategoryResult.Error -> {
                    log("Failed to load categories: ${response.message}", response.cause)
                    XtreamCategories()
                }
            }
        } catch (e: Exception) {
            log("Failed to load categories: ${e.message}", e)
            XtreamCategories()
        }
    }

    override suspend fun clearCheckpoint(accountKey: String) {
        checkpointStore.clearCheckpoint(checkpointKey(accountKey))
        log("Cleared checkpoint for account: $accountKey")
    }

    override suspend fun getLastSyncTime(accountKey: String): Long? {
        return checkpointStore.getCheckpoint(checkpointKey(accountKey))?.lastSyncTimestamp
    }

    // === Private Helpers ===

    private fun resolveDeviceProfile(config: XtreamSyncConfig): DeviceProfile {
        return if (config.deviceProfile == DeviceProfile.AUTO) {
            deviceProfileDetector.detect()
        } else {
            config.deviceProfile
        }
    }

    private suspend fun checkSyncStrategy(config: XtreamSyncConfig): SyncStrategy {
        // Use the incremental sync decider with correct parameters
        return incrementalSyncDecider.decideSyncStrategy(
            sourceType = "xtream",
            accountId = config.accountKey,
            contentType = "all",
            forceFullSync = config.forceFullSync,
        )
    }

    private suspend fun saveCheckpointIfEnabled(config: XtreamSyncConfig, completedPhase: SyncPhase, totalItems: Long) {
        if (config.enableCheckpoints) {
            checkpointStore.saveCheckpoint(
                key = checkpointKey(config.accountKey),
                checkpoint = SyncCheckpoint(
                    lastCompletedPhase = completedPhase,
                    totalItemsSynced = totalItems,
                    isPartialSync = true,
                ),
            )
        }
    }

    private fun shouldRunPhase(currentStartPhase: SyncPhase, targetPhase: SyncPhase): Boolean {
        // Run if our starting phase is at or before the target phase
        return currentStartPhase.ordinal <= targetPhase.ordinal
    }

    private fun checkpointKey(accountKey: String): String = "xtream_$accountKey"

    private suspend fun getLastSyncTimeForContentType(accountKey: String, contentType: String): Long? {
        return syncCheckpointRepository.getLastSyncTimestamp(SYNC_SOURCE, accountKey, contentType)
            .takeIf { it > 0 }
    }

    /**
     * Result from a phase execution with incremental sync metrics.
     */
    private data class PhaseResult(
        val processedCount: Int,
        val skippedByTimestamp: Int,
        val skippedByFingerprint: Int,
    )

    // === Phase Execution ===

    private suspend fun executeVodPhase(
        config: XtreamSyncConfig,
        buffer: ChannelSyncBuffer<XtreamSyncItem>,
        scope: CoroutineScope,
        syncStrategy: SyncStrategy,
        lastSyncTimeMs: Long?,
    ): PhaseResult {
        val pipelineConfig = XtreamCatalogConfig(
            accountName = config.accountKey,
            includeVod = true,
            includeSeries = false,
            includeEpisodes = false,
            includeLive = false,
            vodCategoryIds = config.vodCategoryIds,
        )
        return executePhase(pipelineConfig, buffer, scope, "VOD", syncStrategy, lastSyncTimeMs, "vod")
    }

    private suspend fun executeSeriesPhase(
        config: XtreamSyncConfig,
        buffer: ChannelSyncBuffer<XtreamSyncItem>,
        scope: CoroutineScope,
        syncStrategy: SyncStrategy,
        lastSyncTimeMs: Long?,
    ): PhaseResult {
        val pipelineConfig = XtreamCatalogConfig(
            accountName = config.accountKey,
            includeVod = false,
            includeSeries = true,
            includeEpisodes = false,
            includeLive = false,
            seriesCategoryIds = config.seriesCategoryIds,
        )
        return executePhase(pipelineConfig, buffer, scope, "Series", syncStrategy, lastSyncTimeMs, "series")
    }

    private suspend fun executeEpisodesPhase(
        config: XtreamSyncConfig,
        buffer: ChannelSyncBuffer<XtreamSyncItem>,
        scope: CoroutineScope,
        syncStrategy: SyncStrategy,
        lastSyncTimeMs: Long?,
    ): PhaseResult {
        val pipelineConfig = XtreamCatalogConfig(
            accountName = config.accountKey,
            includeVod = false,
            includeSeries = true,
            includeEpisodes = true,
            includeLive = false,
            excludeSeriesIds = config.excludeSeriesIds,
            episodeParallelism = config.episodeParallelism,
        )
        return executePhase(pipelineConfig, buffer, scope, "Episodes", syncStrategy, lastSyncTimeMs, "episodes")
    }

    private suspend fun executeLivePhase(
        config: XtreamSyncConfig,
        buffer: ChannelSyncBuffer<XtreamSyncItem>,
        scope: CoroutineScope,
        syncStrategy: SyncStrategy,
        lastSyncTimeMs: Long?,
    ): PhaseResult {
        val pipelineConfig = XtreamCatalogConfig(
            accountName = config.accountKey,
            includeVod = false,
            includeSeries = false,
            includeEpisodes = false,
            includeLive = true,
            liveCategoryIds = config.liveCategoryIds,
        )
        return executePhase(pipelineConfig, buffer, scope, "Live", syncStrategy, lastSyncTimeMs, "live")
    }

    /**
     * Execute a sync phase with Tier 3/4 incremental filtering.
     *
     * **Tier 3 (Timestamp Filter):** Skip items where addedTimestamp < lastSyncTimeMs
     * **Tier 4 (Fingerprint Check):** Skip items where fingerprint matches stored value
     *
     * @param pipelineConfig Pipeline configuration
     * @param buffer Channel buffer for producer/consumer
     * @param scope Coroutine scope for jobs
     * @param phaseName Phase name for logging
     * @param syncStrategy The strategy (Full/Incremental) - filtering only for Incremental
     * @param lastSyncTimeMs Last successful sync timestamp (for Tier 3)
     * @param contentType Content type key ("vod", "series", "live", "episodes")
     * @return Phase result with processed/skipped counts
     */
    private suspend fun executePhase(
        pipelineConfig: XtreamCatalogConfig,
        buffer: ChannelSyncBuffer<XtreamSyncItem>,
        scope: CoroutineScope,
        phaseName: String,
        syncStrategy: SyncStrategy,
        lastSyncTimeMs: Long?,
        contentType: String,
    ): PhaseResult {
        var processedCount = 0
        var skippedByTimestamp = 0
        var skippedByFingerprint = 0
        
        // Tier 4: Load fingerprints for incremental sync
        val fingerprintMap: Map<String, Int> = if (syncStrategy is SyncStrategy.IncrementalSync) {
            fingerprintRepository.getFingerprintsAsMap(
                sourceType = SYNC_SOURCE,
                accountId = pipelineConfig.accountName,
                contentType = contentType,
            )
        } else {
            emptyMap()
        }
        
        // Track new fingerprints to update after successful sync
        val newFingerprints = mutableMapOf<String, Int>()

        // Producer: emit items from pipeline with Tier 3/4 filtering
        val producerJob = scope.launch {
            try {
                pipeline.scanCatalog(pipelineConfig).collect { event ->
                    if (!scope.isActive) return@collect
                    when (event) {
                        is XtreamCatalogEvent.ItemDiscovered -> {
                            val raw = event.item.raw
                            
                            // === Tier 3: Timestamp Filter (only for incremental) ===
                            if (syncStrategy is SyncStrategy.IncrementalSync && lastSyncTimeMs != null) {
                                val itemAddedTimestamp = raw.addedTimestamp
                                if (itemAddedTimestamp != null) {
                                    // addedTimestamp is in seconds, lastSyncTimeMs is in milliseconds
                                    val itemAddedMs = itemAddedTimestamp * 1000L
                                    if (itemAddedMs < lastSyncTimeMs) {
                                        skippedByTimestamp++
                                        return@collect  // Skip old item
                                    }
                                }
                            }
                            
                            // === Tier 4: Fingerprint Check (only for incremental) ===
                            if (syncStrategy is SyncStrategy.IncrementalSync) {
                                val currentFingerprint = computeFingerprint(raw)
                                val storedFingerprint = fingerprintMap[raw.sourceId]
                                
                                if (storedFingerprint != null && storedFingerprint == currentFingerprint) {
                                    skippedByFingerprint++
                                    return@collect  // Skip unchanged item
                                }
                                
                                // Track for later update
                                newFingerprints[raw.sourceId] = currentFingerprint
                            }
                            
                            val item = XtreamSyncItem(raw)
                            buffer.send(item)
                            processedCount++
                        }
                        is XtreamCatalogEvent.ScanProgress -> {
                            // Log progress
                            log("$phaseName progress: vod=${event.vodCount}, series=${event.seriesCount}, live=${event.liveCount}")
                        }
                        else -> {
                            // ScanStarted, ScanCompleted, etc.
                        }
                    }
                }
            } finally {
                buffer.close()
            }
        }

        // Consumer: process items in batches
        val consumerJob = scope.launch {
            buffer.consumeBatched(batchSize = 50) { items ->
                try {
                    if (items.isEmpty()) return@consumeBatched
                    
                    // Group items by type: live channels vs catalog content
                    val (liveItems, catalogItems) = items.partition { 
                        it.raw.mediaType == MediaType.LIVE 
                    }
                    
                    // Persist live channels
                    if (liveItems.isNotEmpty()) {
                        liveRepository.upsertAll(liveItems.map { it.raw })
                        log("Persisted ${liveItems.size} live channels")
                    }
                    
                    // Persist catalog content (VOD, series, episodes)
                    if (catalogItems.isNotEmpty()) {
                        catalogRepository.upsertAll(catalogItems.map { it.raw })
                        log("Persisted ${catalogItems.size} catalog items")
                    }
                } catch (e: Exception) {
                    UnifiedLog.e("DefaultXtreamSyncService", "Error persisting batch: ${e.message}", e)
                }
            }
        }

        // Wait for completion
        producerJob.join()
        consumerJob.join()
        
        // Update fingerprints after successful phase completion
        if (newFingerprints.isNotEmpty()) {
            val syncGeneration = System.currentTimeMillis()
            fingerprintRepository.putFingerprints(
                sourceType = SYNC_SOURCE,
                accountId = pipelineConfig.accountName,
                contentType = contentType,
                fingerprints = newFingerprints,
                syncGeneration = syncGeneration,
            )
            log("Updated ${newFingerprints.size} fingerprints for $contentType")
        }

        log("Phase $phaseName completed: processed=$processedCount, skippedTimestamp=$skippedByTimestamp, skippedFingerprint=$skippedByFingerprint")
        return PhaseResult(processedCount, skippedByTimestamp, skippedByFingerprint)
    }
    
    /**
     * Compute a stable fingerprint for a RawMediaMetadata item.
     *
     * The fingerprint is a hash of key identifying fields that if changed,
     * indicate the item needs to be re-processed.
     *
     * **Fields included:**
     * - originalTitle
     * - year
     * - season/episode (for episodes)
     * - durationMs
     * - poster (presence/change via hashCode)
     * - externalIds (TMDB, IMDB)
     */
    private fun computeFingerprint(raw: RawMediaMetadata): Int {
        return listOf(
            raw.originalTitle,
            raw.year?.toString() ?: "",
            raw.season?.toString() ?: "",
            raw.episode?.toString() ?: "",
            raw.durationMs?.toString() ?: "",
            raw.poster?.hashCode()?.toString() ?: "",
            raw.externalIds.effectiveTmdbId?.toString() ?: "",
            raw.externalIds.imdbId ?: "",
        ).joinToString("|").hashCode()
    }

    private fun log(message: String, error: Throwable? = null) {
        if (error != null) {
            UnifiedLog.e(TAG, message, error)
        } else {
            UnifiedLog.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "XtreamSyncService"
        private const val SYNC_SOURCE = "xtream"
    }
}

/**
 * Internal wrapper for items flowing through the sync buffer.
 *
 * @property raw The normalized RawMediaMetadata from pipeline
 */
internal data class XtreamSyncItem(
    val raw: RawMediaMetadata,
)
