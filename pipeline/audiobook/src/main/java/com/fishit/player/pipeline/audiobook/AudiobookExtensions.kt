package com.fishit.player.pipeline.audiobook

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.PlaybackType

/**
 * Converts an AudiobookItem to a PlaybackContext for playback initiation.
 *
 * This helper provides a standard conversion from audiobook domain models to the
 * core PlaybackContext model used by the player. It includes all relevant metadata
 * and uses a custom URI scheme (audiobook://) for audiobook-specific handling.
 *
 * @param startChapterNumber Optional chapter to start from (1-based, defaults to 1)
 * @return PlaybackContext configured for this audiobook
 */
fun AudiobookItem.toPlaybackContext(startChapterNumber: Int = 1): PlaybackContext {
    // Build metadata map including audiobook-specific information
    val metadata =
        buildMap {
            put("author", author)
            narrator?.let { put("narrator", it) }
            put("totalDurationMs", totalDurationMs.toString())
            put("chapterCount", chapters.size.toString())
            put("startChapter", startChapterNumber.toString())
            filePath?.let { put("filePath", it) }

            // Include original metadata
            putAll(this@toPlaybackContext.metadata)
        }

    // Use custom audiobook:// URI scheme for future routing
    val uri = filePath ?: "audiobook://$id"

    return PlaybackContext(
        type = PlaybackType.AUDIO,
        uri = uri,
        title = title,
        subtitle = "by $author",
        posterUrl = coverUrl,
        contentId = id,
        metadata = metadata,
    )
}

/**
 * Converts an AudiobookChapter to a PlaybackContext for direct chapter playback.
 *
 * This helper creates a PlaybackContext that starts at a specific chapter position,
 * useful for chapter-based navigation.
 *
 * @param parentAudiobook The audiobook this chapter belongs to
 * @return PlaybackContext configured to start at this chapter
 */
fun AudiobookChapter.toPlaybackContext(parentAudiobook: AudiobookItem): PlaybackContext {
    val metadata =
        buildMap {
            put("audiobookId", audiobookId)
            put("chapterNumber", chapterNumber.toString())
            put("startPositionMs", startPositionMs.toString())
            put("endPositionMs", endPositionMs.toString())
            put("durationMs", durationMs.toString())
            filePath?.let { put("filePath", it) }
            put("author", parentAudiobook.author)
            parentAudiobook.narrator?.let { put("narrator", it) }
        }

    // Use chapter-specific URI if available, otherwise use audiobook URI with chapter marker
    val uri = filePath ?: "audiobook://${parentAudiobook.id}#chapter=$chapterNumber"

    return PlaybackContext(
        type = PlaybackType.AUDIO,
        uri = uri,
        title = "$title - ${parentAudiobook.title}",
        subtitle = "Chapter $chapterNumber - by ${parentAudiobook.author}",
        posterUrl = parentAudiobook.coverUrl,
        contentId = id,
        metadata = metadata,
    )
}
