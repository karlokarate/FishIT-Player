package com.fishit.player.pipeline.xtream.adapter

import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.util.EpochConverter
import com.fishit.player.core.model.util.RatingNormalizer
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamAuthState
import com.fishit.player.infra.transport.xtream.XtreamConnectionState
import com.fishit.player.infra.transport.xtream.XtreamLiveStream
import com.fishit.player.infra.transport.xtream.XtreamSeriesInfo
import com.fishit.player.infra.transport.xtream.XtreamSeriesStream
import com.fishit.player.infra.transport.xtream.XtreamVodStream
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pipeline-level adapter that wraps XtreamApiClient.
 *
 * Provides pipeline-specific APIs by:
 * - Exposing auth/connection state from transport layer
 * - Converting XtreamVodStream → XtreamVodItem
 * - Converting XtreamSeriesStream → XtreamSeriesItem
 * - Converting XtreamLiveStream → XtreamChannel
 * - Fetching episodes and converting to XtreamEpisode
 *
 * This adapter belongs in the pipeline layer and handles all transport-to-pipeline type
 * conversions.
 *
 * **Architecture:**
 * - Transport Layer: XtreamApiClient (returns XtreamVodStream, XtreamLiveStream, etc.)
 * - Pipeline Layer: This adapter (converts to XtreamVodItem, XtreamChannel, etc.)
 * - Pipeline DTOs are internal and never exported to Data/Playback layers
 */
