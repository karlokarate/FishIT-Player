package com.fishit.player.core.catalogsync

import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.metadata.MediaMetadataNormalizer
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.persistence.repository.FingerprintRepository
import com.fishit.player.core.persistence.repository.SyncCheckpointRepository
import com.fishit.player.infra.data.nx.writer.NxCatalogWriter
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
 * - Per-phase batch sizes (Live=600, Movies=400, Series=200)
 * - Time-based flush (1200ms) ensures progressive UI updates
 * - Performance metrics collection for debug builds
 * - SyncActiveState broadcast for UI flow throttling
 *
 * **Layer Position:** Transport → Pipeline → **CatalogSync** → Normalizer → Data → Domain → UI
 *
 * **Cross-Pipeline Flow (NX-ONLY as of Jan 2026):**
 * 1. Pipeline produces RawMediaMetadata
 * 2. CatalogSync normalizes via MediaMetadataNormalizer
 * 3. CatalogSync ingests to NX work graph via NxCatalogWriter
 * 4. Resume positions work across all sources via NxWorkUserStateRepository
 *
 * **Migration Note:** Old OBX repositories (TelegramContentRepository, XtreamCatalogRepository,
 * XtreamLiveRepository, CanonicalMediaRepository) are no longer used for writes.
 * All persistence now flows through NxCatalogWriter → NX_Work/NX_WorkSourceRef/NX_WorkVariant.
 */
