package com.fishit.player.feature.detail.model

import com.fishit.player.core.model.ImageRef

/**
 * UI model for detail screen media display.
 *
 * Architecture (v2 - INV-6 compliant):
 * - This is a UI-only model, NOT a DTO.
 * - Contains exactly what the DetailScreen needs to render.
 * - No logic, no methods, pure data.
 * - Does NOT import pipeline, transport, data, persistence.
 *
 * Populated by: Domain layer via DetailMediaRepository interface.
 * Consumed by: UnifiedDetailViewModel -> DetailScreen composables.
 */
data class DetailMediaInfo(
    /** Unique work identifier (NX_Work.workKey) */
    val workKey: String,
    /** Display title (cleaned/normalized) */
    val title: String,
    /** Media type: MOVIE, SERIES, EPISODE, LIVE, CLIP */
    val mediaType: String,
    /** Release year (null for LIVE) */
    val year: Int?,
    /** Season number (EPISODE only) */
    val season: Int?,
    /** Episode number (EPISODE only) */
    val episode: Int?,
    /** TMDB ID (for external links) */
    val tmdbId: String?,
    /** IMDB ID (for external links) */
    val imdbId: String?,
    /** Poster image reference */
    val poster: ImageRef?,
    /** Backdrop image reference */
    val backdrop: ImageRef?,
    /** Plot/description text */
    val plot: String?,
    /** Rating (0-10 scale) */
    val rating: Double?,
    /** Duration in milliseconds */
    val durationMs: Long?,
    /** Comma-separated genre list */
    val genres: String?,
    /** Director name(s) */
    val director: String?,
    /** Comma-separated cast list */
    val cast: String?,
    /** YouTube trailer URL */
    val trailer: String?,
    /** Adult content flag */
    val isAdult: Boolean,
    /** Available sources for playback */
    val sources: List<DetailSourceInfo>,
) {
    /** True if multiple sources are available */
    val hasMultipleSources: Boolean
        get() = sources.size > 1

    /** True if this is a series (has episodes) */
    val isSeries: Boolean
        get() = mediaType == "SERIES"

    /** True if this is an episode */
    val isEpisode: Boolean
        get() = mediaType == "EPISODE"

    /** True if this is live content */
    val isLive: Boolean
        get() = mediaType == "LIVE"
}
