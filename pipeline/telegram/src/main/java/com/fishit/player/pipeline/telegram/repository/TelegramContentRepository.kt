package com.fishit.player.pipeline.telegram.repository

import com.fishit.player.pipeline.telegram.model.TelegramChatSummary
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem

/**
 * Repository interface for accessing Telegram media content in FishIT Player v2.
 *
 * This is a STUB interface for Phase 2 Task 3 (P2-T3).
 * Defines the contract for retrieving and filtering Telegram media items.
 *
 * Future implementations will integrate with TDLib and ObxTelegramMessage storage.
 */
interface TelegramContentRepository {
    /**
     * Retrieves all available Telegram media items.
     *
     * STUB: Returns empty list in stub implementation.
     *
     * @param limit Maximum number of items to return
     * @param offset Pagination offset
     * @return List of media items
     */
    suspend fun getAllMediaItems(
        limit: Int = 50,
        offset: Int = 0,
    ): List<TelegramMediaItem>

    /**
     * Retrieves media items from a specific chat.
     *
     * STUB: Returns empty list in stub implementation.
     *
     * @param chatId Telegram chat ID
     * @param limit Maximum number of items to return
     * @param offset Pagination offset
     * @return List of media items from the specified chat
     */
    suspend fun getMediaItemsByChat(
        chatId: Long,
        limit: Int = 50,
        offset: Int = 0,
    ): List<TelegramMediaItem>

    /**
     * Retrieves recent media items ordered by date.
     *
     * STUB: Returns empty list in stub implementation.
     *
     * @param limit Maximum number of items to return
     * @return List of recent media items
     */
    suspend fun getRecentMediaItems(limit: Int = 20): List<TelegramMediaItem>

    /**
     * Searches media items by title or caption.
     *
     * STUB: Returns empty list in stub implementation.
     *
     * @param query Search query string
     * @param limit Maximum number of items to return
     * @return List of matching media items
     */
    suspend fun searchMediaItems(
        query: String,
        limit: Int = 50,
    ): List<TelegramMediaItem>

    /**
     * Retrieves media items that are part of a series.
     *
     * STUB: Returns empty list in stub implementation.
     *
     * @param seriesName Series name to filter by
     * @param limit Maximum number of items to return
     * @return List of series episodes
     */
    suspend fun getSeriesMediaItems(
        seriesName: String,
        limit: Int = 50,
    ): List<TelegramMediaItem>

    /**
     * Retrieves a single media item by its ID.
     *
     * STUB: Returns null in stub implementation.
     *
     * @param id Media item ID
     * @return Media item or null if not found
     */
    suspend fun getMediaItemById(id: Long): TelegramMediaItem?

    /**
     * Retrieves all chats that contain media items.
     *
     * STUB: Returns empty list in stub implementation.
     *
     * @return List of chat summaries
     */
    suspend fun getAllChats(): List<TelegramChatSummary>

    /**
     * Refreshes the content repository from Telegram.
     *
     * STUB: No-op in stub implementation.
     * Future implementations will trigger sync with TDLib.
     */
    suspend fun refresh()
}
