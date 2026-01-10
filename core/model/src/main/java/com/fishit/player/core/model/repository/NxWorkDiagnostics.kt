/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - Diagnostics are NOT allowed in UI hot paths (feature/*).
 * - Intended for debug screens and verifier workers.
 * - Implementation may join against other repos internally (e.g., source refs / variants).
 * - Remove this block after diagnostics implementation + debug screen wiring exists.
 */
package com.fishit.player.core.model.repository

/**
 * Diagnostics for canonical Works.
 *
 * Not for UI hot paths.
 */
interface NxWorkDiagnostics {
    suspend fun countAll(): Long
    suspend fun countByType(type: NxWorkRepository.WorkType): Long

    /**
     * Works that violate SSOT invariants.
     * - missing sources / variants are common "broken graph" issues.
     */
    suspend fun findWorksMissingSources(limit: Int = 200): List<NxWorkRepository.Work>
    suspend fun findWorksMissingVariants(limit: Int = 200): List<NxWorkRepository.Work>

    /**
     * Helps measure how much manual cleanup/debug UI will show.
     */
    suspend fun countNeedsReview(): Long
}