package com.fishit.player.pipeline.audiobook

/**
 * Represents an audiobook with its metadata and chapters.
 *
 * This model encapsulates all information needed to display and play an audiobook.
 * Future phases will support loading audiobooks from RAR/ZIP archives with embedded
 * chapter markers and cover art.
 *
 * @property id Unique identifier for this audiobook
 * @property title The audiobook title
 * @property author The audiobook author(s)
 * @property narrator Optional narrator name
 * @property coverUrl Optional URL to cover art
 * @property totalDurationMs Total duration across all chapters in milliseconds
 * @property chapters List of chapters in this audiobook
 * @property filePath Optional file path for local audiobook files
 * @property metadata Additional metadata (publisher, ISBN, genre, etc.)
 */
data class AudiobookItem(
    val id: String,
    val title: String,
    val author: String,
    val narrator: String? = null,
    val coverUrl: String? = null,
    val totalDurationMs: Long = 0L,
    val chapters: List<AudiobookChapter> = emptyList(),
    val filePath: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)
