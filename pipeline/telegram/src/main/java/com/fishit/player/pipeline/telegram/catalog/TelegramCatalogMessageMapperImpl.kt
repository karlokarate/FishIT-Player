package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.Minithumbnail
import dev.g000sha256.tdl.dto.RemoteFile
import dev.g000sha256.tdl.dto.Thumbnail
import javax.inject.Inject

/**
 * Implementation of [TelegramCatalogMessageMapper].
 *
 * Maps TDLib messages to [RawMediaMetadata] following MEDIA_NORMALIZATION_CONTRACT:
 * - NO title cleaning or normalization (raw field extraction only)
 * - NO TMDB lookups or cross-pipeline decisions
 * - Scene-style filenames preserved exactly
 * - Uses ImageRef for thumbnails (no raw URLs or TDLib DTOs)
 *
 * **Telegram URI Format:**
 * `tg://file/<fileId>?remoteId=<remoteId>&uniqueId=<uniqueId>&chatId=<chatId>&messageId=<messageId>`
 *
 * Per TELEGRAM_TDLIB_V2_INTEGRATION.md:
 * - RemoteId-first semantics for cross-session stability
 * - fileId for fast session-local access
 * - uniqueId for permanent file identity
 *
 * @see TelegramCatalogMessageMapper
 */
