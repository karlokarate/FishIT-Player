package com.fishit.player.pipeline.xtream.mapper

import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogItem
import com.fishit.player.pipeline.xtream.catalog.XtreamItemKind
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import javax.inject.Inject

/**
 * Mapping of Xtream pipeline models to XtreamCatalogItem.
 *
 * Uses the existing XtreamRawMetadataExtensions for RawMediaMetadata creation.
 *
 * **Contract Compliance (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - All data is RAW as extracted from Xtream API
 * - NO title cleaning, normalization, or heuristics applied
 * - All processing delegated to :core:metadata-normalizer
 */
interface XtreamCatalogMapper {
    /**
     * Map a VOD item to catalog item.
     *
     * @param item The VOD item from source
     * @param imageAuthHeaders Headers for image authentication
     * @param accountLabel Human-readable account label for UI display
     * @return XtreamCatalogItem wrapping RawMediaMetadata
     */
    fun fromVod(
        item: XtreamVodItem,
        imageAuthHeaders: Map<String, String>,
        accountLabel: String = "xtream",
    ): XtreamCatalogItem

    /**
     * Map a series container to catalog item.
     *
     * @param item The series item from source
     * @param imageAuthHeaders Headers for image authentication
     * @param accountLabel Human-readable account label for UI display
     * @return XtreamCatalogItem wrapping RawMediaMetadata
     */
    fun fromSeries(
        item: XtreamSeriesItem,
        imageAuthHeaders: Map<String, String>,
        accountLabel: String = "xtream",
    ): XtreamCatalogItem

    /**
     * Map an episode to catalog item.
     *
     * @param episode The episode from source
     * @param seriesName Parent series name for context
     * @param imageAuthHeaders Headers for image authentication
     * @param accountLabel Human-readable account label for UI display
     * @return XtreamCatalogItem wrapping RawMediaMetadata
     */
    fun fromEpisode(
        episode: XtreamEpisode,
        seriesName: String? = null,
        imageAuthHeaders: Map<String, String>,
        accountLabel: String = "xtream",
    ): XtreamCatalogItem

    /**
     * Map a live channel to catalog item.
     *
     * @param channel The live channel from source
     * @param imageAuthHeaders Headers for image authentication
     * @param accountLabel Human-readable account label for UI display
     * @return XtreamCatalogItem wrapping RawMediaMetadata
     */
    fun fromChannel(
        channel: XtreamChannel,
        imageAuthHeaders: Map<String, String>,
        accountLabel: String = "xtream",
    ): XtreamCatalogItem
}

/**
 * Default implementation of XtreamCatalogMapper.
 *
 * Uses the existing toRawMetadata() extensions from XtreamRawMetadataExtensions.
 */
class XtreamCatalogMapperImpl
    @Inject
    constructor() : XtreamCatalogMapper {
        override fun fromVod(
            item: XtreamVodItem,
            imageAuthHeaders: Map<String, String>,
            accountLabel: String,
        ): XtreamCatalogItem =
            XtreamCatalogItem(
                raw = item.toRawMetadata(imageAuthHeaders, accountLabel),
                kind = XtreamItemKind.VOD,
                vodId = item.id,
            )

        override fun fromSeries(
            item: XtreamSeriesItem,
            imageAuthHeaders: Map<String, String>,
            accountLabel: String,
        ): XtreamCatalogItem =
            XtreamCatalogItem(
                raw = item.toRawMetadata(imageAuthHeaders, accountLabel),
                kind = XtreamItemKind.SERIES,
                seriesId = item.id,
            )

        override fun fromEpisode(
            episode: XtreamEpisode,
            seriesName: String?,
            imageAuthHeaders: Map<String, String>,
            accountLabel: String,
        ): XtreamCatalogItem =
            XtreamCatalogItem(
                raw = episode.toRawMediaMetadata(
                    seriesNameOverride = seriesName,
                    seriesKind = "series", // Xtream API does not return stream_type for series, default to "series"
                    authHeaders = imageAuthHeaders,
                    accountLabel = accountLabel,
                ),
                kind = XtreamItemKind.EPISODE,
                seriesId = episode.seriesId,
                episodeId = episode.id,
            )

        override fun fromChannel(
            channel: XtreamChannel,
            imageAuthHeaders: Map<String, String>,
            accountLabel: String,
        ): XtreamCatalogItem =
            XtreamCatalogItem(
                raw = channel.toRawMediaMetadata(imageAuthHeaders, accountLabel),
                kind = XtreamItemKind.LIVE,
                channelId = channel.id,
            )
    }
