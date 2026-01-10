package com.fishit.player.core.persistence.repository.nx

import com.fishit.player.core.persistence.obx.NX_WorkRelation
import com.fishit.player.core.persistence.obx.RelationType
import kotlinx.coroutines.flow.Flow

/**
 * Repository for NX_WorkRelation entities - work relationships (series↔episodes, sequels, etc.).
 *
 * **SSOT Contract:** docs/v2/NX_SSOT_CONTRACT.md
 * **Roadmap:** docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
 *
 * ## Invariants (BINDING)
 * - Series ↔ Episode relationships must be consistent
 * - Parent-child relationships are uni-directional (parent → child)
 * - Season/episode numbers REQUIRED for SERIES_EPISODE relations
 * - No circular references allowed (work cannot be both parent and child in same chain)
 * - Sort order determines episode/sequel order within parent
 *
 * ## Relation Types
 * - SERIES_EPISODE: Series contains episodes (season/episode metadata)
 * - SEQUEL: Follow-up work
 * - PREQUEL: Predecessor work
 * - REMAKE: New version of existing work
 * - SPINOFF: Derivative work
 * - ALTERNATIVE: Alternative version (director's cut, extended edition, etc.)
 *
 * ## Return Type Patterns
 * This interface uses standard Kotlin patterns for operation results:
 * - **Entity returns**: `upsert()` returns the entity with populated ID
 * - **Boolean returns**: `delete()`, `linkSeriesEpisode()` etc. return success/failure
 * - **Nullable returns**: `find*()` methods return null when not found
 *
 * For error details beyond boolean success/failure, implementations may throw
 * typed exceptions (e.g., IllegalArgumentException for validation violations).
 *
 * ## Architectural Note
 * This repository interface is in `core/persistence/repository/nx/` because
 * NX entities ARE the domain model (SSOT). See NxWorkRepository for full explanation.
 *
 * @see NX_WorkRelation
 * @see NX_Work
 * @see NxWorkRepository
 */
@Suppress("TooManyFunctions") // Repository interfaces legitimately need comprehensive data access methods
interface NxWorkRelationRepository {
    // ═══════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find relation by ObjectBox ID.
     *
     * @param id ObjectBox entity ID
     * @return Relation if found, null otherwise
     */
    suspend fun findById(id: Long): NX_WorkRelation?

    /**
     * Insert or update a relation.
     *
     * If relation with same ID exists, updates it.
     * Otherwise creates new relation.
     *
     * @param relation Relation to upsert
     * @return Updated relation with ID populated
     */
    suspend fun upsert(relation: NX_WorkRelation): NX_WorkRelation

    /**
     * Delete relation by ID.
     *
     * @param id ObjectBox entity ID
     * @return true if deleted, false if not found
     */
    suspend fun delete(id: Long): Boolean

    /**
     * Find all relations (for debug/export).
     *
     * ⚠️ WARNING: Use with caution on large datasets. Consider pagination.
     *
     * @param limit Maximum results (default 1000, set to Int.MAX_VALUE for all)
     * @return List of all relations
     */
    suspend fun findAll(limit: Int = 1000): List<NX_WorkRelation>

    // ═══════════════════════════════════════════════════════════════════════
    // Parent-Child Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find all relations where parent has given workKey.
     *
     * Returns all children of the parent work, across all relation types.
     *
     * @param parentWorkKey Parent work key
     * @return List of relations (ordered by sortOrder)
     */
    suspend fun findByParentWorkKey(parentWorkKey: String): List<NX_WorkRelation>

    /**
     * Find all relations where parent has given ID.
     *
     * @param parentWorkId Parent work ObjectBox ID
     * @return List of relations (ordered by sortOrder)
     */
    suspend fun findByParentWorkId(parentWorkId: Long): List<NX_WorkRelation>

    /**
     * Find all relations where child has given workKey.
     *
     * Returns all parents of the child work (e.g., series for an episode).
     *
     * @param childWorkKey Child work key
     * @return List of relations
     */
    suspend fun findByChildWorkKey(childWorkKey: String): List<NX_WorkRelation>

    /**
     * Find all relations where child has given ID.
     *
     * @param childWorkId Child work ObjectBox ID
     * @return List of relations
     */
    suspend fun findByChildWorkId(childWorkId: Long): List<NX_WorkRelation>

    /**
     * Find specific relation between parent and child.
     *
     * @param parentWorkKey Parent work key
     * @param childWorkKey Child work key
     * @return First relation found (any type), null if not found
     */
    suspend fun findRelation(
        parentWorkKey: String,
        childWorkKey: String,
    ): NX_WorkRelation?

