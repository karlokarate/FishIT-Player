/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - NOT for UI hot paths.
 */
package com.fishit.player.core.model.repository

interface NxWorkAuthorityDiagnostics {
    suspend fun countAll(): Long
    suspend fun findConflicts(limit: Int = 200): List<NxWorkAuthorityRepository.AuthorityRef>
}

