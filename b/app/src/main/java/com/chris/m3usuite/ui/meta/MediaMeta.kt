package com.chris.m3usuite.ui.meta

import androidx.compose.runtime.Stable

@Stable
data class MediaMeta(
    val year: Int? = null,
    val durationMinutes: Int? = null,
    val video: VideoInfo? = null,
    val audio: AudioInfo? = null,
    val genres: List<String> = emptyList()
)

@Stable
data class VideoInfo(
    val width: Int? = null,
    val height: Int? = null,
    val hdr: Boolean = false,
    val fps: Int? = null
)

@Stable
data class AudioInfo(
    val channels: String? = null,            // "2.0", "5.1", "7.1" …
    val languages: List<String> = emptyList() // ISO codes or names ("de","en","german",…)
)

internal fun secondsToMinutesOrNull(secs: Int?): Int? {
    if (secs == null) return null
    if (secs <= 0) return null
    return (secs / 60).coerceAtLeast(1)
}

internal fun parseGenres(raw: String?): List<String> =
    raw?.split(',', ';', '|', '/', '•')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