@Singleton
class XtreamPipelineAdapter
    @Inject
    constructor(
        private val apiClient: XtreamApiClient,
    ) {
        /** Current authorization state from transport layer. */
        val authState: StateFlow<XtreamAuthState> = apiClient.authState

        /** Current connection state from transport layer. */
        val connectionState: StateFlow<XtreamConnectionState> = apiClient.connectionState

        /**
         * Load all VOD items converted to pipeline format.
         *
         * **CRITICAL:** Uses Int.MAX_VALUE to fetch ALL items from API.
         * Default API limit (500) would truncate large catalogs.
         *
         * @param categoryId Optional category filter
         * @return List of XtreamVodItem (all items, not limited)
         */
        suspend fun loadVodItems(categoryId: String? = null): List<XtreamVodItem> {
            val streams = apiClient.getVodStreams(categoryId = categoryId, limit = Int.MAX_VALUE)
            return streams.map { it.toPipelineItem() }
        }

        /**
         * Load all series containers converted to pipeline format.
         *
         * **CRITICAL:** Uses Int.MAX_VALUE to fetch ALL items from API.
         * Default API limit (500) would truncate large catalogs.
         *
         * @param categoryId Optional category filter
         * @return List of XtreamSeriesItem (all items, not limited)
         */
        suspend fun loadSeriesItems(categoryId: String? = null): List<XtreamSeriesItem> {
            val streams = apiClient.getSeries(categoryId = categoryId, limit = Int.MAX_VALUE)
            return streams.map { it.toPipelineItem() }
        }

        /**
         * Load episodes for a specific series.
         *
         * @param seriesId The series ID
         * @param seriesName Optional series name for context (passed to episodes)
         * @return List of XtreamEpisode
         */
        suspend fun loadEpisodes(
            seriesId: Int,
            seriesName: String? = null,
        ): List<XtreamEpisode> {
            val seriesInfo = apiClient.getSeriesInfo(seriesId) ?: return emptyList()
            // Use provided name or try to get it from seriesInfo
            val resolvedSeriesName = seriesName ?: seriesInfo.info?.name
            return seriesInfo.toEpisodes(seriesId, resolvedSeriesName)
        }

        /**
         * Convert already-fetched series info to RawMediaMetadata list.
         *
         * This method accepts a pre-fetched [XtreamSeriesInfo] to avoid duplicate API calls
         * and converts episodes directly to [RawMediaMetadata], preventing pipeline DTO
         * leakage to the data layer.
         *
         * **Use case:** When [XtreamSeriesInfo] has already been fetched (e.g., for detail
         * loading), this avoids a second `getSeriesInfo()` call and provides data layer
         * a clean core model interface without exposing [XtreamEpisode] DTOs.
         *
         * @param seriesInfo Already-fetched series info from API
         * @param seriesId The series ID (for episode identity keys)
         * @param accountLabel Account label for source identification (e.g., "server1")
         * @return List of RawMediaMetadata for episodes, suitable for direct ingestion
         */
        fun convertEpisodesToRaw(
            seriesInfo: XtreamSeriesInfo,
            seriesId: Int,
            accountLabel: String,
        ): List<RawMediaMetadata> {
            val seriesName = seriesInfo.info?.name
            val episodes = seriesInfo.toEpisodes(seriesId, seriesName)
            return episodes.map { it.toRawMediaMetadata(accountLabel = accountLabel) }
        }

        /**
         * Load all live TV channels converted to pipeline format.
         *
         * **CRITICAL:** Uses Int.MAX_VALUE to fetch ALL items from API.
         * Default API limit (500) would truncate large catalogs.
         *
         * @param categoryId Optional category filter
         * @return List of XtreamChannel (all items, not limited)
         */
        suspend fun loadLiveChannels(categoryId: String? = null): List<XtreamChannel> {
            val streams = apiClient.getLiveStreams(categoryId = categoryId, limit = Int.MAX_VALUE)
            return streams.map { it.toPipelineItem() }
        }

        // ============================================================================
        // Batch Streaming Methods (Memory-efficient)
        // ============================================================================

        /**
         * Stream VOD items in batches with constant memory usage.
         *
         * **Memory-efficient:** Only [batchSize] items in memory at a time, regardless of
         * total catalog size (can be 60K+ items).
         *
         * @param batchSize Number of items per batch
         * @param categoryId Optional category filter
         * @param onBatch Callback invoked for each batch of [XtreamVodItem]
         * @return Total number of items processed
         */
        suspend fun streamVodItems(
            batchSize: Int,
            categoryId: String? = null,
            onBatch: suspend (List<XtreamVodItem>) -> Unit,
        ): Int =
            apiClient.streamVodInBatches(
                batchSize = batchSize,
                categoryId = categoryId,
            ) { batch ->
                onBatch(batch.map { it.toPipelineItem() })
            }

        /**
         * Stream series items in batches with constant memory usage.
         *
         * @param batchSize Number of items per batch
         * @param categoryId Optional category filter
         * @param onBatch Callback invoked for each batch of [XtreamSeriesItem]
         * @return Total number of items processed
         */
        suspend fun streamSeriesItems(
            batchSize: Int,
            categoryId: String? = null,
            onBatch: suspend (List<XtreamSeriesItem>) -> Unit,
        ): Int =
            apiClient.streamSeriesInBatches(
                batchSize = batchSize,
                categoryId = categoryId,
            ) { batch ->
                onBatch(batch.map { it.toPipelineItem() })
            }

        /**
         * Stream live channels in batches with constant memory usage.
         *
         * @param batchSize Number of items per batch
         * @param categoryId Optional category filter
         * @param onBatch Callback invoked for each batch of [XtreamChannel]
         * @return Total number of items processed
         */
        suspend fun streamLiveChannels(
            batchSize: Int,
            categoryId: String? = null,
            onBatch: suspend (List<XtreamChannel>) -> Unit,
        ): Int =
            apiClient.streamLiveInBatches(
                batchSize = batchSize,
                categoryId = categoryId,
            ) { batch ->
                onBatch(batch.map { it.toPipelineItem() })
            }

        /** Build VOD playback URL. */
        fun buildVodUrl(
            vodId: Int,
            containerExtension: String?,
        ): String = apiClient.buildVodUrl(vodId, containerExtension)

        /** Build live stream playback URL. */
        fun buildLiveUrl(
            streamId: Int,
            extension: String? = null,
        ): String = apiClient.buildLiveUrl(streamId, extension)

        /** Build series episode playback URL. */
        fun buildSeriesEpisodeUrl(
            seriesId: Int,
            seasonNumber: Int,
            episodeNumber: Int,
            episodeId: Int?,
            containerExtension: String?,
        ): String =
            apiClient.buildSeriesEpisodeUrl(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeId = episodeId,
                containerExtension = containerExtension,
            )

        // ============================================================================
        // Category Methods (for Selective Sync)
        // ============================================================================

        /**
         * Fetch all VOD categories.
         * @return List of VOD categories (empty on error)
         */
        suspend fun getVodCategories(): List<com.fishit.player.infra.transport.xtream.XtreamCategory> =
            apiClient.getVodCategories()

        /**
         * Fetch all series categories.
         * @return List of series categories (empty on error)
         */
        suspend fun getSeriesCategories(): List<com.fishit.player.infra.transport.xtream.XtreamCategory> =
            apiClient.getSeriesCategories()

        /**
         * Fetch all live TV categories.
         * @return List of live categories (empty on error)
         */
        suspend fun getLiveCategories(): List<com.fishit.player.infra.transport.xtream.XtreamCategory> =
            apiClient.getLiveCategories()
    }

// ============================================================================
// Mapping Extensions (Transport → Pipeline)
// ============================================================================

