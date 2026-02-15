package com.fishit.player.core.home.domain

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing aggregated content for the Home screen.
 *
 * **Architecture:**
 * - Contract lives in core modules (domain API)
 * - Implementations live in infra modules (adapter/repository)
 * - UI features depend only on this interface
 *
 * **Paging Support:**
 * All content methods have paging variants (`*PagingData`) that return
 * `Flow<PagingData<HomeMediaItem>>` for infinite horizontal scroll.
 * Use these with `collectAsLazyPagingItems()` in Compose LazyRow.
 */
interface HomeContentRepository {
    // ==================== Special Rows (Flow-based, bounded) ====================
    // These rows have natural limits and are appropriate for Flow-based loading.

    fun observeContinueWatching(): Flow<List<HomeMediaItem>>

    fun observeRecentlyAdded(): Flow<List<HomeMediaItem>>

    // ==================== Paging Methods (Infinite Scroll) ====================
    // These are the only supported methods for large catalogs.
    // All content (Movies, Series, Clips, Live) MUST use Paging for performance.

    /**
     * Movies row with horizontal paging.
     * Loads movies in pages as user scrolls right.
     */
    fun getMoviesPagingData(): Flow<PagingData<HomeMediaItem>>

    /**
     * Series row with horizontal paging.
     * Loads series in pages as user scrolls right.
     */
    fun getSeriesPagingData(): Flow<PagingData<HomeMediaItem>>

    /**
     * Clips row with horizontal paging.
     * Loads clips in pages as user scrolls right.
     */
    fun getClipsPagingData(): Flow<PagingData<HomeMediaItem>>

    /**
     * Live TV row with horizontal paging.
     * Loads live channels in pages as user scrolls right.
     */
    fun getLivePagingData(): Flow<PagingData<HomeMediaItem>>

    /**
     * Recently Added row with horizontal paging.
     * Sorted by createdAt descending.
     */
    fun getRecentlyAddedPagingData(): Flow<PagingData<HomeMediaItem>>

    // ==================== Search ====================

    /**
     * Search all content by title.
     * Returns a flat list (not paged) for search results display.
     *
     * @param query Search query (case-insensitive, matches title contains)
     * @param limit Maximum results to return (default 50)
     * @return List of matching items across all content types
     */
    suspend fun search(
        query: String,
        limit: Int = 50,
    ): List<HomeMediaItem>
}
