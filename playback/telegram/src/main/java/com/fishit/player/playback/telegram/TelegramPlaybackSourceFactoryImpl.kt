package com.fishit.player.playback.telegram

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.domain.PlaybackSource
import com.fishit.player.playback.domain.PlaybackSourceException
import com.fishit.player.playback.domain.PlaybackSourceFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating Telegram playback sources.
 *
 * Converts a [PlaybackContext] with [SourceType.TELEGRAM] into a [PlaybackSource]
 * that uses the Telegram-specific DataSource for zero-copy streaming.
 *
 * **URL Format (tg:// scheme):**
 * ```
 * tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>
 * ```
 *
 * **Resolution Strategy:**
 * 1. If `context.uri` already has a tg:// URI → use it directly
 * 2. If `context.sourceKey` contains remoteId → build tg:// URI
 * 3. Validate that we have enough info for playback
 *
 * **Architecture:**
 * - Returns [PlaybackSource] with [DataSourceType.TELEGRAM_FILE]
 * - Actual file download/streaming handled by [TelegramFileDataSource]
 */
@Singleton
class TelegramPlaybackSourceFactoryImpl @Inject constructor() : PlaybackSourceFactory {

    companion object {
        private const val TAG = "TelegramPlaybackFactory"
        private const val TG_SCHEME = "tg"
        private const val TG_HOST = "file"
    }

    override fun supports(sourceType: SourceType): Boolean {
        return sourceType == SourceType.TELEGRAM
    }

    override suspend fun createSource(context: PlaybackContext): PlaybackSource {
        UnifiedLog.d(TAG) { "Creating source for: ${context.canonicalId}" }

        // Build or validate tg:// URI
        val telegramUri = resolveTelegramUri(context)
            ?: throw PlaybackSourceException(
                message = "Cannot resolve Telegram URI for: ${context.canonicalId}",
                sourceType = SourceType.TELEGRAM
            )

        UnifiedLog.d(TAG) { "Resolved Telegram URI: $telegramUri" }

        // Determine MIME type from context extras if available
        val mimeType = context.extras["mimeType"]

        return PlaybackSource(
            uri = telegramUri,
            mimeType = mimeType,
            dataSourceType = DataSourceType.TELEGRAM_FILE
        )
    }

    /**
     * Resolves or builds a tg:// URI from the PlaybackContext.
     *
     * Priority:
     * 1. Use existing tg:// URI from context.uri
     * 2. Build from sourceKey (expected format: "fileId:remoteId:chatId:messageId")
     * 3. Build from extras if available
     */
    private fun resolveTelegramUri(context: PlaybackContext): String? {
        // Case 1: Already have a tg:// URI
        val existingUri = context.uri
        if (existingUri != null && existingUri.startsWith("$TG_SCHEME://")) {
            return existingUri
        }

        // Case 2: Build from sourceKey
        // Expected format: "fileId:remoteId:chatId:messageId" or just "remoteId"
        val sourceKey = context.sourceKey
        if (sourceKey != null) {
            return buildUriFromSourceKey(sourceKey, context)
        }

        // Case 3: Build from extras
        val remoteId = context.extras["remoteId"]
        val fileId = context.extras["fileId"]?.toIntOrNull()
        val chatId = context.extras["chatId"]?.toLongOrNull()
        val messageId = context.extras["messageId"]?.toLongOrNull()

        if (remoteId != null || (fileId != null && fileId > 0)) {
            return buildTelegramUri(
                fileId = fileId ?: 0,
                remoteId = remoteId,
                chatId = chatId,
                messageId = messageId
            )
        }

        UnifiedLog.w(TAG) { "Cannot resolve Telegram URI: no valid source info in context" }
        return null
    }

    /**
     * Builds tg:// URI from sourceKey.
     *
     * Supports multiple formats:
     * - "fileId:remoteId:chatId:messageId" (full)
     * - "remoteId" (minimal - will be resolved by DataSource)
     */
    private fun buildUriFromSourceKey(sourceKey: String, context: PlaybackContext): String {
        val parts = sourceKey.split(":")

        return when {
            parts.size >= 4 -> {
                // Full format: fileId:remoteId:chatId:messageId
                val fileId = parts[0].toIntOrNull() ?: 0
                val remoteId = parts[1].takeIf { it.isNotBlank() }
                val chatId = parts[2].toLongOrNull()
                val messageId = parts[3].toLongOrNull()
                buildTelegramUri(fileId, remoteId, chatId, messageId)
            }
            parts.size == 1 && sourceKey.isNotBlank() -> {
                // Just remoteId
                buildTelegramUri(fileId = 0, remoteId = sourceKey, chatId = null, messageId = null)
            }
            else -> {
                UnifiedLog.w(TAG) { "Invalid sourceKey format: $sourceKey" }
                null
            }
        } ?: run {
            // Fallback: try to use extras
            val chatId = context.extras["chatId"]?.toLongOrNull()
            val messageId = context.extras["messageId"]?.toLongOrNull()
            buildTelegramUri(fileId = 0, remoteId = sourceKey, chatId = chatId, messageId = messageId)
        }
    }

    /**
     * Builds a tg:// URI from components.
     *
     * Format: tg://file/<fileId>?remoteId=<remoteId>&chatId=<chatId>&messageId=<messageId>
     */
    private fun buildTelegramUri(
        fileId: Int,
        remoteId: String?,
        chatId: Long?,
        messageId: Long?
    ): String {
        val sb = StringBuilder("$TG_SCHEME://$TG_HOST/$fileId")
        val params = mutableListOf<String>()

        remoteId?.let { params.add("remoteId=$it") }
        chatId?.let { params.add("chatId=$it") }
        messageId?.let { params.add("messageId=$it") }

        if (params.isNotEmpty()) {
            sb.append("?")
            sb.append(params.joinToString("&"))
        }

        return sb.toString()
    }
}
