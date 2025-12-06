package com.fishit.player.pipeline.xtream.model

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.PlaybackType

/**
 * Converts an [XtreamVodItem] to a [PlaybackContext] for playback.
 *
 * This helper extension enables the Xtream pipeline to integrate with the
 * v2 internal player without tight coupling.
 *
 * @param uri The resolved playback URI for this VOD item
 * @param profileId Optional profile ID for tracking
 * @param startPositionMs Optional starting position for resume
 * @return A [PlaybackContext] ready for playback
 */
fun XtreamVodItem.toPlaybackContext(
    uri: String,
    profileId: Long? = null,
    startPositionMs: Long = 0L,
): PlaybackContext =
    PlaybackContext(
        type = PlaybackType.VOD,
        uri = uri,
        title = name,
        posterUrl = streamIcon,
        contentId = id.toString(),
        startPositionMs = startPositionMs,
        profileId = profileId,
        extras =
            mapOf(
                "categoryId" to (categoryId ?: ""),
                "containerExtension" to (containerExtension ?: ""),
            ),
    )

/**
 * Converts an [XtreamEpisode] to a [PlaybackContext] for playback.
 *
 * @param uri The resolved playback URI for this episode
 * @param profileId Optional profile ID for tracking
 * @param startPositionMs Optional starting position for resume
 * @return A [PlaybackContext] ready for playback
 */
fun XtreamEpisode.toPlaybackContext(
    uri: String,
    profileId: Long? = null,
    startPositionMs: Long = 0L,
): PlaybackContext =
    PlaybackContext(
        type = PlaybackType.SERIES,
        uri = uri,
        title = title,
        posterUrl = thumbnail,
        contentId = id.toString(),
        seriesId = seriesId.toString(),
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        startPositionMs = startPositionMs,
        profileId = profileId,
        extras =
            mapOf(
                "containerExtension" to (containerExtension ?: ""),
            ),
    )

/**
 * Converts an [XtreamChannel] to a [PlaybackContext] for live playback.
 *
 * @param uri The resolved playback URI for this channel
 * @param profileId Optional profile ID for tracking
 * @return A [PlaybackContext] ready for playback
 */
fun XtreamChannel.toPlaybackContext(
    uri: String,
    profileId: Long? = null,
): PlaybackContext =
    PlaybackContext(
        type = PlaybackType.LIVE,
        uri = uri,
        title = name,
        posterUrl = streamIcon,
        contentId = id.toString(),
        profileId = profileId,
        extras =
            mapOf(
                "epgChannelId" to (epgChannelId ?: ""),
                "tvArchive" to tvArchive.toString(),
                "categoryId" to (categoryId ?: ""),
            ),
    )
