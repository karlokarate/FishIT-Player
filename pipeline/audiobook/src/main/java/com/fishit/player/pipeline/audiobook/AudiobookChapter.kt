package com.fishit.player.pipeline.audiobook

/**
 * Represents a chapter within an audiobook.
 *
 * Chapters provide navigation points within an audiobook, allowing users to skip
 * to specific sections. Chapter information can be embedded in audio metadata,
 * or parsed from separate chapter files in RAR/ZIP archives.
 *
 * @property id Unique identifier for this chapter
 * @property audiobookId ID of the parent audiobook
 * @property title Chapter title
 * @property chapterNumber Chapter sequence number (1-based)
 * @property startPositionMs Start position in milliseconds within the audiobook
 * @property endPositionMs End position in milliseconds within the audiobook
 * @property durationMs Chapter duration in milliseconds
 * @property filePath Optional file path if chapter is a separate audio file
 */
data class AudiobookChapter(
    val id: String,
    val audiobookId: String,
    val title: String,
    val chapterNumber: Int,
    val startPositionMs: Long,
    val endPositionMs: Long,
    val durationMs: Long = endPositionMs - startPositionMs,
    val filePath: String? = null,
)
