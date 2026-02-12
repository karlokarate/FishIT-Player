/**
 * NX-backed implementation of XtreamCatalogRepository.
 *
 * Reads catalog data exclusively from NX_Work + NX_WorkSourceRef entities.
 * Writes are handled by NxCatalogWriter via CatalogSyncService.
 *
 * **Architecture:**
 * - UI SSOT: NX_Work graph (per NX_SSOT_CONTRACT.md INV-6)
 * - Transport DTOs: XtreamVodStream, XtreamSeriesStream (infra/transport-xtream)
 * - Pipeline DTOs: XtreamVodItem, XtreamSeriesItem (internal to pipeline)
 * - Core Models: RawMediaMetadata (canonical output)
 * - This repo: Maps NX_Work → RawMediaMetadata for legacy compatibility
 *
 * **DTO Flow (end-to-end):**
 * ```
 * Transport (API)         → Pipeline               → Normalizer        → NxCatalogWriter     → NX_Work
 * XtreamVodStream         → XtreamVodItem          → RawMediaMetadata  → NX_Work/SourceRef   → UI
 * XtreamSeriesStream      → XtreamSeriesItem       → RawMediaMetadata  → NX_Work/SourceRef   → UI
 * XtreamEpisodeInfo       → XtreamEpisode          → RawMediaMetadata  → NX_Work/SourceRef   → UI
 * ```
 *
 * **Field Mapping (Transport → NX_Work):**
 * | Transport Field      | NX_Work Field       | Notes                              |
 * |---------------------|--------------------|------------------------------------|
 * | name                | canonicalTitle     | Cleaned by normalizer              |
 * | vodId/seriesId      | (in NX_WorkSourceRef.sourceItemKey) | Via sourceKey           |
 * | stream_icon/cover   | poster             | Stored as ImageRef                 |
 * | backdrop_path       | backdrop           | Stored as ImageRef                 |
 * | year                | year               | Parsed to Int                      |
 * | rating              | rating             | Float rating                       |
 * | plot/description    | plot               | Long description                   |
 * | genre               | genres             | Comma-separated                    |
 * | director            | director           | Director name                      |
 * | cast                | cast               | Cast list                          |
 * | tmdb_id             | tmdbId             | External ID                        |
 * | imdb_id             | imdbId             | External ID                        |
 * | duration/durationSecs | durationMs       | Converted to milliseconds          |
 * | containerExtension  | (in NX_WorkVariant) | Stored in variant                 |
 *
 * @see com.fishit.player.infra.data.xtream.XtreamCatalogRepository
 * @see com.fishit.player.infra.data.nx.writer.NxCatalogWriter
 */
package com.fishit.player.infra.data.nx.xtream

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.ids.XtreamIdCodec
import com.fishit.player.core.model.ids.XtreamParsedSourceId
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceItemKind
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef_
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.infra.data.nx.mapper.MediaTypeMapper
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.mapper.WorkTypeMapper
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NX-backed implementation of XtreamCatalogRepository.
 *
 * Provides read operations for Xtream VOD, Series, and Episode content
 * from the NX work graph.
 */
