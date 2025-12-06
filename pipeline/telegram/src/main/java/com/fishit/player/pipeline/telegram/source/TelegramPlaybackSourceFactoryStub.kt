package com.fishit.player.pipeline.telegram.source

import com.fishit.player.pipeline.telegram.model.TelegramMediaItem

/**
 * Stub implementation of TelegramPlaybackSourceFactory for Phase 2.
 *
 * Generates tg:// URLs but does not create actual DataSource instances.
 * Phase 3+ will integrate with Media3 and TDLib.
 */
class TelegramPlaybackSourceFactoryStub : TelegramPlaybackSourceFactory {
    /**
     * Creates a tg:// URL for the media item.
     *
     * Format: tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>
     *
     * Returns null if fileId is not available.
     */
    override fun createPlaybackUrl(item: TelegramMediaItem): String? {
        val fileId = item.fileId ?: return null
        return buildString {
            append("tg://file/$fileId")
            append("?chatId=${item.chatId}")
            append("&messageId=${item.messageId}")
        }
    }

    /**
     * Checks if URL starts with "tg://"
     */
    override fun isTelegramUrl(url: String): Boolean = url.startsWith("tg://")

    /**
     * Parses a tg:// URL to extract components.
     *
     * Expected format: tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>
     *
     * Returns null if URL is malformed.
     */
    override fun parseUrl(url: String): TelegramPlaybackSourceFactory.TelegramUrlInfo? {
        if (!isTelegramUrl(url)) return null

        return try {
            // Simple parsing - Phase 3+ will use proper URI parsing
            val afterScheme = url.removePrefix("tg://file/")
            if (afterScheme.isEmpty() || afterScheme == "tg://") return null

            val parts = afterScheme.split("?")
            if (parts.isEmpty() || parts[0].isEmpty()) return null

            val fileId = parts[0]
            var chatId: Long? = null
            var messageId: Long? = null

            if (parts.size > 1) {
                val queryParams = parts[1].split("&")
                for (param in queryParams) {
                    val keyValue = param.split("=")
                    if (keyValue.size == 2) {
                        when (keyValue[0]) {
                            "chatId" -> chatId = keyValue[1].toLongOrNull()
                            "messageId" -> messageId = keyValue[1].toLongOrNull()
                        }
                    }
                }
            }

            TelegramPlaybackSourceFactory.TelegramUrlInfo(
                fileId = fileId,
                chatId = chatId,
                messageId = messageId,
            )
        } catch (e: Exception) {
            null
        }
    }
}
