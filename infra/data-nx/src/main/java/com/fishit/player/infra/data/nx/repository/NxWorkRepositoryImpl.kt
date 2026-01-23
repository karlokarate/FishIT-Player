/**
 * ObjectBox implementation of [NxWorkRepository].
 *
 * Provides the SSOT for canonical Works in the UI layer.
 * Maps between NX_Work entity and Work domain model.
 */
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox-backed repository for canonical Works.
 *
 * Thread-safe: all suspend functions run on IO dispatcher.
 * Uses ObjectBox Flow for reactive observation.
 */
@Singleton
class NxWorkRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore,
) : NxWorkRepository {
    private val box by lazy { boxStore.boxFor<NX_Work>() }

    // ──────────────────────────────────────────────────────────────────────
    // Single item
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun get(workKey: String): Work? = withContext(Dispatchers.IO) {
        box.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?.toDomain()
    }

    override fun observe(workKey: String): Flow<Work?> {
        return box.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .flow()
            .map { list -> list.firstOrNull()?.toDomain() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Lists (UI-critical)
    // ──────────────────────────────────────────────────────────────────────

    override fun observeByType(type: WorkType, limit: Int): Flow<List<Work>> {
        val typeString = type.toEntityString()
        return box.query(NX_Work_.workType.equal(typeString, StringOrder.CASE_SENSITIVE))
            .order(NX_Work_.canonicalTitle)
            .build()
            .flow()
            .map { list -> list.take(limit).map { it.toDomain() } }
    }

    override fun observeRecentlyUpdated(limit: Int): Flow<List<Work>> {
        return box.query()
            .orderDesc(NX_Work_.updatedAt)
            .build()
            .flow()
            .map { list -> list.take(limit).map { it.toDomain() } }
    }

    override fun observeNeedsReview(limit: Int): Flow<List<Work>> {
        return box.query(NX_Work_.needsReview.equal(true))
            .order(NX_Work_.canonicalTitle)
            .build()
            .flow()
            .map { list -> list.take(limit).map { it.toDomain() } }
    }

    override suspend fun searchByTitle(queryNormalized: String, limit: Int): List<Work> = withContext(Dispatchers.IO) {
        box.query(NX_Work_.canonicalTitleLower.contains(queryNormalized.lowercase(), StringOrder.CASE_INSENSITIVE))
            .order(NX_Work_.canonicalTitle)
            .build()
            .find(0, limit.toLong())
            .map { it.toDomain() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Writes (MVP)
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun upsert(work: Work): Work = withContext(Dispatchers.IO) {
        val existing = box.query(NX_Work_.workKey.equal(work.workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
        val entity = work.toEntity(existing)
        box.put(entity)
        entity.toDomain()
    }

    override suspend fun upsertBatch(works: List<Work>): List<Work> = withContext(Dispatchers.IO) {
        if (works.isEmpty()) return@withContext emptyList()

        // Batch lookup existing entities by workKey
        val workKeys = works.map { it.workKey }
        val existingMap = box.query(NX_Work_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
            .build()
            .find()
            .associateBy { it.workKey }

        val entities = works.map { work ->
            work.toEntity(existingMap[work.workKey])
        }
        box.put(entities)
        entities.map { it.toDomain() }
    }

    override suspend fun softDelete(workKey: String): Boolean = withContext(Dispatchers.IO) {
        // Soft delete not implemented in entity yet, for now just mark needsReview
        // TODO: Add isDeleted flag to NX_Work entity when soft delete is needed
        val entity = box.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
        if (entity != null) {
            box.put(entity.copy(needsReview = true))
            true
        } else {
            false
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun WorkType.toEntityString(): String = when (this) {
        WorkType.MOVIE -> "MOVIE"
        WorkType.SERIES -> "SERIES"
        WorkType.EPISODE -> "EPISODE"
        WorkType.CLIP -> "CLIP"
        WorkType.LIVE_CHANNEL -> "LIVE"
        WorkType.AUDIOBOOK -> "AUDIOBOOK"
        WorkType.MUSIC_TRACK -> "MUSIC"
        WorkType.UNKNOWN -> "UNKNOWN"
    }
}
