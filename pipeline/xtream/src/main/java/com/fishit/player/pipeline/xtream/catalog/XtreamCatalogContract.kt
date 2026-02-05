package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.core.model.RawMediaMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Stateless, side-effect-free Xtream catalog pipeline.
 *
 * Responsibilities:
 * - Fetch VOD / Series / Episodes / Live channels from XtreamCatalogSource
 * - Map items to RawMediaMetadata (no normalization)
 * - Emit catalog events for higher-level CatalogSync modules
 * - Fetch categories for category-based selective sync
 *
 * Non-responsibilities:
 * - No DB or search index writes
 * - No UI logic
 * - No playback URL resolution
 *
 * **Architecture Integration:**
 * - Input: XtreamCatalogSource (API abstraction)
 * - Output: XtreamCatalogEvent stream
 * - Consumer: CatalogSync (persistence layer)
 *
 * See: docs/v2/MEDIA_NORMALIZATION_CONTRACT.md for RawMediaMetadata requirements
 */
interface XtreamCatalogPipeline {
    /**
     * Triggers a catalog scan and emits XtreamCatalogEvents.
     *
     * Implementations must:
     * - Respect [XtreamCatalogConfig] filters
     * - Stop emitting when the returned Flow is cancelled
     * - Never perform DB writes or UI updates directly
     *
     * @param config Scan configuration
     * @return Cold Flow of catalog events
     */
    fun scanCatalog(config: XtreamCatalogConfig): Flow<XtreamCatalogEvent>

    // =========================================================================
    // Category Discovery (for Selective Sync)
    // =========================================================================

    /**
     * Fetch all available categories from the Xtream server.
     *
     * Used for category selection UI before starting a full catalog sync.
     * Categories are lightweight (typically 1-5 KB total).
     *
     * @param config Configuration with API credentials
     * @return All categories grouped by type
     */
    suspend fun fetchCategories(config: XtreamCatalogConfig): XtreamCategoryResult

    /**
     * Fetch only VOD categories.
     * @param config Configuration with API credentials
     * @return List of VOD categories
     */
    suspend fun fetchVodCategories(config: XtreamCatalogConfig): List<XtreamCategoryInfo>

    /**
     * Fetch only Series categories.
     * @param config Configuration with API credentials
     * @return List of Series categories
     */
    suspend fun fetchSeriesCategories(config: XtreamCatalogConfig): List<XtreamCategoryInfo>

    /**
     * Fetch only Live categories.
     * @param config Configuration with API credentials
     * @return List of Live categories
     */
    suspend fun fetchLiveCategories(config: XtreamCatalogConfig): List<XtreamCategoryInfo>
}

// =============================================================================
// Category Models (for Selective Sync)
// =============================================================================

/**
 * Category information from Xtream API.
 *
 * Domain model for category - used in UI and sync logic.
 * Decoupled from transport-layer XtreamCategory.
 *
 * @property categoryId Unique category ID from server
 * @property categoryName Display name
 * @property parentId Optional parent category ID (for hierarchy)
 * @property categoryType VOD, SERIES, or LIVE
 */
data class XtreamCategoryInfo(
    val categoryId: String,
    val categoryName: String,
    val parentId: Int? = null,
    val categoryType: XtreamCategoryType,
)

/**
 * Type of Xtream category.
 */
enum class XtreamCategoryType {
    VOD,
    SERIES,
    LIVE,
}

/**
 * Result of fetching all categories from Xtream server.
 *
 * Used by XtreamCategoryPreloader and category selection UI.
 */
sealed interface XtreamCategoryResult {
    /**
     * Categories fetched successfully.
     *
     * @property vodCategories VOD/Movie categories
     * @property seriesCategories Series categories
     * @property liveCategories Live TV categories
     */
    data class Success(
        val vodCategories: List<XtreamCategoryInfo>,
        val seriesCategories: List<XtreamCategoryInfo>,
        val liveCategories: List<XtreamCategoryInfo>,
    ) : XtreamCategoryResult {
        /** Total category count across all types */
        val totalCount: Int
            get() = vodCategories.size + seriesCategories.size + liveCategories.size

        /** Check if any categories are available */
        val isEmpty: Boolean
            get() = totalCount == 0
    }

