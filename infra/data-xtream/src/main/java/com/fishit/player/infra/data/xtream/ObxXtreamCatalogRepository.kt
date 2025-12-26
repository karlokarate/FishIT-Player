package com.fishit.player.infra.data.xtream

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.ObxEpisode
import com.fishit.player.core.persistence.obx.ObxEpisode_
import com.fishit.player.core.persistence.obx.ObxSeries
import com.fishit.player.core.persistence.obx.ObxSeries_
import com.fishit.player.core.persistence.obx.ObxVod
import com.fishit.player.core.persistence.obx.ObxVod_
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * ObjectBox-backed implementation of [XtreamCatalogRepository].
 *
 * **Architecture Compliance:**
 * - Works only with RawMediaMetadata (no pipeline DTOs)
 * - Uses ObjectBox entities internally (ObxVod, ObxSeries, ObxEpisode)
 * - Provides reactive Flows for UI consumption
 *
 * **Layer Boundaries:**
 * - Transport → Pipeline → Data → Domain → UI
 * - This repository sits in Data layer
 * - Consumes RawMediaMetadata from Pipeline (via CatalogSync)
 * - Serves RawMediaMetadata to Domain/UI
 *
 * **Source ID Formats:**
 * - VOD: "xtream:vod:{vodId}"
 * - Series: "xtream:series:{seriesId}"
 * - Episode: "xtream:episode:{seriesId}:{seasonNum}:{episodeNum}"
 */