    /**
     * Find specific relation between parent and child of given type.
     *
     * @param parentWorkKey Parent work key
     * @param childWorkKey Child work key
     * @param relationType Relation type filter
     * @return Relation if found, null otherwise
     */
    suspend fun findRelationByType(
        parentWorkKey: String,
        childWorkKey: String,
        relationType: RelationType,
    ): NX_WorkRelation?

    // ═══════════════════════════════════════════════════════════════════════
    // Relation Type Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find all relations of given type.
     *
     * @param relationType Relation type filter
     * @param limit Maximum results (default 100)
     * @return List of relations
     */
    suspend fun findByRelationType(
        relationType: RelationType,
        limit: Int = 100,
    ): List<NX_WorkRelation>

    /**
     * Find all children of parent work filtered by relation type.
     *
     * @param parentWorkKey Parent work key
     * @param relationType Relation type filter
     * @return List of relations (ordered by sortOrder)
     */
    suspend fun findChildrenByType(
        parentWorkKey: String,
        relationType: RelationType,
    ): List<NX_WorkRelation>

    /**
     * Find all parents of child work filtered by relation type.
     *
     * @param childWorkKey Child work key
     * @param relationType Relation type filter
     * @return List of relations
     */
    suspend fun findParentsByType(
        childWorkKey: String,
        relationType: RelationType,
    ): List<NX_WorkRelation>

    // ═══════════════════════════════════════════════════════════════════════
    // Series ↔ Episode Queries (SERIES_EPISODE type)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find all episodes for a series.
     *
     * Returns SERIES_EPISODE relations for the given series.
     *
     * @param seriesWorkKey Series work key
     * @return List of episode relations (ordered by season, episode)
     */
    suspend fun findEpisodesForSeries(seriesWorkKey: String): List<NX_WorkRelation>

    /**
     * Find all episodes for a specific season of a series.
     *
     * @param seriesWorkKey Series work key
     * @param season Season number
     * @return List of episode relations for the season (ordered by episode number)
     */
    suspend fun findEpisodesBySeason(
        seriesWorkKey: String,
        season: Int,
    ): List<NX_WorkRelation>

    /**
     * Find specific episode in a series.
     *
     * @param seriesWorkKey Series work key
     * @param season Season number
     * @param episode Episode number
     * @return Episode relation if found, null otherwise
     */
    suspend fun findEpisode(
        seriesWorkKey: String,
        season: Int,
        episode: Int,
    ): NX_WorkRelation?

    /**
     * Find series relation for an episode.
     *
     * Returns the parent series for the given episode.
     *
     * @param episodeWorkKey Episode work key
     * @return Series relation if found, null otherwise
     */
    suspend fun findSeriesForEpisode(episodeWorkKey: String): NX_WorkRelation?

    /**
     * Get all season numbers for a series.
     *
     * @param seriesWorkKey Series work key
     * @return Distinct season numbers (sorted ascending)
     */
    suspend fun getSeasonNumbers(seriesWorkKey: String): List<Int>

    /**
     * Get total episode count for a series.
     *
     * @param seriesWorkKey Series work key
     * @return Total number of episodes across all seasons
     */
    suspend fun getEpisodeCount(seriesWorkKey: String): Int

    /**
     * Get episode count for a specific season.
     *
     * @param seriesWorkKey Series work key
     * @param season Season number
     * @return Number of episodes in the season
     */
    suspend fun getEpisodeCountBySeason(
        seriesWorkKey: String,
        season: Int,
    ): Int

    // ═══════════════════════════════════════════════════════════════════════
    // Sequel/Prequel/Related Queries
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Find all sequels to a work.
     *
     * Returns works where given work is the parent (original) in SEQUEL relations.
     *
     * @param workKey Original work key
     * @return List of sequel relations (ordered by sortOrder)
     */
    suspend fun findSequels(workKey: String): List<NX_WorkRelation>

    /**
     * Find all prequels to a work.
     *
     * Returns works where given work is the parent (original) in PREQUEL relations.
     *
     * @param workKey Original work key
     * @return List of prequel relations (ordered by sortOrder)
     */
    suspend fun findPrequels(workKey: String): List<NX_WorkRelation>

    /**
     * Find all remakes of a work.
     *
     * Returns works where given work is the parent (original) in REMAKE relations.
     *
     * @param workKey Original work key
     * @return List of remake relations
     */
    suspend fun findRemakes(workKey: String): List<NX_WorkRelation>

    /**
     * Find all spinoffs of a work.
     *
     * Returns works where given work is the parent (original) in SPINOFF relations.
     *
     * @param workKey Original work key
     * @return List of spinoff relations
     */
    suspend fun findSpinoffs(workKey: String): List<NX_WorkRelation>

    /**
     * Find all related works (all relation types).
     *
     * Returns both parents and children across all relation types.
     *
     * @param workKey Work key
     * @return List of all relations involving this work
     */
    suspend fun findAllRelatedWorks(workKey: String): List<NX_WorkRelation>

