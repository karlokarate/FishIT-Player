package com.fishit.player.pipeline.audiobook

import com.fishit.player.core.model.PlaybackType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for audiobook extension functions.
 *
 * Verifies that AudiobookItem and AudiobookChapter correctly convert to PlaybackContext
 * with appropriate metadata and URI schemes.
 */
class AudiobookExtensionsTest {
    @Test
    fun `AudiobookItem toPlaybackContext creates valid context`() {
        val audiobook =
            AudiobookItem(
                id = "book-123",
                title = "Test Book",
                author = "Test Author",
                narrator = "Test Narrator",
                coverUrl = "https://example.com/cover.jpg",
                totalDurationMs = 3600000L,
                filePath = "/path/to/book.m4b",
            )

        val context = audiobook.toPlaybackContext()

        assertEquals(PlaybackType.AUDIO, context.type)
        assertEquals("/path/to/book.m4b", context.uri)
        assertEquals("Test Book", context.title)
        assertEquals("by Test Author", context.subtitle)
        assertEquals("https://example.com/cover.jpg", context.posterUrl)
        assertEquals("book-123", context.contentId)

        // Verify metadata
        assertEquals("Test Author", context.metadata["author"])
        assertEquals("Test Narrator", context.metadata["narrator"])
        assertEquals("3600000", context.metadata["totalDurationMs"])
        assertEquals("/path/to/book.m4b", context.metadata["filePath"])
    }

    @Test
    fun `AudiobookItem toPlaybackContext without filePath uses custom URI`() {
        val audiobook =
            AudiobookItem(
                id = "book-456",
                title = "Another Book",
                author = "Another Author",
            )

        val context = audiobook.toPlaybackContext()

        assertEquals("audiobook://book-456", context.uri)
    }

    @Test
    fun `AudiobookItem toPlaybackContext with start chapter includes metadata`() {
        val audiobook =
            AudiobookItem(
                id = "book-789",
                title = "Chapter Book",
                author = "Chapter Author",
                chapters =
                    listOf(
                        AudiobookChapter("ch1", "book-789", "Chapter 1", 1, 0L, 600000L),
                        AudiobookChapter("ch2", "book-789", "Chapter 2", 2, 600000L, 1200000L),
                        AudiobookChapter("ch3", "book-789", "Chapter 3", 3, 1200000L, 1800000L),
                    ),
            )

        val context = audiobook.toPlaybackContext(startChapterNumber = 2)

        assertEquals("3", context.metadata["chapterCount"])
        assertEquals("2", context.metadata["startChapter"])
    }

    @Test
    fun `AudiobookChapter toPlaybackContext creates valid context`() {
        val audiobook =
            AudiobookItem(
                id = "book-123",
                title = "Test Book",
                author = "Test Author",
                narrator = "Test Narrator",
                coverUrl = "https://example.com/cover.jpg",
            )

        val chapter =
            AudiobookChapter(
                id = "chapter-5",
                audiobookId = "book-123",
                title = "The Fifth Chapter",
                chapterNumber = 5,
                startPositionMs = 2400000L,
                endPositionMs = 3000000L,
            )

        val context = chapter.toPlaybackContext(audiobook)

        assertEquals(PlaybackType.AUDIO, context.type)
        assertEquals("audiobook://book-123#chapter=5", context.uri)
        assertEquals("The Fifth Chapter - Test Book", context.title)
        assertEquals("Chapter 5 - by Test Author", context.subtitle)
        assertEquals("https://example.com/cover.jpg", context.posterUrl)
        assertEquals("chapter-5", context.contentId)

        // Verify metadata
        assertEquals("book-123", context.metadata["audiobookId"])
        assertEquals("5", context.metadata["chapterNumber"])
        assertEquals("2400000", context.metadata["startPositionMs"])
        assertEquals("3000000", context.metadata["endPositionMs"])
        assertEquals("Test Author", context.metadata["author"])
        assertEquals("Test Narrator", context.metadata["narrator"])
    }

    @Test
    fun `AudiobookChapter toPlaybackContext with filePath uses file URI`() {
        val audiobook =
            AudiobookItem(
                id = "book-123",
                title = "Test Book",
                author = "Test Author",
            )

        val chapter =
            AudiobookChapter(
                id = "chapter-1",
                audiobookId = "book-123",
                title = "Chapter One",
                chapterNumber = 1,
                startPositionMs = 0L,
                endPositionMs = 600000L,
                filePath = "/path/to/chapter1.mp3",
            )

        val context = chapter.toPlaybackContext(audiobook)

        assertEquals("/path/to/chapter1.mp3", context.uri)
        assertEquals("/path/to/chapter1.mp3", context.metadata["filePath"])
    }

    @Test
    fun `AudiobookChapter duration is calculated correctly`() {
        val chapter =
            AudiobookChapter(
                id = "chapter-test",
                audiobookId = "book-test",
                title = "Test Chapter",
                chapterNumber = 1,
                startPositionMs = 1000L,
                endPositionMs = 5000L,
            )

        assertEquals(4000L, chapter.durationMs)
    }

    @Test
    fun `AudiobookItem with custom metadata preserves values`() {
        val customMetadata =
            mapOf(
                "publisher" to "Test Publisher",
                "isbn" to "1234567890",
                "genre" to "Science Fiction",
            )

        val audiobook =
            AudiobookItem(
                id = "book-meta",
                title = "Metadata Book",
                author = "Meta Author",
                metadata = customMetadata,
            )

        val context = audiobook.toPlaybackContext()

        assertEquals("Test Publisher", context.metadata["publisher"])
        assertEquals("1234567890", context.metadata["isbn"])
        assertEquals("Science Fiction", context.metadata["genre"])
    }
}
