package com.fishit.player.core.detail.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

data class SeasonIndexItem(
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeCount: Int?,
    val name: String?,
    val coverUrl: String?,
    val airDate: String?,
    val lastUpdatedMs: Long,
) {
    val isStale: Boolean
        get() = System.currentTimeMillis() - lastUpdatedMs > SEASON_INDEX_TTL_MS

    companion object {
        const val SEASON_INDEX_TTL_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

data class EpisodeIndexItem(
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val sourceKey: String,
    val episodeId: Int?,
    val title: String?,
    // --- Images ---
    /** Primary thumbnail (API: movie_image → poster_path → still_path) */
    val thumbUrl: String?,
    /** Cover/backdrop image (API: info.cover) */
    val coverUrl: String? = null,
    /** Alternative thumbnail (API: info.thumbnail, info.img) */
    val thumbnailUrl: String? = null,
    // --- Core metadata ---
    val durationSecs: Int?,
    /** Duration as display string from API (e.g. "45:00") */
    val durationString: String? = null,
    /** Full plot — unlimited, never truncated */
    val plot: String?,
    val rating: Double?,
    val airDate: String?,
    // --- External IDs ---
    /** Episode-level TMDB ID (different from series TMDB ID) */
    val tmdbId: Int? = null,
    // --- Xtream-specific ---
    /** Raw `added` timestamp from API (epoch string) */
    val addedTimestamp: String? = null,
    /** Custom SID from API (`custom_sid`) */
    val customSid: String? = null,
    // --- Technical stream metadata ---
    /** Video codec (e.g. "h264", "hevc") from ffprobe info */
    val videoCodec: String? = null,
    /** Video width in pixels */
    val videoWidth: Int? = null,
    /** Video height in pixels (e.g. 1080, 2160) */
    val videoHeight: Int? = null,
    /** Video aspect ratio (e.g. "16:9") */
    val videoAspectRatio: String? = null,
    /** Audio codec (e.g. "aac", "ac3") from ffprobe info */
    val audioCodec: String? = null,
    /** Number of audio channels (e.g. 2, 6) */
    val audioChannels: Int? = null,
    /** Audio language tag */
    val audioLanguage: String? = null,
    /** Stream bitrate in kbps */
    val bitrateKbps: Int? = null,
    // --- Playback ---
    val playbackHintsJson: String?,
    val lastUpdatedMs: Long,
    val playbackHintsUpdatedMs: Long,
) {
    val isIndexStale: Boolean
        get() = System.currentTimeMillis() - lastUpdatedMs > INDEX_TTL_MS

    val arePlaybackHintsStale: Boolean
        get() =
            playbackHintsUpdatedMs == 0L ||
                System.currentTimeMillis() - playbackHintsUpdatedMs > PLAYBACK_HINTS_TTL_MS

    val isPlaybackReady: Boolean
        get() = !playbackHintsJson.isNullOrEmpty() && !arePlaybackHintsStale

    companion object {
        const val INDEX_TTL_MS = 7 * 24 * 60 * 60 * 1000L
        const val PLAYBACK_HINTS_TTL_MS = 30 * 24 * 60 * 60 * 1000L
    }
}

@Serializable
data class EpisodePlaybackHints(
    val episodeId: Int?,
    val streamId: Int?,
    val containerExtension: String?,
    val directUrl: String? = null,
)

interface XtreamSeriesIndexRepository {
    fun observeSeasons(seriesId: Int): Flow<List<SeasonIndexItem>>

    suspend fun getSeasons(seriesId: Int): List<SeasonIndexItem>

    suspend fun hasFreshSeasons(seriesId: Int): Boolean

    suspend fun upsertSeasons(
        seriesId: Int,
        seasons: List<SeasonIndexItem>,
    )

    suspend fun deleteSeasons(seriesId: Int)

    fun observeEpisodes(
        seriesId: Int,
        seasonNumber: Int,
        page: Int = 0,
        pageSize: Int = DEFAULT_EPISODE_PAGE_SIZE,
    ): Flow<List<EpisodeIndexItem>>

    suspend fun getEpisodeCount(
        seriesId: Int,
        seasonNumber: Int,
    ): Int

    suspend fun getEpisodeBySourceKey(sourceKey: String): EpisodeIndexItem?

    suspend fun hasFreshEpisodes(
        seriesId: Int,
        seasonNumber: Int,
    ): Boolean

    suspend fun upsertEpisodes(episodes: List<EpisodeIndexItem>)

    suspend fun updatePlaybackHints(
        sourceKey: String,
        hintsJson: String?,
    )

    suspend fun getPlaybackHints(sourceKey: String): EpisodePlaybackHints?

    suspend fun isPlaybackReady(sourceKey: String): Boolean

    suspend fun deleteEpisodes(
        seriesId: Int,
        seasonNumber: Int,
    )

    suspend fun deleteAllEpisodesForSeries(seriesId: Int)

    suspend fun invalidateStaleEntries(): Int

    suspend fun invalidateAll()

    companion object {
        const val DEFAULT_EPISODE_PAGE_SIZE = 30
    }
}

// =============================================================================
// PLATIN PHASE 3 COMPLETE: XtreamSeriesIndexRefresher REMOVED (2025-01-31)
// =============================================================================
//
// The deprecated XtreamSeriesIndexRefresher interface has been completely removed.
// All callers have been migrated to use UnifiedDetailLoader directly:
//
// Migration completed for:
// - LoadSeriesSeasonsUseCase → unifiedDetailLoader.loadSeriesDetailBySeriesId()
// - LoadSeasonEpisodesUseCase → unifiedDetailLoader.loadSeriesDetailBySeriesId()
// - EnsureEpisodePlaybackReadyUseCase → unifiedDetailLoader.loadSeriesDetailBySeriesId()
//
// Benefits achieved:
// - ONE API call instead of multiple separate calls
// - Atomic save of metadata + seasons + episodes
// - Proper layer boundaries (detail API in infra:data-detail)
// - Reduced code complexity and maintenance burden
//
// See: UnifiedDetailLoader, XtreamDetailSync
// =============================================================================
