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
 * Per IMAGING_SYSTEM.md:
 * - ImageRef fields populated from source images
 * - Uses XtreamImageRefExtensions for conversion
 * - NO raw URLs passed through - only ImageRef
 *
 * These extensions enable seamless integration with the centralized
 * metadata normalization pipeline.
 */

/**
 * Converts an XtreamVodItem to RawMediaMetadata.
 *
 * @param authHeaders Optional headers for image URL authentication
 * @return RawMediaMetadata with VOD-specific fields
 */
fun XtreamVodItem.toRawMediaMetadata(
    authHeaders: Map<String, String> = emptyMap(),
): RawMediaMetadata = RawMediaMetadata(
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
    // === ImageRef from XtreamImageRefExtensions ===
    poster = toPosterImageRef(authHeaders),
    backdrop = null, // VOD list doesn't provide backdrop
    thumbnail = null, // Use poster as fallback in UI
)

/**
 * Converts an XtreamSeriesItem to RawMediaMetadata.
 *
 * Note: This represents the series as a whole, not individual episodes.
 * Use XtreamEpisode.toRawMediaMetadata() for episode-level metadata.
 *
 * @param authHeaders Optional headers for image URL authentication
 * @return RawMediaMetadata with series-specific fields
 */
fun XtreamSeriesItem.toRawMediaMetadata(
    authHeaders: Map<String, String> = emptyMap(),
): RawMediaMetadata = RawMediaMetadata(
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
    // === ImageRef from XtreamImageRefExtensions ===
    poster = toPosterImageRef(authHeaders),
    backdrop = null, // Series list doesn't provide backdrop
    thumbnail = null,
)

/**
 * Converts an XtreamEpisode to RawMediaMetadata.
 *
 * @param seriesName The parent series name (required for full context)
 * @param authHeaders Optional headers for image URL authentication
 * @return RawMediaMetadata with episode-specific fields
 */
fun XtreamEpisode.toRawMediaMetadata(
    seriesName: String? = null,
    authHeaders: Map<String, String> = emptyMap(),
): RawMediaMetadata = RawMediaMetadata(
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
    // === ImageRef from XtreamImageRefExtensions ===
    poster = null, // Episodes don't have poster; inherit from series
    backdrop = null,
    thumbnail = toThumbnailImageRef(authHeaders),
)

/**
 * Converts an XtreamChannel to RawMediaMetadata.
 *
 * @param authHeaders Optional headers for image URL authentication
 * @return RawMediaMetadata with live channel fields
 */
fun XtreamChannel.toRawMediaMetadata(
    authHeaders: Map<String, String> = emptyMap(),
): RawMediaMetadata = RawMediaMetadata(
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
    // === ImageRef from XtreamImageRefExtensions ===
    poster = toLogoImageRef(authHeaders), // Use logo as poster for channels
    backdrop = null,
    thumbnail = toLogoImageRef(authHeaders), // Thumbnail same as logo
)
