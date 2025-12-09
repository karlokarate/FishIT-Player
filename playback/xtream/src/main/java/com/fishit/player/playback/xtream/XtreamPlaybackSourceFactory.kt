package com.fishit.player.playback.xtream

import com.fishit.player.core.model.PlaybackContext

/**
 * Factory interface for creating Xtream playback sources.
 *
 * This factory is responsible for converting Xtream [PlaybackContext] into
 * playback source descriptors that the internal player's source resolver can use.
 *
 * Phase 2 stub: Returns basic source descriptors without real streaming logic.
 *
 * Production implementation will:
 * - Build proper Xtream stream URLs with authentication
 * - Configure appropriate DataSource implementations
 * - Handle different stream types (HLS, MPEG-TS, etc.)
 */
interface XtreamPlaybackSourceFactory {
    /**
     * Creates a playback source descriptor for the given context.
     *
     * @param context The playback context containing stream information
     * @return A source descriptor that can be used by the internal player
     */
    fun createSource(context: PlaybackContext): XtreamPlaybackSource

    /**
     * Validates whether the given context is supported by this factory.
     *
     * @param context The playback context to validate
     * @return true if this factory can handle the context
     */
    fun supportsContext(context: PlaybackContext): Boolean
}

/**
 * Represents an Xtream playback source descriptor.
 *
 * This is a pipeline-specific data structure that the internal player's
 * source resolver can use to create appropriate Media3 MediaItems.
 *
 * Phase 2 stub: Contains minimal information.
 *
 * @property uri The playback URI
 * @property contentType The MIME type or stream format hint
 * @property headers Optional HTTP headers for authentication
 */
data class XtreamPlaybackSource(
    val uri: String,
    val contentType: String? = null,
    val headers: Map<String, String> = emptyMap(),
)
