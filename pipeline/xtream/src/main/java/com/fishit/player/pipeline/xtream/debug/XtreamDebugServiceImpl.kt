package com.fishit.player.pipeline.xtream.debug

import com.fishit.player.infra.transport.xtream.XtreamAuthState
import com.fishit.player.pipeline.xtream.adapter.XtreamPipelineAdapter
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata

/**
 * Default implementation of [XtreamDebugService].
 *
 * Uses [XtreamPipelineAdapter] for data access.
 *
 * @param adapter Pipeline adapter for Xtream transport
 */
class XtreamDebugServiceImpl(
        private val adapter: XtreamPipelineAdapter,
) : XtreamDebugService {

    override suspend fun getStatus(): XtreamStatus {
        val authState = adapter.authState.value
        val isAuthenticated = authState is XtreamAuthState.Authenticated

        // Get counts by loading items
        val vodCount =
                try {
                    adapter.loadVodItems().size
                } catch (e: Exception) {
                    0
                }

        val seriesCount =
                try {
                    adapter.loadSeriesItems().size
                } catch (e: Exception) {
                    0
                }

        val liveCount =
                try {
                    adapter.loadLiveChannels().size
                } catch (e: Exception) {
                    0
                }

        // Get base URL from connection state
        val baseUrl =
                when (val conn = adapter.connectionState.value) {
                    is com.fishit.player.infra.transport.xtream.XtreamConnectionState.Connected ->
                            conn.baseUrl
                    else -> "unknown"
                }

        return XtreamStatus(
                baseUrl = baseUrl,
                isAuthenticated = isAuthenticated,
                vodCountEstimate = vodCount,
                seriesCountEstimate = seriesCount,
                liveCountEstimate = liveCount,
        )
    }

    override suspend fun listVod(limit: Int): List<XtreamVodSummary> {
        val items = adapter.loadVodItems()
        return items.take(limit).map { item ->
            val rawMeta = item.toRawMediaMetadata()
            XtreamVodSummary(
                    streamId = item.id,
                    title = item.name,
                    year = null, // VOD list doesn't include year
                    categoryName = item.categoryId,
                    extension = item.containerExtension,
                    normalizedMediaType = rawMeta.mediaType,
            )
        }
    }

    override suspend fun listSeries(limit: Int): List<XtreamSeriesSummary> {
        val items = adapter.loadSeriesItems()
        return items.take(limit).map { item ->
            XtreamSeriesSummary(
                    seriesId = item.id,
                    title = item.name,
                    year = item.year?.toIntOrNull(),
                    categoryName = item.categoryId,
                    rating = item.rating,
            )
        }
    }

    override suspend fun listLive(limit: Int): List<XtreamLiveSummary> {
        val channels = adapter.loadLiveChannels()
        return channels.take(limit).map { channel ->
            XtreamLiveSummary(
                    channelId = channel.id,
                    name = channel.name,
                    categoryName = channel.categoryId,
                    hasTvArchive = channel.tvArchive > 0,
            )
        }
    }

    override suspend fun inspectVod(streamId: Int): XtreamVodDetails? {
        val items = adapter.loadVodItems()
        val item = items.find { it.id == streamId } ?: return null

        return XtreamVodDetails(
                raw = item,
                rawMedia = item.toRawMediaMetadata(),
        )
    }
}
