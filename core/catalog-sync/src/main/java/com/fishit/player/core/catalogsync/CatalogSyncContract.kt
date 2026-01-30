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
 * @property batchSize Number of items to batch before persisting to DB
 * @property jsonStreamingBatchSize Number of items per batch when streaming from network (JSON parsing)
 * @property enableNormalization Whether to normalize metadata before persisting
 * @property enableCanonicalLinking Whether to link items to canonical media (can be decoupled for speed)
 * @property emitProgressEvery Emit progress status every N items
 */
data class SyncConfig(
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val jsonStreamingBatchSize: Int = DEFAULT_JSON_STREAMING_BATCH_SIZE,
    val enableNormalization: Boolean = true,
    val enableCanonicalLinking: Boolean = true,
    val emitProgressEvery: Int = DEFAULT_PROGRESS_INTERVAL,
) {
    companion object {
        const val DEFAULT_BATCH_SIZE = 150 // Optimized for ObjectBox speed (Dec 2025)
        const val DEFAULT_JSON_STREAMING_BATCH_SIZE = 500 // For memory-efficient network streaming
        const val DEFAULT_PROGRESS_INTERVAL = 150

        val DEFAULT = SyncConfig()

        /**
         * Fast initial sync config: Skip canonical linking for maximum speed.
         * Use for first-time sync to get UI tiles visible ASAP.
         * Canonical linking can be done later via backlog worker.
         */
        val FAST_INITIAL_SYNC =
            SyncConfig(
                enableNormalization = true,
                enableCanonicalLinking = false, // Decouple for speed
            )
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
     * - Per-phase batch sizes (Live=600, Movies=400, Series=200)
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
     * @param excludeSeriesIds Series IDs to skip during episode loading (for checkpoint resume)
     * @param episodeParallelism Max concurrent series for parallel episode loading
     * @param config Enhanced sync configuration with per-phase settings
     * @return Flow of sync status events
     */
    fun syncXtreamEnhanced(
        includeVod: Boolean = true,
        includeSeries: Boolean = true,
        includeEpisodes: Boolean = false, // Lazy load episodes by default
        includeLive: Boolean = true,
        excludeSeriesIds: Set<Int> = emptySet(),
        episodeParallelism: Int = 4,
        config: EnhancedSyncConfig = EnhancedSyncConfig.DEFAULT,
    ): Flow<SyncStatus>

    /**
     * Synchronize Xtream catalog with delta filtering.
     *
     * **Incremental Sync Optimization:**
     * This method fetches the full catalog from the Xtream API (server doesn't support
     * timestamp filtering), but only persists items that have been modified since
     * [sinceTimestampMs]. This significantly reduces DB write load during incremental syncs.
     *
     * **How it works:**
     * 1. Fetches all items from Xtream API (like regular sync)
     * 2. Filters items where `added` timestamp (mapped to `lastModifiedTimestamp`) > sinceTimestampMs
     * 3. Only persists filtered items to NX work graph
     * 4. Returns count of actually modified items
     *
     * **Use Case:**
     * Called by `XtreamCatalogScanWorker.runIncrementalSync()` when count comparison
     * detects changes but we want to minimize DB writes.
     *
     * @param sinceTimestampMs Only persist items modified after this timestamp (epoch ms)
     * @param includeVod Whether to sync VOD items
     * @param includeSeries Whether to sync series
     * @param includeLive Whether to sync live channels
     * @param config Sync configuration
     * @return Flow of sync status events (itemCount reflects only modified items)
     */
    fun syncXtreamDelta(
        sinceTimestampMs: Long,
        includeVod: Boolean = true,
        includeSeries: Boolean = true,
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
     * Synchronize Xtream catalog with 4-tier incremental sync optimization.
     *
     * **Incremental Sync Tiers:**
     * - Tier 1: ETag/304 - Skip if server returns 304 Not Modified
     * - Tier 2: Count Check - Quick gate if item count differs
     * - Tier 3: Timestamp Filter - Process only items added > lastSync
     * - Tier 4: Fingerprint Comparison - Detect changed items via hash comparison
     *
     * **Workflow:**
     * 1. Calls [IncrementalSyncDecider.decideSyncStrategy] to determine approach
     * 2. Records checkpoint at start/complete/failure
     * 3. Stores item fingerprints for future delta detection
     * 4. Detects deleted items via syncGeneration comparison
     *
     * **Performance Impact:**
     * - Sync time: ~60s → ~5-10s (80-90% reduction)
     * - DB writes: 40,000 → ~500-2,000 (80-95% reduction)
     *
     * @param accountKey Unique identifier for this Xtream account (e.g., "host:user")
     * @param includeVod Whether to sync VOD items
     * @param includeSeries Whether to sync series
     * @param includeLive Whether to sync live channels
     * @param forceFullSync Override incremental and force full sync
     * @param syncConfig Sync configuration
     * @return Flow of sync status events with incremental metrics
     *
     * @see IncrementalSyncDecider
     * @see SyncStrategy
     */
    fun syncXtreamIncremental(
        accountKey: String,
        includeVod: Boolean = true,
        includeSeries: Boolean = true,
        includeLive: Boolean = true,
        forceFullSync: Boolean = false,
        syncConfig: SyncConfig = SyncConfig.DEFAULT,
    ): Flow<SyncStatus>

    /**
     * OPTIMIZED: Channel-buffered Xtream sync with parallel DB writes.
     *
     * **Performance Improvement:**
     * - Sequential: 253s (baseline)
     * - Throttled Parallel: 160s (-37%, already implemented)
     * - Channel-Buffered: 120s (-52%, THIS METHOD)
     *
     * **How it works:**
     * 1. Pipeline produces items → Channel buffer ([bufferSize] capacity)
     * 2. [consumerCount] parallel consumers read from buffer → DB write
     * 3. Backpressure when buffer full (controlled memory)
     *
     * **Memory:**
     * - Buffer: ~2MB (1000 items × 2KB)
     * - Peak: ~145MB (5MB more than throttled parallel, acceptable)
     *
     * **Use Cases:**
     * - Large catalogs (10K+ items)
     * - Background sync workers
     * - Initial onboarding
     *
     * **ObjectBox Safety:**
     * Consumers use `Dispatchers.IO.limitedParallelism(1)` to ensure
     * each consumer stays on same thread (prevents transaction leak).
     *
     * @param includeVod Whether to sync VOD items
     * @param includeSeries Whether to sync series
     * @param includeEpisodes Whether to sync episodes (default false for speed)
     * @param includeLive Whether to sync live channels
     * @param bufferSize Channel buffer capacity (default: 1000 phone, 500 FireTV)
     * @param consumerCount Number of parallel DB writers (default: 3 phone, 2 FireTV)
     * @return Flow of SyncStatus with buffer metrics
     */
    fun syncXtreamBuffered(
        includeVod: Boolean = true,
        includeSeries: Boolean = true,
        includeEpisodes: Boolean = false,
        includeLive: Boolean = true,
        bufferSize: Int = ChannelSyncBuffer.DEFAULT_CAPACITY,
        consumerCount: Int = 3,
    ): Flow<SyncStatus>

    /**
     * Clear all synced data for a source.
     *
     * @param source Source identifier ("telegram" or "xtream")
     */
    suspend fun clearSource(source: String)
}
