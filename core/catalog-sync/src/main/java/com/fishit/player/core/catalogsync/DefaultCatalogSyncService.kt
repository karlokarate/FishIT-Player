package com.fishit.player.core.catalogsync

import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.metadata.MediaMetadataNormalizer
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.ids.asPipelineItemId
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.infra.data.telegram.TelegramContentRepository
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogConfig
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogEvent
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogPipeline
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogConfig
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogPipeline
import com.fishit.player.pipeline.xtream.catalog.XtreamItemKind
import com.fishit.player.pipeline.xtream.catalog.XtreamScanPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [CatalogSyncService].
 *
 * Orchestrates catalog synchronization between pipelines and data repositories, with full canonical
 * media unification per MEDIA_NORMALIZATION_CONTRACT.md.
 *
 * **Architecture Compliance:**
 * - Consumes catalog events from pipelines
 * - Normalizes RawMediaMetadata via MediaMetadataNormalizer
 * - Persists to pipeline-specific repos (for fast pipeline-local queries)
 * - Creates canonical entries in CanonicalMediaRepository (for cross-pipeline unification)
 * - Links sources to canonical entries via MediaSourceRef
 * - Emits SyncStatus events for progress tracking
 * - Uses batching for efficient persistence
 *
 * **Performance Enhancements (Dec 2025):**
 * - Phase ordering: Live → Movies → Series (perceived speed)
 * - Per-phase batch sizes (Live=400, Movies=250, Series=150)
 * - Time-based flush (1200ms) ensures progressive UI updates
 * - Performance metrics collection for debug builds
 * - SyncActiveState broadcast for UI flow throttling
 *
 * **Layer Position:** Transport → Pipeline → **CatalogSync** → Normalizer → Data → Domain → UI
 *
 * **Cross-Pipeline Flow:**
 * 1. Pipeline produces RawMediaMetadata
 * 2. CatalogSync stores raw in pipeline-specific repo (fast local queries)
 * 3. CatalogSync normalizes via MediaMetadataNormalizer
 * 4. CatalogSync upserts to CanonicalMediaRepository (cross-pipeline identity)
 * 5. CatalogSync links source via addOrUpdateSourceRef
 * 6. Resume positions work across all sources via percentage-based positioning
 */
