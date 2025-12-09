package com.fishit.player.pipeline.xtream.model

/**
 * Represents a single episode within an Xtream series.
 *
 * This is a pipeline-internal DTO that will be converted to RawMediaMetadata
 * via toRawMediaMetadata() extension function.
 *
 * @property id Unique identifier for this episode
 * @property seriesId The series this episode belongs to
 * @property seasonNumber Season number
 * @property episodeNumber Episode number within the season
 * @property title Display title of the episode
 * @property containerExtension File container extension (e.g., "mp4", "mkv")
 * @property plot Episode plot/description
 * @property duration Duration string (e.g., "45:00")
 * @property releaseDate Release date string
 * @property rating Rating (0-10 scale)
 * @property thumbnail URL to the episode thumbnail
 */
data class XtreamEpisode(
    val id: Int,
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val containerExtension: String? = null,
    val plot: String? = null,
    val duration: String? = null,
    val releaseDate: String? = null,
    val rating: Double? = null,
    val thumbnail: String? = null,
)
