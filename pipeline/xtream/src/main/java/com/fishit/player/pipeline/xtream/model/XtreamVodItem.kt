package com.fishit.player.pipeline.xtream.model

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
)
