/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - Diagnostics are NOT allowed in UI hot paths (feature layer).
 * - Intended for verifier workers and debug tooling.
 * - Remove this block after infra/data-nx diagnostics implementation is available.
 */
package com.fishit.player.core.model.repository

/**
 * Diagnostics for source references.
 *
 * Not for UI hot paths.
 */
interface NxWorkSourceRefDiagnostics {
    suspend fun countAll(): Long

    /**
     * Source refs that look malformed (SSOT violations).
     */
    suspend fun findMissingAccountKey(limit: Int = 200): List<NxWorkSourceRefRepository.SourceRef>
    suspend fun findInvalidSourceKeyFormat(limit: Int = 200): List<NxWorkSourceRefRepository.SourceRef>

    /**
     * Orphans: point to workKey that does not exist anymore.
     * Implementation may join against NxWorkRepository internally.
     */
    suspend fun findOrphaned(limit: Int = 200): List<NxWorkSourceRefRepository.SourceRef>
}
