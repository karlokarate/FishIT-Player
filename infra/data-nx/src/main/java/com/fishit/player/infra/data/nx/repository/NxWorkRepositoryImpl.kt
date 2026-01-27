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
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlowWithLimit
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryBuilder
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    // Lists (UI-critical) - Uses asFlowWithLimit for DB-level efficiency
    // ──────────────────────────────────────────────────────────────────────

    override fun observeByType(type: WorkType, limit: Int): Flow<List<Work>> {
        val typeString = type.toEntityString()
        return box.query(NX_Work_.workType.equal(typeString, StringOrder.CASE_SENSITIVE))
            .order(NX_Work_.canonicalTitle)
            .build()
            .asFlowWithLimit(limit)
            .map { list -> list.map { it.toDomain() } }
    }

    override fun observeRecentlyUpdated(limit: Int): Flow<List<Work>> {
        // Uses @Index on updatedAt for efficient ordering
        return box.query()
            .orderDesc(NX_Work_.updatedAt)
            .build()
            .asFlowWithLimit(limit)
            .map { list -> list.map { it.toDomain() } }
    }

    override fun observeRecentlyCreated(limit: Int): Flow<List<Work>> {
        // Uses @Index on createdAt for efficient ordering
        return box.query()
            .orderDesc(NX_Work_.createdAt)
            .build()
            .asFlowWithLimit(limit)
            .map { list -> list.map { it.toDomain() } }
    }

    override fun observeNeedsReview(limit: Int): Flow<List<Work>> {
        return box.query(NX_Work_.needsReview.equal(true))
            .order(NX_Work_.canonicalTitle)
            .build()
            .asFlowWithLimit(limit)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun searchByTitle(queryNormalized: String, limit: Int): List<Work> = withContext(Dispatchers.IO) {
        box.query(NX_Work_.canonicalTitleLower.contains(queryNormalized.lowercase(), StringOrder.CASE_INSENSITIVE))
            .order(NX_Work_.canonicalTitle)
            .build()
            .find(0, limit.toLong())
            .map { it.toDomain() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Advanced Query (Sort/Filter/Search)
    // ──────────────────────────────────────────────────────────────────────

    override fun observeWithOptions(options: NxWorkRepository.QueryOptions): Flow<List<Work>> {
        return buildQuery(options)
            .asFlowWithLimit(options.limit)
            .map { list -> list.map { it.toDomain() } }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Paging (Jetpack Paging 3 Integration)
    // ──────────────────────────────────────────────────────────────────────

    override fun pagingSourceFactory(
        options: NxWorkRepository.QueryOptions,
    ): () -> androidx.paging.PagingSource<Int, Work> = {
        com.fishit.player.core.persistence.paging.ObjectBoxPagingSource(
            queryFactory = { buildQuery(options) },
            mapper = { it.toDomain() },
        )
    }

    override suspend fun count(options: NxWorkRepository.QueryOptions): Int = withContext(Dispatchers.IO) {
        buildQuery(options).count().toInt()
    }

    override suspend fun advancedSearch(
        query: String,
        options: NxWorkRepository.QueryOptions,
    ): List<Work> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext buildQuery(options)
                .find(0, options.limit.toLong())
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
            .find(0, options.limit.toLong())
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
