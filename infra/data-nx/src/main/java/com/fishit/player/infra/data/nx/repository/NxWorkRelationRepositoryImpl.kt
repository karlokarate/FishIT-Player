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
    boxStore: BoxStore,
) : NxWorkRelationRepository {

    private val box: Box<NX_WorkRelation> = boxStore.boxFor(NX_WorkRelation::class.java)
    private val workBox: Box<NX_Work> = boxStore.boxFor(NX_Work::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun findChildren(
        parentWorkKey: String,
        relationType: RelationType?,
    ): List<Relation> = withContext(Dispatchers.IO) {
        val parentWork = workBox.query(NX_Work_.workKey.equal(parentWorkKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst() ?: return@withContext emptyList()

        // Get all relations where this work is parent
        val relations = box.all.filter { it.parentWork.targetId == parentWork.id }
            .let { list ->
                if (relationType != null) {
                    val typeStr = relationType.name
                    list.filter { it.relationType == typeStr }
                } else {
                    list
                }
            }
            .sortedBy { it.sortOrder }

        relations.mapNotNull { relation ->
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
        // Observe all relations and filter client-side
        return box.query()
            .apply {
                if (relationType != null) {
                    equal(NX_WorkRelation_.relationType, relationType.name, StringOrder.CASE_SENSITIVE)
                }
            }
            .build()
            .asFlow()
            .map { relations ->
                val parentWork = workBox.query(NX_Work_.workKey.equal(parentWorkKey, StringOrder.CASE_SENSITIVE))
                    .build()
                    .findFirst() ?: return@map emptyList()

                relations
                    .filter { it.parentWork.targetId == parentWork.id }
                    .sortedBy { it.sortOrder }
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
        val existing = findExistingRelation(parentWork.id, childWork.id, relation.relationType.name)

        val entity = if (existing != null) {
            // Update existing
            existing.apply {
                sortOrder = relation.orderIndex ?: 0
                season = relation.seasonNumber
                episode = relation.episodeNumber
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

    override suspend fun delete(
        parentWorkKey: String,
        childWorkKey: String,
        relationType: RelationType,
    ): Boolean = withContext(Dispatchers.IO) {
        val parentWork = workBox.query(NX_Work_.workKey.equal(parentWorkKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst() ?: return@withContext false

        val childWork = workBox.query(NX_Work_.workKey.equal(childWorkKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst() ?: return@withContext false

        val existing = findExistingRelation(parentWork.id, childWork.id, relationType.name)
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

    private fun findExistingRelation(
        parentId: Long,
        childId: Long,
        relationType: String,
    ): NX_WorkRelation? = box.all
        .filter { relation ->
            relation.parentWork.targetId == parentId &&
                relation.childWork.targetId == childId &&
                relation.relationType == relationType
        }
        .firstOrNull()
}
