package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.adapter.TelegramChatInfo
import com.fishit.player.pipeline.telegram.adapter.TelegramPipelineAdapter
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Cursor-based chat history traversal with incremental sync support.
 *
 * Uses TDLib's fromMessageId semantics:
 * - fromMessageId=0 → start from latest
 * - subsequent pages use last message ID as cursor
 *
 * **Incremental Sync (High-Water Mark):**
 * When [stopAtMessageId] is provided, scanning stops when we reach a message
 * with ID <= stopAtMessageId. This enables "only fetch new content" behavior:
 * - TDLib returns messages newest-first
 * - We scan until we hit a message we've already seen
 * - The caller tracks the highest seen messageId for next sync
 *
 * **Design:**
 * - Stateful cursor (tracks position, counts)
 * - Client-side timestamp filtering via minMessageTimestampMs
 * - Respects maxMessages quota
 * - Incremental sync via stopAtMessageId (high-water mark)
 * - Cancellation-aware via coroutine context
 *
 * **Usage:**
 * ```kotlin
 * val cursor = TelegramMessageCursor(adapter, chat, config, highWaterMark)
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
 * @property stopAtMessageId Stop scanning when reaching this messageId (for incremental sync)
 */
internal class TelegramMessageCursor(
    private val adapter: TelegramPipelineAdapter,
    private val chat: TelegramChatInfo,
    private val maxMessages: Long?,
    private val minMessageTimestampMs: Long?,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val stopAtMessageId: Long? = null,
) {
    private var fromMessageId: Long = 0L
    private var reachedEnd: Boolean = false
    private var scannedMessages: Long = 0L
    private var highestSeenMessageId: Long = 0L
    private var reachedHighWaterMark: Boolean = false

    /**
     * Fetch the next batch of media items from the chat.
     *
     * **TDLib Async Loading Handling:**
     * TDLib may return empty results while loading data from the server in the background.
     * We retry up to [EMPTY_PAGE_MAX_RETRIES] times with exponential backoff before
     * concluding that the chat history is truly exhausted.
     *
     * **Incremental Sync:**
     * If [stopAtMessageId] is set, the batch is truncated at the first message
     * with ID <= stopAtMessageId, and scanning stops.
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

        // Track highest seen messageId for checkpoint updates
        val firstMsgId = page.firstOrNull()?.messageId ?: 0L
        if (firstMsgId > highestSeenMessageId) {
            highestSeenMessageId = firstMsgId
        }

        // Incremental sync: Check for high-water mark
        // TDLib returns messages newest-first, so if we see messageId <= stopAtMessageId,
        // we've reached content we've already seen
        val truncatedPage = if (stopAtMessageId != null) {
            val hwmIndex = page.indexOfFirst { it.messageId <= stopAtMessageId }
            if (hwmIndex >= 0) {
                // Found known content - truncate and stop
                reachedHighWaterMark = true
                reachedEnd = true
                val newItems = page.take(hwmIndex)
                UnifiedLog.d(TAG, "Chat ${chat.chatId}: Reached high-water mark at messageId=$stopAtMessageId, " +
                        "returning ${newItems.size} new items (truncated from ${page.size})")
                newItems
            } else {
                page
            }
        } else {
            page
        }

        scannedMessages += truncatedPage.size
        if (truncatedPage.isNotEmpty()) {
            fromMessageId = truncatedPage.last().messageId
        }

        // Apply timestamp filter
        val filtered = if (minMessageTimestampMs != null) {
            val cutoffSeconds = minMessageTimestampMs / 1000
            truncatedPage.filter { item ->
                val dateSeconds = item.date ?: 0L
                dateSeconds >= cutoffSeconds
            }
        } else {
            truncatedPage
        }

        // If all messages were filtered out and we hit quota, we're done
        if (filtered.isEmpty() && maxMessages != null && scannedMessages >= maxMessages) {
            reachedEnd = true
        }

        // If we got a partial page (less than requested), we've reached the end
        if (truncatedPage.size < remainingForQuota && !reachedHighWaterMark) {
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

    /**
     * Get the highest messageId seen during this cursor's lifetime.
     * Used by caller to update high-water marks for next incremental sync.
     */
    fun highestSeenMessageId(): Long = highestSeenMessageId

    /**
     * Check if scanning stopped because we reached the high-water mark.
     * True = incremental sync completed (found known content).
     * False = full scan completed (reached end of chat history).
     */
    fun reachedHighWaterMark(): Boolean = reachedHighWaterMark

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
