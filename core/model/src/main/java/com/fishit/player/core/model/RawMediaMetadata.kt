package com.fishit.player.core.model

import com.fishit.player.core.model.ids.TmdbId

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
 * @property durationMinutes Media runtime in minutes if available
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
        val durationMinutes: Int? = null,
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
         * `movie:<title>:<year>` / `episode:<title>:SxxExx`).
         *
         * @see com.fishit.player.core.metadata.FallbackCanonicalKeyGenerator for fallback generation logic
         */
        val globalId: String = "",
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

/** External IDs from upstream sources. These MUST be passed through without modification. */
data class ExternalIds(
        val tmdbId: TmdbId? = null,
        val imdbId: String? = null,
        val tvdbId: String? = null,
)

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
