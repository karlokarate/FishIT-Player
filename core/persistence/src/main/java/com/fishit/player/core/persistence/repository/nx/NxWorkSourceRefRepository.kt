package com.fishit.player.core.persistence.repository.nx

import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.SourceType
import kotlinx.coroutines.flow.Flow

/**
 * Repository for NX_WorkSourceRef entities - links works to pipeline sources.
 *
 * **SSOT Contract:** docs/v2/NX_SSOT_CONTRACT.md
 * **Roadmap:** docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
 *
 * ## Invariants (BINDING)
 * - INV-04: sourceKey is globally unique across all accounts
 * - INV-10: Every NX_Work has ≥1 NX_WorkSourceRef
 * - INV-13: accountKey is MANDATORY in all NX_WorkSourceRef
 *
 * ## Key Format
 * sourceKey: `<sourceType>:<accountKey>:<sourceId>`
 *
 * ## Architectural Note
 * This repository interface is in `core/persistence/repository/nx/` because
 * NX entities ARE the domain model (SSOT). See NxWorkRepository for full explanation.
 *
 * @see NX_WorkSourceRef
 * @see NxKeyGenerator
 * @see NxWorkRepository
 */
interface NxWorkSourceRefRepository {

    // ═══════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find source ref by unique sourceKey.
     *
     * @param sourceKey Source key (e.g., "telegram:tg:123:chat:-100:msg:456")
     * @return SourceRef if found, null otherwise
     */
    suspend fun findBySourceKey(sourceKey: String): NX_WorkSourceRef?

    /**
     * Find source ref by ObjectBox ID.
     *
     * @param id ObjectBox entity ID
     * @return SourceRef if found, null otherwise
     */
    suspend fun findById(id: Long): NX_WorkSourceRef?

    /**
     * Insert or update a source ref.
     *
     * **INV-13:** accountKey must be non-blank.
     *
     * @param sourceRef Source ref to upsert
     * @return Updated source ref with ID populated
     * @throws IllegalArgumentException if accountKey is blank
     */
    suspend fun upsert(sourceRef: NX_WorkSourceRef): NX_WorkSourceRef

    /**
     * Delete source ref by sourceKey.
     *
     * @param sourceKey Source key to delete
     * @return true if deleted, false if not found
     */
    suspend fun delete(sourceKey: String): Boolean

    /**
     * Delete source ref by ID.
     *
     * @param id ObjectBox entity ID
     * @return true if deleted, false if not found
     */
    suspend fun deleteById(id: Long): Boolean

    // ═══════════════════════════════════════════════════════════════════════
    // Work Relationship Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find all source refs for a work.
     *
     * @param workKey Work key to find sources for
     * @return List of source refs linked to the work
     */
    suspend fun findByWorkKey(workKey: String): List<NX_WorkSourceRef>

    /**
     * Find all source refs for a work by work ID.
     *
     * @param workId ObjectBox work ID
     * @return List of source refs linked to the work
     */
    suspend fun findByWorkId(workId: Long): List<NX_WorkSourceRef>

    /**
     * Link a source ref to a work.
     *
     * @param sourceKey Source key
     * @param workKey Work key to link to
     * @return true if linked successfully
     */
    suspend fun linkToWork(sourceKey: String, workKey: String): Boolean

    /**
     * Unlink a source ref from its work.
     *
     * ⚠️ WARNING: This may violate INV-10 if it's the last source ref.
     *
     * @param sourceKey Source key to unlink
     * @return true if unlinked, false if not found
     */
    suspend fun unlinkFromWork(sourceKey: String): Boolean

    // ═══════════════════════════════════════════════════════════════════════
    // Source Type & Account Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find all source refs by source type.
     *
     * @param sourceType Source type filter (TELEGRAM, XTREAM, LOCAL, PLEX)
     * @param limit Maximum results (default 100)
     * @param offset Pagination offset (default 0)
     * @return List of source refs of given type
     */
    suspend fun findBySourceType(
        sourceType: SourceType,
        limit: Int = 100,
        offset: Int = 0
    ): List<NX_WorkSourceRef>

    /**
     * Find all source refs for an account.
     *
     * @param accountKey Account key to filter by
     * @param limit Maximum results (default 100)
     * @return List of source refs for the account
     */
    suspend fun findByAccountKey(accountKey: String, limit: Int = 100): List<NX_WorkSourceRef>

    /**
     * Find source refs by source type AND account.
     *
     * @param sourceType Source type filter
     * @param accountKey Account key filter
     * @param limit Maximum results (default 100)
     * @return List of matching source refs
     */
    suspend fun findBySourceTypeAndAccount(
        sourceType: SourceType,
        accountKey: String,
        limit: Int = 100
    ): List<NX_WorkSourceRef>

    // ═══════════════════════════════════════════════════════════════════════
    // Telegram-Specific Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find source ref by Telegram chat and message ID.
     *
     * @param chatId Telegram chat ID
     * @param messageId Telegram message ID
     * @return SourceRef if found, null otherwise
     */
    suspend fun findByTelegramIds(chatId: Long, messageId: Long): NX_WorkSourceRef?

    /**
     * Find all source refs for a Telegram chat.
     *
     * @param chatId Telegram chat ID
     * @param limit Maximum results (default 100)
     * @return List of source refs from the chat
     */
    suspend fun findByTelegramChatId(chatId: Long, limit: Int = 100): List<NX_WorkSourceRef>

