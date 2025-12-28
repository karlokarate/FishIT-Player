package com.fishit.player.infra.data.xtream

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PlaybackHintKeys
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
    // Info Backfill Support
    // ========================================================================

    override suspend fun getVodIdsNeedingInfoBackfill(limit: Int, afterId: Int): List<Int> =
        withContext(Dispatchers.IO) {
            // Find VOD items where plot is null/empty (indicates missing info)
            vodBox.query()
                .apply {
                    if (afterId > 0) {
                        greater(ObxVod_.vodId, afterId.toLong())
                    }
                }
                .isNull(ObxVod_.plot)
                .order(ObxVod_.vodId)
                .build()
                .find(0, limit.toLong())
                .map { it.vodId }
        }

    override suspend fun getSeriesIdsNeedingInfoBackfill(limit: Int, afterId: Int): List<Int> =
        withContext(Dispatchers.IO) {
            // Find series items where plot is null/empty (indicates missing info)
            seriesBox.query()
                .apply {
                    if (afterId > 0) {
                        greater(ObxSeries_.seriesId, afterId.toLong())
                    }
                }
                .isNull(ObxSeries_.plot)
                .order(ObxSeries_.seriesId)
                .build()
                .find(0, limit.toLong())
                .map { it.seriesId }
        }

    override suspend fun updateVodInfo(
        vodId: Int,
        plot: String?,
        director: String?,
        cast: String?,
        genre: String?,
        rating: Double?,
        durationSecs: Int?,
        trailer: String?,
        tmdbId: String?,
    ) = withContext(Dispatchers.IO) {
        val existing = vodBox.query(ObxVod_.vodId.equal(vodId.toLong()))
            .build()
            .findFirst() ?: return@withContext

        val updated = existing.copy(
            plot = plot ?: existing.plot,
            director = director ?: existing.director,
            cast = cast ?: existing.cast,
            genre = genre ?: existing.genre,
            rating = rating ?: existing.rating,
            durationSecs = durationSecs ?: existing.durationSecs,
            trailer = trailer ?: existing.trailer,
            tmdbId = tmdbId ?: existing.tmdbId,
            updatedAt = System.currentTimeMillis(),
        )
        vodBox.put(updated)
        UnifiedLog.d(TAG) { "Updated VOD info: vodId=$vodId tmdbId=$tmdbId" }
    }

    override suspend fun updateSeriesInfo(
        seriesId: Int,
        plot: String?,
        director: String?,
        cast: String?,
        genre: String?,
        rating: Double?,
        trailer: String?,
        tmdbId: String?,
    ) = withContext(Dispatchers.IO) {
        val existing = seriesBox.query(ObxSeries_.seriesId.equal(seriesId.toLong()))
            .build()
            .findFirst() ?: return@withContext

        val updated = existing.copy(
            plot = plot ?: existing.plot,
            director = director ?: existing.director,
            cast = cast ?: existing.cast,
            genre = genre ?: existing.genre,
            rating = rating ?: existing.rating,
            trailer = trailer ?: existing.trailer,
            tmdbId = tmdbId ?: existing.tmdbId,
            updatedAt = System.currentTimeMillis(),
        )
        seriesBox.put(updated)
        UnifiedLog.d(TAG) { "Updated series info: seriesId=$seriesId tmdbId=$tmdbId" }
    }

    override suspend fun countVodNeedingInfoBackfill(): Long =
        withContext(Dispatchers.IO) {
            vodBox.query()
                .isNull(ObxVod_.plot)
                .build()
                .count()
        }

    override suspend fun countSeriesNeedingInfoBackfill(): Long =
        withContext(Dispatchers.IO) {
            seriesBox.query()
                .isNull(ObxSeries_.plot)
                .build()
                .count()
        }

    // ========================================================================
    // Private Upsert Helpers (Batch-Optimized)
    // ========================================================================

    /**
     * Batch upsert VOD items with optimized ID lookup.
     *
     * **Performance Optimization:**
     * Instead of N queries (one per item), we:
     * 1. Collect all vodIds from the batch
     * 2. Load ALL existing entities with those IDs in ONE query
     * 3. Build a lookup map for O(1) access
     * 4. Map new items to preserve existing ObjectBox IDs
     * 5. Put all in one batch
     *
     * This reduces 43,000 queries to 1 query for a 43k VOD catalog.
     */
    private fun upsertVods(vods: List<ObxVod>) {
        if (vods.isEmpty()) return
        
        // Step 1: Collect all vodIds we need to check
        val vodIds = vods.map { it.vodId.toLong() }.toLongArray()
        
        // Step 2: Load all existing entities in ONE query
        val existingEntities = vodBox.query(ObxVod_.vodId.oneOf(vodIds))
            .build()
            .find()
        
        // Step 3: Build lookup map (vodId → ObjectBox entity id)
        val existingIdMap = existingEntities.associateBy({ it.vodId }, { it.id })
        
        // Step 4: Map new items, preserving existing IDs for upsert
        val toUpsert = vods.map { vod ->
            val existingId = existingIdMap[vod.vodId]
            if (existingId != null && existingId > 0) {
                vod.copy(id = existingId)
            } else {
                vod
            }
        }
        
        // Step 5: Batch put
        vodBox.put(toUpsert)
        UnifiedLog.d(TAG, "upsertVods: ${vods.size} items (${existingEntities.size} updates, ${vods.size - existingEntities.size} inserts)")
    }

    /**
     * Batch upsert Series items with optimized ID lookup.
     */
    private fun upsertSeriesEntities(series: List<ObxSeries>) {
        if (series.isEmpty()) return
        
        val seriesIds = series.map { it.seriesId.toLong() }.toLongArray()
        val existingEntities = seriesBox.query(ObxSeries_.seriesId.oneOf(seriesIds))
            .build()
            .find()
        val existingIdMap = existingEntities.associateBy({ it.seriesId }, { it.id })
        
        val toUpsert = series.map { s ->
            val existingId = existingIdMap[s.seriesId]
            if (existingId != null && existingId > 0) {
                s.copy(id = existingId)
            } else {
                s
            }
        }
        
        seriesBox.put(toUpsert)
        UnifiedLog.d(TAG, "upsertSeriesEntities: ${series.size} items (${existingEntities.size} updates, ${series.size - existingEntities.size} inserts)")
    }

    /**
     * Batch upsert Episode items with optimized lookup.
     *
     * Episodes are identified by composite key (seriesId, season, episodeNum).
     * We use a composite string key for the lookup map.
     */
    private fun upsertEpisodes(episodes: List<ObxEpisode>) {
        if (episodes.isEmpty()) return
        
        // For episodes, we need to match on (seriesId, season, episodeNum)
        // Load all series IDs involved in this batch
        val seriesIds = episodes.map { it.seriesId.toLong() }.distinct().toLongArray()
        
        // Load all existing episodes for these series in ONE query
        val existingEpisodes = episodeBox.query(ObxEpisode_.seriesId.oneOf(seriesIds))
            .build()
            .find()
        
        // Build lookup map with composite key "seriesId:season:episodeNum"
        val existingIdMap = existingEpisodes.associateBy(
            { "${it.seriesId}:${it.season}:${it.episodeNum}" },
            { it.id }
        )
        
        val toUpsert = episodes.map { ep ->
            val key = "${ep.seriesId}:${ep.season}:${ep.episodeNum}"
            val existingId = existingIdMap[key]
            if (existingId != null && existingId > 0) {
                ep.copy(id = existingId)
            } else {
                ep
            }
        }
        
        episodeBox.put(toUpsert)
        UnifiedLog.d(TAG, "upsertEpisodes: ${episodes.size} items (${existingEpisodes.size} existing)")
    }

    // ========================================================================
    // Mapping: ObxVod/ObxSeries/ObxEpisode ↔ RawMediaMetadata
    // ========================================================================

    private fun ObxVod.toRawMediaMetadata(): RawMediaMetadata {
            // Build playback hints for VOD URL construction
            val hints = buildMap {
                    put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_VOD)
                    put(PlaybackHintKeys.Xtream.VOD_ID, vodId.toString())
                    containerExt?.takeIf { it.isNotBlank() }?.let {
                            put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
                    }
            }
            return RawMediaMetadata(
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
                                    ?: ExternalIds(),
                    playbackHints = hints,
                    // === Rich metadata from persisted info backfill ===
                    rating = rating,
                    plot = plot,
                    genres = genre,
                    director = director,
                    cast = cast,
                    trailer = trailer,
            )
    }

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
                                    ?: ExternalIds(),
                    // === Rich metadata from persisted info backfill ===
                    rating = rating,
                    plot = plot,
                    genres = genre,
                    director = director,
                    cast = cast,
                    trailer = trailer,
            )
            )

    private fun ObxEpisode.toRawMediaMetadata(): RawMediaMetadata {
            // Build playback hints from stored episode data
            val hints = buildMap {
                    put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_SERIES)
                    put(PlaybackHintKeys.Xtream.SERIES_ID, seriesId.toString())
                    put(PlaybackHintKeys.Xtream.SEASON_NUMBER, season.toString())
                    put(PlaybackHintKeys.Xtream.EPISODE_NUMBER, episodeNum.toString())
                    // Episode ID (stream ID) - CRITICAL for URL construction
                    if (episodeId != 0) {
                            put(PlaybackHintKeys.Xtream.EPISODE_ID, episodeId.toString())
                    }
                    playExt?.takeIf { it.isNotBlank() }?.let {
                            put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
                    }
            }
            return RawMediaMetadata(
                    originalTitle = title ?: "Episode $episodeNum",
                    mediaType = MediaType.SERIES_EPISODE,
                    season = season,
                    episode = episodeNum,
                    durationMs = durationSecs?.let { it * 1000L },
                    sourceType = SourceType.XTREAM,
                    sourceLabel = "Xtream Episode",
                    sourceId = "xtream:episode:$seriesId:$season:$episodeNum",
                    thumbnail = imageUrl?.let { ImageRef.Http(it) },
                    playbackHints = hints,
            )
    }

    private fun RawMediaMetadata.toObxVod(): ObxVod? {
        // Accept both legacy and current formats:
        // - xtream:vod:{id}
        // - xtream:vod:{id}:{ext}
        val vodId =
                sourceId.removePrefix("xtream:vod:").split(":").firstOrNull()?.toIntOrNull()
                        ?: return null
        // Extract containerExt from playbackHints
        val containerExt = playbackHints[PlaybackHintKeys.Xtream.CONTAINER_EXT]
        return ObxVod(
                vodId = vodId,
                name = originalTitle,
                nameLower = originalTitle.lowercase(),
                sortTitleLower = originalTitle.lowercase(),
                year = year,
                durationSecs = durationMs?.let { (it / 1000).toInt() },
                poster = (poster as? ImageRef.Http)?.url,
                tmdbId = externalIds.tmdb?.id?.toString(),
                containerExt = containerExt,
                // Rich metadata from provider
                rating = rating,
                plot = plot,
                genre = genres,
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
                // Rich metadata from provider
                rating = rating,
                plot = plot,
                genre = genres,
                director = director,
                cast = cast,
                updatedAt = System.currentTimeMillis()
        )
    }

    private fun RawMediaMetadata.toObxEpisode(): ObxEpisode? {
        val parts = sourceId.removePrefix("xtream:episode:").split(":")
        if (parts.size < 3) return null
        val seriesId = parts[0].toIntOrNull() ?: return null
        val seasonNum = parts[1].toIntOrNull() ?: return null
        val episodeNum = parts[2].toIntOrNull() ?: return null

        // Extract episodeId and playExt from playbackHints
        val episodeStreamId = playbackHints[PlaybackHintKeys.Xtream.EPISODE_ID]?.toIntOrNull() ?: 0
        val containerExt = playbackHints[PlaybackHintKeys.Xtream.CONTAINER_EXT]

        return ObxEpisode(
                seriesId = seriesId,
                season = seasonNum,
                episodeNum = episodeNum,
                episodeId = episodeStreamId,
                title = originalTitle,
                durationSecs = durationMs?.let { (it / 1000).toInt() },
                imageUrl = (thumbnail as? ImageRef.Http)?.url,
                playExt = containerExt,
        )
    }
}
