package com.fishit.player.infra.data.xtream

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.ObxLive
import com.fishit.player.core.persistence.obx.ObxLive_
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox-backed implementation of [XtreamLiveRepository].
 *
 * @deprecated Use [com.fishit.player.infra.data.nx.NxCatalogWriter] for writes
 * and NX repositories (NxWorkRepository, etc.) for reads.
 * This class is retained for dual-write migration period only.
 * See NX_SSOT_CONTRACT.md INV-6 for migration requirements.
 *
 * **Architecture Compliance:**
 * - Works only with RawMediaMetadata (no pipeline DTOs)
 * - Uses ObjectBox entities internally (ObxLive)
 * - Provides reactive Flows for UI consumption
 *
 * **Layer Boundaries:**
 * - Transport → Pipeline → Data → Domain → UI
 * - This repository sits in Data layer
 * - Consumes RawMediaMetadata from Pipeline (via CatalogSync)
 * - Serves RawMediaMetadata to Domain/UI
 *
 * **Source ID Format:**
 * - Live: "xtream:live:{streamId}"
 */
@Deprecated("Use NX repositories for reads. See NX_SSOT_CONTRACT.md INV-6.")
@Singleton
class ObxXtreamLiveRepository
    @Inject
    constructor(
        private val boxStore: BoxStore,
    ) : XtreamLiveRepository {
        companion object {
            private const val TAG = "ObxXtreamLiveRepo"
        }

        private val liveBox by lazy { boxStore.boxFor<ObxLive>() }

        override fun observeChannels(categoryId: String?): Flow<List<RawMediaMetadata>> {
            val query =
                if (categoryId != null) {
                    liveBox.query(ObxLive_.categoryId.equal(categoryId)).build()
                } else {
                    liveBox.query().order(ObxLive_.nameLower).build()
                }
            return query.asFlow().map { entities -> entities.map { it.toRawMediaMetadata() } }
        }

        override suspend fun getAll(
            limit: Int,
            offset: Int,
        ): List<RawMediaMetadata> =
            withContext(Dispatchers.IO) {
                liveBox
                    .query()
                    .order(ObxLive_.nameLower)
                    .build()
                    .find(offset.toLong(), limit.toLong())
                    .map { it.toRawMediaMetadata() }
            }

        override suspend fun getBySourceId(sourceId: String): RawMediaMetadata? =
            withContext(Dispatchers.IO) {
                val streamId =
                    sourceId.removePrefix("xtream:live:").toIntOrNull()
                        ?: return@withContext null
                liveBox
                    .query(ObxLive_.streamId.equal(streamId.toLong()))
                    .build()
                    .findFirst()
                    ?.toRawMediaMetadata()
            }

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<RawMediaMetadata> =
            withContext(Dispatchers.IO) {
                val lowerQuery = query.lowercase()
                liveBox
                    .query(ObxLive_.nameLower.contains(lowerQuery))
                    .build()
                    .find(0, limit.toLong())
                    .map { it.toRawMediaMetadata() }
            }

        override suspend fun upsertAll(items: List<RawMediaMetadata>) =
            withContext(Dispatchers.IO) {
                UnifiedLog.d(TAG, "upsertAll(${items.size} items)")

                val entities = items.mapNotNull { it.toObxLive() }
                if (entities.isEmpty()) return@withContext

                // Batch-optimized upsert: ONE query instead of N queries
                val streamIds = entities.map { it.streamId }.toIntArray()
                val existingEntities =
                    liveBox
                        .query(ObxLive_.streamId.oneOf(streamIds))
                        .build()
                        .find()
                val existingIdMap = existingEntities.associateBy({ it.streamId }, { it.id })

                val toUpsert =
                    entities.map { live ->
                        val existingId = existingIdMap[live.streamId]
                        if (existingId != null && existingId > 0) {
                            live.copy(id = existingId)
                        } else {
                            live
                        }
                    }

                liveBox.put(toUpsert)
                UnifiedLog.d(
                    TAG,
                    "upsertAll: ${entities.size} items (${existingEntities.size} updates, ${entities.size - existingEntities.size} inserts)",
                )
            }

        override suspend fun upsert(item: RawMediaMetadata) {
            upsertAll(listOf(item))
        }

        override suspend fun count(): Long =
            withContext(Dispatchers.IO) {
                liveBox.count()
            }

        override suspend fun deleteAll() =
            withContext(Dispatchers.IO) {
                UnifiedLog.d(TAG, "deleteAll()")
                liveBox.removeAll()
            }

        // ========================================================================
        // Mapping: ObxLive ↔ RawMediaMetadata
        // ========================================================================

        private fun ObxLive.toRawMediaMetadata(): RawMediaMetadata =
            RawMediaMetadata(
                originalTitle = name,
                mediaType = MediaType.LIVE,
                sourceType = SourceType.XTREAM,
                sourceLabel = categoryId ?: "Xtream Live",
                sourceId = "xtream:live:$streamId",
                poster = logo?.let { ImageRef.Http(it) },
                thumbnail = logo?.let { ImageRef.Http(it) },
            )

        private fun RawMediaMetadata.toObxLive(): ObxLive? {
            val streamId = sourceId.removePrefix("xtream:live:").toIntOrNull() ?: return null
            return ObxLive(
                streamId = streamId,
                name = originalTitle,
                nameLower = originalTitle.lowercase(),
                sortTitleLower = originalTitle.lowercase(),
                logo = (poster as? ImageRef.Http)?.url ?: (thumbnail as? ImageRef.Http)?.url,
                categoryId = sourceLabel.takeIf { it != "Xtream Live" },
            )
        }
    }
