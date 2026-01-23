package com.fishit.player.core.library.domain

import com.fishit.player.core.model.filter.FilterConfig
import com.fishit.player.core.model.sort.SortOption
import kotlinx.coroutines.flow.Flow

/**
 * Query options combining sort and filter for library content.
 *
 * Uses unified core/model types instead of library-specific duplicates.
 * This enables sharing sort/filter logic across all screens (Home, Library, Live).
 *
 * @param sort Unified sort configuration from core/model/sort
 * @param filter Unified filter configuration from core/model/filter
 * @param limit Maximum items to retrieve
 */
data class LibraryQueryOptions(
    val sort: SortOption = SortOption.DEFAULT,
    val filter: FilterConfig = FilterConfig.DEFAULT,
    val limit: Int = 200,
) {
    companion object {
        val DEFAULT = LibraryQueryOptions()
        val RECENTLY_ADDED = LibraryQueryOptions(
            sort = SortOption.RECENTLY_ADDED,
        )
    }
}

// Repository interface for Library screen content.
//
// Architecture:
// - Contract lives in core modules
// - Implementations live in infra modules
interface LibraryContentRepository {
    fun observeVod(categoryId: String? = null): Flow<List<LibraryMediaItem>>

    fun observeSeries(categoryId: String? = null): Flow<List<LibraryMediaItem>>

    fun observeVodCategories(): Flow<List<LibraryCategory>>

    fun observeSeriesCategories(): Flow<List<LibraryCategory>>

    suspend fun search(
        query: String,
        limit: Int = 50,
    ): List<LibraryMediaItem>

    // ──────────────────────────────────────────────────────────────────────
    // Advanced Query (Sort/Filter)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Observe VOD items with sort and filter options.
     *
     * @param categoryId Optional category filter
     * @param options Sort and filter configuration
     * @return Flow of filtered and sorted items
     */
    fun observeVodWithOptions(
        categoryId: String? = null,
        options: LibraryQueryOptions = LibraryQueryOptions(),
    ): Flow<List<LibraryMediaItem>>

    /**
     * Observe Series items with sort and filter options.
     *
     * @param categoryId Optional category filter
     * @param options Sort and filter configuration
     * @return Flow of filtered and sorted items
     */
    fun observeSeriesWithOptions(
        categoryId: String? = null,
        options: LibraryQueryOptions = LibraryQueryOptions(),
    ): Flow<List<LibraryMediaItem>>

    /**
     * Get all unique genres available in the library.
     *
     * @return Set of unique genre names
     */
    suspend fun getAllGenres(): Set<String>

    /**
     * Get the year range of available content.
     *
     * @return Pair of (minYear, maxYear) or null if no content
     */
    suspend fun getYearRange(): Pair<Int, Int>?
}

data class LibraryCategory(
    val id: String,
    val name: String,
    val itemCount: Int = 0,
)
