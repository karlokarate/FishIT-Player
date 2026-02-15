/**
 * NX-backed implementation of XtreamLiveRepository.
 *
 * Reads live channel data exclusively from NX_Work + NX_WorkSourceRef entities.
 * Writes are handled by NxCatalogWriter via XtreamSyncService.
 *
 * **Architecture:**
 * - UI SSOT: NX_Work graph (per NX_SSOT_CONTRACT.md INV-6)
 * - Transport DTOs: XtreamLiveStream (infra/transport-xtream)
 * - Pipeline DTOs: XtreamChannel (internal to pipeline)
 * - Core Models: RawMediaMetadata (canonical output)
 * - This repo: Maps NX_Work → RawMediaMetadata for legacy compatibility
 *
 * **DTO Flow (end-to-end):**
 * ```
 * Transport (API)         → Pipeline               → Normalizer        → NxCatalogWriter     → NX_Work
 * XtreamLiveStream        → XtreamChannel          → RawMediaMetadata  → NX_Work/SourceRef   → UI
 * ```
 *
 * **Field Mapping (Transport → NX_Work):**
 * | Transport Field      | NX_Work Field       | Notes                              |
 * |---------------------|--------------------|------------------------------------|
 * | name                | canonicalTitle     | Cleaned by normalizer              |
 * | streamId            | (in NX_WorkSourceRef.sourceItemKey) | Via sourceKey           |
 * | stream_icon/logo    | poster             | Stored as ImageRef                 |
 * | epg_channel_id      | (in extras)        | For EPG mapping                    |
 * | category_id         | (in sourceLabel)   | Category context                   |
 * | tv_archive          | (in playbackHints) | Catchup availability               |
 *
 * @see com.fishit.player.infra.data.xtream.XtreamLiveRepository
 * @see com.fishit.player.infra.data.nx.writer.NxCatalogWriter
 */
package com.fishit.player.infra.data.nx.xtream

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.XtreamIdCodec
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef_
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.mapper.WorkTypeMapper
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NX-backed implementation of XtreamLiveRepository.
 *
 * Provides read operations for Xtream Live TV channels from the NX work graph.
 */
