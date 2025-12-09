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
}

/**
 * Configuration for Xtream catalog scans.
 *
 * @property includeVod Include VOD items in the scan
 * @property includeSeries Include series containers in the scan
 * @property includeEpisodes Include series episodes in the scan
 * @property includeLive Include live channels in the scan
 * @property imageAuthHeaders Optional headers for authenticated image access
 */
data class XtreamCatalogConfig(
    val includeVod: Boolean = true,
    val includeSeries: Boolean = true,
    val includeEpisodes: Boolean = true,
    val includeLive: Boolean = true,
    val imageAuthHeaders: Map<String, String> = emptyMap(),
) {
    companion object {
        /** Default config including all content types. */
        val DEFAULT = XtreamCatalogConfig()

        /** VOD only scan. */
        val VOD_ONLY = XtreamCatalogConfig(
            includeVod = true,
            includeSeries = false,
            includeEpisodes = false,
            includeLive = false,
        )

        /** Series and episodes only scan. */
        val SERIES_ONLY = XtreamCatalogConfig(
            includeVod = false,
            includeSeries = true,
            includeEpisodes = true,
            includeLive = false,
        )

        /** Live channels only scan. */
        val LIVE_ONLY = XtreamCatalogConfig(
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
