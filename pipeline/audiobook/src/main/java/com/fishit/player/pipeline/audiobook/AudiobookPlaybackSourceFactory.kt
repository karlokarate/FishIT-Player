package com.fishit.player.pipeline.audiobook

import com.fishit.player.core.model.PlaybackContext

/**
 * Factory interface for creating playback sources for audiobook content.
 *
 * This interface provides the integration point between the audiobook pipeline
 * and the internal player. It's responsible for creating appropriate playback
 * configurations for audiobook playback, including chapter-based navigation.
 *
 * Phase 2 provides stub implementations. Future phases will support:
 * - RAR/ZIP archive streaming without full extraction
 * - Chapter-based seeking and navigation
 * - Bookmark management
 * - Playback speed control
 * - Sleep timer integration
 */
interface AudiobookPlaybackSourceFactory {
    /**
     * Create a playback context for an audiobook.
     *
     * This method prepares the audiobook for playback, resolving file paths
     * and setting up chapter markers.
     *
     * @param audiobook The audiobook to play
     * @param startChapterNumber Optional chapter to start from (1-based)
     * @return PlaybackContext configured for audiobook playback, or null if unavailable
     */
    suspend fun createPlaybackContext(
        audiobook: AudiobookItem,
        startChapterNumber: Int = 1,
    ): PlaybackContext?

    /**
     * Create a playback context for a specific chapter.
     *
     * @param audiobook The parent audiobook
     * @param chapter The chapter to play
     * @return PlaybackContext configured to start at the chapter, or null if unavailable
     */
    suspend fun createPlaybackContextForChapter(
        audiobook: AudiobookItem,
        chapter: AudiobookChapter,
    ): PlaybackContext?
}
