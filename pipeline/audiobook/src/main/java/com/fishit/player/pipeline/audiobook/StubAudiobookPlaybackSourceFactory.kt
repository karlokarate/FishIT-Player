package com.fishit.player.pipeline.audiobook

import com.fishit.player.core.model.PlaybackContext

/**
 * Stub implementation of AudiobookPlaybackSourceFactory for Phase 2.
 *
 * This implementation returns null for all playback requests to establish
 * the factory contract without actual file access or streaming setup.
 *
 * Future phases will implement:
 * - RAR/ZIP archive streaming via custom DataSource
 * - Chapter-based seeking with precise positioning
 * - Integration with resume/bookmark system
 * - Speed control and audio effects
 */
class StubAudiobookPlaybackSourceFactory : AudiobookPlaybackSourceFactory {
    override suspend fun createPlaybackContext(
        audiobook: AudiobookItem,
        startChapterNumber: Int,
    ): PlaybackContext? {
        // Stub returns null (no playback source available)
        // Future: Create PlaybackContext with proper URI scheme (e.g., audiobook://{id})
        // and configure chapter markers for Media3 integration
        return null
    }

    override suspend fun createPlaybackContextForChapter(
        audiobook: AudiobookItem,
        chapter: AudiobookChapter,
    ): PlaybackContext? {
        // Stub returns null (no playback source available)
        // Future: Create PlaybackContext starting at chapter position
        // with proper seek configuration
        return null
    }
}
