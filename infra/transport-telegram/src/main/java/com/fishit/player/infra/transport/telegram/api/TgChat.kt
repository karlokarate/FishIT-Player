package com.fishit.player.infra.transport.telegram.api

/**
 * Chat type classification for Telegram chats.
 */
enum class TgChatType {
    PRIVATE,
    BASIC_GROUP,
    SUPERGROUP,
    CHANNEL,
    SECRET,
    UNKNOWN,
    ;

    companion object {
        /**
         * Parse from string representation used by mappers.
         */
        fun fromString(value: String): TgChatType =
            when (value.lowercase()) {
                "private" -> PRIVATE
                "basicgroup" -> BASIC_GROUP
                "supergroup" -> SUPERGROUP
                "channel" -> CHANNEL
                "secret" -> SECRET
                else -> UNKNOWN
            }
    }
}

/**
 * Transport-layer chat descriptor for Telegram chats.
 *
 * Pure DTO with no Telegram API dependencies. Created by mapping
 * Telegram API `Chat` objects in the transport layer.
 *
 * **v2 Architecture:**
 * - Transport produces this DTO
 * - Pipeline consumes it for chat filtering/selection
 * - "No bot chats" filtering uses [isBot] flag
 *
 * @property chatId Unique chat identifier (use [id] alias)
 * @property title Chat title (may be null for private chats without names)
 * @property type Chat type as string (private, basicGroup, supergroup, channel, secret)
 * @property memberCount Member count (0 until full info is fetched)
 * @property lastMessageId ID of last message (for navigation)
 * @property lastMessageDate Timestamp of last message (epoch seconds), for ordering
 */
data class TgChat(
    val chatId: Long,
    val title: String?,
    val type: String,
    val memberCount: Int = 0,
    val lastMessageId: Long? = null,
    val lastMessageDate: Long? = null,
) {
    /**
     * Alias for [chatId] for compatibility with existing code.
     */
    val id: Long get() = chatId

    /**
     * Parse [type] string to enum.
     */
    val chatType: TgChatType get() = TgChatType.fromString(type)
}
