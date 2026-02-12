package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.core.model.RawMediaMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Stateless, side-effect-free Telegram catalog pipeline.
 *
 * Scans chat histories via TelegramClient and emits catalog events that a higher-level
 * CatalogSync module can consume and persist.
 *
 * **Design Principles:**
 * - Stateless producer: no DB writes, no caching, no UI
 * - Uses existing TelegramClient for chat/message access
 * - Emits events via cold Flow (cancellation-safe)
 * - Respects configuration filters (maxMessages, minTimestamp)
 *
 * **Architecture Integration:**
 * - Input: TelegramClient (chat/message access)
 * - Output: TelegramCatalogEvent stream
 * - Consumer: CatalogSync (persistence layer)
 *
 * See: docs/v2/MEDIA_NORMALIZATION_CONTRACT.md for RawMediaMetadata requirements
 */
interface TelegramCatalogPipeline {
    /**
     * Scan Telegram chats and emit catalog events for all discovered media.
     *
     * Implementations must:
     * - Respect [TelegramCatalogConfig] filters
     * - Stop emitting when the returned [Flow] is cancelled
     * - Never perform DB writes or UI updates directly
     * - Use TelegramMediaItem.toRawMediaMetadata() for conversion
     *
     * @param config Scan configuration (limits, filters)
     * @return Cold Flow of catalog events
     */
    fun scanCatalog(config: TelegramCatalogConfig): Flow<TelegramCatalogEvent>

    /**
     * Listen for live media-only updates and emit catalog events.
     *
     * Implementations must:
     * - Emit ItemDiscovered for each playable media update
     * - Trigger a one-shot warm-up ingestion when a chat transitions from COLD → WARM/HOT
     * - Respect coroutine cancellation
     */
    fun liveMediaUpdates(config: TelegramLiveUpdatesConfig = TelegramLiveUpdatesConfig()): Flow<TelegramCatalogEvent>
}

/**
 * Configuration for catalog scans.
 *
 * @property maxMessagesPerChat Maximum messages to scan per chat (null = no limit)
 * @property minMessageTimestampMs Minimum message timestamp in ms since epoch (null = no filter)
 * @property chatIds Specific chat IDs to scan (null = all chats)
 * @property pageSize Messages per page for Telegram API pagination
 * @property highWaterMarks Map of chatId to highest seen messageId for incremental sync.
 *           When provided, scanning stops for a chat when reaching messageId <= highWaterMark.
 *           This enables "only fetch new content" behavior on subsequent syncs.
 * @property excludeChatIds Chat IDs to skip during scanning (for checkpoint resume, PLATINUM)
 * @property chatParallelism Max concurrent chats to scan in parallel (PLATINUM)
 */
data class TelegramCatalogConfig(
    val maxMessagesPerChat: Long? = null,
    val minMessageTimestampMs: Long? = null,
    val chatIds: List<Long>? = null,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val highWaterMarks: Map<Long, Long>? = null,
    val excludeChatIds: Set<Long> = emptySet(),
    val chatParallelism: Int = DEFAULT_CHAT_PARALLELISM,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 100

        /** Default parallelism for chat scanning (3 concurrent chats). */
        const val DEFAULT_CHAT_PARALLELISM = 3

        /** Default config with no limits (full scan). */
        val DEFAULT = TelegramCatalogConfig()

        /** Quick scan: limited messages, recent only. */
        fun quickScan(
            maxPerChat: Long = 50,
            recentDays: Int = 7,
        ) = TelegramCatalogConfig(
            maxMessagesPerChat = maxPerChat,
            minMessageTimestampMs = System.currentTimeMillis() - (recentDays * 24 * 60 * 60 * 1000L),
        )

        /**
         * Incremental scan using high-water marks.
         *
         * Only fetches messages newer than the last sync for each chat.
         *
         * @param highWaterMarks Map of chatId to highest seen messageId
         */
        fun incremental(highWaterMarks: Map<Long, Long>) =
            TelegramCatalogConfig(
                highWaterMarks = highWaterMarks,
            )
    }

    /**
     * Check if this is an incremental scan (has high-water marks).
     */
    val isIncremental: Boolean
        get() = !highWaterMarks.isNullOrEmpty()

    /**
     * Get high-water mark for a specific chat.
     *
     * @param chatId Chat ID
     * @return High-water mark messageId, or null if full scan for this chat
     */
    fun getHighWaterMark(chatId: Long): Long? = highWaterMarks?.get(chatId)
}

/**
 * Configuration for live media update stream.
 *
 * @property warmUpIngestMessages Number of history messages to ingest when a chat warms up
 * @property minMessageTimestampMs Optional lower bound for history ingestion
 * @property pageSize Page size used during warm-up ingestion
 * @property chatLookupLimit Limit when resolving chat titles for warm-up ingestion
 */
