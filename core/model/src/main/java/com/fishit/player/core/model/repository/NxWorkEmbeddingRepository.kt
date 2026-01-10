/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only.
 * - Embeddings stored separately to avoid rewriting NX_Work frequently.
 * - Vector search (KNN) can be added later; MVP is storage + retrieval.
 * - Implementation maps to NX_WorkEmbedding entity in infra/data-nx.
 */
package com.fishit.player.core.model.repository

interface NxWorkEmbeddingRepository {

    data class WorkEmbedding(
        val workKey: String,
        val embeddingModel: String,
        val embeddingVersion: Int,
        val embedding: FloatArray,
        val updatedAtMs: Long = 0L,
    )

    suspend fun get(workKey: String, embeddingModel: String, embeddingVersion: Int): WorkEmbedding?

    suspend fun upsert(embedding: WorkEmbedding): WorkEmbedding

    suspend fun delete(workKey: String, embeddingModel: String, embeddingVersion: Int): Boolean
}

