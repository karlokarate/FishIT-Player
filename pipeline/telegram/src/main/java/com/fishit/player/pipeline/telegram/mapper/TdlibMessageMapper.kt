package com.fishit.player.pipeline.telegram.mapper

import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.model.TelegramMediaType
import com.fishit.player.pipeline.telegram.model.TelegramPhotoSize
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.Minithumbnail
import dev.g000sha256.tdl.dto.PhotoSize
import dev.g000sha256.tdl.dto.RemoteFile
import dev.g000sha256.tdl.dto.Thumbnail

/**
 * Maps TDLib Message DTOs to TelegramMediaItem domain objects.
 *
 * **CONTRACT COMPLIANCE (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - All data is RAW as extracted from TDLib Messages
 * - NO title cleaning, normalization, or heuristics applied
 * - Scene-style filenames preserved exactly
 * - All processing delegated to :core:metadata-normalizer
 *
 * **v1 Reference:**
 * - v1 Mapper: `app/.../telegram/parser/ExportMessageFactory.kt`
 * - v1 Service: `T_TelegramServiceClient` message parsing
 *
 * **g00sha DTO Note:** g000sha256:tdl-coroutines-android DTOs are generated from TL schema. While
 * Kotlin sees them as non-nullable, we use defensive accessors via helper functions to handle edge
 * cases consistently.
 *
 * **Supported Message Types:**
 * - MessageVideo → VIDEO
 * - MessageDocument → DOCUMENT (filtered by MIME type in caller)
 * - MessageAudio → AUDIO
 * - MessagePhoto → PHOTO
 *
 * @see TelegramMediaItem
 */
object TdlibMessageMapper {

    /**
     * Convert a TDLib Message to TelegramMediaItem if it contains media content.
     *
     * @param message TDLib Message DTO
     * @return TelegramMediaItem or null if message has no extractable media
     */
    fun toMediaItem(message: Message): TelegramMediaItem? {
        return when (val content = message.content) {
            is MessageVideo -> mapVideoMessage(message, content)
            is MessageDocument -> mapDocumentMessage(message, content)
            is MessageAudio -> mapAudioMessage(message, content)
            is MessagePhoto -> mapPhotoMessage(message, content)
            else -> null
        }
    }

    /**
     * Convert a list of TDLib Messages to TelegramMediaItems, filtering non-media.
     *
     * @param messages List of TDLib Messages
     * @return List of TelegramMediaItems (only media messages)
     */
    fun toMediaItems(messages: List<Message>): List<TelegramMediaItem> =
            messages.mapNotNull { toMediaItem(it) }

    // ========== Private Mappers ==========

    private fun mapVideoMessage(message: Message, content: MessageVideo): TelegramMediaItem? {
        val video = content.video
        val videoFile: File = video.video
        val thumbnail: Thumbnail? = video.thumbnail
        val minithumbnail: Minithumbnail? = video.minithumbnail
        val remote: RemoteFile = videoFile.remote
        val local: LocalFile = videoFile.local

        // Validate required remote identifiers (per v1 pattern)
        val remoteId = remote.safeId()
        val uniqueId = remote.safeUniqueId()
        if (remoteId.isNullOrBlank() || uniqueId.isNullOrBlank()) {
            return null
        }

        return TelegramMediaItem(
                id = message.id,
                chatId = message.chatId,
                messageId = message.id,
                mediaAlbumId = message.mediaAlbumId.takeIf { it != 0L },
                mediaType = TelegramMediaType.VIDEO,
                fileId = videoFile.id,
                fileUniqueId = uniqueId,
                remoteId = remoteId,
                title = "", // RAW: title extracted from filename/caption by normalizer
                fileName = video.fileName,
                caption = content.caption.safeText(),
                mimeType = video.mimeType,
                sizeBytes = videoFile.size.toLong(),
                durationSecs = video.duration,
                width = video.width,
                height = video.height,
                supportsStreaming = video.supportsStreaming,
                localPath = local.safeCompletedPath(),
                thumbnailFileId = thumbnail?.file?.id?.toString(),
                thumbnailUniqueId = thumbnail?.file?.remote?.safeUniqueId(),
                thumbnailWidth = thumbnail?.width,
                thumbnailHeight = thumbnail?.height,
                thumbnailPath = thumbnail?.file?.local?.safeCompletedPathOrNull(),
                // === Minithumbnail (inline JPEG for instant blur placeholder) ===
                minithumbnailBytes = minithumbnail?.toByteArray(),
                minithumbnailWidth = minithumbnail?.width,
                minithumbnailHeight = minithumbnail?.height,
                date = message.date.toLong(),
        )
    }

