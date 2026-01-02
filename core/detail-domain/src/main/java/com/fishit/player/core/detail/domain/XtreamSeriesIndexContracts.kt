package com.fishit.player.core.detail.domain

import kotlinx.coroutines.flow.Flow

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
    val thumbUrl: String?,
    val durationSecs: Int?,
    val plotBrief: String?,
    val rating: Double?,
    val airDate: String?,
    val playbackHintsJson: String?,
    val lastUpdatedMs: Long,
    val playbackHintsUpdatedMs: Long,
) {
    val isIndexStale: Boolean
        get() = System.currentTimeMillis() - lastUpdatedMs > INDEX_TTL_MS

    val arePlaybackHintsStale: Boolean
        get() = playbackHintsUpdatedMs == 0L ||
                System.currentTimeMillis() - playbackHintsUpdatedMs > PLAYBACK_HINTS_TTL_MS

    val isPlaybackReady: Boolean
        get() = !playbackHintsJson.isNullOrEmpty() && !arePlaybackHintsStale

    companion object {
        const val INDEX_TTL_MS = 7 * 24 * 60 * 60 * 1000L
        const val PLAYBACK_HINTS_TTL_MS = 30 * 24 * 60 * 60 * 1000L
    }
}

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

    suspend fun upsertSeasons(seriesId: Int, seasons: List<SeasonIndexItem>)

    suspend fun deleteSeasons(seriesId: Int)

    fun observeEpisodes(
        seriesId: Int,
        seasonNumber: Int,
        page: Int = 0,
        pageSize: Int = DEFAULT_EPISODE_PAGE_SIZE,
    ): Flow<List<EpisodeIndexItem>>

    suspend fun getEpisodeCount(seriesId: Int, seasonNumber: Int): Int

    suspend fun getEpisodeBySourceKey(sourceKey: String): EpisodeIndexItem?

    suspend fun hasFreshEpisodes(seriesId: Int, seasonNumber: Int): Boolean

    suspend fun upsertEpisodes(episodes: List<EpisodeIndexItem>)

    suspend fun updatePlaybackHints(sourceKey: String, hintsJson: String?)

    suspend fun getPlaybackHints(sourceKey: String): EpisodePlaybackHints?

    suspend fun isPlaybackReady(sourceKey: String): Boolean

    suspend fun deleteEpisodes(seriesId: Int, seasonNumber: Int)

    suspend fun deleteAllEpisodesForSeries(seriesId: Int)

    suspend fun invalidateStaleEntries(): Int

    suspend fun invalidateAll()

    companion object {
        const val DEFAULT_EPISODE_PAGE_SIZE = 30
    }
}

interface XtreamSeriesIndexRefresher {
    suspend fun refreshSeasons(seriesId: Int): Boolean

    suspend fun refreshEpisodes(seriesId: Int, seasonNumber: Int): Boolean

    suspend fun refreshEpisodePlaybackHints(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        sourceKey: String,
    ): EpisodePlaybackHints?
}
