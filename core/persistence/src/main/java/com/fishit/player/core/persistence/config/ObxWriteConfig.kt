package com.fishit.player.core.persistence.config

import android.content.Context
import com.fishit.player.core.device.DeviceClass
import com.fishit.player.core.device.DeviceClassProvider
import io.objectbox.Box
import kotlin.math.min

/**
 * SSOT for ObjectBox Write Configuration.
 *
 * **Konsolidiert:**
 * - WorkerConstants.FIRETV_BATCH_SIZE / NORMAL_BATCH_SIZE
 * - SyncPhaseConfig Batch-Größen
 *
 * **Device-Class-Aware:**
 * - Uses DeviceClassProvider (core:device-api) as SSOT for device detection
 * - FireTV/Low-RAM: Konservative Werte (35-500)
 * - Phone/Tablet: Optimierte Werte (200-4000)
 *
 * **PLATIN Architecture:**
 * - Device detection abstracted via DeviceClassProvider interface
 * - Implementation provided by infra:device-android
 * - Injected via Hilt/Dagger
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 W-17 (FireTV Safety)
 *
 * @see DeviceClassProvider for device detection interface
 * @see DeviceClass for device classification enum
 */
object ObxWriteConfig {
    // =========================================================================
    // Device-Class Batch Sizes (SSOT)
    // =========================================================================

    /**
     * FireTV/Low-RAM: Global Safety Cap (W-17).
     * Per app-work.instructions.md: FireTV devices use a global cap of 35 items
     * to prevent OOM on limited RAM devices.
     */
    const val FIRETV_BATCH_CAP = 35

    /**
     * FireTV/Low-RAM: Larger batches for non-sync operations.
     * Used for backfill and bulk operations where memory pressure is lower.
     */
    const val FIRETV_BACKFILL_CHUNK_SIZE = 500

    /**
     * Phone/Tablet: Standard Batch Size.
     * Default for most operations on normal devices.
     */
    const val NORMAL_BATCH_SIZE = 100

    /**
     * Phone/Tablet: Large Batch für Backfill-Operationen.
     * Optimized for throughput during background processing.
     */
    const val NORMAL_BACKFILL_CHUNK_SIZE = 2000

    /**
     * Phone/Tablet: Page Size für Query-Paging.
     * Used when processing large result sets in chunks.
     */
    const val NORMAL_PAGE_SIZE = 4000

    // =========================================================================
    // Sync-Phase-Spezifische Batch-Größen (Phone/Tablet)
    // =========================================================================

    /**
     * Live Channels: Kleine Items, große Batches.
     * Raised from 400 to 600 in PR #604 for speed optimization.
     */
    const val SYNC_LIVE_BATCH_PHONE = 600

    /**
     * VOD/Movies: Medium Items, mittlere Batches.
     * Raised from 250 to 400 in PR #604 for speed optimization.
     */
    const val SYNC_MOVIES_BATCH_PHONE = 400

    /**
     * Series Index: Komplexere Items, kleinere Batches.
     * Raised from 150 to 200 in PR #604 for speed optimization.
     */
    const val SYNC_SERIES_BATCH_PHONE = 200

    /**
     * Episodes: Lazy-Loaded, mittlere Batches.
     * Used when episodes are loaded on-demand.
     */
    const val SYNC_EPISODES_BATCH_PHONE = 200

    // =========================================================================
    // Device-Aware Accessors
    // =========================================================================

