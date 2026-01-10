/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - NOT for UI hot paths.
 */
package com.fishit.player.core.model.repository

interface NxProfileDiagnostics {
    suspend fun countAll(): Long
    suspend fun findDeleted(limit: Int = 200): List<NxProfileRepository.Profile>
}

