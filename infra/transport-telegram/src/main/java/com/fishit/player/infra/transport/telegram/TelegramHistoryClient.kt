package com.fishit.player.infra.transport.telegram

import com.fishit.player.infra.transport.telegram.api.TgChat
import com.fishit.player.infra.transport.telegram.api.TgMessage
import kotlinx.coroutines.flow.Flow

/**
 * Typed interface for Telegram chat and message history operations.
 *
 * This is part of the v2 Transport API Surface. Pipeline layer consumes
 * this interface to fetch messages for catalog ingestion.
 *
 * **v2 Architecture:**
 * - Returns [TgMessage], [TgChat] wrapper types (not raw TDLib DTOs)
 * - No media classification/normalization (belongs in pipeline)
 * - No persistence (belongs in data layer)
 *
 * **Paging Rule (critical):**
 * Per TDLib semantics, `getChatHistory` requires:
 * - First page: `fromMessageId=0`, `offset=0`
 * - Subsequent pages: `fromMessageId=oldestMsgId`, `offset=-1` (to avoid duplicates)
 *
 * **Implementation:** [DefaultTelegramClient] implements this interface internally.
 *
 * @see TelegramAuthClient for authentication
 * @see TelegramFileClient for file downloads
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
interface TelegramHistoryClient {

    /**
     * Stream of incoming messages (live updates).
     *
     * Emits new messages as they arrive. Pipeline can use this for
     * warm ingestion (new content appears without manual refresh).
     *
     * **Note:** Emits ALL messages, not just media. Pipeline filters.
     */
    val messageUpdates: Flow<TgMessage>

    /**
     * Get list of available chats.
     *
     * Returns chats from the main chat list, ordered by last message time.
     * Excludes bot chats per product decision.
     *
     * @param limit Maximum number of chats to return
     * @return List of chat info (id, title, type, member count, last message)
     */
    suspend fun getChats(limit: Int = 100): List<TgChat>

    /**
     * Get a single chat by ID.
     *
     * Uses internal cache to reduce API calls.
     *
     * @param chatId Telegram chat ID
     * @return Chat info or null if not found
     */
    suspend fun getChat(chatId: Long): TgChat?

    /**
     * Fetch message history from a chat (paged).
     *
     * **Paging Rule:**
     * - First page: `fromMessageId=0`, `offset=0`
     * - Subsequent pages: `fromMessageId=oldestMsgId`, `offset=-1`
     *
     * @param chatId Chat ID to fetch from
     * @param limit Maximum messages per page (default 100)
     * @param fromMessageId Starting message ID for pagination (0 = most recent)
     * @param offset Offset from fromMessageId (0 for first page, -1 for subsequent)
     * @return List of messages in reverse chronological order (newest first)
     */
    suspend fun fetchMessages(
        chatId: Long,
        limit: Int = 100,
        fromMessageId: Long = 0,
        offset: Int = 0
    ): List<TgMessage>

    /**
     * Load complete message history from a chat.
     *
     * Iterates through all pages until history is exhausted.
     * Use with caution for large chats.
     *
     * **Product decision:** This is used for background backfill.
     * During playback, backfill should be paused (see playback policy).
     *
     * @param chatId Chat ID to load
     * @param pageSize Messages per page (default 100)
     * @param maxMessages Safety limit (default 10000)
     * @param onProgress Optional callback for progress updates
     * @return Complete list of messages
     */
    suspend fun loadAllMessages(
        chatId: Long,
        pageSize: Int = 100,
        maxMessages: Int = 10000,
        onProgress: ((loaded: Int) -> Unit)? = null
    ): List<TgMessage>

    /**
     * Search for messages in a chat.
     *
     * @param chatId Chat to search in
     * @param query Search query text
     * @param limit Maximum results (default 100)
     * @return List of matching messages
     */
    suspend fun searchMessages(
        chatId: Long,
        query: String,
        limit: Int = 100
    ): List<TgMessage>
}