    /**
     * Get the appropriate batch size for the current device.
     *
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @return Batch size (35 for TV_LOW_RAM, 100 for others)
     */
    fun getBatchSize(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) FIRETV_BATCH_CAP else NORMAL_BATCH_SIZE
    }

    /**
     * Get the appropriate batch size for Live channel sync.
     *
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @return Batch size (35 for TV_LOW_RAM, 600 for others)
     */
    fun getSyncLiveBatchSize(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) FIRETV_BATCH_CAP else SYNC_LIVE_BATCH_PHONE
    }

    /**
     * Get the appropriate batch size for Movies/VOD sync.
     *
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @return Batch size (35 for TV_LOW_RAM, 400 for others)
     */
    fun getSyncMoviesBatchSize(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) FIRETV_BATCH_CAP else SYNC_MOVIES_BATCH_PHONE
    }

    /**
     * Get the appropriate batch size for Series sync.
     *
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @return Batch size (35 for TV_LOW_RAM, 200 for others)
     */
    fun getSyncSeriesBatchSize(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) FIRETV_BATCH_CAP else SYNC_SERIES_BATCH_PHONE
    }

    /**
     * Get the appropriate batch size for Episodes sync.
     *
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @return Batch size (35 for TV_LOW_RAM, 200 for others)
     */
    fun getSyncEpisodesBatchSize(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) FIRETV_BATCH_CAP else SYNC_EPISODES_BATCH_PHONE
    }

    /**
     * Get the appropriate chunk size for backfill operations.
     *
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @return Chunk size (500 for TV_LOW_RAM, 2000 for others)
     */
    fun getBackfillChunkSize(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) FIRETV_BACKFILL_CHUNK_SIZE else NORMAL_BACKFILL_CHUNK_SIZE
    }

    /**
     * Get the appropriate page size for query paging.
     *
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @return Page size (500 for TV_LOW_RAM, 4000 for others)
     */
    fun getPageSize(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) FIRETV_BACKFILL_CHUNK_SIZE else NORMAL_PAGE_SIZE
    }

    // =========================================================================
    // Backward Compatibility Overloads (Context-only, no injection)
    // =========================================================================

    /**
     * Get batch size with context-only API (backward compatible).
     *
     * **Note:** This creates a new DeviceClassProvider instance. For performance,
     * prefer the injected DeviceClassProvider version in production code.
     *
     * @param context Android context for device detection
     * @return Batch size based on device class
     * @deprecated Use version with DeviceClassProvider parameter for better performance
     */
    @Deprecated(
        message = "Use getBatchSize(DeviceClassProvider, Context) for better performance",
        replaceWith = ReplaceWith(
            "getBatchSize(deviceClassProvider, context)",
            "com.fishit.player.core.device.DeviceClassProvider",
        ),
    )
    fun getBatchSize(context: Context): Int = getBatchSize(createProvider(), context)

    /**
     * Get Live batch size with context-only API (backward compatible).
     *
     * @param context Android context for device detection
     * @return Batch size based on device class
     * @deprecated Use version with DeviceClassProvider parameter
     */
    @Deprecated(
        message = "Use getSyncLiveBatchSize(DeviceClassProvider, Context) for better performance",
        replaceWith = ReplaceWith("getSyncLiveBatchSize(deviceClassProvider, context)"),
    )
    fun getSyncLiveBatchSize(context: Context): Int = getSyncLiveBatchSize(createProvider(), context)

    /**
     * Get Movies batch size with context-only API (backward compatible).
     *
     * @param context Android context for device detection
     * @return Batch size based on device class
     * @deprecated Use version with DeviceClassProvider parameter
     */
    @Deprecated(
        message = "Use getSyncMoviesBatchSize(DeviceClassProvider, Context) for better performance",
        replaceWith = ReplaceWith("getSyncMoviesBatchSize(deviceClassProvider, context)"),
    )
    fun getSyncMoviesBatchSize(context: Context): Int = getSyncMoviesBatchSize(createProvider(), context)

    /**
     * Get Series batch size with context-only API (backward compatible).
     *
     * @param context Android context for device detection
     * @return Batch size based on device class
     * @deprecated Use version with DeviceClassProvider parameter
     */
    @Deprecated(
        message = "Use getSyncSeriesBatchSize(DeviceClassProvider, Context) for better performance",
        replaceWith = ReplaceWith("getSyncSeriesBatchSize(deviceClassProvider, context)"),
    )
    fun getSyncSeriesBatchSize(context: Context): Int = getSyncSeriesBatchSize(createProvider(), context)

    /**
     * Get backfill chunk size with context-only API (backward compatible).
     *
     * @param context Android context for device detection
     * @return Chunk size based on device class
     * @deprecated Use version with DeviceClassProvider parameter
     */
    @Deprecated(
        message = "Use getBackfillChunkSize(DeviceClassProvider, Context) for better performance",
        replaceWith = ReplaceWith("getBackfillChunkSize(deviceClassProvider, context)"),
    )
    fun getBackfillChunkSize(context: Context): Int = getBackfillChunkSize(createProvider(), context)

    /**
     * Get page size with context-only API (backward compatible).
     *
     * @param context Android context for device detection
     * @return Page size based on device class
     * @deprecated Use version with DeviceClassProvider parameter
     */
    @Deprecated(
        message = "Use getPageSize(DeviceClassProvider, Context) for better performance",
        replaceWith = ReplaceWith("getPageSize(deviceClassProvider, context)"),
    )
    fun getPageSize(context: Context): Int = getPageSize(createProvider(), context)

    /**
     * Create a temporary DeviceClassProvider instance.
     *
     * This is used for backward compatibility. Production code should inject
     * DeviceClassProvider via Hilt for better performance (caching).
     */
    private fun createProvider(): DeviceClassProvider {
        // Import here to avoid forcing all consumers to depend on infra:device-android
        return com.fishit.player.infra.device.AndroidDeviceClassProvider()
    }

    // =========================================================================
    // Box Extension Functions
    // =========================================================================

    /**
     * Put items in chunks with device-aware chunk size.
     *
     * Automatically selects chunk size based on device class:
     * - TV_LOW_RAM: 500 items per chunk
     * - Phone/Tablet/TV: 2000 items per chunk
     *
     * @param items List of items to persist
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     */
    fun <T> Box<T>.putChunked(
        items: List<T>,
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ) {
        val chunkSize = getBackfillChunkSize(deviceClassProvider, context)
        putChunked(items, chunkSize)
    }

    /**
     * Put items in chunks with device-aware chunk size (backward compatible).
     *
     * **Note:** Creates a new DeviceClassProvider instance. For better performance,
     * use the version with DeviceClassProvider parameter.
     *
     * @param items List of items to persist
     * @param context Android context for device detection
     * @deprecated Use version with DeviceClassProvider parameter
     */
    @Deprecated(
        message = "Use putChunked(items, deviceClassProvider, context) for better performance",
        replaceWith = ReplaceWith("putChunked(items, deviceClassProvider, context)"),
    )
    fun <T> Box<T>.putChunked(
        items: List<T>,
        context: Context,
    ) {
        val chunkSize = getBackfillChunkSize(context)
        putChunked(items, chunkSize)
    }

    /**
     * Put items in chunks with explicit chunk size.
     *
     * Chunked writes provide:
     * - Progressive observer updates (UI sees items appear incrementally)
     * - Memory efficiency (smaller transactions)
     * - Better error recovery (partial success possible)
     *
     * @param items List of items to persist
     * @param chunkSize Number of items per chunk (default: 2000)
     */
    fun <T> Box<T>.putChunked(
        items: List<T>,
        chunkSize: Int = NORMAL_BACKFILL_CHUNK_SIZE,
    ) {
        var i = 0
        val n = items.size
        while (i < n) {
            val to = min(i + chunkSize, n)
            put(items.subList(i, to))
            i = to
        }
    }
}
