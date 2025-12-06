package com.fishit.player.pipeline.telegram.mapper

import com.fishit.player.core.persistence.obx.ObxTelegramMessage
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem

/**
 * Mapping utilities for converting between ObxTelegramMessage and TelegramMediaItem.
 *
 * This is a STUB implementation for Phase 2 Task 3 (P2-T3).
 * Demonstrates the structure of mapping from ObxTelegramMessage to domain models
 * without requiring real TDLib integration.
 *
 * Future implementations will:
 * - Add real TDLib data enrichment
 * - Handle thumbnail and poster loading
 * - Implement content parsing heuristics
 */
object TelegramMappers {
    /**
     * Converts an ObxTelegramMessage entity to a TelegramMediaItem domain model.
     *
     * STUB: Simple field mapping, no TDLib enrichment.
     *
     * @param obxMessage ObjectBox entity from persistence layer
     * @return TelegramMediaItem domain model
     */
    fun fromObxTelegramMessage(obxMessage: ObxTelegramMessage): TelegramMediaItem =
        TelegramMediaItem(
            id = obxMessage.id,
            chatId = obxMessage.chatId,
            messageId = obxMessage.messageId,
            fileId = obxMessage.fileId,
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
            thumbnailPath = obxMessage.thumbLocalPath,
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
     * STUB: Simple field mapping, no TDLib state.
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
            fileUniqueId = existingMessage?.fileUniqueId,
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
     * Extracts a display title from ObxTelegramMessage.
     * Priority: title > episodeTitle > caption > fileName > "Untitled"
     *
     * @param obxMessage ObjectBox entity
     * @return Display title string
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
