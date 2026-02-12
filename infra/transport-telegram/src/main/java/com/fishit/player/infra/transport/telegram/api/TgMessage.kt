package com.fishit.player.infra.transport.telegram.api

/**
 * Transport-layer message descriptor for Telegram messages.
 *
 * Pure DTO with no Telegram API dependencies. Created by mapping
 * Telegram API `Message` objects in the transport layer.
 *
 * **v2 Architecture:**
 * - Transport maps Telegram API Message to this DTO
 * - Pipeline consumes this for catalog ingestion
 * - No normalization here (pipeline's job)
 *
 * @property messageId Unique message ID within the chat (use [id] alias)
 * @property chatId Chat this message belongs to
 * @property senderId Sender ID (Telegram API MessageSender, opaque type)
 * @property date Unix timestamp when message was sent (epoch seconds)
 * @property content Message content (media type or null for non-media)
 * @property replyToMessageId ID of message this replies to, if any
 * @property forwardInfo Forward info string if forwarded, null otherwise
 */
data class TgMessage(
    val messageId: Long,
    val chatId: Long,
    val senderId: Any? = null,
    val date: Long,
    val content: TgContent?,
    val replyToMessageId: Long? = null,
    val forwardInfo: String? = null,
) {
    /**
     * Alias for [messageId] for compatibility with existing code.
     */
    val id: Long get() = messageId

    /**
     * Whether this message was forwarded.
     */
    val isForwarded: Boolean get() = forwardInfo != null
}
