/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - NOT for UI hot paths.
 */
package com.fishit.player.core.model.repository

interface NxWorkEmbeddingDiagnostics {
    suspend fun countAll(): Long

    suspend fun findMissingEmbedding(model: String, version: Int, limit: Int = 200): List<String> // workKeys
}
