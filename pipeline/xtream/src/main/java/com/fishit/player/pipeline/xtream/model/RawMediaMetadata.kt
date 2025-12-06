package com.fishit.player.pipeline.xtream.model

/**
 * Temporary placeholder for RawMediaMetadata structure.
 *
 * This type is defined here temporarily for Phase 3 prep work.
 * In Phase 3, this will move to :core:metadata-normalizer module.
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
 */
enum class SourceType {
    XTREAM,
    TELEGRAM,
    IO,
    AUDIOBOOK,
    LOCAL,
    PLEX,
    OTHER,
}
