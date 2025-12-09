package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.pipeline.telegram.tdlib.TelegramChatInfo
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.RemoteFile
import javax.inject.Inject

/**
 * Default implementation of TelegramCatalogMessageMapper.
 *
 * Maps TDLib Message DTOs to RawMediaMetadata following v2 contracts:
 * - NO title cleaning (per MEDIA_NORMALIZATION_CONTRACT)
 * - NO TMDB lookups
 * - Raw field extraction only
 *
 * **Telegram URI Format:**
 * tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>&uniqueId=<uniqueId>
 *
 * Per GOLD_TELEGRAM_CORE.md:
 * - fileId: session-specific (fast path)
 * - remoteId: stable cross-session identifier (fallback)
 * - uniqueId: stable file identifier
 *
 * @see TelegramCatalogMessageMapper
 */
class TelegramCatalogMessageMapperImpl
    @Inject
    constructor() : TelegramCatalogMessageMapper {
        override fun classifyMediaKind(message: Message): MediaKind =
            when (val content = message.content) {
                is MessagePhoto -> MediaKind.Image
                is MessageVideo -> MediaKind.Video
                is MessageAudio, is MessageVoiceNote -> MediaKind.Audio
                is MessageDocument -> {
                    // Classify documents by MIME type
                    val mimeType = content.document.mimeType.lowercase()
                    when {
                        mimeType.startsWith("video/") -> MediaKind.Video
                        mimeType.startsWith("audio/") -> MediaKind.Audio
                        mimeType.startsWith("image/") -> MediaKind.Image
                        else -> MediaKind.Document
                    }
                }
                else -> MediaKind.Unknown
            }

        override fun toRawMediaMetadata(
            message: Message,
            chat: TelegramChatInfo,
            mediaKind: MediaKind,
        ): RawMediaMetadata? {
            if (mediaKind == MediaKind.Unknown) return null

            return when (val content = message.content) {
                is MessageVideo -> mapVideoMessage(message, content, chat)
                is MessageDocument -> mapDocumentMessage(message, content, chat, mediaKind)
                is MessageAudio -> mapAudioMessage(message, content, chat)
                is MessageVoiceNote -> mapVoiceNoteMessage(message, content, chat)
                is MessagePhoto -> mapPhotoMessage(message, content, chat)
                else -> null
            }
        }

        // ========== Private Mappers ==========

        private fun mapVideoMessage(
            message: Message,
            content: MessageVideo,
            chat: TelegramChatInfo,
        ): RawMediaMetadata? {
            val video = content.video
            val videoFile: File = video.video
            val remoteId = videoFile.remote.safeId() ?: return null
            val uniqueId = videoFile.remote.safeUniqueId() ?: return null

            return RawMediaMetadata(
                originalTitle = extractTitle(content.caption, video.fileName),
                mediaType = MediaType.CLIP, // Default to CLIP; normalizer will refine
                year = null,
                season = null,
                episode = null,
                durationMinutes = video.duration?.let { it / 60 },
                sourceType = SourceType.TELEGRAM,
                sourceLabel = chat.title.ifBlank { "Telegram" },
                sourceId = buildSourceId(chat.chatId, message.id, uniqueId),
            )
        }

        private fun mapDocumentMessage(
            message: Message,
            content: MessageDocument,
            chat: TelegramChatInfo,
            mediaKind: MediaKind,
        ): RawMediaMetadata? {
            val doc = content.document
            val docFile: File = doc.document
            val remoteId = docFile.remote.safeId() ?: return null
            val uniqueId = docFile.remote.safeUniqueId() ?: return null

            // Determine MediaType based on MediaKind
            val mediaType =
                when (mediaKind) {
                    MediaKind.Video -> MediaType.CLIP
                    MediaKind.Audio -> MediaType.CLIP
                    MediaKind.Image -> MediaType.UNKNOWN // Images as documents are unusual
                    else -> MediaType.UNKNOWN
                }

            return RawMediaMetadata(
                originalTitle = extractTitle(content.caption, doc.fileName),
                mediaType = mediaType,
                year = null,
                season = null,
                episode = null,
                durationMinutes = null, // Documents rarely have duration
                sourceType = SourceType.TELEGRAM,
                sourceLabel = chat.title.ifBlank { "Telegram" },
                sourceId = buildSourceId(chat.chatId, message.id, uniqueId),
            )
        }

        private fun mapAudioMessage(
            message: Message,
            content: MessageAudio,
            chat: TelegramChatInfo,
        ): RawMediaMetadata? {
            val audio = content.audio
            val audioFile: File = audio.audio
            val remoteId = audioFile.remote.safeId() ?: return null
            val uniqueId = audioFile.remote.safeUniqueId() ?: return null

            return RawMediaMetadata(
                originalTitle = extractTitle(content.caption, audio.fileName, audio.title),
                mediaType = MediaType.CLIP, // Audio clips; normalizer may refine
                year = null,
                season = null,
                episode = null,
                durationMinutes = audio.duration?.let { it / 60 },
                sourceType = SourceType.TELEGRAM,
                sourceLabel = chat.title.ifBlank { "Telegram" },
                sourceId = buildSourceId(chat.chatId, message.id, uniqueId),
            )
        }

        private fun mapVoiceNoteMessage(
            message: Message,
            content: MessageVoiceNote,
            chat: TelegramChatInfo,
        ): RawMediaMetadata? {
            val voiceNote = content.voiceNote
            val voiceFile: File = voiceNote.voice
            val remoteId = voiceFile.remote.safeId() ?: return null
            val uniqueId = voiceFile.remote.safeUniqueId() ?: return null

            return RawMediaMetadata(
                originalTitle = extractTitle(content.caption, null),
                mediaType = MediaType.CLIP,
                year = null,
                season = null,
                episode = null,
                durationMinutes = voiceNote.duration?.let { it / 60 },
                sourceType = SourceType.TELEGRAM,
                sourceLabel = chat.title.ifBlank { "Telegram" },
                sourceId = buildSourceId(chat.chatId, message.id, uniqueId),
            )
        }

        private fun mapPhotoMessage(
            message: Message,
            content: MessagePhoto,
            chat: TelegramChatInfo,
        ): RawMediaMetadata? {
            val photo = content.photo
            val sizes = photo.sizes.toList()
            val largestSize = sizes.maxByOrNull { it.width * it.height } ?: return null
            val largestFile: File = largestSize.photo

            val remoteId = largestFile.remote.safeId() ?: return null
            val uniqueId = largestFile.remote.safeUniqueId() ?: return null

            return RawMediaMetadata(
                originalTitle = extractTitle(content.caption, null),
                mediaType = MediaType.UNKNOWN, // Photos don't fit standard categories
                year = null,
                season = null,
                episode = null,
                durationMinutes = null,
                sourceType = SourceType.TELEGRAM,
                sourceLabel = chat.title.ifBlank { "Telegram" },
                sourceId = buildSourceId(chat.chatId, message.id, uniqueId),
            )
        }

        // ========== Helpers ==========

        /**
         * Extract title from caption or filename (NO cleaning).
         *
         * Priority:
         * 1. Caption text (if not blank)
         * 2. Audio title (if provided)
         * 3. Filename (if not blank)
         * 4. Empty string (normalizer will handle)
         */
        private fun extractTitle(
            caption: FormattedText?,
            fileName: String?,
            audioTitle: String? = null,
        ): String {
            // Check caption first
            val captionText = caption?.text?.takeIf { it.isNotBlank() }
            if (captionText != null) return captionText

            // Check audio title
            val title = audioTitle?.takeIf { it.isNotBlank() }
            if (title != null) return title

            // Check filename
            val name = fileName?.takeIf { it.isNotBlank() }
            if (name != null) return name

            // Fallback to empty (normalizer will handle)
            return ""
        }

        /**
         * Build stable sourceId from chatId, messageId, and uniqueId.
         *
         * Format: "tg:<chatId>:<messageId>:<uniqueId>"
         */
        private fun buildSourceId(
            chatId: Long,
            messageId: Long,
            uniqueId: String,
        ): String = "tg:$chatId:$messageId:$uniqueId"

        /**
         * Build Telegram URI for file access.
         *
         * Format: tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>&uniqueId=<uniqueId>
         *
         * Per GOLD_TELEGRAM_CORE.md:
         * - fileId for fast path (session-specific)
         * - remoteId for cross-session fallback
         * - uniqueId for stable file identity
         */
        private fun buildTelegramUri(
            fileId: Int,
            chatId: Long,
            messageId: Long,
            remoteId: String,
            uniqueId: String,
        ): String = "tg://file/$fileId?chatId=$chatId&messageId=$messageId&remoteId=$remoteId&uniqueId=$uniqueId"

        // ========== Safe Accessors ==========

        private fun RemoteFile.safeId(): String? = id.takeIf { it.isNotBlank() }

        private fun RemoteFile.safeUniqueId(): String? = uniqueId.takeIf { it.isNotBlank() }
    }
