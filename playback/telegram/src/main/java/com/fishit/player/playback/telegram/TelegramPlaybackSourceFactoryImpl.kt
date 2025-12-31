package com.fishit.player.playback.telegram

import com.fishit.player.core.model.PlaybackHintKeys
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
 * Converts a [PlaybackContext] with [SourceType.TELEGRAM] into a [PlaybackSource] that uses the
 * Telegram-specific DataSource for zero-copy streaming.
 *
 * **URI Format (tg:// scheme) - SSOT via [TelegramPlaybackUriContract]:**
 * ```
 * tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>&mimeType=<mimeType>
 * ```
 *
 * **Resolution Contract (HARD RULES):**
 * - MUST include chatId AND messageId (for fallback message fetch)
 * - MUST include EITHER fileId > 0 OR remoteId (for file resolution)
 * - If neither is available, FAIL EARLY with clear error message
 * - NO "best effort" URIs that may fail silently in DataSource
 *
 * **Architecture:**
 * - Pure URI builder - no transport dependencies required
 * - Uses [TelegramPlaybackUriContract] as SSOT for URI format
 * - Returns [PlaybackSource] with [DataSourceType.TELEGRAM_FILE]
 * - Actual file resolution/download handled by [TelegramFileDataSource]
 *
 * @see TelegramPlaybackUriContract for URI format contract
 * @see TelegramFileDataSource for file resolution
 */
@Singleton
class TelegramPlaybackSourceFactoryImpl
    @Inject
    constructor() : PlaybackSourceFactory {

    companion object {
        private const val TAG = "TelegramPlaybackFactory"
    }

    override fun supports(sourceType: SourceType): Boolean = sourceType == SourceType.TELEGRAM

    override suspend fun createSource(context: PlaybackContext): PlaybackSource {
        UnifiedLog.d(TAG) { "Creating source for: ${context.canonicalId}" }

        // Build validated tg:// URI using SSOT contract
        val telegramUri = try {
            buildValidatedUri(context)
        } catch (e: IllegalArgumentException) {
            throw PlaybackSourceException(
                message = "Cannot create Telegram playback URI: ${e.message}",
                sourceType = SourceType.TELEGRAM,
                cause = e,
            )
        }

        // Validate URI before returning
        val validation = TelegramPlaybackUriContract.validate(telegramUri)
        if (validation is TelegramPlaybackUriContract.ValidationResult.Invalid) {
            throw PlaybackSourceException(
                message = "Invalid Telegram URI: ${validation.reason}",
                sourceType = SourceType.TELEGRAM,
            )
        }

        // Log success (redacted for security - no full remoteId)
        val parsed = TelegramPlaybackUriContract.parseUri(telegramUri)
        UnifiedLog.d(TAG) {
            "Created valid URI: chatId=${parsed?.chatId}, messageId=${parsed?.messageId}, " +
                "fileId=${parsed?.fileId}, hasRemoteId=${!parsed?.remoteId.isNullOrBlank()}"
        }

        // Determine MIME type from context extras
        val mimeType = context.extras[PlaybackHintKeys.Telegram.MIME_TYPE]
            ?: context.extras["mimeType"]

        return PlaybackSource(
            uri = telegramUri,
            mimeType = mimeType,
            dataSourceType = DataSourceType.TELEGRAM_FILE,
        )
    }

    /**
     * Builds a validated tg:// URI from the PlaybackContext.
     *
     * Priority:
     * 1. Use existing tg:// URI from context.uri (if valid)
     * 2. Build from context.extras (playbackHints) - PREFERRED PATH
     * 3. Build from sourceKey + extras combination (legacy support)
     *
     * @throws IllegalArgumentException if required fields are missing
     */
    private fun buildValidatedUri(context: PlaybackContext): String {
        // Case 1: Already have a tg:// URI - validate and return
        val existingUri = context.uri
        if (existingUri != null && TelegramPlaybackUriContract.isTelegramUri(existingUri)) {
            val validation = TelegramPlaybackUriContract.validate(existingUri)
            if (validation is TelegramPlaybackUriContract.ValidationResult.Valid) {
                UnifiedLog.d(TAG) { "Using existing valid tg:// URI" }
                return existingUri
            }
            // Existing URI is invalid - try to rebuild from extras
            UnifiedLog.w(TAG) {
                "Existing URI invalid: ${(validation as TelegramPlaybackUriContract.ValidationResult.Invalid).reason}, rebuilding from extras"
            }
        }

        // Case 2: Build from extras (SSOT path - playbackHints from pipeline)
        val chatIdFromExtras = (context.extras[PlaybackHintKeys.Telegram.CHAT_ID]
            ?: context.extras["chatId"])?.toLongOrNull()
        val messageIdFromExtras = (context.extras[PlaybackHintKeys.Telegram.MESSAGE_ID]
            ?: context.extras["messageId"])?.toLongOrNull()
        val fileIdFromExtras = (context.extras[PlaybackHintKeys.Telegram.FILE_ID]
            ?: context.extras["fileId"])?.toIntOrNull() ?: 0

        // If we have chatId and messageId from extras, use the contract builder
        if (chatIdFromExtras != null && messageIdFromExtras != null) {
            return TelegramPlaybackUriContract.buildUriFromExtras(
                extras = context.extras,
                fileId = fileIdFromExtras,
            )
        }

        // Case 3: Try to extract chatId/messageId from sourceKey (legacy support)
        val sourceKey = context.sourceKey
        if (sourceKey != null) {
            return buildUriFromSourceKey(sourceKey, context)
        }

        // No valid source information available
        throw IllegalArgumentException(
            "Missing Telegram playback info: no chatId/messageId in extras and no sourceKey. " +
                "canonicalId=${context.canonicalId}"
        )
    }

    /**
     * Builds URI from sourceKey with extras fallback.
     *
     * Supports formats:
     * - "msg:<chatId>:<messageId>" (v2 pipeline format)
     * - Legacy formats (for backward compatibility only)
     *
     * @throws IllegalArgumentException if required fields cannot be determined
     */
    private fun buildUriFromSourceKey(
        sourceKey: String,
        context: PlaybackContext,
    ): String {
        // v2 format: "msg:<chatId>:<messageId>"
        if (sourceKey.startsWith("msg:")) {
            val parts = sourceKey.split(":")
            if (parts.size >= 3) {
                val chatId = parts[1].toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid chatId in sourceKey: $sourceKey")
                val messageId = parts[2].toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid messageId in sourceKey: $sourceKey")

                // Get remoteId from extras (REQUIRED for resolution)
                val remoteId = context.extras[PlaybackHintKeys.Telegram.REMOTE_ID]
                    ?: context.extras["remoteId"]
                val fileId = (context.extras[PlaybackHintKeys.Telegram.FILE_ID]
                    ?: context.extras["fileId"])?.toIntOrNull() ?: 0
                val mimeType = context.extras[PlaybackHintKeys.Telegram.MIME_TYPE]
                    ?: context.extras["mimeType"]

                // Validate we have a file locator
                if (fileId <= 0 && remoteId.isNullOrBlank()) {
                    throw IllegalArgumentException(
                        "Cannot resolve Telegram file: no fileId or remoteId in extras. " +
                            "sourceKey=$sourceKey, chatId=$chatId, messageId=$messageId. " +
                            "Ensure playbackHints include telegram.remoteId."
                    )
                }

                return TelegramPlaybackUriContract.buildUri(
                    fileId = fileId,
                    chatId = chatId,
                    messageId = messageId,
                    remoteId = remoteId,
                    mimeType = mimeType,
                )
            }
        }

        // Legacy support: sourceKey might be a remoteId directly (discouraged)
        val chatId = (context.extras[PlaybackHintKeys.Telegram.CHAT_ID]
            ?: context.extras["chatId"])?.toLongOrNull()
        val messageId = (context.extras[PlaybackHintKeys.Telegram.MESSAGE_ID]
            ?: context.extras["messageId"])?.toLongOrNull()

        if (chatId != null && messageId != null) {
            // Treat sourceKey as remoteId if it looks like one
            val remoteId = if (sourceKey.contains(":")) null else sourceKey
            val fileId = (context.extras[PlaybackHintKeys.Telegram.FILE_ID]
                ?: context.extras["fileId"])?.toIntOrNull() ?: 0
            val mimeType = context.extras[PlaybackHintKeys.Telegram.MIME_TYPE]
                ?: context.extras["mimeType"]

            // Validate file locator
            if (fileId <= 0 && remoteId.isNullOrBlank()) {
                throw IllegalArgumentException(
                    "Cannot resolve Telegram file: sourceKey is not a valid remoteId and no fileId. " +
                        "sourceKey=$sourceKey, chatId=$chatId, messageId=$messageId"
                )
            }

            return TelegramPlaybackUriContract.buildUri(
                fileId = fileId,
                chatId = chatId,
                messageId = messageId,
                remoteId = remoteId,
                mimeType = mimeType,
            )
        }

        throw IllegalArgumentException(
            "Invalid sourceKey format and missing chatId/messageId in extras: $sourceKey"
        )
    }
}
