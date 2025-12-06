package com.fishit.player.pipeline.xtream.ext

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.PlaybackType
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamVodItem

/**
 * Converts an [XtreamVodItem] to a [PlaybackContext] for VOD playback.
 *
 * @return PlaybackContext configured with VOD type and all relevant metadata
 */
fun XtreamVodItem.toPlaybackContext(): PlaybackContext =
    PlaybackContext(
        type = PlaybackType.VOD,
        uri = streamUrl,
        title = name,
        contentId = "xtream-vod-$id",
        posterUrl = posterUrl,
        vodId = id,
    )

/**
 * Converts an [XtreamEpisode] to a [PlaybackContext] for series episode playback.
 *
 * @return PlaybackContext configured with SERIES type and episode metadata
 */
fun XtreamEpisode.toPlaybackContext(): PlaybackContext =
    PlaybackContext(
        type = PlaybackType.SERIES,
        uri = streamUrl,
        title = title,
        contentId = "xtream-episode-$id",
        posterUrl = posterUrl,
        seriesId = seriesId,
        season = seasonNumber,
        episode = episodeNumber,
    )

/**
 * Converts an [XtreamChannel] to a [PlaybackContext] for live TV playback.
 *
 * @return PlaybackContext configured with LIVE type and channel metadata
 */
fun XtreamChannel.toPlaybackContext(): PlaybackContext =
    PlaybackContext(
        type = PlaybackType.LIVE,
        uri = streamUrl,
        title = name,
        contentId = "xtream-live-$id",
        posterUrl = logoUrl,
        liveChannelId = id,
    )
