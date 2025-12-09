package com.fishit.player.pipeline.telegram.adapter

import com.fishit.player.infra.transport.telegram.TelegramAuthState
import com.fishit.player.infra.transport.telegram.TelegramConnectionState
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import com.fishit.player.infra.transport.telegram.TgChat
import com.fishit.player.infra.transport.telegram.TgContent
import com.fishit.player.infra.transport.telegram.TgMessage
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.model.TelegramMediaType
import com.fishit.player.pipeline.telegram.model.TelegramPhotoSize
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pipeline-level adapter that wraps TelegramTransportClient.
 *
 * Provides pipeline-specific APIs by:
 * - Exposing auth/connection state from transport layer
 * - Converting TgMessage → TelegramMediaItem
 * - Converting TgChat → TelegramChatInfo
 * - Filtering for media-only messages
 *
 * This adapter belongs in the pipeline layer and handles all
 * transport-to-pipeline type conversions.
 */
@Singleton
class TelegramPipelineAdapter @Inject constructor(
    private val transport: TelegramTransportClient
) {
    /** Current authorization state from transport layer. */
    val authState: Flow<TelegramAuthState> = transport.authState

    /** Current connection state from transport layer. */
    val connectionState: Flow<TelegramConnectionState> = transport.connectionState

    /**
     * Get available chats converted to pipeline format.
     */
    suspend fun getChats(limit: Int = 100): List<TelegramChatInfo> {
        return transport.getChats(limit).map { it.toChatInfo() }
    }

    /**
     * Fetch media messages from a chat.
     *
     * Filters for media content and converts to TelegramMediaItem.
     *
     * @param chatId Telegram chat ID
     * @param limit Maximum messages to fetch
     * @param offsetMessageId Pagination offset
     * @return List of TelegramMediaItem (only media messages)
     */
    suspend fun fetchMediaMessages(
        chatId: Long,
        limit: Int = 100,
        offsetMessageId: Long = 0
    ): List<TelegramMediaItem> {
        val messages = transport.fetchMessages(chatId, limit, offsetMessageId)
        return messages.mapNotNull { it.toMediaItem() }
    }

    /**
     * Ensure client is authorized.
     */
    suspend fun ensureAuthorized() = transport.ensureAuthorized()

    /**
     * Check if authorized.
     */
    suspend fun isAuthorized(): Boolean = transport.isAuthorized()
}

/**
 * Pipeline-level chat info.
 */
data class TelegramChatInfo(
    val chatId: Long,
    val title: String,
    val type: String,
    val memberCount: Int?
)

// ============================================================================
// Mapping Extensions
// ============================================================================

private fun TgChat.toChatInfo(): TelegramChatInfo = TelegramChatInfo(
    chatId = id,
    title = title,
    type = type.name,
    memberCount = memberCount
)

/**
 * Convert TgMessage to TelegramMediaItem if it contains media.
 * Returns null for non-media messages.
 */