@Singleton
class NxXtreamCatalogRepositoryImpl
    @Inject
    constructor(
        private val boxStore: BoxStore,
        private val workRepository: NxWorkRepository,
        private val sourceRefRepository: NxWorkSourceRefRepository,
        private val variantRepository: NxWorkVariantRepository,
    ) : XtreamCatalogRepository {
        companion object {
            private const val TAG = "NxXtreamCatalogRepo"
            // FIX: Match DB storage format - toEntityString() writes lowercase
            // See WorkSourceRefMapper.toEntityString() and NxWorkSourceRefRepositoryImpl.toEntityString()
            private const val SOURCE_TYPE = "xtream"
        }

        private val workBox by lazy { boxStore.boxFor<NX_Work>() }
        private val sourceRefBox by lazy { boxStore.boxFor<NX_WorkSourceRef>() }

        // =========================================================================
        // Observe Methods
        // =========================================================================

        override fun observeVod(categoryId: String?): Flow<List<RawMediaMetadata>> {
            // Query NX_Work for MOVIE type with Xtream source
            val workQuery = workBox.query(
                NX_Work_.workType.equal(WorkTypeMapper.toEntityString(WorkType.MOVIE)),
            ).order(NX_Work_.canonicalTitleLower).build()

            val sourceRefQuery = sourceRefBox.query(
                NX_WorkSourceRef_.sourceType.equal(SOURCE_TYPE)
                    .and(NX_WorkSourceRef_.sourceKey.contains(":vod:")),
            ).build()

            // Combine work and sourceRef data
            return combine(
                workQuery.asFlow(),
                sourceRefQuery.asFlow(),
            ) { works, sourceRefs ->
                val sourceKeysByWorkKey = sourceRefs
                    .groupBy { it.work.target?.workKey }
                    .mapValues { (_, refs) -> refs.firstOrNull() }

                works.mapNotNull { work ->
                    val sourceRef = sourceKeysByWorkKey[work.workKey]
                    if (sourceRef != null) {
                        work.toRawMediaMetadataVod(sourceRef)
                    } else null
                }
            }
        }

        override fun observeSeries(categoryId: String?): Flow<List<RawMediaMetadata>> {
            val workQuery = workBox.query(
                NX_Work_.workType.equal(WorkTypeMapper.toEntityString(WorkType.SERIES)),
            ).order(NX_Work_.canonicalTitleLower).build()

            val sourceRefQuery = sourceRefBox.query(
                NX_WorkSourceRef_.sourceType.equal(SOURCE_TYPE)
                    .and(NX_WorkSourceRef_.sourceKey.contains(":series:")),
            ).build()

            return combine(
                workQuery.asFlow(),
                sourceRefQuery.asFlow(),
            ) { works, sourceRefs ->
                val sourceKeysByWorkKey = sourceRefs
                    .groupBy { it.work.target?.workKey }
                    .mapValues { (_, refs) -> refs.firstOrNull() }

                works.mapNotNull { work ->
                    val sourceRef = sourceKeysByWorkKey[work.workKey]
                    if (sourceRef != null) {
                        work.toRawMediaMetadataSeries(sourceRef)
                    } else null
                }
            }
        }

        override fun observeEpisodes(
            seriesId: String,
            seasonNumber: Int?,
        ): Flow<List<RawMediaMetadata>> {
            // Episodes have sourceKey pattern: src:xtream:...:episode:{seriesId}:{season}:{episode}
            val episodePattern = ":episode:$seriesId:"

            val sourceRefQuery = sourceRefBox.query(
                NX_WorkSourceRef_.sourceType.equal(SOURCE_TYPE)
                    .and(NX_WorkSourceRef_.sourceKey.contains(episodePattern)),
            ).build()

            return sourceRefQuery.asFlow().map { sourceRefs ->
                sourceRefs.mapNotNull { sourceRef ->
                    val work = sourceRef.work.target ?: return@mapNotNull null
                    // Filter by season if specified
                    if (seasonNumber != null && work.season != seasonNumber) {
                        return@mapNotNull null
                    }
                    work.toRawMediaMetadataEpisode(sourceRef)
                }.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
            }
        }

        // =========================================================================
        // Query Methods
        // =========================================================================

        override suspend fun getAll(
            mediaType: MediaType?,
            limit: Int,
            offset: Int,
        ): List<RawMediaMetadata> = withContext(Dispatchers.IO) {
            val workType = mediaType?.let { WorkTypeMapper.toEntityString(MediaTypeMapper.toWorkType(it)) }

            val baseCondition: QueryCondition<NX_Work> = if (workType != null) {
                NX_Work_.workType.equal(workType)
            } else {
                NX_Work_.workType.oneOf(
                    arrayOf(
                        WorkTypeMapper.toEntityString(WorkType.MOVIE),
                        WorkTypeMapper.toEntityString(WorkType.SERIES),
                        WorkTypeMapper.toEntityString(WorkType.EPISODE),
                    ),
                )
            }

            val works = workBox.query(baseCondition)
                .order(NX_Work_.canonicalTitleLower)
                .build()
                .find(offset.toLong(), limit.toLong())

            // Get source refs for these works
            val workKeys = works.map { it.workKey }
            val sourceRefs = sourceRefBox.query(
                NX_WorkSourceRef_.sourceType.equal(SOURCE_TYPE),
            ).build().find().filter { it.work.target?.workKey in workKeys }

            val sourceRefMap = sourceRefs.associateBy { it.work.target?.workKey }

            works.mapNotNull { work ->
                val sourceRef = sourceRefMap[work.workKey] ?: return@mapNotNull null
                when (WorkTypeMapper.toWorkType(work.workType)) {
                    WorkType.MOVIE -> work.toRawMediaMetadataVod(sourceRef)
                    WorkType.SERIES -> work.toRawMediaMetadataSeries(sourceRef)
                    WorkType.EPISODE -> work.toRawMediaMetadataEpisode(sourceRef)
                    else -> null
                }
            }
        }

        override suspend fun getBySourceId(sourceId: String): RawMediaMetadata? =
            withContext(Dispatchers.IO) {
                // Find sourceRef by legacy sourceId pattern
                val sourceRef = findSourceRefByLegacyId(sourceId) ?: return@withContext null
                val work = sourceRef.work.target ?: return@withContext null

                when {
                    // NX format: src:xtream:<account>:vod:<id> or legacy: xtream:vod:<id>
            sourceId.contains(":vod:") -> work.toRawMediaMetadataVod(sourceRef)
                    sourceId.contains(":series:") -> work.toRawMediaMetadataSeries(sourceRef)
                    sourceId.contains(":episode:") -> work.toRawMediaMetadataEpisode(sourceRef)
                    else -> null
                }
            }

        override suspend fun search(query: String, limit: Int): List<RawMediaMetadata> =
            withContext(Dispatchers.IO) {
                val lowerQuery = query.lowercase()
                val works = workBox.query(
                    NX_Work_.canonicalTitleLower.contains(lowerQuery)
                        .and(
                            NX_Work_.workType.oneOf(
                                arrayOf(
                                    WorkTypeMapper.toEntityString(WorkType.MOVIE),
                                    WorkTypeMapper.toEntityString(WorkType.SERIES),
                                ),
                            ),
                        ),
                ).build().find(0, limit.toLong())

                val workKeys = works.map { it.workKey }
                val sourceRefs = sourceRefBox.query(
                    NX_WorkSourceRef_.sourceType.equal(SOURCE_TYPE),
                ).build().find().filter { it.work.target?.workKey in workKeys }

                val sourceRefMap = sourceRefs.associateBy { it.work.target?.workKey }

                works.mapNotNull { work ->
                    val sourceRef = sourceRefMap[work.workKey] ?: return@mapNotNull null
                    when (WorkTypeMapper.toWorkType(work.workType)) {
                        WorkType.MOVIE -> work.toRawMediaMetadataVod(sourceRef)
                        WorkType.SERIES -> work.toRawMediaMetadataSeries(sourceRef)
                        else -> null
                    }
                }
            }

        // =========================================================================
        // Write Methods (delegated to NxCatalogWriter via CatalogSync)
        // =========================================================================

        override suspend fun upsertAll(items: List<RawMediaMetadata>) {
            // Writes are handled by NxCatalogWriter via CatalogSyncService.
            // This method is a no-op for NX-backed implementation.
            UnifiedLog.w(TAG) { "upsertAll() called - writes should go through NxCatalogWriter" }
        }

        override suspend fun upsert(item: RawMediaMetadata) {
            UnifiedLog.w(TAG) { "upsert() called - writes should go through NxCatalogWriter" }
        }

        override suspend fun count(mediaType: MediaType?): Long = withContext(Dispatchers.IO) {
            val workType = mediaType?.let { WorkTypeMapper.toEntityString(MediaTypeMapper.toWorkType(it)) }

            if (workType != null) {
                workBox.query(NX_Work_.workType.equal(workType)).build().count()
            } else {
                workBox.query(
                    NX_Work_.workType.oneOf(
                        arrayOf(
                            WorkTypeMapper.toEntityString(WorkType.MOVIE),
                            WorkTypeMapper.toEntityString(WorkType.SERIES),
                            WorkTypeMapper.toEntityString(WorkType.EPISODE),
                        ),
                    ),
                ).build().count()
            }
        }

        override suspend fun deleteAll() = withContext(Dispatchers.IO) {
            UnifiedLog.w(TAG) { "deleteAll() called - use NxCatalogWriter.clearSourceType() instead" }
        }

        // =========================================================================
        // Info Backfill Support (legacy - metadata now comes through normalizer)
        // =========================================================================

        override suspend fun getVodIdsNeedingInfoBackfill(limit: Int, afterId: Int): List<Int> =
            withContext(Dispatchers.IO) {
                // NX stores already-normalized metadata. Backfill happens in normalizer.
                // Return empty list to indicate no backfill needed at this layer.
                emptyList()
            }

        override suspend fun getSeriesIdsNeedingInfoBackfill(limit: Int, afterId: Int): List<Int> =
            withContext(Dispatchers.IO) {
                emptyList()
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
            // Info updates should go through normalizer/NxCatalogWriter
            UnifiedLog.w(TAG) { "updateVodInfo() called - use normalizer for metadata updates" }
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
            UnifiedLog.w(TAG) { "updateSeriesInfo() called - use normalizer for metadata updates" }
        }

        override suspend fun countVodNeedingInfoBackfill(): Long = 0L

        override suspend fun countSeriesNeedingInfoBackfill(): Long = 0L

        // =========================================================================
        // Canonical Linking Support (legacy - NX handles this automatically)
        // =========================================================================

        override suspend fun getUnlinkedForCanonicalLinking(
            mediaType: MediaType?,
            limit: Int,
        ): List<RawMediaMetadata> = withContext(Dispatchers.IO) {
            // In NX architecture, all items are automatically linked via NX_WorkSourceRef.
            // Return empty list to indicate no unlinked items.
            emptyList()
        }

        override suspend fun countUnlinkedForCanonicalLinking(mediaType: MediaType?): Long = 0L

        // =========================================================================
        // Private Helpers
        // =========================================================================

        /**
         * Find NX_WorkSourceRef by legacy sourceId format.
         *
         * Delegates to [XtreamIdCodec] SSOT for parsing legacy format,
         * then searches for matching NX sourceKey.
         *
         * Legacy format: "xtream:vod:{id}" or "xtream:series:{id}" or "xtream:episode:{seriesId}:{season}:{episode}"
         * NX sourceKey: "src:xtream:{accountKey}:vod:{id}"
         */
        private fun findSourceRefByLegacyId(legacySourceId: String): NX_WorkSourceRef? {
            val parsed = XtreamIdCodec.parse(legacySourceId) ?: return null

            // Build search pattern from parsed result
            val searchPattern = when (parsed) {
                is XtreamParsedSourceId.Vod -> ":vod:${parsed.vodId}"
                is XtreamParsedSourceId.Series -> ":series:${parsed.seriesId}"
                is XtreamParsedSourceId.Episode -> ":episode:${parsed.episodeId}"
                is XtreamParsedSourceId.EpisodeComposite ->
                    ":episode:series:${parsed.seriesId}:s${parsed.season}:e${parsed.episode}"
                is XtreamParsedSourceId.Live -> ":live:${parsed.channelId}"
            }

            return sourceRefBox.query(
                NX_WorkSourceRef_.sourceType.equal(SOURCE_TYPE)
                    .and(NX_WorkSourceRef_.sourceKey.contains(searchPattern)),
            ).build().findFirst()
        }

        // =========================================================================
        // Mapping: NX_Work → RawMediaMetadata
        // =========================================================================

        /**
         * Map NX_Work to RawMediaMetadata for VOD items.
         *
         * Field mapping from NX_Work (populated by NxCatalogWriter from normalized metadata):
         * - canonicalTitle → originalTitle (normalized title)
         * - year → year
         * - durationMs → durationMs
         * - poster → poster (ImageRef)
         * - backdrop → backdrop (ImageRef)
         * - rating → rating
         * - plot → plot
         * - genres → genres
         * - director → director
         * - cast → cast
         * - tmdbId → externalIds.tmdb
         * - imdbId → externalIds.imdbId
         */
        private fun NX_Work.toRawMediaMetadataVod(sourceRef: NX_WorkSourceRef): RawMediaMetadata {
            // Extract vodId from sourceKey for playback hints (SSOT: SourceKeyParser)
            val vodId = SourceKeyParser.extractItemKey(sourceRef.sourceKey)

            val hints = buildMap {
                put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_VOD)
                vodId?.let { put(PlaybackHintKeys.Xtream.VOD_ID, it) }
            }

            return RawMediaMetadata(
                originalTitle = canonicalTitle,
                mediaType = MediaType.MOVIE,
                year = year,
                durationMs = durationMs,
                sourceType = SourceType.XTREAM,
                sourceLabel = "Xtream VOD",
                sourceId = XtreamIdCodec.vodOrUnknown(vodId),
                poster = poster,
                backdrop = backdrop,
                externalIds = buildExternalIds(TmdbMediaType.MOVIE),
                rating = rating,
                plot = plot,
                genres = genres,
                director = director,
                cast = cast,
                playbackHints = hints,
            )
        }

        private fun NX_Work.toRawMediaMetadataSeries(sourceRef: NX_WorkSourceRef): RawMediaMetadata {
            val seriesId = SourceKeyParser.extractItemKey(sourceRef.sourceKey)

            val hints = buildMap {
                put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_SERIES)
                seriesId?.let { put(PlaybackHintKeys.Xtream.SERIES_ID, it) }
            }

            return RawMediaMetadata(
                originalTitle = canonicalTitle,
                mediaType = MediaType.SERIES,
                year = year,
                sourceType = SourceType.XTREAM,
                sourceLabel = "Xtream Series",
                sourceId = XtreamIdCodec.seriesOrUnknown(seriesId),
                poster = poster,
                backdrop = backdrop,
                externalIds = buildExternalIds(TmdbMediaType.TV),
                rating = rating,
                plot = plot,
                genres = genres,
                director = director,
                cast = cast,
                playbackHints = hints,
            )
        }

        private fun NX_Work.toRawMediaMetadataEpisode(sourceRef: NX_WorkSourceRef): RawMediaMetadata {
            // Extract episode info from sourceKey (SSOT: SourceKeyParser)
            val episodeInfo = SourceKeyParser.extractXtreamEpisodeInfo(sourceRef.sourceKey)
            val episodeId = SourceKeyParser.extractXtreamEpisodeId(sourceRef.sourceKey)

            val hints = buildMap {
                put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_SERIES)
                episodeInfo?.seriesId?.let { put(PlaybackHintKeys.Xtream.SERIES_ID, it.toString()) }
                season?.let { put(PlaybackHintKeys.Xtream.SEASON_NUMBER, it.toString()) }
                episode?.let { put(PlaybackHintKeys.Xtream.EPISODE_NUMBER, it.toString()) }
                episodeId?.let { put(PlaybackHintKeys.Xtream.EPISODE_ID, it.toString()) }
            }

            return RawMediaMetadata(
                originalTitle = canonicalTitle,
                mediaType = MediaType.SERIES_EPISODE,
                season = season,
                episode = episode,
                durationMs = durationMs,
                sourceType = SourceType.XTREAM,
                sourceLabel = "Xtream Episode",
                sourceId = XtreamIdCodec.episodeCompositeOrUnknown(episodeInfo?.seriesId?.toString(), season, episode),
                thumbnail = thumbnail ?: poster,
                externalIds = buildExternalIds(TmdbMediaType.TV),
                rating = rating,
                plot = plot,
                playbackHints = hints,
            )
        }

        /**
         * Build ExternalIds from NX_Work entity.
         *
         * Note: TMDB-ID is stored as String in NX_Work for persistence compatibility.
         * The pipeline writes Int via NxCatalogWriter, NX_Work stores as String,
         * and we convert back to TmdbRef here for domain use. This is intentional.
         */
        private fun NX_Work.buildExternalIds(tmdbType: TmdbMediaType): ExternalIds {
            val tmdbRef = tmdbId?.toIntOrNull()?.let { TmdbRef(tmdbType, it) }
            return ExternalIds(tmdb = tmdbRef, imdbId = imdbId)
        }

    }
