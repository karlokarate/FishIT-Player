package com.fishit.player.infra.data.xtream

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Xtream VOD and Series catalog operations.
 *
 * **Architecture Compliance (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - Data layer works with RawMediaMetadata (source-agnostic)
 * - NO pipeline-specific DTOs (XtreamVodItem etc. are internal to pipeline)
 * - Repositories consume catalog events and persist RawMediaMetadata
 *
 * **Layer Boundaries:**
 * - Transport → Pipeline → Data → Domain → UI
 * - Data layer never imports from pipeline layer
 * - Pipeline produces RawMediaMetadata, Data consumes it
 */
interface XtreamCatalogRepository {

    /**
     * Observe all VOD items as RawMediaMetadata.
     *
     * @param categoryId Optional category filter (from extras)
     * @return Flow of VOD items
     */
    fun observeVod(categoryId: String? = null): Flow<List<RawMediaMetadata>>

    /**
     * Observe all series items as RawMediaMetadata.
     *
     * @param categoryId Optional category filter
     * @return Flow of series items
     */
    fun observeSeries(categoryId: String? = null): Flow<List<RawMediaMetadata>>

    /**
     * Observe episodes for a specific series.
     *
     * Episodes are identified by sourceId pattern "xtream:episode:*"
     * and filtered by seriesId in extras.
     *
     * @param seriesId The series ID
     * @param seasonNumber Optional season filter
     * @return Flow of episode items
     */
    fun observeEpisodes(seriesId: String, seasonNumber: Int? = null): Flow<List<RawMediaMetadata>>

    /**
     * Get all catalog items (one-shot query).
     *
     * @param mediaType Filter by media type (MOVIE, SERIES_EPISODE, etc.)
     * @param limit Maximum number of items
     * @param offset Pagination offset
     * @return List of media items
     */
    suspend fun getAll(
        mediaType: MediaType? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<RawMediaMetadata>

    /**
     * Get item by source ID.
     *
     * @param sourceId The unique source identifier (e.g., "xtream:vod:123")
     * @return Media item or null if not found
     */
    suspend fun getBySourceId(sourceId: String): RawMediaMetadata?

    /**
     * Search catalog by title.
     *
     * @param query Search query
     * @param limit Maximum results
     * @return Matching media items
     */
    suspend fun search(query: String, limit: Int = 50): List<RawMediaMetadata>

    /**
     * Insert or update catalog items from pipeline.
     *
     * Called by CatalogSync when processing XtreamCatalogEvent.ItemDiscovered.
     *
     * @param items List of RawMediaMetadata from catalog pipeline
     */
    suspend fun upsertAll(items: List<RawMediaMetadata>)

    /**
     * Insert or update a single catalog item.
     *
     * @param item RawMediaMetadata to persist
     */
    suspend fun upsert(item: RawMediaMetadata)

    /**
     * Count total catalog items.
     *
     * @param mediaType Optional filter by media type
     * @return Total count
     */
    suspend fun count(mediaType: MediaType? = null): Long

    /**
     * Delete all stored catalog items.
     */
    suspend fun deleteAll()

    // =========================================================================
    // Info Backfill Support
    // =========================================================================

    /**
     * Get VOD IDs that need info backfill (missing plot/cast/director).
     *
     * @param limit Maximum IDs to return
     * @param afterId Cursor for pagination (return IDs > this)
     * @return List of VOD IDs needing backfill
     */
    suspend fun getVodIdsNeedingInfoBackfill(limit: Int = 50, afterId: Int = 0): List<Int>

    /**
     * Get series IDs that need info backfill (missing plot/cast).
     *
     * @param limit Maximum IDs to return
     * @param afterId Cursor for pagination (return IDs > this)
     * @return List of series IDs needing backfill
     */
    suspend fun getSeriesIdsNeedingInfoBackfill(limit: Int = 50, afterId: Int = 0): List<Int>

    /**
     * Update VOD with detailed info from get_vod_info API.
     *
     * @param vodId The VOD ID
     * @param plot Plot/description
     * @param director Director name(s)
     * @param cast Cast list
     * @param genre Genre(s)
     * @param rating Rating value
     * @param durationSecs Duration in seconds
     * @param trailer Trailer URL
     * @param tmdbId TMDB ID (from get_vod_info)
     */
    suspend fun updateVodInfo(
        vodId: Int,
        plot: String?,
        director: String?,
        cast: String?,
        genre: String?,
        rating: Double?,
        durationSecs: Int?,
        trailer: String?,
        tmdbId: String? = null,
    )

    /**
     * Update series with detailed info from get_series_info API.
     *
     * @param seriesId The series ID
     * @param plot Plot/description
     * @param director Director name(s)
     * @param cast Cast list
     * @param genre Genre(s)
     * @param rating Rating value
     * @param trailer Trailer URL
     * @param tmdbId TMDB ID (from get_series_info)
     */
    suspend fun updateSeriesInfo(
        seriesId: Int,
        plot: String?,
        director: String?,
        cast: String?,
        genre: String?,
        rating: Double?,
        trailer: String?,
        tmdbId: String? = null,
    )

    /**
     * Count VOD items still needing info backfill.
     *
     * @return Number of VOD items without plot/cast/director
     */
    suspend fun countVodNeedingInfoBackfill(): Long

    /**
     * Count series items still needing info backfill.
     *
     * @return Number of series items without plot/cast
     */
    suspend fun countSeriesNeedingInfoBackfill(): Long
}
