/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only: must not reference ObjectBox entities or BoxStore.
 * - sourceKey SSOT format (MANDATORY):
 *     "src:<sourceType>:<accountKey>:<sourceItemKind>:<sourceItemKey>"
 * - accountKey is REQUIRED (multi-account ready).
 * - Keep this MVP surface small. Add health/orphan scans to NxWorkSourceRefDiagnostics only.
 * - Remove this block after infra/data-nx implementation + integration tests are green.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

/**
 * MVP repository for source references linking Works to pipeline/account items.
 */
interface NxWorkSourceRefRepository {

    enum class SourceType {
        TELEGRAM,
        XTREAM,
        IO,
        LOCAL,
        PLEX,
        UNKNOWN,
    }

    enum class SourceItemKind {
        VOD,
        SERIES,
        EPISODE,
        LIVE,
        FILE,
        UNKNOWN,
    }

    enum class AvailabilityState {
        ACTIVE,
        MISSING,
        REMOVED,
    }

    data class SourceRef(
        val sourceKey: String,
        val workKey: String,
        val sourceType: SourceType,
        val accountKey: String,
        val sourceItemKind: SourceItemKind,
        val sourceItemKey: String,
        val sourceTitle: String? = null,
        val firstSeenAtMs: Long = 0L,
        val lastSeenAtMs: Long = 0L,
        val availability: AvailabilityState = AvailabilityState.ACTIVE,
        val note: String? = null,
    )

    // ──────────────────────────────────────────────────────────────────────
    // Reads (UI/Playback critical)
    // ──────────────────────────────────────────────────────────────────────

    suspend fun getBySourceKey(sourceKey: String): SourceRef?

    fun observeByWorkKey(workKey: String): Flow<List<SourceRef>>

    suspend fun findByWorkKey(workKey: String): List<SourceRef>

    suspend fun findByAccount(
        sourceType: SourceType,
        accountKey: String,
        limit: Int = 500,
    ): List<SourceRef>

    // ──────────────────────────────────────────────────────────────────────
    // Writes (MVP)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Upsert by sourceKey (idempotent). Implementation must validate:
     * - accountKey not blank
     * - sourceKey follows SSOT format
     */
    suspend fun upsert(sourceRef: SourceRef): SourceRef

    suspend fun upsertBatch(sourceRefs: List<SourceRef>): List<SourceRef>

    suspend fun updateLastSeen(sourceKey: String, lastSeenAtMs: Long): Boolean
}