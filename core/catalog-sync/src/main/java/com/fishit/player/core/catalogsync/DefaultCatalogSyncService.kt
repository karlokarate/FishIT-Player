package com.fishit.player.core.catalogsync

import com.fishit.player.core.metadata.MediaMetadataNormalizer
import com.fishit.player.core.model.RawMediaMetadata
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [CatalogSyncService].
 *
 * Orchestrates catalog synchronization between pipelines and data repositories.
 *
 * **Architecture Compliance:**
 * - Consumes catalog events from pipelines
 * - Extracts RawMediaMetadata and persists via repositories
 * - Emits SyncStatus events for progress tracking
 * - Uses batching for efficient persistence
 *
 * **Layer Position:**
 * Transport → Pipeline → **CatalogSync** → Data → Domain → UI
 */
@Singleton
class DefaultCatalogSyncService @Inject constructor(
    private val telegramPipeline: TelegramCatalogPipeline,
    private val xtreamPipeline: XtreamCatalogPipeline,
    private val telegramRepository: TelegramContentRepository,
    private val xtreamCatalogRepository: XtreamCatalogRepository,
    private val xtreamLiveRepository: XtreamLiveRepository,
    private val normalizer: MediaMetadataNormalizer,
) : CatalogSyncService {

    companion object {
        private const val TAG = "CatalogSyncService"
        private const val SOURCE_TELEGRAM = "telegram"
        private const val SOURCE_XTREAM = "xtream"
    }

    override fun syncTelegram(
        chatIds: List<Long>?,
        syncConfig: SyncConfig,
    ): Flow<SyncStatus> = flow {
        UnifiedLog.i(TAG, "Starting Telegram sync with config: $syncConfig")
        emit(SyncStatus.Started(SOURCE_TELEGRAM))

        val startTimeMs = System.currentTimeMillis()
        val batch = mutableListOf<RawMediaMetadata>()
        var itemsDiscovered = 0L
        var itemsPersisted = 0L

        val pipelineConfig = TelegramCatalogConfig(chatIds = chatIds)

        try {
            telegramPipeline.scanCatalog(pipelineConfig).collect { event ->
                when (event) {
                    is TelegramCatalogEvent.ItemDiscovered -> {
                        itemsDiscovered++
                        batch.add(event.item.raw)

                        if (batch.size >= syncConfig.batchSize) {
                            persistTelegramBatch(batch)
                            itemsPersisted += batch.size
                            batch.clear()
                        }

                        if (itemsDiscovered % syncConfig.emitProgressEvery == 0L) {
                            emit(SyncStatus.InProgress(
                                source = SOURCE_TELEGRAM,
                                itemsDiscovered = itemsDiscovered,
                                itemsPersisted = itemsPersisted,
                            ))
                        }
                    }

                    is TelegramCatalogEvent.ScanProgress -> {
                        emit(SyncStatus.InProgress(
                            source = SOURCE_TELEGRAM,
                            itemsDiscovered = event.discoveredItems,
                            itemsPersisted = itemsPersisted,
                            currentPhase = "Scanning ${event.scannedChats}/${event.totalChats} chats",
                        ))
                    }

                    is TelegramCatalogEvent.ScanCompleted -> {
                        // Persist remaining batch
                        if (batch.isNotEmpty()) {
                            persistTelegramBatch(batch)
                            itemsPersisted += batch.size
                            batch.clear()
                        }

                        val durationMs = System.currentTimeMillis() - startTimeMs
                        UnifiedLog.i(TAG, "Telegram sync completed: $itemsPersisted items in ${durationMs}ms")
                        emit(SyncStatus.Completed(
                            source = SOURCE_TELEGRAM,
                            totalItems = itemsPersisted,
                            durationMs = durationMs,
                        ))
                    }

                    is TelegramCatalogEvent.ScanCancelled -> {
                        // Persist remaining batch before reporting cancellation
                        if (batch.isNotEmpty()) {
                            persistTelegramBatch(batch)
                            itemsPersisted += batch.size
                            batch.clear()
                        }

                        UnifiedLog.w(TAG, "Telegram sync cancelled: $itemsPersisted items persisted")
                        emit(SyncStatus.Cancelled(
                            source = SOURCE_TELEGRAM,
                            itemsPersisted = itemsPersisted,
                        ))
                    }

                    is TelegramCatalogEvent.ScanError -> {
                        UnifiedLog.e(TAG, "Telegram sync error: ${event.reason} - ${event.message}")
                        emit(SyncStatus.Error(
                            source = SOURCE_TELEGRAM,
                            reason = event.reason,
                            message = event.message,
                            throwable = event.throwable,
                        ))
                    }

                    is TelegramCatalogEvent.ScanStarted -> {
                        UnifiedLog.d(TAG, "Telegram scan started: ${event.chatCount} chats")
                    }
                }
            }
        } catch (e: CancellationException) {
            // Persist remaining batch on cancellation
            if (batch.isNotEmpty()) {
                persistTelegramBatch(batch)
                itemsPersisted += batch.size
            }
            emit(SyncStatus.Cancelled(SOURCE_TELEGRAM, itemsPersisted))
            throw e
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Telegram sync failed", e)
            emit(SyncStatus.Error(
                source = SOURCE_TELEGRAM,
                reason = "exception",
                message = e.message ?: "Unknown error",
                throwable = e,
            ))
        }
    }

    override fun syncXtream(
        includeVod: Boolean,
        includeSeries: Boolean,
        includeEpisodes: Boolean,
        includeLive: Boolean,
        syncConfig: SyncConfig,
    ): Flow<SyncStatus> = flow {
        UnifiedLog.i(TAG, "Starting Xtream sync with config: $syncConfig")
        emit(SyncStatus.Started(SOURCE_XTREAM))

        val startTimeMs = System.currentTimeMillis()
        val catalogBatch = mutableListOf<RawMediaMetadata>()
        val liveBatch = mutableListOf<RawMediaMetadata>()
        var itemsDiscovered = 0L
        var itemsPersisted = 0L

        val pipelineConfig = XtreamCatalogConfig(
            includeVod = includeVod,
            includeSeries = includeSeries,
            includeEpisodes = includeEpisodes,
            includeLive = includeLive,
        )

        try {
            xtreamPipeline.scanCatalog(pipelineConfig).collect { event ->
                when (event) {
                    is XtreamCatalogEvent.ItemDiscovered -> {
                        itemsDiscovered++

                        // Route to appropriate batch based on item kind
                        when (event.item.kind) {
                            XtreamItemKind.LIVE -> liveBatch.add(event.item.raw)
                            else -> catalogBatch.add(event.item.raw)
                        }

                        // Persist catalog batch when full
                        if (catalogBatch.size >= syncConfig.batchSize) {
                            persistXtreamCatalogBatch(catalogBatch)
                            itemsPersisted += catalogBatch.size
                            catalogBatch.clear()
                        }

                        // Persist live batch when full
                        if (liveBatch.size >= syncConfig.batchSize) {
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

                    is XtreamCatalogEvent.ScanProgress -> {
                        emit(SyncStatus.InProgress(
                            source = SOURCE_XTREAM,
                            itemsDiscovered = (event.vodCount + event.seriesCount + 
                                             event.episodeCount + event.liveCount).toLong(),
                            itemsPersisted = itemsPersisted,
                            currentPhase = event.currentPhase.name,
                        ))
                    }

                    is XtreamCatalogEvent.ScanCompleted -> {
                        // Persist remaining batches
                        if (catalogBatch.isNotEmpty()) {
                            persistXtreamCatalogBatch(catalogBatch)
                            itemsPersisted += catalogBatch.size
                            catalogBatch.clear()
                        }
                        if (liveBatch.isNotEmpty()) {
                            persistXtreamLiveBatch(liveBatch)
                            itemsPersisted += liveBatch.size
                            liveBatch.clear()
                        }

                        val durationMs = System.currentTimeMillis() - startTimeMs
                        UnifiedLog.i(TAG, "Xtream sync completed: $itemsPersisted items in ${durationMs}ms")
                        emit(SyncStatus.Completed(
                            source = SOURCE_XTREAM,
                            totalItems = itemsPersisted,
                            durationMs = durationMs,
                        ))
                    }

                    is XtreamCatalogEvent.ScanCancelled -> {
                        // Persist remaining batches before reporting cancellation
                        if (catalogBatch.isNotEmpty()) {
                            persistXtreamCatalogBatch(catalogBatch)
                            itemsPersisted += catalogBatch.size
                        }
                        if (liveBatch.isNotEmpty()) {
                            persistXtreamLiveBatch(liveBatch)
                            itemsPersisted += liveBatch.size
                        }

                        UnifiedLog.w(TAG, "Xtream sync cancelled: $itemsPersisted items persisted")
                        emit(SyncStatus.Cancelled(
                            source = SOURCE_XTREAM,
                            itemsPersisted = itemsPersisted,
                        ))
                    }

                    is XtreamCatalogEvent.ScanError -> {
                        UnifiedLog.e(TAG, "Xtream sync error: ${event.reason} - ${event.message}")
                        emit(SyncStatus.Error(
                            source = SOURCE_XTREAM,
                            reason = event.reason,
                            message = event.message,
                            throwable = event.throwable,
                        ))
                    }

                    is XtreamCatalogEvent.ScanStarted -> {
                        UnifiedLog.d(TAG, "Xtream scan started: VOD=$includeVod, Series=$includeSeries, Live=$includeLive")
                    }
                }
            }
        } catch (e: CancellationException) {
            // Persist remaining batches on cancellation
            if (catalogBatch.isNotEmpty()) {
                persistXtreamCatalogBatch(catalogBatch)
                itemsPersisted += catalogBatch.size
            }
            if (liveBatch.isNotEmpty()) {
                persistXtreamLiveBatch(liveBatch)
                itemsPersisted += liveBatch.size
            }
            emit(SyncStatus.Cancelled(SOURCE_XTREAM, itemsPersisted))
            throw e
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Xtream sync failed", e)
            emit(SyncStatus.Error(
                source = SOURCE_XTREAM,
                reason = "exception",
                message = e.message ?: "Unknown error",
                throwable = e,
            ))
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
    // Private Helpers
    // ========================================================================

    private suspend fun persistTelegramBatch(items: List<RawMediaMetadata>) {
        UnifiedLog.d(TAG, "Persisting Telegram batch: ${items.size} items")
        telegramRepository.upsertAll(items)
    }

    private suspend fun persistXtreamCatalogBatch(items: List<RawMediaMetadata>) {
        UnifiedLog.d(TAG, "Persisting Xtream catalog batch: ${items.size} items")
        xtreamCatalogRepository.upsertAll(items)
    }

    private suspend fun persistXtreamLiveBatch(items: List<RawMediaMetadata>) {
        UnifiedLog.d(TAG, "Persisting Xtream live batch: ${items.size} items")
        xtreamLiveRepository.upsertAll(items)
    }
}
