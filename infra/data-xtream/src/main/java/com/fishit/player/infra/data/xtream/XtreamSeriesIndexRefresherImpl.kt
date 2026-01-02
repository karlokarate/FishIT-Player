package com.fishit.player.infra.data.xtream

import com.fishit.player.core.detail.domain.EpisodePlaybackHints
import com.fishit.player.core.detail.domain.SeasonIndexItem
import com.fishit.player.core.detail.domain.EpisodeIndexItem
import com.fishit.player.core.detail.domain.XtreamSeriesIndexRefresher
import com.fishit.player.core.detail.domain.XtreamSeriesIndexRepository
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class XtreamSeriesIndexRefresherImpl
@Inject
constructor(
    private val repository: XtreamSeriesIndexRepository,
    private val apiClient: XtreamApiClient,
) : XtreamSeriesIndexRefresher {

    companion object {
        private const val TAG = "XtreamSeriesIndexRefresher"
        private const val API_TIMEOUT_MS = 10_000L
    }

    override suspend fun refreshSeasons(seriesId: Int): Boolean {
        UnifiedLog.d(TAG) { "refreshSeasons seriesId=$seriesId" }

        val seriesInfo = withTimeout(API_TIMEOUT_MS) { apiClient.getSeriesInfo(seriesId) }
        if (seriesInfo == null) {
            UnifiedLog.w(TAG) { "refreshSeasons: API returned null seriesId=$seriesId" }
            return false
        }

        val seasons =
            seriesInfo.seasons
                ?.mapNotNull { season ->
                    val seasonNum = season.seasonNumber ?: return@mapNotNull null
                    SeasonIndexItem(
                        seriesId = seriesId,
                        seasonNumber = seasonNum,
                        episodeCount = season.episodeCount,
                        name = season.name,
                        coverUrl = season.coverBig ?: season.cover,
                        airDate = season.airDate,
                        lastUpdatedMs = System.currentTimeMillis(),
                    )
                }
                .orEmpty()

        if (seasons.isEmpty()) {
            UnifiedLog.w(TAG) { "refreshSeasons: no seasons found seriesId=$seriesId" }
            return false
        }

        repository.upsertSeasons(seriesId, seasons)
        UnifiedLog.d(TAG) { "refreshSeasons: persisted ${seasons.size} seasons seriesId=$seriesId" }
        return true
    }

    override suspend fun refreshEpisodes(seriesId: Int, seasonNumber: Int): Boolean {
        UnifiedLog.d(TAG) { "refreshEpisodes seriesId=$seriesId season=$seasonNumber" }

        val seriesInfo = withTimeout(API_TIMEOUT_MS) { apiClient.getSeriesInfo(seriesId) }
        if (seriesInfo == null) {
            UnifiedLog.w(TAG) { "refreshEpisodes: API returned null seriesId=$seriesId" }
            return false
        }

        val seasonKey = seasonNumber.toString()
        val episodesList = seriesInfo.episodes?.get(seasonKey).orEmpty()

        val now = System.currentTimeMillis()
        val episodes =
            episodesList.mapNotNull { ep ->
                val episodeNum = ep.episodeNum ?: return@mapNotNull null
                val sourceKey = "xtream:episode:${seriesId}:${seasonNumber}:${episodeNum}"
                val resolvedId = ep.resolvedEpisodeId

                val hintsJson =
                    resolvedId?.let { streamId ->
                        buildPlaybackHintsJson(
                            streamId = streamId,
                            containerExtension = ep.containerExtension,
                            directUrl = ep.directSource,
                        )
                    }

                EpisodeIndexItem(
                    seriesId = seriesId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNum,
                    sourceKey = sourceKey,
                    episodeId = resolvedId,
                    title = ep.title ?: ep.info?.name,
                    thumbUrl = ep.info?.movieImage ?: ep.info?.posterPath ?: ep.info?.stillPath,
                    durationSecs = ep.info?.durationSecs,
                    plotBrief = ep.info?.plot?.take(200),
                    rating = ep.info?.rating?.toDoubleOrNull(),
                    airDate = ep.info?.releaseDate ?: ep.info?.airDate,
                    playbackHintsJson = hintsJson,
                    lastUpdatedMs = now,
                    playbackHintsUpdatedMs = if (hintsJson != null) now else 0L,
                )
            }

        if (episodes.isEmpty()) {
            UnifiedLog.w(TAG) { "refreshEpisodes: no episodes found seriesId=$seriesId season=$seasonNumber" }
            return false
        }

        repository.upsertEpisodes(episodes)
        UnifiedLog.d(TAG) { "refreshEpisodes: persisted ${episodes.size} episodes seriesId=$seriesId season=$seasonNumber" }
        return true
    }

    override suspend fun refreshEpisodePlaybackHints(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        sourceKey: String,
    ): EpisodePlaybackHints? {
        UnifiedLog.d(TAG) {
            "refreshEpisodePlaybackHints sourceKey=$sourceKey (series=$seriesId s=$seasonNumber e=$episodeNumber)"
        }

        val seriesInfo = withTimeout(API_TIMEOUT_MS) { apiClient.getSeriesInfo(seriesId) }
            ?: return null

        val seasonKey = seasonNumber.toString()
        val episodesList = seriesInfo.episodes?.get(seasonKey).orEmpty()
        val episode = episodesList.find { it.episodeNum == episodeNumber } ?: return null

        val resolvedId = episode.resolvedEpisodeId ?: return null

        val hintsJson =
            buildPlaybackHintsJson(
                streamId = resolvedId,
                containerExtension = episode.containerExtension,
                directUrl = episode.directSource,
            )

        repository.updatePlaybackHints(sourceKey, hintsJson)

        return EpisodePlaybackHints(
            episodeId = resolvedId,
            streamId = resolvedId,
            containerExtension = episode.containerExtension,
            directUrl = episode.directSource,
        )
    }

    private fun buildPlaybackHintsJson(
        streamId: Int?,
        containerExtension: String?,
        directUrl: String?,
    ): String? {
        if (streamId == null) return null
        return Json.encodeToString(
            mapOf(
                "streamId" to streamId.toString(),
                "containerExtension" to (containerExtension ?: ""),
                "directUrl" to (directUrl ?: ""),
            ),
        )
    }
}
