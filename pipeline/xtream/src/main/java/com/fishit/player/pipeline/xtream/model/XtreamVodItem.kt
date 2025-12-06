package com.fishit.player.pipeline.xtream.model

/**
 * Represents a Video on Demand item from an Xtream provider.
 *
 * @property id Unique identifier for the VOD item
 * @property name Display title of the VOD content
 * @property streamUrl Direct URL to the video stream
 * @property posterUrl URL to poster/thumbnail image
 * @property description Optional description or synopsis
 * @property rating Optional content rating (e.g., "PG-13", "TV-MA")
 * @property year Optional release year
 * @property durationSeconds Optional duration in seconds
 * @property categoryId Optional category/genre ID
 */
data class XtreamVodItem(
    val id: Long,
    val name: String,
    val streamUrl: String,
    val posterUrl: String? = null,
    val description: String? = null,
    val rating: String? = null,
    val year: Int? = null,
    val durationSeconds: Int? = null,
    val categoryId: Long? = null,
)
