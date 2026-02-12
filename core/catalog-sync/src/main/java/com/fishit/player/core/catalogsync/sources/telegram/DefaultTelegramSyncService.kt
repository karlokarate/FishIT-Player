// Module: core/catalog-sync/sources/telegram
// Extracted from DefaultCatalogSyncService (Phase 1 – Telegram Chain Parity)

package com.fishit.player.core.catalogsync.sources.telegram

import com.fishit.player.core.catalogsync.SyncCheckpointStore
import com.fishit.player.core.catalogsync.SyncConfig
import com.fishit.player.core.catalogsync.SyncStatus
import com.fishit.player.core.catalogsync.TelegramSyncCheckpoint
import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.metadata.MediaMetadataNormalizer
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.persistence.obx.NxKeyGenerator
import com.fishit.player.infra.data.nx.writer.NxCatalogWriter
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogConfig
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogEvent
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [TelegramSyncService].
 *
 * Extracted from [DefaultCatalogSyncService] to match the Xtream pattern:
 * dedicated service per source, called directly by [CatalogSyncOrchestratorWorker].
 *
 * **Architecture:**
 * ```
 * Worker
 *   └→ TelegramSyncService.sync(config)
 *        ├→ TelegramCatalogPipeline.scanCatalog()    // Pipeline layer
 *        ├→ MediaMetadataNormalizer.normalize()       // Normalizer
 *        └→ NxCatalogWriter.ingestBatchOptimized()    // Data layer
 * ```
 *
 * **Key behaviors:**
 * - Checkpoint-based incremental sync (high-water marks per chat)
 * - Account switch detection (userId mismatch → force full scan)
 * - PLATINUM parallel chat scanning via TelegramCatalogPipeline
 * - Per-chat completion events for Worker-level checkpoint tracking
 * - Batch persistence with playback hint validation
 *
 * @see TelegramSyncService Interface contract
 * @see TelegramSyncConfig Configuration
 * @see DefaultXtreamSyncService Xtream counterpart (same pattern)
 */
