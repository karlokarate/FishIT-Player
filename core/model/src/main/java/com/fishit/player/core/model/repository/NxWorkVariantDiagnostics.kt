/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - Diagnostics are NOT allowed in UI hot paths (feature layer).
 * - Intended for verifier workers and debug tooling.
 * - Remove this block after infra/data-nx diagnostics implementation is available.
 */
package com.fishit.player.core.model.repository

/**
 * Diagnostics for variants.
 *
 * Not for UI hot paths.
 */
interface NxWorkVariantDiagnostics {
    suspend fun countAll(): Long

    suspend fun findOrphanedVariants(limit: Int = 200): List<NxWorkVariantRepository.Variant>

    suspend fun findVariantsMissingPlaybackHints(limit: Int = 200): List<NxWorkVariantRepository.Variant>

    /**
     * Useful for "why no playback" debugging.
     */
    suspend fun countVariantsMissingPlaybackHints(): Long
}
