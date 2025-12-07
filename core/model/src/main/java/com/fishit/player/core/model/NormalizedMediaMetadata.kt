package com.fishit.player.core.model

/**
 * Normalized media metadata after processing by the metadata normalizer.
 *
 * This type is produced by :core:metadata-normalizer from RawMediaMetadata.
 * It contains cleaned, standardized metadata used for canonical identity.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Only the normalizer/TMDB resolver may populate these fields
 * - Pipelines must treat this as read-only
 * - Deterministic: same input → same normalized output
 *
 * @property canonicalTitle Cleaned, normalized title used for canonical identity
 * @property mediaType Type of media content (may be refined during normalization)
 * @property year Release year (possibly refined after normalization/TMDB enrichment)
 * @property season Season number for episodes, null for movies
 * @property episode Episode number for episodes, null for movies
 * @property tmdbId TMDB ID from resolver or trusted upstream source
 * @property externalIds Aggregated external IDs (TMDB, IMDB, TVDB, etc.)
 */
data class NormalizedMediaMetadata(
    val canonicalTitle: String,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val tmdbId: String? = null,
    val externalIds: ExternalIds = ExternalIds(),
)
