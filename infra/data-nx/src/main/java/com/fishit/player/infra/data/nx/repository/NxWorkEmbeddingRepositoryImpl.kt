package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkEmbeddingRepository
import com.fishit.player.core.model.repository.NxWorkEmbeddingRepository.WorkEmbedding
import com.fishit.player.core.persistence.obx.NX_WorkEmbedding
import com.fishit.player.core.persistence.obx.NX_WorkEmbedding_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxWorkEmbeddingRepository].
 *
 * Stores vector embeddings for semantic search.
 * MVP: storage and retrieval only - KNN search comes later.
 */
@Singleton
class NxWorkEmbeddingRepositoryImpl @Inject constructor(
    boxStore: BoxStore,
) : NxWorkEmbeddingRepository {

    private val box: Box<NX_WorkEmbedding> = boxStore.boxFor(NX_WorkEmbedding::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun get(
        workKey: String,
        embeddingModel: String,
        embeddingVersion: Int,
    ): WorkEmbedding? = withContext(Dispatchers.IO) {
        // Query by workKey and embeddingType, filter by dimension in memory
        box.query(NX_WorkEmbedding_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .find()
            .filter { it.embeddingType == embeddingModel && it.dimension == embeddingVersion }
            .firstOrNull()
            ?.toDomain()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun upsert(embedding: WorkEmbedding): WorkEmbedding = withContext(Dispatchers.IO) {
        val existing = box.query(NX_WorkEmbedding_.workKey.equal(embedding.workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .find()
            .filter { it.embeddingType == embedding.embeddingModel && it.dimension == embedding.embeddingVersion }
            .firstOrNull()

        val entity = if (existing != null) {
            existing.apply {
                embeddingVector = embedding.embedding.joinToString(",")
                generatedAt = System.currentTimeMillis()
            }
        } else {
            embedding.toEntity()
        }

        box.put(entity)
        entity.toDomain()
    }

    override suspend fun delete(
        workKey: String,
        embeddingModel: String,
        embeddingVersion: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        val toDelete = box.query(NX_WorkEmbedding_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .find()
            .filter { it.embeddingType == embeddingModel && it.dimension == embeddingVersion }
            .firstOrNull()

        if (toDelete != null) {
            box.remove(toDelete.id)
            true
        } else {
            false
        }
    }
}