@Singleton
class ObxXtreamCatalogRepository @Inject constructor(private val boxStore: BoxStore) :
        XtreamCatalogRepository {

    companion object {
        private const val TAG = "ObxXtreamCatalogRepo"
    }

    private val vodBox by lazy { boxStore.boxFor<ObxVod>() }
    private val seriesBox by lazy { boxStore.boxFor<ObxSeries>() }
    private val episodeBox by lazy { boxStore.boxFor<ObxEpisode>() }

    override fun observeVod(categoryId: String?): Flow<List<RawMediaMetadata>> {
        val query =
                if (categoryId != null) {
                    vodBox.query(ObxVod_.categoryId.equal(categoryId)).build()
                } else {
                    vodBox.query().order(ObxVod_.nameLower).build()
                }
        return query.asFlow().map { entities -> entities.map { it.toRawMediaMetadata() } }
    }

    override fun observeSeries(categoryId: String?): Flow<List<RawMediaMetadata>> {
        val query =
                if (categoryId != null) {
                    seriesBox.query(ObxSeries_.categoryId.equal(categoryId)).build()
                } else {
                    seriesBox.query().order(ObxSeries_.nameLower).build()
                }
        return query.asFlow().map { entities -> entities.map { it.toRawMediaMetadata() } }
    }

    override fun observeEpisodes(
            seriesId: String,
            seasonNumber: Int?
    ): Flow<List<RawMediaMetadata>> {
        val seriesIdInt =
                seriesId.toIntOrNull() ?: return kotlinx.coroutines.flow.flowOf(emptyList())

        val query =
                if (seasonNumber != null) {
                    episodeBox
                            .query(
                                    ObxEpisode_.seriesId
                                            .equal(seriesIdInt.toLong())
                                            .and(ObxEpisode_.season.equal(seasonNumber.toLong()))
                            )
                            .order(ObxEpisode_.episodeNum)
                            .build()
                } else {
                    episodeBox
                            .query(ObxEpisode_.seriesId.equal(seriesIdInt.toLong()))
                            .order(ObxEpisode_.season)
                            .order(ObxEpisode_.episodeNum)
                            .build()
                }
        return query.asFlow().map { entities -> entities.map { it.toRawMediaMetadata() } }
    }

    override suspend fun getAll(
            mediaType: MediaType?,
            limit: Int,
            offset: Int
    ): List<RawMediaMetadata> =
            withContext(Dispatchers.IO) {
                when (mediaType) {
                    MediaType.MOVIE ->
                            vodBox.query()
                                    .order(ObxVod_.nameLower)
                                    .build()
                                    .find(offset.toLong(), limit.toLong())
                                    .map { it.toRawMediaMetadata() }
                    MediaType.SERIES ->
                            seriesBox
                                    .query()
                                    .order(ObxSeries_.nameLower)
                                    .build()
                                    .find(offset.toLong(), limit.toLong())
                                    .map { it.toRawMediaMetadata() }
                    MediaType.SERIES_EPISODE ->
                            episodeBox
                                    .query()
                                    .order(ObxEpisode_.seriesId)
                                    .build()
                                    .find(offset.toLong(), limit.toLong())
                                    .map { it.toRawMediaMetadata() }
                    else -> {
                        val vods = vodBox.all.map { it.toRawMediaMetadata() }
                        val series = seriesBox.all.map { it.toRawMediaMetadata() }
                        (vods + series).drop(offset).take(limit)
                    }
                }
            }

    override suspend fun getBySourceId(sourceId: String): RawMediaMetadata? =
            withContext(Dispatchers.IO) {
                val parts = sourceId.split(":")
                if (parts.size < 3 || parts[0] != "xtream") return@withContext null

                when (parts[1]) {
                    "vod" -> {
                        val vodId = parts[2].toIntOrNull() ?: return@withContext null
                        vodBox.query(ObxVod_.vodId.equal(vodId.toLong()))
                                .build()
                                .findFirst()
                                ?.toRawMediaMetadata()
                    }
                    "series" -> {
                        val seriesId = parts[2].toIntOrNull() ?: return@withContext null
                        seriesBox
                                .query(ObxSeries_.seriesId.equal(seriesId.toLong()))
                                .build()
                                .findFirst()
                                ?.toRawMediaMetadata()
                    }
                    "episode" -> {
                        if (parts.size < 5) return@withContext null
                        val seriesId = parts[2].toIntOrNull() ?: return@withContext null
                        val season = parts[3].toIntOrNull() ?: return@withContext null
                        val episodeNum = parts[4].toIntOrNull() ?: return@withContext null
                        episodeBox
                                .query(
                                        ObxEpisode_.seriesId
                                                .equal(seriesId.toLong())
                                                .and(ObxEpisode_.season.equal(season.toLong()))
                                                .and(
                                                        ObxEpisode_.episodeNum.equal(
                                                                episodeNum.toLong()
                                                        )
                                                )
                                )
                                .build()
                                .findFirst()
                                ?.toRawMediaMetadata()
                    }
                    else -> null
                }
            }

    override suspend fun search(query: String, limit: Int): List<RawMediaMetadata> =
            withContext(Dispatchers.IO) {
                val lowerQuery = query.lowercase()
                val vods =
                        vodBox.query(ObxVod_.nameLower.contains(lowerQuery))
                                .build()
                                .find(0, limit.toLong())
                                .map { it.toRawMediaMetadata() }

                val series =
                        seriesBox
                                .query(ObxSeries_.nameLower.contains(lowerQuery))
                                .build()
                                .find(0, limit.toLong())
                                .map { it.toRawMediaMetadata() }

                (vods + series).take(limit)
            }

    override suspend fun upsertAll(items: List<RawMediaMetadata>) =
            withContext(Dispatchers.IO) {
                UnifiedLog.d(TAG, "upsertAll(${items.size} items)")

                val vods = mutableListOf<ObxVod>()
                val series = mutableListOf<ObxSeries>()
                val episodes = mutableListOf<ObxEpisode>()

                items.forEach { item ->
                    when {
                        item.sourceId.startsWith("xtream:vod:") -> {
                            item.toObxVod()?.let { vods.add(it) }
                        }
                        item.sourceId.startsWith("xtream:series:") -> {
                            item.toObxSeries()?.let { series.add(it) }
                        }
                        item.sourceId.startsWith("xtream:episode:") -> {
                            item.toObxEpisode()?.let { episodes.add(it) }
                        }
                    }
                }

                if (vods.isNotEmpty()) upsertVods(vods)
                if (series.isNotEmpty()) upsertSeriesEntities(series)
                if (episodes.isNotEmpty()) upsertEpisodes(episodes)
            }

    override suspend fun upsert(item: RawMediaMetadata) {
        upsertAll(listOf(item))
    }

    override suspend fun count(mediaType: MediaType?): Long =
            withContext(Dispatchers.IO) {
                when (mediaType) {
                    MediaType.MOVIE -> vodBox.count()
                    MediaType.SERIES -> seriesBox.count()
                    MediaType.SERIES_EPISODE -> episodeBox.count()
                    else -> vodBox.count() + seriesBox.count() + episodeBox.count()
                }
            }

    override suspend fun deleteAll() =
            withContext(Dispatchers.IO) {
                UnifiedLog.d(TAG, "deleteAll()")
                vodBox.removeAll()
                seriesBox.removeAll()
                episodeBox.removeAll()
            }

    // ========================================================================
    // Private Upsert Helpers
    // ========================================================================

    private fun upsertVods(vods: List<ObxVod>) {
        val toUpsert =
                vods.map { vod ->
                    val existing =
                            vodBox.query(ObxVod_.vodId.equal(vod.vodId.toLong()))
                                    .build()
                                    .findFirst()
                    if (existing != null) vod.copy(id = existing.id) else vod
                }
        vodBox.put(toUpsert)
    }

    private fun upsertSeriesEntities(series: List<ObxSeries>) {
        val toUpsert =
                series.map { s ->
                    val existing =
                            seriesBox
                                    .query(ObxSeries_.seriesId.equal(s.seriesId.toLong()))
                                    .build()
                                    .findFirst()
                    if (existing != null) s.copy(id = existing.id) else s
                }
        seriesBox.put(toUpsert)
    }

    private fun upsertEpisodes(episodes: List<ObxEpisode>) {
        val toUpsert =
                episodes.map { ep ->
                    val existing =
                            episodeBox
                                    .query(
                                            ObxEpisode_.seriesId
                                                    .equal(ep.seriesId.toLong())
                                                    .and(
                                                            ObxEpisode_.season.equal(
                                                                    ep.season.toLong()
                                                            )
                                                    )
                                                    .and(
                                                            ObxEpisode_.episodeNum.equal(
                                                                    ep.episodeNum.toLong()
                                                            )
                                                    )
                                    )
                                    .build()
                                    .findFirst()
                    if (existing != null) ep.copy(id = existing.id) else ep
                }
        episodeBox.put(toUpsert)
    }

    // ========================================================================
    // Mapping: ObxVod/ObxSeries/ObxEpisode ↔ RawMediaMetadata
    // ========================================================================

    private fun ObxVod.toRawMediaMetadata(): RawMediaMetadata =
            RawMediaMetadata(
                    originalTitle = name,
                    mediaType = MediaType.MOVIE,
                    year = year,
                    durationMs = durationSecs?.let { it * 1000L },
                    sourceType = SourceType.XTREAM,
                    sourceLabel = "Xtream VOD",
                    sourceId = "xtream:vod:$vodId",
                    poster = poster?.let { ImageRef.Http(it) },
                    backdrop = null,
                    externalIds =
                            tmdbId?.toIntOrNull()?.let { id ->
                                ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, id))
                            }
                                    ?: ExternalIds()
            )

    private fun ObxSeries.toRawMediaMetadata(): RawMediaMetadata =
            RawMediaMetadata(
                    originalTitle = name,
                    mediaType = MediaType.SERIES,
                    year = year,
                    sourceType = SourceType.XTREAM,
                    sourceLabel = "Xtream Series",
                    sourceId = "xtream:series:$seriesId",
                    poster = imagesJson?.let { ImageRef.Http(it) },
                    backdrop = null,
                    externalIds =
                            tmdbId?.toIntOrNull()?.let { id ->
                                ExternalIds(tmdb = TmdbRef(TmdbMediaType.TV, id))
                            }
                                    ?: ExternalIds()
            )

    private fun ObxEpisode.toRawMediaMetadata(): RawMediaMetadata =
            RawMediaMetadata(
                    originalTitle = title ?: "Episode $episodeNum",
                    mediaType = MediaType.SERIES_EPISODE,
                    season = season,
                    episode = episodeNum,
                    durationMs = durationSecs?.let { it * 1000L },
                    sourceType = SourceType.XTREAM,
                    sourceLabel = "Xtream Episode",
                    sourceId = "xtream:episode:$seriesId:$season:$episodeNum",
                    thumbnail = imageUrl?.let { ImageRef.Http(it) }
            )

    private fun RawMediaMetadata.toObxVod(): ObxVod? {
        // Accept both legacy and current formats:
        // - xtream:vod:{id}
        // - xtream:vod:{id}:{ext}
        val vodId =
                sourceId.removePrefix("xtream:vod:").split(":").firstOrNull()?.toIntOrNull()
                        ?: return null
        return ObxVod(
                vodId = vodId,
                name = originalTitle,
                nameLower = originalTitle.lowercase(),
                sortTitleLower = originalTitle.lowercase(),
                year = year,
                durationSecs = durationMs?.let { (it / 1000).toInt() },
                poster = (poster as? ImageRef.Http)?.url,
                tmdbId = externalIds.tmdb?.id?.toString(),
                updatedAt = System.currentTimeMillis()
        )
    }

    private fun RawMediaMetadata.toObxSeries(): ObxSeries? {
        val seriesId = sourceId.removePrefix("xtream:series:").toIntOrNull() ?: return null
        return ObxSeries(
                seriesId = seriesId,
                name = originalTitle,
                nameLower = originalTitle.lowercase(),
                sortTitleLower = originalTitle.lowercase(),
                year = year,
                imagesJson = (poster as? ImageRef.Http)?.url,
                tmdbId = externalIds.tmdb?.id?.toString(),
                updatedAt = System.currentTimeMillis()
        )
    }

    private fun RawMediaMetadata.toObxEpisode(): ObxEpisode? {
        val parts = sourceId.removePrefix("xtream:episode:").split(":")
        if (parts.size < 3) return null
        val seriesId = parts[0].toIntOrNull() ?: return null
        val seasonNum = parts[1].toIntOrNull() ?: return null
        val episodeNum = parts[2].toIntOrNull() ?: return null

        return ObxEpisode(
                seriesId = seriesId,
                season = seasonNum,
                episodeNum = episodeNum,
                title = originalTitle,
                durationSecs = durationMs?.let { (it / 1000).toInt() },
                imageUrl = (thumbnail as? ImageRef.Http)?.url
        )
    }
}
