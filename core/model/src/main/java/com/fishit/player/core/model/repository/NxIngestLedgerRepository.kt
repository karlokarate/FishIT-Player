/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only.
 * - Ledger enforces "no silent drops": every ingest candidate must write exactly one entry.
 * - Implementation maps to NX_IngestLedger entity (infra/data-nx).
 * - Pipelines must NOT write ledger directly; orchestration layer writes it.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

interface NxIngestLedgerRepository {
    enum class LedgerState { ACCEPTED, REJECTED, SKIPPED }

    enum class ReasonCode {
        NOT_PLAYABLE,
        DURATION_LT_60S,
        UNSUPPORTED_CONTAINER,
        MISSING_REQUIRED_HINTS,
        UNKNOWN,
    }

    data class LedgerEntry(
        val ledgerKey: String, // led:<sourceType>:<accountKey>:<sourceItemKey>
        val sourceType: NxWorkSourceRefRepository.SourceType,
        val accountKey: String,
        val sourceItemKey: String,
        val state: LedgerState,
        val reason: ReasonCode = ReasonCode.UNKNOWN,
        val firstSeenAtMs: Long = 0L,
        val lastSeenAtMs: Long = 0L,
        val workKey: String? = null, // present when ACCEPTED
        val ttlUntilMs: Long? = null,
    )

    suspend fun get(ledgerKey: String): LedgerEntry?

    suspend fun upsert(entry: LedgerEntry): LedgerEntry

    suspend fun upsertBatch(entries: List<LedgerEntry>): List<LedgerEntry>

    /**
     * Fast-path skip check (especially useful for Telegram candidates).
     */
    suspend fun shouldSkip(
        ledgerKey: String,
        nowMs: Long,
    ): Boolean

    fun observeRecentRejected(limit: Int = 200): Flow<List<LedgerEntry>>
}
