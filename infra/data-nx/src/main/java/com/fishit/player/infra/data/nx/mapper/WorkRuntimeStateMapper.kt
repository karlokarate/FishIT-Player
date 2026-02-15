package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxWorkRuntimeStateRepository
import com.fishit.player.core.persistence.obx.NX_WorkRuntimeState

/**
 * Mapper between NX_WorkRuntimeState entity and NxWorkRuntimeStateRepository.RuntimeState domain model.
 *
 * Note: Entity has a different structure (stateType, progress fields) than domain model (availability, EPG fields).
 * This mapper adapts between the two paradigms.
 */

internal fun NX_WorkRuntimeState.toDomain(): NxWorkRuntimeStateRepository.RuntimeState =
    NxWorkRuntimeStateRepository.RuntimeState(
        workKey = workKey,
        isAvailable = stateType != "ERROR" && stateType != "UNAVAILABLE",
        lastProbeAtMs = updatedAt.takeIf { it > 0 },
        lastErrorCode = errorCode,
        ttlUntilMs = null, // Entity doesn't store TTL
        // EPG-like fields - not stored in current entity schema
        nowTitle = null,
        nowStartMs = null,
        nowEndMs = null,
        nextTitle = null,
        nextStartMs = null,
        nextEndMs = null,
    )

internal fun NxWorkRuntimeStateRepository.RuntimeState.toEntity(): NX_WorkRuntimeState =
    NX_WorkRuntimeState(
        workKey = workKey,
        stateType =
            when {
                lastErrorCode != null -> "ERROR"
                !isAvailable -> "UNAVAILABLE"
                else -> "AVAILABLE"
            },
        errorCode = lastErrorCode,
        errorMessage = lastErrorCode?.let { "Error: $it" },
        updatedAt = lastProbeAtMs ?: System.currentTimeMillis(),
    )