@Singleton
class DefaultTelegramSyncService
    @Inject
    constructor(
        private val telegramPipeline: TelegramCatalogPipeline,
        private val normalizer: MediaMetadataNormalizer,
        private val nxCatalogWriter: NxCatalogWriter,
        private val checkpointStore: SyncCheckpointStore,
        private val telegramAuthRepository: TelegramAuthRepository,
    ) : TelegramSyncService {

    companion object {
        private const val TAG = "TelegramSyncService"
        private const val SOURCE = "telegram"
        private const val BATCH_SIZE = 100
        private const val EMIT_PROGRESS_EVERY = 150L
    }

    private var activeJob: Job? = null

    // ========================================================================
    // TelegramSyncService interface
    // ========================================================================

    override fun sync(config: TelegramSyncConfig): Flow<SyncStatus> = flow {
        UnifiedLog.i(TAG) { "Starting Telegram sync: accountKey=${config.accountKey} forceFullSync=${config.forceFullSync}" }
        emit(SyncStatus.Started(SOURCE))

        val startTimeMs = System.currentTimeMillis()
        val batch = mutableListOf<RawMediaMetadata>()
        var itemsDiscovered = 0L
        var itemsPersisted = 0L

        // ── Step 1: Resolve current user for checkpoint validation ──────
        val currentUserId = telegramAuthRepository.getCurrentUserId()
        UnifiedLog.d(TAG) { "Current Telegram userId: $currentUserId" }

        val telegramAccountKey = currentUserId
            ?.let { NxKeyGenerator.telegramAccountKeyFromUserId(it) }
            ?: config.accountKey

        // ── Step 2: Load / validate checkpoint ──────────────────────────
        var checkpoint = if (config.forceFullSync) {
            UnifiedLog.i(TAG) { "Force full sync — ignoring checkpoint" }
            checkpointStore.clearTelegramCheckpoint()
            TelegramSyncCheckpoint.INITIAL
        } else {
            checkpointStore
                .getTelegramCheckpoint()
                ?.let { TelegramSyncCheckpoint.decode(it) }
                ?: TelegramSyncCheckpoint.INITIAL
        }

        // Account switch detection
        if (!checkpoint.isValidFor(currentUserId)) {
            UnifiedLog.w(TAG) {
                "Account switch detected! checkpoint.userId=${checkpoint.telegramUserId}, " +
                    "current=$currentUserId → forcing full scan"
            }
            checkpointStore.clearTelegramCheckpoint()
            checkpoint = TelegramSyncCheckpoint.INITIAL
        }

        val isIncremental = !checkpoint.isInitial
        val effectiveExcludeChatIds = config.excludeChatIds.ifEmpty { checkpoint.processedChatIds }

        UnifiedLog.i(TAG) {
            "Sync mode: ${if (isIncremental) "INCREMENTAL" else "FULL"} " +
                "(tracked_chats=${checkpoint.trackedChatCount}, " +
                "exclude_chats=${effectiveExcludeChatIds.size}, " +
                "parallel=${config.chatParallelism})"
        }

        // ── Step 3: Build pipeline config ───────────────────────────────
        val pipelineConfig = TelegramCatalogConfig(
            chatIds = config.chatIds?.toList(),
            highWaterMarks = if (isIncremental) checkpoint.highWaterMarks else null,
            excludeChatIds = effectiveExcludeChatIds,
            chatParallelism = config.chatParallelism,
            maxMessagesPerChat = config.maxMessagesPerChat?.toLong(),
        )

        // ── Step 4: Execute pipeline + persist ──────────────────────────
        try {
            telegramPipeline.scanCatalog(pipelineConfig).collect { event ->
                when (event) {
                    is TelegramCatalogEvent.ItemDiscovered -> {
                        itemsDiscovered++
                        batch.add(event.item.raw)

                        if (batch.size >= BATCH_SIZE) {
                            persistBatch(batch, telegramAccountKey)
                            itemsPersisted += batch.size
                            batch.clear()
                        }

                        if (itemsDiscovered % EMIT_PROGRESS_EVERY == 0L) {
                            emit(SyncStatus.InProgress(
                                source = SOURCE,
                                itemsDiscovered = itemsDiscovered,
                                itemsPersisted = itemsPersisted,
                            ))
                        }
                    }

                    is TelegramCatalogEvent.ScanProgress -> {
                        emit(SyncStatus.InProgress(
                            source = SOURCE,
                            itemsDiscovered = event.discoveredItems,
                            itemsPersisted = itemsPersisted,
                            currentPhase = "Scanning ${event.scannedChats}/${event.totalChats} chats",
                        ))
                    }

                    is TelegramCatalogEvent.ScanCompleted -> {
                        // Flush remaining batch
                        if (batch.isNotEmpty()) {
                            persistBatch(batch, telegramAccountKey)
                            itemsPersisted += batch.size
                            batch.clear()
                        }

                        // Save checkpoint with new high-water marks
                        if (config.enableCheckpoints) {
                            val updatedCheckpoint = checkpoint
                                .updateHighWaterMarks(event.newHighWaterMarks)
                                .markComplete(currentUserId)
                            checkpointStore.saveTelegramCheckpoint(updatedCheckpoint.encode())
                            UnifiedLog.i(TAG) {
                                "Checkpoint saved: ${updatedCheckpoint.trackedChatCount} chats, userId=$currentUserId"
                            }
                        }

                        val durationMs = System.currentTimeMillis() - startTimeMs
                        UnifiedLog.i(TAG) {
                            "Completed: $itemsPersisted items in ${durationMs}ms " +
                                "(incremental=$isIncremental, hwm_chats=${event.newHighWaterMarks.size})"
                        }
                        emit(SyncStatus.Completed(
                            source = SOURCE,
                            totalItems = itemsPersisted,
                            durationMs = durationMs,
                        ))
                    }

                    is TelegramCatalogEvent.ScanCancelled -> {
                        // Flush remaining batch
                        if (batch.isNotEmpty()) {
                            persistBatch(batch, telegramAccountKey)
                            itemsPersisted += batch.size
                            batch.clear()
                        }

                        // Save partial checkpoint for resume
                        if (config.enableCheckpoints && event.partialHighWaterMarks.isNotEmpty()) {
                            val partialCheckpoint = checkpoint
                                .updateHighWaterMarks(event.partialHighWaterMarks)
                            checkpointStore.saveTelegramCheckpoint(partialCheckpoint.encode())
                            UnifiedLog.i(TAG) { "Partial checkpoint saved: ${event.partialHighWaterMarks.size} chats" }
                        }

                        UnifiedLog.w(TAG) { "Cancelled: $itemsPersisted items persisted" }
                        emit(SyncStatus.Cancelled(source = SOURCE, itemsPersisted = itemsPersisted))
                    }

                    is TelegramCatalogEvent.ChatScanComplete -> {
                        UnifiedLog.v(TAG) { "Chat ${event.chatId} complete: ${event.itemCount} items" }
                        emit(SyncStatus.TelegramChatComplete(
                            source = SOURCE,
                            chatId = event.chatId,
                            messageCount = event.messageCount,
                            itemCount = event.itemCount,
                            newHighWaterMark = event.newHighWaterMark,
                        ))
                    }

                    is TelegramCatalogEvent.ChatScanFailed -> {
                        UnifiedLog.w(TAG) { "Chat ${event.chatId} scan failed: ${event.reason}" }
                    }

                    is TelegramCatalogEvent.ScanError -> {
                        UnifiedLog.e(TAG) { "Sync error: ${event.reason} - ${event.message}" }
                        emit(SyncStatus.Error(
                            source = SOURCE,
                            reason = event.reason,
                            message = event.message,
                            throwable = event.throwable,
                        ))
                    }

                    is TelegramCatalogEvent.ScanStarted -> {
                        UnifiedLog.d(TAG) { "Scan started: ${event.chatCount} chats" }
                    }
                }
            }
        } catch (e: CancellationException) {
            // Persist remaining batch on cancellation
            if (batch.isNotEmpty()) {
                persistBatch(batch, telegramAccountKey)
                itemsPersisted += batch.size
            }
            emit(SyncStatus.Cancelled(SOURCE, itemsPersisted))
            throw e
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Sync failed: ${e.message}" }
            emit(SyncStatus.Error(
                source = SOURCE,
                reason = "exception",
                message = e.message ?: "Unknown error",
                throwable = e,
            ))
        }
    }

    override fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }

    override val isActive: Boolean
        get() = activeJob?.isActive == true

    override suspend fun clearCheckpoint(accountKey: String) {
        checkpointStore.clearTelegramCheckpoint()
        UnifiedLog.i(TAG) { "Checkpoint cleared for $accountKey" }
    }

    override suspend fun getLastSyncTime(accountKey: String): Long? {
        val checkpoint = checkpointStore
            .getTelegramCheckpoint()
            ?.let { TelegramSyncCheckpoint.decode(it) }
        return checkpoint?.lastSyncTimestampMs?.takeIf { it > 0 }
    }

    // ========================================================================
    // Private Helpers — Batch Persistence
    // ========================================================================

    /**
     * Persist a batch of Telegram media items to the NX work graph.
     *
     * Flow: RawMediaMetadata → normalize → NxCatalogWriter.ingestBatchOptimized()
     */
    private suspend fun persistBatch(
        items: List<RawMediaMetadata>,
        telegramAccountKey: String,
    ) {
        val startMs = System.currentTimeMillis()

        var hintWarnings = 0
        val preparedItems = items.mapNotNull { raw ->
            try {
                if (!validatePlaybackHints(raw)) hintWarnings++
                val normalized = normalizer.normalize(raw)
                Triple(raw, normalized, telegramAccountKey)
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to prepare ${raw.sourceId}: ${e.message}" }
                null
            }
        }

        val ingestedCount = nxCatalogWriter.ingestBatchOptimized(preparedItems)
        val durationMs = System.currentTimeMillis() - startMs

        if (hintWarnings > 0) {
            UnifiedLog.w(TAG) {
                "Batch: ingested=$ingestedCount hint_warnings=$hintWarnings duration_ms=$durationMs"
            }
        } else {
            UnifiedLog.d(TAG) {
                "Batch: ingested=$ingestedCount duration_ms=$durationMs"
            }
        }
    }

    /**
     * Validate required Telegram playback hints.
     *
     * Required per PLATINUM contract:
     * - chatId (telegram.chatId)
     * - messageId (telegram.messageId)
     * - EITHER remoteId (telegram.remoteId) OR fileId (telegram.fileId)
     */
    private fun validatePlaybackHints(raw: RawMediaMetadata): Boolean {
        val hints = raw.playbackHints
        val chatId = hints[PlaybackHintKeys.Telegram.CHAT_ID]
        val messageId = hints[PlaybackHintKeys.Telegram.MESSAGE_ID]
        val remoteId = hints[PlaybackHintKeys.Telegram.REMOTE_ID]
        val fileId = hints[PlaybackHintKeys.Telegram.FILE_ID]

        return !chatId.isNullOrBlank() &&
            !messageId.isNullOrBlank() &&
            (!remoteId.isNullOrBlank() || !fileId.isNullOrBlank())
    }
}
