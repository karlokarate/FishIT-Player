package com.fishit.player.core.model

/**
 * Media type classification for content items.
 *
 * This enum provides fine-grained media type classification used by pipelines
 * and the metadata normalization system to categorize content.
 *
 * Unlike MediaKind (which is binary: MOVIE or EPISODE), MediaType provides
 * more detailed categorization including live content, clips, audiobooks, etc.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Pipelines should provide the most specific MediaType they can determine
 * - The normalizer may refine the type during processing
 * - Some content may not fit cleanly into a single category
 */
enum class MediaType {
    /**
     * Feature film or VOD movie (typically > 40 minutes).
     * Examples: theatrical releases, made-for-TV movies, documentaries.
     */
    MOVIE,

    /**
     * TV series container/metadata.
     * Represents the series itself, not an individual episode.
     * Examples: "Breaking Bad" (the series), "The Office" (the series).
     * Used for series cards in UI, not for playback.
     */
    SERIES,

    /**
     * TV series episode.
     * Examples: episodic television, web series episodes.
     */
    SERIES_EPISODE,

    /**
     * Live streaming content (ongoing broadcast).
     * Examples: live TV channels, live sports, news broadcasts.
     */
    LIVE,

    /**
     * Short-form video clip (typically < 40 minutes).
     * Examples: trailers, music videos, YouTube clips, behind-the-scenes.
     */
    CLIP,

    /**
     * Audiobook content.
     * Examples: narrated books, audio drama series.
     */
    AUDIOBOOK,

    /**
     * Music album or track.
     * Examples: music albums, individual songs, playlists.
     */
    MUSIC,

    /**
     * Podcast episode.
     * Examples: podcast episodes, radio shows.
     */
    PODCAST,

    /**
     * Unknown or undetermined media type.
     * Used when the pipeline cannot determine a more specific type.
     */
    UNKNOWN,
}
