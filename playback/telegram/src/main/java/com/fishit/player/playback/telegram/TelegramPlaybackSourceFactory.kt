package com.fishit.player.playback.telegram

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.RawMediaMetadata

/**
 * Factory interface for creating playback sources from Telegram media.
 *
 * This is a STUB interface for Phase 2 Task 3 (P2-T3).
 * Defines the contract for converting RawMediaMetadata (from Telegram pipeline)
 * into PlaybackContext that the internal player can consume.
 *
 * **Architecture Note:**
 * - Uses RawMediaMetadata (not TelegramMediaItem) to respect layer boundaries
 * - Playback layer must NOT depend on Pipeline layer
 * - Source-specific logic (tg:// URI resolution) handled internally
 *
 * Future implementations will:
 * - Resolve Telegram file URIs (tg:// scheme) from sourceId
 * - Integrate with TelegramFileDataSource for zero-copy streaming
 * - Handle download state and offline playback
 */
interface TelegramPlaybackSourceFactory {
    /**
     * Converts RawMediaMetadata into a PlaybackContext for the internal player.
     *
     * STUB: Returns a basic PlaybackContext with the Telegram URI.
     *
     * @param metadata The raw media metadata from Telegram pipeline
     * @param profileId Current user profile ID (for tracking and kids mode)
     * @param startPositionMs Optional starting position (for resume)
     * @return PlaybackContext ready for internal player
     */
    fun createPlaybackContext(
        metadata: RawMediaMetadata,
        profileId: Long? = null,
        startPositionMs: Long = 0L,
    ): PlaybackContext

    /**
     * Validates whether media can be played.
     *
     * STUB: Returns false for items without valid sourceId.
     *
     * @param metadata The metadata to validate
     * @return True if playable, false otherwise
     */
    fun canPlay(metadata: RawMediaMetadata): Boolean
}
