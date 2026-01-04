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
 * **Typed TMDB Reference (Gold Decision Dec 2025):**
 * - [tmdb] is the typed TMDB reference (MOVIE or TV) - preferred field
 * - Use [tmdb] directly for TMDB API calls without conversion
 * - Episodes: tmdb.type=TV + season/episode fields (never EPISODE type)
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
 * @property tmdb Typed TMDB reference (MOVIE or TV type + ID). For API calls without conversion.
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
    /**
     * Typed TMDB reference from resolver or trusted upstream source.
     *
     * Use directly for TMDB API calls:
     * - MOVIE → GET /movie/{id}
     * - TV → GET /tv/{id}
     * - Episode: GET /tv/{id}/season/{season}/episode/{episode}
     */
    val tmdb: TmdbRef? = null,
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
    // === Rich Metadata Fields (v2 - Pipeline Passthrough) ===
    /**
     * Plot/description passed through from source.
     *
     * May be enriched/replaced by TMDB resolver.
     */
    val plot: String? = null,
    /**
     * Comma-separated genre list passed through from source.
     *
     * May be enriched/replaced by TMDB resolver.
     */
    val genres: String? = null,
    /**
     * Director name(s) passed through from source.
     *
     * May be enriched/replaced by TMDB resolver.
     */
    val director: String? = null,
    /**
     * Comma-separated cast list passed through from source.
     *
     * May be enriched/replaced by TMDB resolver.
     */
    val cast: String? = null,
    /**
     * Rating (0.0-10.0 scale) passed through from source.
     *
     * Xtream provides this from TMDB scraping. May be updated by TMDB resolver.
     */
    val rating: Double? = null,
    /**
     * Runtime in milliseconds passed through from source.
     *
     * Useful for display before TMDB enrichment.
     */
    val durationMs: Long? = null,
    /**
     * Trailer URL passed through from source.
     *
     * May be a YouTube URL or video ID. May be enriched by TMDB resolver.
     */
    val trailer: String? = null,
)