@Singleton
class DefaultCatalogSyncService
    @Inject
    constructor(
        private val telegramPipeline: TelegramCatalogPipeline,
        private val xtreamPipeline: XtreamCatalogPipeline,
        private val normalizer: MediaMetadataNormalizer,
        private val nxCatalogWriter: NxCatalogWriter,
        private val checkpointStore: SyncCheckpointStore,
        private val telegramAuthRepository: TelegramAuthRepository,
        // Incremental sync components (Jan 2026)
        private val incrementalSyncDecider: IncrementalSyncDecider,
        private val syncCheckpointRepository: SyncCheckpointRepository,
        private val fingerprintRepository: FingerprintRepository,
        // PLATINUM OPTIMIZATION: Sync state management (Jan 2026)
        private val workRepository: com.fishit.player.infra.data.nx.repository.NxWorkRepositoryImpl,
        // ObjectBox store for thread resource cleanup
        private val boxStore: io.objectbox.BoxStore,
    ) : CatalogSyncService {
        companion object {
            private const val TAG = "CatalogSyncService"
            private const val SOURCE_TELEGRAM = "telegram"
            private const val SOURCE_XTREAM = "xtream"
            private const val TIME_FLUSH_CHECK_INTERVAL_MS = 200L
            // PLATINUM: Reduced from 400 to 100 for better memory behavior
            // - Smaller batches = more frequent DB flushes = lower memory peaks
            // - Works better with buffer=300, consumers=3 (100 items per consumer per flush)
            // - ObjectBox bulk insert is efficient even at 100 items
            private const val BATCH_SIZE = 100
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
                var newHighWaterMarks = emptyMap<Long, Long>()

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
                                
                                // TASK 2: Backlog scheduling moved to workers to avoid DI cycle
                                // Workers will schedule backlog processing after sync completes
                                
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

        @Deprecated(
            message = "Use syncXtreamBuffered() for better performance",
            replaceWith = ReplaceWith("syncXtreamBuffered(includeVod, includeSeries, includeEpisodes, includeLive)"),
        )
        @Suppress("DEPRECATION")
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
                UnifiedLog.w(TAG, "⚠️ Using DEPRECATED syncXtream() - consider syncXtreamBuffered() for 52% faster sync")
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
                        batchSize = syncConfig.jsonStreamingBatchSize,
                        // accountName defaults to "xtream" - good enough for single-account setup
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
                                
                                // TASK 2: Backlog scheduling moved to workers to avoid DI cycle
                                // Workers will schedule backlog processing after sync completes
                                
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
                SOURCE_TELEGRAM -> {
                    val count = nxCatalogWriter.clearSourceType(
                        com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType.TELEGRAM
                    )
                    UnifiedLog.i(TAG, "clearSource(TELEGRAM): Removed $count source refs from NX")
                }
                SOURCE_XTREAM -> {
                    val count = nxCatalogWriter.clearSourceType(
                        com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType.XTREAM
                    )
                    UnifiedLog.i(TAG, "clearSource(XTREAM): Removed $count source refs from NX")
                }
                else -> UnifiedLog.w(TAG, "Unknown source: $source")
            }
        }

        // ========================================================================
        // Incremental Sync (4-Tier with Fingerprint Comparison)
        // ========================================================================

        /**
         * Incremental Xtream sync with 4-tier optimization.
         *
         * **Tier Strategy:**
         * - SkipSync: No changes detected → emit Completed immediately
         * - FullSync: First sync or too old → process everything
         * - IncrementalSync: Compare fingerprints → only process new/changed
         *
         * **Design:** docs/v2/INCREMENTAL_SYNC_DESIGN.md
         */
        override fun syncXtreamIncremental(
            accountKey: String,
            includeVod: Boolean,
            includeSeries: Boolean,
            includeLive: Boolean,
            forceFullSync: Boolean,
            syncConfig: SyncConfig,
        ): Flow<SyncStatus> = flow {
            UnifiedLog.i(TAG, "Starting Xtream INCREMENTAL sync: account=$accountKey, forceFullSync=$forceFullSync")
            emit(SyncStatus.Started(SOURCE_XTREAM))

            val startTimeMs = System.currentTimeMillis()
            
            // Determine which content types to sync
            val contentTypes = buildList {
                if (includeVod) add("vod")
                if (includeSeries) add("series")
                if (includeLive) add("live")
            }

            // Decide sync strategy for each content type
            val strategies = mutableMapOf<String, SyncStrategy>()
            for (contentType in contentTypes) {
                val strategy = incrementalSyncDecider.decideSyncStrategy(
                    sourceType = SOURCE_XTREAM,
                    accountId = accountKey,
                    contentType = contentType,
                    forceFullSync = forceFullSync,
                )
                strategies[contentType] = strategy
                UnifiedLog.i(TAG, "[$contentType] Strategy: $strategy")
            }

            // Check if all content types can skip
            val allSkip = strategies.values.all { it is SyncStrategy.SkipSync }
            if (allSkip && !forceFullSync) {
                val lastSyncMs = (strategies.values.first() as SyncStrategy.SkipSync).lastSyncMs
                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.i(TAG, "All content types unchanged - skipping sync (lastSync=${lastSyncMs})")
                emit(SyncStatus.Completed(
                    source = SOURCE_XTREAM,
                    totalItems = 0,
                    durationMs = durationMs,
                ))
                return@flow
            }

            // Record sync start for non-SkipSync content types
            for (contentType in contentTypes) {
                if (strategies[contentType] is SyncStrategy.SkipSync) continue
                syncCheckpointRepository.recordSyncStart(SOURCE_XTREAM, accountKey, contentType)
            }

            // Load fingerprints for incremental comparison
            val fingerprintsMap = mutableMapOf<String, Map<String, Int>>()
            val incrementalGenerations = mutableMapOf<String, Long>()
            
            for ((contentType, strategy) in strategies) {
                if (strategy is SyncStrategy.IncrementalSync) {
                    fingerprintsMap[contentType] = incrementalSyncDecider.getFingerprints(
                        SOURCE_XTREAM, accountKey, contentType
                    )
                    incrementalGenerations[contentType] = strategy.syncGeneration
                    UnifiedLog.d(TAG, "[$contentType] Loaded ${fingerprintsMap[contentType]?.size ?: 0} fingerprints")
                } else if (strategy is SyncStrategy.FullSync) {
                    // For full sync, use generation 1 (or increment from existing)
                    val existingGen = syncCheckpointRepository.getSyncGeneration(SOURCE_XTREAM, accountKey, contentType)
                    incrementalGenerations[contentType] = existingGen + 1
                }
            }

            // Tracking variables
            val catalogBatch = mutableListOf<RawMediaMetadata>()
            val seriesBatch = mutableListOf<RawMediaMetadata>()
            val liveBatch = mutableListOf<RawMediaMetadata>()
            
            // Fingerprints to store after sync
            val newFingerprints = mutableMapOf<String, MutableMap<String, Int>>()
            contentTypes.forEach { newFingerprints[it] = mutableMapOf() }
            
            // Counters per content type
            val itemCounts = mutableMapOf<String, Int>()
            val newItemCounts = mutableMapOf<String, Int>()
            val updatedItemCounts = mutableMapOf<String, Int>()
            val unchangedItemCounts = mutableMapOf<String, Int>()
            val deletedItemCounts = mutableMapOf<String, Int>()  // BUG FIX: Track per content type
            contentTypes.forEach { 
                itemCounts[it] = 0
                newItemCounts[it] = 0
                updatedItemCounts[it] = 0
                unchangedItemCounts[it] = 0
                deletedItemCounts[it] = 0
            }
            
            var itemsDiscovered = 0L
            var itemsPersisted = 0L

            // BUG FIX: Honor SkipSync strategies - don't fetch content types that are skipped
            val actuallyIncludeVod = includeVod && strategies["vod"] !is SyncStrategy.SkipSync
            val actuallyIncludeSeries = includeSeries && strategies["series"] !is SyncStrategy.SkipSync
            val actuallyIncludeLive = includeLive && strategies["live"] !is SyncStrategy.SkipSync

            val pipelineConfig = XtreamCatalogConfig(
                includeVod = actuallyIncludeVod,
                includeSeries = actuallyIncludeSeries,
                includeEpisodes = false, // Episodes handled separately
                includeLive = actuallyIncludeLive,
                batchSize = syncConfig.jsonStreamingBatchSize,
            )

            try {
                // PLATINUM OPTIMIZATION: Enable aggressive throttling during sync
                workRepository.setSyncInProgress(true)
                UnifiedLog.i(TAG) { "Sync started - UI observation throttling enabled" }

                xtreamPipeline.scanCatalog(pipelineConfig).collect { event ->
                    when (event) {
                        is XtreamCatalogEvent.ItemDiscovered -> {
                            itemsDiscovered++
                            val raw = event.item.raw
                            val itemKind = event.item.kind
                            
                            // Determine content type and fingerprint
                            val (contentType, itemId, fingerprint) = when (itemKind) {
                                XtreamItemKind.LIVE -> {
                                    val id = raw.playbackHints["stream_id"] ?: raw.sourceId
                                    Triple("live", id, computeFingerprint(raw))
                                }
                                XtreamItemKind.SERIES -> {
                                    val id = raw.playbackHints["series_id"] ?: raw.sourceId
                                    Triple("series", id, computeFingerprint(raw))
                                }
                                else -> { // VOD
                                    val id = raw.playbackHints["stream_id"] ?: raw.sourceId
                                    Triple("vod", id, computeFingerprint(raw))
                                }
                            }

                            // Track total count
                            itemCounts[contentType] = (itemCounts[contentType] ?: 0) + 1

                            // Check if item needs processing (incremental mode)
                            val existingFingerprints = fingerprintsMap[contentType] ?: emptyMap()
                            val existingFp = existingFingerprints[itemId]
                            
                            val shouldProcess = when {
                                existingFp == null -> {
                                    // New item
                                    newItemCounts[contentType] = (newItemCounts[contentType] ?: 0) + 1
                                    true
                                }
                                existingFp != fingerprint -> {
                                    // Changed item
                                    updatedItemCounts[contentType] = (updatedItemCounts[contentType] ?: 0) + 1
                                    true
                                }
                                else -> {
                                    // Unchanged
                                    unchangedItemCounts[contentType] = (unchangedItemCounts[contentType] ?: 0) + 1
                                    false
                                }
                            }

                            // Store fingerprint for later persistence
                            newFingerprints[contentType]?.put(itemId, fingerprint)

                            // Only add to batch if should process
                            if (shouldProcess) {
                                when (itemKind) {
                                    XtreamItemKind.LIVE -> liveBatch.add(raw)
                                    XtreamItemKind.SERIES -> seriesBatch.add(raw)
                                    else -> catalogBatch.add(raw)
                                }
                            }

                            // Persist batches when full
                            if (catalogBatch.size >= syncConfig.batchSize) {
                                persistXtreamCatalogBatch(catalogBatch, syncConfig)
                                itemsPersisted += catalogBatch.size
                                catalogBatch.clear()
                            }
                            if (seriesBatch.size >= minOf(100, syncConfig.batchSize)) {
                                persistXtreamCatalogBatch(seriesBatch, syncConfig)
                                itemsPersisted += seriesBatch.size
                                seriesBatch.clear()
                            }
                            if (liveBatch.size >= minOf(100, syncConfig.batchSize)) {
                                persistXtreamLiveBatch(liveBatch)
                                itemsPersisted += liveBatch.size
                                liveBatch.clear()
                            }

                            if (itemsDiscovered % syncConfig.emitProgressEvery == 0L) {
                                emit(SyncStatus.InProgress(
                                    source = SOURCE_XTREAM,
                                    itemsDiscovered = itemsDiscovered,
                                    itemsPersisted = itemsPersisted,
                                ))
                            }
                        }
                        is XtreamCatalogEvent.ScanCompleted -> {
                            // Flush remaining batches
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

                            // Store fingerprints for each content type
                            for ((contentType, fps) in newFingerprints) {
                                if (fps.isNotEmpty()) {
                                    val generation = incrementalGenerations[contentType] ?: 1L
                                    fingerprintRepository.putFingerprints(
                                        SOURCE_XTREAM, accountKey, contentType, fps, generation
                                    )
                                }
                            }

                            // Detect and handle deletions (only for non-SkipSync content types)
                            for (contentType in contentTypes) {
                                // BUG FIX: Skip deletion detection for SkipSync types
                                if (strategies[contentType] is SyncStrategy.SkipSync) continue
                                
                                val generation = incrementalGenerations[contentType] ?: continue
                                val staleItems = fingerprintRepository.findStaleItems(
                                    SOURCE_XTREAM, accountKey, contentType, generation
                                )
                                if (staleItems.isNotEmpty()) {
                                    UnifiedLog.i(TAG, "[$contentType] Detected ${staleItems.size} deleted items")
                                    // Delete stale fingerprints (items no longer in catalog)
                                    fingerprintRepository.deleteFingerprints(
                                        SOURCE_XTREAM, accountKey, contentType, staleItems
                                    )
                                    deletedItemCounts[contentType] = staleItems.size
                                    // Note: Actual NX_Work deletion would go here if needed
                                }
                            }

                            // Record checkpoints (only for non-SkipSync content types)
                            for (contentType in contentTypes) {
                                // BUG FIX: Skip checkpoint for SkipSync - previous checkpoint is still valid
                                if (strategies[contentType] is SyncStrategy.SkipSync) continue
                                
                                val isIncremental = strategies[contentType] is SyncStrategy.IncrementalSync
                                syncCheckpointRepository.recordSyncComplete(
                                    sourceType = SOURCE_XTREAM,
                                    accountId = accountKey,
                                    contentType = contentType,
                                    itemCount = itemCounts[contentType] ?: 0,
                                    newItemCount = newItemCounts[contentType] ?: 0,
                                    updatedItemCount = updatedItemCounts[contentType] ?: 0,
                                    deletedItemCount = deletedItemCounts[contentType] ?: 0,  // BUG FIX: Per-type count
                                    wasIncremental = isIncremental,
                                )
                            }

                            val durationMs = System.currentTimeMillis() - startTimeMs
                            val totalNew = newItemCounts.values.sum()
                            val totalUpdated = updatedItemCounts.values.sum()
                            val totalUnchanged = unchangedItemCounts.values.sum()
                            val totalDeleted = deletedItemCounts.values.sum()
                            
                            UnifiedLog.i(TAG) {
                                "Incremental sync complete: discovered=$itemsDiscovered, " +
                                    "persisted=$itemsPersisted, new=$totalNew, updated=$totalUpdated, " +
                                    "unchanged=$totalUnchanged, deleted=$totalDeleted, " +
                                    "savings=${if (itemsDiscovered > 0) (totalUnchanged * 100 / itemsDiscovered) else 0}%, " +
                                    "duration=${durationMs}ms"
                            }

                            emit(SyncStatus.Completed(
                                source = SOURCE_XTREAM,
                                totalItems = itemsPersisted,
                                durationMs = durationMs,
                            ))
                        }
                        is XtreamCatalogEvent.ScanCancelled -> {
                            // Flush remaining
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
                            emit(SyncStatus.Cancelled(SOURCE_XTREAM, itemsPersisted))
                        }
                        is XtreamCatalogEvent.ScanError -> {
                            // Record failure for all content types
                            for (contentType in contentTypes) {
                                syncCheckpointRepository.recordSyncFailure(
                                    SOURCE_XTREAM, accountKey, contentType, event.message
                                )
                            }
                            emit(SyncStatus.Error(
                                source = SOURCE_XTREAM,
                                reason = event.reason,
                                message = event.message,
                                throwable = event.throwable,
                            ))
                        }
                        is XtreamCatalogEvent.ScanStarted -> {
                            UnifiedLog.d(TAG, "Incremental scan started")
                        }
                        is XtreamCatalogEvent.ScanProgress -> {
                            emit(SyncStatus.InProgress(
                                source = SOURCE_XTREAM,
                                itemsDiscovered = (event.vodCount + event.seriesCount + event.liveCount).toLong(),
                                itemsPersisted = itemsPersisted,
                                currentPhase = event.currentPhase.name,
                            ))
                        }
                        is XtreamCatalogEvent.SeriesEpisodeComplete,
                        is XtreamCatalogEvent.SeriesEpisodeFailed -> {
                            // Not applicable for non-episode sync
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (catalogBatch.isNotEmpty()) persistXtreamCatalogBatch(catalogBatch, syncConfig)
                if (seriesBatch.isNotEmpty()) persistXtreamCatalogBatch(seriesBatch, syncConfig)
                if (liveBatch.isNotEmpty()) persistXtreamLiveBatch(liveBatch)
                emit(SyncStatus.Cancelled(SOURCE_XTREAM, itemsPersisted))
                throw e
            } catch (e: Exception) {
                for (contentType in contentTypes) {
                    syncCheckpointRepository.recordSyncFailure(
                        SOURCE_XTREAM, accountKey, contentType, e.message ?: "Unknown error"
                    )
                }
                UnifiedLog.e(TAG, "Incremental sync failed", e)
                emit(SyncStatus.Error(
                    source = SOURCE_XTREAM,
                    reason = "exception",
                    message = e.message ?: "Unknown error",
                    throwable = e,
                ))
            } finally {
                // PLATINUM OPTIMIZATION: Restore normal throttling
                workRepository.setSyncInProgress(false)
                UnifiedLog.i(TAG) { "Sync completed - UI observation throttling disabled" }
            }
        }

        /**
         * Compute fingerprint for a RawMediaMetadata item.
         *
         * Uses key fields that indicate content changes:
         * - sourceId, originalTitle, addedTimestamp, categoryId, poster, rating
         */
        private fun computeFingerprint(raw: RawMediaMetadata): Int {
            return java.util.Objects.hash(
                raw.sourceId,
                raw.originalTitle,
                raw.addedTimestamp,
                raw.categoryId,
                raw.poster?.hashCode(),
                raw.rating,
            )
        }

        // ========================================================================
        // Delta Sync (Incremental with lastModified filtering)
        // ========================================================================

        /**
         * Delta sync: fetches all items but only persists those modified since [sinceTimestampMs].
         *
         * **Why client-side filtering?**
         * The Xtream API doesn't support server-side timestamp filtering.
         * We fetch everything, but filter by `lastModifiedTimestamp` before persisting.
         * This significantly reduces DB write load during incremental syncs.
         *
         * **Performance:**
         * - Network: Same as full sync (fetches all items)
         * - CPU: Slightly higher (filtering logic)
         * - DB I/O: Much lower (only writes changed items)
         * - Memory: Slightly higher (holds timestamp for comparison)
         */
        override fun syncXtreamDelta(
            sinceTimestampMs: Long,
            includeVod: Boolean,
            includeSeries: Boolean,
            includeLive: Boolean,
            config: EnhancedSyncConfig,
        ): Flow<SyncStatus> =
            flow {
                UnifiedLog.i(TAG) {
                    "Starting Xtream DELTA sync: sinceTimestamp=$sinceTimestampMs " +
                        "(${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(sinceTimestampMs))}), " +
                        "vod=$includeVod, series=$includeSeries, live=$includeLive"
                }
                emit(SyncStatus.Started(SOURCE_XTREAM))

                val startTimeMs = System.currentTimeMillis()
                var itemsDiscovered = 0L
                var itemsFiltered = 0L // Items that passed the timestamp filter
                var itemsPersisted = 0L

                // Broadcast sync active
                _syncActiveState.value = SyncActiveState(
                    isActive = true,
                    source = SOURCE_XTREAM,
                    currentPhase = "DELTA_SYNC",
                )

                val pipelineConfig = XtreamCatalogConfig(
                    includeVod = includeVod,
                    includeSeries = includeSeries,
                    includeEpisodes = false, // Delta sync doesn't include episodes
                    includeLive = includeLive,
                    batchSize = config.jsonStreamingBatchSize,
                )

                val syncConfig = config.toSyncConfig()

                // Batch for items that pass the filter
                val filteredBatch = mutableListOf<RawMediaMetadata>()
                val batchSize = config.moviesConfig.batchSize

                try {
                    xtreamPipeline.scanCatalog(pipelineConfig).collect { event ->
                        when (event) {
                            is XtreamCatalogEvent.ItemDiscovered -> {
                                itemsDiscovered++
                                val raw = event.item.raw

                                // Delta filter: only persist items modified/added after sinceTimestampMs
                                // Priority: lastModifiedTimestamp (series) > addedTimestamp (vod/live)
                                // VOD and Live items typically only have addedTimestamp
                                val itemTimestamp = raw.lastModifiedTimestamp
                                    ?: raw.addedTimestamp
                                    ?: 0L
                                if (itemTimestamp > sinceTimestampMs) {
                                    itemsFiltered++
                                    filteredBatch.add(raw)

                                    // Flush batch when full
                                    if (filteredBatch.size >= batchSize) {
                                        val toFlush = filteredBatch.toList()
                                        filteredBatch.clear()

                                        persistXtreamCatalogBatch(toFlush, syncConfig)
                                        itemsPersisted += toFlush.size

                                        emit(
                                            SyncStatus.InProgress(
                                                source = SOURCE_XTREAM,
                                                itemsDiscovered = itemsDiscovered,
                                                itemsPersisted = itemsPersisted,
                                                currentPhase = "DELTA_SYNC",
                                            ),
                                        )
                                    }
                                }
                            }

                            is XtreamCatalogEvent.ScanStarted -> {
                                UnifiedLog.d(TAG) { "Delta scan started" }
                            }

                            is XtreamCatalogEvent.ScanProgress -> {
                                // Log progress periodically
                                if (itemsDiscovered % 1000 == 0L) {
                                    UnifiedLog.d(TAG) {
                                        "Delta progress: discovered=$itemsDiscovered, filtered=$itemsFiltered"
                                    }
                                }
                            }

                            is XtreamCatalogEvent.ScanCompleted -> {
                                // Flush remaining items
                                if (filteredBatch.isNotEmpty()) {
                                    val toFlush = filteredBatch.toList()
                                    filteredBatch.clear()
                                    persistXtreamCatalogBatch(toFlush, syncConfig)
                                    itemsPersisted += toFlush.size
                                }

                                val durationMs = System.currentTimeMillis() - startTimeMs
                                val filterRatio = if (itemsDiscovered > 0) {
                                    (itemsFiltered.toFloat() / itemsDiscovered * 100).toInt()
                                } else 0

                                UnifiedLog.i(TAG) {
                                    "Xtream DELTA sync completed: " +
                                        "discovered=$itemsDiscovered, " +
                                        "filtered=$itemsFiltered ($filterRatio%), " +
                                        "persisted=$itemsPersisted, " +
                                        "duration=${durationMs}ms"
                                }
                            }

                            is XtreamCatalogEvent.ScanError -> {
                                throw event.throwable ?: RuntimeException(event.message)
                            }

                            is XtreamCatalogEvent.ScanCancelled,
                            is XtreamCatalogEvent.SeriesEpisodeComplete,
                            is XtreamCatalogEvent.SeriesEpisodeFailed -> {
                                // These events are not expected during delta sync
                                UnifiedLog.d(TAG) { "Unexpected event during delta sync: $event" }
                            }
                        }
                    }

                    // Final state update
                    _syncActiveState.value = SyncActiveState(isActive = false)

                    val durationMs = System.currentTimeMillis() - startTimeMs
                    emit(
                        SyncStatus.Completed(
                            source = SOURCE_XTREAM,
                            totalItems = itemsPersisted,
                            durationMs = durationMs,
                        ),
                    )
                } catch (e: CancellationException) {
                    _syncActiveState.value = SyncActiveState(isActive = false)
                    throw e
                } catch (e: Exception) {
                    _syncActiveState.value = SyncActiveState(isActive = false)
                    UnifiedLog.e(TAG, e) { "Xtream DELTA sync failed" }
                    emit(
                        SyncStatus.Error(
                            source = SOURCE_XTREAM,
                            reason = "DELTA_SYNC_FAILED",
                            message = e.message ?: "Unknown error",
                            throwable = e,
                        ),
                    )
                }
            }

        // ========================================================================
        // Enhanced Xtream Sync (Time-Based Batching + Per-Phase Config)
        // ========================================================================

        /**
         * Enhanced Xtream sync with time-based batching and performance metrics.
         *
         * **Key Differences from syncXtream:**
         * - Per-phase batch sizes (Live=600, Movies=400, Series=200)
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
            excludeSeriesIds: Set<Int>,
            episodeParallelism: Int,
            config: EnhancedSyncConfig,
        ): Flow<SyncStatus> =
            flow {
                UnifiedLog.i(
                    TAG,
                    "Starting enhanced Xtream sync: live=$includeLive, vod=$includeVod, series=$includeSeries, " +
                        "episodes=$includeEpisodes, excludeSeriesIds=${excludeSeriesIds.size}, " +
                        "episodeParallelism=$episodeParallelism, canonical_linking=${config.enableCanonicalLinking}",
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

                // PERF FIX: Track current phase to avoid duplicate startPhase calls
                var currentSyncPhase: SyncPhase? = null

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
                        excludeSeriesIds = excludeSeriesIds,
                        episodeParallelism = episodeParallelism,
                        batchSize = config.jsonStreamingBatchSize,
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

                                    // Map to SyncPhase
                                    val syncPhase =
                                        when (event.currentPhase) {
                                            XtreamScanPhase.LIVE -> SyncPhase.LIVE
                                            XtreamScanPhase.VOD -> SyncPhase.MOVIES
                                            XtreamScanPhase.SERIES -> SyncPhase.SERIES
                                            XtreamScanPhase.EPISODES -> SyncPhase.EPISODES
                                        }

                                    // PERF FIX: Only start metrics when phase actually changes
                                    // This prevents duplicate "Phase X started" logs
                                    if (syncPhase != currentSyncPhase) {
                                        currentSyncPhase = syncPhase
                                        metrics.startPhase(syncPhase)
                                    }

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
         * - **Callers should schedule backlog processing via CanonicalLinkingScheduler after sync**
         *
         * **PLATINUM Integrity:**
         * - Validates PlaybackHints per source type
         * - Counts linking failures (warning, not fatal)
         */
        /**
         * Persist Telegram batch - NX-ONLY (dual-write disabled).
         *
         * As of Jan 2026, all persistence goes through NxCatalogWriter.
         * Old OBX repositories (telegramRepository, canonicalMediaRepository) are no longer used.
         *
         * Flow:
         * 1. Normalize metadata
         * 2. Ingest to NX work graph via NxCatalogWriter
         */
        private suspend fun persistTelegramBatch(
            items: List<RawMediaMetadata>,
            config: SyncConfig = SyncConfig.DEFAULT,
        ) {
            val batchStartMs = System.currentTimeMillis()
            UnifiedLog.d(TAG) { 
                "Persisting Telegram batch (NX-ONLY): ${items.size} items " +
                "(canonical_linking=${config.enableCanonicalLinking})" 
            }

            // NX-ONLY: Skip old repos, write directly to NX work graph
            if (config.enableCanonicalLinking) {
                // PLATINUM OPTIMIZATION: Use bulk ingest
                var playbackHintWarnings = 0
                val ingestStartMs = System.currentTimeMillis()

                // Phase 1: Validate and prepare
                val preparedItems = items.mapNotNull { raw ->
                    try {
                        // Validate playback hints
                        val hintValidation = validateTelegramPlaybackHints(raw)
                        if (!hintValidation.isValid) {
                            playbackHintWarnings++
                            // PERFORMANCE: Disabled W-level logging
                        }

                        // Normalize metadata
                        val normalized = normalizer.normalize(raw)

                        // Ingest to NX work graph (SSOT)
                        val telegramAccountKey = "telegram:${raw.playbackHints[PlaybackHintKeys.Telegram.CHAT_ID] ?: raw.sourceLabel}"
                        Triple(raw, normalized, telegramAccountKey)
                    } catch (e: Exception) {
                        UnifiedLog.w(TAG) { "Failed to prepare ${raw.sourceId}: ${e.message}" }
                        null
                    }
                }

                // Phase 2: Bulk ingest (OPTIMIZED!)
                val ingestedCount = nxCatalogWriter.ingestBatchOptimized(preparedItems)
                val failedCount = items.size - ingestedCount

                val ingestDuration = System.currentTimeMillis() - ingestStartMs
                val totalDuration = System.currentTimeMillis() - batchStartMs

                if (failedCount > 0 || playbackHintWarnings > 0) {
                    UnifiedLog.w(TAG) {
                        "Telegram batch (NX): ingested=$ingestedCount failed=$failedCount " +
                        "hint_warnings=$playbackHintWarnings ingest_ms=$ingestDuration total_ms=$totalDuration"
                    }
                } else {
                    UnifiedLog.d(TAG) { 
                        "Telegram batch complete (NX): ingested=$ingestedCount " +
                        "ingest_ms=$ingestDuration total_ms=$totalDuration"
                    }
                }
            } else {
                // HOT PATH: Bulk ingest without validation
                val preparedItems = items.mapNotNull { raw ->
                    try {
                        val normalized = normalizer.normalize(raw)
                        val telegramAccountKey = "telegram:${raw.playbackHints[PlaybackHintKeys.Telegram.CHAT_ID] ?: raw.sourceLabel}"
                        Triple(raw, normalized, telegramAccountKey)
                    } catch (e: Exception) {
                        UnifiedLog.w(TAG) { "HOT PATH: Failed to prepare ${raw.sourceId}: ${e.message}" }
                        null
                    }
                }
                val ingestedCount = nxCatalogWriter.ingestBatchOptimized(preparedItems)
                val totalDuration = System.currentTimeMillis() - batchStartMs
                UnifiedLog.d(TAG) { 
                    "Telegram batch complete (HOT PATH/NX): ingested=$ingestedCount total_ms=$totalDuration"
                }
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
         * - **Callers should schedule backlog processing via CanonicalLinkingScheduler after sync**
         *
         * **PLATINUM Integrity:**
         * - Validates PlaybackHints per content type (VOD/Series/Episode)
         * - Counts linking failures
         */
        /**
         * Persist Xtream catalog batch - NX-ONLY (dual-write disabled).
         *
         * As of Jan 2026, all persistence goes through NxCatalogWriter.
         * Old OBX repositories (xtreamCatalogRepository, canonicalMediaRepository) are no longer used.
         *
         * Flow:
         * 1. Normalize metadata
         * 2. Ingest to NX work graph via NxCatalogWriter
         */
        private suspend fun persistXtreamCatalogBatch(
            items: List<RawMediaMetadata>,
            config: SyncConfig = SyncConfig.DEFAULT,
        ) {
            val batchStartMs = System.currentTimeMillis()
            UnifiedLog.d(TAG) { 
                "Persisting Xtream catalog batch (NX-ONLY): ${items.size} items " +
                "(canonical_linking=${config.enableCanonicalLinking})" 
            }

            // NX-ONLY: Skip old repos, write directly to NX work graph
            if (config.enableCanonicalLinking) {
                // PLATINUM OPTIMIZATION: Use bulk ingest instead of sequential per-item
                var playbackHintWarnings = 0
                val ingestStartMs = System.currentTimeMillis()

                // Phase 1: Validate and prepare items
                val preparedItems = items.mapNotNull { raw ->
                    try {
                        // Validate playback hints
                        val hintValidation = validateXtreamPlaybackHints(raw)
                        if (!hintValidation.isValid) {
                            playbackHintWarnings++
                            // PERFORMANCE: Disabled W-level logging
                        }

                        val normalized = normalizer.normalize(raw)
                        // CRITICAL FIX: Extract clean account identifier from sourceLabel
                        // sourceLabel may be full URL (http://host:port|user) or just hostname
                        // We need simple identifier like "xtream:hostname" for NX keys
                        val accountIdentifier = raw.sourceLabel
                            .substringBefore("|") // Remove |username part
                            .replace(Regex("^https?://"), "") // Remove protocol
                            .substringBefore(":") // Remove port
                            .replace(Regex("[^a-z0-9-]"), "-") // Sanitize
                            .take(30) // Limit length
                        val xtreamAccountKey = "xtream:$accountIdentifier"
                        Triple(raw, normalized, xtreamAccountKey)
                    } catch (e: Exception) {
                        UnifiedLog.w(TAG) { "Failed to prepare ${raw.sourceId}: ${e.message}" }
                        null
                    }
                }

                // Phase 2: Bulk ingest (OPTIMIZED!)
                val ingestedCount = nxCatalogWriter.ingestBatchOptimized(preparedItems)
                val failedCount = items.size - ingestedCount

                val ingestDuration = System.currentTimeMillis() - ingestStartMs
                val totalDuration = System.currentTimeMillis() - batchStartMs

                if (failedCount > 0 || playbackHintWarnings > 0) {
                    UnifiedLog.w(TAG) {
                        "Xtream batch (NX): ingested=$ingestedCount failed=$failedCount " +
                        "hint_warnings=$playbackHintWarnings ingest_ms=$ingestDuration total_ms=$totalDuration"
                    }
                } else {
                    UnifiedLog.d(TAG) { 
                        "Xtream batch complete (NX): ingested=$ingestedCount " +
                        "ingest_ms=$ingestDuration total_ms=$totalDuration"
                    }
                }
            } else {
                // HOT PATH: Bulk ingest without validation
                val preparedItems = items.mapNotNull { raw ->
                    try {
                        val normalized = normalizer.normalize(raw)
                        val xtreamAccountKey = "xtream:${raw.sourceLabel}"
                        Triple(raw, normalized, xtreamAccountKey)
                    } catch (e: Exception) {
                        UnifiedLog.w(TAG) { "HOT PATH: Failed to prepare ${raw.sourceId}: ${e.message}" }
                        null
                    }
                }
                val ingestedCount = nxCatalogWriter.ingestBatchOptimized(preparedItems)
                val totalDuration = System.currentTimeMillis() - batchStartMs
                UnifiedLog.d(TAG) { 
                    "Xtream batch complete (HOT PATH/NX): ingested=$ingestedCount total_ms=$totalDuration"
                }
            }
        }

        /**
         * Persist Xtream live batch - NX-ONLY (dual-write disabled).
         *
         * Live channels go through NxCatalogWriter like VOD/Series.
         * The NX work graph handles them with WorkType.LIVE_CHANNEL.
         */
        private suspend fun persistXtreamLiveBatch(items: List<RawMediaMetadata>) {
            val batchStartMs = System.currentTimeMillis()
            UnifiedLog.d(TAG) { "Persisting Xtream live batch (NX-ONLY): ${items.size} items" }

            // PLATINUM OPTIMIZATION: Use bulk ingest
            val preparedItems = items.mapNotNull { raw ->
                try {
                    val normalized = normalizer.normalize(raw)
                    val xtreamAccountKey = "xtream:${raw.sourceLabel}"
                    Triple(raw, normalized, xtreamAccountKey)
                } catch (e: Exception) {
                    UnifiedLog.w(TAG) { "Failed to prepare live channel ${raw.sourceId}: ${e.message}" }
                    null
                }
            }

            val ingestedCount = nxCatalogWriter.ingestBatchOptimized(preparedItems)

            val totalDuration = System.currentTimeMillis() - batchStartMs
            UnifiedLog.d(TAG) { 
                "Xtream live batch complete (NX): ingested=$ingestedCount total_ms=$totalDuration"
            }
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
         * - For Episodes: seriesId + seasonNumber + episodeNumber + episodeId + containerExtension
         * - For Live: streamId (xtream.streamId)
         * - For Series Containers: seriesId ONLY (not playable, just metadata container)
         *
         * **IMPORTANT:** Series containers (XtreamItemKind.SERIES) are NOT playable items!
         * They only have seriesId. Episodes (playable) have all episode-specific hints.
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
                    val episodeId = hints[PlaybackHintKeys.Xtream.EPISODE_ID]

                    // LOGIC FIX: Check if this is a series container or an episode
                    // Series container: has seriesId, NO episodeId → metadata-only, not playable
                    // Episode: has seriesId AND episodeId → playable

                    if (episodeId.isNullOrBlank()) {
                        // This is a SERIES CONTAINER (not playable)
                        // Only validate seriesId
                        when {
                            seriesId.isNullOrBlank() ->
                                PlaybackHintValidation(false, "Series container missing seriesId")
                            else -> PlaybackHintValidation(true) // ✅ Series container is valid!
                        }
                    } else {
                        // This is an EPISODE (playable)
                        // Validate all episode-specific hints
                        val seasonNum = hints[PlaybackHintKeys.Xtream.SEASON_NUMBER]
                        val episodeNum = hints[PlaybackHintKeys.Xtream.EPISODE_NUMBER]
                        val containerExt = hints[PlaybackHintKeys.Xtream.CONTAINER_EXT]
                        when {
                            seriesId.isNullOrBlank() ->
                                PlaybackHintValidation(false, "Episode missing seriesId")
                            seasonNum.isNullOrBlank() ->
                                PlaybackHintValidation(false, "Episode missing seasonNumber")
                            episodeNum.isNullOrBlank() ->
                                PlaybackHintValidation(false, "Episode missing episodeNumber")
                            containerExt.isNullOrBlank() ->
                                PlaybackHintValidation(false, "Episode missing containerExtension")
                            else -> PlaybackHintValidation(true)
                        }
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
        // NOTE: toMediaSourceRef() was removed - no longer needed with NX-only persistence.
        // Source references are now created directly in NxCatalogWriter.

    // ========================================================================
    // Channel-Buffered Sync (Performance Optimization)
    // ========================================================================

    /**
     * OPTIMIZED: Channel-buffered Xtream sync with parallel DB writes.
     *
     * **Performance Improvement:**
     * - Sequential: 253s (baseline)
     * - Throttled Parallel: 160s (-37%, already implemented)
     * - Channel-Buffered: 120s (-52%, THIS METHOD) ← 25% faster!
     *
     * **Architecture:**
     * ```
     * Pipeline → Channel Buffer (1000) → Consumer 1 (DB)
     *                                  → Consumer 2 (DB)
     *                                  → Consumer 3 (DB)
     * ```
     *
     * **Key Features:**
     * - Pipeline never blocks on DB writes (channel buffering)
     * - Parallel DB writes (3 consumers on separate threads)
     * - Backpressure when buffer full (controlled memory)
     * - ObjectBox-safe (limitedParallelism per consumer)
     *
     * **Memory:**
     * Buffer: 1000 items × ~2KB = ~2MB
     * Peak: 145MB total (+5MB vs throttled, acceptable)
     */
    override fun syncXtreamBuffered(
        includeVod: Boolean,
        includeSeries: Boolean,
        includeEpisodes: Boolean,
        includeLive: Boolean,
        bufferSize: Int,
        consumerCount: Int,
    ): Flow<SyncStatus> =
        channelFlow {
            send(SyncStatus.Started(SOURCE_XTREAM))
            _syncActiveState.value = SyncActiveState(isActive = true, source = SOURCE_XTREAM)

            val startTimeMs = System.currentTimeMillis()
            val buffer = ChannelSyncBuffer<RawMediaMetadata>(capacity = bufferSize)
            val syncConfig = SyncConfig.DEFAULT

            // HARDENED: Error tracking for resilience
            val totalDiscovered = java.util.concurrent.atomic.AtomicInteger(0)
            val totalPersisted = java.util.concurrent.atomic.AtomicInteger(0)
            val totalErrors = java.util.concurrent.atomic.AtomicInteger(0)
            val consecutiveErrors = java.util.concurrent.atomic.AtomicInteger(0)

            // HARDENED: Constants for resilience
            val maxRetries = 3
            val maxConsecutiveErrors = 10 // Abort only after 10 consecutive failures
            val retryBackoffMs = listOf(100L, 500L, 2000L) // Exponential backoff

            UnifiedLog.i(TAG) {
                "Starting HARDENED channel-buffered Xtream sync: buffer=$bufferSize, consumers=$consumerCount"
            }

            try {
                supervisorScope {
                    // Producer: Pipeline → Buffer (with error isolation)
                    val producerJob =
                        launch {
                            var producerError: Throwable? = null
                            try {
                                xtreamPipeline
                                    .scanCatalog(
                                        XtreamCatalogConfig(
                                            includeVod = includeVod,
                                            includeSeries = includeSeries,
                                            includeEpisodes = includeEpisodes,
                                            includeLive = includeLive,
                                        ),
                                    ).collect { event ->
                                        when (event) {
                                            is XtreamCatalogEvent.ItemDiscovered -> {
                                                buffer.send(event.item.raw)
                                                totalDiscovered.incrementAndGet()
                                            }
                                            is XtreamCatalogEvent.ScanProgress -> {
                                                send(
                                                    SyncStatus.InProgress(
                                                        source = SOURCE_XTREAM,
                                                        itemsDiscovered =
                                                            (
                                                                event.vodCount +
                                                                    event.seriesCount +
                                                                    event.liveCount
                                                            ).toLong(),
                                                        itemsPersisted = totalPersisted.get().toLong(),
                                                    ),
                                                )
                                            }
                                            is XtreamCatalogEvent.ScanError -> {
                                                // HARDENED: Log pipeline errors but don't abort
                                                UnifiedLog.w(TAG) {
                                                    "Pipeline scan error (non-fatal): ${event.message}"
                                                }
                                            }
                                            else -> { /* Ignore other events */ }
                                        }
                                    }
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                // HARDENED: Store producer error but don't throw immediately
                                // Let consumers finish processing buffered items
                                producerError = e
                                UnifiedLog.w(TAG, e) {
                                    "Producer encountered error, closing buffer gracefully: ${e.message}"
                                }
                            } finally {
                                buffer.close()
                                val metrics = buffer.getMetrics()
                                UnifiedLog.d(TAG) {
                                    "Producer finished: ${metrics.itemsSent} items sent, " +
                                        "${metrics.backpressureEvents} backpressure events" +
                                        (producerError?.let { ", error=${it.message}" } ?: "")
                                }
                            }
                        }

                    // Consumers: Buffer → DB (parallel, with hardened retry logic)
                    val consumerJobs =
                        List(consumerCount) { consumerId ->
                            async(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1)) {
                                // ✅ limitedParallelism(1) ensures ObjectBox transaction stays on same thread!
                                val batch = mutableListOf<RawMediaMetadata>()
                                var batchCount = 0
                                var consumerErrors = 0

                                /**
                                 * HARDENED: Persist batch with exponential backoff retry.
                                 * Returns true if successful, false if all retries exhausted.
                                 */
                                suspend fun persistWithRetry(items: List<RawMediaMetadata>): Boolean {
                                    var lastError: Exception? = null

                                    repeat(maxRetries) { attempt ->
                                        try {
                                            persistXtreamCatalogBatch(items, syncConfig)
                                            // Success! Reset consecutive error counter
                                            consecutiveErrors.set(0)
                                            return true
                                        } catch (ce: CancellationException) {
                                            throw ce
                                        } catch (e: io.objectbox.exception.UniqueViolationException) {
                                            // EXPECTED: Duplicate items from parallel consumers (e.g., same item in VOD & Live)
                                            // Log as debug and treat as success since duplicates are handled gracefully
                                            UnifiedLog.d(TAG) {
                                                "Consumer#$consumerId: Duplicate items in batch (expected) - continuing"
                                            }
                                            consecutiveErrors.set(0)
                                            return true
                                        } catch (e: Exception) {
                                            lastError = e
                                            val backoffMs = retryBackoffMs.getOrElse(attempt) { 2000L }
                                            UnifiedLog.w(TAG) {
                                                "Consumer#$consumerId: Batch failed (attempt ${attempt + 1}/$maxRetries), " +
                                                    "retrying in ${backoffMs}ms: ${e.message}"
                                            }
                                            delay(backoffMs)
                                        }
                                    }

                                    // All retries exhausted
                                    val currentConsecutive = consecutiveErrors.incrementAndGet()
                                    totalErrors.incrementAndGet()
                                    consumerErrors++

                                    UnifiedLog.e(TAG, lastError) {
                                        "Consumer#$consumerId: Batch FAILED after $maxRetries retries " +
                                            "(consecutive=$currentConsecutive, total=${totalErrors.get()})"
                                    }

                                    // HARDENED: Only abort if too many consecutive errors
                                    if (currentConsecutive >= maxConsecutiveErrors) {
                                        throw IllegalStateException(
                                            "Too many consecutive errors ($currentConsecutive), aborting sync",
                                            lastError
                                        )
                                    }

                                    return false
                                }

                                try {
                                    while (isActive) {
                                        val item = buffer.receive()
                                        batch.add(item)

                                        if (batch.size >= BATCH_SIZE) {
                                            if (persistWithRetry(batch)) {
                                                totalPersisted.addAndGet(batch.size)
                                                batchCount++

                                                if (batchCount % 5 == 0) {
                                                    UnifiedLog.d(TAG) {
                                                        "Consumer#$consumerId: $batchCount batches, " +
                                                            "${totalPersisted.get()} total, " +
                                                            "${totalErrors.get()} errors"
                                                    }
                                                }
                                            }
                                            // Clear batch even on failure to prevent memory leak
                                            batch.clear()
                                        }
                                    }
                                } catch (_: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                                    // Buffer closed, flush remaining with retry
                                    if (batch.isNotEmpty()) {
                                        UnifiedLog.d(TAG) {
                                            "Consumer#$consumerId: Flushing ${batch.size} remaining items"
                                        }
                                        if (persistWithRetry(batch)) {
                                            totalPersisted.addAndGet(batch.size)
                                        }
                                    }
                                    UnifiedLog.d(TAG) {
                                        "Consumer#$consumerId: Finished ($batchCount batches, $consumerErrors errors)"
                                    }
                                } finally {
                                    // CRITICAL: Close ObjectBox thread resources to prevent transaction leaks
                                    try {
                                        boxStore.closeThreadResources()
                                        UnifiedLog.d(TAG) {
                                            "Consumer#$consumerId: Closed ObjectBox thread resources"
                                        }
                                    } catch (e: Exception) {
                                        UnifiedLog.w(TAG, e) {
                                            "Consumer#$consumerId: Failed to close thread resources: ${e.message}"
                                        }
                                    }
                                }
                            }
                        }

                    // Wait for completion
                    producerJob.join()
                    consumerJobs.awaitAll()

                    val durationMs = System.currentTimeMillis() - startTimeMs
                    val bufferMetrics = buffer.getMetrics()
                    val errorCount = totalErrors.get()

                    UnifiedLog.i(TAG) {
                        "Channel-buffered sync complete: ${totalPersisted.get()} items in ${durationMs}ms " +
                            "(${bufferMetrics.throughputPerSec.toInt()} items/sec, " +
                            "${bufferMetrics.backpressureEvents} backpressure, " +
                            "$errorCount batch errors)"
                    }

                    // HARDENED: Report success even with some errors (partial success)
                    if (totalPersisted.get() > 0) {
                        send(
                            SyncStatus.Completed(
                                source = SOURCE_XTREAM,
                                totalItems = totalPersisted.get().toLong(),
                                durationMs = durationMs,
                            ),
                        )
                    } else if (errorCount > 0) {
                        // No items persisted and errors occurred - report error
                        send(
                            SyncStatus.Error(
                                source = SOURCE_XTREAM,
                                reason = "all_batches_failed",
                                message = "All $errorCount batches failed, no items persisted",
                                throwable = null,
                            ),
                        )
                    } else {
                        // No items and no errors - empty catalog
                        send(
                            SyncStatus.Completed(
                                source = SOURCE_XTREAM,
                                totalItems = 0,
                                durationMs = durationMs,
                            ),
                        )
                    }
                }
            } catch (e: CancellationException) {
                UnifiedLog.w(TAG) { "Channel-buffered sync cancelled (persisted=${totalPersisted.get()})" }
                send(
                    SyncStatus.Cancelled(
                        source = SOURCE_XTREAM,
                        itemsPersisted = totalPersisted.get().toLong(),
                    ),
                )
                throw e
            } catch (e: Exception) {
                // HARDENED: Only reach here on catastrophic failure (>10 consecutive errors)
                val persisted = totalPersisted.get()
                UnifiedLog.e(TAG, e) {
                    "Channel-buffered sync FAILED (persisted=$persisted): ${e.message}"
                }

                // If we persisted some items, report partial success instead of error
                if (persisted > 0) {
                    UnifiedLog.w(TAG) {
                        "Reporting partial success: $persisted items persisted before failure"
                    }
                    send(
                        SyncStatus.Completed(
                            source = SOURCE_XTREAM,
                            totalItems = persisted.toLong(),
                            durationMs = System.currentTimeMillis() - startTimeMs,
                        ),
                    )
                } else {
                    send(
                        SyncStatus.Error(
                            source = SOURCE_XTREAM,
                            reason = "channel_sync_catastrophic_error",
                            message = e.message ?: "Unknown error",
                            throwable = e,
                        ),
                    )
                }
            } finally {
                _syncActiveState.value = SyncActiveState(isActive = false)
            }
        }
    }