data class TelegramLiveUpdatesConfig(
    val warmUpIngestMessages: Long = 50,
    val minMessageTimestampMs: Long? = null,
    val pageSize: Int = TelegramCatalogConfig.DEFAULT_PAGE_SIZE,
    val chatLookupLimit: Int = 200,
)

/**
 * Events produced by the catalog pipeline.
 *
 * Consumers (CatalogSync) process these events to update their storage.
 */
sealed interface TelegramCatalogEvent {
    /**
     * A new media item was discovered in Telegram history.
     *
     * The consumer decides whether this is a "create" or "update" event
     * by comparing against its own storage.
     */
    data class ItemDiscovered(
        val item: TelegramCatalogItem,
    ) : TelegramCatalogEvent

    /**
     * Initial scan is about to start.
     *
     * @property chatCount Number of chats to scan
     * @property estimatedTotalMessages Estimated total messages (if known)
     */
    data class ScanStarted(
        val chatCount: Int,
        val estimatedTotalMessages: Long?,
    ) : TelegramCatalogEvent

    /**
     * Periodic progress update during scanning.
     *
     * @property scannedChats Chats fully scanned so far
     * @property totalChats Total chats to scan
     * @property scannedMessages Total messages scanned
     * @property discoveredItems Media items found so far
     */
    data class ScanProgress(
        val scannedChats: Int,
        val totalChats: Int,
        val scannedMessages: Long,
        val discoveredItems: Long,
    ) : TelegramCatalogEvent

    /**
     * Normal completion of a scan.
     *
     * @property scannedChats Total chats scanned
     * @property scannedMessages Total messages scanned
     * @property discoveredItems Total media items found
     * @property durationMs Scan duration in milliseconds
     * @property newHighWaterMarks Updated high-water marks (chatId → highest messageId).
     *           Consumers should persist these for incremental sync on next run.
     */
    data class ScanCompleted(
        val scannedChats: Int,
        val scannedMessages: Long,
        val discoveredItems: Long,
        val durationMs: Long,
        val newHighWaterMarks: Map<Long, Long> = emptyMap(),
    ) : TelegramCatalogEvent

    /**
     * Scan was cancelled (coroutine cancelled).
     *
     * @property scannedChats Chats scanned before cancellation
     * @property scannedMessages Messages scanned before cancellation
     * @property partialHighWaterMarks Partial high-water marks from chats scanned before cancellation
     */
    data class ScanCancelled(
        val scannedChats: Int,
        val scannedMessages: Long,
        val partialHighWaterMarks: Map<Long, Long> = emptyMap(),
    ) : TelegramCatalogEvent

    /**
     * Non-recoverable error during scanning.
     *
     * @property reason Error category (e.g., "unauthenticated", "network_error")
     * @property message Human-readable error message
     * @property throwable Original exception (if any)
     */
    data class ScanError(
        val reason: String,
        val message: String,
        val throwable: Throwable? = null,
    ) : TelegramCatalogEvent

    /**
     * A chat scan completed successfully (PLATINUM parallel scanning).
     *
     * Emitted when all messages for a specific chat have been scanned.
     * Used for checkpoint tracking to enable cross-run resume.
     *
     * @property chatId The chat ID that completed scanning
     * @property messageCount Number of messages scanned for this chat
     * @property itemCount Number of media items discovered in this chat
     * @property newHighWaterMark Updated high-water mark for this chat (highest messageId seen)
     */
    data class ChatScanComplete(
        val chatId: Long,
        val messageCount: Long,
        val itemCount: Long,
        val newHighWaterMark: Long?,
    ) : TelegramCatalogEvent

    /**
     * A chat scan failed (PLATINUM parallel scanning).
     *
     * The overall scan continues with other chats.
     * Failed chats are NOT added to processedChatIds for retry on next run.
     *
     * @property chatId The chat ID that failed
     * @property reason Error description
     */
    data class ChatScanFailed(
        val chatId: Long,
        val reason: String,
    ) : TelegramCatalogEvent
}

/**
 * Raw catalog item wrapping RawMediaMetadata with Telegram-specific origin info.
 *
 * This is the output of the catalog pipeline, containing:
 * - Raw metadata (for normalization pipeline)
 * - Origin info (for deduplication and tracking)
 *
 * @property raw RawMediaMetadata from TelegramMediaItem.toRawMediaMetadata()
 * @property chatId Telegram chat ID where the media was found
 * @property messageId Telegram message ID
 * @property chatTitle Chat title (for UI/debugging)
 * @property discoveredAtMs Timestamp when item was discovered (for freshness tracking)
 */
data class TelegramCatalogItem(
    val raw: RawMediaMetadata,
    val chatId: Long,
    val messageId: Long,
    val chatTitle: String?,
    val discoveredAtMs: Long = System.currentTimeMillis(),
)
