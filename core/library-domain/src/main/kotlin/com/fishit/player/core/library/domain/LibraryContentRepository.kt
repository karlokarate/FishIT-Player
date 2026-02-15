package com.fishit.player.core.library.domain

import androidx.paging.PagingData
import com.fishit.player.core.model.ContentDisplayLimits
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
 * @param limit Maximum items to retrieve (used for non-paging methods only)
 */
data class LibraryQueryOptions(
    val sort: SortOption = SortOption.DEFAULT,
    val filter: FilterConfig = FilterConfig.DEFAULT,
    val limit: Int = 200,
) {
    companion object {
        val DEFAULT = LibraryQueryOptions()
        val RECENTLY_ADDED =
            LibraryQueryOptions(
                sort = SortOption.RECENTLY_ADDED,
            )
    }
}

/**
 * Paging configuration for library content.
 *
 * Uses values from [ContentDisplayLimits.LibraryPaging] as the centralized SSOT
 * for paging parameters across the entire app.
 *
 * @param pageSize Items per page (default from ContentDisplayLimits)
 * @param prefetchDistance How far ahead to prefetch (default = pageSize)
 * @param initialLoadSize Items for first load (default from ContentDisplayLimits)
 */
data class LibraryPagingConfig(
    val pageSize: Int = ContentDisplayLimits.LibraryPaging.PAGE_SIZE,
    val prefetchDistance: Int = ContentDisplayLimits.LibraryPaging.PREFETCH_DISTANCE,
    val initialLoadSize: Int = ContentDisplayLimits.LibraryPaging.INITIAL_LOAD_SIZE,
) {
    companion object {
        /** Default config for TV grids - uses centralized ContentDisplayLimits values */
        val DEFAULT = LibraryPagingConfig()

        /** Config for larger grids (30 items/page) */
        val GRID = LibraryPagingConfig(pageSize = 30, prefetchDistance = 30, initialLoadSize = 90)

        /** Config for compact lists (100 items/page) */
        val LIST = LibraryPagingConfig(pageSize = 100, prefetchDistance = 100, initialLoadSize = 300)
    }
}

// Repository interface for Library screen content.
//
// Architecture:
// - Contract lives in core modules
// - Implementations live in infra modules
interface LibraryContentRepository {
    // ──────────────────────────────────────────────────────────────────────
    // Legacy Flow-based methods (limited results)
    // ──────────────────────────────────────────────────────────────────────

    fun observeVod(categoryId: String? = null): Flow<List<LibraryMediaItem>>

    fun observeSeries(categoryId: String? = null): Flow<List<LibraryMediaItem>>

    fun observeVodCategories(): Flow<List<LibraryCategory>>

    fun observeSeriesCategories(): Flow<List<LibraryCategory>>

    suspend fun search(
        query: String,
        limit: Int = 50,
    ): List<LibraryMediaItem>

    // ──────────────────────────────────────────────────────────────────────
    // Advanced Query (Sort/Filter) - Limited
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

    // ──────────────────────────────────────────────────────────────────────
    // Paging 3 (Infinite Scroll)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Get paginated VOD items as a Flow of PagingData.
     *
     * This is the preferred method for browsing large catalogs.
     * Supports infinite scroll with automatic prefetching.
     *
     * **Usage in ViewModel:**
     * ```kotlin
     * val vodPagingFlow = repository.getVodPagingData(options, config)
     *     .cachedIn(viewModelScope)
     * ```
     *
     * **Usage in Compose:**
     * ```kotlin
     * val lazyPagingItems = viewModel.vodPagingFlow.collectAsLazyPagingItems()
     * LazyVerticalGrid(...) {
     *     items(lazyPagingItems.itemCount) { index ->
     *         lazyPagingItems[index]?.let { MediaTile(it) }
     *     }
     * }
     * ```
     *
     * @param options Sort and filter configuration
     * @param config Paging configuration (page size, prefetch distance)
     * @return Flow of PagingData for infinite scroll
     */
    fun getVodPagingData(
        options: LibraryQueryOptions = LibraryQueryOptions(),
        config: LibraryPagingConfig = LibraryPagingConfig.DEFAULT,
    ): Flow<PagingData<LibraryMediaItem>>

    /**
     * Get paginated Series items as a Flow of PagingData.
     *
     * @see getVodPagingData for usage details
     */
    fun getSeriesPagingData(
        options: LibraryQueryOptions = LibraryQueryOptions(),
        config: LibraryPagingConfig = LibraryPagingConfig.DEFAULT,
    ): Flow<PagingData<LibraryMediaItem>>

    // ──────────────────────────────────────────────────────────────────────
    // Metadata
    // ──────────────────────────────────────────────────────────────────────

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

    /**
     * Get total count of VOD items (for UI indicators).
     */
    suspend fun getVodCount(options: LibraryQueryOptions = LibraryQueryOptions()): Int

    /**
     * Get total count of Series items (for UI indicators).
     */
    suspend fun getSeriesCount(options: LibraryQueryOptions = LibraryQueryOptions()): Int
}

data class LibraryCategory(
    val id: String,
    val name: String,
    val itemCount: Int = 0,
)
