/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - NOT for UI hot paths.
 * - Useful for verifier/debug: ledger coverage, reject reasons distribution, TTL cleanup.
 */
package com.fishit.player.core.model.repository

interface NxIngestLedgerDiagnostics {
    suspend fun countAll(): Long
    suspend fun countByState(state: NxIngestLedgerRepository.LedgerState): Long
    suspend fun countByReason(reason: NxIngestLedgerRepository.ReasonCode): Long
    suspend fun deleteExpired(nowMs: Long): Long
}

