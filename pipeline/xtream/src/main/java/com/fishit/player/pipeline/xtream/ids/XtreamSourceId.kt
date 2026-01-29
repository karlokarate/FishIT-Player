package com.fishit.player.pipeline.xtream.ids

/**
 * Typed Xtream ID Wrappers.
 *
 * These inline classes provide type safety for Xtream IDs without runtime overhead.
 * Use these throughout the codebase instead of raw Long values to prevent
 * accidental mixing of VOD IDs with Series IDs, etc.
 *
 * **Contract:** IDs are always positive Long values from the Xtream API.
 *
 * @see XtreamIdCodec for formatting these to source ID strings
 */

/**
 * Xtream VOD (Movie) stream ID.
 * Used in: get_vod_streams, get_vod_info, playback URLs
 */
@JvmInline
value class XtreamVodId(val id: Long) {
    init {
        require(id > 0) { "XtreamVodId must be positive, got: $id" }
    }
}

/**
 * Xtream Series ID.
 * Used in: get_series, get_series_info
 */
@JvmInline
value class XtreamSeriesId(val id: Long) {
    init {
        require(id > 0) { "XtreamSeriesId must be positive, got: $id" }
    }
}

/**
 * Xtream Episode stream ID.
 * This is the actual stream ID for playback, NOT the composite series/season/episode.
 * Used in: series/{seriesId}/{episodeId}.{ext} playback URLs
 */
@JvmInline
value class XtreamEpisodeId(val id: Long) {
    init {
        require(id > 0) { "XtreamEpisodeId must be positive, got: $id" }
    }
}

/**
 * Xtream Live Channel stream ID.
 * Used in: get_live_streams, live playback URLs
 */
@JvmInline
value class XtreamChannelId(val id: Long) {
    init {
        require(id > 0) { "XtreamChannelId must be positive, got: $id" }
    }
}

/**
 * Xtream Category ID.
 * Used for grouping VOD/Series/Live content.
 */
@JvmInline
value class XtreamCategoryId(val id: String) {
    init {
        require(id.isNotBlank()) { "XtreamCategoryId must not be blank" }
    }
}

/**
 * Parsed Xtream Source ID.
 *
 * Represents a successfully parsed Xtream source ID string.
 * Use [XtreamIdCodec.parse] to create instances from raw strings.
 * Use [XtreamIdCodec] format functions to create source ID strings.
 *
 * **Episode ID Strategy:**
 * - Prefer [Episode] with `episodeId` when available (stable, unique)
 * - Fall back to [EpisodeComposite] with series/season/episode when episodeId unavailable
 *
 * @see XtreamIdCodec
 */
sealed class XtreamParsedSourceId {
    /**
     * VOD (Movie) source.
     * Format: `xtream:vod:{vodId}`
     */
    data class Vod(val vodId: Long) : XtreamParsedSourceId()

    /**
     * Series (Show) source.
     * Format: `xtream:series:{seriesId}`
     */
    data class Series(val seriesId: Long) : XtreamParsedSourceId()

    /**
     * Episode with stable episode ID.
     * Format: `xtream:episode:{episodeId}`
     *
     * This is the PREFERRED format when the Xtream API provides a unique episode stream ID.
     */
    data class Episode(val episodeId: Long) : XtreamParsedSourceId()

    /**
     * Episode with composite identity (fallback).
     * Format: `xtream:episode:series:{seriesId}:s{season}:e{episode}`
     *
     * Used when no unique episode stream ID is available.
     * Less stable than [Episode] but works for basic navigation.
     */
    data class EpisodeComposite(
        val seriesId: Long,
        val season: Int,
        val episode: Int,
    ) : XtreamParsedSourceId()

    /**
     * Live channel source.
     * Format: `xtream:live:{channelId}`
     */
    data class Live(val channelId: Long) : XtreamParsedSourceId()
}