class TelegramCatalogMessageMapperImpl
    @Inject
    constructor() : TelegramCatalogMessageMapper {
        override fun classifyMediaKind(message: Message): MediaKind =
            when (val content = message.content) {
                is MessageVideo -> MediaKind.Video
                is MessageAnimation -> MediaKind.Video
                is MessageDocument -> classifyDocument(content)
                is MessageAudio, is MessageVoiceNote -> MediaKind.Audio
                is MessagePhoto -> MediaKind.Image
                else -> MediaKind.Unknown
            }

        override fun toRawMediaMetadata(
            message: Message,
            chatId: Long,
            chatTitle: String?,
            mediaKind: MediaKind,
        ): RawMediaMetadata? =
            when (val content = message.content) {
                is MessageVideo -> mapVideo(message, content, chatId, chatTitle)
                is MessageAnimation -> mapAnimation(message, content, chatId, chatTitle)
                is MessageDocument -> mapDocument(message, content, chatId, chatTitle)
                is MessageAudio -> mapAudio(message, content, chatId, chatTitle)
                is MessageVoiceNote -> mapVoiceNote(message, content, chatId, chatTitle)
                is MessagePhoto -> mapPhoto(message, content, chatId, chatTitle)
                else -> null
            }

        // ========== Private Mappers ==========

        private fun mapVideo(
            message: Message,
            content: MessageVideo,
            chatId: Long,
            chatTitle: String?,
        ): RawMediaMetadata? {
            val video = content.video
            val videoFile = video.video
            val remote = videoFile.remote

            // Validate required remote identifiers
            val remoteId = remote.safeId() ?: return null
            val uniqueId = remote.safeUniqueId() ?: return null

            val originalTitle = extractTitle(content.caption, video.fileName)
            val sourceId = buildTelegramUri(videoFile.id, remoteId, uniqueId, chatId, message.id)
            val sourceLabel = buildSourceLabel(chatTitle)

            return RawMediaMetadata(
                originalTitle = originalTitle,
                mediaType = MediaType.UNKNOWN, // Delegate classification to normalizer
                year = null,
                season = null,
                episode = null,
                durationMinutes = video.duration / 60, // Convert seconds to minutes
                externalIds = ExternalIds(),
                sourceType = SourceType.TELEGRAM,
                sourceLabel = sourceLabel,
                sourceId = sourceId,
                thumbnail = video.thumbnail?.toImageRef(chatId, message.id),
                placeholderThumbnail = video.minithumbnail?.toImageRef(),
            )
        }

        private fun mapAnimation(
            message: Message,
            content: MessageAnimation,
            chatId: Long,
            chatTitle: String?,
        ): RawMediaMetadata? {
            val animation = content.animation
            val animationFile = animation.animation
            val remote = animationFile.remote

            val remoteId = remote.safeId() ?: return null
            val uniqueId = remote.safeUniqueId() ?: return null

            val originalTitle = extractTitle(content.caption, animation.fileName)
            val sourceId = buildTelegramUri(animationFile.id, remoteId, uniqueId, chatId, message.id)
            val sourceLabel = buildSourceLabel(chatTitle)

            return RawMediaMetadata(
                originalTitle = originalTitle,
                mediaType = MediaType.CLIP, // Animations are typically short clips/GIFs
                year = null,
                season = null,
                episode = null,
                durationMinutes = animation.duration / 60,
                externalIds = ExternalIds(),
                sourceType = SourceType.TELEGRAM,
                sourceLabel = sourceLabel,
                sourceId = sourceId,
                thumbnail = animation.thumbnail?.toImageRef(chatId, message.id),
                placeholderThumbnail = animation.minithumbnail?.toImageRef(),
            )
        }

        private fun mapDocument(
            message: Message,
            content: MessageDocument,
            chatId: Long,
            chatTitle: String?,
        ): RawMediaMetadata? {
            val doc = content.document
            val docFile = doc.document
            val remote = docFile.remote

            val remoteId = remote.safeId() ?: return null
            val uniqueId = remote.safeUniqueId() ?: return null

            val originalTitle = extractTitle(content.caption, doc.fileName)
            val sourceId = buildTelegramUri(docFile.id, remoteId, uniqueId, chatId, message.id)
            val sourceLabel = buildSourceLabel(chatTitle)

            return RawMediaMetadata(
                originalTitle = originalTitle,
                mediaType = MediaType.UNKNOWN, // Let normalizer decide based on filename/content
                year = null,
                season = null,
                episode = null,
                durationMinutes = null,
                externalIds = ExternalIds(),
                sourceType = SourceType.TELEGRAM,
                sourceLabel = sourceLabel,
                sourceId = sourceId,
                thumbnail = doc.thumbnail?.toImageRef(chatId, message.id),
                placeholderThumbnail = doc.minithumbnail?.toImageRef(),
            )
        }

        private fun mapAudio(
            message: Message,
            content: MessageAudio,
            chatId: Long,
            chatTitle: String?,
        ): RawMediaMetadata? {
            val audio = content.audio
            val audioFile = audio.audio
            val remote = audioFile.remote

            val remoteId = remote.safeId() ?: return null
            val uniqueId = remote.safeUniqueId() ?: return null

            // Audio often has title metadata
            val originalTitle =
                audio.title?.takeIf { it.isNotBlank() }
                    ?: extractTitle(content.caption, audio.fileName)
            val sourceId = buildTelegramUri(audioFile.id, remoteId, uniqueId, chatId, message.id)
            val sourceLabel = buildSourceLabel(chatTitle)

            return RawMediaMetadata(
                originalTitle = originalTitle,
                mediaType = MediaType.MUSIC,
                year = null,
                season = null,
                episode = null,
                durationMinutes = audio.duration / 60,
                externalIds = ExternalIds(),
                sourceType = SourceType.TELEGRAM,
                sourceLabel = sourceLabel,
                sourceId = sourceId,
                thumbnail = audio.albumCoverThumbnail?.toImageRef(chatId, message.id),
                placeholderThumbnail = audio.albumCoverMinithumbnail?.toImageRef(),
            )
        }

        private fun mapVoiceNote(
            message: Message,
            content: MessageVoiceNote,
            chatId: Long,
            chatTitle: String?,
        ): RawMediaMetadata? {
            val voice = content.voiceNote
            val voiceFile = voice.voice
            val remote = voiceFile.remote

            val remoteId = remote.safeId() ?: return null
            val uniqueId = remote.safeUniqueId() ?: return null

            val originalTitle = extractTitle(content.caption, "Voice Note ${message.id}")
            val sourceId = buildTelegramUri(voiceFile.id, remoteId, uniqueId, chatId, message.id)
            val sourceLabel = buildSourceLabel(chatTitle)

            return RawMediaMetadata(
                originalTitle = originalTitle,
                mediaType = MediaType.PODCAST, // Voice notes are like podcast audio
                year = null,
                season = null,
                episode = null,
                durationMinutes = voice.duration / 60,
                externalIds = ExternalIds(),
                sourceType = SourceType.TELEGRAM,
                sourceLabel = sourceLabel,
                sourceId = sourceId,
            )
        }

        private fun mapPhoto(
            message: Message,
            content: MessagePhoto,
            chatId: Long,
            chatTitle: String?,
        ): RawMediaMetadata? {
            val photo = content.photo
            val sizes = photo.sizes.toList()

            // Get largest photo size for primary file info
            val largestSize = sizes.maxByOrNull { it.width * it.height } ?: return null
            val largestFile = largestSize.photo
            val remote = largestFile.remote

            val remoteId = remote.safeId() ?: return null
            val uniqueId = remote.safeUniqueId() ?: return null

            val originalTitle = extractTitle(content.caption, "Photo ${message.id}")
            val sourceId = buildTelegramUri(largestFile.id, remoteId, uniqueId, chatId, message.id)
            val sourceLabel = buildSourceLabel(chatTitle)

            return RawMediaMetadata(
                originalTitle = originalTitle,
                mediaType = MediaType.UNKNOWN, // Photos don't have a specific media type
                year = null,
                season = null,
                episode = null,
                durationMinutes = null,
                externalIds = ExternalIds(),
                sourceType = SourceType.TELEGRAM,
                sourceLabel = sourceLabel,
                sourceId = sourceId,
                thumbnail =
                    ImageRef.TelegramThumb(
                        fileId = largestFile.id,
                        uniqueId = uniqueId,
                        chatId = chatId,
                        messageId = message.id,
                    ),
                placeholderThumbnail = photo.minithumbnail?.toImageRef(),
            )
        }

        // ========== Helper Functions ==========

        private fun classifyDocument(content: MessageDocument): MediaKind {
            val mimeType = content.document.mimeType?.lowercase()
            return when {
                mimeType?.contains("zip") == true ||
                    mimeType?.contains("rar") == true ||
                    mimeType?.contains("7z") == true ||
                    mimeType?.contains("archive") == true -> MediaKind.Document
                mimeType?.startsWith("video/") == true -> MediaKind.Video
                mimeType?.startsWith("audio/") == true -> MediaKind.Audio
                mimeType?.startsWith("image/") == true -> MediaKind.Image
                else -> MediaKind.Document
            }
        }

        /**
         * Extract title from caption or filename.
         *
         * Priority:
         * 1. Caption text (if non-blank)
         * 2. Filename (if non-null)
         * 3. Fallback string
         *
         * **NO CLEANING:** Scene-style filenames are preserved exactly.
         */
        private fun extractTitle(
            caption: FormattedText?,
            fileName: String?,
        ): String {
            val captionText = caption?.text?.takeIf { it.isNotBlank() }
            return captionText ?: fileName ?: "Untitled Media"
        }

        /**
         * Build Telegram URI in the format:
         * tg://file/<fileId>?remoteId=<remoteId>&uniqueId=<uniqueId>&chatId=<chatId>&messageId=<messageId>
         */
        private fun buildTelegramUri(
            fileId: Int,
            remoteId: String,
            uniqueId: String,
            chatId: Long,
            messageId: Long,
        ): String = "tg://file/$fileId?remoteId=$remoteId&uniqueId=$uniqueId&chatId=$chatId&messageId=$messageId"

        /**
         * Build source label for UI display.
         */
        private fun buildSourceLabel(chatTitle: String?): String =
            if (chatTitle != null) {
                "Telegram: $chatTitle"
            } else {
                "Telegram"
            }

        // ========== Extension Functions ==========

        /** Safe access to RemoteFile.id */
        private fun RemoteFile.safeId(): String? = id.takeIf { it.isNotBlank() }

        /** Safe access to RemoteFile.uniqueId */
        private fun RemoteFile.safeUniqueId(): String? = uniqueId.takeIf { it.isNotBlank() }

        /** Convert Thumbnail to ImageRef.TelegramThumb */
        private fun Thumbnail.toImageRef(
            chatId: Long?,
            messageId: Long?,
        ): ImageRef.TelegramThumb? {
            val thumbFile = file
            val remote = thumbFile.remote
            val remoteId = remote.safeId() ?: return null
            val uniqueId = remote.safeUniqueId() ?: return null

            return ImageRef.TelegramThumb(
                fileId = thumbFile.id,
                uniqueId = uniqueId,
                chatId = chatId,
                messageId = messageId,
                preferredWidth = width,
                preferredHeight = height,
            )
        }

        /** Convert Minithumbnail to ImageRef.InlineBytes */
        private fun Minithumbnail.toImageRef(): ImageRef.InlineBytes =
            ImageRef.InlineBytes(
                bytes = data,
                mimeType = "image/jpeg",
                preferredWidth = width,
                preferredHeight = height,
            )
    }
