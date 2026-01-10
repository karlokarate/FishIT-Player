/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - NOT for UI hot paths.
 * - Used for cleanup/verifier/debug screens.
 */
package com.fishit.player.core.model.repository

interface NxWorkRuntimeStateDiagnostics {
    suspend fun countAll(): Long

    suspend fun findExpired(
        ttlBeforeMs: Long,
        limit: Int = 200,
    ): List<NxWorkRuntimeStateRepository.RuntimeState>

    suspend fun deleteExpired(ttlBeforeMs: Long): Long
}