    // ═══════════════════════════════════════════════════════════════════════
    // Link/Unlink Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Link episode to series with season/episode numbers.
     *
     * Creates SERIES_EPISODE relation.
     *
     * @param seriesWorkKey Series work key (parent)
     * @param episodeWorkKey Episode work key (child)
     * @param season Season number
     * @param episode Episode number
     * @param sortOrder Sort order within parent (default 0)
     * @return Created relation
     */
    suspend fun linkSeriesEpisode(
        seriesWorkKey: String,
        episodeWorkKey: String,
        season: Int,
        episode: Int,
        sortOrder: Int = 0,
    ): NX_WorkRelation

    /**
     * Link sequel to original work.
     *
     * Creates SEQUEL relation (original is parent, sequel is child).
     *
     * @param originalWorkKey Original work key (parent)
     * @param sequelWorkKey Sequel work key (child)
     * @param sortOrder Sort order within parent (default 0, for multiple sequels)
     * @return Created relation
     */
    suspend fun linkSequel(
        originalWorkKey: String,
        sequelWorkKey: String,
        sortOrder: Int = 0,
    ): NX_WorkRelation

    /**
     * Link prequel to original work.
     *
     * Creates PREQUEL relation (original is parent, prequel is child).
     *
     * @param originalWorkKey Original work key (parent)
     * @param prequelWorkKey Prequel work key (child)
     * @param sortOrder Sort order within parent (default 0, for multiple prequels)
     * @return Created relation
     */
    suspend fun linkPrequel(
        originalWorkKey: String,
        prequelWorkKey: String,
        sortOrder: Int = 0,
    ): NX_WorkRelation

    /**
     * Link remake to original work.
     *
     * Creates REMAKE relation (original is parent, remake is child).
     *
     * @param originalWorkKey Original work key (parent)
     * @param remakeWorkKey Remake work key (child)
     * @return Created relation
     */
    suspend fun linkRemake(
        originalWorkKey: String,
        remakeWorkKey: String,
    ): NX_WorkRelation

    /**
     * Link spinoff to original work.
     *
     * Creates SPINOFF relation (original is parent, spinoff is child).
     *
     * @param originalWorkKey Original work key (parent)
     * @param spinoffWorkKey Spinoff work key (child)
     * @return Created relation
     */
    suspend fun linkSpinoff(
        originalWorkKey: String,
        spinoffWorkKey: String,
    ): NX_WorkRelation

    /**
     * Unlink relation between parent and child (any type).
     *
     * Deletes the first relation found between the two works.
     *
     * @param parentWorkKey Parent work key
     * @param childWorkKey Child work key
     * @return true if relation was found and deleted
     */
    suspend fun unlinkRelation(
        parentWorkKey: String,
        childWorkKey: String,
    ): Boolean

    /**
     * Unlink all children from a parent work.
     *
     * Deletes all relations where given work is the parent.
     *
     * @param parentWorkKey Parent work key
     * @return Number of relations deleted
     */
    suspend fun unlinkAllChildren(parentWorkKey: String): Int

    /**
     * Unlink all parents from a child work.
     *
     * Deletes all relations where given work is the child.
     *
     * @param childWorkKey Child work key
     * @return Number of relations deleted
     */
    suspend fun unlinkAllParents(childWorkKey: String): Int

    // ═══════════════════════════════════════════════════════════════════════
    // Sort Order Management
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Update sort order for a relation.
     *
     * @param relationId Relation ID
     * @param newSortOrder New sort order value
     * @return true if updated, false if relation not found
     */
    suspend fun updateSortOrder(
        relationId: Long,
        newSortOrder: Int,
    ): Boolean

    /**
     * Reorder children of a parent work.
     *
     * Sets sortOrder for each child based on position in the list.
     *
     * @param parentWorkKey Parent work key
     * @param orderedChildWorkKeys Ordered list of child work keys
     * @return true if all children were found and reordered
     */
    suspend fun reorderChildren(
        parentWorkKey: String,
        orderedChildWorkKeys: List<String>,
    ): Boolean

    /**
     * Get next available sort order for a parent.
     *
     * Returns max(sortOrder) + 1 for the parent's children.
     *
     * @param parentWorkKey Parent work key
     * @return Next sort order value (0 if no children)
     */
    suspend fun getNextSortOrder(parentWorkKey: String): Int

    // ═══════════════════════════════════════════════════════════════════════
    // Reactive Streams (Flow)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Observe all relations where parent has given workKey.
     *
     * Emits updated list whenever relations change.
     *
     * @param parentWorkKey Parent work key
     * @return Flow of relation lists (ordered by sortOrder)
     */
    fun observeByParentWorkKey(parentWorkKey: String): Flow<List<NX_WorkRelation>>

