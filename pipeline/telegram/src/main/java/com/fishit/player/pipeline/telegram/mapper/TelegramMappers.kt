package com.fishit.player.pipeline.telegram.mapper

import com.fishit.player.core.persistence.obx.ObxTelegramMessage
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem

import com.fishit.player.pipeline.telegram.model.TelegramMediaType
import com.fishit.player.pipeline.telegram.model.TelegramMetadataMessage
import com.fishit.player.pipeline.telegram.model.TelegramPhotoSize

/**
 * Mapping utilities for converting between ObxTelegramMessage and Telegram domain models.
 *
 * Updated based on analysis of real Telegram export JSONs from docs/telegram/exports/exports/.
 *
 * **CONTRACT COMPLIANCE (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - Mappers provide RAW field extraction ONLY
 * - NO title cleaning, normalization, or heuristics
 * - Scene-style filenames preserved exactly as they appear
 * - All normalization delegated to :core:metadata-normalizer
 *
 * Handles:
 * - Video messages (standard video or document with video mime type)
 * - Document messages (RARs, ZIPs, archives)
 * - Audio messages (music, soundtracks)
 * - Photo messages (multiple sizes)
 * - Pure text metadata messages (year, FSK, genres, TMDB URL, etc.)
 */
object TelegramMappers {
    /**
     * Converts an ObxTelegramMessage entity to a TelegramMediaItem domain model.
     *
     * Performs RAW field extraction with NO normalization or cleaning.
     * Scene-style filenames and tags are preserved exactly.
     *
     * @param obxMessage ObjectBox entity from persistence layer
     * @return TelegramMediaItem domain model
     */
    fun fromObxTelegramMessage(obxMessage: ObxTelegramMessage): TelegramMediaItem =
        TelegramMediaItem(
            id = obxMessage.id,
            chatId = obxMessage.chatId,
            messageId = obxMessage.messageId,
            mediaAlbumId = null, // Not yet tracked in ObxTelegramMessage
            mediaType = inferMediaType(obxMessage),
            fileId = obxMessage.fileId,
            fileUniqueId = obxMessage.fileUniqueId,
            remoteId = obxMessage.remoteId,
            title = extractTitle(obxMessage),
            fileName = obxMessage.fileName,
            caption = obxMessage.caption,
            mimeType = obxMessage.mimeType,
            sizeBytes = obxMessage.sizeBytes,
            durationSecs = obxMessage.durationSecs,
            width = obxMessage.width,
            height = obxMessage.height,
            supportsStreaming = obxMessage.supportsStreaming,
            localPath = obxMessage.localPath,
            thumbnailFileId = null, // Will be populated from TDLib in future phases
            thumbnailUniqueId = null, // Will be populated from TDLib in future phases
            thumbnailWidth = null, // Will be populated from TDLib in future phases
            thumbnailHeight = null, // Will be populated from TDLib in future phases
            thumbnailPath = obxMessage.thumbLocalPath,
            photoSizes = emptyList(), // Will be populated from TDLib in future phases
            date = obxMessage.date,
            isSeries = obxMessage.isSeries,
            seriesName = obxMessage.seriesName,
            seasonNumber = obxMessage.seasonNumber,
            episodeNumber = obxMessage.episodeNumber,
            episodeTitle = obxMessage.episodeTitle,
            year = obxMessage.year,
            genres = obxMessage.genres,
            description = obxMessage.description,
        )

    /**
     * Converts a list of ObxTelegramMessage entities to TelegramMediaItem list.
     *
     * @param obxMessages List of ObjectBox entities
     * @return List of domain models
     */
    fun fromObxTelegramMessages(obxMessages: List<ObxTelegramMessage>): List<TelegramMediaItem> =
        obxMessages.map { fromObxTelegramMessage(it) }

