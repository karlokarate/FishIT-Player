package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxSourceAccountRepository
import com.fishit.player.core.persistence.obx.NX_SourceAccount

/**
 * Mapper between NX_SourceAccount entity and NxSourceAccountRepository.SourceAccount domain model.
 */

internal fun NX_SourceAccount.toDomain(): NxSourceAccountRepository.SourceAccount =
    NxSourceAccountRepository.SourceAccount(
        accountKey = accountKey,
        sourceType = SourceTypeMapper.toSourceType(sourceType),
        label = displayName,
        status =
            when (syncStatus) {
                "OK", "PENDING" -> NxSourceAccountRepository.AccountStatus.ACTIVE
                "ERROR" -> NxSourceAccountRepository.AccountStatus.ERROR
                else -> NxSourceAccountRepository.AccountStatus.DISABLED
            },
        lastErrorCode = syncError?.take(50), // Truncate for code field
        lastErrorMessage = syncError,
        createdAtMs = createdAt,
        updatedAtMs = updatedAt,
    )

internal fun NxSourceAccountRepository.SourceAccount.toEntity(): NX_SourceAccount =
    NX_SourceAccount(
        accountKey = accountKey,
        sourceType = SourceTypeMapper.toEntityString(sourceType),
        displayName = label,
        isActive = status == NxSourceAccountRepository.AccountStatus.ACTIVE,
        syncStatus =
            when (status) {
                NxSourceAccountRepository.AccountStatus.ACTIVE -> "OK"
                NxSourceAccountRepository.AccountStatus.ERROR -> "ERROR"
                NxSourceAccountRepository.AccountStatus.DISABLED -> "DISABLED"
            },
        syncError = lastErrorMessage,
        createdAt = if (createdAtMs > 0) createdAtMs else System.currentTimeMillis(),
        updatedAt = if (updatedAtMs > 0) updatedAtMs else System.currentTimeMillis(),
    )
