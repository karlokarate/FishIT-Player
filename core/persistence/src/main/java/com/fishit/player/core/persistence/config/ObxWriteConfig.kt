package com.fishit.player.core.persistence.config

import android.content.Context
import com.fishit.player.infra.transport.xtream.XtreamTransportConfig
import io.objectbox.Box
import kotlin.math.min

/**
 * SSOT for ObjectBox Write Configuration.
 *
 * **Konsolidiert:**
 * - WorkerConstants.FIRETV_BATCH_SIZE / NORMAL_BATCH_SIZE
 * - SyncPhaseConfig Batch-Größen
 * - ObxKeyBackfillWorker chunkSize/pageSize
 * - M3UExporter batchSize
 *
 * **Device-Class-Aware:**
 * - Nutzt XtreamTransportConfig.detectDeviceClass() als SSOT für Device-Erkennung
 * - FireTV/Low-RAM: Konservative Werte (35-500)
 * - Phone/Tablet: Optimierte Werte (200-4000)
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 W-17 (FireTV Safety)
 *
 * @see XtreamTransportConfig.detectDeviceClass
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
     * @param context Android context for device detection
     * @return Batch size (35 for FireTV, 100 for normal devices)
     */
    fun getBatchSize(context: Context): Int = if (isFireTvLowRam(context)) FIRETV_BATCH_CAP else NORMAL_BATCH_SIZE

    /**
     * Get the appropriate batch size for Live channel sync.
     *
     * @param context Android context for device detection
     * @return Batch size (35 for FireTV, 600 for normal devices)
     */
    fun getSyncLiveBatchSize(context: Context): Int = if (isFireTvLowRam(context)) FIRETV_BATCH_CAP else SYNC_LIVE_BATCH_PHONE

    /**
     * Get the appropriate batch size for Movies/VOD sync.
     *
     * @param context Android context for device detection
     * @return Batch size (35 for FireTV, 400 for normal devices)
     */
    fun getSyncMoviesBatchSize(context: Context): Int = if (isFireTvLowRam(context)) FIRETV_BATCH_CAP else SYNC_MOVIES_BATCH_PHONE

    /**
     * Get the appropriate batch size for Series sync.
     *
     * @param context Android context for device detection
     * @return Batch size (35 for FireTV, 200 for normal devices)
     */
    fun getSyncSeriesBatchSize(context: Context): Int = if (isFireTvLowRam(context)) FIRETV_BATCH_CAP else SYNC_SERIES_BATCH_PHONE

    /**
     * Get the appropriate batch size for Episodes sync.
     *
     * @param context Android context for device detection
     * @return Batch size (35 for FireTV, 200 for normal devices)
     */
    fun getSyncEpisodesBatchSize(context: Context): Int = if (isFireTvLowRam(context)) FIRETV_BATCH_CAP else SYNC_EPISODES_BATCH_PHONE

    /**
     * Get the appropriate chunk size for backfill operations.
     *
     * @param context Android context for device detection
     * @return Chunk size (500 for FireTV, 2000 for normal devices)
     */
    fun getBackfillChunkSize(context: Context): Int =
        if (isFireTvLowRam(context)) FIRETV_BACKFILL_CHUNK_SIZE else NORMAL_BACKFILL_CHUNK_SIZE

    /**
     * Get the appropriate page size for query paging.
     *
     * @param context Android context for device detection
     * @return Page size (500 for FireTV, 4000 for normal devices)
     */
    fun getPageSize(context: Context): Int = if (isFireTvLowRam(context)) FIRETV_BACKFILL_CHUNK_SIZE else NORMAL_PAGE_SIZE

    // =========================================================================
    // Device Detection (delegates to XtreamTransportConfig SSOT)
    // =========================================================================

    /**
     * Check if the current device is FireTV/Low-RAM.
     *
     * Delegates to XtreamTransportConfig.detectDeviceClass() as the SSOT
     * for device classification.
     *
     * @param context Android context for device detection
     * @return true if device is FireTV or low-RAM, false otherwise
     * @see XtreamTransportConfig.detectDeviceClass
     */
    private fun isFireTvLowRam(context: Context): Boolean =
        XtreamTransportConfig.detectDeviceClass(context) ==
            XtreamTransportConfig.DeviceClass.TV_LOW_RAM

    // =========================================================================
    // Box Extension Functions
    // =========================================================================

    /**
     * Put items in chunks with device-aware chunk size.
     *
     * Automatically selects chunk size based on device class:
     * - FireTV/Low-RAM: 500 items per chunk
     * - Phone/Tablet: 2000 items per chunk
     *
     * @param items List of items to persist
     * @param context Android context for device detection
     */
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
