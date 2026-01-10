/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - NOT for UI hot paths.
 */
package com.fishit.player.core.model.repository

interface NxProfileUsageDiagnostics {
    suspend fun countAll(): Long
    suspend fun findAnomalies(profileKey: String, limit: Int = 200): List<NxProfileUsageRepository.UsageDay>
}


