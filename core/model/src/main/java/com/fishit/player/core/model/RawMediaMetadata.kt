package com.fishit.player.core.model

/**
 * Raw media metadata from a pipeline source.
 *
 * This type is shared across all pipeline modules (Telegram, Xtream, IO, etc.)
 * and serves as the input to the centralized metadata normalization system.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Pipelines MUST provide raw metadata without normalization
 * - NO title cleaning, heuristics, or TMDB lookups in pipeline code
 * - All normalization handled centrally by :core:metadata-normalizer
 *
 * @property originalTitle Human-readable title as provided by source (NO cleaning)
 * @property year Release year if available
 * @property season Season number for episodes, null for movies/VOD
 * @property episode Episode number for episodes, null for movies/VOD
 * @property durationMinutes Media runtime in minutes if available
 * @property externalIds External IDs provided by upstream (e.g., TMDB from Xtream)
 * @property sourceType Pipeline identifier (XTREAM, TELEGRAM, IO, etc.)
 * @property sourceLabel Human-readable label for UI
 * @property sourceId Stable unique identifier within pipeline
 */
data class RawMediaMetadata(
    val originalTitle: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val durationMinutes: Int? = null,
    val externalIds: ExternalIds = ExternalIds(),
    val sourceType: SourceType,
    val sourceLabel: String,
    val sourceId: String,
)

/**
 * External IDs from upstream sources.
 * These MUST be passed through without modification.
 */
data class ExternalIds(
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val tvdbId: String? = null,
)

/**
 * Source type identifier for pipeline.
 *
 * Identifies which pipeline provided the raw media metadata.
 * Used for tracking, debugging, and source-specific behavior.
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
}
