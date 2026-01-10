/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - NOT for UI hot paths (feature layer).
 * - Intended for verifier workers and debug tooling.
 * - validateState() must be pure (no IO).
 * - Remove this header after diagnostics impl exists.
 */
package com.fishit.player.core.model.repository

import com.fishit.player.core.model.userstate.WorkUserState

interface NxWorkUserStateDiagnostics {
    suspend fun countAll(): Long
    suspend fun countByProfile(profileKey: String): Long
    suspend fun findOrphanedStates(limit: Int = 200): List<WorkUserState>
    suspend fun findInvalidRatings(limit: Int = 200): List<WorkUserState>
    suspend fun findDuplicateStates(limit: Int = 200): List<WorkUserState>
    fun validateState(state: WorkUserState): List<String>
}

