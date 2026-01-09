package com.fishit.player.core.persistence.repository.nx

import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.WorkType
import kotlinx.coroutines.flow.Flow

/**
 * Repository for NX_Work entities - the central UI SSOT.
 *
 * **SSOT Contract:** docs/v2/NX_SSOT_CONTRACT.md
 * **Roadmap:** docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
 *
 * ## Invariants (BINDING)
 * - INV-10: Every NX_Work has ≥1 NX_WorkSourceRef
 * - INV-11: Every NX_Work has ≥1 NX_WorkVariant
 * - INV-12: workKey is globally unique
 *
 * ## Key Format
 * workKey: `<workType>:<canonicalSlug>:<year|LIVE>`
 *
 * ## Architectural Note
 * This repository interface is intentionally placed in `core/persistence/repository/nx/`
 * rather than `core/model/repository/` because:
 * 1. NX entities ARE the domain model (SSOT) - not an implementation detail
 * 2. The interface works directly with `NX_Work` ObjectBox entities
 * 3. Placing it in `core/model` would create a circular dependency
 *    (core/model cannot depend on core/persistence)
 *
 * This differs from legacy repositories (CanonicalMediaRepository, etc.) which:
 * - Define interfaces in core/model using domain types
 * - Implement in core/persistence using Obx* entities for persistence
 *
 * The NX pattern eliminates the domain/persistence split - NX entities
 * are both the domain model AND the persistence model.
 *
 * @see NX_Work
 * @see com.fishit.player.core.persistence.obx.NxKeyGenerator
 */
interface NxWorkRepository {
    // ═══════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find work by unique workKey.
     *
     * @param workKey Canonical work key (e.g., "movie:the-matrix:1999")
     * @return Work if found, null otherwise
     */
    suspend fun findByWorkKey(workKey: String): NX_Work?

    /**
     * Find work by ObjectBox ID.
     *
     * @param id ObjectBox entity ID
     * @return Work if found, null otherwise
     */
    suspend fun findById(id: Long): NX_Work?

    /**
     * Insert or update a work.
     *
     * If work with same workKey exists, updates it.
     * Otherwise creates new work.
     *
     * @param work Work to upsert
     * @return Updated work with ID populated
     */
    suspend fun upsert(work: NX_Work): NX_Work

    /**
     * Delete work by workKey.
     *
     * ⚠️ WARNING: Also deletes related SourceRefs and Variants (cascade).
     *
     * @param workKey Work key to delete
     * @return true if deleted, false if not found
     */
    suspend fun delete(workKey: String): Boolean

    /**
     * Delete work by ID.
     *
     * @param id ObjectBox entity ID
     * @return true if deleted, false if not found
     */
    suspend fun deleteById(id: Long): Boolean

    // ═══════════════════════════════════════════════════════════════════════
    // Authority Lookups (TMDB, IMDB, TVDB)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find work by authority key.
     *
     * @param authorityKey Authority key (e.g., "tmdb:movie:603")
     * @return Work if found, null otherwise
     */
    suspend fun findByAuthorityKey(authorityKey: String): NX_Work?

    /**
     * Find work by TMDB ID.
     *
     * @param tmdbId TMDB numeric ID as string
     * @return Work if found, null otherwise
     */
    suspend fun findByTmdbId(tmdbId: String): NX_Work?

    /**
     * Find work by IMDB ID.
     *
     * @param imdbId IMDB ID (e.g., "tt0133093")
     * @return Work if found, null otherwise
     */
    suspend fun findByImdbId(imdbId: String): NX_Work?

    /**
     * Find work by TVDB ID.
     *
     * @param tvdbId TVDB ID
     * @return Work if found, null otherwise
     */
    suspend fun findByTvdbId(tvdbId: String): NX_Work?

    // ═══════════════════════════════════════════════════════════════════════
    // Search & Filter
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Search works by title (case-insensitive partial match).
     *
     * @param query Search query
     * @param limit Maximum results (default 50)
     * @return List of matching works
     */
    suspend fun searchByTitle(
        query: String,
        limit: Int = 50,
    ): List<NX_Work>

    /**
     * Find works by type.
     *
     * @param workType Work type filter
     * @param limit Maximum results (default 100)
     * @param offset Pagination offset (default 0)
     * @return List of works of given type
     */
    suspend fun findByWorkType(
        workType: WorkType,
        limit: Int = 100,
        offset: Int = 0,
    ): List<NX_Work>

