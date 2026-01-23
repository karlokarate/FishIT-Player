package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxWorkEmbeddingRepository
import com.fishit.player.core.persistence.obx.NX_WorkEmbedding

/**
 * Mapper between NX_WorkEmbedding entity and NxWorkEmbeddingRepository.WorkEmbedding domain model.
 */

internal fun NX_WorkEmbedding.toDomain(): NxWorkEmbeddingRepository.WorkEmbedding =
    NxWorkEmbeddingRepository.WorkEmbedding(
        workKey = workKey,
        embeddingModel = embeddingType,
        embeddingVersion = dimension, // Using dimension as version proxy
        embedding = embeddingVector.split(",").mapNotNull { it.trim().toFloatOrNull() },
        updatedAtMs = generatedAt,
    )

internal fun NxWorkEmbeddingRepository.WorkEmbedding.toEntity(): NX_WorkEmbedding =
    NX_WorkEmbedding(
        workKey = workKey,
        embeddingType = embeddingModel,
        embeddingVector = embedding.joinToString(","),
        dimension = embeddingVersion, // Using version as dimension proxy
        generatedAt = if (updatedAtMs > 0) updatedAtMs else System.currentTimeMillis(),
    )
