package com.fishit.player.pipeline.xtream.mapper

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.infra.transport.xtream.XtreamVodInfo
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem

/**
 * Extensions for converting Xtream pipeline models to RawMediaMetadata.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Provides RAW metadata only (no cleaning, no normalization, no heuristics)
 * - Title passed through exactly as received from Xtream API
 * - TMDB fields stored as typed TmdbRef (Gold Decision Dec 2025)
 * - All normalization delegated to :core:metadata-normalizer
 *
 * Per Gold Decision (Dec 2025) - Typed TMDB References:
 * - VOD items → TmdbRef(MOVIE, tmdbId)
 * - Series → TmdbRef(TV, tmdbId)
 * - Episodes → TmdbRef(TV, seriesTmdbId) + season/episode fields
 *
 * Per IMAGING_SYSTEM.md:
 * - ImageRef fields populated from source images
 * - Uses XtreamImageRefExtensions for conversion
 * - NO raw URLs passed through - only ImageRef
 *
 * Live Channel Names:
 * - Unicode block decorators (▃ ▅ ▆ █) are cleaned for display
 * - Country prefix (DE:, US:, etc.) is preserved
 *
 * These extensions enable seamless integration with the centralized metadata normalization
 * pipeline.
 */

// Unicode block characters used as decorators in live channel names
private val UNICODE_DECORATORS = Regex("[▃▅▆█▇▄▂░▒▓■□●○◆◇★☆⬛⬜]+")
private val WHITESPACE_COLLAPSE = Regex("\\s+")

/**
 * Clean live channel name by removing Unicode decorators.
 *
 * Examples:
 * - "▃ ▅ ▆ █ DE HEVC █ ▆ ▅ ▃" → "DE HEVC"
 * - "DE: RTL HD" → "DE: RTL HD" (no change needed)
 */
private fun cleanLiveChannelName(name: String): String = name.replace(UNICODE_DECORATORS, " ").replace(WHITESPACE_COLLAPSE, " ").trim()

/**
 * Parse duration string to milliseconds.
 *
 * Handles various formats from Xtream panels:
 * - "01:30:00" → 5400000 (HH:MM:SS)
 * - "90:00" → 5400000 (MM:SS)
 * - "90" → 5400000 (minutes as number)
 * - "90 min" → 5400000 (with unit suffix)
 * - null/empty → null
 */
private fun parseDurationToMs(duration: String?): Long? {
    if (duration.isNullOrBlank()) return null
    val cleaned = duration.trim().lowercase()

    // Try HH:MM:SS or MM:SS format
    val parts = cleaned.split(":")
    if (parts.size >= 2) {
        return try {
            when (parts.size) {
                3 -> { // HH:MM:SS
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val seconds = parts[2].toInt()
                    ((hours * 3600L + minutes * 60L + seconds) * 1000L)
                }
                2 -> { // MM:SS
                    val minutes = parts[0].toInt()
                    val seconds = parts[1].toInt()
                    ((minutes * 60L + seconds) * 1000L)
                }
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    // Try plain number (minutes) or "90 min" format
    val numberMatch = Regex("^(\\d+)").find(cleaned)
    if (numberMatch != null) {
        val minutes = numberMatch.groupValues[1].toLongOrNull() ?: return null
        return minutes * 60L * 1000L
    }

    return null
}

/**
 * Converts an XtreamVodItem to RawMediaMetadata.
 *
 * Per Gold Decision (Dec 2025):
 * - VOD items map to TmdbRef(MOVIE, tmdbId) when tmdbId is available
 *
 * @param authHeaders Optional headers for image URL authentication
 * @return RawMediaMetadata with VOD-specific fields
 */
fun XtreamVodItem.toRawMediaMetadata(authHeaders: Map<String, String> = emptyMap()): RawMediaMetadata {
    val rawTitle = name
    // Parse year from quick info field if available
    val rawYear: Int? = year?.toIntOrNull()
    // Parse duration - format varies: "01:30:00", "90", "90 min", etc.
    val durationMs: Long? = parseDurationToMs(duration)
    // Stable source ID format (contract): xtream:vod:{id}
    // Container extension is *format*, not identity.
    // Playback can infer a default extension or use detail fetch.
    val sourceIdStable = "xtream:vod:$id"
    // Build typed TMDB reference for movies
    val externalIds =
        tmdbId?.let { ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, it)) }
            ?: ExternalIds()
    // Build playback hints for VOD URL construction
    val hints =
        buildMap {
            put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_VOD)
            put(PlaybackHintKeys.Xtream.VOD_ID, id.toString())
            containerExtension?.takeIf { it.isNotBlank() }?.let {
                put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
            }
        }
    return RawMediaMetadata(
        originalTitle = rawTitle,
        mediaType = MediaType.MOVIE,
        year = rawYear,
        season = null,
        episode = null,
        durationMs = durationMs,
        externalIds = externalIds,
        sourceType = SourceType.XTREAM,
        sourceLabel = "Xtream VOD",
        sourceId = sourceIdStable,
        // === Pipeline Identity (v2) ===
        pipelineIdTag = PipelineIdTag.XTREAM,
        // === Timing (v2) - for "Recently Added" sorting ===
        addedTimestamp = added,
        // === Rating (v2) - TMDB rating from provider ===
        rating = rating,
        // === ImageRef from XtreamImageRefExtensions ===
        poster = toPosterImageRef(authHeaders),
        backdrop = null, // VOD list doesn't provide backdrop
        thumbnail = null, // Use poster as fallback in UI
        // === Playback Hints (v2) ===
        playbackHints = hints,
        // === Rich metadata (v2) - from provider TMDB scraping ===
        plot = plot,
        genres = genre, // Xtream uses "genre" singular
    )
}