private fun TgMessage.toMediaItem(): TelegramMediaItem? {
    return when (val content = content) {
        is TgContent.Video -> TelegramMediaItem(
            id = id,
            chatId = chatId,
            messageId = id,
            mediaType = TelegramMediaType.VIDEO,
            fileId = content.fileId,
            fileUniqueId = content.uniqueId,
            remoteId = content.remoteId,
            title = content.caption ?: content.fileName ?: "",
            fileName = content.fileName,
            caption = content.caption,
            mimeType = content.mimeType,
            sizeBytes = content.fileSize,
            durationSecs = content.duration,
            width = content.width,
            height = content.height,
            thumbnailFileId = content.thumbnail?.fileId?.toString(),
            thumbnailUniqueId = content.thumbnail?.uniqueId,
            thumbnailWidth = content.thumbnail?.width,
            thumbnailHeight = content.thumbnail?.height,
            date = date.toLong()
        )

        is TgContent.Document -> {
            // Only include video/audio documents
            val mimeType = content.mimeType ?: ""
            val isMedia = mimeType.startsWith("video/") ||
                    mimeType.startsWith("audio/") ||
                    mimeType.endsWith("/x-matroska") ||
                    mimeType.endsWith("/x-msvideo")

            if (!isMedia) return null

            TelegramMediaItem(
                id = id,
                chatId = chatId,
                messageId = id,
                mediaType = TelegramMediaType.DOCUMENT,
                fileId = content.fileId,
                fileUniqueId = content.uniqueId,
                remoteId = content.remoteId,
                title = content.caption ?: content.fileName ?: "",
                fileName = content.fileName,
                caption = content.caption,
                mimeType = content.mimeType,
                sizeBytes = content.fileSize,
                thumbnailFileId = content.thumbnail?.fileId?.toString(),
                thumbnailUniqueId = content.thumbnail?.uniqueId,
                thumbnailWidth = content.thumbnail?.width,
                thumbnailHeight = content.thumbnail?.height,
                date = date.toLong()
            )
        }

        is TgContent.Audio -> TelegramMediaItem(
            id = id,
            chatId = chatId,
            messageId = id,
            mediaType = TelegramMediaType.AUDIO,
            fileId = content.fileId,
            fileUniqueId = content.uniqueId,
            remoteId = content.remoteId,
            title = content.title ?: content.caption ?: content.fileName ?: "",
            fileName = content.fileName,
            caption = content.caption,
            mimeType = content.mimeType,
            sizeBytes = content.fileSize,
            durationSecs = content.duration,
            thumbnailFileId = content.albumCoverThumbnail?.fileId?.toString(),
            thumbnailUniqueId = content.albumCoverThumbnail?.uniqueId,
            thumbnailWidth = content.albumCoverThumbnail?.width,
            thumbnailHeight = content.albumCoverThumbnail?.height,
            date = date.toLong()
        )

        is TgContent.Photo -> {
            val bestSize = content.sizes.maxByOrNull { it.width * it.height }
            if (bestSize == null) return null

            TelegramMediaItem(
                id = id,
                chatId = chatId,
                messageId = id,
                mediaType = TelegramMediaType.PHOTO,
                fileId = bestSize.fileId,
                fileUniqueId = bestSize.uniqueId,
                remoteId = bestSize.remoteId,
                title = content.caption ?: "",
                caption = content.caption,
                width = bestSize.width,
                height = bestSize.height,
                sizeBytes = bestSize.fileSize,
                photoSizes = content.sizes.map { size ->
                    TelegramPhotoSize(
                        width = size.width,
                        height = size.height,
                        fileId = size.fileId.toString(),
                        fileUniqueId = size.uniqueId,
                        sizeBytes = size.fileSize
                    )
                },
                date = date.toLong()
            )
        }

        is TgContent.Animation -> TelegramMediaItem(
            id = id,
            chatId = chatId,
            messageId = id,
            mediaType = TelegramMediaType.VIDEO, // Treat as video
            fileId = content.fileId,
            fileUniqueId = content.uniqueId,
            remoteId = content.remoteId,
            title = content.caption ?: content.fileName ?: "",
            fileName = content.fileName,
            caption = content.caption,
            mimeType = content.mimeType,
            sizeBytes = content.fileSize,
            durationSecs = content.duration,
            width = content.width,
            height = content.height,
            thumbnailFileId = content.thumbnail?.fileId?.toString(),
            thumbnailUniqueId = content.thumbnail?.uniqueId,
            thumbnailWidth = content.thumbnail?.width,
            thumbnailHeight = content.thumbnail?.height,
            date = date.toLong()
        )

        is TgContent.VideoNote -> TelegramMediaItem(
            id = id,
            chatId = chatId,
            messageId = id,
            mediaType = TelegramMediaType.VIDEO, // Treat as video
            fileId = content.fileId,
            fileUniqueId = content.uniqueId,
            remoteId = content.remoteId,
            title = "",
            sizeBytes = content.fileSize,
            durationSecs = content.duration,
            width = content.length,
            height = content.length,
            thumbnailFileId = content.thumbnail?.fileId?.toString(),
            thumbnailUniqueId = content.thumbnail?.uniqueId,
            thumbnailWidth = content.thumbnail?.width,
            thumbnailHeight = content.thumbnail?.height,
            date = date.toLong()
        )

        is TgContent.VoiceNote -> TelegramMediaItem(
            id = id,
            chatId = chatId,
            messageId = id,
            mediaType = TelegramMediaType.AUDIO,
            fileId = content.fileId,
            fileUniqueId = content.uniqueId,
            remoteId = content.remoteId,
            title = content.caption ?: "",
            caption = content.caption,
            mimeType = content.mimeType,
            sizeBytes = content.fileSize,
            durationSecs = content.duration,
            date = date.toLong()
        )

        is TgContent.Text, is TgContent.Unsupported -> null
    }
}