@Singleton
class DefaultCatalogSyncService
    @Inject
    constructor(
        private val telegramPipeline: TelegramCatalogPipeline,
        private val xtreamPipeline: XtreamCatalogPipeline,
        private val telegramRepository: TelegramContentRepository,
        private val xtreamCatalogRepository: XtreamCatalogRepository,
        private val xtreamLiveRepository: XtreamLiveRepository,
        private val normalizer: MediaMetadataNormalizer,
        private val canonicalMediaRepository: CanonicalMediaRepository,
        private val checkpointStore: SyncCheckpointStore,
        private val telegramAuthRepository: TelegramAuthRepository,
    ) : CatalogSyncService {
        companion object {
            private const val TAG = "CatalogSyncService"
            private const val SOURCE_TELEGRAM = "telegram"
            private const val SOURCE_XTREAM = "xtream"
            private const val TIME_FLUSH_CHECK_INTERVAL_MS = 200L
        }

        // Sync active state for UI flow throttling
        private val _syncActiveState = MutableStateFlow(SyncActiveState())
        override val syncActiveState: StateFlow<SyncActiveState> = _syncActiveState.asStateFlow()

        // Performance metrics (debug builds only)
        private var lastSyncMetrics: SyncPerfMetrics? = null

        override fun getLastSyncMetrics(): SyncPerfMetrics? = lastSyncMetrics

        override fun syncTelegram(
            chatIds: List<Long>?,
            syncConfig: SyncConfig,
        ): Flow<SyncStatus> =
            syncTelegramInternal(
                chatIds = chatIds,
                syncConfig = syncConfig,
                excludeChatIds = emptySet(),
                chatParallelism = TelegramCatalogConfig.DEFAULT_CHAT_PARALLELISM,
            )

        /**
         * PLATINUM: Enhanced Telegram sync with parallel chat scanning and checkpoint resume.
         *
         * @param chatIds Optional list of specific chat IDs to scan
         * @param syncConfig Batching and progress configuration
         * @param excludeChatIds Chat IDs to skip (from checkpoint resume)
         * @param chatParallelism Number of chats to scan in parallel (default 3)
         * @return Flow of [SyncStatus] events including [SyncStatus.TelegramChatComplete] for checkpoint tracking
         */
        fun syncTelegramPlatinum(
            chatIds: List<Long>? = null,
            syncConfig: SyncConfig = SyncConfig.DEFAULT,
            excludeChatIds: Set<Long> = emptySet(),
            chatParallelism: Int = TelegramCatalogConfig.DEFAULT_CHAT_PARALLELISM,
        ): Flow<SyncStatus> =
            syncTelegramInternal(
                chatIds = chatIds,
                syncConfig = syncConfig,
                excludeChatIds = excludeChatIds,
                chatParallelism = chatParallelism,
            )

        /**
         * Internal Telegram sync implementation with full PLATINUM support.
         */
        private fun syncTelegramInternal(
            chatIds: List<Long>?,
            syncConfig: SyncConfig,
            excludeChatIds: Set<Long>,
            chatParallelism: Int,
        ): Flow<SyncStatus> =
            flow {
                UnifiedLog.i(TAG, "Starting Telegram sync with config: $syncConfig")
                emit(SyncStatus.Started(SOURCE_TELEGRAM))

                val startTimeMs = System.currentTimeMillis()
                val batch = mutableListOf<RawMediaMetadata>()
                var itemsDiscovered = 0L
                var itemsPersisted = 0L

                // Get current user ID for checkpoint validation (account switch detection)
                val currentUserId = telegramAuthRepository.getCurrentUserId()
                UnifiedLog.d(TAG, "Current Telegram userId: $currentUserId")

                // Load existing checkpoint for incremental sync
                var existingCheckpoint =
                    checkpointStore
                        .getTelegramCheckpoint()
                        ?.let { TelegramSyncCheckpoint.decode(it) }
                        ?: TelegramSyncCheckpoint.INITIAL

                // Account switch detection: If checkpoint belongs to different user, force full scan
                if (!existingCheckpoint.isValidFor(currentUserId)) {
                    UnifiedLog.w(
                        TAG,
                        "Checkpoint userId mismatch! " +
                            "Checkpoint userId=${existingCheckpoint.telegramUserId}, " +
                            "current userId=$currentUserId. Forcing full scan.",
                    )
                    checkpointStore.clearTelegramCheckpoint()
                    existingCheckpoint = TelegramSyncCheckpoint.INITIAL
                }

                val isIncremental = !existingCheckpoint.isInitial
                val effectiveExcludeChatIds = excludeChatIds.ifEmpty { existingCheckpoint.processedChatIds }

                UnifiedLog.i(
                    TAG,
                    "Telegram sync mode: ${if (isIncremental) "INCREMENTAL" else "FULL"} " +
                        "(tracked_chats=${existingCheckpoint.trackedChatCount}, " +
                        "exclude_chats=${effectiveExcludeChatIds.size}, parallel=$chatParallelism)",
                )

                // Build pipeline config with high-water marks for incremental sync + PLATINUM parameters
                val pipelineConfig =
                    TelegramCatalogConfig(
                        chatIds = chatIds,
                        highWaterMarks = if (isIncremental) existingCheckpoint.highWaterMarks else null,
                        excludeChatIds = effectiveExcludeChatIds,
                        chatParallelism = chatParallelism,
                    )

                // Track new high-water marks during this sync
                var newHighWaterMarks: Map<Long, Long> = emptyMap()

                try {
                    telegramPipeline.scanCatalog(pipelineConfig).collect { event ->
                        when (event) {
                            is TelegramCatalogEvent.ItemDiscovered -> {
                                itemsDiscovered++
                                batch.add(event.item.raw)

                                if (batch.size >= syncConfig.batchSize) {
                                    persistTelegramBatch(batch, syncConfig)
                                    itemsPersisted += batch.size
                                    batch.clear()
                                }

                                if (itemsDiscovered % syncConfig.emitProgressEvery == 0L) {
                                    emit(
                                        SyncStatus.InProgress(
                                            source = SOURCE_TELEGRAM,
                                            itemsDiscovered = itemsDiscovered,
                                            itemsPersisted = itemsPersisted,
                                        ),
                                    )
                                }
                            }
                            is TelegramCatalogEvent.ScanProgress -> {
                                emit(
                                    SyncStatus.InProgress(
                                        source = SOURCE_TELEGRAM,
                                        itemsDiscovered = event.discoveredItems,
                                        itemsPersisted = itemsPersisted,
                                        currentPhase =
                                            "Scanning ${event.scannedChats}/${event.totalChats} chats",
                                    ),
                                )
                            }
                            is TelegramCatalogEvent.ScanCompleted -> {
                                // Persist remaining batch
                                if (batch.isNotEmpty()) {
                                    persistTelegramBatch(batch, syncConfig)
                                    itemsPersisted += batch.size
                                    batch.clear()
                                }

                                // Update checkpoint with new high-water marks and current userId
                                newHighWaterMarks = event.newHighWaterMarks
                                val updatedCheckpoint =
                                    existingCheckpoint
                                        .updateHighWaterMarks(newHighWaterMarks)
                                        .markComplete(currentUserId)
                                checkpointStore.saveTelegramCheckpoint(updatedCheckpoint.encode())
                                UnifiedLog.i(
                                    TAG,
                                    "Telegram checkpoint saved: ${updatedCheckpoint.trackedChatCount} chats tracked, userId=$currentUserId",
                                )

                                val durationMs = System.currentTimeMillis() - startTimeMs
                                UnifiedLog.i(
                                    TAG,
                                    "Telegram sync completed: $itemsPersisted items in ${durationMs}ms " +
                                        "(incremental=$isIncremental, new_hwm_chats=${newHighWaterMarks.size})",
                                )
                                emit(
                                    SyncStatus.Completed(
                                        source = SOURCE_TELEGRAM,
                                        totalItems = itemsPersisted,
                                        durationMs = durationMs,
                                    ),
                                )
                            }
                            is TelegramCatalogEvent.ScanCancelled -> {
                                // Persist remaining batch before reporting cancellation
                                if (batch.isNotEmpty()) {
                                    persistTelegramBatch(batch, syncConfig)
                                    itemsPersisted += batch.size
                                    batch.clear()
                                }

                                // Save partial checkpoint so next sync can resume
                                if (event.partialHighWaterMarks.isNotEmpty()) {
                                    val partialCheckpoint =
                                        existingCheckpoint
                                            .updateHighWaterMarks(event.partialHighWaterMarks)
                                    checkpointStore.saveTelegramCheckpoint(partialCheckpoint.encode())
                                    UnifiedLog.i(TAG, "Telegram partial checkpoint saved: ${event.partialHighWaterMarks.size} chats")
                                }

                                UnifiedLog.w(
                                    TAG,
                                    "Telegram sync cancelled: $itemsPersisted items persisted",
                                )
                                emit(
                                    SyncStatus.Cancelled(
                                        source = SOURCE_TELEGRAM,
                                        itemsPersisted = itemsPersisted,
                                    ),
                                )
                            }
                            is TelegramCatalogEvent.ChatScanComplete -> {
                                // PLATINUM: Emit checkpoint event for per-chat tracking
                                UnifiedLog.v(TAG) { "Chat ${event.chatId} complete: ${event.itemCount} items" }
                                emit(
                                    SyncStatus.TelegramChatComplete(
                                        source = SOURCE_TELEGRAM,
                                        chatId = event.chatId,
                                        messageCount = event.messageCount,
                                        itemCount = event.itemCount,
                                        newHighWaterMark = event.newHighWaterMark,
                                    ),
                                )
                            }
                            is TelegramCatalogEvent.ChatScanFailed -> {
                                // PLATINUM: Log chat failure but don't emit error (scan continues)
                                UnifiedLog.w(TAG, "Chat ${event.chatId} scan failed: ${event.reason}")
                            }
                            is TelegramCatalogEvent.ScanError -> {
                                UnifiedLog.e(TAG, "Telegram sync error: ${event.reason} - ${event.message}")
                                emit(
                                    SyncStatus.Error(
                                        source = SOURCE_TELEGRAM,
                                        reason = event.reason,
                                        message = event.message,
                                        throwable = event.throwable,
                                    ),
                                )
                            }
                            is TelegramCatalogEvent.ScanStarted -> {
                                UnifiedLog.d(TAG, "Telegram scan started: ${event.chatCount} chats")
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    // Persist remaining batch on cancellation
                    if (batch.isNotEmpty()) {
                        persistTelegramBatch(batch, syncConfig)
                        itemsPersisted += batch.size
                    }
                    emit(SyncStatus.Cancelled(SOURCE_TELEGRAM, itemsPersisted))
                    throw e
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, "Telegram sync failed", e)
                    emit(
                        SyncStatus.Error(
                            source = SOURCE_TELEGRAM,
                            reason = "exception",
                            message = e.message ?: "Unknown error",
                            throwable = e,
                        ),
                    )
                }
            }

        override fun syncXtream(
            includeVod: Boolean,
            includeSeries: Boolean,
            includeEpisodes: Boolean,
            includeLive: Boolean,
            excludeSeriesIds: Set<Int>,
            episodeParallelism: Int,
            syncConfig: SyncConfig,
        ): Flow<SyncStatus> =
            flow {
                UnifiedLog.i(TAG, "Starting Xtream sync with config: $syncConfig")
                emit(SyncStatus.Started(SOURCE_XTREAM))

                val startTimeMs = System.currentTimeMillis()
                val catalogBatch = mutableListOf<RawMediaMetadata>() // VODs only
                val seriesBatch = mutableListOf<RawMediaMetadata>() // Series + Episodes
                val liveBatch = mutableListOf<RawMediaMetadata>() // Live channels
                var itemsDiscovered = 0L
                var itemsPersisted = 0L

                val pipelineConfig =
                    XtreamCatalogConfig(
                        includeVod = includeVod,
                        includeSeries = includeSeries,
                        includeEpisodes = includeEpisodes,
                        includeLive = includeLive,
                        excludeSeriesIds = excludeSeriesIds,
                        episodeParallelism = episodeParallelism,
                    )

                try {
                    xtreamPipeline.scanCatalog(pipelineConfig).collect { event ->
                        when (event) {
                            is XtreamCatalogEvent.ItemDiscovered -> {
                                itemsDiscovered++

                                // Route to appropriate batch based on item kind
                                // Separate batches ensure early flush for each content type
                                when (event.item.kind) {
                                    XtreamItemKind.LIVE -> liveBatch.add(event.item.raw)
                                    XtreamItemKind.SERIES, XtreamItemKind.EPISODE ->
                                        seriesBatch.add(event.item.raw)
                                    else -> catalogBatch.add(event.item.raw) // VOD
                                }

                                // Persist catalog batch (VOD) when full
                                if (catalogBatch.size >= syncConfig.batchSize) {
                                    persistXtreamCatalogBatch(catalogBatch, syncConfig)
                                    itemsPersisted += catalogBatch.size
                                    catalogBatch.clear()
                                }

                                // Persist series batch when full
                                // Use smaller limit (100) to ensure early flush before budget-exceeded
                                val seriesBatchLimit = minOf(100, syncConfig.batchSize)
                                if (seriesBatch.size >= seriesBatchLimit) {
                                    persistXtreamCatalogBatch(seriesBatch, syncConfig)
                                    itemsPersisted += seriesBatch.size
                                    seriesBatch.clear()
                                }

                                // Persist live batch when full
                                // Use smaller limit (100) to ensure early flush before budget-exceeded
                                val liveBatchLimit = minOf(100, syncConfig.batchSize)
                                if (liveBatch.size >= liveBatchLimit) {
                                    persistXtreamLiveBatch(liveBatch)
                                    itemsPersisted += liveBatch.size
                                    liveBatch.clear()
                                }

                                if (itemsDiscovered % syncConfig.emitProgressEvery == 0L) {
                                    emit(
                                        SyncStatus.InProgress(
                                            source = SOURCE_XTREAM,
                                            itemsDiscovered = itemsDiscovered,
                                            itemsPersisted = itemsPersisted,
                                        ),
                                    )
                                }
                            }
                            is XtreamCatalogEvent.ScanProgress -> {
                                emit(
                                    SyncStatus.InProgress(
                                        source = SOURCE_XTREAM,
                                        itemsDiscovered =
                                            (
                                                event.vodCount +
                                                    event.seriesCount +
                                                    event.episodeCount +
                                                    event.liveCount
                                            ).toLong(),
                                        itemsPersisted = itemsPersisted,
                                        currentPhase = event.currentPhase.name,
                                    ),
                                )
                            }
                            is XtreamCatalogEvent.ScanCompleted -> {
                                // Persist remaining batches
                                if (catalogBatch.isNotEmpty()) {
                                    persistXtreamCatalogBatch(catalogBatch, syncConfig)
                                    itemsPersisted += catalogBatch.size
                                    catalogBatch.clear()
                                }
                                if (seriesBatch.isNotEmpty()) {
                                    persistXtreamCatalogBatch(seriesBatch, syncConfig)
                                    itemsPersisted += seriesBatch.size
                                    seriesBatch.clear()
                                }
                                if (liveBatch.isNotEmpty()) {
                                    persistXtreamLiveBatch(liveBatch)
                                    itemsPersisted += liveBatch.size
                                    liveBatch.clear()
                                }

                                val durationMs = System.currentTimeMillis() - startTimeMs
                                UnifiedLog.i(
                                    TAG,
                                    "Xtream sync completed: $itemsPersisted items in ${durationMs}ms",
                                )
                                emit(
                                    SyncStatus.Completed(
                                        source = SOURCE_XTREAM,
                                        totalItems = itemsPersisted,
                                        durationMs = durationMs,
                                    ),
                                )
                            }
                            is XtreamCatalogEvent.ScanCancelled -> {
                                // Persist remaining batches before reporting cancellation
                                if (catalogBatch.isNotEmpty()) {
                                    persistXtreamCatalogBatch(catalogBatch, syncConfig)
                                    itemsPersisted += catalogBatch.size
                                }
                                if (seriesBatch.isNotEmpty()) {
                                    persistXtreamCatalogBatch(seriesBatch, syncConfig)
                                    itemsPersisted += seriesBatch.size
                                }
                                if (liveBatch.isNotEmpty()) {
                                    persistXtreamLiveBatch(liveBatch)
                                    itemsPersisted += liveBatch.size
                                }

                                UnifiedLog.w(TAG, "Xtream sync cancelled: $itemsPersisted items persisted")
                                emit(
                                    SyncStatus.Cancelled(
                                        source = SOURCE_XTREAM,
                                        itemsPersisted = itemsPersisted,
                                    ),
                                )
                            }
                            is XtreamCatalogEvent.ScanError -> {
                                UnifiedLog.e(TAG, "Xtream sync error: ${event.reason} - ${event.message}")
                                emit(
                                    SyncStatus.Error(
                                        source = SOURCE_XTREAM,
                                        reason = event.reason,
                                        message = event.message,
                                        throwable = event.throwable,
                                    ),
                                )
                            }
                            is XtreamCatalogEvent.ScanStarted -> {
                                UnifiedLog.d(
                                    TAG,
                                    "Xtream scan started: VOD=$includeVod, Series=$includeSeries, Live=$includeLive",
                                )
                            }
                            is XtreamCatalogEvent.SeriesEpisodeComplete -> {
                                // Emit for Worker checkpoint tracking (PLATINUM)
                                emit(
                                    SyncStatus.SeriesEpisodeComplete(
                                        source = SOURCE_XTREAM,
                                        seriesId = event.seriesId,
                                        episodeCount = event.episodeCount,
                                    ),
                                )
                            }
                            is XtreamCatalogEvent.SeriesEpisodeFailed -> {
                                // Log but don't fail sync - other series continue
                                UnifiedLog.w(TAG, "Series ${event.seriesId} episode load failed: ${event.reason}")
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    // Persist remaining batches on cancellation
                    if (catalogBatch.isNotEmpty()) {
                        persistXtreamCatalogBatch(catalogBatch, syncConfig)
                        itemsPersisted += catalogBatch.size
                    }
                    if (seriesBatch.isNotEmpty()) {
                        persistXtreamCatalogBatch(seriesBatch, syncConfig)
                        itemsPersisted += seriesBatch.size
                    }
                    if (liveBatch.isNotEmpty()) {
                        persistXtreamLiveBatch(liveBatch)
                        itemsPersisted += liveBatch.size
                    }
                    UnifiedLog.w(TAG) {
                        "CancellationException: flushed remaining batches, persisted=$itemsPersisted"
                    }
                    emit(SyncStatus.Cancelled(SOURCE_XTREAM, itemsPersisted))
                    throw e
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, "Xtream sync failed", e)
                    emit(
                        SyncStatus.Error(
                            source = SOURCE_XTREAM,
                            reason = "exception",
                            message = e.message ?: "Unknown error",
                            throwable = e,
                        ),
                    )
                }
            }

        override suspend fun clearSource(source: String) {
            UnifiedLog.i(TAG, "Clearing source: $source")
            when (source) {
                SOURCE_TELEGRAM -> telegramRepository.deleteAll()
                SOURCE_XTREAM -> {
                    xtreamCatalogRepository.deleteAll()
                    xtreamLiveRepository.deleteAll()
                }
                else -> UnifiedLog.w(TAG, "Unknown source: $source")
            }
        }

        // ========================================================================
        // Enhanced Xtream Sync (Time-Based Batching + Per-Phase Config)
        // ========================================================================

        /**
         * Enhanced Xtream sync with time-based batching and performance metrics.
         *
         * **Key Differences from syncXtream:**
         * - Per-phase batch sizes (Live=400, Movies=250, Series=150)
         * - Time-based flush every 1200ms for progressive UI
         * - Performance metrics collection
         * - SyncActiveState broadcast for UI throttling
         * - Episodes NOT synced by default (lazy loaded)
         */
        override fun syncXtreamEnhanced(
            includeVod: Boolean,
            includeSeries: Boolean,
            includeEpisodes: Boolean,
            includeLive: Boolean,
            config: EnhancedSyncConfig,
        ): Flow<SyncStatus> =
            flow {
                UnifiedLog.i(
                    TAG,
                    "Starting enhanced Xtream sync: live=$includeLive, vod=$includeVod, series=$includeSeries, " +
                        "episodes=$includeEpisodes, canonical_linking=${config.enableCanonicalLinking}",
                )
                emit(SyncStatus.Started(SOURCE_XTREAM))

                // Initialize metrics
                val metrics = SyncPerfMetrics(isEnabled = true)
                lastSyncMetrics = metrics

                // Initialize batch manager
                val batchManager = SyncBatchManager(config, metrics)

                // Convert to SyncConfig for persist methods
                val syncConfig = config.toSyncConfig()

                val startTimeMs = System.currentTimeMillis()
                var itemsDiscovered = 0L
                var itemsPersisted = 0L

                // Broadcast sync active
                _syncActiveState.value =
                    SyncActiveState(
                        isActive = true,
                        source = SOURCE_XTREAM,
                        currentPhase = "INITIALIZING",
                    )

                val pipelineConfig =
                    XtreamCatalogConfig(
                        includeVod = includeVod,
                        includeSeries = includeSeries,
                        includeEpisodes = includeEpisodes,
                        includeLive = includeLive,
                    )

                // Time-based flush job
                var flushJob: Job? = null

                try {
                    coroutineScope {
                        // Start time-based flush checker
                        flushJob =
                            launch {
                                while (isActive) {
                                    delay(TIME_FLUSH_CHECK_INTERVAL_MS)

                                    // Check each phase for time-based flush
                                    for (phase in SyncPhase.entries) {
                                        val toFlush = batchManager.checkTimeBasedFlush(phase)
                                        if (toFlush != null && toFlush.isNotEmpty()) {
                                            val flushStart = System.currentTimeMillis()
                                            when (phase) {
                                                SyncPhase.LIVE -> persistXtreamLiveBatch(toFlush)
                                                else -> persistXtreamCatalogBatch(toFlush, syncConfig)
                                            }
                                            val flushDuration = System.currentTimeMillis() - flushStart
                                            metrics.recordPersist(
                                                phase,
                                                flushDuration,
                                                toFlush.size,
                                                isTimeBased = true,
                                            )
                                            itemsPersisted += toFlush.size

                                            UnifiedLog.d(TAG) {
                                                "Time-based flush $phase: ${toFlush.size} items in ${flushDuration}ms"
                                            }
                                        }
                                    }
                                }
                            }

                        // Collect pipeline events
                        xtreamPipeline.scanCatalog(pipelineConfig).collect { event ->
                            when (event) {
                                is XtreamCatalogEvent.ItemDiscovered -> {
                                    itemsDiscovered++

                                    // Route to appropriate phase/batch
                                    val phase =
                                        when (event.item.kind) {
                                            XtreamItemKind.LIVE -> SyncPhase.LIVE
                                            XtreamItemKind.SERIES -> SyncPhase.SERIES
                                            XtreamItemKind.EPISODE -> SyncPhase.EPISODES
                                            else -> SyncPhase.MOVIES
                                        }

                                    metrics.recordItemsDiscovered(phase, 1)

                                    // Add to batch - may return items to flush
                                    val toFlush = batchManager.add(phase, event.item.raw)
                                    if (toFlush != null && toFlush.isNotEmpty()) {
                                        val flushStart = System.currentTimeMillis()
                                        when (phase) {
                                            SyncPhase.LIVE -> persistXtreamLiveBatch(toFlush)
                                            else -> persistXtreamCatalogBatch(toFlush, syncConfig)
                                        }
                                        val flushDuration = System.currentTimeMillis() - flushStart
                                        metrics.recordPersist(
                                            phase,
                                            flushDuration,
                                            toFlush.size,
                                            isTimeBased = false,
                                        )
                                        itemsPersisted += toFlush.size
                                    }

                                    // Emit progress periodically
                                    if (itemsDiscovered % config.emitProgressEvery == 0L) {
                                        emit(
                                            SyncStatus.InProgress(
                                                source = SOURCE_XTREAM,
                                                itemsDiscovered = itemsDiscovered,
                                                itemsPersisted = itemsPersisted,
                                            ),
                                        )
                                    }
                                }
                                is XtreamCatalogEvent.ScanProgress -> {
                                    val currentPhase = event.currentPhase.name
                                    _syncActiveState.value =
                                        _syncActiveState.value.copy(currentPhase = currentPhase)

                                    // Start metrics for new phase
                                    val syncPhase =
                                        when (event.currentPhase) {
                                            XtreamScanPhase.LIVE -> SyncPhase.LIVE
                                            XtreamScanPhase.VOD -> SyncPhase.MOVIES
                                            XtreamScanPhase.SERIES -> SyncPhase.SERIES
                                            XtreamScanPhase.EPISODES -> SyncPhase.EPISODES
                                        }
                                    metrics.startPhase(syncPhase)

                                    emit(
                                        SyncStatus.InProgress(
                                            source = SOURCE_XTREAM,
                                            itemsDiscovered =
                                                (
                                                    event.vodCount +
                                                        event.seriesCount +
                                                        event.episodeCount +
                                                        event.liveCount
                                                ).toLong(),
                                            itemsPersisted = itemsPersisted,
                                            currentPhase = currentPhase,
                                        ),
                                    )
                                }
                                is XtreamCatalogEvent.ScanCompleted -> {
                                    // Stop time-based flush job
                                    flushJob?.cancel()
                                    flushJob = null

                                    // Flush all remaining batches
                                    val remaining = batchManager.flushAllPhases()
                                    for ((phase, items) in remaining) {
                                        if (items.isNotEmpty()) {
                                            val flushStart = System.currentTimeMillis()
                                            when (phase) {
                                                SyncPhase.LIVE -> persistXtreamLiveBatch(items)
                                                else -> persistXtreamCatalogBatch(items, syncConfig)
                                            }
                                            val flushDuration = System.currentTimeMillis() - flushStart
                                            metrics.recordPersist(
                                                phase,
                                                flushDuration,
                                                items.size,
                                                isTimeBased = false,
                                            )
                                            itemsPersisted += items.size
                                        }
                                        metrics.endPhase(phase)
                                    }

                                    val durationMs = System.currentTimeMillis() - startTimeMs

                                    // Log metrics report
                                    UnifiedLog.i(TAG) {
                                        "Enhanced sync completed:\n${metrics.exportReport()}"
                                    }

                                    emit(
                                        SyncStatus.Completed(
                                            source = SOURCE_XTREAM,
                                            totalItems = itemsPersisted,
                                            durationMs = durationMs,
                                        ),
                                    )
                                }
                                is XtreamCatalogEvent.ScanCancelled -> {
                                    flushJob?.cancel()

                                    // Flush remaining
                                    val remaining = batchManager.flushAllPhases()
                                    for ((phase, items) in remaining) {
                                        if (items.isNotEmpty()) {
                                            when (phase) {
                                                SyncPhase.LIVE -> persistXtreamLiveBatch(items)
                                                else -> persistXtreamCatalogBatch(items, syncConfig)
                                            }
                                            itemsPersisted += items.size
                                        }
                                    }

                                    emit(SyncStatus.Cancelled(SOURCE_XTREAM, itemsPersisted))
                                }
                                is XtreamCatalogEvent.ScanError -> {
                                    flushJob?.cancel()
                                    UnifiedLog.e(
                                        TAG,
                                        "Enhanced sync error: ${event.reason} - ${event.message}",
                                    )
                                    emit(
                                        SyncStatus.Error(
                                            source = SOURCE_XTREAM,
                                            reason = event.reason,
                                            message = event.message,
                                            throwable = event.throwable,
                                        ),
                                    )
                                }
                                is XtreamCatalogEvent.ScanStarted -> {
                                    UnifiedLog.d(TAG) { "Enhanced scan started" }
                                }
                                is XtreamCatalogEvent.SeriesEpisodeComplete -> {
                                    // Emit for Worker checkpoint tracking (PLATINUM)
                                    emit(
                                        SyncStatus.SeriesEpisodeComplete(
                                            source = SOURCE_XTREAM,
                                            seriesId = event.seriesId,
                                            episodeCount = event.episodeCount,
                                        ),
                                    )
                                }
                                is XtreamCatalogEvent.SeriesEpisodeFailed -> {
                                    // Log but don't fail sync - other series continue
                                    UnifiedLog.w(
                                        TAG,
                                        "Series ${event.seriesId} episode load failed: ${event.reason}",
                                    )
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    flushJob?.cancel()

                    // Flush remaining batches
                    val remaining = batchManager.flushAllPhases()
                    for ((phase, items) in remaining) {
                        if (items.isNotEmpty()) {
                            when (phase) {
                                SyncPhase.LIVE -> persistXtreamLiveBatch(items)
                                else -> persistXtreamCatalogBatch(items, syncConfig)
                            }
                            itemsPersisted += items.size
                        }
                    }

                    UnifiedLog.w(TAG) { "Enhanced sync cancelled: $itemsPersisted items persisted" }
                    emit(SyncStatus.Cancelled(SOURCE_XTREAM, itemsPersisted))
                    throw e
                } catch (e: Exception) {
                    flushJob?.cancel()
                    UnifiedLog.e(TAG, "Enhanced sync failed", e)
                    emit(
                        SyncStatus.Error(
                            source = SOURCE_XTREAM,
                            reason = "exception",
                            message = e.message ?: "Unknown error",
                            throwable = e,
                        ),
                    )
                } finally {
                    // Always clear sync active state
                    _syncActiveState.value = SyncActiveState(isActive = false)
                }
            }

        // ========================================================================
        // Private Helpers - Persistence with Canonical Unification
        // ========================================================================

        /**
         * Persist Telegram batch with optional canonical media unification.
         *
         * Per MEDIA_NORMALIZATION_CONTRACT.md:
         * 1. Store raw in pipeline-specific repo (fast local queries) - ALWAYS
         * 2. Normalize via MediaMetadataNormalizer - OPTIONAL (config.enableNormalization)
         * 3. Upsert to CanonicalMediaRepository (cross-pipeline identity) - OPTIONAL (config.enableCanonicalLinking)
         * 4. Link source via MediaSourceRef (enables unified resume) - OPTIONAL (config.enableCanonicalLinking)
         *
         * **TASK 2: Hot Path Relief**
         * - When enableCanonicalLinking=false, skips steps 2-4 for maximum speed
         * - Use for initial sync to get UI tiles visible ASAP
         * - Canonical linking can be done later via backlog worker
         *
         * **PLATINUM Integrity:**
         * - Validates PlaybackHints per source type
         * - Counts linking failures (warning, not fatal)
         */
        private suspend fun persistTelegramBatch(
            items: List<RawMediaMetadata>,
            config: SyncConfig = SyncConfig.DEFAULT,
        ) {
            UnifiedLog.d(TAG) { "Persisting Telegram batch: ${items.size} items (canonical_linking=${config.enableCanonicalLinking})" }

            // Step 1: Store raw in pipeline-specific repo (ALWAYS - for fast Telegram-only queries)
            telegramRepository.upsertAll(items)

            // Step 2-4: Normalize and link to canonical (OPTIONAL - for cross-pipeline unification)
            if (config.enableCanonicalLinking) {
                var linkedCount = 0
                var failedCount = 0
                var playbackHintWarnings = 0

                items.forEach { raw ->
                    try {
                        // PLATINUM: Validate playback hints before linking
                        val hintValidation = validateTelegramPlaybackHints(raw)
                        if (!hintValidation.isValid) {
                            playbackHintWarnings++
                            UnifiedLog.w(TAG) {
                                "Telegram playback hint warning for ${raw.sourceId}: ${hintValidation.reason}"
                            }
                        }

                        // Step 2: Normalize metadata
                        val normalized = normalizer.normalize(raw)

                        // Step 3: Upsert to canonical repository
                        val canonicalId = canonicalMediaRepository.upsertCanonicalMedia(normalized)

                        // Step 4: Link this source to the canonical entry
                        val sourceRef = raw.toMediaSourceRef()
                        canonicalMediaRepository.addOrUpdateSourceRef(canonicalId, sourceRef)
                        linkedCount++
                    } catch (e: Exception) {
                        failedCount++
                        UnifiedLog.w(TAG) { "Failed to link ${raw.sourceId} to canonical: ${e.message}" }
                    }
                }

                // PLATINUM: Log integrity summary (DEBUG level for normal runs)
                if (failedCount > 0 || playbackHintWarnings > 0) {
                    UnifiedLog.w(TAG) {
                        "Telegram batch integrity: linked=$linkedCount failed=$failedCount hint_warnings=$playbackHintWarnings"
                    }
                } else {
                    UnifiedLog.d(TAG) { "Telegram batch complete: linked=$linkedCount" }
                }
            } else {
                UnifiedLog.d(TAG) { "Telegram batch complete: raw stored, canonical linking skipped for speed" }
            }
        }

        /**
         * Persist Xtream catalog batch with optional canonical media unification.
         *
         * Same flow as Telegram: raw storage + optional normalize + canonical link.
         *
         * **TASK 2: Hot Path Relief**
         * - When enableCanonicalLinking=false, only stores raw data for maximum speed
         * - Use for initial sync to get UI tiles visible ASAP
         * - Canonical linking can be done later via backlog worker
         *
         * **PLATINUM Integrity:**
         * - Validates PlaybackHints per content type (VOD/Series/Episode)
         * - Counts linking failures
         */
        private suspend fun persistXtreamCatalogBatch(
            items: List<RawMediaMetadata>,
            config: SyncConfig = SyncConfig.DEFAULT,
        ) {
            UnifiedLog.d(TAG) { "Persisting Xtream catalog batch: ${items.size} items (canonical_linking=${config.enableCanonicalLinking})" }

            // Step 1: Store raw in pipeline-specific repo (ALWAYS)
            xtreamCatalogRepository.upsertAll(items)

            // Step 2-4: Normalize and link to canonical (OPTIONAL)
            if (config.enableCanonicalLinking) {
                var linkedCount = 0
                var failedCount = 0
                var playbackHintWarnings = 0

                items.forEach { raw ->
                    try {
                        // PLATINUM: Validate playback hints before linking
                        val hintValidation = validateXtreamPlaybackHints(raw)
                        if (!hintValidation.isValid) {
                            playbackHintWarnings++
                            UnifiedLog.w(TAG) {
                                "Xtream playback hint warning for ${raw.sourceId}: ${hintValidation.reason}"
                            }
                        }

                        val normalized = normalizer.normalize(raw)
                        val canonicalId = canonicalMediaRepository.upsertCanonicalMedia(normalized)
                        val sourceRef = raw.toMediaSourceRef()
                        canonicalMediaRepository.addOrUpdateSourceRef(canonicalId, sourceRef)
                        linkedCount++
                    } catch (e: Exception) {
                        failedCount++
                        UnifiedLog.w(TAG) { "Failed to link ${raw.sourceId} to canonical: ${e.message}" }
                    }
                }

                // PLATINUM: Log integrity summary
                if (failedCount > 0 || playbackHintWarnings > 0) {
                    UnifiedLog.w(TAG) {
                        "Xtream batch integrity: linked=$linkedCount failed=$failedCount hint_warnings=$playbackHintWarnings"
                    }
                } else {
                    UnifiedLog.d(TAG) { "Xtream batch complete: linked=$linkedCount" }
                }
            } else {
                UnifiedLog.d(TAG) { "Xtream batch complete: raw stored, canonical linking skipped for speed" }
            }
        }

        /**
         * Persist Xtream live batch.
         *
         * Live channels are NOT linked to canonical media (they're ephemeral streams, not on-demand
         * content that benefits from cross-pipeline unification).
         */
        private suspend fun persistXtreamLiveBatch(items: List<RawMediaMetadata>) {
            UnifiedLog.d(TAG) { "Persisting Xtream live batch: ${items.size} items" }
            xtreamLiveRepository.upsertAll(items)
            // Note: Live channels don't get canonical entries - they're ephemeral streams
        }

        // ========================================================================
        // PLATINUM: Playback Hint Validation
        // ========================================================================

        /** Result of playback hint validation. */
        private data class PlaybackHintValidation(
            val isValid: Boolean,
            val reason: String? = null,
        )

        /**
         * Validate Telegram playback hints.
         *
         * Required hints per PLATINUM contract:
         * - chatId (telegram.chatId)
         * - messageId (telegram.messageId)
         * - EITHER remoteId (telegram.remoteId) OR fileId (telegram.fileId)
         */
        private fun validateTelegramPlaybackHints(raw: RawMediaMetadata): PlaybackHintValidation {
            val hints = raw.playbackHints

            val chatId = hints[PlaybackHintKeys.Telegram.CHAT_ID]
            val messageId = hints[PlaybackHintKeys.Telegram.MESSAGE_ID]
            val remoteId = hints[PlaybackHintKeys.Telegram.REMOTE_ID]
            val fileId = hints[PlaybackHintKeys.Telegram.FILE_ID]

            return when {
                chatId.isNullOrBlank() -> PlaybackHintValidation(false, "missing chatId")
                messageId.isNullOrBlank() -> PlaybackHintValidation(false, "missing messageId")
                remoteId.isNullOrBlank() && fileId.isNullOrBlank() ->
                    PlaybackHintValidation(false, "missing both remoteId and fileId")
                else -> PlaybackHintValidation(true)
            }
        }

        /**
         * Validate Xtream playback hints.
         *
         * Required hints per PLATINUM contract:
         * - contentType (xtream.contentType)
         * - For VOD: vodId (xtream.vodId) + containerExtension
         * - For Series/Episode: seriesId + seasonNumber + episodeNumber + episodeId +
         * containerExtension
         * - For Live: streamId (xtream.streamId)
         */
        private fun validateXtreamPlaybackHints(raw: RawMediaMetadata): PlaybackHintValidation {
            val hints = raw.playbackHints

            val contentType = hints[PlaybackHintKeys.Xtream.CONTENT_TYPE]

            return when (contentType) {
                PlaybackHintKeys.Xtream.CONTENT_VOD -> {
                    val vodId = hints[PlaybackHintKeys.Xtream.VOD_ID]
                    val containerExt = hints[PlaybackHintKeys.Xtream.CONTAINER_EXT]
                    when {
                        vodId.isNullOrBlank() -> PlaybackHintValidation(false, "VOD missing vodId")
                        containerExt.isNullOrBlank() ->
                            PlaybackHintValidation(false, "VOD missing containerExtension")
                        else -> PlaybackHintValidation(true)
                    }
                }
                PlaybackHintKeys.Xtream.CONTENT_SERIES -> {
                    val seriesId = hints[PlaybackHintKeys.Xtream.SERIES_ID]
                    val seasonNum = hints[PlaybackHintKeys.Xtream.SEASON_NUMBER]
                    val episodeNum = hints[PlaybackHintKeys.Xtream.EPISODE_NUMBER]
                    val episodeId = hints[PlaybackHintKeys.Xtream.EPISODE_ID]
                    val containerExt = hints[PlaybackHintKeys.Xtream.CONTAINER_EXT]
                    when {
                        seriesId.isNullOrBlank() ->
                            PlaybackHintValidation(false, "Episode missing seriesId")
                        seasonNum.isNullOrBlank() ->
                            PlaybackHintValidation(false, "Episode missing seasonNumber")
                        episodeNum.isNullOrBlank() ->
                            PlaybackHintValidation(false, "Episode missing episodeNumber")
                        episodeId.isNullOrBlank() ->
                            PlaybackHintValidation(
                                false,
                                "Episode missing episodeId (critical for playback)",
                            )
                        containerExt.isNullOrBlank() ->
                            PlaybackHintValidation(false, "Episode missing containerExtension")
                        else -> PlaybackHintValidation(true)
                    }
                }
                PlaybackHintKeys.Xtream.CONTENT_LIVE -> {
                    val streamId = hints[PlaybackHintKeys.Xtream.STREAM_ID]
                    when {
                        streamId.isNullOrBlank() ->
                            PlaybackHintValidation(false, "Live missing streamId")
                        else -> PlaybackHintValidation(true)
                    }
                }
                null, "" -> PlaybackHintValidation(false, "missing contentType")
                else -> PlaybackHintValidation(false, "unknown contentType: $contentType")
            }
        }

        // ========================================================================
        // Extension: RawMediaMetadata → MediaSourceRef
        // ========================================================================

        /**
         * Convert RawMediaMetadata to MediaSourceRef for canonical linking.
         *
         * This creates the source reference that links a pipeline item to its canonical media identity,
         * enabling:
         * - Cross-pipeline resume (percentage-based positioning)
         * - Source selection in unified detail screen
         * - Quality/language comparison across sources
         */
        private fun RawMediaMetadata.toMediaSourceRef(): MediaSourceRef =
            MediaSourceRef(
                sourceType = sourceType,
                sourceId = sourceId.asPipelineItemId(),
                sourceLabel = sourceLabel,
                quality = null, // TODO: Extract from RawMediaMetadata.quality when available
                languages =
                null, // TODO: Extract from RawMediaMetadata.languages when available
                format = null, // TODO: Extract from RawMediaMetadata.format when available
                sizeBytes = null, // TODO: Add to RawMediaMetadata
                durationMs = durationMs,
                // v2 PlaybackHints: MUST be preserved for playback URL construction.
                // SSOT is RawMediaMetadata.playbackHints (keys in PlaybackHintKeys).
                playbackHints = playbackHints,
                priority = calculateSourcePriority(),
            )

        /**
         * Calculate source priority for ordering in source selection.
         *
         * Higher values = preferred source. Xtream typically gets higher priority because it provides
         * more structured metadata.
         */
        private fun RawMediaMetadata.calculateSourcePriority(): Int =
            when (sourceType) {
                com.fishit.player.core.model.SourceType.XTREAM -> 100
                com.fishit.player.core.model.SourceType.TELEGRAM -> 50
                com.fishit.player.core.model.SourceType.IO -> 75
                com.fishit.player.core.model.SourceType.AUDIOBOOK -> 25
                else -> 0
            }
    }
