package com.fishit.player.pipeline.xtream.model

/**
 * Represents a single episode within an Xtream series.
 *
 * This is a pipeline-internal DTO that will be converted to RawMediaMetadata via
 * toRawMediaMetadata() extension function.
 *
 * @property id Unique identifier for this episode
 * @property seriesId The series this episode belongs to
 * @property seriesName Name of the parent series (for context in RawMediaMetadata)
 * @property seasonNumber Season number
 * @property episodeNumber Episode number within the season
 * @property title Display title of the episode
 * @property containerExtension File container extension (e.g., "mp4", "mkv")
 * @property plot Episode plot/description
 * @property duration Duration string (e.g., "45:00")
 * @property releaseDate Release date string
 * @property rating Rating (0-10 scale)
 * @property thumbnail URL to the episode thumbnail
 * @property added Unix epoch timestamp when episode was added
 * @property seriesTmdbId TMDB TV show ID inherited from parent series (Gold Decision Dec 2025)
 */
data class XtreamEpisode(
    val id: Int,
    val seriesId: Int,
    val seriesName: String? = null,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val containerExtension: String? = null,
    val plot: String? = null,
    val duration: String? = null,
    /**
     * Duration in seconds from API (more accurate than parsing duration string).
     *
     * When available, this should be preferred over parsing the "duration" string (HH:MM:SS).
     * API field: info.duration_secs
     */
    val durationSecs: Int? = null,
    val releaseDate: String? = null,
    val rating: Double? = null,
    val thumbnail: String? = null,
    val added: Long? = null,
    /**
     * Video bitrate in kbps from API ffprobe data.
     *
     * Useful for quality selection in player. API field: info.bitrate
     */
    val bitrate: Int? = null,
    /**
     * TMDB TV show ID inherited from parent series (Gold Decision Dec 2025).
     *
     * Episodes use the series TMDB ID (TV type) combined with season/episode numbers.
     * Maps to ExternalIds.tmdb = TmdbRef(TV, seriesTmdbId).
     * TMDB API: GET /tv/{seriesTmdbId}/season/{seasonNumber}/episode/{episodeNumber}
     */
    val seriesTmdbId: Int? = null,
    /**
     * Episode-specific TMDB ID from API info block.
     *
     * This is the episode's own TMDB ID (different from seriesTmdbId).
     * Available when Xtream panel has scraped TMDB episode metadata.
     */
    val episodeTmdbId: Int? = null,
    /**
     * Video codec name (e.g., "h264", "hevc").
     *
     * Extracted from API info.video object when available.
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
     * Extracted from API info.audio object when available.
     */
    val audioCodec: String? = null,
    /**
     * Audio channel count (e.g., 2 for stereo, 6 for 5.1).
     */
    val audioChannels: Int? = null,
)
