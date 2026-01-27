package com.fishit.player.pipeline.xtream.model

import java.util.Objects

/**
 * Represents a TV series in the Xtream pipeline.
 *
 * This is a pipeline-internal DTO that will be converted to RawMediaMetadata via
 * toRawMediaMetadata() extension function.
 *
 * @property id Unique identifier for this series (may be negative on some panels)
 * @property name Display name of the series
 * @property cover URL to the series cover/poster
 * @property backdrop URL to the series backdrop image
 * @property categoryId Category identifier this series belongs to
 * @property year Release year (either from 'year' field or extracted from 'releaseDate')
 * @property rating Rating (0-10 scale)
 * @property plot Plot/description
 * @property cast Cast members
 * @property director Director(s)
 * @property genre Genre string
 * @property releaseDate Full release date (ISO format: "2014-09-21")
 * @property youtubeTrailer YouTube trailer URL/ID
 * @property episodeRunTime Average episode runtime in minutes
 * @property lastModified Unix epoch timestamp of last modification (for "recently updated" sorting)
 * @property tmdbId TMDB TV show ID if available from provider (some panels scrape this)
 */
data class XtreamSeriesItem(
    val id: Int,
    val name: String,
    val cover: String? = null,
    val backdrop: String? = null,
    val categoryId: String? = null,
    val year: String? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val youtubeTrailer: String? = null,
    val episodeRunTime: String? = null,
    val lastModified: Long? = null,
    /**
     * TMDB TV show ID from provider (Gold Decision Dec 2025).
     *
     * Some Xtream providers scrape TMDB and include the ID.
     * Maps to ExternalIds.tmdb = TmdbRef(TV, tmdbId).
     * Episodes inherit this from the parent series.
     */
    val tmdbId: Int? = null,
    /**
     * Adult content flag from provider.
     *
     * Xtream provides this from API (is_adult field as "1" or "0" string).
     */
    val isAdult: Boolean = false,
) {
    /**
     * Validates if the series ID is valid. Some Xtream panels return negative IDs - we treat them
     * as valid but log a warning.
     */
    val isValidId: Boolean
        get() = id != 0

    /**
     * Compute fingerprint hash for incremental sync change detection.
     *
     * **Fields included:**
     * - id: Primary key
     * - name: Title changes
     * - lastModified: Modification timestamp
     * - categoryId: Category reassignment
     * - cover: Artwork changes
     * - rating: Metadata updates
     *
     * Note: Does NOT include episode count (not available in list response).
     * Episode count changes are detected when episodes are synced separately.
     *
     * **Design:** docs/v2/INCREMENTAL_SYNC_DESIGN.md Section 7
     */
    fun fingerprint(): Int = Objects.hash(
        id,
        name,
        lastModified,
        categoryId,
        cover,
        rating,
    )
}
