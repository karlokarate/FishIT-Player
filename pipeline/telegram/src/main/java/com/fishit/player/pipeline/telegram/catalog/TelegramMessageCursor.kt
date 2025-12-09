package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.tdlib.TelegramClient
import dev.g000sha256.tdl.dto.Message

/**
 * Internal cursor for traversing Telegram chat message history.
 *
 * Implements TDLib pagination semantics:
 * - First page: fromMessageId=0, offset=0
 * - Subsequent pages: fromMessageId=oldest message ID, offset=-1 (avoids duplicates)
 *
 * **v1 Reference:** Adapted from T_ChatBrowser.kt message traversal pattern
 *
 * **Architecture:** Internal to catalog pipeline, not exposed outside the package.
 *
 * @property client TelegramClient for TDLib access
 * @property chatId Chat ID to traverse
 * @property maxMessages Maximum messages to fetch (null = unlimited)
 * @property minMessageTimestampMs Only fetch messages >= this timestamp
 * @property pageSize Messages per TDLib request (default 100, TDLib max)
 */
internal class TelegramMessageCursor(
    private val client: TelegramClient,
    private val chatId: Long,
    private val maxMessages: Long?,
    private val minMessageTimestampMs: Long?,
    private val pageSize: Int = 100
) {
    companion object {
        private const val TAG = "TelegramMessageCursor"
        private const val TDLIB_MAX_PAGE_SIZE = 100
    }

    private var currentMessageId: Long = 0 // 0 = start from latest
    private var fetchedCount: Long = 0
    private var reachedEnd = false

    /**
     * Check if more messages are available.
     *
     * @return true if cursor has more messages to fetch
     */
    fun hasNext(): Boolean {
        if (reachedEnd) return false

        // Check if we've reached maxMessages limit
        if (maxMessages != null && fetchedCount >= maxMessages) {
            return false
        }

        return true
    }

    /**
     * Fetch the next batch of messages.
     *
     * Returns empty list if no more messages are available or limits are reached.
     *
     * **TDLib Pagination:**
     * - First call: fromMessageId=0 (latest), offset=0
     * - Subsequent: fromMessageId=oldest from previous batch, offset=-1
     *
     * **Filtering:**
     * - Stops when message timestamp < minMessageTimestampMs
     * - Respects maxMessages limit
     *
     * @return List of messages (may be empty)
     */
    suspend fun nextBatch(): List<Message> {
        if (!hasNext()) {
            return emptyList()
        }

        try {
            // Calculate effective page size respecting maxMessages
            val remainingMessages = maxMessages?.let { it - fetchedCount } ?: Long.MAX_VALUE
            val effectivePageSize = pageSize.coerceAtMost(TDLIB_MAX_PAGE_SIZE)
                .coerceAtMost(remainingMessages.toInt())

            if (effectivePageSize <= 0) {
                reachedEnd = true
                return emptyList()
            }

            UnifiedLog.d(
                TAG,
                "Fetching batch: chatId=$chatId, fromMessageId=$currentMessageId, " +
                    "pageSize=$effectivePageSize, fetched=$fetchedCount"
            )

            // Fetch via TelegramClient (which calls TDLib getChatHistory internally)
            // Note: TelegramClient.fetchMediaMessages doesn't directly support our pagination needs,
            // so we'll need to use a lower-level method or extend TelegramClient.
            // For now, we'll assume TelegramClient has been extended with getMessagesPage method.
            val messages = fetchMessagesPage(effectivePageSize)

            if (messages.isEmpty()) {
                UnifiedLog.d(TAG, "No more messages in chat $chatId")
                reachedEnd = true
                return emptyList()
            }

            // Filter by timestamp if specified
            val filtered = if (minMessageTimestampMs != null) {
                messages.filter { msg ->
                    // TDLib message.date is in seconds, convert to ms
                    val timestampMs = msg.date.toLong() * 1000
                    val shouldInclude = timestampMs >= minMessageTimestampMs
                    if (!shouldInclude) {
                        UnifiedLog.d(
                            TAG,
                            "Stopping: message ${msg.id} timestamp $timestampMs < min $minMessageTimestampMs"
                        )
                        reachedEnd = true
                    }
                    shouldInclude
                }
            } else {
                messages
            }

            // Update cursor state
            fetchedCount += filtered.size

            // Update currentMessageId for next batch (oldest message ID from this batch)
            if (messages.isNotEmpty()) {
                currentMessageId = messages.last().id
            }

            // If we got fewer messages than requested, we've reached the end
            if (messages.size < effectivePageSize) {
                reachedEnd = true
            }

            UnifiedLog.d(TAG, "Fetched ${filtered.size} messages (total: $fetchedCount)")
            return filtered

        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Failed to fetch messages for chat $chatId", e)
            reachedEnd = true
            return emptyList()
        }
    }

    /**
     * Fetch a page of messages using TelegramClient.
     *
     * Uses TelegramClient.getMessagesPage which returns raw TDLib Message DTOs.
     */
    private suspend fun fetchMessagesPage(limit: Int): List<Message> {
        return client.getMessagesPage(
            chatId = chatId,
            fromMessageId = currentMessageId,
            limit = limit
        )
    }
}
