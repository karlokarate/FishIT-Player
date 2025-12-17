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
)
