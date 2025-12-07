package com.fishit.player.core.metadata

import com.fishit.player.core.metadata.parser.RegexSceneNameParser
import com.fishit.player.core.metadata.parser.SceneNameParser
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.RawMediaMetadata

/**
 * Default no-op implementation of MediaMetadataNormalizer.
 *
 * Phase 3 skeleton behavior:
 * - Returns the input RawMediaMetadata wrapped as NormalizedMediaMetadata
 * - No title cleaning, no scene-naming parsing
 * - Uses originalTitle as canonicalTitle
 * - Copies all other fields as-is (including ImageRefs)
 *
 * This is intentional – full normalization logic comes in later phases.
 */
class DefaultMediaMetadataNormalizer : MediaMetadataNormalizer {
    override suspend fun normalize(raw: RawMediaMetadata): NormalizedMediaMetadata =
            NormalizedMediaMetadata(
                    canonicalTitle = raw.originalTitle, // No cleaning - pass through
                    mediaType = raw.mediaType, // Pass through media type
                    year = raw.year,
                    season = raw.season,
                    episode = raw.episode,
                    tmdbId = raw.externalIds.tmdbId, // Pass through if provided by source
                    externalIds = raw.externalIds,
                    // === Pass through ImageRefs from pipeline ===
                    poster = raw.poster,
                    backdrop = raw.backdrop,
                    thumbnail = raw.thumbnail,
                    placeholderThumbnail =
                            raw.placeholderThumbnail, // Minithumbnail for blur placeholder
            )
}

/**
 * Regex-based media metadata normalizer.
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
 * @property sceneParser Parser for extracting metadata from filenames
 */
class RegexMediaMetadataNormalizer(
        private val sceneParser: SceneNameParser = RegexSceneNameParser(),
) : MediaMetadataNormalizer {
    override suspend fun normalize(raw: RawMediaMetadata): NormalizedMediaMetadata {
        // Parse filename to extract metadata
        val parsed = sceneParser.parse(raw.originalTitle)

        // Use parsed title as canonical (it's already cleaned)
        val canonicalTitle = parsed.title

        // Prefer explicit metadata from raw over parsed
        // (e.g., Xtream API provides structured data)
        val year = raw.year ?: parsed.year
        val season = raw.season ?: parsed.season
        val episode = raw.episode ?: parsed.episode

        // Refine media type based on parsed metadata if UNKNOWN
        val mediaType =
                if (raw.mediaType == MediaType.UNKNOWN) {
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
                tmdbId = raw.externalIds.tmdbId, // Pass through if provided by source
                externalIds = raw.externalIds,
                // === Pass through ImageRefs from pipeline ===
                poster = raw.poster,
                backdrop = raw.backdrop,
                thumbnail = raw.thumbnail,
                placeholderThumbnail =
                        raw.placeholderThumbnail, // Minithumbnail for blur placeholder
        )
    }
}
