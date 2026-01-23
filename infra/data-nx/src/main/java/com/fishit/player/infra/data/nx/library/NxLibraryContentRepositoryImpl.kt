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
 */
package com.fishit.player.infra.data.nx.library

import com.fishit.player.core.library.domain.LibraryCategory
import com.fishit.player.core.library.domain.LibraryContentRepository
import com.fishit.player.core.library.domain.LibraryFilterConfig
import com.fishit.player.core.library.domain.LibraryMediaItem
import com.fishit.player.core.library.domain.LibraryQueryOptions
import com.fishit.player.core.library.domain.LibrarySortDirection
import com.fishit.player.core.library.domain.LibrarySortField
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
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
            poster = posterRef?.let { parseImageRef(it) },
            backdrop = backdropRef?.let { parseImageRef(it) },
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

    private fun parseImageRef(serialized: String): ImageRef? {
        val colonIndex = serialized.indexOf(':')
        if (colonIndex < 0) return null

        val type = serialized.substring(0, colonIndex)
        val value = serialized.substring(colonIndex + 1)

        return when (type) {
            "http" -> ImageRef.Http(url = value)
            "tg" -> ImageRef.TelegramThumb(remoteId = value)
            "file" -> ImageRef.LocalFile(path = value)
            else -> null
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

    // ==================== Mapping Helpers ====================

    private fun LibraryQueryOptions.toNxQueryOptions(workType: WorkType): NxWorkRepository.QueryOptions {
        return NxWorkRepository.QueryOptions(
            type = workType,
            sortField = sort.field.toNxSortField(),
            sortDirection = sort.direction.toNxSortDirection(),
            hideAdult = filter.hideAdult,
            minRating = filter.minRating,
            genres = filter.includeGenres,
            excludeGenres = filter.excludeGenres,
            yearRange = filter.yearRange,
            limit = limit,
        )
    }

    private fun LibrarySortField.toNxSortField(): NxWorkRepository.SortField {
        return when (this) {
            LibrarySortField.TITLE -> NxWorkRepository.SortField.TITLE
            LibrarySortField.YEAR -> NxWorkRepository.SortField.YEAR
            LibrarySortField.RATING -> NxWorkRepository.SortField.RATING
            LibrarySortField.RECENTLY_ADDED -> NxWorkRepository.SortField.RECENTLY_ADDED
            LibrarySortField.RECENTLY_UPDATED -> NxWorkRepository.SortField.RECENTLY_UPDATED
            LibrarySortField.DURATION -> NxWorkRepository.SortField.DURATION
        }
    }

    private fun LibrarySortDirection.toNxSortDirection(): NxWorkRepository.SortDirection {
        return when (this) {
            LibrarySortDirection.ASCENDING -> NxWorkRepository.SortDirection.ASCENDING
            LibrarySortDirection.DESCENDING -> NxWorkRepository.SortDirection.DESCENDING
        }
    }
}