    /**
     * Category fetch failed.
     *
     * @property message Error description
     * @property cause Optional underlying exception
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : XtreamCategoryResult
}

/**
 * Configuration for Xtream catalog scans.
 *
 * @property includeVod Include VOD items in the scan
 * @property includeSeries Include series containers in the scan
 * @property includeEpisodes Include series episodes in the scan
 * @property includeLive Include live channels in the scan
 * @property excludeSeriesIds Series IDs to skip during episode loading (for checkpoint resume)
 * @property episodeParallelism Max concurrent series for parallel episode loading (PLATINUM)
 * @property batchSize Batch size for streaming (memory-efficient loading)
 * @property imageAuthHeaders Optional headers for authenticated image access
 * @property accountName Xtream account identifier (e.g., "konigtv") used for sourceLabel in RawMediaMetadata
 * @property vodCategoryIds VOD category IDs to include (empty = all)
 * @property seriesCategoryIds Series category IDs to include (empty = all)
 * @property liveCategoryIds Live category IDs to include (empty = all)
 */
data class XtreamCatalogConfig(
    val includeVod: Boolean = true,
    val includeSeries: Boolean = true,
    val includeEpisodes: Boolean = true,
    val includeLive: Boolean = true,
    val excludeSeriesIds: Set<Int> = emptySet(),
    val episodeParallelism: Int = DEFAULT_EPISODE_PARALLELISM,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val imageAuthHeaders: Map<String, String> = emptyMap(),
    val accountName: String = "xtream",
    // Issue #669: Category filters for selective sync
    val vodCategoryIds: Set<String> = emptySet(),
    val seriesCategoryIds: Set<String> = emptySet(),
    val liveCategoryIds: Set<String> = emptySet(),
) {
    companion object {
        /** Default parallelism for episode loading (4 concurrent series). */
        const val DEFAULT_EPISODE_PARALLELISM = 4

        /**
         * Memory-optimized batch size for streaming (150 items per batch).
         *
         * **Memory Profile:**
         * - 150 items × ~3KB/item = ~450KB per batch
         * - Enables Semaphore(3) without GC thrashing
         * - Safe for 1GB RAM devices (Fire TV Stick)
         *
         * Previous: 500 items (~1.5MB per batch) required Semaphore(2)
         */
        const val DEFAULT_BATCH_SIZE = 150

        /** Default config including all content types. */
        val DEFAULT = XtreamCatalogConfig()

        /** VOD only scan. */
        val VOD_ONLY =
            XtreamCatalogConfig(
                includeVod = true,
                includeSeries = false,
                includeEpisodes = false,
                includeLive = false,
            )

        /** Series and episodes only scan. */
        val SERIES_ONLY =
            XtreamCatalogConfig(
                includeVod = false,
                includeSeries = true,
                includeEpisodes = true,
                includeLive = false,
            )

        /** Live channels only scan. */
        val LIVE_ONLY =
            XtreamCatalogConfig(
                includeVod = false,
                includeSeries = false,
                includeEpisodes = false,
                includeLive = true,
            )
    }
}

/**
 * Events produced by the Xtream catalog pipeline.
 */
sealed interface XtreamCatalogEvent {
    /**
     * A new media item was discovered.
     *
     * The consumer decides whether this is a "create" or "update" event
     * by comparing against its own storage.
     */
    data class ItemDiscovered(
        val item: XtreamCatalogItem,
    ) : XtreamCatalogEvent

    /**
     * Scan is about to start.
     *
     * @property includesVod Whether VOD is included
     * @property includesSeries Whether series are included
     * @property includesEpisodes Whether episodes are included
     * @property includesLive Whether live channels are included
     */
    data class ScanStarted(
        val includesVod: Boolean,
        val includesSeries: Boolean,
        val includesEpisodes: Boolean,
        val includesLive: Boolean,
    ) : XtreamCatalogEvent