    /**
     * Observe all relations where child has given workKey.
     *
     * Emits updated list whenever relations change.
     *
     * @param childWorkKey Child work key
     * @return Flow of relation lists
     */
    fun observeByChildWorkKey(childWorkKey: String): Flow<List<NX_WorkRelation>>

    /**
     * Observe all episodes for a series.
     *
     * Emits updated list whenever episode relations change.
     *
     * @param seriesWorkKey Series work key
     * @return Flow of episode relation lists (ordered by season, episode)
     */
    fun observeEpisodesForSeries(seriesWorkKey: String): Flow<List<NX_WorkRelation>>

    /**
     * Observe all relations.
     *
     * ⚠️ WARNING: Use with caution on large datasets.
     *
     * @return Flow of all relations
     */
    fun observeAll(): Flow<List<NX_WorkRelation>>

    // ═══════════════════════════════════════════════════════════════════════
    // Counts & Statistics
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get total count of all relations.
     *
     * @return Total relation count
     */
    suspend fun count(): Long

    /**
     * Get count of relations by type.
     *
     * @param relationType Relation type filter
     * @return Count of relations of given type
     */
    suspend fun countByRelationType(relationType: RelationType): Long

    /**
     * Get count of children for a parent work.
     *
     * @param parentWorkKey Parent work key
     * @return Number of child relations
     */
    suspend fun countChildren(parentWorkKey: String): Int

    /**
     * Get count of parents for a child work.
     *
     * @param childWorkKey Child work key
     * @return Number of parent relations
     */
    suspend fun countParents(childWorkKey: String): Int

    /**
     * Get distribution of relation types.
     *
     * Returns map of relation type to count.
     *
     * @return Map of RelationType to count
     */
    suspend fun getRelationTypeDistribution(): Map<RelationType, Long>

    // ═══════════════════════════════════════════════════════════════════════
    // Batch Operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Insert or update multiple relations.
     *
     * **Batch Size Limits:** Respect device constraints from ObxWriteConfig.
     * - FireTV/Low-RAM: Max 35 items (FIRETV_BATCH_CAP)
     * - Phone/Tablet: 100-600 items
     *
     * @param relations List of relations to upsert
     * @return List of updated relations with IDs populated
     */
    suspend fun upsertBatch(relations: List<NX_WorkRelation>): List<NX_WorkRelation>

    /**
     * Delete multiple relations by IDs.
     *
     * @param relationIds List of relation IDs to delete
     * @return Number of relations deleted
     */
    suspend fun deleteBatch(relationIds: List<Long>): Int

    /**
     * Delete all relations where parent has given workKey.
     *
     * @param parentWorkKey Parent work key
     * @return Number of relations deleted
     */
    suspend fun deleteByParentWorkKey(parentWorkKey: String): Int

    /**
     * Delete all relations where child has given workKey.
     *
     * @param childWorkKey Child work key
     * @return Number of relations deleted
     */
    suspend fun deleteByChildWorkKey(childWorkKey: String): Int

    // ═══════════════════════════════════════════════════════════════════════
    // Validation & Health
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if relation exists between parent and child.
     *
     * @param parentWorkKey Parent work key
     * @param childWorkKey Child work key
     * @return true if any relation exists
     */
    suspend fun exists(
        parentWorkKey: String,
        childWorkKey: String,
    ): Boolean

    /**
     * Find relations with missing parent or child works.
     *
     * Returns relations where parentWork or childWork ToOne is not set.
     *
     * @param limit Maximum results (default 100)
     * @return List of orphaned relations
     */
    suspend fun findOrphanedRelations(limit: Int = 100): List<NX_WorkRelation>

    /**
     * Find duplicate relations (same parent/child/type).
     *
     * Returns relations that have the same parent, child, and type.
     *
     * @param limit Maximum results (default 100)
     * @return List of duplicate relations
     */
    suspend fun findDuplicateRelations(limit: Int = 100): List<NX_WorkRelation>

    /**
     * Find circular relations (work is both parent and child).
     *
     * Returns relations where parent and child reference the same work.
     *
     * @param limit Maximum results (default 100)
     * @return List of circular relations
     */
    suspend fun findCircularRelations(limit: Int = 100): List<NX_WorkRelation>

    /**
     * Validate relation for correctness.
     *
     * Checks:
     * - Parent and child works exist
     * - Season/episode numbers present for SERIES_EPISODE type
     * - No circular reference
     * - No duplicate relation exists
     *
     * @param relation Relation to validate
     * @return List of validation error messages (empty if valid)
     */
    suspend fun validateRelation(relation: NX_WorkRelation): List<String>
}
