/**
 * Maps between NX_WorkSourceRef entity and NxWorkSourceRefRepository.SourceRef domain model.
 */
package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.AvailabilityState
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceItemKind
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceRef
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef

/**
 * Converts NX_WorkSourceRef entity to SourceRef domain model.
 */
fun NX_WorkSourceRef.toDomain(): SourceRef = SourceRef(
    sourceKey = sourceKey,
    workKey = work.target?.workKey ?: "",
    sourceType = SourceTypeMapper.toSourceType(sourceType),
    accountKey = accountKey,
    sourceItemKind = deriveSourceItemKind(),
    sourceItemKey = sourceId,
    sourceTitle = rawTitle,
    firstSeenAtMs = discoveredAt,
    lastSeenAtMs = lastSeenAt,
    sourceLastModifiedMs = sourceLastModifiedMs,
    availability = AvailabilityState.ACTIVE, // Entity doesn't track availability yet
    note = null,
    epgChannelId = epgChannelId,
    tvArchive = tvArchive,
    tvArchiveDuration = tvArchiveDuration,
)

/**
 * Converts SourceRef domain model to NX_WorkSourceRef entity.
 * Note: Does NOT set the ToOne relationship - caller must do that.
 */
fun SourceRef.toEntity(existingEntity: NX_WorkSourceRef? = null): NX_WorkSourceRef {
    val entity = existingEntity ?: NX_WorkSourceRef()
    return entity.copy(
        id = existingEntity?.id ?: 0,
        sourceKey = sourceKey,
        sourceType = SourceTypeMapper.toEntityString(sourceType),
        accountKey = accountKey,
        sourceId = sourceItemKey,
        rawTitle = sourceTitle,
        epgChannelId = epgChannelId,
        tvArchive = tvArchive,
        tvArchiveDuration = tvArchiveDuration,
        discoveredAt = if (existingEntity == null) firstSeenAtMs.takeIf { it > 0 } ?: System.currentTimeMillis() else existingEntity.discoveredAt,
        lastSeenAt = System.currentTimeMillis(),
        sourceLastModifiedMs = sourceLastModifiedMs ?: existingEntity?.sourceLastModifiedMs,
    )
}

/**
 * Derives SourceItemKind from entity fields.
 * Falls back to UNKNOWN if cannot determine.
 */
private fun NX_WorkSourceRef.deriveSourceItemKind(): SourceItemKind {
    return when {
        xtreamStreamId != null -> SourceItemKind.VOD // Could also be LIVE - need more context
        telegramMessageId != null -> SourceItemKind.FILE
        else -> SourceItemKind.UNKNOWN
    }
}
