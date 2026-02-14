/**
 * ObjectBox implementation of [NxWorkRepository].
 *
 * Provides the SSOT for canonical Works in the UI layer.
 * Maps between NX_Work entity and Work domain model.
 */
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.RecognitionState
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlowWithLimit
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.core.persistence.obx.NxKeyGenerator
import com.fishit.player.infra.data.nx.mapper.WorkTypeMapper
import com.fishit.player.infra.data.nx.mapper.base.MappingUtils
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryBuilder
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Singleton
class NxWorkRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore,
) : NxWorkRepository {
    private val box by lazy { boxStore.boxFor<NX_Work>() }

    companion object {
        private const val TAG = "NxWorkRepository"
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PLATINUM OPTIMIZATION: Sync State Management
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Internal sync state flag.
     * When true, observeByType() throttles emissions aggressively to reduce UI load.
     */
    @Volatile
    private var syncInProgress = false

    /**
     * Enable/disable aggressive throttling during sync.
     * Should be called by sync orchestration before/after sync.
     */
    fun setSyncInProgress(inProgress: Boolean) {
        val oldValue = syncInProgress
        syncInProgress = inProgress
        if (oldValue != inProgress) {
            UnifiedLog.i(TAG) { "Sync state changed: syncInProgress=$inProgress" }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Single item
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun get(workKey: String): Work? = withContext(Dispatchers.IO) {
        box.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?.toDomain()
    }

    override suspend fun getBatch(workKeys: List<String>): Map<String, Work> = withContext(Dispatchers.IO) {
        if (workKeys.isEmpty()) return@withContext emptyMap()

        box.query(NX_Work_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
            .build()
            .find()
            .associate { it.workKey to it.toDomain() }
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
        UnifiedLog.i(TAG) { "observeByType CALLED: type=$type (entity=$typeString), limit=$limit" }

        return kotlinx.coroutines.flow.flow {
            box.query(NX_Work_.workType.equal(typeString, StringOrder.CASE_SENSITIVE))
                .order(NX_Work_.canonicalTitle)
                .build()
                .asFlowWithLimit(limit)
                .distinctUntilChanged() // PERF FIX: Prevent duplicate emissions
                .debounce {
                    // PLATINUM OPTIMIZATION: Aggressive throttling during sync!
                    // Normal: 100ms debounce
                    // During Sync: 2000ms debounce (20x less emissions!)
                    if (syncInProgress) 2000L else 100L
                }
                .collect { list ->
                    if (!syncInProgress || list.size >= limit / 2) {
                        // Only emit if not syncing OR list is substantial
                        UnifiedLog.i(TAG) { "observeByType EMITTING: type=$type, count=${list.size}" }
                        emit(list.map { it.toDomain() })
                    }
                    // Else: Skip intermediate emissions during sync
                }
        }.flowOn(Dispatchers.IO) // PERF FIX: Ensure mapping happens off main thread
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
        val count = buildQuery(options).count().toInt()
        UnifiedLog.d(TAG) {
            "📊 NxWorkRepository.count() | workType=${options.type?.name ?: "ALL"} → count=$count"
        }
        count
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
        // Primary lookup by workKey
        var existing = box.query(NX_Work_.workKey.equal(work.workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()

        // tmdbId fallback: handles key format migration (tmdb→heuristic)
        // and title variations producing different slugs.
        // If workKey lookup fails but an entity exists with the same tmdbId,
        // reuse it to prevent duplicate creation.
        if (existing == null && work.tmdbId != null) {
            existing = box.query(NX_Work_.tmdbId.equal(work.tmdbId!!))
                .build()
                .findFirst()
        }

        // INV-12: workKey is IMMUTABLE. When existing entity was found via tmdbId
        // (different workKey), preserve the original workKey to avoid orphaning
        // NX_WorkSourceRef / NX_WorkUserState references that use the old key.
        val effectiveWork = if (existing != null && existing.workKey != work.workKey) {
            work.copy(workKey = existing.workKey)
        } else {
            work
        }

        val entity = effectiveWork.toEntity(existing)
        box.put(entity)
        entity.toDomain()
    }

    /**
     * Enrich an existing work with additional metadata, respecting field guards.
     *
     * **NX_CONSOLIDATION_PLAN Phase 1 — Write Protection**
     *
     * Field categories:
     * - IMMUTABLE: workKey, workType, canonicalTitle, canonicalTitleLower, year, createdAt → SKIP
     * - ENRICH_ONLY: poster, backdrop, thumbnail, plot, genres, etc. → only if currently null
     * - ALWAYS_UPDATE: tmdbId, imdbId, tvdbId → always overwrite with new non-null value
     * - MONOTONIC_UP: recognitionState → only upgrade (lower ordinal = higher confidence)
     * - AUTO: updatedAt → always current time
     */
    override suspend fun enrichIfAbsent(workKey: String, enrichment: NxWorkRepository.Enrichment): NxWorkRepository.Work? =
        withContext(Dispatchers.IO) {
            val existing = box.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
                .build()
                .findFirst()
                ?: return@withContext null

            // IMMUTABLE: workKey, workType, canonicalTitle, canonicalTitleLower, year, createdAt — SKIP

            // ENRICH_ONLY: only set if entity field is currently null/default
            existing.season = MappingUtils.enrichOnly(existing.season, enrichment.season)
            existing.episode = MappingUtils.enrichOnly(existing.episode, enrichment.episode)
            existing.durationMs = MappingUtils.enrichOnly(existing.durationMs, enrichment.runtimeMs)
            existing.rating = MappingUtils.enrichOnly(existing.rating, enrichment.rating)
            existing.genres = MappingUtils.enrichOnly(existing.genres, enrichment.genres)
            existing.plot = MappingUtils.enrichOnly(existing.plot, enrichment.plot)
            existing.director = MappingUtils.enrichOnly(existing.director, enrichment.director)
            existing.cast = MappingUtils.enrichOnly(existing.cast, enrichment.cast)
            existing.trailer = MappingUtils.enrichOnly(existing.trailer, enrichment.trailer)
            existing.releaseDate = MappingUtils.enrichOnly(existing.releaseDate, enrichment.releaseDate)

            // ENRICH_ONLY for ImageRef — Phase 4: ImageRef direct, no String parsing
            if (existing.poster == null) {
                enrichment.poster?.let { existing.poster = it }
            }
            if (existing.backdrop == null) {
                enrichment.backdrop?.let { existing.backdrop = it }
            }
            if (existing.thumbnail == null) {
                enrichment.thumbnail?.let { existing.thumbnail = it }
            }

            // ALWAYS_UPDATE: External IDs (come from detail enrichment)
            existing.tmdbId = MappingUtils.alwaysUpdate(existing.tmdbId, enrichment.tmdbId)
            existing.imdbId = MappingUtils.alwaysUpdate(existing.imdbId, enrichment.imdbId)
            existing.tvdbId = MappingUtils.alwaysUpdate(existing.tvdbId, enrichment.tvdbId)

            // Update authorityKey if tmdbId changed — delegate to NxKeyGenerator SSOT
            enrichment.tmdbId?.let { newTmdbId ->
                val workType = existing.workType.lowercase()
                existing.authorityKey = NxKeyGenerator.authorityKey("TMDB", workType, newTmdbId)
            }

            // MONOTONIC_UP: RecognitionState — only upgrade, never downgrade
            val currentState = MappingUtils.safeEnumFromString(
                existing.recognitionState,
                RecognitionState.HEURISTIC,
            )
            val upgradedState = MappingUtils.monotonicUp(currentState, enrichment.recognitionState)
            if (upgradedState != null) {
                existing.recognitionState = upgradedState.name
                @Suppress("DEPRECATION")
                existing.needsReview = upgradedState == RecognitionState.NEEDS_REVIEW
            }

            // AUTO: always update timestamp
            existing.updatedAt = System.currentTimeMillis()

            box.put(existing)
            existing.toDomain()
        }

    /**
     * Enrich from detail API — non-null enrichment values ALWAYS overwrite existing values.
     *
     * The detail info API (`get_vod_info`, `get_series_info`) is more authoritative
     * than the listing API. Fields like poster, plot, rating, genres etc. from the
     * detail API should overwrite whatever the listing API set during catalog sync.
     */
    override suspend fun enrichFromDetail(workKey: String, enrichment: NxWorkRepository.Enrichment): NxWorkRepository.Work? =
        withContext(Dispatchers.IO) {
            val existing = box.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
                .build()
                .findFirst()
                ?: return@withContext null

            // IMMUTABLE: workKey, workType, canonicalTitle, canonicalTitleLower, year, createdAt — SKIP

            // DETAIL_OVERWRITE: non-null enrichment value always wins (detail API is authoritative)
            existing.season = MappingUtils.alwaysUpdate(existing.season, enrichment.season)
            existing.episode = MappingUtils.alwaysUpdate(existing.episode, enrichment.episode)
            existing.durationMs = MappingUtils.alwaysUpdate(existing.durationMs, enrichment.runtimeMs)
            existing.rating = MappingUtils.alwaysUpdate(existing.rating, enrichment.rating)
            existing.genres = MappingUtils.alwaysUpdate(existing.genres, enrichment.genres)
            existing.plot = MappingUtils.alwaysUpdate(existing.plot, enrichment.plot)
            existing.director = MappingUtils.alwaysUpdate(existing.director, enrichment.director)
            existing.cast = MappingUtils.alwaysUpdate(existing.cast, enrichment.cast)
            existing.trailer = MappingUtils.alwaysUpdate(existing.trailer, enrichment.trailer)
            existing.releaseDate = MappingUtils.alwaysUpdate(existing.releaseDate, enrichment.releaseDate)

            // DETAIL_OVERWRITE for ImageRef — non-null enrichment wins
            enrichment.poster?.let { existing.poster = it }
            enrichment.backdrop?.let { existing.backdrop = it }
            enrichment.thumbnail?.let { existing.thumbnail = it }

            // ALWAYS_UPDATE: External IDs (come from detail enrichment)
            existing.tmdbId = MappingUtils.alwaysUpdate(existing.tmdbId, enrichment.tmdbId)
            existing.imdbId = MappingUtils.alwaysUpdate(existing.imdbId, enrichment.imdbId)
            existing.tvdbId = MappingUtils.alwaysUpdate(existing.tvdbId, enrichment.tvdbId)

            // Update authorityKey if tmdbId changed — delegate to NxKeyGenerator SSOT
            enrichment.tmdbId?.let { newTmdbId ->
                val workType = existing.workType.lowercase()
                existing.authorityKey = NxKeyGenerator.authorityKey("TMDB", workType, newTmdbId)
            }

            // MONOTONIC_UP: RecognitionState — only upgrade, never downgrade
            val currentState = MappingUtils.safeEnumFromString(
                existing.recognitionState,
                RecognitionState.HEURISTIC,
            )
            val upgradedState = MappingUtils.monotonicUp(currentState, enrichment.recognitionState)
            if (upgradedState != null) {
                existing.recognitionState = upgradedState.name
                @Suppress("DEPRECATION")
                existing.needsReview = upgradedState == RecognitionState.NEEDS_REVIEW
            }

            // AUTO: always update timestamp
            existing.updatedAt = System.currentTimeMillis()

            box.put(existing)
            existing.toDomain()
        }

    override suspend fun upsertBatch(works: List<Work>): List<Work> = withContext(Dispatchers.IO) {
        if (works.isEmpty()) return@withContext emptyList()

        try {
            // CRITICAL FIX: Deduplicate works by workKey within batch
            // If multiple works have same workKey, only keep the last one
            val uniqueWorks = works.associateBy { it.workKey }.values.toList()

            if (uniqueWorks.size < works.size) {
                UnifiedLog.w(TAG) {
                    "Deduped ${works.size - uniqueWorks.size} duplicate workKeys in batch " +
                    "(${works.size} → ${uniqueWorks.size})"
                }
            }

            // Use runInTx for atomic batch update
            boxStore.runInTx {
                // CRITICAL: Query existing entities INSIDE transaction to avoid race conditions!
                val workKeys = uniqueWorks.map { it.workKey }
                val existingMap = box.query(NX_Work_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
                    .build()
                    .find()
                    .associateBy { it.workKey }

                val entities = uniqueWorks.map { work ->
                    work.toEntity(existingMap[work.workKey])
                }
                box.put(entities)
            }

            // Return all works that were successfully persisted
            uniqueWorks
        } finally {
            // CRITICAL: Cleanup thread-local resources to prevent transaction leaks
            try {
                boxStore.closeThreadResources()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
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

    /** Delegates to shared [WorkTypeMapper] — eliminates duplicate when-block. */
    private fun WorkType.toEntityString(): String = WorkTypeMapper.toEntityString(this)
}
