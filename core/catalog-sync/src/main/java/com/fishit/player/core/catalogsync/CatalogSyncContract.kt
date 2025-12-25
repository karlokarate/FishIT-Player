package com.fishit.player.core.catalogsync

import kotlinx.coroutines.flow.Flow

/**
 * Status events emitted during catalog synchronization.
 *
 * Consumers (UI/WorkManager) can observe these to show progress
 * and handle completion/errors.
 */
sealed interface SyncStatus {
    /**
     * Sync has started.
     *
     * @property source The source being synced (e.g., "telegram", "xtream")
     */
    data class Started(
        val source: String,
    ) : SyncStatus

    /**
     * Sync is in progress.
     *
     * @property source The source being synced
     * @property itemsDiscovered Total items discovered so far
     * @property itemsPersisted Items successfully persisted
     * @property currentPhase Current phase description (e.g., "VOD", "Series")
     */
    data class InProgress(
        val source: String,
        val itemsDiscovered: Long,
        val itemsPersisted: Long,
        val currentPhase: String? = null,
    ) : SyncStatus

    /**
     * Sync completed successfully.
     *
     * @property source The source that was synced
     * @property totalItems Total items synced
     * @property durationMs Duration in milliseconds
     */
    data class Completed(
        val source: String,
        val totalItems: Long,
        val durationMs: Long,
    ) : SyncStatus

    /**
     * Sync was cancelled.
     *
     * @property source The source being synced
     * @property itemsPersisted Items persisted before cancellation
     */
    data class Cancelled(
        val source: String,
        val itemsPersisted: Long,
    ) : SyncStatus

    /**
     * Sync encountered an error.
     *
     * @property source The source being synced
     * @property reason Error category
     * @property message Human-readable error message
     * @property throwable Original exception (if any)
     */
    data class Error(
        val source: String,
        val reason: String,
        val message: String,
        val throwable: Throwable? = null,
    ) : SyncStatus
}

/**
 * Configuration for catalog synchronization.
 *
 * @property batchSize Number of items to batch before persisting
 * @property enableNormalization Whether to normalize metadata before persisting
 * @property emitProgressEvery Emit progress status every N items
 */
data class SyncConfig(
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val enableNormalization: Boolean = true,
    val emitProgressEvery: Int = DEFAULT_PROGRESS_INTERVAL,
) {
    companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val DEFAULT_PROGRESS_INTERVAL = 100

        val DEFAULT = SyncConfig()
    }
}

/**
 * Service for synchronizing catalog data from pipelines to data repositories.
 *
 * This is the orchestration layer between Pipeline and Data layers.
 * It consumes catalog events from pipelines and persists them to repositories.
 *
 * **Architecture Position:**
 * Transport → Pipeline → **CatalogSync** → Data → Domain → UI
 *
 * **Responsibilities:**
 * - Consume TelegramCatalogEvent and XtreamCatalogEvent from pipelines
 * - Extract RawMediaMetadata from catalog items
 * - Optionally normalize metadata via MediaMetadataNormalizer
 * - Call repository.upsertAll() to persist items
 * - Track sync progress and emit status events
 *
 * **NOT Allowed:**
 * - Direct network calls (use Pipeline)
 * - Direct TDLib/Xtream API calls
 * - ObjectBox/DB access directly (use Data repositories)
 */
interface CatalogSyncService {
    /**
     * Synchronize Telegram catalog to local storage.
     *
     * Scans Telegram chats via TelegramCatalogPipeline and persists
     * discovered media items to TelegramContentRepository.
     *
     * @param chatIds Optional list of chat IDs to sync (null = all chats)
     * @param syncConfig Sync configuration
     * @return Flow of sync status events
     */
    fun syncTelegram(
        chatIds: List<Long>? = null,
        syncConfig: SyncConfig = SyncConfig.DEFAULT,
    ): Flow<SyncStatus>

    /**
     * Synchronize Xtream catalog to local storage.
     *
     * Scans Xtream sources via XtreamCatalogPipeline and persists
     * discovered media items to XtreamCatalogRepository and XtreamLiveRepository.
     *
     * @param includeVod Whether to sync VOD items
     * @param includeSeries Whether to sync series
     * @param includeEpisodes Whether to sync episodes
     * @param includeLive Whether to sync live channels
     * @param syncConfig Sync configuration
     * @return Flow of sync status events
     */
    fun syncXtream(
        includeVod: Boolean = true,
        includeSeries: Boolean = true,
        includeEpisodes: Boolean = true,
        includeLive: Boolean = true,
        syncConfig: SyncConfig = SyncConfig.DEFAULT,
    ): Flow<SyncStatus>

    /**
     * Clear all synced data for a source.
     *
     * @param source Source identifier ("telegram" or "xtream")
     */
    suspend fun clearSource(source: String)
}
