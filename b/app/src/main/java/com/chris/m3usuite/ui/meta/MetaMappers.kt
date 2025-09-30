package com.chris.m3usuite.ui.meta

import com.chris.m3usuite.model.MediaItem

/**
 * Lightweight, source-agnostic mappers to MediaMeta.
 * These avoid touching network or heavy repos and derive only display-level metadata.
 */
object MetaMappers {

    /**
     * Generic builder when only year/duration/genre are known.
     */
    fun fromBasics(
        year: Int? = null,
        durationSecs: Int? = null,
        genresRaw: String? = null,
        video: VideoInfo? = null,
        audio: AudioInfo? = null
    ): MediaMeta {
        val mins = secondsToMinutesOrNull(durationSecs)
        val genres = parseGenres(genresRaw)
        return MediaMeta(
            year = year,
            durationMinutes = mins,
            video = video,
            audio = audio,
            genres = genres
        )
    }

    /**
     * Map a generic MediaItem (OBX-backed) to a MediaMeta.
     * Genres are not carried on MediaItem; callers can augment via [extraGenres].
     */
    fun fromMediaItem(item: MediaItem, extraGenres: List<String> = emptyList()): MediaMeta {
        val mins = secondsToMinutesOrNull(item.durationSecs)
        return MediaMeta(
            year = item.year,
            durationMinutes = mins,
            video = null,
            audio = null,
            genres = extraGenres
        )
    }
