package com.chris.m3usuite.telegram.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * Tests for ExportMessage loading and conversion.
 */
class ExportMessageLoaderTest {
    @Test
    fun `loadMessages returns list of typed messages`() {
        // Skip if fixtures not available (CI environments may not have them)
        val files = ExportFixtures.listExportFiles()
        Assume.assumeTrue("Export fixtures not available", files.isNotEmpty())

        val file = files.first()
        val messages = ExportFixtures.loadMessagesFromFile(file)

        assertTrue("Should load messages from fixture", messages.isNotEmpty())
    }

    @Test
    fun `toExportMessage converts text messages correctly`() {
        val raw =
            RawExportMessage(
                id = 123L,
                chatId = -1001000L,
                date = 1700000000L,
                dateIso = "2023-11-14T22:13:20Z",
                text = "Test message",
                title = "Test Title",
                year = 2023,
                genres = listOf("Action", "Drama"),
            )

        val message = raw.toExportMessage()

        assertTrue("Should be ExportText", message is ExportText)
        val text = message as ExportText
        assertEquals("Test message", text.text)
        assertEquals("Test Title", text.title)
        assertEquals(2023, text.year)
        assertEquals(listOf("Action", "Drama"), text.genres)
    }

    @Test
    fun `toExportMessage converts video messages correctly`() {
        val videoContent =
            ExportVideoContent(
                duration = 5400,
                width = 1920,
                height = 1080,
                fileName = "Movie.2023.mp4",
                mimeType = "video/mp4",
                video =
                    ExportFile(
                        id = 100,
                        size = 1000000000L,
                        remote =
                            ExportRemoteFile(
                                id = "remote_123",
                                uniqueId = "unique_abc",
                            ),
                    ),
            )

        val raw =
            RawExportMessage(
                id = 456L,
                chatId = -1001000L,
                date = 1700000000L,
                content =
                    ExportMessageContent(
                        video = videoContent,
                        caption = ExportFormattedText(text = "Test caption"),
                    ),
            )

        val message = raw.toExportMessage()

        assertTrue("Should be ExportVideo", message is ExportVideo)
        val video = message as ExportVideo
        assertEquals(5400, video.video.duration)
        assertEquals(1920, video.video.width)
        assertEquals(1080, video.video.height)
        assertEquals("Test caption", video.caption)
    }

    @Test
    fun `toExportMessage falls back to OtherRaw for unknown content`() {
        val raw =
            RawExportMessage(
                id = 789L,
                chatId = -1001000L,
                date = 1700000000L,
                content = ExportMessageContent(),
            )

        val message = raw.toExportMessage()

        assertTrue("Should be ExportOtherRaw", message is ExportOtherRaw)
    }

    @Test
    fun `ChatExport deserialization works with real fixture`() {
        val files = ExportFixtures.listExportFiles()
        Assume.assumeTrue("Export fixtures not available", files.isNotEmpty())

        val export = ExportFixtures.loadChatExportFromFile(files.first())

        assertNotNull("Should load chat export", export)
        assertTrue("Should have chatId", export!!.chatId != 0L)
        assertTrue("Should have messages", export.messages.isNotEmpty())
    }

    @Test
    fun `getFixtureStats reports correct counts`() {
        val files = ExportFixtures.listExportFiles()
        Assume.assumeTrue("Export fixtures not available", files.isNotEmpty())

        val stats = ExportFixtures.getFixtureStats()

        assertTrue("Should have files", stats.fileCount > 0)
        assertTrue("Should have messages", stats.totalMessages > 0)
        // Text messages are common in exports
        assertTrue("Should have text messages", stats.textCount > 0)
    }
}
