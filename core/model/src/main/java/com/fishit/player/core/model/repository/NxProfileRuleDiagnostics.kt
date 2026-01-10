/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - NOT for UI hot paths.
 */
package com.fishit.player.core.model.repository

interface NxProfileRuleDiagnostics {
    suspend fun countAll(): Long

    suspend fun findExpired(
        nowMs: Long,
        limit: Int = 200,
    ): List<NxProfileRuleRepository.ProfileRule>

    suspend fun deleteExpired(nowMs: Long): Long
}