internal fun XtreamVodStream.toPipelineItem(): XtreamVodItem =
    XtreamVodItem(
        id = resolvedId,
        name = name.orEmpty(),
        streamIcon = resolvedPoster,
        categoryId = categoryId,
        containerExtension = containerExtension,
        streamType = streamType,
        added = EpochConverter.secondsToMs(added),
        rating = rating?.toDoubleOrNull(),
        rating5Based = rating5Based,
        // Quick info fields (some panels include these in list)
        year = year,
        genre = genre,
        plot = plot,
        duration = duration,
        isAdult = isAdult == "1",
    )

internal fun XtreamSeriesStream.toPipelineItem(): XtreamSeriesItem =
    XtreamSeriesItem(
        id = resolvedId,
        name = name.orEmpty(),
        cover = resolvedCover,
        backdrop = backdropPath,
        categoryId = categoryId,
        streamType = streamType,
        year = resolvedYear, // Uses year or extracts from releaseDate
        rating = RatingNormalizer.resolve(rating, rating5Based),
        plot = plot,
        cast = cast,
        director = director,
        genre = genre,
        releaseDate = releaseDate,
        youtubeTrailer = youtubeTrailer?.takeIf { it.isNotBlank() },
        episodeRunTime = episodeRunTime,
        lastModified = EpochConverter.secondsToMs(lastModified),
        isAdult = isAdult == "1",
    )

internal fun XtreamLiveStream.toPipelineItem(): XtreamChannel =
    XtreamChannel(
        id = resolvedId,
        name = name.orEmpty(),
        streamIcon = resolvedIcon,
        epgChannelId = epgChannelId,
        tvArchive = tvArchive ?: 0,
        tvArchiveDuration = tvArchiveDuration ?: 0,
        categoryId = categoryId,
        streamType = streamType,
        added = EpochConverter.secondsToMs(added),
        isAdult = isAdult == "1",
        // BUG FIX (Jan 2026): Direct HLS source URL for potential playback optimization
        directSource = directSource?.takeIf { it.isNotBlank() },
    )

/**
 * Convert [XtreamSeriesInfo] episodes to flattened [XtreamEpisode] pipeline DTOs.
 *
 * Flattens all nested episode fields (video/audio codec, bitrate, tmdbId, etc.)
 * from the Xtream `get_series_info` response into the pipeline-level [XtreamEpisode] model.
 *
 * Used by:
 * - [XtreamCatalogPipeline] during catalog sync (episodes phase)
 * - XtreamDetailSync during on-demand detail loading
 *
 * @param seriesId The Xtream series ID (for identity keys)
 * @param seriesName The series title (embedded in each [XtreamEpisode] for context)
 * @return Flattened list of all episodes across all seasons
 */
internal fun XtreamSeriesInfo.toEpisodes(
    seriesId: Int,
    seriesName: String?,
): List<XtreamEpisode> {
    val result = mutableListOf<XtreamEpisode>()

    episodes?.forEach { (seasonNumber, episodeList) ->
        val seasonNum = seasonNumber.toIntOrNull() ?: return@forEach

        episodeList.forEach { ep ->
            result.add(
                XtreamEpisode(
                    id = ep.resolvedEpisodeId ?: 0,
                    seriesId = seriesId,
                    seriesName = seriesName,
                    seasonNumber = seasonNum,
                    episodeNumber = ep.episodeNum ?: 0,
                    title = ep.title.orEmpty(),
                    containerExtension = ep.containerExtension,
                    plot = ep.info?.plot,
                    duration = ep.info?.duration,
                    // BUG FIX (Jan 2026): durationSecs from API is more accurate than parsing duration string
                    durationSecs = ep.info?.durationSecs,
                    releaseDate = ep.info?.releaseDate ?: ep.info?.airDate,
                    rating = ep.info?.rating?.toDoubleOrNull(),
                    thumbnail =
                        ep.info?.movieImage
                            ?: ep.info?.posterPath ?: ep.info?.thumbnail,
                    added = EpochConverter.secondsToMs(ep.added),
                    // BUG FIX (Jan 2026): bitrate from API for quality info in player
                    bitrate = ep.info?.bitrate,
                    // Episode-specific TMDB ID from info block
                    episodeTmdbId = ep.info?.tmdbId,
                    // Video codec info from ffprobe
                    videoCodec = ep.info?.video?.codec,
                    videoWidth = ep.info?.video?.width,
                    videoHeight = ep.info?.video?.height,
                    // Audio codec info from ffprobe
                    audioCodec = ep.info?.audio?.codec,
                    audioChannels = ep.info?.audio?.channels,
                ),
            )
        }
    }

    return result
}
