package com.chris.m3usuite.telegram.ingestion

import com.chris.m3usuite.core.logging.UnifiedLog
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.parser.ExportMessage
import com.chris.m3usuite.telegram.parser.TdlMessageMapper
import dev.g000sha256.tdl.dto.Message
import kotlinx.coroutines.delay

/**
 * Scanner for fetching and converting chat history from TDLib.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Phase C.2:
 * - For a given chatId, perform history backfill using TDLib's getChatHistory
 * - Reuse paging logic from CLI reference (fromMessageId progression, offset rules, limit, retry)
 * - Map raw TDLib messages → ExportMessage via TdlMessageMapper
 * - Return List<ExportMessage> per batch
 *
 * This module is ingest-only; no direct ObjectBox writes happen here.
 * All auth gating logic should live at the coordinator/worker layer.
 *
 * Paging strategy (from CLI reference):
 * - First page: fromMessageId=0, offset=0, limit=100
 * - Subsequent pages: fromMessageId=oldest message from previous batch, offset=-1, limit=100
 * - Retry with backoff when TDLib returns empty
 */
class TelegramHistoryScanner(
    private val serviceClient: T_TelegramServiceClient,
) {
    companion object {
        private const val TAG = "TelegramHistoryScanner"
        private const val DEFAULT_PAGE_SIZE = 100
        private const val DEFAULT_MAX_RETRIES = 5
        private const val INITIAL_RETRY_DELAY_MS = 200L
        private const val PAGE_DELAY_MS = 100L

        /**
         * Threshold for detecting TDLib's initial partial response.
         * Per tdlibsetup.md: TDLib often returns only 1 message on the first getChatHistory
         * call because it loads older messages asynchronously in the background.
         * If we receive <= this many messages on the first call, we retry to get the full batch.
         */
        private const val FIRST_BATCH_MIN_THRESHOLD = 5

        /**
         * Delay before retrying when TDLib returns a partial first batch.
         * This gives TDLib time to load messages from the server.
         */
        private const val TDLIB_ASYNC_LOAD_DELAY_MS = 500L
    }

    /**
     * Configuration for a scan operation.
     */
    data class ScanConfig(
        /** Number of messages per page (max 100 per TDLib limits) */
        val pageSize: Int = DEFAULT_PAGE_SIZE,
        /**
         * Maximum number of pages to scan.
         * Set to Int.MAX_VALUE for unlimited scanning (loads entire chat history).
         * Default: Int.MAX_VALUE (unlimited).
         */
        val maxPages: Int = Int.MAX_VALUE,
        /** Maximum number of retries for empty results */
        val maxRetries: Int = DEFAULT_MAX_RETRIES,
        /** Whether to use only locally cached messages (onlyLocal=true) */
        val onlyLocal: Boolean = false,
        /** Starting message ID (0 for most recent messages) */
        val fromMessageId: Long = 0L,
    )

    /**
     * Result of a scan operation.
     */
    data class ScanResult(
        /** List of converted ExportMessage objects */
        val messages: List<ExportMessage>,
        /** Message ID of the oldest message scanned (for resume) */
        val oldestMessageId: Long,
        /** Whether there is more history to fetch */
        val hasMoreHistory: Boolean,
        /** Number of raw TDLib messages received */
        val rawMessageCount: Int,
        /** Number of messages successfully converted to ExportMessage */
        val convertedCount: Int,
    )

    /**
     * Scan chat history and convert to ExportMessages.
     *
     * @param chatId Chat ID to scan
     * @param config Scan configuration
     * @param onBatchReceived Optional callback invoked after each page is received with (messages, pageIndex)
     * @return ScanResult containing converted messages and scan metadata
     */
    suspend fun scan(
        chatId: Long,
        config: ScanConfig = ScanConfig(),
        onBatchReceived: (suspend (batch: List<ExportMessage>, pageIndex: Int) -> Unit)? = null,
    ): ScanResult {
        UnifiedLog.info(
            TAG,
            "Starting scan for chat $chatId (pageSize=${config.pageSize}, maxPages=${config.maxPages})",
        )

        val allMessages = mutableListOf<ExportMessage>()
        val seenMessageIds = mutableSetOf<Long>()
        var fromMessageId = config.fromMessageId
        var consecutiveEmptyPages = 0
        var hasMoreHistory = true
        var totalRawCount = 0
        var oldestMessageId = 0L

        for (pageIndex in 0 until config.maxPages) {
            // Determine offset based on TDLib paging rules
            // First page: offset=0, subsequent pages: offset=-1 (to include the anchor message boundary)
            val offset = if (fromMessageId == 0L) 0 else -1

            val batch =
                loadHistoryBatch(
                    chatId = chatId,
                    fromMessageId = fromMessageId,
                    offset = offset,
                    limit = config.pageSize.coerceAtMost(100),
                    onlyLocal = config.onlyLocal,
                    maxRetries = config.maxRetries,
                )

            // Filter duplicates
            val uniqueMessages = batch.filter { seenMessageIds.add(it.id) }

            if (uniqueMessages.isEmpty()) {
                consecutiveEmptyPages++
                if (consecutiveEmptyPages >= 2) {
                    UnifiedLog.info(
                        TAG,
                        "Stopping scan: $consecutiveEmptyPages consecutive empty pages",
                    )
                    hasMoreHistory = false
                    break
                }
                continue
            }

            consecutiveEmptyPages = 0
            totalRawCount += uniqueMessages.size

            // Convert to ExportMessage
            val exportMessages = TdlMessageMapper.toExportMessages(uniqueMessages)
            allMessages.addAll(exportMessages)

            UnifiedLog.debug(
                TAG,
                "Page $pageIndex: ${uniqueMessages.size} raw -> ${exportMessages.size} converted",
            )

            // Invoke callback if provided
            onBatchReceived?.invoke(exportMessages, pageIndex)

            // Find oldest message for pagination
            val oldestInBatch = uniqueMessages.minByOrNull { it.id }
            if (oldestInBatch == null || oldestInBatch.id == fromMessageId) {
                UnifiedLog.info(
                    TAG,
                    "Stopping scan: reached oldest known message",
                )
                hasMoreHistory = false
                break
            }

            oldestMessageId = oldestInBatch.id
            fromMessageId = oldestMessageId

            // Short delay for TDLib-friendly pacing
            delay(PAGE_DELAY_MS)

            // If batch is smaller than page size, likely at end of history
            if (uniqueMessages.size < config.pageSize) {
                UnifiedLog.info(
                    TAG,
                    "Stopping scan: received partial batch (${uniqueMessages.size} < ${config.pageSize})",
                )
                hasMoreHistory = false
                break
            }
        }

        UnifiedLog.info(
            TAG,
            "Scan complete for chat $chatId: $totalRawCount raw -> ${allMessages.size} converted",
        )

        return ScanResult(
            messages = allMessages,
            oldestMessageId = oldestMessageId,
            hasMoreHistory = hasMoreHistory,
            rawMessageCount = totalRawCount,
            convertedCount = allMessages.size,
        )
    }

    /**
     * Scan a single batch (one page) of history.
     * Useful for incremental scanning or when caller wants fine-grained control.
     *
     * @param chatId Chat ID to scan
     * @param fromMessageId Message ID to start from (0 for most recent)
     * @param limit Number of messages to fetch
     * @param onlyLocal Whether to use only locally cached messages
     * @return List of converted ExportMessages
     */
    suspend fun scanSingleBatch(
        chatId: Long,
        fromMessageId: Long = 0L,
        limit: Int = DEFAULT_PAGE_SIZE,
        onlyLocal: Boolean = false,
    ): List<ExportMessage> {
        val offset = if (fromMessageId == 0L) 0 else -1
        val batch =
            loadHistoryBatch(
                chatId = chatId,
                fromMessageId = fromMessageId,
                offset = offset,
                limit = limit.coerceAtMost(100),
                onlyLocal = onlyLocal,
                maxRetries = DEFAULT_MAX_RETRIES,
            )
        return TdlMessageMapper.toExportMessages(batch)
    }

    /**
     * Load a single history batch from TDLib with retry logic.
     *
     * Per tdlibsetup.md documentation: TDLib's getChatHistory often returns only 1 message
     * on the first call (when fromMessageId=0) because it loads older messages asynchronously
     * from the server in the background. The solution is to retry after a short delay to
     * allow TDLib to complete loading, then the second call will return the full batch.
     *
     * This function implements the recommended retry strategy:
     * - On first page (fromMessageId=0): If we receive <= FIRST_BATCH_MIN_THRESHOLD messages,
     *   wait for TDLib to finish async loading and retry
     * - On subsequent pages: Return immediately if we have any messages
     * - On empty results: Retry with exponential backoff
     */
    private suspend fun loadHistoryBatch(
        chatId: Long,
        fromMessageId: Long,
        offset: Int,
        limit: Int,
        onlyLocal: Boolean,
        maxRetries: Int,
    ): List<Message> {
        val isFirstPage = fromMessageId == 0L

        repeat(maxRetries) { attempt ->
            try {
                val messages =
                    serviceClient.browser().loadMessagesPaged(
                        chatId = chatId,
                        fromMessageId = fromMessageId,
                        offset = offset,
                        limit = limit,
                    )

                if (messages.isEmpty()) {
                    // Empty result - retry with backoff
                    if (attempt < maxRetries - 1) {
                        val delayMs = INITIAL_RETRY_DELAY_MS * (attempt + 1)
                        UnifiedLog.debug(
                            TAG,
                            "Empty batch from chat $chatId, retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxRetries)",
                        )
                        delay(delayMs)
                    }
                    // Continue to next attempt
                } else if (isFirstPage && messages.size <= FIRST_BATCH_MIN_THRESHOLD && attempt < 2) {
                    // First page returned very few messages (typical TDLib async loading behavior).
                    // Per tdlibsetup.md: "The first call often returns only 1 message, the second
                    // call shortly after delivers the rest (up to the set limit)"
                    // Wait for TDLib to finish loading from server, then retry.
                    // We retry up to 2 times (attempt 0 and 1) to allow TDLib time to load.
                    UnifiedLog.debug(
                        TAG,
                        "First batch from chat $chatId returned only ${messages.size} messages " +
                            "(TDLib async loading), waiting ${TDLIB_ASYNC_LOAD_DELAY_MS}ms and retrying",
                    )
                    delay(TDLIB_ASYNC_LOAD_DELAY_MS)
                    // Continue to next attempt - do NOT return yet
                } else {
                    // We have a good batch of messages
                    return messages
                }
            } catch (e: Exception) {
                UnifiedLog.warn(
                    TAG,
                    "Error loading batch from chat $chatId (attempt ${attempt + 1}/$maxRetries): ${e.message}",
                )
                if (attempt < maxRetries - 1) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (attempt + 1)
                    delay(delayMs)
                }
            }
        }

        UnifiedLog.warn(
            TAG,
            "Failed to load batch from chat $chatId after $maxRetries attempts",
        )
        return emptyList()
    }
}