    // ═══════════════════════════════════════════════════════════════════════
    // Xtream-Specific Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find source ref by Xtream stream ID and account.
     *
     * @param accountKey Xtream account key
     * @param streamId Xtream stream ID
     * @return SourceRef if found, null otherwise
     */
    suspend fun findByXtreamStreamId(accountKey: String, streamId: Int): NX_WorkSourceRef?

    /**
     * Find all source refs for an Xtream category.
     *
     * @param accountKey Xtream account key
     * @param categoryId Xtream category ID
     * @param limit Maximum results (default 100)
     * @return List of source refs in the category
     */
    suspend fun findByXtreamCategoryId(
        accountKey: String,
        categoryId: Int,
        limit: Int = 100
    ): List<NX_WorkSourceRef>

    // ═══════════════════════════════════════════════════════════════════════
    // Reactive Streams (Flow)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Observe source ref by sourceKey.
     *
     * @param sourceKey Source key to observe
     * @return Flow emitting source ref on changes
     */
    fun observeBySourceKey(sourceKey: String): Flow<NX_WorkSourceRef?>

    /**
     * Observe all source refs for a work.
     *
     * @param workKey Work key to observe sources for
     * @return Flow emitting list on changes
     */
    fun observeByWorkKey(workKey: String): Flow<List<NX_WorkSourceRef>>

    /**
     * Observe source refs by source type.
     *
     * @param sourceType Source type filter
     * @return Flow emitting list on changes
     */
    fun observeBySourceType(sourceType: SourceType): Flow<List<NX_WorkSourceRef>>

    /**
     * Observe source refs for an account.
     *
     * @param accountKey Account key filter
     * @return Flow emitting list on changes
     */
    fun observeByAccountKey(accountKey: String): Flow<List<NX_WorkSourceRef>>

    // ═══════════════════════════════════════════════════════════════════════
    // Counts & Statistics
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Count total source refs.
     *
     * @return Total source ref count
     */
    suspend fun count(): Long

    /**
     * Count source refs by source type.
     *
     * @param sourceType Source type filter
     * @return Count of source refs of given type
     */
    suspend fun countBySourceType(sourceType: SourceType): Long

    /**
     * Count source refs for an account.
     *
     * @param accountKey Account key filter
     * @return Count of source refs for the account
     */
    suspend fun countByAccountKey(accountKey: String): Long

    /**
     * Count source refs for a work.
     *
     * @param workKey Work key
     * @return Count of source refs linked to the work
     */
    suspend fun countByWorkKey(workKey: String): Long

    /**
     * Get source type distribution.
     *
     * @return Map of SourceType to count
     */
    suspend fun getSourceTypeDistribution(): Map<SourceType, Long>

    // ═══════════════════════════════════════════════════════════════════════
    // Batch Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Upsert multiple source refs in a single transaction.
     *
     * **INV-13:** All source refs must have non-blank accountKey.
     *
     * @param sourceRefs List of source refs to upsert
     * @return List of upserted source refs with IDs populated
     * @throws IllegalArgumentException if any accountKey is blank
     */
    suspend fun upsertBatch(sourceRefs: List<NX_WorkSourceRef>): List<NX_WorkSourceRef>

    /**
     * Delete multiple source refs by sourceKeys.
     *
     * @param sourceKeys List of source keys to delete
     * @return Number of source refs deleted
     */
    suspend fun deleteBatch(sourceKeys: List<String>): Int

    /**
     * Find multiple source refs by sourceKeys.
     *
     * @param sourceKeys List of source keys
     * @return List of found source refs
     */
    suspend fun findBySourceKeys(sourceKeys: List<String>): List<NX_WorkSourceRef>

    /**
     * Delete all source refs for a work.
     *
     * ⚠️ WARNING: Use with caution - may orphan the work.
     *
     * @param workKey Work key
     * @return Number of source refs deleted
     */
    suspend fun deleteByWorkKey(workKey: String): Int

    // ═══════════════════════════════════════════════════════════════════════
    // Validation & Health
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if sourceKey exists.
     *
     * @param sourceKey Source key to check
     * @return true if exists, false otherwise
     */
    suspend fun exists(sourceKey: String): Boolean

    /**
     * Find orphaned source refs (not linked to any work).
     *
     * @param limit Maximum results
     * @return List of source refs without a work link
     */
    suspend fun findOrphaned(limit: Int = 100): List<NX_WorkSourceRef>

    /**
     * Find source refs with blank accountKey (INV-13 violations).
     *
     * @param limit Maximum results
     * @return List of source refs violating INV-13
     */
    suspend fun findMissingAccountKey(limit: Int = 100): List<NX_WorkSourceRef>

    /**
     * Update lastSeenAt timestamp for a source ref.
     *
     * @param sourceKey Source key to update
     * @param timestamp New lastSeenAt timestamp (default: now)
     * @return true if updated, false if not found
     */
    suspend fun updateLastSeen(
        sourceKey: String,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean

    /**
     * Find stale source refs (not seen since given timestamp).
     *
     * @param olderThan Timestamp threshold
     * @param limit Maximum results
     * @return List of source refs not seen since threshold
     */
    suspend fun findStale(olderThan: Long, limit: Int = 100): List<NX_WorkSourceRef>
}