    /**
     * Find works by year.
     *
     * @param year Release year
     * @param limit Maximum results (default 100)
     * @return List of works from given year
     */
    suspend fun findByYear(
        year: Int,
        limit: Int = 100,
    ): List<NX_Work>

    /**
     * Find works needing manual review (classification UNKNOWN).
     *
     * @param limit Maximum results (default 100)
     * @return List of works with needsReview=true
     */
    suspend fun findNeedsReview(limit: Int = 100): List<NX_Work>

    /**
     * Find adult content works.
     *
     * @param limit Maximum results (default 100)
     * @return List of works with isAdult=true
     */
    suspend fun findAdultContent(limit: Int = 100): List<NX_Work>

    /**
     * Find recently added works.
     *
     * @param limit Maximum results (default 50)
     * @return List of works ordered by createdAt DESC
     */
    suspend fun findRecentlyAdded(limit: Int = 50): List<NX_Work>

    /**
     * Find recently updated works.
     *
     * @param limit Maximum results (default 50)
     * @return List of works ordered by updatedAt DESC
     */
    suspend fun findRecentlyUpdated(limit: Int = 50): List<NX_Work>

    // ═══════════════════════════════════════════════════════════════════════
    // Reactive Streams (Flow)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Observe work by workKey.
     *
     * Emits null if work doesn't exist or is deleted.
     *
     * @param workKey Work key to observe
     * @return Flow emitting work on changes
     */
    fun observeByWorkKey(workKey: String): Flow<NX_Work?>

    /**
     * Observe works by type.
     *
     * @param workType Work type filter
     * @return Flow emitting list on changes
     */
    fun observeByWorkType(workType: WorkType): Flow<List<NX_Work>>

    /**
     * Observe all works.
     *
     * ⚠️ WARNING: Can be expensive for large datasets. Use with limit.
     *
     * @return Flow emitting all works on changes
     */
    fun observeAll(): Flow<List<NX_Work>>

    /**
     * Observe works needing review.
     *
     * @return Flow emitting works with needsReview=true
     */
    fun observeNeedsReview(): Flow<List<NX_Work>>

    /**
     * Observe recently added works.
     *
     * @param limit Maximum results
     * @return Flow emitting recently added works
     */
    fun observeRecentlyAdded(limit: Int = 50): Flow<List<NX_Work>>

    // ═══════════════════════════════════════════════════════════════════════
    // Counts & Statistics
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Count total works.
     *
     * @return Total work count
     */
    suspend fun count(): Long

    /**
     * Count works by type.
     *
     * @param workType Work type filter
     * @return Count of works of given type
     */
    suspend fun countByWorkType(workType: WorkType): Long

    /**
     * Count works needing review.
     *
     * @return Count of works with needsReview=true
     */
    suspend fun countNeedsReview(): Long

    /**
     * Count adult content works.
     *
     * @return Count of works with isAdult=true
     */
    suspend fun countAdultContent(): Long

    /**
     * Get work type distribution.
     *
     * @return Map of WorkType to count
     */
    suspend fun getWorkTypeDistribution(): Map<WorkType, Long>

    // ═══════════════════════════════════════════════════════════════════════
    // Batch Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Upsert multiple works in a single transaction.
     *
     * @param works List of works to upsert
     * @return List of upserted works with IDs populated
     */
    suspend fun upsertBatch(works: List<NX_Work>): List<NX_Work>

    /**
     * Delete multiple works by workKeys.
     *
     * @param workKeys List of work keys to delete
     * @return Number of works deleted
     */
    suspend fun deleteBatch(workKeys: List<String>): Int

    /**
     * Find multiple works by workKeys.
     *
     * @param workKeys List of work keys
     * @return List of found works (may be smaller than input if some not found)
     */
    suspend fun findByWorkKeys(workKeys: List<String>): List<NX_Work>

    // ═══════════════════════════════════════════════════════════════════════
    // Validation & Health
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if workKey exists.
     *
     * @param workKey Work key to check
     * @return true if exists, false otherwise
     */
    suspend fun exists(workKey: String): Boolean

    /**
     * Find works missing source refs (INV-10 violations).
     *
     * @param limit Maximum results
     * @return List of works without any NX_WorkSourceRef
     */
    suspend fun findMissingSourceRefs(limit: Int = 100): List<NX_Work>

    /**
     * Find works missing variants (INV-11 violations).
     *
     * @param limit Maximum results
     * @return List of works without any NX_WorkVariant
     */
    suspend fun findMissingVariants(limit: Int = 100): List<NX_Work>
}
