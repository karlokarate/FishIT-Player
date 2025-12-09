package com.fishit.player.infra.data.xtream

import com.fishit.player.core.model.RawMediaMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Xtream live TV operations.
 *
 * **Architecture Compliance (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - Data layer works with RawMediaMetadata (source-agnostic)
 * - NO pipeline-specific DTOs (XtreamChannel is internal to pipeline)
 * - Repositories consume catalog events and persist RawMediaMetadata
 *
 * **Layer Boundaries:**
 * - Transport → Pipeline → Data → Domain → UI
 * - Data layer never imports from pipeline layer
 * - Pipeline produces RawMediaMetadata, Data consumes it
 *
 * **EPG Note:**
 * EPG data is stored separately from channel metadata. This interface
 * handles channel metadata only. EPG queries use dedicated methods.
 */
interface XtreamLiveRepository {

    /**
     * Observe all live channels as RawMediaMetadata.
     *
     * Channels are identified by sourceId pattern "xtream:live:*"
     * and mediaType = LIVE.
     *
     * @param categoryId Optional category filter (from extras)
     * @return Flow of live channel items
     */
    fun observeChannels(categoryId: String? = null): Flow<List<RawMediaMetadata>>

    /**
     * Get all live channels (one-shot query).
     *
     * @param limit Maximum number of items
     * @param offset Pagination offset
     * @return List of live channel items
     */
    suspend fun getAll(limit: Int = 100, offset: Int = 0): List<RawMediaMetadata>

    /**
     * Get channel by source ID.
     *
     * @param sourceId The unique source identifier (e.g., "xtream:live:123")
     * @return Channel item or null if not found
     */
    suspend fun getBySourceId(sourceId: String): RawMediaMetadata?

    /**
     * Search channels by title.
     *
     * @param query Search query
     * @param limit Maximum results
     * @return Matching channel items
     */
    suspend fun search(query: String, limit: Int = 50): List<RawMediaMetadata>

    /**
     * Insert or update channels from pipeline.
     *
     * Called by CatalogSync when processing XtreamCatalogEvent.ItemDiscovered
     * for live channel items.
     *
     * @param items List of RawMediaMetadata from catalog pipeline
     */
    suspend fun upsertAll(items: List<RawMediaMetadata>)

    /**
     * Insert or update a single channel.
     *
     * @param item RawMediaMetadata to persist
     */
    suspend fun upsert(item: RawMediaMetadata)

    /**
     * Count total live channels.
     *
     * @return Total count
     */
    suspend fun count(): Long

    /**
     * Delete all stored channels.
     */
    suspend fun deleteAll()
}