    /**
     * Converts a TelegramMediaItem back to ObxTelegramMessage for persistence.
     *
     * Performs simple field mapping without TDLib state changes.
     *
     * @param mediaItem Domain model
     * @param existingId Existing ObjectBox ID if updating (0 for new)
     * @param existingMessage Optional existing ObxTelegramMessage to preserve internal fields
     * @return ObjectBox entity ready for persistence
     */
    fun toObxTelegramMessage(
        mediaItem: TelegramMediaItem,
        existingId: Long = 0,
        existingMessage: ObxTelegramMessage? = null,
    ): ObxTelegramMessage =
        ObxTelegramMessage(
            id = existingId,
            chatId = mediaItem.chatId,
            messageId = mediaItem.messageId,
            fileId = mediaItem.fileId,
            fileUniqueId = mediaItem.fileUniqueId ?: existingMessage?.fileUniqueId,
            remoteId = mediaItem.remoteId,
            supportsStreaming = mediaItem.supportsStreaming,
            caption = mediaItem.caption,
            captionLower = mediaItem.caption?.lowercase(),
            date = mediaItem.date,
            localPath = mediaItem.localPath,
            thumbFileId = existingMessage?.thumbFileId,
            thumbLocalPath = mediaItem.thumbnailPath,
            fileName = mediaItem.fileName,
            durationSecs = mediaItem.durationSecs,
            mimeType = mediaItem.mimeType,
            sizeBytes = mediaItem.sizeBytes,
            width = mediaItem.width,
            height = mediaItem.height,
            language = existingMessage?.language,
            title = mediaItem.title,
            year = mediaItem.year,
            genres = mediaItem.genres,
            fsk = existingMessage?.fsk,
            description = mediaItem.description,
            posterFileId = existingMessage?.posterFileId,
            posterLocalPath = existingMessage?.posterLocalPath,
            isSeries = mediaItem.isSeries,
            seriesName = mediaItem.seriesName,
            seriesNameNormalized = mediaItem.seriesName?.lowercase()?.trim(),
            seasonNumber = mediaItem.seasonNumber,
            episodeNumber = mediaItem.episodeNumber,
            episodeTitle = mediaItem.episodeTitle,
        )

    /**
     * Infers the media type from ObxTelegramMessage fields.
     *
     * Based on analysis of real Telegram export JSONs:
     * - VIDEO: video mime types or has durationSecs + width/height
     * - AUDIO: audio mime types
     * - DOCUMENT: document mime types (archives, RARs, ZIPs)
     * - PHOTO: photo-related mime types or has width/height without duration
     * - OTHER: anything else
     *
     * @param obxMessage ObjectBox entity
     * @return Inferred media type
     */
    private fun inferMediaType(obxMessage: ObxTelegramMessage): TelegramMediaType {
        val mimeType = obxMessage.mimeType?.lowercase()
        
        return when {
            mimeType?.startsWith("video/") == true -> TelegramMediaType.VIDEO
            mimeType?.startsWith("audio/") == true -> TelegramMediaType.AUDIO
            mimeType?.startsWith("image/") == true -> TelegramMediaType.PHOTO
            mimeType?.contains("zip") == true || 
                mimeType?.contains("rar") == true ||
                mimeType?.contains("octet-stream") == true -> TelegramMediaType.DOCUMENT
            obxMessage.durationSecs != null && 
                obxMessage.width != null && 
                obxMessage.height != null -> TelegramMediaType.VIDEO
            obxMessage.width != null && 
                obxMessage.height != null -> TelegramMediaType.PHOTO
            else -> TelegramMediaType.OTHER
        }
    }

    /**
     * Extracts a display title from ObxTelegramMessage.
     *
     * Simple priority selector (NO cleaning or normalization):
     * 1. title field
     * 2. episodeTitle field
     * 3. caption field
     * 4. fileName field
     * 5. Fallback: "Untitled Media {messageId}"
     *
     * Scene-style filenames are preserved exactly as they appear.
     * Examples that stay unchanged:
     * - "Movie.2020.1080p.BluRay.x264-GROUP.mkv" → returned AS-IS
     * - "Series.S01E05.HDTV.x264.rar" → returned AS-IS
     *
     * @param obxMessage ObjectBox entity
     * @return Display title string (RAW, no cleaning)
     */
    private fun extractTitle(obxMessage: ObxTelegramMessage): String =
        when {
            !obxMessage.title.isNullOrBlank() -> obxMessage.title!!
            !obxMessage.episodeTitle.isNullOrBlank() -> obxMessage.episodeTitle!!
            !obxMessage.caption.isNullOrBlank() -> obxMessage.caption!!
            !obxMessage.fileName.isNullOrBlank() -> obxMessage.fileName!!
            else -> "Untitled Media ${obxMessage.messageId}"
        }
}
