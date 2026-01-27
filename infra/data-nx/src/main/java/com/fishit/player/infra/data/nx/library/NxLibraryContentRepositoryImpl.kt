/**
 * NX-based LibraryContentRepository implementation.
 *
 * This implementation reads from the NX work graph (v2 SSOT) instead of the
 * legacy ObxVod/ObxSeries/ObxCategory layer.
 *
 * **Architecture:**
 * - Feature layer (feature:library) defines LibraryContentRepository interface
 * - This implementation provides the data from NX repositories
 * - All reads go through NxWorkRepository, NxWorkSourceRefRepository
 *
 * **Migration Note:**
 * This replaces LibraryContentRepositoryAdapter which reads from legacy Obx* entities.
 *
 * **Category Handling:**
 * Categories are derived from NX_WorkSourceRef's sourceItemKind and metadata.
 * The category grouping is done by sourceItemKind for now (VOD, SERIES).
 *
 * **Sort/Filter:**
 * Uses unified core/model types (SortOption, FilterConfig) for shared sort/filter
 * logic across all screens. Maps to NxWorkRepository.QueryOptions internally.
 */
package com.fishit.player.infra.data.nx.library

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.fishit.player.core.library.domain.LibraryCategory
import com.fishit.player.core.library.domain.LibraryContentRepository
import com.fishit.player.core.library.domain.LibraryMediaItem
import com.fishit.player.core.library.domain.LibraryPagingConfig
import com.fishit.player.core.library.domain.LibraryQueryOptions
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.filter.FilterConfig
import com.fishit.player.core.model.filter.FilterCriterion
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.sort.SortDirection
import com.fishit.player.core.model.sort.SortField
import com.fishit.player.core.model.sort.SortOption
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NxLibraryContentRepositoryImpl @Inject constructor(
    private val workRepository: NxWorkRepository,
    private val sourceRefRepository: NxWorkSourceRefRepository,
) : LibraryContentRepository {

    companion object {
        private const val TAG = "NxLibraryContentRepo"
        private const val VOD_LIMIT = 500
        private const val SERIES_LIMIT = 500
        private const val SEARCH_LIMIT = 100
    }

    // ==================== VOD Content ====================

    override fun observeVod(categoryId: String?): Flow<List<LibraryMediaItem>> {
        return workRepository.observeByType(WorkType.MOVIE, limit = VOD_LIMIT)
            .map { works ->
                works.mapNotNull { work ->
                    val sourceType = determineSourceType(work.workKey)
                    work.toLibraryMediaItem(sourceType = sourceType)
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe VOD" }
                emit(emptyList())
            }
    }

    override fun observeVodCategories(): Flow<List<LibraryCategory>> {
        // For MVP: return a single "All Movies" category
        // TODO: Implement category grouping when NX_Category entity is added
        return workRepository.observeByType(WorkType.MOVIE, limit = VOD_LIMIT)
            .map { works ->
                listOf(
                    LibraryCategory(
                        id = "all_movies",
                        name = "All Movies",
                        itemCount = works.size,
                    ),
                )
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe VOD categories" }
                emit(emptyList())
            }
    }

    // ==================== Series Content ====================

    override fun observeSeries(categoryId: String?): Flow<List<LibraryMediaItem>> {
        return workRepository.observeByType(WorkType.SERIES, limit = SERIES_LIMIT)
            .map { works ->
                works.mapNotNull { work ->
                    val sourceType = determineSourceType(work.workKey)
                    work.toLibraryMediaItem(sourceType = sourceType)
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe series" }
                emit(emptyList())
            }
    }

    override fun observeSeriesCategories(): Flow<List<LibraryCategory>> {
        // For MVP: return a single "All Series" category
        // TODO: Implement category grouping when NX_Category entity is added
        return workRepository.observeByType(WorkType.SERIES, limit = SERIES_LIMIT)
            .map { works ->
                listOf(
                    LibraryCategory(
                        id = "all_series",
                        name = "All Series",
                        itemCount = works.size,
                    ),
                )
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe series categories" }
                emit(emptyList())
            }
    }

    // ==================== Search ====================

    override suspend fun search(query: String, limit: Int): List<LibraryMediaItem> {
        return try {
            val normalizedQuery = query.trim().lowercase()
            if (normalizedQuery.isBlank()) return emptyList()

            workRepository.searchByTitle(normalizedQuery, limit.coerceAtMost(SEARCH_LIMIT))
                .filter { it.type == WorkType.MOVIE || it.type == WorkType.SERIES }
                .mapNotNull { work ->
                    val sourceType = determineSourceType(work.workKey)
                    work.toLibraryMediaItem(sourceType = sourceType)
                }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Search failed for query: $query" }
            emptyList()
        }
    }

    // ==================== Mapping Helpers ====================

    private fun Work.toLibraryMediaItem(sourceType: SourceType): LibraryMediaItem {
        return LibraryMediaItem(
            id = workKey,
            title = displayTitle,
            poster = ImageRef.fromString(posterRef),
            backdrop = ImageRef.fromString(backdropRef),
            mediaType = mapWorkTypeToMediaType(type),
            sourceType = sourceType,
            year = year,
            rating = rating?.toFloat(),
            categoryId = null, // TODO: Add category support
            categoryName = null,
            genres = genres?.split(",")?.map { it.trim() } ?: emptyList(),
            description = plot,
            navigationId = workKey,
            navigationSource = sourceType,
        )
    }

    private suspend fun determineSourceType(workKey: String): SourceType {
        val sourceRefs = sourceRefRepository.findByWorkKey(workKey)
        if (sourceRefs.isEmpty()) return SourceType.UNKNOWN

        return when {
            sourceRefs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.XTREAM } ->
                SourceType.XTREAM
            sourceRefs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.TELEGRAM } ->
                SourceType.TELEGRAM
            sourceRefs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.IO } ->
                SourceType.IO
            else -> SourceType.UNKNOWN
        }
    }

    private fun mapWorkTypeToMediaType(type: WorkType): MediaType {
        return when (type) {
            WorkType.MOVIE -> MediaType.MOVIE
            WorkType.SERIES -> MediaType.SERIES
            WorkType.EPISODE -> MediaType.SERIES_EPISODE
            WorkType.LIVE_CHANNEL -> MediaType.LIVE
            WorkType.CLIP -> MediaType.CLIP
            WorkType.AUDIOBOOK -> MediaType.AUDIOBOOK
            WorkType.MUSIC_TRACK -> MediaType.MUSIC
            WorkType.UNKNOWN -> MediaType.UNKNOWN
        }
    }

    // ==================== Advanced Query (Sort/Filter) ====================

    override fun observeVodWithOptions(
        categoryId: String?,
        options: LibraryQueryOptions,
    ): Flow<List<LibraryMediaItem>> {
        val queryOptions = options.toNxQueryOptions(WorkType.MOVIE)
        return workRepository.observeWithOptions(queryOptions)
            .map { works ->
                works.mapNotNull { work ->
                    val sourceType = determineSourceType(work.workKey)
                    work.toLibraryMediaItem(sourceType = sourceType)
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe VOD with options" }
                emit(emptyList())
            }
    }

    override fun observeSeriesWithOptions(
        categoryId: String?,
        options: LibraryQueryOptions,
    ): Flow<List<LibraryMediaItem>> {
        val queryOptions = options.toNxQueryOptions(WorkType.SERIES)
        return workRepository.observeWithOptions(queryOptions)
            .map { works ->
                works.mapNotNull { work ->
                    val sourceType = determineSourceType(work.workKey)
                    work.toLibraryMediaItem(sourceType = sourceType)
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe series with options" }
                emit(emptyList())
            }
    }

    override suspend fun getAllGenres(): Set<String> {
        return try {
            workRepository.getAllGenres()
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to get all genres" }
            emptySet()
        }
    }

    override suspend fun getYearRange(): Pair<Int, Int>? {
        return try {
            workRepository.getYearRange()
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to get year range" }
            null
        }
    }

    // ==================== Paging (Infinite Scroll) ====================

    override fun getVodPagingData(
        options: LibraryQueryOptions,
        config: LibraryPagingConfig,
    ): Flow<PagingData<LibraryMediaItem>> {
        val queryOptions = options.toNxQueryOptions(WorkType.MOVIE)
        val pagingConfig = PagingConfig(
            pageSize = config.pageSize,
            prefetchDistance = config.prefetchDistance,
            initialLoadSize = config.initialLoadSize,
            enablePlaceholders = false,
        )
        return Pager(
            config = pagingConfig,
            pagingSourceFactory = workRepository.pagingSourceFactory(queryOptions),
        ).flow.map { pagingData ->
            pagingData.map { work ->
                work.toLibraryMediaItemSync()
            }
        }
    }

    override fun getSeriesPagingData(
        options: LibraryQueryOptions,
        config: LibraryPagingConfig,
    ): Flow<PagingData<LibraryMediaItem>> {
        val queryOptions = options.toNxQueryOptions(WorkType.SERIES)
        val pagingConfig = PagingConfig(
            pageSize = config.pageSize,
            prefetchDistance = config.prefetchDistance,
            initialLoadSize = config.initialLoadSize,
            enablePlaceholders = false,
        )
        return Pager(
            config = pagingConfig,
            pagingSourceFactory = workRepository.pagingSourceFactory(queryOptions),
        ).flow.map { pagingData ->
            pagingData.map { work ->
                work.toLibraryMediaItemSync()
            }
        }
    }

    override suspend fun getVodCount(options: LibraryQueryOptions): Int {
        return try {
            val queryOptions = options.toNxQueryOptions(WorkType.MOVIE)
            workRepository.count(queryOptions)
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to get VOD count" }
            0
        }
    }

    override suspend fun getSeriesCount(options: LibraryQueryOptions): Int {
        return try {
            val queryOptions = options.toNxQueryOptions(WorkType.SERIES)
            workRepository.count(queryOptions)
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to get series count" }
            0
        }
    }

    /**
     * Synchronous mapping for Paging (cannot use suspend in PagingData.map).
     * Uses cached SourceType determination or defaults to XTREAM.
     */
    private fun Work.toLibraryMediaItemSync(): LibraryMediaItem {
        // For paging performance, we determine source type from workKey prefix
        // instead of doing a DB lookup for each item
        val sourceType = workKey.sourceTypeFromPrefix()
        return toLibraryMediaItem(sourceType = sourceType)
    }

    /**
     * Fast source type determination from workKey prefix.
     * WorkKey format: "{source}_{accountId}_{itemId}" e.g. "xtream_1_12345"
     */
    private fun String.sourceTypeFromPrefix(): SourceType {
        return when {
            startsWith("xtream_") || startsWith("x_") -> SourceType.XTREAM
            startsWith("telegram_") || startsWith("tg_") -> SourceType.TELEGRAM
            startsWith("io_") || startsWith("local_") -> SourceType.IO
            else -> SourceType.UNKNOWN
        }
    }

    // ==================== Mapping Helpers (Unified core/model types) ====================

    /**
     * Map LibraryQueryOptions (using unified core/model types) to NxWorkRepository.QueryOptions.
     *
     * This bridges the unified SortOption/FilterConfig to the NX repository's internal query model.
     */
    private fun LibraryQueryOptions.toNxQueryOptions(workType: WorkType): NxWorkRepository.QueryOptions {
        return NxWorkRepository.QueryOptions(
            type = workType,
            sortField = sort.field.toNxSortField(),
            sortDirection = sort.direction.toNxSortDirection(),
            hideAdult = filter.hideAdult,
            minRating = filter.extractMinRating(),
            genres = filter.extractIncludedGenres(),
            excludeGenres = filter.excludedGenres,
            yearRange = filter.extractYearRange(),
            limit = limit,
        )
    }

    /**
     * Map unified SortField (core/model) to NxWorkRepository.SortField.
     */
    private fun SortField.toNxSortField(): NxWorkRepository.SortField {
        return when (this) {
            SortField.TITLE -> NxWorkRepository.SortField.TITLE
            SortField.YEAR -> NxWorkRepository.SortField.YEAR
            SortField.RATING -> NxWorkRepository.SortField.RATING
            SortField.RECENTLY_ADDED -> NxWorkRepository.SortField.RECENTLY_ADDED
            SortField.RECENTLY_UPDATED -> NxWorkRepository.SortField.RECENTLY_UPDATED
            SortField.DURATION -> NxWorkRepository.SortField.DURATION
            SortField.GENRE -> NxWorkRepository.SortField.TITLE // Fallback - NX doesn't support genre sort
        }
    }

    /**
     * Map unified SortDirection (core/model) to NxWorkRepository.SortDirection.
     */
    private fun SortDirection.toNxSortDirection(): NxWorkRepository.SortDirection {
        return when (this) {
            SortDirection.ASCENDING -> NxWorkRepository.SortDirection.ASCENDING
            SortDirection.DESCENDING -> NxWorkRepository.SortDirection.DESCENDING
        }
    }

    /**
     * Extract minimum rating from FilterConfig criteria.
     */
    private fun FilterConfig.extractMinRating(): Double? {
        return criteria
            .filterIsInstance<FilterCriterion.RatingMinimum>()
            .firstOrNull { it.isActive }
            ?.minRating
    }

    /**
     * Extract included genres from FilterConfig criteria.
     */
    private fun FilterConfig.extractIncludedGenres(): Set<String>? {
        val included = criteria
            .filterIsInstance<FilterCriterion.GenreInclude>()
            .firstOrNull { it.isActive }
            ?.genres
        return if (included.isNullOrEmpty()) null else included
    }

    /**
     * Extract year range from FilterConfig criteria.
     */
    private fun FilterConfig.extractYearRange(): IntRange? {
        val yearCriterion = criteria
            .filterIsInstance<FilterCriterion.YearRange>()
            .firstOrNull { it.isActive }
            ?: return null

        val min = yearCriterion.minYear ?: return null
        val max = yearCriterion.maxYear ?: return null
        return min..max
    }
}
