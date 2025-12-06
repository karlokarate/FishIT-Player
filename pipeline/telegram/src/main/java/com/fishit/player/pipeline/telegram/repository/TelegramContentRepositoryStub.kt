package com.fishit.player.pipeline.telegram.repository

import com.fishit.player.pipeline.telegram.model.TelegramChat
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.model.TelegramMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Stub implementation of TelegramContentRepository for Phase 2.
 *
 * This implementation returns deterministic empty results and does not
 * make any actual TDLib calls. It exists to:
 * - Validate the interface design
 * - Enable compilation of dependent modules
 * - Serve as a placeholder for Phase 3+ real implementation
 *
 * All methods return empty lists or null values to simulate "no content"
 * scenarios safely during the stub phase.
 */
class TelegramContentRepositoryStub : TelegramContentRepository {
    /**
     * Returns an empty list of chats.
     *
     * Phase 3+ will populate from TDLib chat list with media filters.
     */
    override fun getChatsWithMedia(): Flow<List<TelegramChat>> = flowOf(emptyList())

    /**
     * Returns an empty list of messages.
     *
     * Phase 3+ will fetch messages from TDLib for the specified chat.
     */
    override fun getMessagesFromChat(
        chatId: Long,
        offset: Int,
        limit: Int,
    ): Flow<List<TelegramMessage>> = flowOf(emptyList())

    /**
     * Returns an empty list of media items.
     *
     * Phase 3+ will filter messages with media content.
     */
    override fun getMediaFromChat(
        chatId: Long,
        offset: Int,
        limit: Int,
    ): Flow<List<TelegramMediaItem>> = flowOf(emptyList())

    /**
     * Returns an empty list of all media items.
     *
     * Phase 3+ will aggregate media from all chats.
     */
    override fun getAllMedia(
        offset: Int,
        limit: Int,
    ): Flow<List<TelegramMediaItem>> = flowOf(emptyList())

    /**
     * Returns an empty search result.
     *
     * Phase 3+ will implement full-text search on captions and titles.
     */
    override fun searchMedia(
        query: String,
        offset: Int,
        limit: Int,
    ): Flow<List<TelegramMediaItem>> = flowOf(emptyList())

    /**
     * Returns an empty list of series media.
     *
     * Phase 3+ will group episodes by normalized series name.
     */
    override fun getSeriesMedia(seriesName: String): Flow<List<TelegramMediaItem>> = flowOf(emptyList())

    /**
     * Returns null (media item not found).
     *
     * Phase 3+ will fetch from ObjectBox or TDLib.
     */
    override suspend fun getMediaItem(
        chatId: Long,
        messageId: Long,
    ): TelegramMediaItem? = null
}