    private fun mapDocumentMessage(message: Message, content: MessageDocument): TelegramMediaItem? {
        val doc = content.document
        val docFile: File = doc.document
        val thumbnail: Thumbnail? = doc.thumbnail
        val minithumbnail: Minithumbnail? = doc.minithumbnail
        val remote: RemoteFile = docFile.remote
        val local: LocalFile = docFile.local

        // Validate required remote identifiers
        val remoteId = remote.safeId()
        val uniqueId = remote.safeUniqueId()
        if (remoteId.isNullOrBlank() || uniqueId.isNullOrBlank()) {
            return null
        }

        return TelegramMediaItem(
                id = message.id,
                chatId = message.chatId,
                messageId = message.id,
                mediaAlbumId = message.mediaAlbumId.takeIf { it != 0L },
                mediaType = TelegramMediaType.DOCUMENT,
                fileId = docFile.id,
                fileUniqueId = uniqueId,
                remoteId = remoteId,
                title = "", // RAW: determined by normalizer
                fileName = doc.fileName,
                caption = content.caption.safeText(),
                mimeType = doc.mimeType,
                sizeBytes = docFile.size.toLong(),
                durationSecs = null, // Documents don't have duration
                width = null,
                height = null,
                supportsStreaming = null,
                localPath = local.safeCompletedPath(),
                thumbnailFileId = thumbnail?.file?.id?.toString(),
                thumbnailUniqueId = thumbnail?.file?.remote?.safeUniqueId(),
                thumbnailWidth = thumbnail?.width,
                thumbnailHeight = thumbnail?.height,
                thumbnailPath = thumbnail?.file?.local?.safeCompletedPathOrNull(),
                // === Minithumbnail (inline JPEG for instant blur placeholder) ===
                minithumbnailBytes = minithumbnail?.toByteArray(),
                minithumbnailWidth = minithumbnail?.width,
                minithumbnailHeight = minithumbnail?.height,
                date = message.date.toLong(),
        )
    }

    private fun mapAudioMessage(message: Message, content: MessageAudio): TelegramMediaItem? {
        val audio = content.audio
        val audioFile: File = audio.audio
        val albumCover: Thumbnail? = audio.albumCoverThumbnail
        val minithumbnail: Minithumbnail? = audio.albumCoverMinithumbnail
        val remote: RemoteFile = audioFile.remote
        val local: LocalFile = audioFile.local

        // Validate required remote identifiers
        val remoteId = remote.safeId()
        val uniqueId = remote.safeUniqueId()
        if (remoteId.isNullOrBlank() || uniqueId.isNullOrBlank()) {
            return null
        }

        return TelegramMediaItem(
                id = message.id,
                chatId = message.chatId,
                messageId = message.id,
                mediaAlbumId = message.mediaAlbumId.takeIf { it != 0L },
                mediaType = TelegramMediaType.AUDIO,
                fileId = audioFile.id,
                fileUniqueId = uniqueId,
                remoteId = remoteId,
                title = audio.title, // Audio often has title metadata - RAW
                fileName = audio.fileName,
                caption = content.caption.safeText(),
                mimeType = audio.mimeType,
                sizeBytes = audioFile.size.toLong(),
                durationSecs = audio.duration,
                width = null,
                height = null,
                supportsStreaming = null,
                localPath = local.safeCompletedPath(),
                thumbnailFileId = albumCover?.file?.id?.toString(),
                thumbnailUniqueId = albumCover?.file?.remote?.safeUniqueId(),
                thumbnailWidth = albumCover?.width,
                thumbnailHeight = albumCover?.height,
                thumbnailPath = albumCover?.file?.local?.safeCompletedPathOrNull(),
                // === Minithumbnail (album cover mini for instant blur placeholder) ===
                minithumbnailBytes = minithumbnail?.toByteArray(),
                minithumbnailWidth = minithumbnail?.width,
                minithumbnailHeight = minithumbnail?.height,
                date = message.date.toLong(),
        )
    }