@Singleton
class NxXtreamLiveRepositoryImpl
    @Inject
    constructor(
        private val boxStore: BoxStore,
    ) : XtreamLiveRepository {
        companion object {
            private const val TAG = "NxXtreamLiveRepo"

            // FIX: Match DB storage format - toEntityString() writes lowercase
            // See WorkSourceRefMapper.toEntityString() and NxWorkSourceRefRepositoryImpl.toEntityString()
            private const val SOURCE_TYPE = "xtream"
        }

        private val workBox by lazy { boxStore.boxFor<NX_Work>() }
        private val sourceRefBox by lazy { boxStore.boxFor<NX_WorkSourceRef>() }

        // =========================================================================
        // Observe Methods
        // =========================================================================

        override fun observeChannels(categoryId: String?): Flow<List<RawMediaMetadata>> {
            // Query NX_Work for LIVE_CHANNEL type
            val workQuery =
                workBox
                    .query(
                        NX_Work_.workType.equal(WorkTypeMapper.toEntityString(WorkType.LIVE_CHANNEL)),
                    ).order(NX_Work_.canonicalTitleLower)
                    .build()

            val sourceRefQuery =
                sourceRefBox
                    .query(
                        NX_WorkSourceRef_.sourceType
                            .equal(SOURCE_TYPE)
                            .and(NX_WorkSourceRef_.sourceKey.contains(":live:")),
                    ).build()

            return combine(
                workQuery.asFlow(),
                sourceRefQuery.asFlow(),
            ) { works, sourceRefs ->
                val sourceKeysByWorkKey =
                    sourceRefs
                        .groupBy { it.workKey }
                        .mapValues { (_, refs) -> refs.firstOrNull() }

                works.mapNotNull { work ->
                    val sourceRef = sourceKeysByWorkKey[work.workKey]
                    if (sourceRef != null) {
                        work.toRawMediaMetadataLive(sourceRef)
                    } else {
                        null
                    }
                }
            }
        }

        // =========================================================================
        // Query Methods
        // =========================================================================

        override suspend fun getAll(
            limit: Int,
            offset: Int,
        ): List<RawMediaMetadata> =
            withContext(Dispatchers.IO) {
                val works =
                    workBox
                        .query(
                            NX_Work_.workType.equal(WorkTypeMapper.toEntityString(WorkType.LIVE_CHANNEL)),
                        ).order(NX_Work_.canonicalTitleLower)
                        .build()
                        .find(offset.toLong(), limit.toLong())

                val workKeys = works.map { it.workKey }
                val sourceRefs =
                    if (workKeys.isNotEmpty()) {
                        sourceRefBox
                            .query(
                                NX_WorkSourceRef_.sourceType
                                    .equal(SOURCE_TYPE)
                                    .and(NX_WorkSourceRef_.sourceKey.contains(":live:"))
                                    .and(NX_WorkSourceRef_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE)),
                            ).build()
                            .find()
                    } else {
                        emptyList()
                    }

                val sourceRefMap = sourceRefs.associateBy { it.workKey }

                works.mapNotNull { work ->
                    val sourceRef = sourceRefMap[work.workKey] ?: return@mapNotNull null
                    work.toRawMediaMetadataLive(sourceRef)
                }
            }

        override suspend fun getBySourceId(sourceId: String): RawMediaMetadata? =
            withContext(Dispatchers.IO) {
                // Parse the sourceId via XtreamIdCodec SSOT — handles both legacy and NX formats:
                // Legacy: "xtream:live:456"
                // NX: "src:xtream:account:live:456"
                val parsed = XtreamIdCodec.parse(sourceId)
                val channelId =
                    when (parsed) {
                        is com.fishit.player.core.model.ids.XtreamParsedSourceId.Live -> parsed.channelId
                        else -> null
                    } ?: return@withContext null

                // Find sourceRef by pattern matching
                val searchPattern = ":live:$channelId"
                val sourceRef =
                    sourceRefBox
                        .query(
                            NX_WorkSourceRef_.sourceType
                                .equal(SOURCE_TYPE)
                                .and(NX_WorkSourceRef_.sourceKey.contains(searchPattern)),
                        ).build()
                        .findFirst() ?: return@withContext null

                val work = sourceRef.work.target ?: return@withContext null
                work.toRawMediaMetadataLive(sourceRef)
            }

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<RawMediaMetadata> =
            withContext(Dispatchers.IO) {
                val lowerQuery = query.lowercase()
                val works =
                    workBox
                        .query(
                            NX_Work_.canonicalTitleLower
                                .contains(lowerQuery)
                                .and(NX_Work_.workType.equal(WorkTypeMapper.toEntityString(WorkType.LIVE_CHANNEL))),
                        ).build()
                        .find(0, limit.toLong())

                val workKeys = works.map { it.workKey }
                val sourceRefs =
                    if (workKeys.isNotEmpty()) {
                        sourceRefBox
                            .query(
                                NX_WorkSourceRef_.sourceType
                                    .equal(SOURCE_TYPE)
                                    .and(NX_WorkSourceRef_.sourceKey.contains(":live:"))
                                    .and(NX_WorkSourceRef_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE)),
                            ).build()
                            .find()
                    } else {
                        emptyList()
                    }

                val sourceRefMap = sourceRefs.associateBy { it.workKey }

                works.mapNotNull { work ->
                    val sourceRef = sourceRefMap[work.workKey] ?: return@mapNotNull null
                    work.toRawMediaMetadataLive(sourceRef)
                }
            }

        // =========================================================================
        // Write Methods (delegated to NxCatalogWriter via CatalogSync)
        // =========================================================================

        override suspend fun upsertAll(items: List<RawMediaMetadata>) {
            UnifiedLog.w(TAG) { "upsertAll() called - writes should go through NxCatalogWriter" }
        }

        override suspend fun upsert(item: RawMediaMetadata) {
            UnifiedLog.w(TAG) { "upsert() called - writes should go through NxCatalogWriter" }
        }

        override suspend fun count(): Long =
            withContext(Dispatchers.IO) {
                workBox
                    .query(
                        NX_Work_.workType.equal(WorkTypeMapper.toEntityString(WorkType.LIVE_CHANNEL)),
                    ).build()
                    .count()
            }

        override suspend fun deleteAll() =
            withContext(Dispatchers.IO) {
                UnifiedLog.w(TAG) { "deleteAll() called - use NxCatalogWriter.clearSourceType() instead" }
            }

        // =========================================================================
        // Mapping: NX_Work → RawMediaMetadata (Live Channel)
        // =========================================================================

        /**
         * Map NX_Work to RawMediaMetadata for live channels.
         *
         * Live channels have simpler metadata compared to VOD/Series:
         * - No year, duration, or episode info
         * - Poster/logo is the channel icon
         * - sourceId format: "xtream:live:{streamId}"
         */
        private fun NX_Work.toRawMediaMetadataLive(sourceRef: NX_WorkSourceRef): RawMediaMetadata {
            val streamId = SourceKeyParser.extractXtreamStreamId(sourceRef.sourceKey)

            return RawMediaMetadata(
                originalTitle = canonicalTitle,
                mediaType = MediaType.LIVE,
                sourceType = SourceType.XTREAM,
                sourceLabel = "Xtream Live",
                sourceId = XtreamIdCodec.liveOrUnknown(streamId),
                poster = poster,
                thumbnail = poster, // Use poster as thumbnail for live channels
            )
        }
    }
