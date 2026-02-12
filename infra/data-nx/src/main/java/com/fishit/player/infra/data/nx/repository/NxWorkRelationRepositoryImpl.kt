package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkRelationRepository
import com.fishit.player.core.model.repository.NxWorkRelationRepository.Relation
import com.fishit.player.core.model.repository.NxWorkRelationRepository.RelationType
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkRelation
import com.fishit.player.core.persistence.obx.NX_WorkRelation_
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.Box
import io.objectbox.BoxStore
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxWorkRelationRepository].
 *
 * Manages work relationships (series ↔ episode navigation).
 * Uses ToOne relations: parentWork and childWork.
 */
@Singleton
class NxWorkRelationRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore,
) : NxWorkRelationRepository {

    private val box: Box<NX_WorkRelation> = boxStore.boxFor(NX_WorkRelation::class.java)
    private val workBox: Box<NX_Work> = boxStore.boxFor(NX_Work::class.java)

    init {
        // One-time backfill on background thread to avoid ANR.
        // Reactive Flows auto-correct when put() triggers re-emission.
        Thread({
            backfillDenormalizedParentWorkKeys()
        }, "backfill-relation-parentWorkKey").start()
    }

    private fun backfillDenormalizedParentWorkKeys() {
        val empty = box.query(
            NX_WorkRelation_.parentWorkKey.equal("", StringOrder.CASE_SENSITIVE),
        ).build().find()
        if (empty.isEmpty()) return
        val updated = empty.mapNotNull { entity ->
            val wk = entity.parentWork.target?.workKey
            if (!wk.isNullOrBlank()) {
                entity.parentWorkKey = wk
                entity
            } else null
        }
        if (updated.isNotEmpty()) {
            box.put(updated)
            UnifiedLog.i("NxWorkRelationRepo") { "Backfilled parentWorkKey on ${updated.size} pre-migration relations" }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun findChildren(
        parentWorkKey: String,
        relationType: RelationType?,
    ): List<Relation> = withContext(Dispatchers.IO) {
        // INV-PERF: Indexed query via @Index parentWorkKey + native sort (replaces box.all full scan)
        val query = box.query(
            NX_WorkRelation_.parentWorkKey.equal(parentWorkKey, StringOrder.CASE_SENSITIVE).let { cond ->
                if (relationType != null) {
                    cond.and(NX_WorkRelation_.relationType.equal(relationType.name, StringOrder.CASE_SENSITIVE))
                } else cond
            },
        ).order(NX_WorkRelation_.sortOrder).build()

        query.find().mapNotNull { relation ->
            val childWork = relation.childWork.target
            if (childWork != null) {
                relation.toDomain(parentWorkKey, childWork.workKey)
            } else null
        }
    }

    override fun observeChildren(
        parentWorkKey: String,
        relationType: RelationType?,
    ): Flow<List<Relation>> {
        // INV-PERF: Indexed query + native sort (replaces box.all + client-side filter + sort)
        val query = box.query()
            .apply {
                equal(NX_WorkRelation_.parentWorkKey, parentWorkKey, StringOrder.CASE_SENSITIVE)
                if (relationType != null) {
                    equal(NX_WorkRelation_.relationType, relationType.name, StringOrder.CASE_SENSITIVE)
                }
                order(NX_WorkRelation_.sortOrder)
            }
            .build()

        return query.asFlow()
            .map { relations ->
                relations
                    .mapNotNull { relation ->
                        val childWork = relation.childWork.target
                        if (childWork != null) {
                            relation.toDomain(parentWorkKey, childWork.workKey)
                        } else null
                    }
            }
    }

    override suspend fun findEpisodesForSeries(seriesWorkKey: String): List<Relation> =
        findChildren(seriesWorkKey, RelationType.SERIES_EPISODE)

    override fun observeEpisodesForSeries(seriesWorkKey: String): Flow<List<Relation>> =
        observeChildren(seriesWorkKey, RelationType.SERIES_EPISODE)

    // ──────────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun upsert(relation: Relation): Relation = withContext(Dispatchers.IO) {
        val parentWork = workBox.query(NX_Work_.workKey.equal(relation.parentWorkKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst() ?: error("Parent work not found: ${relation.parentWorkKey}")

        val childWork = workBox.query(NX_Work_.workKey.equal(relation.childWorkKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst() ?: error("Child work not found: ${relation.childWorkKey}")

        // Check for existing relation
        val existing = findExistingRelation(relation.parentWorkKey, childWork.id, relation.relationType.name)

        val entity = if (existing != null) {
            // Update existing (backfill parentWorkKey for pre-migration entities)
            existing.apply {
                sortOrder = relation.orderIndex ?: 0
                season = relation.seasonNumber
                episode = relation.episodeNumber
                parentWorkKey = relation.parentWorkKey
            }
        } else {
            // Create new
            relation.toEntity().also { entity ->
                entity.parentWork.target = parentWork
                entity.childWork.target = childWork
            }
        }

        box.put(entity)
        entity.toDomain(relation.parentWorkKey, relation.childWorkKey)
    }

    override suspend fun upsertBatch(relations: List<Relation>): List<Relation> =
        withContext(Dispatchers.IO) {
            if (relations.isEmpty()) return@withContext emptyList()

            try {
                // Dedup by (parentWorkKey, childWorkKey, relationType)
                val uniqueRelations = relations
                    .associateBy { Triple(it.parentWorkKey, it.childWorkKey, it.relationType) }
                    .values.toList()

                boxStore.runInTx {
                    // Batch-resolve all needed workKeys to NX_Work entities
                    val allWorkKeys = (
                        uniqueRelations.map { it.parentWorkKey } +
                            uniqueRelations.map { it.childWorkKey }
                        ).distinct().toTypedArray()

                    val workMap = workBox.query(
                        NX_Work_.workKey.oneOf(allWorkKeys, StringOrder.CASE_SENSITIVE),
                    ).build().find().associateBy { it.workKey }

                    // INV-PERF: Scope existing relations by parentWorkKey index + relationType index
                    val batchParentKeys = uniqueRelations.map { it.parentWorkKey }.distinct().toTypedArray()
                    val batchTypes = uniqueRelations.map { it.relationType.name }.distinct().toTypedArray()

                    val existingRelations = box.query(
                        NX_WorkRelation_.parentWorkKey.oneOf(batchParentKeys, StringOrder.CASE_SENSITIVE)
                            .and(NX_WorkRelation_.relationType.oneOf(batchTypes, StringOrder.CASE_SENSITIVE)),
                    ).build().find()

                    // Build lookup: (parentId, childId, type) → entity
                    val existingMap = existingRelations.associateBy {
                        Triple(it.parentWork.targetId, it.childWork.targetId, it.relationType)
                    }

                    val entities = uniqueRelations.mapNotNull { relation ->
                        val parentWork = workMap[relation.parentWorkKey] ?: return@mapNotNull null
                        val childWork = workMap[relation.childWorkKey] ?: return@mapNotNull null

                        val existing = existingMap[Triple(parentWork.id, childWork.id, relation.relationType.name)]
                        if (existing != null) {
                            // Update existing (backfill parentWorkKey for pre-migration entities)
                            existing.apply {
                                sortOrder = relation.orderIndex ?: 0
                                season = relation.seasonNumber
                                episode = relation.episodeNumber
                                parentWorkKey = relation.parentWorkKey
                            }
                        } else {
                            // Create new
                            relation.toEntity().also { entity ->
                                entity.parentWork.target = parentWork
                                entity.childWork.target = childWork
                            }
                        }
                    }

                    box.put(entities)
                }

                uniqueRelations
            } finally {
                try {
                    boxStore.closeThreadResources()
                } catch (_: Exception) {
                    // Ignore cleanup errors
                }
            }
        }

    override suspend fun delete(
        parentWorkKey: String,
        childWorkKey: String,
        relationType: RelationType,
    ): Boolean = withContext(Dispatchers.IO) {
        val childWork = workBox.query(NX_Work_.workKey.equal(childWorkKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst() ?: return@withContext false

        val existing = findExistingRelation(parentWorkKey, childWork.id, relationType.name)
            ?: return@withContext false

        box.remove(existing.id)
        true
    }

    override suspend fun linkSeriesEpisode(
        seriesWorkKey: String,
        episodeWorkKey: String,
        season: Int,
        episode: Int,
        orderIndex: Int?,
    ): Relation = upsert(
        Relation(
            parentWorkKey = seriesWorkKey,
            childWorkKey = episodeWorkKey,
            relationType = RelationType.SERIES_EPISODE,
            orderIndex = orderIndex ?: (season * 1000 + episode), // Default sort order
            seasonNumber = season,
            episodeNumber = episode,
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * INV-PERF: Indexed query via @Index parentWorkKey + relationType (replaces box.all full scan).
     * Child is filtered in-memory from the small result set (typically <200 per parent).
     */
    private fun findExistingRelation(
        parentWorkKey: String,
        childId: Long,
        relationType: String,
    ): NX_WorkRelation? = box.query(
        NX_WorkRelation_.parentWorkKey.equal(parentWorkKey, StringOrder.CASE_SENSITIVE)
            .and(NX_WorkRelation_.relationType.equal(relationType, StringOrder.CASE_SENSITIVE)),
    ).build().find()
        .firstOrNull { it.childWork.targetId == childId }
}