/**
 * Converts an XtreamSeriesItem to RawMediaMetadata.
 *
 * Note: This represents the series as a whole, not individual episodes. Use
 * XtreamEpisode.toRawMediaMetadata() for episode-level metadata.
 *
 * Per Gold Decision (Dec 2025):
 * - Series map to TmdbRef(TV, tmdbId) when tmdbId is available
 *
 * @param authHeaders Optional headers for image URL authentication
 * @return RawMediaMetadata with series-specific fields
 */
fun XtreamSeriesItem.toRawMediaMetadata(authHeaders: Map<String, String> = emptyMap()): RawMediaMetadata {
    val rawTitle = name
    val rawYear = year?.toIntOrNull()
    // Build typed TMDB reference for TV shows
    val externalIds =
        tmdbId?.let { ExternalIds(tmdb = TmdbRef(TmdbMediaType.TV, it)) } ?: ExternalIds()
    return RawMediaMetadata(
        originalTitle = rawTitle,
        mediaType = MediaType.SERIES, // Series container, not episode
        year = rawYear,
        season = null,
        episode = null,
        durationMs = null, // Series list doesn't include duration
        externalIds = externalIds,
        sourceType = SourceType.XTREAM,
        sourceLabel = "Xtream Series",
        sourceId = "xtream:series:$id",
        // === Pipeline Identity (v2) ===
        pipelineIdTag = PipelineIdTag.XTREAM,
        // === Timing (v2) - for "Recently Updated" sorting ===
        addedTimestamp = lastModified,
        // === Rating (v2) - TMDB rating from provider ===
        rating = rating,
        // === ImageRef from XtreamImageRefExtensions ===
        poster = toPosterImageRef(authHeaders),
        backdrop = null, // Series list doesn't provide backdrop
        thumbnail = null,
        // === Rich metadata (v2) - from provider TMDB scraping ===
        plot = plot,
        genres = genre, // Xtream uses "genre" singular
        director = director,
        cast = cast,
    )
}

/**
 * Converts an XtreamEpisode to RawMediaMetadata.
 *
 * Uses the embedded seriesName property (set during loadEpisodes) for context. Falls back to
 * external parameter if provided.
 *
 * Per Gold Decision (Dec 2025):
 * - Episodes map to TmdbRef(TV, seriesTmdbId) - using series TMDB ID, not episode ID
 * - season/episode fields enable episode-level API calls: GET /tv/{id}/season/{s}/episode/{e}
 *
 * @param seriesNameOverride Optional override for parent series name
 * @param authHeaders Optional headers for image URL authentication
 * @return RawMediaMetadata with episode-specific fields
 */
