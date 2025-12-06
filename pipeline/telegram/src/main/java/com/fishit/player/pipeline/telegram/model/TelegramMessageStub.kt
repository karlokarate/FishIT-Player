package com.fishit.player.pipeline.telegram.model

/**
 * Stub representation of a Telegram message for Phase 2 Task 3 (P2-T3).
 *
 * This is a minimal stub that mimics the structure of a TDLib message
 * without requiring actual TDLib integration. Used for testing and
 * interface definition during Phase 2.
 *
 * @property chatId Telegram chat ID
 * @property messageId Telegram message ID
 * @property date Message timestamp (Unix time)
 * @property text Message text content
 * @property hasMediaContent Whether this message contains media
 * @property fileId Optional file ID if media is present
 * @property fileName Optional file name
 * @property mimeType Optional MIME type
 * @property fileSize Optional file size in bytes
 */
data class TelegramMessageStub(
    val chatId: Long,
    val messageId: Long,
    val date: Long,
    val text: String? = null,
    val hasMediaContent: Boolean = false,
    val fileId: Int? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
) {
    companion object {
        /**
         * Creates an empty stub message for testing.
         */
        fun empty(chatId: Long = 0L, messageId: Long = 0L): TelegramMessageStub {
            return TelegramMessageStub(
                chatId = chatId,
                messageId = messageId,
                date = System.currentTimeMillis() / 1000,
                hasMediaContent = false,
            )
        }

        /**
         * Creates a media stub message for testing.
         */
        fun withMedia(
            chatId: Long = 0L,
            messageId: Long = 0L,
            fileName: String = "test_video.mp4",
            mimeType: String = "video/mp4",
            fileSize: Long = 1024000L,
        ): TelegramMessageStub {
            return TelegramMessageStub(
                chatId = chatId,
                messageId = messageId,
                date = System.currentTimeMillis() / 1000,
                hasMediaContent = true,
                fileId = messageId.toInt(),
                fileName = fileName,
                mimeType = mimeType,
                fileSize = fileSize,
            )
        }
    }
}
