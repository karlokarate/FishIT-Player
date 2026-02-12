package com.fishit.player.playback.telegram

import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.domain.PlaybackSource
import com.fishit.player.playback.domain.PlaybackSourceException
import com.fishit.player.playback.domain.PlaybackSourceFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating Telegram playback sources via the Telethon HTTP proxy.
 *
 * Converts a [PlaybackContext] with [SourceType.TELEGRAM] into a [PlaybackSource]
 * using an HTTP URL pointing to the localhost Telethon proxy.
 *
 * **URI Format (HTTP proxy):**
 * ```
 * http://127.0.0.1:8089/file?chat=<chatId>&id=<messageId>
 * ```
 *
 * **Architecture:**
 * - Builds HTTP URL from chatId + messageId (from extras or sourceKey)
 * - Returns [DataSourceType.DEFAULT] — standard HTTP DataSource handles streaming
 * - The proxy supports Range headers for seeking
 * - No custom DataSource needed (unlike the former Telegram API tg:// scheme)
 */
@Singleton
class TelegramPlaybackSourceFactoryImpl
    @Inject
    constructor(
        private val sessionConfig: TelegramSessionConfig,
    ) : PlaybackSourceFactory {

    companion object {
        private const val TAG = "TelegramPlaybackFactory"
    }

    override fun supports(sourceType: SourceType): Boolean = sourceType == SourceType.TELEGRAM

    override suspend fun createSource(context: PlaybackContext): PlaybackSource {
        UnifiedLog.d(TAG) { "Creating source for: ${context.canonicalId}" }

        val (chatId, messageId) = extractChatAndMessage(context)
            ?: throw PlaybackSourceException(
                message = "Cannot create Telegram playback URI: missing chatId/messageId. " +
                    "canonicalId=${context.canonicalId}",
                sourceType = SourceType.TELEGRAM,
            )

        // Build HTTP URL pointing to the Telethon proxy
        val proxyUrl = "${sessionConfig.proxyBaseUrl}/file?chat=$chatId&id=$messageId"

        UnifiedLog.d(TAG) { "Created proxy URL: chatId=$chatId, messageId=$messageId" }

        val mimeType = context.extras[PlaybackHintKeys.Telegram.MIME_TYPE]
            ?: context.extras["mimeType"]

        return PlaybackSource(
            uri = proxyUrl,
            mimeType = mimeType,
            dataSourceType = DataSourceType.DEFAULT,
        )
    }

    /**
     * Extract chatId and messageId from the PlaybackContext.
     *
     * Priority:
     * 1. From extras (playbackHints) — preferred
     * 2. From sourceKey in "msg:chatId:messageId" format
     */
    private fun extractChatAndMessage(context: PlaybackContext): Pair<Long, Long>? {
        // Try extras first
        val chatIdFromExtras = (
            context.extras[PlaybackHintKeys.Telegram.CHAT_ID]
                ?: context.extras["chatId"]
        )?.toLongOrNull()

        val messageIdFromExtras = (
            context.extras[PlaybackHintKeys.Telegram.MESSAGE_ID]
                ?: context.extras["messageId"]
        )?.toLongOrNull()

        if (chatIdFromExtras != null && messageIdFromExtras != null) {
            return chatIdFromExtras to messageIdFromExtras
        }

        // Try sourceKey "msg:chatId:messageId"
        val sourceKey = context.sourceKey
        if (sourceKey != null && sourceKey.startsWith("msg:")) {
            val parts = sourceKey.split(":")
            if (parts.size >= 3) {
                val chatId = parts[1].toLongOrNull()
                val messageId = parts[2].toLongOrNull()
                if (chatId != null && messageId != null) {
                    return chatId to messageId
                }
            }
        }

        return null
    }
}
