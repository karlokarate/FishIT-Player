package com.fishit.player.pipeline.telegram.ext

import com.fishit.player.pipeline.telegram.model.PlaybackContext
import com.fishit.player.pipeline.telegram.model.PlaybackType
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem

/**
 * Extension functions for converting Telegram models to playback domain models.
 */

/**
 * Convert a TelegramMediaItem to a PlaybackContext.
 *
 * This helper enables integration with the playback domain layer.
 * It determines the appropriate PlaybackType based on media metadata:
 * - SERIES if isSeries is true
 * - TELEGRAM otherwise
 *
 * @return PlaybackContext suitable for playback orchestration
 */
fun TelegramMediaItem.toPlaybackContext(): PlaybackContext =
    if (isSeries) {
        PlaybackContext(
            type = PlaybackType.SERIES,
            mediaId = messageId,
            telegramChatId = chatId,
            telegramMessageId = messageId,
            telegramFileId = fileId?.toString(),
            seriesId = seriesNameNormalized?.hashCode(), // Temporary ID generation
            season = seasonNumber,
            episodeNumber = episodeNumber,
        )
    } else {
        PlaybackContext(
            type = PlaybackType.TELEGRAM,
            mediaId = messageId,
            telegramChatId = chatId,
            telegramMessageId = messageId,
            telegramFileId = fileId?.toString(),
        )
    }

/**
 * Convert a list of TelegramMediaItems to PlaybackContexts.
 *
 * @return List of PlaybackContext objects
 */
fun List<TelegramMediaItem>.toPlaybackContexts(): List<PlaybackContext> = map { it.toPlaybackContext() }
