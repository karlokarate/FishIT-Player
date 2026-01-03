package com.fishit.player.core.catalogsync

/**
 * Resumable checkpoint for Telegram catalog synchronization.
 *
 * Stores high-water marks (highest seen messageId) per chat to enable
 * incremental synchronization. On subsequent syncs, only messages
 * newer than the high-water mark are fetched.
 *
 * **Format:** `telegram|v=3|uid=<telegramUserId>|hwm=<chatId>:<msgId>,...|processed=<chatId1,chatId2,...>|ts=<timestamp>`
 *
 * **Session Safety (v2+):**
 * - Stores Telegram userId to detect account switches
 * - On userId mismatch → forces full rescan (INITIAL checkpoint)
 * - Prevents wrong HWMs being applied to different account's chats
 *
 * **PLATINUM Chat Checkpoint (v3):**
 * - `processedChatIds` tracks which chats have been fully scanned in current sync
 * - On resume, these chats are skipped in `scanCatalog(excludeChatIds)`
 * - Enables incremental chat scanning across worker runs
 *
 * **Sync Strategy:**
 * 1. INITIAL SYNC (no checkpoint / empty HWM / userId mismatch):
 *    → Full scan: All chats, all messages, from newest to oldest
 *    → Record highest messageId per chat as high-water marks
 *
 * 2. INCREMENTAL SYNC (checkpoint exists AND userId matches):
 *    → For each chat, fetch from newest until reaching the high-water mark
 *    → Update high-water marks with any newer messages found
 *
 * **TDLib Integration:**
 * - TDLib getChatHistory returns messages from newest to oldest
 * - We stop scanning a chat when we reach messageId <= highWaterMark
 * - This naturally gives us "only new content" behavior
 *
 * @property telegramUserId The Telegram user ID this checkpoint belongs to (for account switch detection)
 * @property highWaterMarks Map of chatId to highest seen messageId
 * @property processedChatIds Chat IDs fully scanned in current sync run (PLATINUM)
 * @property lastSyncTimestampMs Timestamp of last successful sync (for staleness detection)
 * @property version Schema version for forward compatibility
 */
