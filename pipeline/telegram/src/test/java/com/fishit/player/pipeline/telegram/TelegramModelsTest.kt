package com.fishit.player.pipeline.telegram

import com.fishit.player.pipeline.telegram.model.TelegramChat
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.model.TelegramMessage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Telegram domain models.
 *
 * Validates construction and basic properties.
 */
class TelegramModelsTest {
    @Test
    fun `TelegramChat can be constructed with minimal fields`() {
        val chat =
            TelegramChat(
                chatId = 123456789L,
                title = "Test Chat",
            )

        assertEquals(123456789L, chat.chatId)
        assertEquals("Test Chat", chat.title)
        assertEquals("unknown", chat.type)
    }

    @Test
    fun `TelegramChat can be constructed with all fields`() {
        val chat =
            TelegramChat(
                chatId = 123456789L,
                title = "Test Channel",
                type = "channel",
                username = "testchannel",
                photoUrl = "https://example.com/photo.jpg",
            )

        assertEquals(123456789L, chat.chatId)
        assertEquals("Test Channel", chat.title)
        assertEquals("channel", chat.type)
        assertEquals("testchannel", chat.username)
        assertEquals("https://example.com/photo.jpg", chat.photoUrl)
    }

    @Test
    fun `TelegramMessage can be constructed`() {
        val message =
            TelegramMessage(
                messageId = 42L,
                chatId = 123456789L,
                date = 1638360000L,
                caption = "Test message",
                hasMedia = true,
            )

        assertEquals(42L, message.messageId)
        assertEquals(123456789L, message.chatId)
        assertEquals(1638360000L, message.date)
        assertEquals("Test message", message.caption)
        assertTrue(message.hasMedia)
    }

    @Test
    fun `TelegramMediaItem can be constructed with minimal fields`() {
        val item =
            TelegramMediaItem(
                chatId = 123456789L,
                messageId = 42L,
            )

        assertEquals(123456789L, item.chatId)
        assertEquals(42L, item.messageId)
        assertFalse(item.isSeries)
    }

    @Test
    fun `TelegramMediaItem can be constructed with movie metadata`() {
        val item =
            TelegramMediaItem(
                chatId = 123456789L,
                messageId = 42L,
                fileId = 1001,
                title = "Test Movie",
                year = 2023,
                genres = "Action, Sci-Fi",
                description = "A test movie description",
                durationSecs = 7200,
                mimeType = "video/mp4",
                sizeBytes = 1024 * 1024 * 1024L,
            )

        assertEquals("Test Movie", item.title)
        assertEquals(2023, item.year)
        assertEquals("Action, Sci-Fi", item.genres)
        assertEquals(7200, item.durationSecs)
        assertEquals("video/mp4", item.mimeType)
        assertEquals(1024 * 1024 * 1024L, item.sizeBytes)
    }

    @Test
    fun `TelegramMediaItem can be constructed with series metadata`() {
        val item =
            TelegramMediaItem(
                chatId = 123456789L,
                messageId = 42L,
                fileId = 1001,
                isSeries = true,
                seriesName = "Test Series",
                seriesNameNormalized = "test-series",
                seasonNumber = 1,
                episodeNumber = 5,
                episodeTitle = "Episode 5",
            )

        assertTrue(item.isSeries)
        assertEquals("Test Series", item.seriesName)
        assertEquals("test-series", item.seriesNameNormalized)
        assertEquals(1, item.seasonNumber)
        assertEquals(5, item.episodeNumber)
        assertEquals("Episode 5", item.episodeTitle)
    }

    @Test
    fun `TelegramMediaItem supports all ObxTelegramMessage fields`() {
        val item =
            TelegramMediaItem(
                chatId = 123456789L,
                messageId = 42L,
                fileId = 1001,
                fileUniqueId = "unique-file-id",
                remoteId = "remote-id",
                supportsStreaming = true,
                caption = "Test caption",
                captionLower = "test caption",
                date = 1638360000L,
                localPath = "/storage/test.mp4",
                thumbFileId = 2001,
                thumbLocalPath = "/storage/thumb.jpg",
                fileName = "test-movie.mp4",
                durationSecs = 7200,
                mimeType = "video/mp4",
                sizeBytes = 1024 * 1024 * 1024L,
                width = 1920,
                height = 1080,
                language = "en",
                title = "Test Movie",
                year = 2023,
                genres = "Action",
                fsk = 16,
                description = "Description",
                posterFileId = 3001,
                posterLocalPath = "/storage/poster.jpg",
                isSeries = false,
                seriesName = null,
                seriesNameNormalized = null,
                seasonNumber = null,
                episodeNumber = null,
                episodeTitle = null,
            )

        // Verify all fields are accessible
        assertNotNull(item)
        assertEquals(1001, item.fileId)
        assertEquals("unique-file-id", item.fileUniqueId)
        assertEquals("remote-id", item.remoteId)
        assertEquals(true, item.supportsStreaming)
        assertEquals(1920, item.width)
        assertEquals(1080, item.height)
        assertEquals("en", item.language)
        assertEquals(16, item.fsk)
    }
}
