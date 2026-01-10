/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - NOT for UI hot paths.
 * - Used to detect redirect loops/chains.
 */
package com.fishit.player.core.model.repository

interface NxWorkRedirectDiagnostics {
    suspend fun countAll(): Long
    suspend fun findLoops(limit: Int = 50): List<List<String>> // chains of workKeys
}

