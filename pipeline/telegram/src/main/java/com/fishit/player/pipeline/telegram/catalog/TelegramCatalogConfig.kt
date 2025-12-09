package com.fishit.player.pipeline.telegram.catalog

/**
 * Configuration for Telegram catalog scanning.
 *
 * Defines which chats to scan, message filtering criteria, and media type selection.
 *
 * **Design Principles:**
 * - Immutable configuration passed to [TelegramCatalogPipeline]
 * - Empty includedChatIds means use default chat selection
 * - Filters applied at scan time (no persistence side effects)
 *
 * @property includedChatIds Set of chat IDs to scan. Empty = default selection (user, supergroups, channels).
 * @property maxMessagesPerChat Maximum messages to scan per chat. Null = no limit.
 * @property minMessageTimestampMs Minimum message timestamp in milliseconds. Messages older than this are skipped. Null = no filter.
 * @property includeImages Whether to include image messages in the scan.
 * @property includeVideo Whether to include video messages in the scan.
 * @property includeAudio Whether to include audio messages in the scan.
 * @property includeDocuments Whether to include document messages in the scan.
 */
data class TelegramCatalogConfig(
    val includedChatIds: Set<Long> = emptySet(),
    val maxMessagesPerChat: Long? = null,
    val minMessageTimestampMs: Long? = null,
    val includeImages: Boolean = true,
    val includeVideo: Boolean = true,
    val includeAudio: Boolean = true,
    val includeDocuments: Boolean = true,
) {
    companion object {
        /**
         * Default configuration: scan all media types from default chats with no limits.
         */
        val DEFAULT = TelegramCatalogConfig()
    }
}
