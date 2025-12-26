package com.fishit.player.core.model

/**
 * Raw media metadata from a pipeline source.
 *
 * This type is shared across all pipeline modules (Telegram, Xtream, IO, etc.) and serves as the
 * input to the centralized metadata normalization system.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Pipelines MUST provide raw metadata without normalization
 * - NO title cleaning, heuristics, or TMDB lookups in pipeline code
 * - All normalization handled centrally by :core:metadata-normalizer
 *
 * **Imaging Contract (IMAGING_SYSTEM.md):**
 * - Pipelines populate ImageRef fields from source data
 * - ImageRef is the ONLY way to pass images through the system
 * - NO raw URLs or TDLib DTOs allowed past this layer
 *
 * @property originalTitle Human-readable title as provided by source (NO cleaning)
 * @property mediaType Type of media content (MOVIE, SERIES_EPISODE, LIVE, CLIP, etc.)
 * @property year Release year if available
 * @property season Season number for episodes, null for movies/VOD
 * @property episode Episode number for episodes, null for movies/VOD
 * @property durationMs Media runtime in **milliseconds** (v2 standard). Pipelines convert from source units.
 * @property externalIds External IDs provided by upstream (e.g., TMDB from Xtream)
 * @property sourceType Pipeline identifier (XTREAM, TELEGRAM, IO, etc.)
 * @property sourceLabel Human-readable label for UI
 * @property sourceId Stable unique identifier within pipeline
 * @property poster Primary poster image reference (portrait, ~2:3 aspect)
 * @property backdrop Backdrop/banner image reference (landscape, ~16:9 aspect)
 * @property thumbnail Small preview thumbnail (for lists/grids)
 * @property placeholderThumbnail Inline blur placeholder (for instant display before thumbnail
 * loads)
 */
data class RawMediaMetadata(
    val originalTitle: String,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /**
     * Media runtime in **milliseconds**.
     *
     * **v2 Standard:** All durations in RawMediaMetadata are stored as milliseconds.
     * Pipelines MUST convert from source units:
     * - Telegram: `durationSecs * 1000L`
     * - Xtream: depends on source (usually not available in list API)
     * - IO: `durationMs` from MediaStore or FFmpeg probe
     *
     * UI/Player can convert to display format (e.g., "1h 32m") as needed.
     */
    val durationMs: Long? = null,
    val externalIds: ExternalIds = ExternalIds(),
    val sourceType: SourceType,
    val sourceLabel: String,
    val sourceId: String,
    // === Pipeline Identity (v2) ===
    /** Pipeline that produced this metadata */
    val pipelineIdTag: PipelineIdTag = PipelineIdTag.UNKNOWN,
    /**
     * Canonical identity key for cross-pipeline deduplication.
     *
     * **PIPELINE CONTRACT:** Pipelines MUST leave this empty (""). Canonical identity is
     * assigned centrally by `:core:metadata-normalizer` during unification.
     *
     * Format when set by normalizer: contract canonical key (`tmdb:<id>` or
     * `movie:<title>[:<year>]` / `episode:<title>:SxxExx`).
     *
     * @see com.fishit.player.core.metadata.FallbackCanonicalKeyGenerator for generation logic
     */
    val globalId: String = "",
    // === Timing Fields (v2) ===
    /**
     * Unix epoch timestamp (seconds) when this item was added to the source.
     *
     * Used for:
     * - "Recently Added" sorting
     * - New content discovery
     * - Catalog freshness detection
     *
     * Pipelines should extract this from source (e.g., Xtream "added" field). Null if not
     * available from source.
     */
    val addedTimestamp: Long? = null,
    // === Imaging Fields (v2) ===
    val poster: ImageRef? = null,
    val backdrop: ImageRef? = null,
    val thumbnail: ImageRef? = null,
    /**
     * Minithumbnail (inline JPEG bytes) for instant blur placeholder before full thumbnail
     * loads
     */
    val placeholderThumbnail: ImageRef? = null,
    // === Rating Fields (v2) ===
    /**
     * User rating from source (0.0-10.0 scale).
     *
     * Xtream provides this directly from TMDB scraping. Used for sorting and display before
     * normalization.
     */
    val rating: Double? = null,
    // === Age Rating Fields (v2 - Structured Bundles) ===
    /**
     * Age rating from source (FSK/MPAA/etc.) for Kids filter.
     *
     * Structured Telegram bundles provide this directly (fsk field).
     * Range: 0-21 (per Schema Guards in TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md R4).
     * 0 = FSK 0 (all ages), 6/12/16/18 = FSK ratings, 21 = explicit adult.
     *
     * Used for:
     * - Kids profile content filtering
     * - Age gate enforcement
     * - Parental controls
     */
    val ageRating: Int? = null,
    // === Rich Metadata Fields (v2 - Pipeline Passthrough) ===
    /**
     * Plot/description from source.
     *
     * Xtream provides this from API ("plot" field).
     * Telegram structured bundles may provide this.
     * Passed through to canonical storage for display in detail screens.
     */
    val plot: String? = null,
    /**
     * Comma-separated genre list from source.
     *
     * Xtream provides this from API ("genre" field).
     * Example: "Action, Sci-Fi, Thriller"
     */
    val genres: String? = null,
    /**
     * Director name(s) from source.
     *
     * Xtream provides this from API ("director" field).
     */
    val director: String? = null,
    /**
     * Comma-separated cast list from source.
     *
     * Xtream provides this from API ("cast" field).
     * Example: "Tom Hanks, Robin Wright, Gary Sinise"
     */
    val cast: String? = null,
    // === Playback Hints (v2) ===
    /**
     * Source-specific hints required for playback but NOT part of media identity.
     *
     * **Contract:**
     * - Contains data needed by PlaybackSourceFactory to build playback URL/source
     * - Does NOT affect canonical identity or deduplication
     * - Keys are defined in [PlaybackHintKeys] for type-safety
     * - Values are always strings (factory converts as needed)
     *
     * **Use Cases:**
     * - Xtream episodeId (stream ID distinct from episode number)
     * - Xtream containerExtension (mp4, mkv, ts)
     * - Telegram fileId, chatId, messageId
     *
     * **Flow:** Pipeline → RawMediaMetadata → MediaSourceRef → PlaybackContext.extras
     *
     * @see PlaybackHintKeys
     */
    val playbackHints: Map<String, String> = emptyMap(),
)