fun XtreamEpisode.toRawMediaMetadata(
    seriesNameOverride: String? = null,
    authHeaders: Map<String, String> = emptyMap(),
): RawMediaMetadata {
    // Prefer embedded seriesName from data class, fall back to override parameter
    val effectiveSeriesName = seriesName ?: seriesNameOverride
    val rawTitle = title.ifBlank { effectiveSeriesName ?: "Episode $episodeNumber" }
    val rawYear: Int? = null // Episodes typically don't have year; inherit from series
    // Stable source ID format used across v2:
    // xtream:episode:{seriesId}:{season}:{episode}
    // (episode stream id is stored in provider tables, not in the identity string)
    val sourceIdStable = "xtream:episode:$seriesId:$seasonNumber:$episodeNumber"
    // Build typed TMDB reference for episodes (uses series TMDB ID with TV type)
    val externalIds =
        seriesTmdbId?.let { ExternalIds(tmdb = TmdbRef(TmdbMediaType.TV, it)) }
            ?: ExternalIds()
    // Build playback hints for episode URL construction
    // **Critical:** episodeId (this.id) is the stream ID needed for playback URL
    val hints =
        buildMap {
            put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_SERIES)
            put(PlaybackHintKeys.Xtream.SERIES_ID, seriesId.toString())
            put(PlaybackHintKeys.Xtream.SEASON_NUMBER, seasonNumber.toString())
            put(PlaybackHintKeys.Xtream.EPISODE_NUMBER, episodeNumber.toString())
            // Episode ID (stream ID) - CRITICAL for URL construction
            put(PlaybackHintKeys.Xtream.EPISODE_ID, id.toString())
            containerExtension?.takeIf { it.isNotBlank() }?.let {
                put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
            }
        }
    return RawMediaMetadata(
        originalTitle = rawTitle,
        mediaType = MediaType.SERIES_EPISODE,
        year = rawYear,
        season = seasonNumber,
        episode = episodeNumber,
        durationMs = null, // Episode list doesn't include duration
        externalIds = externalIds,
        sourceType = SourceType.XTREAM,
        sourceLabel = effectiveSeriesName?.let { "Xtream: $it" } ?: "Xtream Series",
        sourceId = sourceIdStable,
        // === Pipeline Identity (v2) ===
        pipelineIdTag = PipelineIdTag.XTREAM,
        // === Timing (v2) - for "Recently Added" sorting ===
        addedTimestamp = added,
        // === Rating (v2) ===
        rating = rating,
        // === ImageRef from XtreamImageRefExtensions ===
        poster = null, // Episodes don't have poster; inherit from series
        backdrop = null,
        thumbnail = toThumbnailImageRef(authHeaders),
        // === Playback Hints (v2) ===
        playbackHints = hints,
    )
}

/**
 * Converts an XtreamChannel to RawMediaMetadata.
 *
 * Live channel names are cleaned:
 * - Unicode block decorators (▃ ▅ ▆ █) removed
 * - Multiple spaces collapsed
 * - Country prefix (DE:, US:) preserved
 *
 * @param authHeaders Optional headers for image URL authentication
 * @return RawMediaMetadata with live channel fields
 */
fun XtreamChannel.toRawMediaMetadata(authHeaders: Map<String, String> = emptyMap()): RawMediaMetadata {
    // Clean Unicode decorators from live channel names
    val rawTitle = cleanLiveChannelName(name)
    // Build playback hints for live stream URL construction
    // Note: Live channels don't have containerExtension - streams are typically .ts or .m3u8
    val hints =
        buildMap {
            put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_LIVE)
            put(PlaybackHintKeys.Xtream.STREAM_ID, id.toString())
        }
    return RawMediaMetadata(
        originalTitle = rawTitle,
        mediaType = MediaType.LIVE, // Live channels - NO year/scene parsing needed
        year = null,
        season = null,
        episode = null,
        durationMs = null, // Live channels don't have duration
        externalIds = ExternalIds(),
        sourceType = SourceType.XTREAM,
        sourceLabel = "Xtream Live",
        sourceId = "xtream:live:$id",
        // === Pipeline Identity (v2) ===
        pipelineIdTag = PipelineIdTag.XTREAM,
        // === Timing (v2) - for "Recently Added" sorting ===
        addedTimestamp = added,
        // === ImageRef from XtreamImageRefExtensions ===
        poster = toLogoImageRef(authHeaders), // Use logo as poster for channels
        backdrop = null,
        thumbnail = toLogoImageRef(authHeaders), // Thumbnail same as logo
        // === Playback Hints (v2) ===
        playbackHints = hints,
    )
}

// =============================================================================
// VOD Detail Extensions (from get_vod_info API)
// =============================================================================

