package com.fishit.player.pipeline.xtream.repository

import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSearchResult
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Xtream VOD and Series catalog operations.
 *
 * This interface defines the contract for accessing Xtream VOD and series content.
 * Implementations may use local cache (ObjectBox), remote API, or both.
 *
 * Phase 2 stub: Returns empty/predictable results.
 */
interface XtreamCatalogRepository {
    /**
     * Retrieves a list of VOD items, optionally filtered by category.
     *
     * @param categoryId Optional category filter
     * @param limit Maximum number of items to return
     * @param offset Pagination offset
     * @return Flow emitting list of VOD items
     */
    fun getVodItems(
        categoryId: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): Flow<List<XtreamVodItem>>

    /**
     * Retrieves a single VOD item by ID.
     *
     * @param vodId The VOD item ID
     * @return Flow emitting the VOD item, or null if not found
     */
    fun getVodById(vodId: Int): Flow<XtreamVodItem?>

    /**
     * Retrieves a list of series items, optionally filtered by category.
     *
     * @param categoryId Optional category filter
     * @param limit Maximum number of items to return
     * @param offset Pagination offset
     * @return Flow emitting list of series items
     */
    fun getSeriesItems(
        categoryId: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): Flow<List<XtreamSeriesItem>>

    /**
     * Retrieves a single series item by ID.
     *
     * @param seriesId The series ID
     * @return Flow emitting the series item, or null if not found
     */
    fun getSeriesById(seriesId: Int): Flow<XtreamSeriesItem?>

    /**
     * Retrieves episodes for a specific series and season.
     *
     * @param seriesId The series ID
     * @param seasonNumber The season number
     * @return Flow emitting list of episodes
     */
    fun getEpisodes(
        seriesId: Int,
        seasonNumber: Int,
    ): Flow<List<XtreamEpisode>>

    /**
     * Searches VOD and series content by query string.
     *
     * @param query The search query
     * @param limit Maximum number of results
     * @return Flow emitting search results (VOD and series combined)
     */
    fun search(
        query: String,
        limit: Int = 50,
    ): Flow<List<XtreamSearchResult>>

    /**
     * Refreshes the catalog from the remote source.
     *
     * @return Result indicating success or failure
     */
    suspend fun refreshCatalog(): Result<Unit>
}
