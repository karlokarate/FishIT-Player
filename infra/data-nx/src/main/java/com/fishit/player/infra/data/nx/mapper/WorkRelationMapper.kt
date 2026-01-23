package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxWorkRelationRepository
import com.fishit.player.core.persistence.obx.NX_WorkRelation

/**
 * Mapper between NX_WorkRelation entity and NxWorkRelationRepository.Relation domain model.
 *
 * Note: parentWork and childWork ToOne relations must be set externally after mapping.
 */

internal fun NX_WorkRelation.toDomain(
    parentWorkKey: String,
    childWorkKey: String,
): NxWorkRelationRepository.Relation = NxWorkRelationRepository.Relation(
    parentWorkKey = parentWorkKey,
    childWorkKey = childWorkKey,
    relationType = when (relationType) {
        "SERIES_EPISODE" -> NxWorkRelationRepository.RelationType.SERIES_EPISODE
        "RELATED" -> NxWorkRelationRepository.RelationType.RELATED
        else -> NxWorkRelationRepository.RelationType.UNKNOWN
    },
    orderIndex = sortOrder.takeIf { it > 0 },
    seasonNumber = season,
    episodeNumber = episode,
    createdAtMs = createdAt,
    updatedAtMs = createdAt, // Entity doesn't have updatedAt, use createdAt
)

internal fun NxWorkRelationRepository.Relation.toEntity(): NX_WorkRelation = NX_WorkRelation(
    relationType = when (relationType) {
        NxWorkRelationRepository.RelationType.SERIES_EPISODE -> "SERIES_EPISODE"
        NxWorkRelationRepository.RelationType.RELATED -> "RELATED"
        NxWorkRelationRepository.RelationType.UNKNOWN -> "UNKNOWN"
    },
    sortOrder = orderIndex ?: 0,
    season = seasonNumber,
    episode = episodeNumber,
    createdAt = if (createdAtMs > 0) createdAtMs else System.currentTimeMillis(),
)
