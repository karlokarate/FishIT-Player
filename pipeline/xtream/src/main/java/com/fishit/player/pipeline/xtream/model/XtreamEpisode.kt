package com.fishit.player.pipeline.xtream.model

/**
 * Represents a single episode within an Xtream series.
 *
 * This is a v2 stub model - production implementation will be expanded in later phases.
 *
 * @property id Unique identifier for this episode
 * @property seriesId The series this episode belongs to
 * @property seasonNumber Season number
 * @property episodeNumber Episode number within the season
 * @property title Display title of the episode
 * @property containerExtension File container extension (e.g., "mp4", "mkv")
 * @property thumbnail URL to the episode thumbnail
 */
data class XtreamEpisode(
    val id: Int,
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val containerExtension: String? = null,
    val thumbnail: String? = null,
)
