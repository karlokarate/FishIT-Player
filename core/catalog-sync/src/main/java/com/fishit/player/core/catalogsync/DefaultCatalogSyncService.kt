package com.fishit.player.core.catalogsync

import com.fishit.player.core.catalogsync.sources.xtream.XtreamSyncConfig
import com.fishit.player.core.catalogsync.sources.xtream.XtreamSyncService
import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.metadata.MediaMetadataNormalizer
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.repository.NxSourceAccountRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType
import com.fishit.player.core.model.sync.SyncStatus as CoreSyncStatus
import com.fishit.player.core.persistence.obx.NxKeyGenerator
import com.fishit.player.infra.data.nx.writer.NxCatalogWriter
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogConfig
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogEvent
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [CatalogSyncService].
 *
 * Orchestrates catalog synchronization between pipelines and data repositories, with full canonical
 * media unification per MEDIA_NORMALIZATION_CONTRACT.md.
 *
 * **Architecture Compliance (Phase 4 Refactoring - Feb 2026):**
 * - Telegram sync: Implemented directly (consumes TelegramCatalogPipeline)
 * - Xtream sync: **DELEGATED** to [XtreamSyncService] (single unified entry point)
 *
 * **Layer Position:** Transport → Pipeline → **CatalogSync** → Normalizer → Data → Domain → UI
 *
 * **Xtream Delegation:**
 * All Xtream sync methods (`syncXtream`, `syncXtreamEnhanced`, `syncXtreamDelta`,
 * `syncXtreamIncremental`, `syncXtreamBuffered`) delegate to `XtreamSyncService.sync(config)`.
 * This eliminates 1400+ lines of duplicated Xtream sync logic.
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
        private val normalizer: MediaMetadataNormalizer,
        private val nxCatalogWriter: NxCatalogWriter,
        private val checkpointStore: SyncCheckpointStore,
        private val telegramAuthRepository: TelegramAuthRepository,
        // 📍 SSOT: Central source account repository for accountKey management (Feb 2026)
        private val nxSourceAccountRepository: NxSourceAccountRepository,
        // 🎯 UNIFIED XTREAM: Delegate all Xtream sync to unified service (Phase 4)
        private val xtreamSyncService: XtreamSyncService,
    ) : CatalogSyncService {
        companion object {
            private const val TAG = "CatalogSyncService"
            private const val SOURCE_TELEGRAM = "telegram"
            private const val SOURCE_XTREAM = "xtream"
            private const val BATCH_SIZE = 100
        }

        // ========================================================================
        // Sync State Management
        // ========================================================================

        // Sync active state for UI flow throttling
        private val _syncActiveState = MutableStateFlow(SyncActiveState())
        override val syncActiveState: StateFlow<SyncActiveState> = _syncActiveState.asStateFlow()

        // Performance metrics (debug builds only)
        private var lastSyncMetrics: SyncPerfMetrics? = null

        override fun getLastSyncMetrics(): SyncPerfMetrics? = lastSyncMetrics

        // ========================================================================
        // Telegram Sync Implementation
        // ========================================================================

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

                // SSOT: Generate telegramAccountKey from userId using NxKeyGenerator
                val telegramAccountKey = currentUserId?.let { NxKeyGenerator.telegramAccountKeyFromUserId(it) }
                    ?: "telegram:unknown"
                UnifiedLog.d(TAG, "SSOT telegramAccountKey: $telegramAccountKey")

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
                                    persistTelegramBatch(batch, telegramAccountKey, syncConfig)
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
                                    persistTelegramBatch(batch, telegramAccountKey, syncConfig)
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
                                    persistTelegramBatch(batch, telegramAccountKey, syncConfig)
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
                        persistTelegramBatch(batch, telegramAccountKey, syncConfig)
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

        // ========================================================================
        // Xtream Sync Delegation (Phase 4: All methods delegate to XtreamSyncService)
        // ========================================================================

        /**
         * Helper to get the active Xtream account key from the repository.
         *
         * Returns the first available Xtream account key, or a fallback placeholder.
         * Used by legacy sync methods that don't accept accountKey parameter.
         */
        private suspend fun getFirstXtreamAccountKey(): String {
            // Get all accounts and find the first Xtream one
            val accounts = nxSourceAccountRepository.observeAll().first()
            val xtreamAccount = accounts.firstOrNull { it.sourceType == SourceType.XTREAM }
            return xtreamAccount?.accountKey ?: "xtream:unknown"
        }

        /**
         * Maps core/model SyncStatus to catalog-sync SyncStatus.
         *
         * XtreamSyncService uses core/model/sync/SyncStatus, while CatalogSyncService
         * uses its own SyncStatus. This mapper bridges the two.
         *
         * CoreSyncStatus structure (core/model):
         * - Started(source, accountKey, isFullSync, estimatedPhases)
         * - InProgress(source, phase, processedItems, totalItems?, ...)
         * - CheckpointReached(source, checkpointId, phase, processedItems, metadata)
         * - Completed(source, totalDuration, itemCounts, wasIncremental, ...)
         * - Cancelled(source, reason, phase, processedItems, duration, ...)
         * - Error(source, errorType, message, phase, processedItems, exception?, ...)
         */
        private fun CoreSyncStatus.toCatalogSyncStatus(): SyncStatus = when (this) {
            is CoreSyncStatus.Started -> SyncStatus.Started(
                source = SOURCE_XTREAM
            )
            is CoreSyncStatus.InProgress -> SyncStatus.InProgress(
                source = SOURCE_XTREAM,
                itemsDiscovered = processedItems.toLong(),
                itemsPersisted = processedItems.toLong(),
                currentPhase = phase.name
            )
            is CoreSyncStatus.CheckpointReached -> SyncStatus.InProgress(
                source = SOURCE_XTREAM,
                itemsDiscovered = processedItems.toLong(),
                itemsPersisted = processedItems.toLong(),
                currentPhase = "Checkpoint: ${phase.name}"
            )
            is CoreSyncStatus.Completed -> SyncStatus.Completed(
                source = SOURCE_XTREAM,
                totalItems = itemCounts.total.toLong(),
                durationMs = totalDuration.inWholeMilliseconds
            )
            is CoreSyncStatus.Cancelled -> SyncStatus.Cancelled(
                source = SOURCE_XTREAM,
                itemsPersisted = processedItems.toLong()
            )
            is CoreSyncStatus.Error -> SyncStatus.Error(
                source = SOURCE_XTREAM,
                reason = errorType.name,
                message = message,
                throwable = exception
            )
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
        ): Flow<SyncStatus> = flow {
            // Get accountKey from first available Xtream account
            val accountKey = getFirstXtreamAccountKey()

            val config = XtreamSyncConfig(
                accountKey = accountKey,
                syncVod = includeVod,
                syncSeries = includeSeries,
                syncEpisodes = includeEpisodes,
                syncLive = includeLive,
                forceFullSync = true, // Legacy method always does full sync
                enableCheckpoints = false,
                excludeSeriesIds = excludeSeriesIds,
                episodeParallelism = episodeParallelism,
            )
            UnifiedLog.d(TAG, "syncXtream() delegating to XtreamSyncService with config: $config")
            emitAll(xtreamSyncService.sync(config).map { it.toCatalogSyncStatus() })
        }

        override fun syncXtreamEnhanced(
            includeVod: Boolean,
            includeSeries: Boolean,
            includeEpisodes: Boolean,
            includeLive: Boolean,
            excludeSeriesIds: Set<Int>,
            episodeParallelism: Int,
            config: EnhancedSyncConfig,
        ): Flow<SyncStatus> = flow {
            val accountKey = getFirstXtreamAccountKey()

            val syncConfig = XtreamSyncConfig(
                accountKey = accountKey,
                syncVod = includeVod,
                syncSeries = includeSeries,
                syncEpisodes = includeEpisodes,
                syncLive = includeLive,
                forceFullSync = false,
                enableCheckpoints = true,
                excludeSeriesIds = excludeSeriesIds,
                episodeParallelism = episodeParallelism,
            )
            UnifiedLog.d(TAG, "syncXtreamEnhanced() delegating to XtreamSyncService")
            emitAll(xtreamSyncService.sync(syncConfig).map { it.toCatalogSyncStatus() })
        }

        override fun syncXtreamDelta(
            sinceTimestampMs: Long,
            includeVod: Boolean,
            includeSeries: Boolean,
            includeLive: Boolean,
            config: EnhancedSyncConfig,
        ): Flow<SyncStatus> = flow {
            val accountKey = getFirstXtreamAccountKey()

            val syncConfig = XtreamSyncConfig(
                accountKey = accountKey,
                syncVod = includeVod,
                syncSeries = includeSeries,
                syncEpisodes = false, // Delta doesn't include episodes
                syncLive = includeLive,
                forceFullSync = false,
                enableCheckpoints = false,
                // Note: Delta timestamp filtering is handled internally by XtreamSyncService
            )
            UnifiedLog.d(TAG, "syncXtreamDelta() delegating to XtreamSyncService (sinceTs=$sinceTimestampMs)")
            emitAll(xtreamSyncService.sync(syncConfig).map { it.toCatalogSyncStatus() })
        }

        override fun syncXtreamIncremental(
            accountKey: String,
            includeVod: Boolean,
            includeSeries: Boolean,
            includeLive: Boolean,
            forceFullSync: Boolean,
            syncConfig: SyncConfig,
        ): Flow<SyncStatus> {
            val config = XtreamSyncConfig(
                accountKey = accountKey,
                syncVod = includeVod,
                syncSeries = includeSeries,
                syncEpisodes = false,
                syncLive = includeLive,
                forceFullSync = forceFullSync,
                enableCheckpoints = true,
            )
            UnifiedLog.d(TAG, "syncXtreamIncremental() delegating to XtreamSyncService (accountKey=$accountKey)")
            return xtreamSyncService.sync(config).map { it.toCatalogSyncStatus() }
        }

        override fun syncXtreamBuffered(
            includeVod: Boolean,
            includeSeries: Boolean,
            includeEpisodes: Boolean,
            includeLive: Boolean,
            bufferSize: Int,
            consumerCount: Int,
            vodCategoryIds: Set<String>,
            seriesCategoryIds: Set<String>,
            liveCategoryIds: Set<String>,
        ): Flow<SyncStatus> = flow {
            val accountKey = getFirstXtreamAccountKey()

            val config = XtreamSyncConfig(
                accountKey = accountKey,
                syncVod = includeVod,
                syncSeries = includeSeries,
                syncEpisodes = includeEpisodes,
                syncLive = includeLive,
                forceFullSync = false,
                enableCheckpoints = true,
                vodCategoryIds = vodCategoryIds,
                seriesCategoryIds = seriesCategoryIds,
                liveCategoryIds = liveCategoryIds,
                bufferSize = bufferSize,
                consumerCount = consumerCount,
            )
            UnifiedLog.d(TAG, "syncXtreamBuffered() delegating to XtreamSyncService")
            emitAll(xtreamSyncService.sync(config).map { it.toCatalogSyncStatus() })
        }

        // ========================================================================
        // Source Management
        // ========================================================================

        override suspend fun clearSource(source: String) {
            UnifiedLog.i(TAG, "Clearing source: $source")
            when (source) {
                SOURCE_TELEGRAM -> {
                    val count = nxCatalogWriter.clearSourceType(SourceType.TELEGRAM)
                    UnifiedLog.i(TAG, "clearSource(TELEGRAM): Removed $count source refs from NX")
                }
                SOURCE_XTREAM -> {
                    val count = nxCatalogWriter.clearSourceType(SourceType.XTREAM)
                    UnifiedLog.i(TAG, "clearSource(XTREAM): Removed $count source refs from NX")
                }
                else -> UnifiedLog.w(TAG, "Unknown source: $source")
            }
        }

        // ========================================================================
        // Private Helpers - Telegram Persistence
        // ========================================================================

        /**
         * Persist Telegram batch - NX-ONLY (dual-write disabled).
         *
         * As of Jan 2026, all persistence goes through NxCatalogWriter.
         * Old OBX repositories (telegramRepository, canonicalMediaRepository) are no longer used.
         *
         * Flow:
         * 1. Normalize metadata
         * 2. Ingest to NX work graph via NxCatalogWriter
         *
         * @param items Raw metadata items to persist
         * @param telegramAccountKey SSOT account key (from NxKeyGenerator.telegramAccountKeyFromUserId)
         * @param config Sync configuration
         */
        private suspend fun persistTelegramBatch(
            items: List<RawMediaMetadata>,
            telegramAccountKey: String,
            config: SyncConfig = SyncConfig.DEFAULT,
        ) {
            val batchStartMs = System.currentTimeMillis()
            UnifiedLog.d(TAG) {
                "Persisting Telegram batch (NX-ONLY): ${items.size} items " +
                    "(canonical_linking=${config.enableCanonicalLinking})"
            }

            // NX-ONLY: Write directly to NX work graph
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
                        }

                        // Normalize metadata
                        val normalized = normalizer.normalize(raw)

                        // SSOT: Use passed accountKey (from NxKeyGenerator via syncTelegramInternal)
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
                        // SSOT: Use passed accountKey (from NxKeyGenerator via syncTelegramInternal)
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
    }
