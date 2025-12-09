package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.core.model.RawMediaMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Telegram Catalog Pipeline Interface
 *
 * Scans Telegram chats via TDLib, extracts media-bearing messages, and emits RawMediaMetadata
 * wrapped in catalog events.
 *
 * **Architecture Boundaries:**
 * - NO auth UI (only checks auth state and reports errors)
 * - NO downloads (only metadata extraction)
 * - NO database writes (stateless scanning)
 * - NO playback logic (only URI construction)
 *
 * **Design:**
 * - Side-effect-free pipeline (no UI, no persistence, no playback)
 * - Uses existing DefaultTelegramClient for TDLib access
 * - Emits Flow<TelegramCatalogEvent> for reactive consumption
 *
 * @see TelegramCatalogConfig
 * @see TelegramCatalogEvent
 */
interface TelegramCatalogPipeline {
    /**
     * Scan Telegram chats for media content and emit catalog events.
     *
     * **Behavior:**
     * - Checks auth/connection state first (emits ScanError if not ready)
     * - Resolves chats to scan via config.includedChatIds or all available chats
     * - Iterates messages using TDLib history pagination
     * - Filters by config (includeImages, includeVideo, etc.)
     * - Emits ItemDiscovered for each relevant media message
     * - Emits progress events (ScanningInitial, ScanProgress, ScanCompleted)
     *
     * **Error Handling:**
     * - If not authenticated: emits ScanError("unauthenticated_or_not_ready_auth_state")
     * - If not connected: emits ScanError("offline_or_not_connected")
     * - Chat/message fetch failures are logged but don't stop the scan
     *
     * @param config Scan configuration (chat filters, content types, limits)
     * @return Flow of catalog events (cold flow, starts on collection)
     */
    fun scanCatalog(config: TelegramCatalogConfig): Flow<TelegramCatalogEvent>
}

/**
 * Configuration for Telegram catalog scanning.
 *
 * Controls which chats to scan, what content types to include, and limits on message traversal.
 *
 * @property includedChatIds Set of chat IDs to scan. If empty, scans all available chats.
 * @property maxMessagesPerChat Maximum messages to fetch per chat. null = no limit.
 * @property minMessageTimestampMs Only include messages newer than this timestamp (Unix ms).
 * @property includeImages Include photo messages in results
 * @property includeVideo Include video messages in results
 * @property includeAudio Include audio messages in results
 * @property includeDocuments Include document messages in results
 */
data class TelegramCatalogConfig(
    val includedChatIds: Set<Long> = emptySet(),
    val maxMessagesPerChat: Long? = null,
    val minMessageTimestampMs: Long? = null,
    val includeImages: Boolean = true,
    val includeVideo: Boolean = true,
    val includeAudio: Boolean = true,
    val includeDocuments: Boolean = true,
)

/**
 * Catalog scan events.
 *
 * Emitted by [TelegramCatalogPipeline.scanCatalog] to report scan progress and discovered items.
 */
sealed interface TelegramCatalogEvent {
    /**
     * A media item was discovered during scanning.
     *
     * @property item The discovered media item with RawMediaMetadata
     */
    data class ItemDiscovered(
        val item: TelegramCatalogItem,
    ) : TelegramCatalogEvent

    /**
     * A media item was updated (kept for future use, initial impl may treat all as discovered).
     *
     * @property item The updated media item
     */
    data class ItemUpdated(
        val item: TelegramCatalogItem,
    ) : TelegramCatalogEvent

    /**
     * A media item was deleted.
     *
     * @property stableId Stable identifier of deleted item (sourceId from RawMediaMetadata)
     */
    data class ItemDeleted(
        val stableId: String,
    ) : TelegramCatalogEvent

    /**
     * Scan is starting.
     *
     * Emitted once at the beginning of a scan.
     *
     * @property chatCount Number of chats that will be scanned
     * @property estimatedTotalMessages Estimated total messages to scan (may be null)
     */
    data class ScanningInitial(
        val chatCount: Int,
        val estimatedTotalMessages: Long? = null,
    ) : TelegramCatalogEvent

    /**
     * Scan progress update.
     *
     * Emitted periodically during scanning to report progress.
     *
     * @property scannedChats Number of chats scanned so far
     * @property totalChats Total number of chats to scan
     * @property scannedMessages Number of messages scanned so far
     * @property totalMessagesEstimate Estimated total messages (may be null or updated)
     */
    data class ScanProgress(
        val scannedChats: Int,
        val totalChats: Int,
        val scannedMessages: Long,
        val totalMessagesEstimate: Long? = null,
    ) : TelegramCatalogEvent

    /**
     * Scan completed successfully.
     *
     * @property scannedChats Total chats scanned
     * @property scannedMessages Total messages scanned
     */
    data class ScanCompleted(
        val scannedChats: Int,
        val scannedMessages: Long,
    ) : TelegramCatalogEvent

    /**
     * Scan error occurred.
     *
     * May be emitted for fatal errors (auth/connection) or non-fatal errors during scanning.
     *
     * @property reason Error reason string (e.g., "unauthenticated_or_not_ready_auth_state")
     * @property throwable Optional exception that caused the error
     */
    data class ScanError(
        val reason: String,
        val throwable: Throwable? = null,
    ) : TelegramCatalogEvent
}

/**
 * Catalog item representing a discovered media message.
 *
 * Wraps RawMediaMetadata with Telegram-specific context (chat, message).
 *
 * @property raw RawMediaMetadata extracted from the message
 * @property chatId Telegram chat ID where the media was found
 * @property messageId Telegram message ID
 * @property chatTitle Human-readable chat title (may be null)
 */
data class TelegramCatalogItem(
    val raw: RawMediaMetadata,
    val chatId: Long,
    val messageId: Long,
    val chatTitle: String?,
)
