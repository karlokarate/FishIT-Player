/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only: must not reference ObjectBox entities or BoxStore.
 * - Implementation maps between:
 *     Domain: NxWorkRepository.Work
 *     Persistence: NX_Work entity (infra/data-nx)
 * - SSOT: UI reads ONLY from NX graph via repositories (no legacy Obx* reads).
 * - Keep this MVP surface stable and small. Add diagnostics to NxWorkDiagnostics only.
 * - Remove this block after infra/data-nx implementation + integration tests are green.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

/**
 * MVP repository for canonical Works (UI SSOT).
 *
 * Domain model only (no persistence annotations).
 */
interface NxWorkRepository {
    enum class WorkType {
        MOVIE,
        SERIES,
        EPISODE,
        CLIP,
        LIVE_CHANNEL,
        AUDIOBOOK,
        MUSIC_TRACK,
        UNKNOWN,
    }

    enum class RecognitionState {
        CONFIRMED,
        HEURISTIC,
        NEEDS_REVIEW,
        UNPLAYABLE,
    }

    /**
     * Minimal Work model needed for UI rendering and stable navigation.
     * Anything frequently updated must live in dedicated state entities/repos.
     */
    data class Work(
        val workKey: String,
        val type: WorkType,
        val displayTitle: String,
        val sortTitle: String = displayTitle,
        val titleNormalized: String = displayTitle.lowercase(),
        val year: Int? = null,
        val runtimeMs: Long? = null,
        val posterRef: String? = null,
        val backdropRef: String? = null,
        val rating: Double? = null, // 0..10 if present
        val genres: String? = null,
        val plot: String? = null,
        val recognitionState: RecognitionState = RecognitionState.HEURISTIC,
        val createdAtMs: Long = 0L,
        val updatedAtMs: Long = 0L,
        val isDeleted: Boolean = false,
    )

    // ──────────────────────────────────────────────────────────────────────
    // Single item
    // ──────────────────────────────────────────────────────────────────────

    suspend fun get(workKey: String): Work?

    fun observe(workKey: String): Flow<Work?>

    // ──────────────────────────────────────────────────────────────────────
    // Lists (UI-critical)
    // ──────────────────────────────────────────────────────────────────────

    fun observeByType(
        type: WorkType,
        limit: Int = 200,
    ): Flow<List<Work>>

    fun observeRecentlyUpdated(limit: Int = 50): Flow<List<Work>>

    fun observeNeedsReview(limit: Int = 200): Flow<List<Work>>

    suspend fun searchByTitle(
        queryNormalized: String,
        limit: Int = 50,
    ): List<Work>

    // ──────────────────────────────────────────────────────────────────────
    // Writes (MVP)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Upsert by workKey (idempotent).
     */
    suspend fun upsert(work: Work): Work

    suspend fun upsertBatch(works: List<Work>): List<Work>

    /**
     * Soft delete preferred. Implementation sets isDeleted=true and updates updatedAtMs.
     */
    suspend fun softDelete(workKey: String): Boolean
}
