package com.fishit.player.pipeline.xtream.model

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType

/**
 * Extensions for converting Xtream pipeline models to RawMediaMetadata.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Provides RAW metadata only (no cleaning, no normalization, no heuristics)
 * - Title passed through exactly as received from Xtream API
 * - TMDB fields stored as raw strings only
 * - All normalization delegated to :core:metadata-normalizer
 *
 * These extensions enable seamless integration with the centralized
 * metadata normalization pipeline.
 */

/**
 * Converts an XtreamVodItem to RawMediaMetadata.
 *
 * @return RawMediaMetadata with VOD-specific fields
 */
fun XtreamVodItem.toRawMediaMetadata(): RawMediaMetadata = RawMediaMetadata(
    originalTitle = name,
    mediaType = MediaType.MOVIE,
    year = null, // Xtream VOD list doesn't include year; detail fetch required
    season = null,
    episode = null,
    durationMinutes = null, // Xtream VOD list doesn't include duration
    externalIds = ExternalIds(), // Xtream list doesn't include TMDB; detail fetch required
    sourceType = SourceType.XTREAM,
    sourceLabel = "Xtream VOD",
    sourceId = "xtream:vod:$id",
)

/**
 * Converts an XtreamSeriesItem to RawMediaMetadata.
 *
 * Note: This represents the series as a whole, not individual episodes.
 * Use XtreamEpisode.toRawMediaMetadata() for episode-level metadata.
 *
 * @return RawMediaMetadata with series-specific fields
 */
fun XtreamSeriesItem.toRawMediaMetadata(): RawMediaMetadata = RawMediaMetadata(
    originalTitle = name,
    mediaType = MediaType.SERIES_EPISODE, // Marked as episode parent; normalizer may refine
    year = null,
    season = null,
    episode = null,
    durationMinutes = null,
    externalIds = ExternalIds(),
    sourceType = SourceType.XTREAM,
    sourceLabel = "Xtream Series",
    sourceId = "xtream:series:$id",
)

/**
 * Converts an XtreamEpisode to RawMediaMetadata.
 *
 * @param seriesName The parent series name (required for full context)
 * @return RawMediaMetadata with episode-specific fields
 */
fun XtreamEpisode.toRawMediaMetadata(seriesName: String? = null): RawMediaMetadata = RawMediaMetadata(
    originalTitle = title.ifBlank { seriesName ?: "Episode $episodeNumber" },
    mediaType = MediaType.SERIES_EPISODE,
    year = null,
    season = seasonNumber,
    episode = episodeNumber,
    durationMinutes = null,
    externalIds = ExternalIds(),
    sourceType = SourceType.XTREAM,
    sourceLabel = seriesName?.let { "Xtream: $it" } ?: "Xtream Series",
    sourceId = "xtream:episode:$id",
)

/**
 * Converts an XtreamChannel to RawMediaMetadata.
 *
 * @return RawMediaMetadata with live channel fields
 */
fun XtreamChannel.toRawMediaMetadata(): RawMediaMetadata = RawMediaMetadata(
    originalTitle = name,
    mediaType = MediaType.LIVE,
    year = null,
    season = null,
    episode = null,
    durationMinutes = null,
    externalIds = ExternalIds(),
    sourceType = SourceType.XTREAM,
    sourceLabel = "Xtream Live",
    sourceId = "xtream:live:$id",
)
