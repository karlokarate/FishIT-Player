package com.fishit.player.infra.transport.telegram.internal

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.ResolvedTelegramMedia
import com.fishit.player.infra.transport.telegram.TelegramRemoteId
import com.fishit.player.infra.transport.telegram.TelegramRemoteResolver
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.Thumbnail

/**
 * Default implementation of TelegramRemoteResolver.
 *
 * Resolves Telegram remote identifiers (chatId + messageId) to current TDLib file references by
 * fetching the message and extracting media content.
 *
 * ## Resolution Strategy
 *
 * 1. Call `client.getMessage(chatId, messageId)`
 * 2. Extract media content (Video, Document, Animation supported)
 * 3. Select best thumbnail (largest available, prefer thumbnail over minithumbnail)
 * 4. Map to ResolvedTelegramMedia with current fileIds
 *
 * ## Supported Content Types
 *
 * - **MessageVideo:** Most common for video files
 * - **MessageDocument:** Video files sent as documents (e.g., .mkv, large files)
 * - **MessageAnimation:** Animated GIFs/MP4 (treated as video)
 *
 * ## Thumbnail Selection Policy
 *
 * For each content type, select thumbnail in priority order:
 * 1. Largest thumbnail from thumbnails array
 * 2. Album cover (for audio/animation if available)
 * 3. Minithumbnail (inline JPEG, always available for instant placeholder)
 *
 * ## Architecture Notes
 *
 * This is `internal` to transport layer. External consumers use [TelegramRemoteResolver] interface.
 *
 * @param client The TDLib client
 * @see TelegramRemoteResolver for interface contract
 * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
 */
