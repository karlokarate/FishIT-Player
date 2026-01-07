package com.fishit.player.core.catalogsync

/**
 * Configuration for individual sync phases with optimized batch sizes.
 *
 * **Performance Optimization Rationale (PLATIN Guidelines):**
 * - LIVE: Larger batches (400) because items are smaller (id, name, logo, category)
 * - MOVIES: Medium batches (250) - more metadata per item (poster, year, etc.)
 * - SERIES: Smaller batches (150) - complex items with episodes
 *
 * **Device-Specific Overrides:**
 * - FireTV Low-RAM: All phases capped at 35 items (global safety limit)
 * - Normal Devices: Use phase-specific sizes above
 *
 * **Time-Based Flush:**
 * Items appear progressively even if batch isn't full.
 * 1200ms ensures tiles appear within ~1-2 seconds of discovery.
 *
 * @property phase The sync phase this config applies to
 * @property batchSize Maximum items before flush
 * @property flushIntervalMs Time-based flush trigger (0 = disabled)
 */
data class SyncPhaseConfig(
    val phase: SyncPhase,
    val batchSize: Int,
    val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
) {
    companion object {
        const val DEFAULT_FLUSH_INTERVAL_MS = 1200L

        // Optimized batch sizes per content type (PLATIN guidelines - app-work.instructions.md)
        const val LIVE_BATCH_SIZE = 400 // Rapid stream inserts
        const val MOVIES_BATCH_SIZE = 250 // Balanced
        const val SERIES_BATCH_SIZE = 150 // Larger items
        const val EPISODES_BATCH_SIZE = 200 // Lazy loaded, larger batches OK

        val LIVE = SyncPhaseConfig(SyncPhase.LIVE, LIVE_BATCH_SIZE)
        val MOVIES = SyncPhaseConfig(SyncPhase.MOVIES, MOVIES_BATCH_SIZE)
        val SERIES = SyncPhaseConfig(SyncPhase.SERIES, SERIES_BATCH_SIZE)
        val EPISODES = SyncPhaseConfig(SyncPhase.EPISODES, EPISODES_BATCH_SIZE)
    }
}

/**
 * Sync phases in order of perceived speed importance.
 *
 * **Order Rationale (Live → Movies → Series):**
 * 1. LIVE: Most frequently accessed, smallest items
 * 2. MOVIES: Quick browsing, no child items to fetch
 * 3. SERIES: Index only (series containers), episodes loaded lazily
 * 4. EPISODES: NOT synced during initial scan - loaded on-demand
 */
enum class SyncPhase {
    LIVE,
    MOVIES,
    SERIES,
    EPISODES,
}

/**
 * Enhanced sync configuration with per-phase batch sizes and time-based flush.
 *
 * @property liveConfig Configuration for live channel sync
 * @property moviesConfig Configuration for VOD/movies sync
 * @property seriesConfig Configuration for series index sync
 * @property episodesConfig Configuration for episodes (lazy loaded)
 * @property emitProgressEvery Emit progress status every N items
 * @property enableTimeBasedFlush Enable 1200ms auto-flush for progressive UI
 * @property enableCanonicalLinking Enable canonical media linking (can be decoupled for speed)
 */
data class EnhancedSyncConfig(
    val liveConfig: SyncPhaseConfig = SyncPhaseConfig.LIVE,
    val moviesConfig: SyncPhaseConfig = SyncPhaseConfig.MOVIES,
    val seriesConfig: SyncPhaseConfig = SyncPhaseConfig.SERIES,
    val episodesConfig: SyncPhaseConfig = SyncPhaseConfig.EPISODES,
    val emitProgressEvery: Int = 100,
    val enableTimeBasedFlush: Boolean = true,
    val enableCanonicalLinking: Boolean = true,
) {
    companion object {
        /**
         * Default configuration optimized for perceived speed.
         * - Live first (400 batch) per PLATIN guidelines
         * - Movies next (250 batch) per PLATIN guidelines
         * - Series last (150 batch) per PLATIN guidelines
         * - Episodes NOT synced during initial sync
         * - Canonical linking ENABLED
         */
        val DEFAULT = EnhancedSyncConfig()

        /**
         * Configuration for full sync including episodes.
         * Use for background refresh, not initial login.
         */
        val FULL_SYNC =
            EnhancedSyncConfig(
                episodesConfig = SyncPhaseConfig.EPISODES.copy(batchSize = 100),
            )

        /**
         * Configuration for quick sync (e.g., pull-to-refresh).
         * Smaller batches for faster first-item visibility.
         */
        val QUICK_SYNC =
            EnhancedSyncConfig(
                liveConfig = SyncPhaseConfig.LIVE.copy(batchSize = 200),
                moviesConfig = SyncPhaseConfig.MOVIES.copy(batchSize = 100),
                seriesConfig = SyncPhaseConfig.SERIES.copy(batchSize = 50),
            )

        /**
         * PROGRESSIVE_UI configuration: Maximum speed for first UI tiles.
         * - Canonical linking DISABLED for hot path relief
         * - Large batches for throughput
         * - Time-based flush for progressive appearance
         *
         * Use for initial sync where UI speed is critical.
         * Run canonical backlog worker later to link items.
         */
        val PROGRESSIVE_UI =
            EnhancedSyncConfig(
                liveConfig = SyncPhaseConfig.LIVE.copy(batchSize = 600),
                moviesConfig = SyncPhaseConfig.MOVIES.copy(batchSize = 400),
                seriesConfig = SyncPhaseConfig.SERIES.copy(batchSize = 200),
                enableTimeBasedFlush = true,
                enableCanonicalLinking = false, // HOT PATH RELIEF
            )

        /**
         * FIRETV_SAFE configuration: Conservative settings for low-RAM devices.
         * - All batches capped at 35 items (PLATIN global safety limit)
         * - Canonical linking DISABLED for speed
         * - Time-based flush for progressive UI
         *
         * Per app-work.instructions.md: FireTV devices use a global cap of 35 items
         * to prevent OOM on limited RAM devices.
         */
        val FIRETV_SAFE =
            EnhancedSyncConfig(
                liveConfig = SyncPhaseConfig.LIVE.copy(batchSize = 35),
                moviesConfig = SyncPhaseConfig.MOVIES.copy(batchSize = 35),
                seriesConfig = SyncPhaseConfig.SERIES.copy(batchSize = 35),
                enableTimeBasedFlush = true,
                enableCanonicalLinking = false, // Reduce load
            )
    }

    /** Get batch size for a specific phase */
    fun batchSizeFor(phase: SyncPhase): Int =
        when (phase) {
            SyncPhase.LIVE -> liveConfig.batchSize
            SyncPhase.MOVIES -> moviesConfig.batchSize
            SyncPhase.SERIES -> seriesConfig.batchSize
            SyncPhase.EPISODES -> episodesConfig.batchSize
        }

    /**
     * Convert to SyncConfig for use with persist methods.
     * Uses Movies batch size as default.
     */
    fun toSyncConfig(): SyncConfig =
        SyncConfig(
            batchSize = moviesConfig.batchSize,
            enableNormalization = true,
            enableCanonicalLinking = enableCanonicalLinking,
            emitProgressEvery = emitProgressEvery,
        )
}
