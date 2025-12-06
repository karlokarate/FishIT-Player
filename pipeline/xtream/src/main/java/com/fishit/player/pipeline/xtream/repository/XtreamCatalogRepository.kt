package com.fishit.player.pipeline.xtream.repository

import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing Xtream VOD and Series catalog content.
 *
 * This interface defines the contract for browsing and searching VOD items
 * and TV series from Xtream providers. In Phase 2, implementations are stubs
 * that return empty/mock data.
 *
 * ## Responsibilities
 * - List available VOD items by category
 * - Search VOD content by title
 * - List available TV series
 * - Fetch episodes for a specific series
 * - Single-item lookups by ID
 *
 * ## Phase 3+ Implementation
 * Full implementation will include:
 * - Real Xtream API network calls
 * - Content caching
 * - Pagination support
 * - Advanced filtering and search
 */
interface XtreamCatalogRepository {
    /**
     * Retrieves all available VOD items, optionally filtered by category.
     *
     * @param categoryId Optional category ID to filter results. If null, returns all VOD items.
     * @return Flow emitting list of VOD items (empty in Phase 2 stub)
     */
    fun getVodItems(categoryId: Long? = null): Flow<List<XtreamVodItem>>

    /**
     * Searches VOD items by title.
     *
     * @param query Search query string
     * @return Flow emitting matching VOD items (empty in Phase 2 stub)
     */
    fun searchVodItems(query: String): Flow<List<XtreamVodItem>>

    /**
     * Retrieves a single VOD item by ID.
     *
     * @param vodId Unique VOD item identifier
     * @return VOD item if found, null otherwise (null in Phase 2 stub)
     */
    suspend fun getVodById(vodId: Long): XtreamVodItem?

    /**
     * Retrieves all available TV series, optionally filtered by category.
     *
     * @param categoryId Optional category ID to filter results. If null, returns all series.
     * @return Flow emitting list of series (empty in Phase 2 stub)
     */
    fun getSeries(categoryId: Long? = null): Flow<List<XtreamSeriesItem>>

    /**
     * Searches TV series by title.
     *
     * @param query Search query string
     * @return Flow emitting matching series (empty in Phase 2 stub)
     */
    fun searchSeries(query: String): Flow<List<XtreamSeriesItem>>

    /**
     * Retrieves a single TV series by ID.
     *
     * @param seriesId Unique series identifier
     * @return Series if found, null otherwise (null in Phase 2 stub)
     */
    suspend fun getSeriesById(seriesId: Long): XtreamSeriesItem?

    /**
     * Retrieves all episodes for a specific TV series.
     *
     * @param seriesId Unique series identifier
     * @return Flow emitting list of episodes ordered by season/episode (empty in Phase 2 stub)
     */
    fun getEpisodes(seriesId: Long): Flow<List<XtreamEpisode>>

    /**
     * Retrieves episodes for a specific season of a TV series.
     *
     * @param seriesId Unique series identifier
     * @param seasonNumber Season number (1-based)
     * @return Flow emitting list of episodes in the season (empty in Phase 2 stub)
     */
    fun getEpisodesBySeason(
        seriesId: Long,
        seasonNumber: Int,
    ): Flow<List<XtreamEpisode>>

    /**
     * Retrieves a specific episode by series ID, season, and episode number.
     *
     * @param seriesId Unique series identifier
     * @param seasonNumber Season number (1-based)
     * @param episodeNumber Episode number (1-based)
     * @return Episode if found, null otherwise (null in Phase 2 stub)
     */
    suspend fun getEpisode(
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
    ): XtreamEpisode?
}
