package com.fishit.player.pipeline.telegram.repository

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem

/**
 * Factory interface for creating playback sources from Telegram media items.
 *
 * This is a STUB interface for Phase 2 Task 3 (P2-T3).
 * Defines the contract for converting TelegramMediaItem into PlaybackContext
 * that the internal player can consume.
 *
 * Future implementations will:
 * - Resolve Telegram file URIs (tg:// scheme)
 * - Integrate with TelegramFileDataSource for zero-copy streaming
 * - Handle download state and offline playback
 */
interface TelegramPlaybackSourceFactory {
    /**
     * Converts a TelegramMediaItem into a PlaybackContext for the internal player.
     *
     * STUB: Returns a basic PlaybackContext with the Telegram URI.
     *
     * @param mediaItem The Telegram media item to convert
     * @param profileId Current user profile ID (for tracking and kids mode)
     * @param startPositionMs Optional starting position (for resume)
     * @return PlaybackContext ready for internal player
     */
    fun createPlaybackContext(
        mediaItem: TelegramMediaItem,
        profileId: Long? = null,
        startPositionMs: Long = 0L,
    ): PlaybackContext

    /**
     * Validates whether a media item can be played.
     *
     * STUB: Returns false for items without file ID.
     *
     * @param mediaItem The media item to validate
     * @return True if playable, false otherwise
     */
    fun canPlay(mediaItem: TelegramMediaItem): Boolean
}
