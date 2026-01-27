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
        /** Source-reported last modification timestamp (ms). For incremental sync. */
        val sourceLastModifiedMs: Long? = null,
        val availability: AvailabilityState = AvailabilityState.ACTIVE,
        val note: String? = null,
        // === Live Channel Specific (EPG/Catchup) ===
        /** EPG channel ID for program guide integration */
        val epgChannelId: String? = null,
        /** TV archive flag: 0=no catchup, 1=catchup available */
        val tvArchive: Int = 0,
        /** TV archive duration in days (catchup window) */
        val tvArchiveDuration: Int = 0,
    )

    // ──────────────────────────────────────────────────────────────────────
    // Reads (UI/Playback critical)
    // ──────────────────────────────────────────────────────────────────────

    suspend fun getBySourceKey(sourceKey: String): SourceRef?

    fun observeByWorkKey(workKey: String): Flow<List<SourceRef>>

    suspend fun findByWorkKey(workKey: String): List<SourceRef>

    /**
     * Batch lookup source refs for multiple work keys.
     *
     * **Performance Critical:** Use this instead of calling findByWorkKey() in a loop!
     * Required for efficient Home/Library content loading where thousands of works
     * need their source types determined.
     *
     * @param workKeys List of work keys to lookup
     * @return Map of workKey → List<SourceRef>. Missing keys will not be in the map.
     */
    suspend fun findByWorkKeysBatch(workKeys: List<String>): Map<String, List<SourceRef>>

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

    suspend fun updateLastSeen(
        sourceKey: String,
        lastSeenAtMs: Long,
    ): Boolean

    // ──────────────────────────────────────────────────────────────────────
    // Bulk Delete (for source clearing)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Delete all source refs by source type.
     *
     * Used for clearing entire pipelines (e.g., "clear all Telegram sources").
     *
     * @param sourceType Source type to delete (TELEGRAM, XTREAM, etc.)
     * @return Number of source refs deleted
     */
    suspend fun deleteBySourceType(sourceType: SourceType): Int

    /**
     * Delete all source refs for a specific account.
     *
     * @param accountKey Account key (e.g., "telegram:123456")
     * @return Number of source refs deleted
     */
    suspend fun deleteByAccountKey(accountKey: String): Int

    // ──────────────────────────────────────────────────────────────────────
    // Pattern Search (for legacy ID lookups)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Find source refs by source type and item kind with optional item key pattern.
     *
     * Used for bridging legacy Xtream seriesId-based lookups to NX workKey.
     *
     * @param sourceType Source type (XTREAM, TELEGRAM, etc.)
     * @param itemKind Item kind (SERIES, EPISODE, etc.)
     * @param itemKeyPrefix Optional prefix to match sourceItemKey (e.g., "123" for seriesId)
     * @return List of matching source refs
     */
    suspend fun findBySourceTypeAndKind(
        sourceType: SourceType,
        itemKind: SourceItemKind,
        itemKeyPrefix: String? = null,
    ): List<SourceRef>

    // ──────────────────────────────────────────────────────────────────────
    // Incremental Sync & New Episodes Detection
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Find series that have been updated since a given timestamp.
     *
     * **Use Cases:**
     * - "New Episodes" badge on Series tiles
     * - Incremental sync optimization (only fetch changed series)
     * - Notifications for series updates
     *
     * **Implementation Notes:**
     * - Queries `sourceLastModifiedMs > sinceMs`
     * - Filters by SourceItemKind.SERIES only
     * - Ordered by `sourceLastModifiedMs DESC` (most recent first)
     *
     * @param sinceMs Unix timestamp in milliseconds (typically last sync or last user check)
     * @param sourceType Optional filter by source type (default: all)
     * @param limit Max results to return
     * @return List of SourceRefs for series updated after sinceMs
     */
    suspend fun findSeriesUpdatedSince(
        sinceMs: Long,
        sourceType: SourceType? = null,
        limit: Int = 100,
    ): List<SourceRef>

    /**
     * Get work keys of series with updates since a given timestamp.
     *
     * Convenience method for UI that only needs to check "has new episodes" status.
     *
     * @param sinceMs Unix timestamp in milliseconds
     * @param sourceType Optional filter by source type
     * @return Set of work keys that have series updates
     */
    suspend fun findWorkKeysWithSeriesUpdates(
        sinceMs: Long,
        sourceType: SourceType? = null,
    ): Set<String>
}