    private fun mapPhotoMessage(message: Message, content: MessagePhoto): TelegramMediaItem? {
        val photo = content.photo
        val sizes: List<PhotoSize> = photo.sizes.toList()
        val minithumbnail: Minithumbnail? = photo.minithumbnail

        // Get largest photo size for primary file info
        val largestSize: PhotoSize? = sizes.maxByOrNull { it.width * it.height }
        val largestFile: File? = largestSize?.photo

        // Validate at least one size with valid remote identifiers
        val remoteId = largestFile?.remote?.safeId()
        val uniqueId = largestFile?.remote?.safeUniqueId()
        if (remoteId.isNullOrBlank() || uniqueId.isNullOrBlank()) {
            return null
        }

        return TelegramMediaItem(
                id = message.id,
                chatId = message.chatId,
                messageId = message.id,
                mediaAlbumId = message.mediaAlbumId.takeIf { it != 0L },
                mediaType = TelegramMediaType.PHOTO,
                fileId = largestFile?.id,
                fileUniqueId = uniqueId,
                remoteId = remoteId,
                title = "", // Photos rarely have titles
                fileName = null, // Photos don't have filenames
                caption = content.caption.safeText(),
                mimeType = "image/jpeg", // TDLib photos are always JPEG
                sizeBytes = largestFile?.size?.toLong(),
                durationSecs = null,
                width = largestSize?.width,
                height = largestSize?.height,
                supportsStreaming = null,
                localPath = largestFile?.local?.safeCompletedPathOrNull(),
                thumbnailFileId = null, // Use smallest size as thumb
                thumbnailUniqueId = null,
                thumbnailWidth = null,
                thumbnailHeight = null,
                thumbnailPath = null,
                photoSizes = sizes.mapNotNull { mapPhotoSize(it) },
                // === Minithumbnail (inline JPEG for instant blur placeholder) ===
                minithumbnailBytes = minithumbnail?.toByteArray(),
                minithumbnailWidth = minithumbnail?.width,
                minithumbnailHeight = minithumbnail?.height,
                date = message.date.toLong(),
        )
    }

    private fun mapPhotoSize(size: PhotoSize): TelegramPhotoSize? {
        val file: File = size.photo
        val remote: RemoteFile = file.remote
        val uniqueId = remote.safeUniqueId() ?: return null
        return TelegramPhotoSize(
                width = size.width,
                height = size.height,
                fileId = file.id.toString(),
                fileUniqueId = uniqueId,
                sizeBytes = file.size.toLong(),
        )
    } // ========== Safe Access Helpers ==========
    // These mirror v1's defensive pattern for handling TDLib DTOs

    /** Safe access to RemoteFile.id */
    private fun RemoteFile.safeId(): String? = id.takeIf { it.isNotBlank() }

    /** Safe access to RemoteFile.uniqueId */
    private fun RemoteFile.safeUniqueId(): String? = uniqueId.takeIf { it.isNotBlank() }

    /** Nullable variant for optional RemoteFile */
    private fun RemoteFile?.safeUniqueIdOrNull(): String? =
            this?.uniqueId?.takeIf { it.isNotBlank() }

    /** Safe access to FormattedText.text */
    private fun FormattedText.safeText(): String? = text.takeIf { it.isNotBlank() }

    /** Safe access to completed download path */
    private fun LocalFile.safeCompletedPath(): String? =
            path.takeIf { isDownloadingCompleted && it.isNotBlank() }

    /** Nullable variant for optional LocalFile */
    private fun LocalFile?.safeCompletedPathOrNull(): String? =
            this?.path?.takeIf { this.isDownloadingCompleted && it.isNotBlank() }
}

/** Extension function for convenient Message list conversion. */
fun List<Message>.toTelegramMediaItems(): List<TelegramMediaItem> =
        TdlibMessageMapper.toMediaItems(this)

/** Extension function for single Message conversion. */
fun Message.toTelegramMediaItemOrNull(): TelegramMediaItem? = TdlibMessageMapper.toMediaItem(this)

// =============================================================================
// Minithumbnail Helpers
// =============================================================================

/**
 * Convert TDLib Minithumbnail data to ByteArray.
 *
 * TDLib stores JPEG bytes which can be decoded directly by Coil/Glide/etc.
 */
private fun Minithumbnail.toByteArray(): ByteArray = data
