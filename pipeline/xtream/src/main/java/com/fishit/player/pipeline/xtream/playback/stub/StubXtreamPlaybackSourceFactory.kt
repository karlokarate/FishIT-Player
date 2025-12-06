package com.fishit.player.pipeline.xtream.playback.stub

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.PlaybackType
import com.fishit.player.pipeline.xtream.playback.XtreamPlaybackSource
import com.fishit.player.pipeline.xtream.playback.XtreamPlaybackSourceFactory

/**
 * Stub implementation of [XtreamPlaybackSourceFactory] for Phase 2.
 *
 * This implementation returns basic playback sources without real streaming logic.
 * It is designed to allow the v2 architecture to compile and integrate without
 * requiring actual Xtream URL building or authentication.
 *
 * Production implementation will:
 * - Build proper Xtream URLs with portal credentials
 * - Add authentication headers
 * - Configure appropriate stream formats
 *
 * Constructor is injectable - no Hilt modules required for stub phase.
 */
class StubXtreamPlaybackSourceFactory : XtreamPlaybackSourceFactory {
    override fun createSource(context: PlaybackContext): XtreamPlaybackSource =
        XtreamPlaybackSource(
            uri = context.uri,
            contentType = inferContentType(context.type),
            headers = emptyMap(),
        )

    override fun supportsContext(context: PlaybackContext): Boolean =
        when (context.type) {
            PlaybackType.VOD,
            PlaybackType.SERIES,
            PlaybackType.LIVE,
            -> true
            else -> false
        }

    private fun inferContentType(type: PlaybackType): String =
        when (type) {
            PlaybackType.LIVE -> "application/x-mpegURL" // HLS for live
            PlaybackType.VOD,
            PlaybackType.SERIES,
            -> "video/mp4" // MP4 for VOD/Series
            else -> "application/octet-stream"
        }
}
