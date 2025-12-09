package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem

/**
 * Abstraction over the data source for Xtream catalog items.
 *
 * This interface decouples the catalog pipeline from concrete API clients
 * or local caches. Implementations may:
 * - Call XtreamApiClient directly (remote-first)
 * - Query local ObjectBox cache
 * - Combine both strategies
 *
 * **Design Principles:**
 * - Minimal surface: only what the catalog pipeline needs
 * - No pagination in interface (implementations handle internally)
 * - No authentication concerns (handled by implementation)
 *
 * **v1 Reference:**
 * - Adapted from v1 XtreamObxRepository patterns
 * - Uses batch loading (not reactive) for catalog sync
 */
interface XtreamCatalogSource {

    /**
     * Load all VOD items.
     *
     * @return List of VOD items (may be empty if none available or on error)
     * @throws XtreamCatalogSourceException on non-recoverable errors
     */
    suspend fun loadVodItems(): List<XtreamVodItem>

    /**
     * Load all series containers.
     *
     * @return List of series items (may be empty if none available or on error)
     * @throws XtreamCatalogSourceException on non-recoverable errors
     */
    suspend fun loadSeriesItems(): List<XtreamSeriesItem>

    /**
     * Load all episodes across all series.
     *
     * Implementations may:
     * - Fetch episodes per series and merge
     * - Use pre-fetched local cache
     * - Call a bulk API if available
     *
     * @return List of all episodes (may be empty)
     * @throws XtreamCatalogSourceException on non-recoverable errors
     */
    suspend fun loadEpisodes(): List<XtreamEpisode>

    /**
     * Load all live TV channels.
     *
     * @return List of live channels (may be empty)
     * @throws XtreamCatalogSourceException on non-recoverable errors
     */
    suspend fun loadLiveChannels(): List<XtreamChannel>
}

/**
 * Exception thrown by XtreamCatalogSource implementations.
 */
class XtreamCatalogSourceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
