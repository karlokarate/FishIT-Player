/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only: must not reference ObjectBox entities or BoxStore.
 * - variantKey must be deterministic and collision-free.
 *   Recommended format:
 *     "v:<sourceKey>:<variantSignatureHash>"
 * - Playback SSOT: every UI-visible Work must have >=1 Variant with non-empty playbackHints.
 * - Keep this MVP surface small. Move heavy stats to NxWorkVariantDiagnostics only.
 * - Remove this block after infra/data-nx implementation + integration tests are green.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

/**
 * MVP repository for playable variants per source ref.
 */
interface NxWorkVariantRepository {

    data class Variant(
        val variantKey: String,
        val workKey: String,
        val sourceKey: String,

        // selection / UX
        val label: String? = null,
        val isDefault: Boolean = false,

        // optional technical metadata
        val qualityHeight: Int? = null,
        val bitrateKbps: Int? = null,
        val container: String? = null,
        val videoCodec: String? = null,
        val audioCodec: String? = null,
        val audioLang: String? = null,
        val durationMs: Long? = null,

        /**
         * PlaybackHints are source-specific key/value pairs consumed by playback layer.
         * Must be non-empty for a playable variant.
         */
        val playbackHints: Map<String, String> = emptyMap(),

        val createdAtMs: Long = 0L,
        val updatedAtMs: Long = 0L,
        val lastVerifiedAtMs: Long? = null,
    )

    // ──────────────────────────────────────────────────────────────────────
    // Reads (UI/playback critical)
    // ──────────────────────────────────────────────────────────────────────

    suspend fun getByVariantKey(variantKey: String): Variant?

    fun observeByWorkKey(workKey: String): Flow<List<Variant>>

    suspend fun findByWorkKey(workKey: String): List<Variant>

    suspend fun findBySourceKey(sourceKey: String): List<Variant>

    /**
     * Deterministic best-variant selection.
     * Implementation should keep rules stable and documented.
     */
    suspend fun selectBestVariant(
        workKey: String,
        preferredAudioLang: String? = null,
        minHeight: Int? = null,
    ): Variant?

    // ──────────────────────────────────────────────────────────────────────
    // Writes (MVP)
    // ──────────────────────────────────────────────────────────────────────

    suspend fun upsert(variant: Variant): Variant

    suspend fun upsertBatch(variants: List<Variant>): List<Variant>

    suspend fun delete(variantKey: String): Boolean
}