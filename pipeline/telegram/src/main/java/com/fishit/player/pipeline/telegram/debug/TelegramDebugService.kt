package com.fishit.player.pipeline.telegram.debug

import com.fishit.player.core.model.MediaType

/**
 * Debug service interface for Telegram pipeline inspection.
 *
 * Provides status, chat listing, and media sampling capabilities
 * for CLI and debugging tools.
 *
 * **Usage:**
 * ```kotlin
 * val service = TelegramDebugServiceImpl(adapter, classifier)
 * val status = service.getStatus()
 * val hotChats = service.listChats(ChatFilter.HOT, limit = 10)
 * val media = service.sampleMedia(chatId, limit = 5)
 * ```
 */
interface TelegramDebugService {
    /**
     * Get current Telegram pipeline status.
     *
     * @return TelegramStatus with auth state and chat counts
     */
    suspend fun getStatus(): TelegramStatus

    /**
     * List chats with optional classification filter.
     *
     * @param filter Filter by media classification (HOT/WARM/COLD/ALL)
     * @param limit Maximum number of chats to return
     * @return List of chat summaries
     */
    suspend fun listChats(
        filter: ChatFilter,
        limit: Int = 20,
    ): List<TelegramChatSummary>

    /**
     * Sample media messages from a specific chat.
     *
     * @param chatId Telegram chat ID
     * @param limit Maximum number of messages to sample
     * @return List of media summaries with normalized titles
     */
    suspend fun sampleMedia(
        chatId: Long,
        limit: Int = 10,
    ): List<TelegramMediaSummary>
}

/**
 * Filter for chat listing.
 */
enum class ChatFilter {
    /** All chats regardless of classification */
    ALL,

    /** Only media-hot chats (high media density) */
    HOT,

    /** Only media-warm chats (moderate media) */
    WARM,

    /** Only media-cold chats (low/no media) */
    COLD,
}

/**
 * Overall Telegram pipeline status.
 *
 * @property isAuthenticated Whether the session is authenticated
 * @property sessionDir Path to TDLib session directory
 * @property chatCount Total number of available chats
 * @property hotChats Number of media-hot chats
 * @property warmChats Number of media-warm chats
 * @property coldChats Number of media-cold chats
 */
data class TelegramStatus(
    val isAuthenticated: Boolean,
    val sessionDir: String,
    val chatCount: Int,
    val hotChats: Int,
    val warmChats: Int,
    val coldChats: Int,
)

/**
 * Summary of a Telegram chat.
 *
 * @property chatId Telegram chat ID
 * @property title Chat title/name
 * @property mediaClass Classification (HOT/WARM/COLD)
 * @property mediaCountEstimate Estimated media message count
 */
data class TelegramChatSummary(
    val chatId: Long,
    val title: String,
    val mediaClass: String,
    val mediaCountEstimate: Int,
)

/**
 * Summary of a media message.
 *
 * @property messageId Message ID
 * @property timestampMillis Message timestamp in milliseconds
 * @property mimeType Content MIME type
 * @property sizeBytes File size in bytes
 * @property normalizedTitle Title after normalization
 * @property normalizedMediaType Media type after normalization
 */
data class TelegramMediaSummary(
    val messageId: Long,
    val timestampMillis: Long,
    val mimeType: String?,
    val sizeBytes: Long?,
    val normalizedTitle: String?,
    val normalizedMediaType: MediaType,
)
