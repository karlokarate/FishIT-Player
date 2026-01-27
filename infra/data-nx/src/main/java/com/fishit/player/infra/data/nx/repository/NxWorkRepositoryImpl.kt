/**
 * ObjectBox implementation of [NxWorkRepository].
 *
 * Provides the SSOT for canonical Works in the UI layer.
 * Maps between NX_Work entity and Work domain model.
 */
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryBuilder
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox-backed repository for canonical Works.
 *
 * Thread-safe: all suspend functions run on IO dispatcher.
 * Uses ObjectBox Flow for reactive observation.
 */
@Singleton
class NxWorkRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore,
) : NxWorkRepository {
    private val box by lazy { boxStore.boxFor<NX_Work>() }

    // ──────────────────────────────────────────────────────────────────────
    // Single item
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun get(workKey: String): Work? = withContext(Dispatchers.IO) {
        box.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?.toDomain()
    }

    override fun observe(workKey: String): Flow<Work?> {
        return box.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .asFlow()
            .map { list -> list.firstOrNull()?.toDomain() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Lists (UI-critical)
    // ──────────────────────────────────────────────────────────────────────

    override fun observeByType(type: WorkType, limit: Int): Flow<List<Work>> {
        val typeString = type.toEntityString()
        val query = box.query(NX_Work_.workType.equal(typeString, StringOrder.CASE_SENSITIVE))
            .order(NX_Work_.canonicalTitle)
            .build()
        
        // Use custom Flow implementation to apply fetch limit before filtering
        // This avoids loading all 41k+ works on every emission
        return callbackFlow {
            // Over-fetch to account for incomplete works (filtered out)
            val fetchLimit = (limit * 3).toLong()
            
            // Emit initial result
            val initial = query.find(0, fetchLimit)
                .filter { isCompleteEfficient(it) }
                .take(limit)
                .map { it.toDomain() }
            trySend(initial)
            
            // Subscribe to changes
            val subscription = query.subscribe().observer { _ ->
                val updated = query.find(0, fetchLimit)
                    .filter { isCompleteEfficient(it) }
                    .take(limit)
                    .map { it.toDomain() }
                trySend(updated)
            }
            
            awaitClose { subscription.cancel() }
        }.flowOn(Dispatchers.IO)
    }

    override fun observeRecentlyUpdated(limit: Int): Flow<List<Work>> {
        val query = box.query()
            .orderDesc(NX_Work_.updatedAt)
            .build()
        
        return callbackFlow {
            val fetchLimit = (limit * 3).toLong()
            
            val initial = query.find(0, fetchLimit)
                .filter { isCompleteEfficient(it) }
                .take(limit)
                .map { it.toDomain() }
            trySend(initial)
            
            val subscription = query.subscribe().observer { _ ->
                val updated = query.find(0, fetchLimit)
                    .filter { isCompleteEfficient(it) }
                    .take(limit)
                    .map { it.toDomain() }
                trySend(updated)
            }
            
            awaitClose { subscription.cancel() }
        }.flowOn(Dispatchers.IO)
    }

    override fun observeRecentlyCreated(limit: Int): Flow<List<Work>> {
        val query = box.query()
            .orderDesc(NX_Work_.createdAt)
            .build()
        
        return callbackFlow {
            val fetchLimit = (limit * 3).toLong()
            
            val initial = query.find(0, fetchLimit)
                .filter { isCompleteEfficient(it) }
                .take(limit)
                .map { it.toDomain() }
            trySend(initial)
            
            val subscription = query.subscribe().observer { _ ->
                val updated = query.find(0, fetchLimit)
                    .filter { isCompleteEfficient(it) }
                    .take(limit)
                    .map { it.toDomain() }
                trySend(updated)
            }
            
            awaitClose { subscription.cancel() }
        }.flowOn(Dispatchers.IO)
    }

    override fun observeNeedsReview(limit: Int): Flow<List<Work>> {
        val query = box.query(NX_Work_.needsReview.equal(true))
            .order(NX_Work_.canonicalTitle)
            .build()
        
        return callbackFlow {
            val fetchLimit = (limit * 3).toLong()
            
            val initial = query.find(0, fetchLimit)
                .filter { isCompleteEfficient(it) }
                .take(limit)
                .map { it.toDomain() }
            trySend(initial)
            
            val subscription = query.subscribe().observer { _ ->
                val updated = query.find(0, fetchLimit)
                    .filter { isCompleteEfficient(it) }
                    .take(limit)
                    .map { it.toDomain() }
                trySend(updated)
            }
            
            awaitClose { subscription.cancel() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun searchByTitle(queryNormalized: String, limit: Int): List<Work> = withContext(Dispatchers.IO) {
        // Use 3x over-fetch to account for filtered incomplete works
        val fetchLimit = (limit * 3).toLong()
        box.query(NX_Work_.canonicalTitleLower.contains(queryNormalized.lowercase(), StringOrder.CASE_INSENSITIVE))
            .order(NX_Work_.canonicalTitle)
            .build()
            .find(0, fetchLimit)
            .filter { isCompleteEfficient(it) }
            .take(limit)
            .map { it.toDomain() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Advanced Query (Sort/Filter/Search)
    // ──────────────────────────────────────────────────────────────────────

    override fun observeWithOptions(options: NxWorkRepository.QueryOptions): Flow<List<Work>> {
        val query = buildQuery(options)
        
        return callbackFlow {
            val fetchLimit = (options.limit * 3).toLong()
            
            val initial = query.find(0, fetchLimit)
                .filter { isCompleteEfficient(it) }
                .take(options.limit)
                .map { it.toDomain() }
            trySend(initial)
            
            val subscription = query.subscribe().observer { _ ->
                val updated = query.find(0, fetchLimit)
                    .filter { isCompleteEfficient(it) }
                    .take(options.limit)
                    .map { it.toDomain() }
                trySend(updated)
            }
            
            awaitClose { subscription.cancel() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun advancedSearch(
        query: String,
        options: NxWorkRepository.QueryOptions,
    ): List<Work> = withContext(Dispatchers.IO) {
        val fetchLimit = (options.limit * 3).toLong()
        
        if (query.isBlank()) {
            return@withContext buildQuery(options)
                .find(0, fetchLimit)
                .filter { isCompleteEfficient(it) }
                .take(options.limit)
                .map { it.toDomain() }
        }

        val queryLower = query.lowercase().trim()

        // Search in title, plot, cast, director
        val queryBuilder = box.query()

        // Title search (primary)
        queryBuilder.contains(NX_Work_.canonicalTitleLower, queryLower, StringOrder.CASE_INSENSITIVE)

        // Apply type filter
        options.type?.let { type ->
            queryBuilder.and().equal(NX_Work_.workType, type.toEntityString(), StringOrder.CASE_SENSITIVE)
        }

        // Apply adult filter
        if (options.hideAdult) {
            queryBuilder.and().equal(NX_Work_.isAdult, false)
        }

        // Apply rating filter
        options.minRating?.let { minRating ->
            queryBuilder.and().greaterOrEqual(NX_Work_.rating, minRating)
        }

        // Apply year filter
        options.yearRange?.let { range ->
            queryBuilder.and().between(NX_Work_.year, range.first.toLong(), range.last.toLong())
        }

        // Apply sorting
        applySorting(queryBuilder, options.sortField, options.sortDirection)

        queryBuilder.build()
            .find(0, fetchLimit)
            .filter { isCompleteEfficient(it) }
            .take(options.limit)
            .map { it.toDomain() }
    }

    override suspend fun getAllGenres(): Set<String> = withContext(Dispatchers.IO) {
        val allWorks = box.query()
            .notNull(NX_Work_.genres)
            .build()
            .property(NX_Work_.genres)
            .findStrings()

        // Parse comma-separated genres and collect unique
        allWorks
            .filterNotNull()
            .flatMap { genreString ->
                genreString.split(",", ";", "/")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
            .toSet()
    }

    override suspend fun getYearRange(type: WorkType?): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val queryBuilder = box.query().notNull(NX_Work_.year)

        type?.let {
            queryBuilder.and().equal(NX_Work_.workType, it.toEntityString(), StringOrder.CASE_SENSITIVE)
        }

        val years = queryBuilder.build()
            .property(NX_Work_.year)
            .findInts()
            .toList()

        if (years.isEmpty()) {
            null
        } else {
            Pair(years.minOrNull() ?: 0, years.maxOrNull() ?: 0)
        }
    }

    /**
     * Build a query with the given options.
     */
    private fun buildQuery(options: NxWorkRepository.QueryOptions): io.objectbox.query.Query<NX_Work> {
        val queryBuilder = box.query()

        // Apply type filter
        options.type?.let { type ->
            queryBuilder.equal(NX_Work_.workType, type.toEntityString(), StringOrder.CASE_SENSITIVE)
        }

        // Apply adult filter
        if (options.hideAdult) {
            queryBuilder.and().equal(NX_Work_.isAdult, false)
        }

        // Apply rating filter
        options.minRating?.let { minRating ->
            queryBuilder.and().greaterOrEqual(NX_Work_.rating, minRating)
        }

        // Apply year filter
        options.yearRange?.let { range ->
            queryBuilder.and().between(NX_Work_.year, range.first.toLong(), range.last.toLong())
        }

        // Apply sorting
        applySorting(queryBuilder, options.sortField, options.sortDirection)

        return queryBuilder.build()
    }

    /**
     * Apply sorting to the query builder.
     */
    private fun applySorting(
        queryBuilder: io.objectbox.query.QueryBuilder<NX_Work>,
        sortField: NxWorkRepository.SortField,
        sortDirection: NxWorkRepository.SortDirection,
    ) {
        val flags = if (sortDirection == NxWorkRepository.SortDirection.DESCENDING) {
            QueryBuilder.DESCENDING
        } else {
            0
        }

        when (sortField) {
            NxWorkRepository.SortField.TITLE -> queryBuilder.order(NX_Work_.canonicalTitle, flags)
            NxWorkRepository.SortField.YEAR -> queryBuilder.order(NX_Work_.year, flags)
            NxWorkRepository.SortField.RATING -> queryBuilder.order(NX_Work_.rating, flags)
            NxWorkRepository.SortField.RECENTLY_ADDED -> queryBuilder.order(NX_Work_.createdAt, flags)
            NxWorkRepository.SortField.RECENTLY_UPDATED -> queryBuilder.order(NX_Work_.updatedAt, flags)
            NxWorkRepository.SortField.DURATION -> queryBuilder.order(NX_Work_.durationMs, flags)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Writes (MVP)
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun upsert(work: Work): Work = withContext(Dispatchers.IO) {
        val existing = box.query(NX_Work_.workKey.equal(work.workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
        val entity = work.toEntity(existing)
        box.put(entity)
        entity.toDomain()
    }

    override suspend fun upsertBatch(works: List<Work>): List<Work> = withContext(Dispatchers.IO) {
        if (works.isEmpty()) return@withContext emptyList()

        // Batch lookup existing entities by workKey
        val workKeys = works.map { it.workKey }
        val existingMap = box.query(NX_Work_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
            .build()
            .find()
            .associateBy { it.workKey }

        val entities = works.map { work ->
            work.toEntity(existingMap[work.workKey])
        }
        box.put(entities)
        entities.map { it.toDomain() }
    }

    override suspend fun softDelete(workKey: String): Boolean = withContext(Dispatchers.IO) {
        // Soft delete not implemented in entity yet, for now just mark needsReview
        // TODO: Add isDeleted flag to NX_Work entity when soft delete is needed
        val entity = box.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
        if (entity != null) {
            box.put(entity.copy(needsReview = true))
            true
        } else {
            false
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Checks if a work is complete according to INV-3 (NX_SSOT_CONTRACT.md).
     * 
     * Every NX_Work visible in the UI must have:
     * - ≥1 NX_WorkSourceRef
     * - ≥1 NX_WorkVariant with valid playbackHints
     *
     * This filter ensures incomplete works are not shown in the UI.
     * Incomplete works may exist during ingestion but should not appear to users.
     * 
     * Note: This accesses lazy-loaded relations. For large result sets,
     * pre-filter with find(offset, limit) to avoid N+1 query explosion.
     */
    private fun isComplete(work: NX_Work): Boolean {
        return work.sourceRefs.isNotEmpty() && work.variants.isNotEmpty()
    }

    /**
     * Efficient completeness check that avoids fully loading relations.
     * Checks if relation collections are empty without iterating through all items.
     */
    private fun isCompleteEfficient(work: NX_Work): Boolean {
        // Use isEmpty() method instead of synthetic property (future-proof)
        return !work.sourceRefs.isEmpty() && !work.variants.isEmpty()
    }

    private fun WorkType.toEntityString(): String = when (this) {
        WorkType.MOVIE -> "MOVIE"
        WorkType.SERIES -> "SERIES"
        WorkType.EPISODE -> "EPISODE"
        WorkType.CLIP -> "CLIP"
        WorkType.LIVE_CHANNEL -> "LIVE"
        WorkType.AUDIOBOOK -> "AUDIOBOOK"
        WorkType.MUSIC_TRACK -> "MUSIC"
        WorkType.UNKNOWN -> "UNKNOWN"
    }
}
