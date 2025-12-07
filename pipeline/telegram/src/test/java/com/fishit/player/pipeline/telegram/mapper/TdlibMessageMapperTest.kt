package com.fishit.player.pipeline.telegram.mapper

import com.fishit.player.pipeline.telegram.model.TelegramMediaType
import dev.g000sha256.tdl.dto.Audio
import dev.g000sha256.tdl.dto.Document
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.Photo
import dev.g000sha256.tdl.dto.PhotoSize
import dev.g000sha256.tdl.dto.RemoteFile
import dev.g000sha256.tdl.dto.Video
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/**
 * Unit Tests für TdlibMessageMapper.
 *
 * Tests the mapping from TDLib Message DTOs to TelegramMediaItem. Uses MockK to create mock g00sha
 * DTOs.
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
        val message =
                createVideoMessage(
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
        val message = createVideoMessage(fileName = "Breaking.Bad.S01E01.720p.mkv")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        // CONTRACT: title MUST be empty for VIDEO - normalizer handles extraction
        assertEquals("", result.title)
        // Filename preserved RAW for normalizer
        assertEquals("Breaking.Bad.S01E01.720p.mkv", result.fileName)
    }

    @Test
    fun `returns null for video with blank remoteId`() {
        val message = createVideoMessage(remoteId = "", uniqueId = "valid_unique")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNull(result, "Should return null when remoteId is blank")
    }

    @Test
    fun `returns null for video with blank uniqueId`() {
        val message = createVideoMessage(remoteId = "valid_remote", uniqueId = "")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNull(result, "Should return null when uniqueId is blank")
    }

    // ========== DOCUMENT Message Tests ==========

    @Test
    fun `maps MessageDocument to TelegramMediaItem with DOCUMENT type`() {
        val message =
                createDocumentMessage(
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
        val message = createDocumentMessage(fileName = "The.Matrix.1999.REMASTERED.mkv")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals("", result.title)
    }

    // ========== AUDIO Message Tests ==========

    @Test
    fun `maps MessageAudio to TelegramMediaItem with AUDIO type`() {
        val message =
                createAudioMessage(
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
        val message = createAudioMessage(audioTitle = "Track Title from ID3")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        // CONTRACT: Audio CAN have title (from ID3 metadata in TDLib)
        assertEquals("Track Title from ID3", result.title)
    }

    // ========== PHOTO Message Tests ==========

    @Test
    fun `maps MessagePhoto to TelegramMediaItem with PHOTO type`() {
        val message =
                createPhotoMessage(
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
        val message = createPhotoMessage()

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals("", result.title)
    }

    @Test
    fun `photo returns largest size`() {
        val message =
                createPhotoMessageWithMultipleSizes(
                        sizes =
                                listOf(
                                        160 to 120, // thumbnail
                                        320 to 240, // small
                                        1280 to 960, // medium
                                        2560 to 1920 // large (should be selected)
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
        val messages =
                listOf(
                        createVideoMessage(messageId = 1),
                        createTextMessage(text = "Skip me"),
                        createDocumentMessage(messageId = 2),
                        createTextMessage(text = "Skip me too"),
                        createAudioMessage(messageId = 3)
                )

        val result = TdlibMessageMapper.toMediaItems(messages)

        assertEquals(3, result.size)
        assertEquals(listOf(1L, 2L, 3L), result.map { it.messageId })
    }

    @Test
    fun `extension function toTelegramMediaItems works`() {
        val messages =
                listOf(createVideoMessage(messageId = 100), createAudioMessage(messageId = 200))

        val result = messages.toTelegramMediaItems()

        assertEquals(2, result.size)
        assertEquals(100L, result[0].messageId)
        assertEquals(200L, result[1].messageId)
    }

    // ========== Edge Cases ==========

    @Test
    fun `handles null caption gracefully`() {
        val message = createVideoMessage(caption = null)

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertNull(result.caption)
    }

    @Test
    fun `handles blank caption as null`() {
        val message = createVideoMessage(caption = "   ")

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        // Blank string should become null after .takeIf { it.isNotBlank() }
        assertNull(result.caption)
    }

    @Test
    fun `mediaAlbumId preserved when non-zero`() {
        val message = createVideoMessage(mediaAlbumId = 123456789L)

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals(123456789L, result.mediaAlbumId)
    }

    @Test
    fun `mediaAlbumId null when zero`() {
        val message = createVideoMessage(mediaAlbumId = 0L)

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertNull(result.mediaAlbumId)
    }

    @Test
    fun `localPath populated when download completed`() {
        val message =
                createVideoMessage(
                        localPath = "/storage/telegram/videos/movie.mkv",
                        isDownloadCompleted = true
                )

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertEquals("/storage/telegram/videos/movie.mkv", result.localPath)
    }

    @Test
    fun `localPath null when download not completed`() {
        val message =
                createVideoMessage(
                        localPath = "/storage/telegram/videos/movie.mkv",
                        isDownloadCompleted = false
                )

        val result = TdlibMessageMapper.toMediaItem(message)

        assertNotNull(result)
        assertNull(result.localPath)
    }

    // ========== Helper Factory Methods ==========

    private fun createVideoMessage(
            messageId: Long = 1L,
            chatId: Long = 100L,
            fileName: String = "test.mp4",
            caption: String? = null,
            mimeType: String = "video/mp4",
            sizeBytes: Long = 1000000L,
            duration: Int = 3600,
            width: Int = 1920,
            height: Int = 1080,
            supportsStreaming: Boolean = true,
            remoteId: String = "remote_default",
            uniqueId: String = "unique_default",
            fileId: Int = 1,
            mediaAlbumId: Long = 0L,
            localPath: String? = null,
            isDownloadCompleted: Boolean = false
    ): Message {
        val localFile =
                mockk<LocalFile> {
                    every { path } returns (localPath ?: "")
                    every { isDownloadingCompleted } returns isDownloadCompleted
                }
        val remoteFile =
                mockk<RemoteFile> {
                    every { id } returns remoteId
                    every { uniqueId } returns uniqueId
                }
        val file =
                mockk<File> {
                    every { id } returns fileId
                    every { size } returns sizeBytes.toInt()
                    every { local } returns localFile
                    every { remote } returns remoteFile
                }
        val video =
                mockk<Video> {
                    every { this@mockk.video } returns file
                    every { this@mockk.fileName } returns fileName
                    every { this@mockk.mimeType } returns mimeType
                    every { this@mockk.duration } returns duration
                    every { this@mockk.width } returns width
                    every { this@mockk.height } returns height
                    every { this@mockk.supportsStreaming } returns supportsStreaming
                    every { thumbnail } returns null
                }
        val captionText = mockk<FormattedText> { every { text } returns (caption ?: "") }
        val content =
                mockk<MessageVideo> {
                    every { this@mockk.video } returns video
                    every { this@mockk.caption } returns captionText
                }
        val sender = mockk<MessageSenderUser> { every { userId } returns 123L }
        return mockk<Message> {
            every { id } returns messageId
            every { this@mockk.chatId } returns chatId
            every { this@mockk.content } returns content
            every { this@mockk.mediaAlbumId } returns mediaAlbumId
            every { date } returns 1704067200 // 2024-01-01
            every { senderId } returns sender
        }
    }

    private fun createDocumentMessage(
            messageId: Long = 1L,
            chatId: Long = 100L,
            fileName: String = "test.mkv",
            caption: String? = null,
            mimeType: String = "video/x-matroska",
            sizeBytes: Long = 2000000L,
            remoteId: String = "doc_remote_default",
            uniqueId: String = "doc_unique_default",
            fileId: Int = 2
    ): Message {
        val localFile =
                mockk<LocalFile> {
                    every { path } returns ""
                    every { isDownloadingCompleted } returns false
                }
        val remoteFile =
                mockk<RemoteFile> {
                    every { id } returns remoteId
                    every { uniqueId } returns uniqueId
                }
        val file =
                mockk<File> {
                    every { id } returns fileId
                    every { size } returns sizeBytes.toInt()
                    every { local } returns localFile
                    every { remote } returns remoteFile
                }
        val document =
                mockk<Document> {
                    every { this@mockk.document } returns file
                    every { this@mockk.fileName } returns fileName
                    every { this@mockk.mimeType } returns mimeType
                    every { thumbnail } returns null
                }
        val captionText = mockk<FormattedText> { every { text } returns (caption ?: "") }
        val content =
                mockk<MessageDocument> {
                    every { this@mockk.document } returns document
                    every { this@mockk.caption } returns captionText
                }
        val sender = mockk<MessageSenderUser> { every { userId } returns 123L }
        return mockk<Message> {
            every { id } returns messageId
            every { this@mockk.chatId } returns chatId
            every { this@mockk.content } returns content
            every { mediaAlbumId } returns 0L
            every { date } returns 1704067200
            every { senderId } returns sender
        }
    }

    private fun createAudioMessage(
            messageId: Long = 1L,
            chatId: Long = 100L,
            audioTitle: String = "",
            performer: String = "",
            fileName: String = "audio.mp3",
            mimeType: String = "audio/mpeg",
            sizeBytes: Long = 10000000L,
            duration: Int = 300,
            remoteId: String = "audio_remote_default",
            uniqueId: String = "audio_unique_default",
            fileId: Int = 3
    ): Message {
        val localFile =
                mockk<LocalFile> {
                    every { path } returns ""
                    every { isDownloadingCompleted } returns false
                }
        val remoteFile =
                mockk<RemoteFile> {
                    every { id } returns remoteId
                    every { uniqueId } returns uniqueId
                }
        val file =
                mockk<File> {
                    every { id } returns fileId
                    every { size } returns sizeBytes.toInt()
                    every { local } returns localFile
                    every { remote } returns remoteFile
                }
        val audio =
                mockk<Audio> {
                    every { this@mockk.audio } returns file
                    every { this@mockk.title } returns audioTitle
                    every { this@mockk.performer } returns performer
                    every { this@mockk.fileName } returns fileName
                    every { this@mockk.mimeType } returns mimeType
                    every { this@mockk.duration } returns duration
                    every { albumCoverThumbnail } returns null
                }
        val captionText = mockk<FormattedText> { every { text } returns "" }
        val content =
                mockk<MessageAudio> {
                    every { this@mockk.audio } returns audio
                    every { caption } returns captionText
                }
        val sender = mockk<MessageSenderUser> { every { userId } returns 123L }
        return mockk<Message> {
            every { id } returns messageId
            every { this@mockk.chatId } returns chatId
            every { this@mockk.content } returns content
            every { mediaAlbumId } returns 0L
            every { date } returns 1704067200
            every { senderId } returns sender
        }
    }

    private fun createPhotoMessage(
            messageId: Long = 1L,
            chatId: Long = 100L,
            caption: String? = null,
            largestWidth: Int = 1920,
            largestHeight: Int = 1080,
            sizeBytes: Long = 3000000L,
            remoteId: String = "photo_remote_default",
            uniqueId: String = "photo_unique_default",
            fileId: Int = 4
    ): Message {
        val localFile =
                mockk<LocalFile> {
                    every { path } returns ""
                    every { isDownloadingCompleted } returns false
                }
        val remoteFile =
                mockk<RemoteFile> {
                    every { id } returns remoteId
                    every { uniqueId } returns uniqueId
                }
        val file =
                mockk<File> {
                    every { id } returns fileId
                    every { size } returns sizeBytes.toInt()
                    every { local } returns localFile
                    every { remote } returns remoteFile
                }
        val photoSize =
                mockk<PhotoSize> {
                    every { width } returns largestWidth
                    every { height } returns largestHeight
                    every { photo } returns file
                }
        val photo = mockk<Photo> { every { sizes } returns arrayOf(photoSize) }
        val captionText = mockk<FormattedText> { every { text } returns (caption ?: "") }
        val content =
                mockk<MessagePhoto> {
                    every { this@mockk.photo } returns photo
                    every { this@mockk.caption } returns captionText
                }
        val sender = mockk<MessageSenderUser> { every { userId } returns 123L }
        return mockk<Message> {
            every { id } returns messageId
            every { this@mockk.chatId } returns chatId
            every { this@mockk.content } returns content
            every { mediaAlbumId } returns 0L
            every { date } returns 1704067200
            every { senderId } returns sender
        }
    }

    private fun createPhotoMessageWithMultipleSizes(
            messageId: Long = 1L,
            chatId: Long = 100L,
            sizes: List<Pair<Int, Int>>,
            remoteId: String = "photo_multi_remote",
            uniqueId: String = "photo_multi_unique"
    ): Message {
        val localFile =
                mockk<LocalFile> {
                    every { path } returns ""
                    every { isDownloadingCompleted } returns false
                }
        val remoteFile =
                mockk<RemoteFile> {
                    every { id } returns remoteId
                    every { uniqueId } returns uniqueId
                }
        val photoSizes =
                sizes
                        .mapIndexed { index, (w, h) ->
                            val file =
                                    mockk<File> {
                                        every { id } returns (100 + index)
                                        every { size } returns (w * h * 3) // rough size estimate
                                        every { local } returns localFile
                                        every { remote } returns remoteFile
                                    }
                            mockk<PhotoSize> {
                                every { width } returns w
                                every { height } returns h
                                every { photo } returns file
                            }
                        }
                        .toTypedArray()

        val photo = mockk<Photo> { every { this@mockk.sizes } returns photoSizes }
        val captionText = mockk<FormattedText> { every { text } returns "" }
        val content =
                mockk<MessagePhoto> {
                    every { this@mockk.photo } returns photo
                    every { caption } returns captionText
                }
        val sender = mockk<MessageSenderUser> { every { userId } returns 123L }
        return mockk<Message> {
            every { id } returns messageId
            every { this@mockk.chatId } returns chatId
            every { this@mockk.content } returns content
            every { mediaAlbumId } returns 0L
            every { date } returns 1704067200
            every { senderId } returns sender
        }
    }

    private fun createTextMessage(text: String): Message {
        val formattedText = mockk<FormattedText> { every { this@mockk.text } returns text }
        val content = mockk<MessageText> { every { this@mockk.text } returns formattedText }
        val sender = mockk<MessageSenderUser> { every { userId } returns 123L }
        return mockk<Message> {
            every { id } returns 1L
            every { chatId } returns 100L
            every { this@mockk.content } returns content
            every { mediaAlbumId } returns 0L
            every { date } returns 1704067200
            every { senderId } returns sender
        }
    }
}
