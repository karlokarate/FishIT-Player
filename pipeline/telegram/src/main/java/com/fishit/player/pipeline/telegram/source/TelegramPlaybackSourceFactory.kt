package com.fishit.player.pipeline.telegram.source

import com.fishit.player.pipeline.telegram.model.TelegramMediaItem

/**
 * Factory for creating Media3 playback sources for Telegram content.
 *
 * Phase 2: Stub interface only.
 * Phase 3+: Real implementation creating TelegramDataSource for Media3.
 *
 * This factory will:
 * - Generate tg:// URLs for Telegram files
 * - Create DataSource instances for Media3 playback
 * - Handle windowed zero-copy streaming
 * - Integrate with TDLib file download APIs
 *
 * URL scheme (as per tdlibAgent.md):
 * tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>
 */
interface TelegramPlaybackSourceFactory {
    /**
     * Generate a playback URL for a Telegram media item.
     *
     * @param item The media item to play
     * @return Telegram URL in tg:// scheme, or null if item has no file ID
     */
    fun createPlaybackUrl(item: TelegramMediaItem): String?

    /**
     * Check if a URL is a Telegram URL.
     *
     * @param url URL to check
     * @return true if URL starts with "tg://"
     */
    fun isTelegramUrl(url: String): Boolean

    /**
     * Parse a Telegram URL to extract media identifiers.
     *
     * @param url Telegram URL in tg:// scheme
     * @return Parsed identifiers, or null if invalid
     */
    fun parseUrl(url: String): TelegramUrlInfo?

    /**
     * Parsed information from a Telegram URL.
     *
     * @property fileId TDLib file identifier
     * @property chatId Chat ID (optional)
     * @property messageId Message ID (optional)
     */
    data class TelegramUrlInfo(
        val fileId: String,
        val chatId: Long? = null,
        val messageId: Long? = null,
    )
}
