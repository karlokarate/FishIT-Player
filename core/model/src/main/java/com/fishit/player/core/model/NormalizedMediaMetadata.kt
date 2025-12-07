package com.fishit.player.core.model

/**
 * Normalized media metadata after processing by the metadata normalizer.
 *
 * This type is produced by :core:metadata-normalizer from RawMediaMetadata. It contains cleaned,
 * standardized metadata used for canonical identity.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Only the normalizer/TMDB resolver may populate these fields
 * - Pipelines must treat this as read-only
 * - Deterministic: same input → same normalized output
 *
 * **Imaging Contract:**
 * - ImageRef fields (poster, backdrop, thumbnail) are populated by normalizer
 * - Pipelines MUST NOT use raw URLs or TDLib DTOs
 * - UI consumes ImageRef via GlobalImageLoader (:core:ui-imaging)
 * - See [ImageRef] for variant types and usage
 *
 * @property canonicalTitle Cleaned, normalized title used for canonical identity
 * @property mediaType Type of media content (may be refined during normalization)
 * @property year Release year (possibly refined after normalization/TMDB enrichment)
 * @property season Season number for episodes, null for movies
 * @property episode Episode number for episodes, null for movies
 * @property tmdbId TMDB ID from resolver or trusted upstream source
 * @property externalIds Aggregated external IDs (TMDB, IMDB, TVDB, etc.)
 * @property poster Primary poster image reference (portrait, ~2:3 aspect)
 * @property backdrop Backdrop/banner image reference (landscape, ~16:9 aspect)
 * @property thumbnail Small preview thumbnail (for lists/grids)
 * @property placeholderThumbnail Inline blur placeholder (for instant display before thumbnail
 * loads)
 */
data class NormalizedMediaMetadata(
        val canonicalTitle: String,
        val mediaType: MediaType = MediaType.UNKNOWN,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val tmdbId: String? = null,
        val externalIds: ExternalIds = ExternalIds(),
        // === Imaging Fields (v2) ===
        val poster: ImageRef? = null,
        val backdrop: ImageRef? = null,
        val thumbnail: ImageRef? = null,
        /**
         * Minithumbnail (inline JPEG bytes) for instant blur placeholder before full thumbnail
         * loads
         */
        val placeholderThumbnail: ImageRef? = null,
)
