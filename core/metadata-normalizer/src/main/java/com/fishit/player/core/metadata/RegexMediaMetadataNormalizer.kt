package com.fishit.player.core.metadata

import com.fishit.player.core.metadata.parser.Re2jSceneNameParser
import com.fishit.player.core.metadata.parser.SceneNameParser
import com.fishit.player.core.model.Layer
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.PipelineComponent
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType

/**
 * Regex-based media metadata normalizer (SSOT implementation).
 *
 * Uses SceneNameParser to extract structured metadata from filenames, then maps to
 * NormalizedMediaMetadata per MEDIA_NORMALIZATION_CONTRACT.md.
 *
 * Key behaviors:
 * - Cleans titles by removing technical tags (resolution, codec, source, group)
 * - Extracts year, season, episode from scene-style naming
 * - Prefers explicit metadata from RawMediaMetadata over parsed values
 * - Deterministic: same input → same output
 *
 * ## Why RE2J?
 *
 * This normalizer processes **untrusted input** from Telegram and user filenames. RE2J guarantees
 * O(n) linear time - no catastrophic backtracking possible. A malformed title can NEVER block the
 * system.
 *
 * @property sceneParser Parser for extracting metadata from filenames (RE2J by default)
 *
 * @responsibility Clean and normalize media titles from raw input
 * @responsibility Extract year, season, episode from scene-style naming
 * @responsibility Determine media type from metadata heuristics
 * @responsibility Handle untrusted input safely with RE2J (O(n) guarantee)
 */
@PipelineComponent(
    layer = Layer.NORMALIZER,
    sourceType = "All",
    genericPattern = "MediaMetadataNormalizer",
)
class RegexMediaMetadataNormalizer(
    private val sceneParser: SceneNameParser = Re2jSceneNameParser(),
) : MediaMetadataNormalizer {
    override suspend fun normalize(raw: RawMediaMetadata): NormalizedMediaMetadata {
        // Parse filename to extract metadata
        val parsed = sceneParser.parse(raw.originalTitle)

        // Use parsed title as canonical (it's already cleaned)
        // Fallback to "[Untitled]" if parser returns blank (defense-in-depth)
        val canonicalTitle = parsed.title.ifBlank { "[Untitled]" }

        // Prefer explicit metadata from raw over parsed
        // (e.g., Xtream API provides structured data)
        val year = raw.year ?: parsed.year
        val season = raw.season ?: parsed.season
        val episode = raw.episode ?: parsed.episode

        // Refine media type based on parsed metadata if UNKNOWN
        val mediaType =
            if (raw.mediaType == MediaType.UNKNOWN && raw.sourceType != SourceType.XTREAM) {
                when {
                    season != null && episode != null -> MediaType.SERIES_EPISODE
                    year != null -> MediaType.MOVIE
                    else -> MediaType.UNKNOWN
                }
            } else {
                raw.mediaType
            }

        return NormalizedMediaMetadata(
            canonicalTitle = canonicalTitle,
            mediaType = mediaType,
            year = year,
            season = season,
            episode = episode,
            tmdb = raw.externalIds.tmdb, // Pass through typed TMDB ref if provided
            externalIds = raw.externalIds,
            // === Pass through ImageRefs from pipeline ===
            poster = raw.poster,
            backdrop = raw.backdrop,
            thumbnail = raw.thumbnail,
            placeholderThumbnail =
                raw.placeholderThumbnail, // Minithumbnail for blur placeholder
            // === Pass through rich metadata from pipeline ===
            plot = raw.plot,
            genres = raw.genres,
            director = raw.director,
            cast = raw.cast,
            rating = raw.rating ?: parsed.rating,
            durationMs = raw.durationMs,
            trailer = raw.trailer,
            releaseDate = raw.releaseDate,
            // === Pass through content classification ===
            isAdult = raw.isAdult,
            categoryId = raw.categoryId,
            // === Pass through live channel fields ===
            epgChannelId = raw.epgChannelId,
            tvArchive = raw.tvArchive,
            tvArchiveDuration = raw.tvArchiveDuration,
            // === Pass through timing ===
            addedTimestamp = raw.addedTimestamp,
        )
    }
}