/**
 * External IDs from upstream sources. These MUST be passed through without modification.
 *
 * **Typed TMDB Reference (Gold Decision Dec 2025):**
 * - [tmdb] is the typed TMDB reference (MOVIE or TV) - preferred field
 * - [legacyTmdbId] is deprecated, kept only for migration of existing data
 *
 * For episodes: Use `TmdbRef(TV, seriesId)` with `season/episode` from RawMediaMetadata.
 * Never create episode-specific TMDB refs.
 *
 * @property tmdb Typed TMDB reference (MOVIE or TV type + ID). Preferred field.
 * @property legacyTmdbId DEPRECATED: Untyped TMDB ID for migration only. Do not use in new code.
 * @property imdbId IMDb ID (tt-prefixed string, e.g., "tt0137523")
 * @property tvdbId TheTVDB ID
 */
data class ExternalIds(
    /**
     * Typed TMDB reference.
     *
     * - For movies: TmdbRef(MOVIE, movieId)
     * - For TV shows/series: TmdbRef(TV, seriesId)
     * - For episodes: TmdbRef(TV, seriesId) + season/episode from RawMediaMetadata
     *
     * Never use EPISODE type (TMDB has no episode root type).
     */
    val tmdb: TmdbRef? = null,
    /**
     * DEPRECATED: Legacy untyped TMDB ID.
     *
     * Kept only for migration of existing stored data. New code MUST use [tmdb] field.
     * Migration: Convert to typed [tmdb] using MediaType context.
     */
    @Deprecated("Use tmdb (typed TmdbRef) instead", replaceWith = ReplaceWith("tmdb"))
    val legacyTmdbId: Int? = null,
    val imdbId: String? = null,
    val tvdbId: String? = null,
) {
    /**
     * Get the effective TMDB ID (from typed or legacy field).
     *
     * Prefers typed [tmdb] field. Falls back to [legacyTmdbId] for migration compatibility.
     */
    @Suppress("DEPRECATION")
    val effectiveTmdbId: Int?
        get() = tmdb?.id ?: legacyTmdbId

    /**
     * Check if this has any TMDB reference (typed or legacy).
     */
    @Suppress("DEPRECATION")
    val hasTmdb: Boolean
        get() = tmdb != null || legacyTmdbId != null

    companion object {
        /**
         * Create ExternalIds from legacy untyped TMDB ID with migration.
         *
         * @param tmdbId Legacy untyped TMDB ID
         * @param mediaType MediaType to determine TMDB type
         * @return ExternalIds with typed tmdb field if type can be determined
         */
        fun fromLegacyTmdbId(
            tmdbId: Int,
            mediaType: MediaType,
        ): ExternalIds {
            val tmdbRef =
                when (mediaType) {
                    MediaType.MOVIE -> TmdbRef(TmdbMediaType.MOVIE, tmdbId)
                    MediaType.SERIES, MediaType.SERIES_EPISODE -> TmdbRef(TmdbMediaType.TV, tmdbId)
                    else -> null // Cannot determine type, store as legacy
                }
            return if (tmdbRef != null) {
                ExternalIds(tmdb = tmdbRef)
            } else {
                @Suppress("DEPRECATION")
                ExternalIds(legacyTmdbId = tmdbId)
            }
        }
    }
}

/**
 * Source type identifier for pipeline.
 *
 * Identifies which pipeline provided the raw media metadata. Used for tracking, debugging, and
 * source-specific behavior.
 *
 * Use OTHER only when the source doesn't fit any specific category.
 */
enum class SourceType {
    /** Xtream Codes API provider (IPTV, VOD, Series) */
    XTREAM,

    /** Telegram media integration via TDLib */
    TELEGRAM,

    /** Local file system, SAF, SMB, ContentResolver */
    IO,

    /** Audiobook-specific pipeline */
    AUDIOBOOK,

    /** Local media library scanner */
    LOCAL,

    /** Plex Media Server integration */
    PLEX,

    /** Other/unknown source types */
    OTHER,

    /** Unknown source type */
    UNKNOWN,
}
