package com.fishit.player.infra.data.telegram

import com.fishit.player.core.model.RawMediaMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing Telegram media content in FishIT Player v2.
 *
 * **Architecture Compliance (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - Data layer works with RawMediaMetadata (source-agnostic)
 * - NO pipeline-specific DTOs (TelegramMediaItem is internal to pipeline)
 * - Repositories consume catalog events and persist RawMediaMetadata
 *
 * **Layer Boundaries:**
 * - Transport → Pipeline → Data → Domain → UI
 * - Data layer never imports from pipeline layer
 * - Pipeline produces RawMediaMetadata, Data consumes it
 */
interface TelegramContentRepository {
    /**
     * Observe all stored Telegram media as RawMediaMetadata.
     *
     * @return Flow of all media items
     */
    fun observeAll(): Flow<List<RawMediaMetadata>>

    /**
     * Observe media from a specific chat.
     *
     * @param chatId Telegram chat ID
     * @return Flow of media items from the specified chat
     */
    fun observeByChat(chatId: Long): Flow<List<RawMediaMetadata>>

    /**
     * Get all media items (one-shot query).
     *
     * @param limit Maximum number of items
     * @param offset Pagination offset
     * @return List of media items
     */
    suspend fun getAll(
        limit: Int = 50,
        offset: Int = 0,
    ): List<RawMediaMetadata>

    /**
     * Get media by source ID.
     *
     * @param sourceId The unique source identifier (e.g., "msg:chatId:messageId")
     * @return Media item or null if not found
     */
    suspend fun getBySourceId(sourceId: String): RawMediaMetadata?

    /**
     * Search media by title.
     *
     * @param query Search query
     * @param limit Maximum results
     * @return Matching media items
     */
    suspend fun search(
        query: String,
        limit: Int = 50,
    ): List<RawMediaMetadata>

    /**
     * Insert or update media items from catalog pipeline.
     *
     * Called by CatalogSync when processing TelegramCatalogEvent.ItemDiscovered.
     *
     * @param items List of RawMediaMetadata from catalog pipeline
     */
    suspend fun upsertAll(items: List<RawMediaMetadata>)

    /**
     * Insert or update a single media item.
     *
     * @param item RawMediaMetadata to persist
     */
    suspend fun upsert(item: RawMediaMetadata)

    /**
     * Get all chat IDs that have media content.
     *
     * @return List of distinct chat IDs
     */
    suspend fun getAllChatIds(): List<Long>

    /**
     * Count total media items.
     *
     * @return Total count
     */
    suspend fun count(): Long

    /**
     * Delete all stored media.
     */
    suspend fun deleteAll()

    // =========================================================================
    // Canonical Linking Support (for CanonicalLinkingBacklogWorker)
    // =========================================================================

    /**
     * Get items that need canonical linking.
     *
     * Queries items that exist in ObxTelegramMessage but do NOT have a corresponding
     * MediaSourceRef entry in ObxMediaSourceRef.
     *
     * This enables the CanonicalLinkingBacklogWorker to process only unlinked items,
     * avoiding duplicate work on already-linked items.
     *
     * **Implementation Strategy:**
     * Query all items and filter out those whose sourceId exists in ObxMediaSourceRef.
     *
     * @param limit Maximum number of items to return
     * @return List of unlinked items
     */
    suspend fun getUnlinkedForCanonicalLinking(limit: Int = 100): List<RawMediaMetadata>

    /**
     * Count items that need canonical linking.
     *
     * @return Number of unlinked items
     */
    suspend fun countUnlinkedForCanonicalLinking(): Long
}
