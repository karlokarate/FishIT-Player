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

    /**
     * Get the appropriate page size for backfill operations.
     *
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @param contentType Type hint ("live", "vod", "series") for phase-specific sizing
     * @return Page size optimized for device and content type
     */
    fun getBackfillPageSize(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
        contentType: String = "vod",
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) {
            // FireTV: Conservative page sizes
            when (contentType.lowercase()) {
                "live" -> 500
                "vod", "movies" -> 400
                "series" -> 300
                else -> 400
            }
        } else {
            // Phone/Tablet: Optimized page sizes
            when (contentType.lowercase()) {
                "live" -> 5000
                "vod", "movies" -> 4000
                "series" -> 4000
                else -> 4000
            }
        }
    }

    /**
     * Get the appropriate batch size for M3U export/streaming operations.
     *
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @return Batch size for streaming operations
     */
    fun getExportBatchSize(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) {
            // FireTV: Conservative for memory pressure
            500
        } else {
            // Phone/Tablet: Optimized for streaming throughput
            5000
        }
    }

    /**
     * Get the appropriate batch size for TMDB enrichment operations.
     *
     * TMDB enrichment is network-bound and requires API rate limiting.
     * FireTV gets smaller batches to prevent memory issues during network waits.
     *
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @return Batch size for TMDB enrichment
     */
    fun getTmdbEnrichmentBatchSize(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) {
            // FireTV: Small batches (15)
            15
        } else {
            // Phone/Tablet: Larger batches (75)
            75
        }
    }

    /**
     * Get the appropriate batch size for JSON streaming operations.
     *
     * This controls how many items are loaded into memory at once when
     * streaming large JSON arrays from Xtream API endpoints (VOD, Live, Series).
     *
     * **Memory Impact:**
     * - Each VOD item ≈ 500 bytes in memory
     * - 500 items = ~250 KB
     * - 2000 items = ~1 MB
     *
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @return Batch size for JSON streaming (items per batch)
     */
    fun getJsonStreamingBatchSize(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) {
            // FireTV Stick/Lite: Conservative batches
            JSON_STREAMING_BATCH_SIZE_LOW_RESOURCE
        } else {
            // Phone/Tablet/High-end TV: Larger batches for throughput
            JSON_STREAMING_BATCH_SIZE_NORMAL
        }
    }

    /** JSON streaming batch size for low-resource devices (FireTV Stick) */
    const val JSON_STREAMING_BATCH_SIZE_LOW_RESOURCE = 300

    /** JSON streaming batch size for normal devices */
    const val JSON_STREAMING_BATCH_SIZE_NORMAL = 1000

    // =========================================================================
    // Box Extension Functions
    // =========================================================================

    /**
     * Put items in chunks with device-aware chunk size.
     *
     * Automatically selects chunk size based on device class and optional phase hint:
     * - TV_LOW_RAM: 35-500 items per chunk (capped for safety)
     * - Phone/Tablet/TV: 200-2000 items per chunk (optimized for throughput)
     *
     * Phase hints:
     * - "live": Uses SYNC_LIVE_BATCH_SIZE (600 on normal devices, 35 on FireTV)
     * - "movies" or "vod": Uses SYNC_MOVIES_BATCH_SIZE (400 on normal devices, 35 on FireTV)
     * - "series": Uses SYNC_SERIES_BATCH_SIZE (200 on normal devices, 35 on FireTV)
     * - null: Uses default backfill chunk size (2000 on normal devices, 500 on FireTV)
     *
     * @param items List of items to persist
     * @param deviceClassProvider Provider for device classification
     * @param context Android context for device detection
     * @param phaseHint Optional phase hint for sync-specific batch sizing
     * @return Number of items persisted
     */
    fun <T> Box<T>.putChunked(
        items: List<T>,
        deviceClassProvider: DeviceClassProvider,
        context: Context,
        phaseHint: String? = null,
    ): Int {
        val chunkSize =
            when (phaseHint?.lowercase()) {
                "live" -> getSyncLiveBatchSize(deviceClassProvider, context)
                "movies", "vod" -> getSyncMoviesBatchSize(deviceClassProvider, context)
                "series" -> getSyncSeriesBatchSize(deviceClassProvider, context)
                else -> getBackfillChunkSize(deviceClassProvider, context)
            }
        return putChunked(items, chunkSize)
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
     * @return Number of items persisted
     */
    fun <T> Box<T>.putChunked(
        items: List<T>,
        chunkSize: Int = NORMAL_BACKFILL_CHUNK_SIZE,
    ): Int {
        var i = 0
        val n = items.size
        while (i < n) {
            val to = min(i + chunkSize, n)
            put(items.subList(i, to))
            i = to
        }
        return n
    }
}
