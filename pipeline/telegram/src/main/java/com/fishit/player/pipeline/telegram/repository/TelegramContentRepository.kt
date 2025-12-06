package com.fishit.player.pipeline.telegram.repository

import com.fishit.player.pipeline.telegram.model.TelegramChat
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.model.TelegramMessage
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing Telegram media content.
 *
 * Phase 2: Interface-only stub with mock implementations.
 * Phase 3+: Real implementation using T_TelegramServiceClient and TDLib.
 *
 * This interface provides methods for:
 * - Listing chats with media content
 * - Browsing messages within chats
 * - Querying media items with filtering and pagination
 * - Searching content by title/caption
 *
 * Future enhancements (Phase 3+):
 * - Background sync integration
 * - Content heuristics for movie/series detection
 * - Integration with ObjectBox for local persistence
 */
interface TelegramContentRepository {
    /**
     * Get all chats that contain media content.
     *
     * @return Flow of chat list, updated reactively as new chats are discovered
     */
    fun getChatsWithMedia(): Flow<List<TelegramChat>>

    /**
     * Get messages from a specific chat.
     *
     * @param chatId Chat identifier
     * @param offset Pagination offset (message index to start from)
     * @param limit Maximum number of messages to return
     * @return Flow of message list
     */
    fun getMessagesFromChat(
        chatId: Long,
        offset: Int = 0,
        limit: Int = 50,
    ): Flow<List<TelegramMessage>>

    /**
     * Get media items from a specific chat.
     *
     * @param chatId Chat identifier
     * @param offset Pagination offset
     * @param limit Maximum number of items to return
     * @return Flow of media item list
     */
    fun getMediaFromChat(
        chatId: Long,
        offset: Int = 0,
        limit: Int = 50,
    ): Flow<List<TelegramMediaItem>>

    /**
     * Get all media items across all chats.
     *
     * @param offset Pagination offset
     * @param limit Maximum number of items to return
     * @return Flow of media item list
     */
    fun getAllMedia(
        offset: Int = 0,
        limit: Int = 50,
    ): Flow<List<TelegramMediaItem>>

    /**
     * Search media items by title or caption.
     *
     * @param query Search query string
     * @param offset Pagination offset
     * @param limit Maximum number of items to return
     * @return Flow of matching media item list
     */
    fun searchMedia(
        query: String,
        offset: Int = 0,
        limit: Int = 50,
    ): Flow<List<TelegramMediaItem>>

    /**
     * Get media items that are part of a series.
     *
     * @param seriesName Series name (normalized)
     * @return Flow of media item list for the series
     */
    fun getSeriesMedia(seriesName: String): Flow<List<TelegramMediaItem>>

    /**
     * Get a specific media item by chat and message IDs.
     *
     * @param chatId Chat identifier
     * @param messageId Message identifier
     * @return The media item, or null if not found
     */
    suspend fun getMediaItem(
        chatId: Long,
        messageId: Long,
    ): TelegramMediaItem?
}
