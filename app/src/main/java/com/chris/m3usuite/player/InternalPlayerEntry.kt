package com.chris.m3usuite.player

import androidx.compose.runtime.Composable
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType

/**
 * Phase 1 bridging entry point for the internal player.
 *
 * Accepts the new typed PlaybackContext model but delegates to the legacy
 * monolithic InternalPlayerScreen implementation to preserve runtime behavior.
 *
 * Future phases will replace the legacy implementation with the modular SIP-based
 * architecture without requiring changes to call sites.
 */
@androidx.media3.common.util.UnstableApi
@Composable
fun InternalPlayerEntry(
    url: String,
    startMs: Long?,
    mimeType: String?,
    headers: Map<String, String> = emptyMap(),
    mediaItem: com.chris.m3usuite.model.MediaItem?,
    playbackContext: PlaybackContext,
    onExit: () -> Unit,
) {
    // Phase 1 bridge: delegate to the monolithic legacy implementation
    InternalPlayerScreen(
        url = url,
        type = when (playbackContext.type) {
            PlaybackType.LIVE -> "live"
            PlaybackType.SERIES -> "series"
            PlaybackType.VOD -> "vod"
        },
        mediaId = playbackContext.mediaId,
        episodeId = playbackContext.episodeId,
        seriesId = playbackContext.seriesId,
        season = playbackContext.season,
        episodeNum = playbackContext.episodeNumber,
        startPositionMs = startMs,
        headers = headers,
        mimeType = mimeType,
        onExit = onExit,
        originLiveLibrary = playbackContext.liveCategoryHint != null,
        liveCategoryHint = playbackContext.liveCategoryHint,
        liveProviderHint = playbackContext.liveProviderHint,
        preparedMediaItem = mediaItem,
    )
}
