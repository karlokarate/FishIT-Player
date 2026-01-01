package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.adapter.TelegramChatInfo
import com.fishit.player.pipeline.telegram.adapter.TelegramPipelineAdapter
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Cursor-based chat history traversal.
 *
 * Uses TDLib's fromMessageId semantics:
 * - fromMessageId=0 → start from latest
 * - subsequent pages use last message ID as cursor
 *
 * **Design:**
 * - Stateful cursor (tracks position, counts)
 * - Client-side timestamp filtering via minMessageTimestampMs
 * - Respects maxMessages quota
 * - Cancellation-aware via coroutine context
 *
 * **Usage:**
 * ```kotlin
 * val cursor = TelegramMessageCursor(adapter, chat, config)
 * while (cursor.hasNext()) {
 *     val batch = cursor.nextBatch()
 *     // process batch
 * }
 * ```
 *
 * @property adapter TelegramPipelineAdapter for message fetching
 * @property chat Target chat info
 * @property maxMessages Maximum messages to fetch (null = no limit)
 * @property minMessageTimestampMs Minimum message timestamp filter (null = no filter)
 * @property pageSize Messages per page
 */
internal class TelegramMessageCursor(
    private val adapter: TelegramPipelineAdapter,
    private val chat: TelegramChatInfo,
    private val maxMessages: Long?,
    private val minMessageTimestampMs: Long?,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    private var fromMessageId: Long = 0L
    private var reachedEnd: Boolean = false
    private var scannedMessages: Long = 0L

    /**
     * Fetch the next batch of media items from the chat.
     *
     * **TDLib Async Loading Handling:**
     * TDLib may return empty results while loading data from the server in the background.
     * We retry up to [EMPTY_PAGE_MAX_RETRIES] times with exponential backoff before
     * concluding that the chat history is truly exhausted.
     *
     * @return List of TelegramMediaItem (may be empty if no more media or quota reached)
     */
    suspend fun nextBatch(): List<TelegramMediaItem> {
        // Check coroutine cancellation
        if (!currentCoroutineContext().isActive) {
            reachedEnd = true
            return emptyList()
        }

        if (reachedEnd) return emptyList()

        // Check quota
        if (maxMessages != null && scannedMessages >= maxMessages) {
            reachedEnd = true
            return emptyList()
        }

        // Calculate limit respecting quota
        val remainingForQuota = (maxMessages?.let { it - scannedMessages } ?: pageSize.toLong())
            .coerceAtMost(pageSize.toLong())
            .toInt()

        // Fetch page with retry for TDLib async loading
        // TDLib may return empty while loading from server - retry with backoff
        var page: List<TelegramMediaItem> = emptyList()
        var emptyRetries = 0
        
        while (emptyRetries <= EMPTY_PAGE_MAX_RETRIES) {
            page = adapter.fetchMediaMessages(
                chatId = chat.chatId,
                limit = remainingForQuota,
                offsetMessageId = fromMessageId,
            )
            
            if (page.isNotEmpty()) {
                break // Got data, continue processing
            }
            
            // Empty page - might be TDLib async loading, retry with backoff
            if (emptyRetries < EMPTY_PAGE_MAX_RETRIES) {
                val backoffMs = EMPTY_PAGE_BASE_DELAY_MS * (1 shl emptyRetries) // Exponential: 300, 600, 1200ms
                UnifiedLog.d(TAG, "Empty page for chat ${chat.chatId}, retry ${emptyRetries + 1}/$EMPTY_PAGE_MAX_RETRIES after ${backoffMs}ms")
                delay(backoffMs)
                emptyRetries++
            } else {
                break // Exhausted retries
            }
        }

        if (page.isEmpty()) {
            UnifiedLog.d(TAG, "Chat ${chat.chatId}: No more messages after $emptyRetries retries")
            reachedEnd = true
            return emptyList()
        }

        scannedMessages += page.size
        fromMessageId = page.last().messageId

        // Apply timestamp filter
        val filtered = if (minMessageTimestampMs != null) {
            val cutoffSeconds = minMessageTimestampMs / 1000
            page.filter { item ->
                val dateSeconds = item.date ?: 0L
                dateSeconds >= cutoffSeconds
            }
        } else {
            page
        }

        // If all messages were filtered out and we hit quota, we're done
        if (filtered.isEmpty() && maxMessages != null && scannedMessages >= maxMessages) {
            reachedEnd = true
        }

        // If we got a partial page (less than requested), we've reached the end
        if (page.size < remainingForQuota) {
            reachedEnd = true
        }

        // If we've exactly hit our quota, mark as done
        if (maxMessages != null && scannedMessages >= maxMessages) {
            reachedEnd = true
        }

        return filtered
    }

    /** Check if there are more messages to fetch. */
    fun hasNext(): Boolean = !reachedEnd

    /** Get total messages scanned so far (before filtering). */
    fun scannedCount(): Long = scannedMessages

    companion object {
        private const val TAG = "TelegramMessageCursor"
        private const val DEFAULT_PAGE_SIZE = 100
        
        /**
         * Maximum retries when TDLib returns empty page.
         * TDLib may be loading data from server asynchronously.
         */
        private const val EMPTY_PAGE_MAX_RETRIES = 3
        
        /**
         * Base delay for exponential backoff on empty pages (milliseconds).
         * Actual delays: 300ms, 600ms, 1200ms
         */
        private const val EMPTY_PAGE_BASE_DELAY_MS = 300L
    }
}