/**
 * Converts XtreamVodInfo (from get_vod_info) to RawMediaMetadata with full rich metadata.
 *
 * This provides all metadata fields available from the detail API:
 * - plot, genres, director, cast
 * - rating, duration
 * - backdrop images
 * - IMDB/TMDB IDs
 * - YouTube trailer URLs
 *
 * Use this for detail screens where full metadata is needed. For list views, use
 * XtreamVodItem.toRawMediaMetadata() instead.
 *
 * @param vodItem The original VOD item (for stream_id and container_extension)
 * @param authHeaders Optional headers for image URL authentication
 * @return RawMediaMetadata with all available rich metadata
 */
fun XtreamVodInfo.toRawMediaMetadata(
    vodItem: XtreamVodItem,
    authHeaders: Map<String, String> = emptyMap(),
): RawMediaMetadata {
    val infoBlock = info
    val movieData = movieData

    // Use info block title, fall back to movie_data name, then original vodItem name
    val rawTitle = infoBlock?.name ?: infoBlock?.originalName ?: movieData?.name ?: vodItem.name

    // Extract year from info block
    val rawYear = infoBlock?.year?.toIntOrNull()

    // Duration: prefer durationSecs (in seconds), convert to ms
    val durationMs = infoBlock?.durationSecs?.let { it * 1000L }

    // Rating: prefer rating string parsed, fall back to rating5Based * 2
    val rating =
        infoBlock?.rating?.toDoubleOrNull()
            ?: infoBlock?.rating5Based?.let { it * 2.0 } ?: vodItem.rating

    // Stable source ID format (contract): xtream:vod:{streamId}
    // Container extension is *format*, not identity.
    val streamId = movieData?.streamId ?: vodItem.id
    val sourceIdStable = "xtream:vod:$streamId"

    // Build TMDB reference - prefer info block, fall back to vodItem
    val tmdbId = infoBlock?.tmdbId?.toIntOrNull() ?: vodItem.tmdbId
    val externalIds =
        tmdbId?.let { ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, it)) }
            ?: ExternalIds()

    // Rich metadata from info block
    val plot = infoBlock?.resolvedPlot

    val genres = infoBlock?.resolvedGenre

    val director = infoBlock?.director

    val cast = infoBlock?.resolvedCast

    // Trailer URL - use resolved property which handles multiple field names
    val trailer = infoBlock?.resolvedTrailer

    // Images: use info block images if available, fall back to vodItem
    val posterUrl = infoBlock?.resolvedPoster

    return RawMediaMetadata(
        originalTitle = rawTitle,
        mediaType = MediaType.MOVIE,
        year = rawYear,
        season = null,
        episode = null,
        durationMs = durationMs,
        externalIds = externalIds,
        sourceType = SourceType.XTREAM,
        sourceLabel = "Xtream VOD",
        sourceId = sourceIdStable,
        // === Pipeline Identity (v2) ===
        pipelineIdTag = PipelineIdTag.XTREAM,
        // === Timing (v2) ===
        addedTimestamp = movieData?.added?.toLongOrNull() ?: vodItem.added,
        // === Rating (v2) ===
        rating = rating,
        // === ImageRef ===
        poster =
            posterUrl?.let { createImageRef(it, authHeaders) }
                ?: vodItem.toPosterImageRef(authHeaders),
        backdrop =
            infoBlock?.backdropPath?.firstOrNull()?.let {
                createImageRef(it, authHeaders)
            },
        thumbnail = null,
        // === Rich metadata (v2) ===
        plot = plot,
        genres = genres,
        director = director,
        cast = cast,
        trailer = trailer,
        // === Playback Hints (v2) ===
        playbackHints =
            buildMap {
                put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_VOD)
                put(PlaybackHintKeys.Xtream.VOD_ID, streamId.toString())
                movieData?.containerExtension?.takeIf { it.isNotBlank() }?.let {
                    put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
                } ?: vodItem.containerExtension?.takeIf { it.isNotBlank() }?.let {
                    put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
                }
            },
    )
}

/**
 * Creates an ImageRef from a URL string. Helper for XtreamVodInfo mapping where we have raw URLs.
 */
private fun createImageRef(
    url: String,
    authHeaders: Map<String, String>,
): com.fishit.player.core.model.ImageRef? {
    if (url.isBlank()) return null
    return com.fishit.player.core.model.ImageRef.Http(
        url = url,
        headers = authHeaders.takeIf { it.isNotEmpty() } ?: emptyMap(),
    )
}
