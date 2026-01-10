/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - NOT for UI hot paths.
 */
package com.fishit.player.core.model.repository

interface NxSourceAccountDiagnostics {
    suspend fun countAll(): Long

    suspend fun countByStatus(status: NxSourceAccountRepository.AccountStatus): Long
}
