package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.pipeline.telegram.mapper.TdlibTestFixtures
import com.fishit.player.pipeline.telegram.tdlib.TelegramChatInfo
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for TelegramCatalogMessageMapperImpl.
 *
 * Validates that TDLib Message DTOs are correctly mapped to RawMediaMetadata
 * following the v2 normalization contract (NO title cleaning, raw field extraction).
 */
class TelegramCatalogMessageMapperTest {
    private val mapper = TelegramCatalogMessageMapperImpl()

    private val testChat =
        TelegramChatInfo(
            chatId = 123456789L,
            title = "Test Chat",
            type = "supergroup",
            photoPath = null,
        )

    // ========== classifyMediaKind Tests ==========

    @Test
    fun `classifyMediaKind - video message returns Video`() {
        val message = TdlibTestFixtures.createVideoMessage()

        val result = mapper.classifyMediaKind(message)
        assertEquals(MediaKind.Video, result)
    }

    @Test
    fun `classifyMediaKind - document with video mime returns Video`() {
        val message =
            TdlibTestFixtures.createDocumentMessage(
                mimeType = "video/x-matroska",
            )

        val result = mapper.classifyMediaKind(message)
        assertEquals(MediaKind.Video, result)
    }

    @Test
    fun `classifyMediaKind - audio message returns Audio`() {
        val message = TdlibTestFixtures.createAudioMessage()

        val result = mapper.classifyMediaKind(message)
        assertEquals(MediaKind.Audio, result)
    }

    @Test
    fun `classifyMediaKind - photo message returns Image`() {
        val message = TdlibTestFixtures.createPhotoMessage()

        val result = mapper.classifyMediaKind(message)
        assertEquals(MediaKind.Image, result)
    }

    // ========== toRawMediaMetadata Tests ==========

    @Test
    fun `toRawMediaMetadata - video message with caption`() {
        val message =
            TdlibTestFixtures.createVideoMessage(
                fileName = "Movie.2020.1080p.BluRay.x264-GROUP.mp4",
                caption = "Great movie!",
                duration = 7200, // 2 hours
                chatId = testChat.chatId,
            )

        val result = mapper.toRawMediaMetadata(message, testChat, MediaKind.Video)

        assertNotNull(result)
        assertEquals("Great movie!", result.originalTitle) // Caption takes precedence
        assertEquals(MediaType.CLIP, result.mediaType)
        assertEquals(120, result.durationMinutes) // 7200 seconds / 60 = 120 minutes
        assertEquals(SourceType.TELEGRAM, result.sourceType)
        assertEquals("Test Chat", result.sourceLabel)
        // sourceId format: tg:chatId:messageId:uniqueId
        assert(result.sourceId.startsWith("tg:${testChat.chatId}:"))
    }

    @Test
    fun `toRawMediaMetadata - video message without caption uses filename`() {
        val message =
            TdlibTestFixtures.createVideoMessage(
                fileName = "Series.S01E05.HDTV.x264.mkv",
                caption = "",
                duration = 2700, // 45 minutes
                chatId = testChat.chatId,
            )

        val result = mapper.toRawMediaMetadata(message, testChat, MediaKind.Video)

        assertNotNull(result)
        // Filename is used when caption is empty - NO cleaning per contract
        assertEquals("Series.S01E05.HDTV.x264.mkv", result.originalTitle)
        assertEquals(45, result.durationMinutes)
    }

    @Test
    fun `toRawMediaMetadata - audio message uses filename when no caption`() {
        val message =
            TdlibTestFixtures.createAudioMessage(
                fileName = "track.mp3",
                audioTitle = "Song Title",
                duration = 240, // 4 minutes
                chatId = testChat.chatId,
            )

        val result = mapper.toRawMediaMetadata(message, testChat, MediaKind.Audio)

        assertNotNull(result)
        // With no caption, title field is used
        assertEquals("Song Title", result.originalTitle)
        assertEquals(MediaType.CLIP, result.mediaType)
        assertEquals(4, result.durationMinutes)
    }

    @Test
    fun `toRawMediaMetadata - message with missing remote identifiers returns null`() {
        // Create video with empty remoteId
        val message =
            TdlibTestFixtures.createVideoMessage(
                remoteId = "",
                uniqueId = "",
            )

        val result = mapper.toRawMediaMetadata(message, testChat, MediaKind.Video)

        assertNull(result) // Should return null when remoteId/uniqueId missing
    }

    @Test
    fun `toRawMediaMetadata - sourceId format is stable and unique`() {
        val message =
            TdlibTestFixtures.createVideoMessage(
                messageId = 999L,
                chatId = 12345L,
                uniqueId = "unique123",
            )

        val chat =
            TelegramChatInfo(
                chatId = 12345L,
                title = "Chat",
                type = "group",
                photoPath = null,
            )

        val result = mapper.toRawMediaMetadata(message, chat, MediaKind.Video)

        assertNotNull(result)
        // sourceId format: tg:chatId:messageId:uniqueId
        val expectedPrefix = "tg:12345:999:"
        assert(result.sourceId.startsWith(expectedPrefix)) {
            "Expected sourceId to start with '$expectedPrefix', got '${result.sourceId}'"
        }
    }
}
