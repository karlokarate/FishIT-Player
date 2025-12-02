package com.chris.m3usuite.telegram.util

import com.chris.m3usuite.telegram.player.TelegramPlaybackRequest
import java.net.URLEncoder

/**
 * Utility object for building Telegram playback URLs.
 * Single canonical place to construct tg://file URLs for zero-copy playback.
 *
 * Phase D+: Supports remoteId-first playback wiring.
 * URL format: tg://file/<fileIdOrZero>?chatId=...&messageId=...&remoteId=...&uniqueId=...
 *
 * Resolution strategy in TelegramFileDataSource:
 * 1. If fileId is present and valid -> use directly
 * 2. Else resolve fileId via getRemoteFile(remoteId)
 */
object TelegramPlayUrl {
    /**
     * Offset used to encode Telegram message IDs as MediaItem IDs.
     *
     * Telegram content uses the range [4_000_000_000_000, 5_000_000_000_000) for MediaItem.id
     * to avoid collisions with Xtream content IDs.
     *
     * Use: mediaId = TELEGRAM_MEDIA_ID_OFFSET + anchorMessageId
     * Decode: anchorMessageId = mediaId - TELEGRAM_MEDIA_ID_OFFSET
     */
    const val TELEGRAM_MEDIA_ID_OFFSET = 4_000_000_000_000L

    /**
     * Build Telegram file URL from a TelegramPlaybackRequest (remoteId-first).
     *
     * URL format: tg://file/<fileIdOrZero>?chatId=...&messageId=...&remoteId=...&uniqueId=...&durationMs=...&fileSizeBytes=...
     *
     * The fileId in the path is used as a fast-path cache. If fileId is null or 0,
     * the DataSource will resolve it via remoteId.
     *
     * @param request TelegramPlaybackRequest with remoteId-first semantics
     * @return Properly formatted Telegram URL with all identifiers
     */
    fun build(request: TelegramPlaybackRequest): String {
        val fileIdPath = request.fileId ?: 0
        val encodedRemoteId = URLEncoder.encode(request.remoteId, "UTF-8")
        val encodedUniqueId = URLEncoder.encode(request.uniqueId, "UTF-8")
        val baseUrl =
            "tg://file/$fileIdPath?" +
                "chatId=${request.chatId}&" +
                "messageId=${request.messageId}&" +
                "remoteId=$encodedRemoteId&" +
                "uniqueId=$encodedUniqueId"

        // Add optional durationMs and fileSizeBytes if available
        val withDuration =
            if (request.durationMs != null) {
                "$baseUrl&durationMs=${request.durationMs}"
            } else {
                baseUrl
            }

        val withSize =
            if (request.fileSizeBytes != null) {
                "$withDuration&fileSizeBytes=${request.fileSizeBytes}"
            } else {
                withDuration
            }

        return withSize
    }

    /**
     * Build Telegram file URL with proper format: tg://file/<fileId>?chatId=...&messageId=...
     *
     * @param fileId TDLib file ID
     * @param chatId Telegram chat ID
     * @param messageId Telegram message ID
     * @return Properly formatted Telegram URL
     * @deprecated Use [build] with [TelegramPlaybackRequest] for remoteId-first semantics
     */
    @Deprecated(
        message = "Use build(TelegramPlaybackRequest) for remoteId-first playback wiring",
        replaceWith = ReplaceWith("build(TelegramPlaybackRequest(chatId, messageId, remoteId, uniqueId, fileId))"),
    )
    fun buildFileUrl(
        fileId: Int?,
        chatId: Long,
        messageId: Long,
    ): String {
        requireNotNull(fileId) { "fileId must not be null when building Telegram URL" }
        return "tg://file/$fileId?chatId=$chatId&messageId=$messageId"
    }

    /**
     * Build Telegram file URL with remoteId-first semantics.
     *
     * This overload allows building URLs with remoteId and uniqueId even when fileId
     * is not yet available. The DataSource will resolve fileId via getRemoteFile(remoteId).
     *
     * @param fileId TDLib file ID (can be null, will use 0 in URL path)
     * @param chatId Telegram chat ID
     * @param messageId Telegram message ID
     * @param remoteId Stable remote file identifier
     * @param uniqueId Stable unique file identifier
     * @return Properly formatted Telegram URL with all identifiers
     */
    fun buildFileUrl(
        fileId: Int?,
        chatId: Long,
        messageId: Long,
        remoteId: String,
        uniqueId: String,
    ): String {
        val request =
            TelegramPlaybackRequest(
                chatId = chatId,
                messageId = messageId,
                remoteId = remoteId,
                uniqueId = uniqueId,
                fileId = fileId,
            )
        return build(request)
    }
}