    /**
     * Periodic progress update during scanning.
     *
     * @property vodCount VOD items discovered so far
     * @property seriesCount Series containers discovered so far
     * @property episodeCount Episodes discovered so far
     * @property liveCount Live channels discovered so far
     * @property currentPhase Current scan phase (VOD/SERIES/EPISODES/LIVE)
     */
    data class ScanProgress(
        val vodCount: Int,
        val seriesCount: Int,
        val episodeCount: Int,
        val liveCount: Int,
        val currentPhase: XtreamScanPhase,
    ) : XtreamCatalogEvent

    /**
     * Normal completion of a scan.
     *
     * @property vodCount Total VOD items discovered
     * @property seriesCount Total series containers discovered
     * @property episodeCount Total episodes discovered
     * @property liveCount Total live channels discovered
     * @property durationMs Scan duration in milliseconds
     */
    data class ScanCompleted(
        val vodCount: Int,
        val seriesCount: Int,
        val episodeCount: Int,
        val liveCount: Int,
        val durationMs: Long,
    ) : XtreamCatalogEvent

    /**
     * Scan was cancelled.
     *
     * @property vodCount VOD items discovered before cancellation
     * @property seriesCount Series discovered before cancellation
     * @property episodeCount Episodes discovered before cancellation
     * @property liveCount Live channels discovered before cancellation
     */
    data class ScanCancelled(
        val vodCount: Int,
        val seriesCount: Int,
        val episodeCount: Int,
        val liveCount: Int,
    ) : XtreamCatalogEvent

    /**
     * Non-recoverable error during scanning.
     *
     * @property reason Error category
     * @property message Human-readable error message
     * @property throwable Original exception (if any)
     */
    data class ScanError(
        val reason: String,
        val message: String,
        val throwable: Throwable? = null,
    ) : XtreamCatalogEvent

    /**
     * A series episode loading completed (PLATINUM parallel streaming).
     *
     * Emitted when all episodes for a specific series have been loaded.
     * Used for checkpoint tracking to enable cross-run resume.
     *
     * @property seriesId The series ID that completed loading
     * @property episodeCount Number of episodes loaded for this series
     */
    data class SeriesEpisodeComplete(
        val seriesId: Int,
        val episodeCount: Int,
    ) : XtreamCatalogEvent

    /**
     * A series episode loading failed (PLATINUM parallel streaming).
     *
     * Emitted when loading episodes for a series fails.
     * Series won't be added to processedSeriesIds for retry on next run.
     *
     * @property seriesId The series ID that failed
     * @property reason Error description
     */
    data class SeriesEpisodeFailed(
        val seriesId: Int,
        val reason: String,
    ) : XtreamCatalogEvent
}

/**
 * Current phase of the Xtream catalog scan.
 */
enum class XtreamScanPhase {
    VOD,
    SERIES,
    EPISODES,
    LIVE,
}

/**
 * Origin-aware catalog item for Xtream.
 *
 * Wraps RawMediaMetadata with Xtream-specific origin info for tracking and deduplication.
 *
 * @property raw RawMediaMetadata from toRawMediaMetadata() extensions
 * @property kind Origin kind (VOD / SERIES / EPISODE / LIVE)
 * @property vodId Original VOD id, if applicable
 * @property seriesId Original series id, if applicable
 * @property episodeId Original episode id, if applicable
 * @property channelId Original live stream id, if applicable
 * @property discoveredAtMs Timestamp when item was discovered
 */
data class XtreamCatalogItem(
    val raw: RawMediaMetadata,
    val kind: XtreamItemKind,
    val vodId: Int? = null,
    val seriesId: Int? = null,
    val episodeId: Int? = null,
    val channelId: Int? = null,
    val discoveredAtMs: Long = System.currentTimeMillis(),
)

/**
 * Type of Xtream catalog item.
 */
enum class XtreamItemKind {
    VOD,
    SERIES,
    EPISODE,
    LIVE,
}
