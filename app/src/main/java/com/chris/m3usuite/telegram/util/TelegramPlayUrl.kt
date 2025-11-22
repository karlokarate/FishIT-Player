package com.chris.m3usuite.telegram.util

/**
 * Utility object for building Telegram playback URLs.
 * Single canonical place to construct tg://file URLs for zero-copy playback.
 */
object TelegramPlayUrl {
    /**
     * Build Telegram file URL with proper format: tg://file/<fileId>?chatId=...&messageId=...
     *
     * @param fileId TDLib file ID
     * @param chatId Telegram chat ID
     * @param messageId Telegram message ID
     * @return Properly formatted Telegram URL
     */
    fun buildFileUrl(
        fileId: Int?,
        chatId: Long,
        messageId: Long,
    ): String {
        requireNotNull(fileId) { "fileId must not be null when building Telegram URL" }
        return "tg://file/$fileId?chatId=$chatId&messageId=$messageId"
    }
}
