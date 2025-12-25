package com.fishit.player.feature.detail.series

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.core.model.repository.CanonicalResumeInfo

/**
 * @deprecated This file is superseded by the unified DetailScreen.
 * Series functionality is now handled in:
 * - [UnifiedDetailState] (in UnifiedDetailViewModel.kt)
 * - [DetailEpisodeItem] (in ui/helper/DetailMediaTypeHelpers.kt)
 *
 * The unified DetailScreen adapts to all media types (MOVIE, SERIES, LIVE, etc.)
 * based on the mediaType field.
 *
 * This file is kept for reference only. DO NOT USE in new code.
 */

@Deprecated("Use UnifiedDetailState in UnifiedDetailViewModel.kt instead")
data class SeriesDetailState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val series: SeriesInfo? = null,
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val episodeResumes: Map<String, CanonicalResumeInfo> = emptyMap(),
) {
    val hasMultipleSeasons: Boolean
        get() = seasons.size > 1

    val displayedEpisodes: List<EpisodeItem>
        get() = if (selectedSeason != null) {
            episodes.filter { it.season == selectedSeason }
        } else {
            episodes
        }
}

/**
 * Series information from canonical media.
 */
data class SeriesInfo(
    val canonicalId: CanonicalMediaId,
    val title: String,
    val year: Int?,
    val poster: ImageRef?,
    val backdrop: ImageRef?,
    val plot: String?,
    val genres: String?,
    val director: String?,
    val cast: String?,
    val rating: Double?,
    val tmdbId: Int?,
)

/**
 * Episode item for display in episode list.
 */
data class EpisodeItem(
    val canonicalId: CanonicalMediaId,
    val sourceId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val thumbnail: ImageRef?,
    val plot: String?,
    val durationMs: Long?,
    val sources: List<MediaSourceRef>,
) {
    val displayTitle: String
        get() = "S${season}E${episode} - $title"

    val durationMinutes: Int?
        get() = durationMs?.let { (it / 60_000).toInt() }
}

/**
 * Events from Series Detail screen.
 */
sealed class SeriesDetailEvent {
    data class PlayEpisode(
        val episode: EpisodeItem,
        val source: MediaSourceRef,
        val resumePositionMs: Long = 0,
    ) : SeriesDetailEvent()

    data class ShowEpisodeDetails(
        val episode: EpisodeItem,
    ) : SeriesDetailEvent()

    data class ShowError(
        val message: String,
    ) : SeriesDetailEvent()
}
