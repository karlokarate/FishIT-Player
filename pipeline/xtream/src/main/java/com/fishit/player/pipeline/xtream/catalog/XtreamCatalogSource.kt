package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.coroutines.flow.Flow

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
     * @deprecated Use [loadEpisodesStreaming] for parallel loading with progress.
     * This method blocks until ALL episodes from ALL series are loaded,
     * which can take minutes for large catalogs and may timeout.
     *
     * @return List of all episodes (may be empty)
     * @throws XtreamCatalogSourceException on non-recoverable errors
     */
    @Deprecated(
        message = "Use loadEpisodesStreaming() for parallel loading with checkpoint support",
        replaceWith = ReplaceWith("loadEpisodesStreaming()"),
    )
    suspend fun loadEpisodes(): List<XtreamEpisode>

    /**
     * Stream episodes from all series with parallel loading.
     *
     * **PLATINUM Episode Loading:**
     * - Loads episodes from multiple series in parallel (controlled by [parallelism])
     * - Emits [EpisodeBatchResult] as soon as each series completes
     * - Supports filtering to skip already-processed series (for checkpoint resume)
     * - Memory-efficient: doesn't accumulate all episodes before returning
     *
     * **Behavior:**
     * - First loads series list, then fetches episodes for each in parallel
     * - Each series emits: Batch(episodes) → SeriesComplete OR SeriesFailed
     * - Flow completes when all series are processed
     *
     * @param parallelism Max concurrent series to load (default: 4)
     * @param excludeSeriesIds Series IDs to skip (for checkpoint resume)
     * @return Cold Flow of [EpisodeBatchResult] events
     */
    fun loadEpisodesStreaming(
        parallelism: Int = DEFAULT_EPISODE_PARALLELISM,
        excludeSeriesIds: Set<Int> = emptySet(),
    ): Flow<EpisodeBatchResult>

    /**
     * Load all live TV channels.
     *
     * @return List of live channels (may be empty)
     * @throws XtreamCatalogSourceException on non-recoverable errors
     */
    suspend fun loadLiveChannels(): List<XtreamChannel>

    // ============================================================================
    // Batch Streaming Methods (Memory-efficient)
    // ============================================================================

    /**
     * Stream VOD items in batches with constant memory usage.
     *
     * **Memory-efficient:** Only [batchSize] items in memory at a time.
     * Use this instead of [loadVodItems] for large catalogs (60K+ items).
     *
     * @param batchSize Number of items per batch (default: 500)
     * @param categoryId Optional category filter for server-side filtering
     * @param onBatch Callback invoked for each batch of [XtreamVodItem]
     * @return Total number of items streamed
     * @throws XtreamCatalogSourceException on non-recoverable errors
     */
    suspend fun streamVodItems(
        batchSize: Int = DEFAULT_BATCH_SIZE,
        categoryId: String? = null,
        onBatch: suspend (List<XtreamVodItem>) -> Unit,
    ): Int

    /**
     * Stream series items in batches with constant memory usage.
     *
     * @param batchSize Number of items per batch (default: 500)
     * @param categoryId Optional category filter for server-side filtering
     * @param onBatch Callback invoked for each batch of [XtreamSeriesItem]
     * @return Total number of items streamed
     * @throws XtreamCatalogSourceException on non-recoverable errors
     */
    suspend fun streamSeriesItems(
        batchSize: Int = DEFAULT_BATCH_SIZE,
        categoryId: String? = null,
        onBatch: suspend (List<XtreamSeriesItem>) -> Unit,
    ): Int

    /**
     * Stream live channels in batches with constant memory usage.
     *
     * @param batchSize Number of items per batch (default: 500)
     * @param categoryId Optional category filter for server-side filtering
     * @param onBatch Callback invoked for each batch of [XtreamChannel]
     * @return Total number of items streamed
     * @throws XtreamCatalogSourceException on non-recoverable errors
     */
    suspend fun streamLiveChannels(
        batchSize: Int = DEFAULT_BATCH_SIZE,
        categoryId: String? = null,
        onBatch: suspend (List<XtreamChannel>) -> Unit,
    ): Int

    companion object {
        /** Default parallelism for episode streaming (4 concurrent series). */
        const val DEFAULT_EPISODE_PARALLELISM = 4

        /** Default batch size for streaming methods. */
        const val DEFAULT_BATCH_SIZE = 500
    }
}

/**
 * Result from streaming episode loading.
 *
 * Emitted per-series during [XtreamCatalogSource.loadEpisodesStreaming].
 */
sealed interface EpisodeBatchResult {
    /** The series ID this result relates to. */
    val seriesId: Int

    /**
     * A batch of episodes loaded from a single series.
     *
     * @property seriesId The series these episodes belong to
     * @property seriesName The series name (for metadata context)
     * @property episodes List of episodes (never empty)
     */
    data class Batch(
        override val seriesId: Int,
        val seriesName: String?,
        val episodes: List<XtreamEpisode>,
    ) : EpisodeBatchResult

    /**
     * Series episode loading completed successfully.
     *
     * Emitted after all episodes for a series have been emitted via [Batch].
     * May be emitted even if series had 0 episodes.
     *
     * @property seriesId The completed series
     * @property episodeCount Total episodes loaded for this series
     */
    data class SeriesComplete(
        override val seriesId: Int,
        val episodeCount: Int,
    ) : EpisodeBatchResult

    /**
     * Series episode loading failed.
     *
     * The overall stream continues with other series.
     *
     * @property seriesId The failed series
     * @property error The failure reason
     */
    data class SeriesFailed(
        override val seriesId: Int,
        val error: Throwable,
    ) : EpisodeBatchResult
}

/**
 * Exception thrown by XtreamCatalogSource implementations.
 */
class XtreamCatalogSourceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
