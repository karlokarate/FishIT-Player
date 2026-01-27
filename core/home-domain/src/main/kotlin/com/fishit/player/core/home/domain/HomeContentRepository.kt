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

    // ==================== Large Catalogs - DEPRECATED (Use Paging) ====================
    // These methods load too much data into memory for large catalogs (40K+ items).
    // Use the corresponding PagingData methods instead.
    
    @Deprecated("Use getMoviesPagingData() instead - this loads all movies into memory", ReplaceWith("getMoviesPagingData()"))
    fun observeMovies(): Flow<List<HomeMediaItem>>

    @Deprecated("Use getSeriesPagingData() instead - this loads all series into memory", ReplaceWith("getSeriesPagingData()"))
    fun observeSeries(): Flow<List<HomeMediaItem>>

    @Deprecated("Use getClipsPagingData() instead - this loads all clips into memory", ReplaceWith("getClipsPagingData()"))
    fun observeClips(): Flow<List<HomeMediaItem>>

    @Deprecated("Use getLivePagingData() instead - this loads all live channels into memory", ReplaceWith("getLivePagingData()"))
    fun observeXtreamLive(): Flow<List<HomeMediaItem>>
    
    // ==================== Paging Methods (Infinite Scroll) ====================
    // These are the preferred methods for large catalogs.
    
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

    // ==================== Legacy Methods (backward compatibility) ====================

    /**
     * @deprecated Use observeMovies(), observeSeries(), observeClips() instead.
     */
    fun observeTelegramMedia(): Flow<List<HomeMediaItem>>

    /**
     * @deprecated Use observeMovies() instead.
     */
    fun observeXtreamVod(): Flow<List<HomeMediaItem>>

    /**
     * @deprecated Use observeSeries() instead.
     */
    fun observeXtreamSeries(): Flow<List<HomeMediaItem>>
}
