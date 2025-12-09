package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.tdlib.TelegramClient
import dev.g000sha256.tdl.dto.Message

/**
 * Internal cursor for paginating through Telegram chat message history.
 *
 * Provides a simple iterator-like interface over TDLib's getChatHistory API using message ID cursors.
 *
 * **TDLib Pagination Pattern:**
 * - `fromMessageId=0` starts from the latest message
 * - Each page returns messages older than the cursor
 * - Next cursor = oldest message ID from the current page
 * - Empty page = end of history reached
 *
 * **Design Principles:**
 * - Stateful cursor (maintains position, counters)
 * - Pure read-only (no side effects, no downloads, no DB writes)
 * - Honors maxMessages and minTimestamp filters locally
 * - Thread-safe (intended for single-coroutine use)
 *
 * @property telegramClient TDLib client for fetching messages.
 * @property chatId Chat ID to scan.
 * @property maxMessagesPerChat Maximum messages to fetch (null = no limit).
 * @property minMessageTimestampMs Minimum message timestamp in milliseconds (null = no filter).
 * @property batchSize Number of messages to fetch per page (default 100, max 100 per TDLib).
 */
internal class TelegramMessageCursor(
    private val telegramClient: TelegramClient,
    private val chatId: Long,
    private val maxMessagesPerChat: Long? = null,
    private val minMessageTimestampMs: Long? = null,
    private val batchSize: Int = 100,
) {
    companion object {
        private const val TAG = "TelegramMessageCursor"
    }

    /** Current cursor position (message ID). 0 = start from latest. */
    private var fromMessageId: Long = 0

    /** Whether the end of history has been reached. */
    private var reachedEnd: Boolean = false

    /** Total number of messages scanned by this cursor. */
    private var scannedMessages: Long = 0

    /**
     * Check if more messages are available.
     *
     * @return True if more messages can be fetched, false if end reached or limits exceeded.
     */
    fun hasNext(): Boolean {
        if (reachedEnd) return false
        if (maxMessagesPerChat != null && scannedMessages >= maxMessagesPerChat) return false
        return true
    }

    /**
     * Fetch the next batch of messages.
     *
     * Returns messages from the current cursor position, filtered by timestamp if configured.
     * Updates the cursor position and counters for the next call.
     *
     * **Behavior:**
     * - First call (fromMessageId=0): starts from latest message
     * - Subsequent calls: continues from oldest message ID of previous batch
     * - Filters messages by minMessageTimestampMs if set
     * - Stops when empty batch received or limits reached
     *
     * @return List of messages in the batch (may be empty if end reached or filters exclude all).
     */
    suspend fun nextBatch(): List<Message> {
        if (!hasNext()) {
            return emptyList()
        }

        // Calculate effective batch size respecting maxMessagesPerChat
        val effectiveBatchSize =
            if (maxMessagesPerChat != null) {
                val remaining = maxMessagesPerChat - scannedMessages
                batchSize.coerceAtMost(remaining.toInt())
            } else {
                batchSize
            }

        if (effectiveBatchSize <= 0) {
            reachedEnd = true
            return emptyList()
        }

        try {
            // Fetch raw messages from TDLib
            val messages =
                telegramClient.getMessagesPage(
                    chatId = chatId,
                    fromMessageId = fromMessageId,
                    limit = effectiveBatchSize,
                )

            // Check if we reached the end
            if (messages.isEmpty()) {
                UnifiedLog.d(TAG, "Reached end of history for chat $chatId")
                reachedEnd = true
                return emptyList()
            }

            // Filter by timestamp if configured
            val filteredMessages =
                if (minMessageTimestampMs != null) {
                    val minTimestampSec = minMessageTimestampMs / 1000
                    messages.filter { it.date >= minTimestampSec.toInt() }
                } else {
                    messages
                }

            // Update cursor to oldest message ID
            val oldestMessage = messages.minByOrNull { it.id }
            if (oldestMessage != null) {
                fromMessageId = oldestMessage.id
            }

            // Update counter
            scannedMessages += messages.size.toLong()

            // Check if timestamp filter cut off remaining messages
            if (filteredMessages.isEmpty() && minMessageTimestampMs != null) {
                UnifiedLog.d(TAG, "All messages older than minTimestamp for chat $chatId, stopping scan")
                reachedEnd = true
            }

            return filteredMessages
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Error fetching messages for chat $chatId: ${e.message}", e)
            reachedEnd = true
            return emptyList()
        }
    }

    /**
     * Get the total number of messages scanned so far.
     */
    fun getScannedCount(): Long = scannedMessages
}
