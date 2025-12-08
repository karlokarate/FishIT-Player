package com.fishit.player.pipeline.telegram.mapper

import com.fishit.player.pipeline.telegram.model.TelegramMediaType
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/**
 * Unit Tests für TdlibMessageMapper.
 *
 * Tests the mapping from TDLib Message DTOs to TelegramMediaItem.
 * Uses TdlibTestFixtures to create REAL g000sha256 DTO instances (not mocks).
 *
 * **CONTRACT COMPLIANCE (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - VIDEO/DOCUMENT/PHOTO: title MUST be empty (normalizer extracts from filename)
 * - AUDIO: title = raw TDLib audio.title (often has ID3 metadata)
 * - All data RAW, no cleaning/normalization
 */
class TdlibMessageMapperTest {

    // ========== VIDEO Message Tests ==========

    @Test
    fun `maps MessageVideo to TelegramMediaItem with VIDEO type`() {
        val message = TdlibTestFixtures.createVideoMessage(
            messageId = 12345L,
            chatId = 67890L,
            fileName = "Movie.2024.1080p.BluRay.x264.mkv",
            caption = "Great movie!",
            mimeType = "video/x-matroska",
            sizeBytes = 1500000000L,
            duration = 7200,
            width = 1920,
            height = 1080,
            supportsStreaming = true,
            remoteId = "remote_file_123",
            uniqueId = "unique_abc",
            fileId = 999
        )

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals(TelegramMediaType.VIDEO, result.mediaType)
        assertEquals(12345L, result.messageId)
        assertEquals(67890L, result.chatId)
        assertEquals("Movie.2024.1080p.BluRay.x264.mkv", result.fileName)
        assertEquals("Great movie!", result.caption)
        assertEquals("video/x-matroska", result.mimeType)
        assertEquals(1500000000L, result.sizeBytes)
        assertEquals(7200, result.durationSecs)
        assertEquals(1920, result.width)
        assertEquals(1080, result.height)
        assertEquals(true, result.supportsStreaming)
        assertEquals("remote_file_123", result.remoteId)
        assertEquals("unique_abc", result.fileUniqueId)
        assertEquals(999, result.fileId)
    }

    @Test
    fun `VIDEO title is empty per contract - normalizer extracts`() {
        val message = TdlibTestFixtures.createVideoMessage(fileName = "Breaking.Bad.S01E01.720p.mkv")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        // CONTRACT: title MUST be empty for VIDEO - normalizer handles extraction
        assertEquals("", result.title)
        // Filename preserved RAW for normalizer
        assertEquals("Breaking.Bad.S01E01.720p.mkv", result.fileName)
    }

    @Test
    fun `returns null for video with blank remoteId`() {
        val message = TdlibTestFixtures.createVideoMessage(remoteId = "", uniqueId = "valid_unique")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNull(result, "Should return null when remoteId is blank")
    }

    @Test
    fun `returns null for video with blank uniqueId`() {
        val message = TdlibTestFixtures.createVideoMessage(remoteId = "valid_remote", uniqueId = "")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNull(result, "Should return null when uniqueId is blank")
    }

    // ========== DOCUMENT Message Tests ==========

    @Test
    fun `maps MessageDocument to TelegramMediaItem with DOCUMENT type`() {
        val message = TdlibTestFixtures.createDocumentMessage(
            messageId = 54321L,
            chatId = 11111L,
            fileName = "Game.of.Thrones.S01E01.1080p.mkv",
            caption = "Episode 1",
            mimeType = "video/x-matroska",
            sizeBytes = 2500000000L,
            remoteId = "doc_remote_456",
            uniqueId = "doc_unique_xyz"
        )

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals(TelegramMediaType.DOCUMENT, result.mediaType)
        assertEquals(54321L, result.messageId)
        assertEquals(11111L, result.chatId)
        assertEquals("Game.of.Thrones.S01E01.1080p.mkv", result.fileName)
        assertEquals("Episode 1", result.caption)
        assertEquals("video/x-matroska", result.mimeType)
        assertEquals(2500000000L, result.sizeBytes)
        assertEquals("doc_remote_456", result.remoteId)
        assertEquals("doc_unique_xyz", result.fileUniqueId)
        // Documents don't have duration/width/height
        assertNull(result.durationSecs)
        assertNull(result.width)
        assertNull(result.height)
    }

    @Test
    fun `DOCUMENT title is empty per contract`() {
        val message = TdlibTestFixtures.createDocumentMessage(fileName = "The.Matrix.1999.REMASTERED.mkv")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals("", result.title)
    }

    // ========== AUDIO Message Tests ==========

    @Test
    fun `maps MessageAudio to TelegramMediaItem with AUDIO type`() {
        val message = TdlibTestFixtures.createAudioMessage(
            messageId = 77777L,
            chatId = 88888L,
            audioTitle = "Bohemian Rhapsody",
            performer = "Queen",
            fileName = "bohemian_rhapsody.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = 12000000L,
            duration = 355,
            remoteId = "audio_remote_789",
            uniqueId = "audio_unique_qrs"
        )

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals(TelegramMediaType.AUDIO, result.mediaType)
        assertEquals(77777L, result.messageId)
        assertEquals(88888L, result.chatId)
        assertEquals("bohemian_rhapsody.mp3", result.fileName)
        assertEquals("audio/mpeg", result.mimeType)
        assertEquals(12000000L, result.sizeBytes)
        assertEquals(355, result.durationSecs)
        assertEquals("audio_remote_789", result.remoteId)
        assertEquals("audio_unique_qrs", result.fileUniqueId)
    }

