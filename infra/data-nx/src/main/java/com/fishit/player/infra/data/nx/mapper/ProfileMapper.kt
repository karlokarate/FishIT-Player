package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxProfileRepository
import com.fishit.player.core.persistence.obx.NX_Profile

/**
 * Mapper between NX_Profile entity and NxProfileRepository.Profile domain model.
 */

internal fun NX_Profile.toDomain(): NxProfileRepository.Profile =
    NxProfileRepository.Profile(
        profileKey = profileKey,
        displayName = name,
        avatarKey = avatarUrl,
        isKids = profileType == "KIDS",
        createdAtMs = createdAt,
        updatedAtMs = lastUsedAt ?: createdAt,
        isDeleted = false, // Entity doesn't have soft delete flag
    )

internal fun NxProfileRepository.Profile.toEntity(): NX_Profile =
    NX_Profile(
        profileKey = profileKey,
        profileType = if (isKids) "KIDS" else "MAIN",
        name = displayName,
        avatarUrl = avatarKey,
        isActive = false,
        isPinProtected = false,
        pinHash = null,
        createdAt = if (createdAtMs > 0) createdAtMs else System.currentTimeMillis(),
        lastUsedAt = updatedAtMs.takeIf { it > 0 },
    )
