package com.fishit.player.internal.source

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.PlaybackType

/**
 * Resolves playback sources for the internal player.
 *
 * In Phase 1, this always returns a hardcoded test URL.
 * In Phase 2+, this will integrate with pipeline-specific source factories.
 */
interface InternalPlaybackSourceResolver {
    /**
     * Resolves the final playback URI for the given context.
     *
     * @param context The playback context.
     * @return The URI to play.
     */
    fun resolveSource(context: PlaybackContext): String
}

/**
 * Default implementation that provides a test stream for Phase 1.
 *
 * Uses Big Buck Bunny as a reliable public test stream.
 */
class DefaultPlaybackSourceResolver : InternalPlaybackSourceResolver {

    companion object {
        /**
         * Big Buck Bunny - reliable public domain test video.
         * MP4 format, works on all devices.
         */
        const val TEST_STREAM_URL = 
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        
        /**
         * Alternative: Sintel trailer for variety.
         */
        const val ALT_TEST_STREAM_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
    }

    override fun resolveSource(context: PlaybackContext): String {
        // Phase 1: Always use test URL regardless of context
        // Later phases will resolve based on PlaybackType and pipeline data
        return when (context.type) {
            PlaybackType.VOD,
            PlaybackType.SERIES -> {
                // If context has a real URI, use it; otherwise use test
                if (context.uri.startsWith("http")) {
                    context.uri
                } else {
                    TEST_STREAM_URL
                }
            }
            PlaybackType.LIVE -> {
                // Live would use a different test stream (HLS)
                if (context.uri.startsWith("http")) {
                    context.uri
                } else {
                    TEST_STREAM_URL
                }
            }
            PlaybackType.TELEGRAM -> {
                // Phase 2: Use Telegram URI from context
                // URI format: tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>
                if (context.uri.startsWith("tg://")) {
                    context.uri
                } else {
                    TEST_STREAM_URL
                }
            }
            PlaybackType.AUDIOBOOK -> {
                // Future: audiobook-specific handling
                TEST_STREAM_URL
            }
            PlaybackType.IO -> {
                // IO uses the provided URI directly
                if (context.uri.isNotEmpty()) {
                    context.uri
                } else {
                    TEST_STREAM_URL
                }
            }
        }
    }
}
