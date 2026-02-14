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

import com.fishit.player.core.model.ImageRef
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
        /** Season number (for episodes/series content) */
        val season: Int? = null,
        /** Episode number within season */
        val episode: Int? = null,
        val runtimeMs: Long? = null,
        /** Poster image — SSOT: ImageRef (NX_CONSOLIDATION_PLAN Phase 4). */
        val poster: ImageRef? = null,
        /** Backdrop/fanart image. */
        val backdrop: ImageRef? = null,
        /** Thumbnail image (e.g., Telegram minithumbnail, episode screenshot). */
        val thumbnail: ImageRef? = null,
        val rating: Double? = null, // 0..10 if present
        val genres: String? = null,
        val plot: String? = null,
        /**
         * Director name(s) from metadata.
         * May be comma-separated for multiple directors.
         */
        val director: String? = null,
        /**
         * Cast list from metadata.
         * Comma-separated list of actor names.
         */
        val cast: String? = null,
        /**
         * YouTube trailer URL or video ID.
         * May be a full URL (https://youtube.com/watch?v=xxx) or just the video ID.
         */
        val trailer: String? = null,
        /** Release date string (e.g., "2024-01-15") from API metadata */
        val releaseDate: String? = null,
        // === External Authority IDs ===
        /** TMDB ID (numeric string for persistence compatibility) */
        val tmdbId: String? = null,
        /** IMDB ID (e.g., "tt0133093") */
        val imdbId: String? = null,
        /** TVDB ID */
        val tvdbId: String? = null,
        /**
         * Adult content flag for parental controls.
         */
        val isAdult: Boolean = false,
        val recognitionState: RecognitionState = RecognitionState.HEURISTIC,
        val createdAtMs: Long = 0L,
        val updatedAtMs: Long = 0L,
        val isDeleted: Boolean = false,
    )

    /**
     * Enrichment payload for [enrichIfAbsent].
     *
     * Contains **only** the fields that enrichment can touch — callers never need
     * to supply dummy values for IMMUTABLE fields like `type` or `displayTitle`.
     * All fields default to `null` / identity so callers specify only what they have.
     */
    data class Enrichment(
        // ENRICH_ONLY — set on entity only if currently null/default
        val season: Int? = null,
        val episode: Int? = null,
        val runtimeMs: Long? = null,
        val poster: ImageRef? = null,
        val backdrop: ImageRef? = null,
        val thumbnail: ImageRef? = null,
        val rating: Double? = null,
        val genres: String? = null,
        val plot: String? = null,
        val director: String? = null,
        val cast: String? = null,
        val trailer: String? = null,
        val releaseDate: String? = null,
        // ALWAYS_UPDATE — always overwrite with new non-null value
        val tmdbId: String? = null,
        val imdbId: String? = null,
        val tvdbId: String? = null,
        // MONOTONIC_UP — only upgrade, never downgrade
        val recognitionState: RecognitionState = RecognitionState.HEURISTIC,
    )

    // ──────────────────────────────────────────────────────────────────────
    // Single item
    // ──────────────────────────────────────────────────────────────────────

    suspend fun get(workKey: String): Work?

    /**
     * Batch lookup works by multiple work keys.
     *
     * **Performance Critical:** Use this instead of calling get() in a loop!
     * Required for efficient episode index building where hundreds of episodes
     * need their Work data loaded.
     *
     * @param workKeys List of work keys to lookup
     * @return Map of workKey → Work. Missing keys will not be in the map.
     */
    suspend fun getBatch(workKeys: List<String>): Map<String, Work>

    fun observe(workKey: String): Flow<Work?>

    // ──────────────────────────────────────────────────────────────────────
    // Lists (UI-critical)
    // ──────────────────────────────────────────────────────────────────────

    fun observeByType(
        type: WorkType,
        limit: Int = 200,
    ): Flow<List<Work>>

    fun observeRecentlyUpdated(limit: Int = 50): Flow<List<Work>>

    /**
     * Observe recently CREATED works (sorted by createdAt DESC).
     * Use for "Recently Added" UI - shows newly ingested content.
     */
    fun observeRecentlyCreated(limit: Int = 50): Flow<List<Work>>

    fun observeNeedsReview(limit: Int = 200): Flow<List<Work>>

    suspend fun searchByTitle(
        queryNormalized: String,
        limit: Int = 50,
    ): List<Work>

    // ──────────────────────────────────────────────────────────────────────
    // Advanced Query (Sort/Filter/Search)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Sort field options for content lists.
     */
    enum class SortField {
        TITLE,
        YEAR,
        RATING,
        RECENTLY_ADDED,
        RECENTLY_UPDATED,
        DURATION,
    }

    /**
     * Sort direction.
     */
    enum class SortDirection {
        ASCENDING,
        DESCENDING,
    }

    /**
     * Query options for advanced content retrieval.
     */
    data class QueryOptions(
        val type: WorkType? = null,
        val sortField: SortField = SortField.TITLE,
        val sortDirection: SortDirection = SortDirection.ASCENDING,
        val hideAdult: Boolean = false,
        val minRating: Double? = null,
        val genres: Set<String>? = null,
        val excludeGenres: Set<String>? = null,
        val yearRange: IntRange? = null,
        val limit: Int = 200,
    )

    /**
     * Observe works with advanced sorting and filtering.
     *
     * @param options Query configuration including sort and filter options
     * @return Flow of works matching the query
     */
    fun observeWithOptions(options: QueryOptions): Flow<List<Work>>

    /**
     * Create a PagingSource for paginated browsing.
     *
     * This is the preferred method for large catalog browsing with infinite scroll.
     * Returns a factory function that creates fresh PagingSource instances for Pager.
     *
     * **Performance:**
     * - O(1) memory per page
     * - Native database offset/limit (no full-table scan)
     * - Automatic invalidation on data changes
     *
     * **Usage:**
     * ```kotlin
     * val pager = Pager(
     *     config = PagingConfig(pageSize = 50),
     *     pagingSourceFactory = repository.pagingSourceFactory(options)
     * )
     * val flow = pager.flow.cachedIn(viewModelScope)
     * ```
     *
     * @param options Query configuration (type, sort, filter)
     * @return Factory function for creating PagingSource instances
     */
    fun pagingSourceFactory(options: QueryOptions): () -> androidx.paging.PagingSource<Int, Work>

    /**
     * Get total count of items matching the query options.
     *
     * Useful for UI indicators like "Showing 50 of 5,432 movies".
     *
     * @param options Query configuration
     * @return Total count of matching items
     */
    suspend fun count(options: QueryOptions): Int

    /**
     * Advanced search across multiple fields.
     *
     * Searches title, plot, cast, and director fields.
     *
     * @param query Search text (case-insensitive)
     * @param options Additional query options for filtering/sorting results
     * @return List of matching works
     */
    suspend fun advancedSearch(
        query: String,
        options: QueryOptions = QueryOptions(),
    ): List<Work>

    /**
     * Get all unique genres in the database.
     *
     * Useful for filter UI population.
     *
     * @return Set of unique genre names
     */
    suspend fun getAllGenres(): Set<String>

    /**
     * Get year range of available content.
     *
     * @param type Optional content type filter
     * @return Pair of (minYear, maxYear) or null if no content
     */
    suspend fun getYearRange(type: WorkType? = null): Pair<Int, Int>?

    // ──────────────────────────────────────────────────────────────────────
    // Writes (MVP)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Upsert by workKey (idempotent).
     */
    suspend fun upsert(work: Work): Work

    /**
     * Enrich an existing work with additional metadata (write-protected).
     *
     * **NX_CONSOLIDATION_PLAN Phase 1 — Field Guard**
     *
     * WRITE PROTECTION RULES:
     * - **IMMUTABLE** fields (set at creation, never overwritten):
     *   workKey, type, displayTitle, sortTitle, titleNormalized, year, createdAtMs
     * - **ENRICH_ONLY** fields (overwrite only if currently null/default):
     *   poster, backdrop, thumbnail, plot, genres, director, cast, rating,
     *   runtimeMs, trailer, releaseDate, season, episode, isAdult
     * - **ALWAYS_UPDATE** fields (always overwritten when new value non-null):
     *   tmdbId, imdbId, tvdbId
     * - **MONOTONIC_UP** for recognitionState:
     *   Only upgrades (HEURISTIC→CONFIRMED), never downgrades
     * - **AUTO**: updatedAtMs always set to current time
     *
     * @param workKey Key of the work to enrich
     * @param enrichment Enrichment data (only enrichable fields, all optional)
     * @return Updated work, or null if workKey doesn't exist
     */
    suspend fun enrichIfAbsent(workKey: String, enrichment: Enrichment): Work?

    /**
     * Enrich an existing work with metadata from a detail info API call.
     *
     * Unlike [enrichIfAbsent], this method treats the enrichment data as **authoritative**:
     * the detail API (`get_vod_info`, `get_series_info`) provides richer, more accurate
     * metadata than the listing API (`get_vod_streams`). Non-null enrichment values
     * always overwrite existing values.
     *
     * Field update rules:
     * - **IMMUTABLE** fields: same as [enrichIfAbsent] — never changed
     * - **DETAIL_OVERWRITE** fields (always overwrite with non-null enrichment value):
     *   poster, backdrop, thumbnail, plot, genres, director, cast, rating,
     *   runtimeMs, trailer, releaseDate, season, episode
     * - **ALWAYS_UPDATE** / **MONOTONIC_UP** / **AUTO**: same as [enrichIfAbsent]
     *
     * @param workKey Key of the work to enrich
     * @param enrichment Enrichment data from detail info API
     * @return Updated work, or null if workKey doesn't exist
     */
    suspend fun enrichFromDetail(workKey: String, enrichment: Enrichment): Work?

    suspend fun upsertBatch(works: List<Work>): List<Work>

    /**
     * Soft delete preferred. Implementation sets isDeleted=true and updates updatedAtMs.
     */
    suspend fun softDelete(workKey: String): Boolean
}

/**
 * Converts a full [NxWorkRepository.Work] into an [NxWorkRepository.Enrichment],
 * extracting only the enrichable fields. Used by callers that already have a full
 * Work (e.g., from [WorkEntityBuilder.build]) to pass into [NxWorkRepository.enrichIfAbsent]
 * or [NxWorkRepository.enrichFromDetail].
 */
fun NxWorkRepository.Work.toEnrichment(): NxWorkRepository.Enrichment = NxWorkRepository.Enrichment(
    season = season,
    episode = episode,
    runtimeMs = runtimeMs,
    poster = poster,
    backdrop = backdrop,
    thumbnail = thumbnail,
    rating = rating,
    genres = genres,
    plot = plot,
    director = director,
    cast = cast,
    trailer = trailer,
    releaseDate = releaseDate,
    tmdbId = tmdbId,
    imdbId = imdbId,
    tvdbId = tvdbId,
    recognitionState = recognitionState,
)
