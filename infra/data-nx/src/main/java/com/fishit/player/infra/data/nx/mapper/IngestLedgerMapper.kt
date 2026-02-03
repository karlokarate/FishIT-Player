package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxIngestLedgerRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.persistence.obx.NX_IngestLedger

/**
 * Mapper between NX_IngestLedger entity and NxIngestLedgerRepository.LedgerEntry domain model.
 */

internal fun NX_IngestLedger.toDomain(): NxIngestLedgerRepository.LedgerEntry =
    NxIngestLedgerRepository.LedgerEntry(
        ledgerKey = sourceKey, // Using sourceKey as ledgerKey
        sourceType = SourceTypeMapper.toSourceType(sourceType),
        accountKey = accountKey,
        sourceItemKey = sourceKey.substringAfterLast(':'), // Extract item part from sourceKey
        state = when (decision) {
            "ACCEPTED" -> NxIngestLedgerRepository.LedgerState.ACCEPTED
            "REJECTED" -> NxIngestLedgerRepository.LedgerState.REJECTED
            else -> NxIngestLedgerRepository.LedgerState.SKIPPED
        },
        reason = when (reasonCode) {
            "NOT_PLAYABLE" -> NxIngestLedgerRepository.ReasonCode.NOT_PLAYABLE
            "DURATION_LT_60S" -> NxIngestLedgerRepository.ReasonCode.DURATION_LT_60S
            "UNSUPPORTED_CONTAINER" -> NxIngestLedgerRepository.ReasonCode.UNSUPPORTED_CONTAINER
            "MISSING_REQUIRED_HINTS" -> NxIngestLedgerRepository.ReasonCode.MISSING_REQUIRED_HINTS
            else -> NxIngestLedgerRepository.ReasonCode.UNKNOWN
        },
        firstSeenAtMs = processedAt,
        lastSeenAtMs = processedAt,
        workKey = resultWorkKey,
        ttlUntilMs = null, // Entity doesn't store TTL
    )

internal fun NxIngestLedgerRepository.LedgerEntry.toEntity(): NX_IngestLedger =
    NX_IngestLedger(
        sourceKey = ledgerKey,
        sourceType = SourceTypeMapper.toEntityString(sourceType),
        accountKey = accountKey,
        decision = when (state) {
            NxIngestLedgerRepository.LedgerState.ACCEPTED -> "ACCEPTED"
            NxIngestLedgerRepository.LedgerState.REJECTED -> "REJECTED"
            NxIngestLedgerRepository.LedgerState.SKIPPED -> "SKIPPED"
        },
        reasonCode = reason.name,
        reasonDetail = null,
        resultWorkKey = workKey,
        createdNewWork = workKey != null,
        rawTitle = null,
        classifiedWorkType = null,
        processedAt = if (lastSeenAtMs > 0) lastSeenAtMs else System.currentTimeMillis(),
    )
