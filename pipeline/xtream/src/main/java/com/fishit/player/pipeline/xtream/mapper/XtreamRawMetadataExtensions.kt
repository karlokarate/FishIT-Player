package com.fishit.player.pipeline.xtream.mapper

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
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
private fun cleanLiveChannelName(name: String): String {
        return name.replace(UNICODE_DECORATORS, " ").replace(WHITESPACE_COLLAPSE, " ").trim()
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
fun XtreamVodItem.toRawMediaMetadata(
        authHeaders: Map<String, String> = emptyMap(),
): RawMediaMetadata {
        val rawTitle = name
        val rawYear: Int? = null // Xtream VOD list doesn't include year; detail fetch required
        // Encode containerExtension in sourceId for playback URL construction
        // Format: xtream:vod:{id}:{ext} or xtream:vod:{id} if no extension
        val sourceIdWithExt = containerExtension?.let { "xtream:vod:$id:$it" } ?: "xtream:vod:$id"
        // Build typed TMDB reference for movies
        val externalIds =
                tmdbId?.let { ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, it)) }
                        ?: ExternalIds()
        return RawMediaMetadata(
                originalTitle = rawTitle,
                mediaType = MediaType.MOVIE,
                year = rawYear,
                season = null,
                episode = null,
                durationMs = null, // Xtream VOD list doesn't include duration
                externalIds = externalIds,
                sourceType = SourceType.XTREAM,
                sourceLabel = "Xtream VOD",
                sourceId = sourceIdWithExt,
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
fun XtreamSeriesItem.toRawMediaMetadata(
        authHeaders: Map<String, String> = emptyMap(),
): RawMediaMetadata {
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
        // Encode containerExtension in sourceId for playback URL construction
        // Format: xtream:episode:{id}:{ext} or xtream:episode:{id} if no extension
        val sourceIdWithExt =
                containerExtension?.let { "xtream:episode:$id:$it" } ?: "xtream:episode:$id"
        // Build typed TMDB reference for episodes (uses series TMDB ID with TV type)
        val externalIds =
                seriesTmdbId?.let { ExternalIds(tmdb = TmdbRef(TmdbMediaType.TV, it)) }
                        ?: ExternalIds()
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
                sourceId = sourceIdWithExt,
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
fun XtreamChannel.toRawMediaMetadata(
        authHeaders: Map<String, String> = emptyMap(),
): RawMediaMetadata {
        // Clean Unicode decorators from live channel names
        val rawTitle = cleanLiveChannelName(name)
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
        )
}
