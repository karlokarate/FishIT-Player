package com.fishit.player.pipeline.telegram.catalog

/**
 * Events emitted during Telegram catalog scanning.
 *
 * Provides progress updates, item discovery, and error reporting for catalog scans.
 *
 * **Design Principles:**
 * - Sealed hierarchy for exhaustive when expressions
 * - Item events (ItemDiscovered, ItemUpdated, ItemDeleted) for incremental catalog updates
 * - Scan status events (ScanningInitial, ScanProgress, ScanCompleted, ScanError) for UI feedback
 *
 * Initial implementation treats all items as discovered; ItemUpdated/ItemDeleted defined for future incremental scans.
 */
sealed interface TelegramCatalogEvent {
    // ========== Item Events ==========

    /**
     * A new media item was discovered during the scan.
     *
     * @property item The discovered Telegram catalog item.
     */
    data class ItemDiscovered(
        val item: TelegramCatalogItem,
    ) : TelegramCatalogEvent

    /**
     * An existing media item was updated during the scan.
     *
     * **FUTURE:** Used for incremental scans where items are re-scanned and metadata may have changed.
     * Initial implementation may not emit this event; all items are treated as discovered.
     *
     * @property item The updated Telegram catalog item.
     */
    data class ItemUpdated(
        val item: TelegramCatalogItem,
    ) : TelegramCatalogEvent

    /**
     * A media item was deleted (no longer available in the chat).
     *
     * **FUTURE:** Used for incremental scans to detect deleted messages.
     * Initial implementation may not emit this event.
     *
     * @property stableId Stable identifier for the deleted item (e.g., "chatId:messageId").
     */
    data class ItemDeleted(
        val stableId: String,
    ) : TelegramCatalogEvent

    // ========== Scan Status Events ==========

    /**
     * Scan is starting.
     *
     * Emitted once at the beginning of a scan with estimates of work to be done.
     *
     * @property chatCount Number of chats to be scanned.
     * @property estimatedTotalMessages Estimated total messages to scan (nullable if unknown).
     */
    data class ScanningInitial(
        val chatCount: Int,
        val estimatedTotalMessages: Long?,
    ) : TelegramCatalogEvent

    /**
     * Scan progress update.
     *
     * Emitted periodically during the scan to report progress.
     *
     * @property scannedChats Number of chats scanned so far.
     * @property totalChats Total number of chats to scan.
     * @property scannedMessages Total messages scanned so far.
     * @property totalMessagesEstimate Estimated total messages (nullable if unknown).
     */
    data class ScanProgress(
        val scannedChats: Int,
        val totalChats: Int,
        val scannedMessages: Long,
        val totalMessagesEstimate: Long?,
    ) : TelegramCatalogEvent

    /**
     * Scan completed successfully.
     *
     * Emitted once at the end of a successful scan with final counts.
     *
     * @property scannedChats Total number of chats scanned.
     * @property scannedMessages Total number of messages scanned.
     */
    data class ScanCompleted(
        val scannedChats: Int,
        val scannedMessages: Long,
    ) : TelegramCatalogEvent

    /**
     * Scan encountered an error and was aborted.
     *
     * Emitted when the scan cannot continue due to auth, connection, or other errors.
     * The flow is closed after emitting this event.
     *
     * @property reason Human-readable error reason.
     * @property throwable Optional exception that caused the error.
     */
    data class ScanError(
        val reason: String,
        val throwable: Throwable? = null,
    ) : TelegramCatalogEvent
}
