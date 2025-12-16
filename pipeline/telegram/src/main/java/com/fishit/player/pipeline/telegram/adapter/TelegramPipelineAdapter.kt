package com.fishit.player.pipeline.telegram.adapter

import com.fishit.player.core.model.MimeDecider
import com.fishit.player.core.model.MimeMediaKind
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import com.fishit.player.infra.transport.telegram.TgChat
import com.fishit.player.infra.transport.telegram.TgContent
import com.fishit.player.infra.transport.telegram.TgMessage
import com.fishit.player.infra.transport.telegram.api.TdlibAuthState
import com.fishit.player.infra.transport.telegram.api.TelegramConnectionState
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.model.TelegramMediaType
import com.fishit.player.pipeline.telegram.model.TelegramPhotoSize
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

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
    val authState: Flow< TdlibAuthState> = transport.authState

    /** Current connection state from transport layer. */
    val connectionState: Flow< TelegramConnectionState> = transport.connectionState

    /** Live stream of media-only updates mapped into pipeline types. */
    val mediaUpdates: Flow<TelegramMediaUpdate> = transport.mediaUpdates
        .mapNotNull { message -> message.toMediaItem()?.let { TelegramMediaUpdate(message, it) } }

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

    suspend fun fetchMessages(chatId: Long, limit: Int = 100, offsetMessageId: Long = 0): List<TgMessage> {
        return transport.fetchMessages(chatId, limit, offsetMessageId)
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
 * Media update carrying both the raw transport message and the mapped pipeline item.
 */
data class TelegramMediaUpdate(
    val message: TgMessage,
    val mediaItem: TelegramMediaItem,
)

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
 *
 * ## v2 remoteId-First Architecture
 *
 * Per `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`:
 * - Only `remoteId` is stored (stable across sessions)
 * - `fileId` and `uniqueId` are NOT stored (volatile/redundant)
 * - Uses `thumbRemoteId` for thumbnail references
 */
private fun TgMessage.toMediaItem(): TelegramMediaItem? {
    val timestampMs = date.toLong() * 1000L

    return when (val content = content) {
        is TgContent.Video -> TelegramMediaItem(
            id = id,
            chatId = chatId,
            messageId = id,
            mediaType = TelegramMediaType.VIDEO,
            remoteId = content.remoteId,
            title = content.caption ?: content.fileName ?: "",
            fileName = content.fileName,
            caption = content.caption,
            mimeType = content.mimeType,
            sizeBytes = content.fileSize,
            durationSecs = content.duration,
            width = content.width,
            height = content.height,
            thumbRemoteId = content.thumbnail?.remoteId,
            thumbnailWidth = content.thumbnail?.width,
            thumbnailHeight = content.thumbnail?.height,
            minithumbnailBytes = content.minithumbnail?.data,
            minithumbnailWidth = content.minithumbnail?.width,
            minithumbnailHeight = content.minithumbnail?.height,
            date = date.toLong() * 1000L
        )

        is TgContent.Document -> {
            val inferredKind = MimeDecider.inferKind(content.mimeType, content.fileName) ?: return null
            val mediaType = when (inferredKind) {
                MimeMediaKind.VIDEO -> TelegramMediaType.VIDEO
                MimeMediaKind.AUDIO -> TelegramMediaType.AUDIO
            }

            TelegramMediaItem(
                id = id,
                chatId = chatId,
                messageId = id,
                mediaType = mediaType,
                remoteId = content.remoteId,
                title = content.caption ?: content.fileName ?: "",
                fileName = content.fileName,
                caption = content.caption,
                mimeType = content.mimeType,
                sizeBytes = content.fileSize,
                thumbRemoteId = content.thumbnail?.remoteId,
                thumbnailWidth = content.thumbnail?.width,
                thumbnailHeight = content.thumbnail?.height,
                minithumbnailBytes = content.minithumbnail?.data,
                minithumbnailWidth = content.minithumbnail?.width,
                minithumbnailHeight = content.minithumbnail?.height,
                date = timestampMs
            )
        }

        is TgContent.Audio -> TelegramMediaItem(
            id = id,
            chatId = chatId,
            messageId = id,
            mediaType = TelegramMediaType.AUDIO,
            remoteId = content.remoteId,
            title = content.title ?: content.caption ?: content.fileName ?: "",
            fileName = content.fileName,
            caption = content.caption,
            mimeType = content.mimeType,
            sizeBytes = content.fileSize,
            durationSecs = content.duration,
            thumbRemoteId = content.albumCoverThumbnail?.remoteId,
            thumbnailWidth = content.albumCoverThumbnail?.width,
            thumbnailHeight = content.albumCoverThumbnail?.height,
            minithumbnailBytes = content.albumCoverMinithumbnail?.data,
            minithumbnailWidth = content.albumCoverMinithumbnail?.width,
            minithumbnailHeight = content.albumCoverMinithumbnail?.height,
            date = timestampMs
        )

        is TgContent.Photo -> {
            val bestSize = content.sizes.maxByOrNull { it.width * it.height }
            if (bestSize == null) return null

            TelegramMediaItem(
                id = id,
                chatId = chatId,
                messageId = id,
                mediaType = TelegramMediaType.PHOTO,
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
                        remoteId = size.remoteId,
                        sizeBytes = size.fileSize
                    )
                },
                minithumbnailBytes = content.minithumbnail?.data,
                minithumbnailWidth = content.minithumbnail?.width,
                minithumbnailHeight = content.minithumbnail?.height,
                date = timestampMs
            )
        }

        is TgContent.Animation -> TelegramMediaItem(
            id = id,
            chatId = chatId,
            messageId = id,
            mediaType = TelegramMediaType.VIDEO, // Treat as video
            remoteId = content.remoteId,
            title = content.caption ?: content.fileName ?: "",
            fileName = content.fileName,
            caption = content.caption,
            mimeType = content.mimeType,
            sizeBytes = content.fileSize,
            durationSecs = content.duration,
            width = content.width,
            height = content.height,
            thumbRemoteId = content.thumbnail?.remoteId,
            thumbnailWidth = content.thumbnail?.width,
            thumbnailHeight = content.thumbnail?.height,
            minithumbnailBytes = content.minithumbnail?.data,
            minithumbnailWidth = content.minithumbnail?.width,
            minithumbnailHeight = content.minithumbnail?.height,
            date = timestampMs
        )

        is TgContent.VideoNote -> TelegramMediaItem(
            id = id,
            chatId = chatId,
            messageId = id,
            mediaType = TelegramMediaType.VIDEO, // Treat as video
            remoteId = content.remoteId,
            title = "",
            sizeBytes = content.fileSize,
            durationSecs = content.duration,
            width = content.length,
            height = content.length,
            thumbRemoteId = content.thumbnail?.remoteId,
            thumbnailWidth = content.thumbnail?.width,
            thumbnailHeight = content.thumbnail?.height,
            minithumbnailBytes = content.minithumbnail?.data,
            minithumbnailWidth = content.minithumbnail?.width,
            minithumbnailHeight = content.minithumbnail?.height,
            date = timestampMs
        )

        is TgContent.VoiceNote -> TelegramMediaItem(
            id = id,
            chatId = chatId,
            messageId = id,
            mediaType = TelegramMediaType.AUDIO,
            remoteId = content.remoteId,
            title = content.caption ?: "",
            caption = content.caption,
            mimeType = content.mimeType,
            sizeBytes = content.fileSize,
            durationSecs = content.duration,
            minithumbnailBytes = null,
            minithumbnailWidth = null,
            minithumbnailHeight = null,
            date = timestampMs
        )

        is TgContent.Text, is TgContent.Unsupported -> null
    }
}
