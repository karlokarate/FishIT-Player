package com.fishit.player.pipeline.xtream.model

/**
 * Represents a single episode within a TV series from an Xtream provider.
 *
 * @property id Unique identifier for the episode
 * @property seriesId ID of the parent series
 * @property seasonNumber Season number (1-based)
 * @property episodeNumber Episode number within the season (1-based)
 * @property title Episode title
 * @property streamUrl Direct URL to the episode video stream
 * @property posterUrl Optional episode thumbnail/poster URL
 * @property description Optional episode description
 * @property airDate Optional original air date (ISO 8601 format)
 * @property durationSeconds Optional duration in seconds
 */
data class XtreamEpisode(
    val id: Long,
    val seriesId: Long,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val streamUrl: String,
    val posterUrl: String? = null,
    val description: String? = null,
    val airDate: String? = null,
    val durationSeconds: Int? = null,
)
