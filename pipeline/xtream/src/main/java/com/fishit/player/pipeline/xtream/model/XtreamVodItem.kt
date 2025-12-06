package com.fishit.player.pipeline.xtream.model

/**
 * Represents a Video-on-Demand item in the Xtream pipeline.
 *
 * This is a v2 stub model - production implementation will be expanded in later phases.
 *
 * @property id Unique identifier for this VOD item
 * @property name Display name of the VOD content
 * @property streamIcon URL to the stream icon/poster
 * @property categoryId Category identifier this VOD belongs to
 * @property containerExtension File container extension (e.g., "mp4", "mkv")
 */
data class XtreamVodItem(
    val id: Int,
    val name: String,
    val streamIcon: String? = null,
    val categoryId: String? = null,
    val containerExtension: String? = null,
)