internal class DefaultTelegramRemoteResolver(
    private val client: TdlClient
) : TelegramRemoteResolver {

    companion object {
        private const val TAG = "TelegramRemoteResolver"
    }

    override suspend fun resolveMedia(remoteId: TelegramRemoteId): ResolvedTelegramMedia? {
        UnifiedLog.d(TAG) {
            "Resolving media: chatId=***${remoteId.chatId.toString().takeLast(3)}, messageId=***${remoteId.messageId.toString().takeLast(3)}"
        }

        // Fetch message from TDLib
        val messageResult = client.getMessage(
            chatId = remoteId.chatId,
            messageId = remoteId.messageId
        )

        val message = when (messageResult) {
            is TdlResult.Success -> messageResult.result
            is TdlResult.Failure -> {
                UnifiedLog.w(TAG) {
                    "Failed to fetch message (masked): ${messageResult.code} - ${messageResult.message}"
                }
                return null
            }
        }

        // Extract media content
        val content = message.content ?: run {
            UnifiedLog.d(TAG) { "Message has no content (masked)" }
            return null
        }

        // Map based on content type
        return when (content) {
            is MessageVideo -> resolveVideo(content)
            is MessageDocument -> resolveDocument(content)
            is MessageAnimation -> resolveAnimation(content)
            else -> {
                UnifiedLog.d(TAG) { "Unsupported content type: ${content::class.simpleName}" }
                null
            }
        }
    }

    /**
     * Resolves MessageVideo to ResolvedTelegramMedia.
     */
    private suspend fun resolveVideo(video: MessageVideo): ResolvedTelegramMedia {
        val videoFile = video.video.video
        val thumbnail = selectBestThumbnail(video.video.thumbnail, null, video.video.minithumbnail)

        // Get local paths if already downloaded
        val mediaLocalPath = if (videoFile.local.isDownloadingCompleted) {
            videoFile.local.path.takeIf { it.isNotBlank() }
        } else null

        val thumbLocalPath = thumbnail?.file?.let { thumbFile ->
            if (thumbFile.local.isDownloadingCompleted) {
                thumbFile.local.path.takeIf { it.isNotBlank() }
            } else null
        }

        return ResolvedTelegramMedia(
            mediaFileId = videoFile.id,
            thumbFileId = thumbnail?.file?.id,
            mimeType = video.video.mimeType?.takeIf { it.isNotBlank() },
            durationSecs = video.video.duration,
            sizeBytes = videoFile.size.toLong(),
            width = video.video.width,
            height = video.video.height,
            supportsStreaming = video.video.supportsStreaming,
            mediaLocalPath = mediaLocalPath,
            thumbLocalPath = thumbLocalPath,
            minithumbnailBytes = video.video.minithumbnail?.data
        )
    }

    /**
     * Resolves MessageDocument to ResolvedTelegramMedia.
     *
     * Documents can contain video files (e.g., .mkv, large MP4 sent as document).
     */
    private suspend fun resolveDocument(document: MessageDocument): ResolvedTelegramMedia {
        val docFile = document.document.document
        val thumbnail = selectBestThumbnail(
            document.document.thumbnail,
            null,
            document.document.minithumbnail
        )

        val mediaLocalPath = if (docFile.local.isDownloadingCompleted) {
            docFile.local.path.takeIf { it.isNotBlank() }
        } else null

        val thumbLocalPath = thumbnail?.file?.let { thumbFile ->
            if (thumbFile.local.isDownloadingCompleted) {
                thumbFile.local.path.takeIf { it.isNotBlank() }
            } else null
        }

        return ResolvedTelegramMedia(
            mediaFileId = docFile.id,
            thumbFileId = thumbnail?.file?.id,
            mimeType = document.document.mimeType?.takeIf { it.isNotBlank() },
            durationSecs = null, // Documents don't expose duration
            sizeBytes = docFile.size.toLong(),
            width = 0, // Documents don't expose dimensions
            height = 0,
            supportsStreaming = false, // Documents typically don't support streaming
            mediaLocalPath = mediaLocalPath,
            thumbLocalPath = thumbLocalPath,
            minithumbnailBytes = document.document.minithumbnail?.data
        )
    }

    /**
     * Resolves MessageAnimation to ResolvedTelegramMedia.
     *
     * Animations (GIF, animated MP4) can be treated as video for playback.
     */
    private suspend fun resolveAnimation(animation: MessageAnimation): ResolvedTelegramMedia {
        val animFile = animation.animation.animation
        val thumbnail = selectBestThumbnail(
            animation.animation.thumbnail,
            null,
            animation.animation.minithumbnail
        )

        val mediaLocalPath = if (animFile.local.isDownloadingCompleted) {
            animFile.local.path.takeIf { it.isNotBlank() }
        } else null

        val thumbLocalPath = thumbnail?.file?.let { thumbFile ->
            if (thumbFile.local.isDownloadingCompleted) {
                thumbFile.local.path.takeIf { it.isNotBlank() }
            } else null
        }

        return ResolvedTelegramMedia(
            mediaFileId = animFile.id,
            thumbFileId = thumbnail?.file?.id,
            mimeType = animation.animation.mimeType?.takeIf { it.isNotBlank() },
            durationSecs = animation.animation.duration,
            sizeBytes = animFile.size.toLong(),
            width = animation.animation.width,
            height = animation.animation.height,
            supportsStreaming = false, // Animations typically don't stream
            mediaLocalPath = mediaLocalPath,
            thumbLocalPath = thumbLocalPath,
            minithumbnailBytes = animation.animation.minithumbnail?.data
        )
    }

    /**
     * Selects the best available thumbnail from multiple sources.
     *
     * Priority:
     * 1. Standard thumbnail (largest available)
     * 2. Album cover (for audio/animation)
     * 3. Minithumbnail (inline, always use for placeholder but also return as fallback)
     *
     * @param thumbnail Standard thumbnail from video/document
     * @param albumCover Album cover from audio/animation
     * @param minithumbnail Inline minithumbnail (not returned as thumbnail, used for placeholder)
     * @return Best thumbnail, or null if none available
     */
    private fun selectBestThumbnail(
        thumbnail: Thumbnail?,
        albumCover: Thumbnail?,
        minithumbnail: dev.g000sha256.tdl.dto.Minithumbnail?
    ): Thumbnail? {
        // Prefer standard thumbnail (usually higher quality)
        if (thumbnail != null) return thumbnail

        // Fallback to album cover (for audio/animation)
        if (albumCover != null) return albumCover

        // No standard thumbnail available (minithumbnail handled separately in ResolvedTelegramMedia)
        return null
    }
}
