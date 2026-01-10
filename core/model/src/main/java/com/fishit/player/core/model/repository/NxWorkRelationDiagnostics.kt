/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - Diagnostics are NOT allowed in UI hot paths (feature/*).
 * - Intended for verifier workers and debug tooling.
 * - Remove this block after infra/data-nx diagnostics implementation is available.
 */
package com.fishit.player.core.model.repository

/**
 * Diagnostics for work relations.
 *
 * Not for UI hot paths.
 */
interface NxWorkRelationDiagnostics {
    suspend fun countAll(): Long

    suspend fun findOrphanedRelations(limit: Int = 200): List<NxWorkRelationRepository.Relation>

    suspend fun findDuplicateRelations(limit: Int = 200): List<NxWorkRelationRepository.Relation>
}