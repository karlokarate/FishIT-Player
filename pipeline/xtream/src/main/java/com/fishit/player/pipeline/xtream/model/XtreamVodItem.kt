package com.fishit.player.pipeline.xtream.model

import java.util.Objects

/**
 * Represents a Video-on-Demand item in the Xtream pipeline.
 *
 * Contains all relevant fields from `get_vod_streams` API response.
 *
 * @property id Unique identifier for this VOD item (stream_id from API)
 * @property name Display name of the VOD content
 * @property streamIcon URL to the stream icon/poster (usually TMDB image URL)
 * @property categoryId Category identifier this VOD belongs to
 * @property containerExtension File container extension (e.g., "mp4", "mkv")
 * @property added Unix epoch timestamp when item was added to provider catalog
 * @property rating TMDB rating (0.0-10.0 scale)
 * @property rating5Based Rating on 5-star scale
 * @property tmdbId TMDB movie ID if available from provider (some panels scrape this)
 */
data class XtreamVodItem(
    val id: Int,
    val name: String,
    val streamIcon: String? = null,
    val categoryId: String? = null,
    val containerExtension: String? = null,
    val streamType: String? = null,
    val added: Long? = null,
    val rating: Double? = null,
    val rating5Based: Double? = null,
    /**
     * TMDB movie ID from provider (Gold Decision Dec 2025).
     *
     * Some Xtream providers scrape TMDB and include the ID.
     * Maps to ExternalIds.tmdb = TmdbRef(MOVIE, tmdbId).
     */
    val tmdbId: Int? = null,
    // Quick info fields (some panels include these in list response)
    val year: String? = null,
    val genre: String? = null,
    val plot: String? = null,
    val duration: String? = null,
    /**
     * Adult content flag from provider.
     *
     * Xtream provides this from API (is_adult field as "1" or "0" string).
     */
    val isAdult: Boolean = false,
    /**
     * Video codec name (e.g., "h264", "hevc").
     *
     * Extracted from VOD info.video object when detail API is fetched.
     */
    val videoCodec: String? = null,
    /**
     * Video width in pixels.
     */
    val videoWidth: Int? = null,
    /**
     * Video height in pixels.
     */
    val videoHeight: Int? = null,
    /**
     * Audio codec name (e.g., "aac", "ac3").
     *
     * Extracted from VOD info.audio object when detail API is fetched.
     */
    val audioCodec: String? = null,
    /**
     * Audio channel count (e.g., 2 for stereo, 6 for 5.1).
     */
    val audioChannels: Int? = null,
) {
    /**
     * Compute fingerprint hash for incremental sync change detection.
     *
     * **Fields included:**
     * - id: Primary key (shouldn't change)
     * - name: Title changes
     * - added: Timestamp (usually stable)
     * - categoryId: Category reassignment
     * - containerExtension: Format changes
     * - streamIcon: Artwork changes
     * - rating: Metadata updates
     *
     * **Design:** docs/v2/INCREMENTAL_SYNC_DESIGN.md Section 7
     */
    fun fingerprint(): Int =
        Objects.hash(
            id,
            name,
            added,
            categoryId,
            containerExtension,
            streamIcon,
            rating,
        )
}
