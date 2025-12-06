package com.fishit.player.pipeline.xtream.source

import com.fishit.player.core.model.PlaybackContext

/**
 * Factory interface for creating playback sources from Xtream content.
 *
 * This interface defines how to resolve Xtream content (VOD, Series, Live)
 * into playable [PlaybackContext] objects. The factory is responsible for:
 * - Validating content availability
 * - Constructing proper stream URLs
 * - Building PlaybackContext with all necessary metadata
 *
 * ## Phase 2 Stub Behavior
 * In Phase 2, this is a marker interface with no concrete implementation needed,
 * as playback sources are constructed directly via extension functions.
 *
 * ## Phase 3+ Implementation
 * Full implementation will include:
 * - URL resolution with authentication tokens
 * - Stream format negotiation (HLS, MPEGTS, etc.)
 * - Adaptive bitrate selection
 * - Catchup TV support for live channels
 * - DRM integration if needed
 *
 * @see PlaybackContext
 */
interface XtreamPlaybackSourceFactory {
    /**
     * Creates a playback source for a VOD item.
     *
     * @param vodId Unique VOD item identifier
     * @return PlaybackContext configured for VOD playback, or null if not available
     */
    suspend fun createVodSource(vodId: Long): PlaybackContext?

    /**
     * Creates a playback source for a series episode.
     *
     * @param seriesId Unique series identifier
     * @param seasonNumber Season number (1-based)
     * @param episodeNumber Episode number (1-based)
     * @return PlaybackContext configured for episode playback, or null if not available
     */
    suspend fun createSeriesEpisodeSource(
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
    ): PlaybackContext?

    /**
     * Creates a playback source for a live TV channel.
     *
     * @param channelId Unique channel identifier
     * @return PlaybackContext configured for live stream playback, or null if not available
     */
    suspend fun createLiveChannelSource(channelId: Long): PlaybackContext?
}
