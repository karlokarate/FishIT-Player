package com.fishit.player.pipeline.telegram.model

/**
 * Domain model representing a Telegram chat summary for FishIT Player v2.
 *
 * This is a STUB implementation for Phase 2 Task 3 (P2-T3).
 * Provides a high-level overview of a chat containing media items.
 *
 * @property chatId Telegram chat ID
 * @property title Chat title
 * @property type Chat type (e.g., "private", "group", "channel")
 * @property mediaCount Number of media items in this chat
 * @property lastMessageDate Timestamp of the most recent message
 * @property thumbnailPath Optional path to chat thumbnail/avatar
 */
data class TelegramChatSummary(
    val chatId: Long,
    val title: String,
    val type: String = "unknown",
    val mediaCount: Int = 0,
    val lastMessageDate: Long? = null,
    val thumbnailPath: String? = null,
)
