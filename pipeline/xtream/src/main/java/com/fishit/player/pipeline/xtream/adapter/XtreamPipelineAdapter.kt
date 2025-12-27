package com.fishit.player.pipeline.xtream.adapter

import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamAuthState
import com.fishit.player.infra.transport.xtream.XtreamConnectionState
import com.fishit.player.infra.transport.xtream.XtreamLiveStream
import com.fishit.player.infra.transport.xtream.XtreamSeriesInfo
import com.fishit.player.infra.transport.xtream.XtreamSeriesStream
import com.fishit.player.infra.transport.xtream.XtreamVodStream
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

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
class XtreamPipelineAdapter @Inject constructor(private val apiClient: XtreamApiClient) {
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
    suspend fun loadEpisodes(seriesId: Int, seriesName: String? = null): List<XtreamEpisode> {
        val seriesInfo = apiClient.getSeriesInfo(seriesId) ?: return emptyList()
        // Use provided name or try to get it from seriesInfo
        val resolvedSeriesName = seriesName ?: seriesInfo.info?.name
        return seriesInfo.toEpisodes(seriesId, resolvedSeriesName)
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

    /** Build VOD playback URL. */
    fun buildVodUrl(vodId: Int, containerExtension: String?): String {
        return apiClient.buildVodUrl(vodId, containerExtension)
    }

    /** Build live stream playback URL. */
    fun buildLiveUrl(streamId: Int, extension: String? = null): String {
        return apiClient.buildLiveUrl(streamId, extension)
    }

    /** Build series episode playback URL. */
    fun buildSeriesEpisodeUrl(
            seriesId: Int,
            seasonNumber: Int,
            episodeNumber: Int,
            episodeId: Int?,
            containerExtension: String?
    ): String {
        return apiClient.buildSeriesEpisodeUrl(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeId = episodeId,
                containerExtension = containerExtension
        )
    }
}

// ============================================================================
// Mapping Extensions (Transport → Pipeline)
// ============================================================================

private fun XtreamVodStream.toPipelineItem(): XtreamVodItem =
        XtreamVodItem(
                id = resolvedId,
                name = name.orEmpty(),
                streamIcon = resolvedPoster,
                categoryId = categoryId,
                containerExtension = containerExtension,
                added = added?.toLongOrNull(),
                rating = rating?.toDoubleOrNull(),
                rating5Based = rating5Based,
                // Quick info fields (some panels include these in list)
                year = year,
                genre = genre,
                plot = plot,
                duration = duration,
        )

private fun XtreamSeriesStream.toPipelineItem(): XtreamSeriesItem =
        XtreamSeriesItem(
                id = resolvedId,
                name = name.orEmpty(),
                cover = resolvedCover,
                backdrop =
                        backdropPath?.let {
                            // backdrop_path can be a single string or array in API response
                            // The DTO handles this via @Serializable
                            it
                        },
                categoryId = categoryId,
                year = resolvedYear, // Uses year or extracts from releaseDate
                rating = rating5Based ?: rating?.toDoubleOrNull(),
                plot = plot,
                cast = cast,
                director = director,
                genre = genre,
                releaseDate = releaseDate,
                youtubeTrailer = youtubeTrailer?.takeIf { it.isNotBlank() },
                episodeRunTime = episodeRunTime,
                lastModified = lastModified?.toLongOrNull(),
        )

private fun XtreamLiveStream.toPipelineItem(): XtreamChannel =
        XtreamChannel(
                id = resolvedId,
                name = name.orEmpty(),
                streamIcon = resolvedIcon,
                epgChannelId = epgChannelId,
                tvArchive = tvArchive ?: 0,
                tvArchiveDuration = tvArchiveDuration ?: 0,
                categoryId = categoryId,
                added = added?.toLongOrNull(),
        )

private fun XtreamSeriesInfo.toEpisodes(seriesId: Int, seriesName: String?): List<XtreamEpisode> {
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
                            releaseDate = ep.info?.releaseDate ?: ep.info?.airDate,
                            rating = ep.info?.rating?.toDoubleOrNull(),
                            thumbnail = ep.info?.movieImage
                                            ?: ep.info?.posterPath ?: ep.info?.thumbnail,
                            added = ep.added?.toLongOrNull(),
                    )
            )
        }
    }

    return result
}
