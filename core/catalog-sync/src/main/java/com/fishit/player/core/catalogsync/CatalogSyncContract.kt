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

    /**
     * A series episode scan completed.
     *
     * Used for checkpoint tracking during parallel episode loading (PLATINUM).
     * Worker can update checkpoint with completed series IDs to enable
     * cross-run resume.
     *
     * @property source The source being synced (always "xtream")
     * @property seriesId The series ID that completed
     * @property episodeCount Number of episodes loaded for this series
     */
    data class SeriesEpisodeComplete(
        val source: String,
        val seriesId: Int,
        val episodeCount: Int,
    ) : SyncStatus

    /**
     * A Telegram chat scan completed.
     *
     * Used for checkpoint tracking during parallel chat scanning (PLATINUM).
     * Worker can update checkpoint with completed chat IDs to enable
     * cross-run resume.
     *
     * @property source The source being synced (always "telegram")
     * @property chatId The chat ID that completed
     * @property messageCount Number of messages scanned in this chat
     * @property itemCount Number of media items discovered in this chat
     * @property newHighWaterMark The new high-water mark for this chat
     */
    data class TelegramChatComplete(
        val source: String,
        val chatId: Long,
        val messageCount: Long,
        val itemCount: Long,
        val newHighWaterMark: Long?,
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
        const val DEFAULT_BATCH_SIZE = 150 // Optimized for ObjectBox speed (Dec 2025)
        const val DEFAULT_PROGRESS_INTERVAL = 150

        val DEFAULT = SyncConfig()
    }
}

/**
 * Active sync state for UI flow throttling.
 *
 * When sync is active, UI should debounce DB-driven flows to avoid
 * materializing huge lists repeatedly during rapid inserts.
 *
 * @property isActive Whether any sync is currently running
 * @property source The source currently being synced (if any)
 * @property currentPhase Current phase (LIVE/MOVIES/SERIES)
 */
data class SyncActiveState(
    val isActive: Boolean = false,
    val source: String? = null,
    val currentPhase: String? = null,
)

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
 * - Broadcast sync active state for UI flow throttling
 *
 * **NOT Allowed:**
 * - Direct network calls (use Pipeline)
 * - Direct TDLib/Xtream API calls
 * - ObjectBox/DB access directly (use Data repositories)
 */
interface CatalogSyncService {
    /**
     * Observe whether sync is currently active.
     *
     * UI layers should use this to debounce DB-driven flows during sync.
     * - While active: debounce to 400ms
     * - While inactive: emit immediately
     */
    val syncActiveState: kotlinx.coroutines.flow.StateFlow<SyncActiveState>

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
     * @param excludeSeriesIds Series IDs to skip during episode loading (for checkpoint resume)
     * @param episodeParallelism Max concurrent series for parallel episode loading
     * @param syncConfig Sync configuration
     * @return Flow of sync status events
     */
    fun syncXtream(
        includeVod: Boolean = true,
        includeSeries: Boolean = true,
        includeEpisodes: Boolean = true,
        includeLive: Boolean = true,
        excludeSeriesIds: Set<Int> = emptySet(),
        episodeParallelism: Int = 4,
        syncConfig: SyncConfig = SyncConfig.DEFAULT,
    ): Flow<SyncStatus>

    /**
     * Synchronize Xtream catalog with enhanced configuration.
     *
     * **Performance Features:**
     * - Phase ordering: Live → Movies → Series (perceived speed)
     * - Per-phase batch sizes (Live=400, Movies=250, Series=150)
     * - Time-based flush (1200ms) for progressive UI updates
     * - Performance metrics collection (debug builds)
     *
     * **Default Behavior:**
     * - Episodes are NOT synced during initial login
     * - Episodes are loaded on-demand via LoadSeasonEpisodesUseCase
     *
     * @param includeVod Whether to sync VOD items
     * @param includeSeries Whether to sync series index
     * @param includeEpisodes Whether to sync episodes (default FALSE for perceived speed)
     * @param includeLive Whether to sync live channels
     * @param config Enhanced sync configuration with per-phase settings
     * @return Flow of sync status events
     */
    fun syncXtreamEnhanced(
        includeVod: Boolean = true,
        includeSeries: Boolean = true,
        includeEpisodes: Boolean = false, // Lazy load episodes by default
        includeLive: Boolean = true,
        config: EnhancedSyncConfig = EnhancedSyncConfig.DEFAULT,
    ): Flow<SyncStatus>

    /**
     * Get performance metrics from the last sync.
     *
     * Only available in debug builds. Returns null in release.
     *
     * @return Performance metrics or null
     */
    fun getLastSyncMetrics(): SyncPerfMetrics?

    /**
     * Clear all synced data for a source.
     *
     * @param source Source identifier ("telegram" or "xtream")
     */
    suspend fun clearSource(source: String)
}