    @Test
    fun `AUDIO title uses TDLib audio title - has ID3 metadata`() {
        val message = TdlibTestFixtures.createAudioMessage(audioTitle = "Track Title from ID3")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        // CONTRACT: Audio CAN have title (from ID3 metadata in TDLib)
        assertEquals("Track Title from ID3", result.title)
    }

    // ========== PHOTO Message Tests ==========

    @Test
    fun `maps MessagePhoto to TelegramMediaItem with PHOTO type`() {
        val message = TdlibTestFixtures.createPhotoMessage(
            messageId = 99999L,
            chatId = 11112L,
            caption = "Beautiful sunset",
            largestWidth = 2048,
            largestHeight = 1536,
            sizeBytes = 5000000L,
            remoteId = "photo_remote_abc",
            uniqueId = "photo_unique_def"
        )

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals(TelegramMediaType.PHOTO, result.mediaType)
        assertEquals(99999L, result.messageId)
        assertEquals(11112L, result.chatId)
        assertEquals("Beautiful sunset", result.caption)
        assertEquals(2048, result.width)
        assertEquals(1536, result.height)
        assertEquals(5000000L, result.sizeBytes)
        assertEquals("image/jpeg", result.mimeType) // Photos are always JPEG
        assertEquals("photo_remote_abc", result.remoteId)
        assertEquals("photo_unique_def", result.fileUniqueId)
        // Photos don't have filenames
        assertNull(result.fileName)
    }

    @Test
    fun `PHOTO title is empty per contract`() {
        val message = TdlibTestFixtures.createPhotoMessage()

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals("", result.title)
    }

    @Test
    fun `photo returns largest size`() {
        val message = TdlibTestFixtures.createPhotoMessageWithMultipleSizes(
            sizes = listOf(
                Triple(160, 120, 10000L),     // thumbnail
                Triple(320, 240, 30000L),     // small
                Triple(1280, 960, 150000L),   // medium
                Triple(2560, 1920, 500000L)   // large (should be selected)
            ),
            remoteId = "photo_multi",
            uniqueId = "photo_multi_unique"
        )

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals(2560, result.width)
        assertEquals(1920, result.height)
    }

    // ========== Non-Media Message Tests ==========

    @Test
    fun `returns null for text message`() {
        val message = createTextMessage(text = "Just a text message")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNull(result, "Text messages should not produce media items")
    }

    // ========== List Conversion Tests ==========

    @Test
    fun `toMediaItems filters non-media messages`() {
        val messages = listOf(
            TdlibTestFixtures.createVideoMessage(messageId = 1),
            createTextMessage(text = "Skip me"),
            TdlibTestFixtures.createDocumentMessage(messageId = 2),
            createTextMessage(text = "Skip me too"),
            TdlibTestFixtures.createAudioMessage(messageId = 3)
        )

        val result = TdlibMessageMapper.toMediaItems(messages)

        assertEquals(3, result.size)
        assertEquals(listOf(1L, 2L, 3L), result.map { it.messageId })
    }

    @Test
    fun `extension function toTelegramMediaItems works`() {
        val messages = listOf(
            TdlibTestFixtures.createVideoMessage(messageId = 100),
            TdlibTestFixtures.createAudioMessage(messageId = 200)
        )

        val result = messages.toTelegramMediaItems()

        assertEquals(2, result.size)
        assertEquals(100L, result[0].messageId)
        assertEquals(200L, result[1].messageId)
    }

    // ========== Edge Cases ==========

    @Test
    fun `handles empty caption gracefully`() {
        val message = TdlibTestFixtures.createVideoMessage(caption = "")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        // Empty caption should become null after processing
        assertNull(result.caption)
    }

    @Test
    fun `handles blank caption as null`() {
        val message = TdlibTestFixtures.createVideoMessage(caption = "   ")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        // Blank string should become null after .takeIf { it.isNotBlank() }
        assertNull(result.caption)
    }

    @Test
    fun `mediaAlbumId preserved when non-zero`() {
        val message = TdlibTestFixtures.createVideoMessage(mediaAlbumId = 123456789L)

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals(123456789L, result.mediaAlbumId)
    }

    @Test
    fun `mediaAlbumId null when zero`() {
        val message = TdlibTestFixtures.createVideoMessage(mediaAlbumId = 0L)

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertNull(result.mediaAlbumId)
    }

    @Test
    fun `localPath populated when download completed`() {
        val message = TdlibTestFixtures.createVideoMessage(
            localPath = "/storage/telegram/videos/movie.mkv",
            isDownloadCompleted = true
        )

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals("/storage/telegram/videos/movie.mkv", result.localPath)
    }

    @Test
    fun `localPath null when download not completed`() {
        val message = TdlibTestFixtures.createVideoMessage(
            localPath = "/storage/telegram/videos/movie.mkv",
            isDownloadCompleted = false
        )

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertNull(result.localPath)
    }

    // ========== Helper for MessageText (uses real DTOs) ==========

    private fun createTextMessage(text: String): Message {
        val formattedText = TdlibTestFixtures.createFormattedText(text)
        val content = MessageText(formattedText, null, null)
        return TdlibTestFixtures.createMessage(
            id = 1L,
            chatId = 100L,
            content = content
        )
    }
}
