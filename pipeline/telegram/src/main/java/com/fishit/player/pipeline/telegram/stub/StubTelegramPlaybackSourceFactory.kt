package com.fishit.player.pipeline.telegram.stub

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.PlaybackType
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.repository.TelegramPlaybackSourceFactory

/**
 * Stub implementation of TelegramPlaybackSourceFactory for Phase 2 Task 3 (P2-T3).
 *
 * This implementation provides deterministic behavior for testing without real TDLib.
 * Converts TelegramMediaItem into PlaybackContext with stub Telegram URIs.
 *
 * NO REAL TDLib integration - uses placeholder URIs.
 */
class StubTelegramPlaybackSourceFactory : TelegramPlaybackSourceFactory {
    override fun createPlaybackContext(
        mediaItem: TelegramMediaItem,
        profileId: Long?,
        startPositionMs: Long,
    ): PlaybackContext =
        PlaybackContext(
            type = PlaybackType.TELEGRAM,
            uri = mediaItem.toTelegramUri(),
            title = mediaItem.title,
            subtitle = buildSubtitle(mediaItem),
            posterUrl = mediaItem.thumbnailPath,
            contentId = buildContentId(mediaItem),
            seriesId = mediaItem.seriesName,
            seasonNumber = mediaItem.seasonNumber,
            episodeNumber = mediaItem.episodeNumber,
            startPositionMs = startPositionMs,
            isKidsContent = false, // STUB: No kids detection
            profileId = profileId,
            extras = buildExtras(mediaItem),
        )

    override fun canPlay(mediaItem: TelegramMediaItem): Boolean {
        // Check if media item has minimum required metadata for playback
        return mediaItem.fileId != null && mediaItem.mimeType != null
    }

    private fun buildSubtitle(mediaItem: TelegramMediaItem): String? =
        when {
            mediaItem.isSeries && mediaItem.episodeTitle != null -> {
                "S${mediaItem.seasonNumber ?: 0}E${mediaItem.episodeNumber ?: 0}: ${mediaItem.episodeTitle}"
            }
            mediaItem.isSeries -> {
                "S${mediaItem.seasonNumber ?: 0}E${mediaItem.episodeNumber ?: 0}"
            }
            mediaItem.fileName != null -> {
                mediaItem.fileName
            }
            else -> null
        }

    private fun buildContentId(mediaItem: TelegramMediaItem): String = "telegram:${mediaItem.chatId}:${mediaItem.messageId}"

    private fun buildExtras(mediaItem: TelegramMediaItem): Map<String, String> {
        val extras = mutableMapOf<String, String>()

        mediaItem.fileId?.let { extras["fileId"] = it.toString() }
        mediaItem.remoteId?.let { extras["remoteId"] = it }
        mediaItem.mimeType?.let { extras["mimeType"] = it }
        mediaItem.sizeBytes?.let { extras["sizeBytes"] = it.toString() }
        mediaItem.durationSecs?.let { extras["durationSecs"] = it.toString() }
        mediaItem.supportsStreaming?.let { extras["supportsStreaming"] = it.toString() }

        return extras
    }
}