data class TelegramSyncCheckpoint(
    val telegramUserId: Long? = null,
    val highWaterMarks: Map<Long, Long> = emptyMap(),
    val processedChatIds: Set<Long> = emptySet(),
    val lastSyncTimestampMs: Long = 0L,
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        private const val PREFIX = "telegram"
        private const val SEPARATOR = "|"
        private const val CURRENT_VERSION = 3
        private const val KEY_VERSION = "v"
        private const val KEY_USER_ID = "uid"
        private const val KEY_HWM = "hwm"
        private const val KEY_PROCESSED = "processed"
        private const val KEY_TIMESTAMP = "ts"

        /** Initial checkpoint - triggers full scan */
        val INITIAL = TelegramSyncCheckpoint()

        /**
         * Decode checkpoint from string representation.
         *
         * Supports v1, v2, and v3 (with processedChatIds) formats.
         *
         * @param encoded Encoded checkpoint string
         * @return Decoded checkpoint or [INITIAL] if parsing fails
         */
        fun decode(encoded: String?): TelegramSyncCheckpoint {
            if (encoded.isNullOrBlank()) return INITIAL
            if (!encoded.startsWith(PREFIX)) return INITIAL

            try {
                val parts = encoded.split(SEPARATOR).drop(1) // Skip prefix
                val map =
                    parts
                        .mapNotNull { part ->
                            val split = part.split("=", limit = 2)
                            if (split.size == 2) split[0] to split[1] else null
                        }.toMap()

                val version = map[KEY_VERSION]?.toIntOrNull() ?: 1
                val userId = map[KEY_USER_ID]?.toLongOrNull()
                val timestamp = map[KEY_TIMESTAMP]?.toLongOrNull() ?: 0L

                // Parse high-water marks: "chatId:msgId,chatId:msgId,..."
                val hwmString = map[KEY_HWM] ?: ""
                val highWaterMarks =
                    if (hwmString.isNotEmpty()) {
                        hwmString
                            .split(",")
                            .mapNotNull { entry ->
                                val (chatId, msgId) =
                                    entry
                                        .split(":", limit = 2)
                                        .takeIf { it.size == 2 }
                                        ?: return@mapNotNull null
                                val cid = chatId.toLongOrNull() ?: return@mapNotNull null
                                val mid = msgId.toLongOrNull() ?: return@mapNotNull null
                                cid to mid
                            }.toMap()
                    } else {
                        emptyMap()
                    }

                // Parse processedChatIds (v3+): "chatId1,chatId2,..."
                val processedChatIds =
                    map[KEY_PROCESSED]
                        ?.split(",")
                        ?.mapNotNull { it.toLongOrNull() }
                        ?.toSet()
                        ?: emptySet()

                return TelegramSyncCheckpoint(
                    telegramUserId = userId,
                    highWaterMarks = highWaterMarks,
                    processedChatIds = processedChatIds,
                    lastSyncTimestampMs = timestamp,
                    version = version,
                )
            } catch (e: Exception) {
                return INITIAL
            }
        }
    }

    /**
     * Encode checkpoint to stable string representation.
     *
     * @return Encoded checkpoint string (never null or empty)
     */
    fun encode(): String =
        buildString {
            append(PREFIX)
            append(SEPARATOR)
            append("$KEY_VERSION=$version")
            append(SEPARATOR)
            append("$KEY_TIMESTAMP=$lastSyncTimestampMs")

            // Include userId for account switch detection (v2+)
            telegramUserId?.let { uid ->
                append(SEPARATOR)
                append("$KEY_USER_ID=$uid")
            }

            if (highWaterMarks.isNotEmpty()) {
                append(SEPARATOR)
                append("$KEY_HWM=")
                append(
                    highWaterMarks.entries
                        .sortedBy { it.key } // Stable ordering
                        .joinToString(",") { "${it.key}:${it.value}" },
                )
            }

            // PLATINUM: Encode processedChatIds (v3+)
            if (processedChatIds.isNotEmpty()) {
                append(SEPARATOR)
                append("$KEY_PROCESSED=")
                append(processedChatIds.sorted().joinToString(","))
            }
        }

    /**
     * Update high-water mark for a specific chat.
     *
     * Only updates if the new messageId is higher than existing.
     *
     * @param chatId Chat ID
     * @param messageId New message ID
     * @return Updated checkpoint
     */
    fun updateHighWaterMark(
        chatId: Long,
        messageId: Long,
    ): TelegramSyncCheckpoint {
        val current = highWaterMarks[chatId] ?: 0L
        if (messageId <= current) return this

        return copy(
            highWaterMarks = highWaterMarks + (chatId to messageId),
        )
    }

    /**
     * Batch update high-water marks from discovered items.
     *
     * @param updates Map of chatId to messageId
     * @return Updated checkpoint
     */
    fun updateHighWaterMarks(updates: Map<Long, Long>): TelegramSyncCheckpoint {
        val merged = highWaterMarks.toMutableMap()
        for ((chatId, messageId) in updates) {
            val current = merged[chatId] ?: 0L
            if (messageId > current) {
                merged[chatId] = messageId
            }
        }
        return copy(highWaterMarks = merged)
    }

    /**
     * Get high-water mark for a specific chat.
     *
     * @param chatId Chat ID
     * @return Highest seen messageId, or null if chat not yet scanned
     */
    fun getHighWaterMark(chatId: Long): Long? = highWaterMarks[chatId]

    /**
     * PLATINUM: Add a processed chat ID to the checkpoint.
     *
     * Used during parallel chat scanning to track which chats have completed.
     *
     * @param chatId Chat ID that was fully scanned
     * @return Updated checkpoint with the chatId added
     */
    fun addProcessedChatId(chatId: Long): TelegramSyncCheckpoint =
        copy(
            processedChatIds = processedChatIds + chatId,
        )

    /**
     * PLATINUM: Add multiple processed chat IDs to the checkpoint.
     *
     * @param chatIds Set of chat IDs that were fully scanned
     * @return Updated checkpoint with the chatIds added
     */
    fun addProcessedChatIds(chatIds: Set<Long>): TelegramSyncCheckpoint =
        copy(
            processedChatIds = processedChatIds + chatIds,
        )

    /**
     * PLATINUM: Clear processed chat IDs (for new sync run).
     *
     * Called when starting a fresh sync to reset the checkpoint.
     *
     * @return Updated checkpoint with empty processedChatIds
     */
    fun clearProcessedChatIds(): TelegramSyncCheckpoint =
        copy(
            processedChatIds = emptySet(),
        )

    /**
     * Mark sync as complete with current timestamp and user ID.
     *
     * @param userId Current Telegram user ID to store for account switch detection
     * @return Updated checkpoint with current timestamp and userId
     */
    fun markComplete(userId: Long? = this.telegramUserId): TelegramSyncCheckpoint =
        copy(
            telegramUserId = userId,
            lastSyncTimestampMs = System.currentTimeMillis(),
        )

    /**
     * Check if this is a fresh/initial checkpoint (no prior sync).
     */
    val isInitial: Boolean
        get() = highWaterMarks.isEmpty() && lastSyncTimestampMs == 0L

    /**
     * Check if checkpoint belongs to a different Telegram account.
     *
     * @param currentUserId The currently logged-in Telegram user ID
     * @return True if checkpoint was created by a different account (stale data)
     */
    fun isAccountMismatch(currentUserId: Long?): Boolean {
        // No stored userId = legacy checkpoint (v1) or never completed → treat as valid
        if (telegramUserId == null) return false
        // No current userId provided → cannot verify, assume valid
        if (currentUserId == null) return false
        // Mismatch detected
        return telegramUserId != currentUserId
    }

    /**
     * Check if this checkpoint is valid for the given user.
     *
     * @param currentUserId The currently logged-in Telegram user ID
     * @return True if checkpoint can be used (same account or legacy)
     */
    fun isValidFor(currentUserId: Long?): Boolean = !isAccountMismatch(currentUserId)

    /**
     * Check if checkpoint is stale (older than threshold).
     *
     * @param maxAgeMs Maximum age in milliseconds (default: 7 days)
     * @return True if checkpoint is stale
     */
    fun isStale(maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000): Boolean {
        if (lastSyncTimestampMs == 0L) return true
        return System.currentTimeMillis() - lastSyncTimestampMs > maxAgeMs
    }

    /**
     * Get number of chats tracked.
     */
    val trackedChatCount: Int
        get() = highWaterMarks.size
}
