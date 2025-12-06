package com.fishit.player.pipeline.io

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.PlaybackType

/**
 * Extension functions for converting IO pipeline models to core models.
 */

/**
 * Converts an IoMediaItem to a PlaybackContext.
 *
 * This helper function bridges the IO pipeline domain model to the
 * playback-agnostic PlaybackContext used by the internal player.
 *
 * **Usage:**
 * ```kotlin
 * val item: IoMediaItem = repository.getItemById("some-id")
 * val context = item.toPlaybackContext()
 * // Navigate to InternalPlayerEntry(context)
 * ```
 *
 * @param profileId Current profile ID for tracking (optional).
 * @param startPositionMs Starting position for resume (optional).
 * @param isKidsContent Whether to treat as kids content (optional).
 * @return PlaybackContext suitable for InternalPlayerEntry.
 */
fun IoMediaItem.toPlaybackContext(
    profileId: Long? = null,
    startPositionMs: Long = 0L,
    isKidsContent: Boolean = false,
): PlaybackContext =
    PlaybackContext(
        type = PlaybackType.IO,
        uri = source.toUriString(),
        title = title,
        subtitle = fileName,
        posterUrl = thumbnailPath,
        contentId = toContentId(),
        startPositionMs = startPositionMs,
        isKidsContent = isKidsContent,
        profileId = profileId,
        extras =
            buildMap {
                mimeType?.let { put("mimeType", it) }
                sizeBytes?.let { put("sizeBytes", it.toString()) }
                durationMs?.let { put("durationMs", it.toString()) }
                putAll(metadata)
            },
    )
