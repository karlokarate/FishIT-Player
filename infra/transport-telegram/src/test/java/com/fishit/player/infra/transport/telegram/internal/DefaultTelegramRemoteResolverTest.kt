package com.fishit.player.infra.transport.telegram.internal

import com.fishit.player.infra.transport.telegram.ResolvedTelegramMedia
import com.fishit.player.infra.transport.telegram.TelegramRemoteId
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.Minithumbnail
import dev.g000sha256.tdl.dto.Thumbnail
import dev.g000sha256.tdl.dto.Video
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DefaultTelegramRemoteResolver.
 *
 * Verifies RemoteId-First resolution for media and thumbnails.
 */
class DefaultTelegramRemoteResolverTest {

    @Test
    fun `resolveMedia returns null when message not found`() = runTest {
        // Given: TdlClient that returns failure
        val client = mockk<TdlClient>()
        coEvery { client.getMessage(any(), any()) } returns TdlResult.Failure(404, "Not found")

        val resolver = DefaultTelegramRemoteResolver(client)

        // When: Resolve non-existent message
        val result = resolver.resolveMedia(TelegramRemoteId(chatId = 123L, messageId = 456L))

        // Then: Returns null
        assertNull(result)
    }

    @Test
    fun `resolveMedia returns null when message has no content`() = runTest {
        // Given: TdlClient that returns message without content
        val client = mockk<TdlClient>()
        val message = mockk<Message> {
            every { content } returns null
        }
        coEvery { client.getMessage(any(), any()) } returns TdlResult.Success(message)

        val resolver = DefaultTelegramRemoteResolver(client)

        // When: Resolve message without content
        val result = resolver.resolveMedia(TelegramRemoteId(chatId = 123L, messageId = 456L))

        // Then: Returns null
        assertNull(result)
    }

    @Test
    fun `resolveMedia extracts video file and thumbnail`() = runTest {
        // Given: TdlClient that returns video message
        val client = mockk<TdlClient>()
        
        val videoFile = mockk<File> {
            every { id } returns 789
            every { size } returns 1024L
            every { local } returns mockk<LocalFile> {
                every { isDownloadingCompleted } returns false
                every { path } returns ""
            }
        }
        
        val thumbFile = mockk<File> {
            every { id } returns 999
            every { local } returns mockk<LocalFile> {
                every { isDownloadingCompleted } returns false
                every { path } returns ""
            }
        }
        
        val thumbnail = mockk<Thumbnail> {
            every { file } returns thumbFile
        }
        
        val miniThumbData = byteArrayOf(1, 2, 3, 4)
        val miniThumbnail = mockk<Minithumbnail> {
            every { data } returns miniThumbData
        }
        
        val video = mockk<Video> {
            every { this@mockk.video } returns videoFile
            every { mimeType } returns "video/mp4"
            every { duration } returns 120
            every { width } returns 1920
            every { height } returns 1080
            every { supportsStreaming } returns true
            every { this@mockk.thumbnail } returns thumbnail
            every { minithumbnail } returns miniThumbnail
        }
        
        val messageContent = mockk<MessageVideo> {
            every { this@mockk.video } returns video
        }
        
        val message = mockk<Message> {
            every { content } returns messageContent
        }
        
        coEvery { client.getMessage(123L, 456L) } returns TdlResult.Success(message)

        val resolver = DefaultTelegramRemoteResolver(client)

        // When: Resolve video message
        val result = resolver.resolveMedia(TelegramRemoteId(chatId = 123L, messageId = 456L))

        // Then: Extracts correct file IDs and metadata
        assertNotNull(result)
        assertEquals(789, result!!.mediaFileId)
        assertEquals(999, result.thumbFileId)
        assertEquals("video/mp4", result.mimeType)
        assertEquals(120, result.durationSecs)
        assertEquals(1024L, result.sizeBytes)
        assertEquals(1920, result.width)
        assertEquals(1080, result.height)
        assertEquals(true, result.supportsStreaming)
        assertNotNull(result.minithumbnailBytes)
        assertEquals(4, result.minithumbnailBytes?.size)
    }
}

    @Test
    fun `ResolvedTelegramMedia equals handles null minithumbnailBytes`() {
        // Given: Two instances with null minithumbnailBytes
        val media1 = ResolvedTelegramMedia(
            mediaFileId = 123,
            thumbFileId = 456,
            mimeType = "video/mp4",
            minithumbnailBytes = null
        )
        val media2 = ResolvedTelegramMedia(
            mediaFileId = 123,
            thumbFileId = 456,
            mimeType = "video/mp4",
            minithumbnailBytes = null
        )

        // When: Compare
        val result = media1 == media2

        // Then: Should be equal and not throw NPE
        assertTrue(result)
    }

    @Test
    fun `ResolvedTelegramMedia equals handles one null minithumbnailBytes`() {
        // Given: One with null, one with bytes
        val media1 = ResolvedTelegramMedia(
            mediaFileId = 123,
            thumbFileId = 456,
            mimeType = "video/mp4",
            minithumbnailBytes = null
        )
        val media2 = ResolvedTelegramMedia(
            mediaFileId = 123,
            thumbFileId = 456,
            mimeType = "video/mp4",
            minithumbnailBytes = byteArrayOf(1, 2, 3)
        )

        // When: Compare
        val result = media1 == media2

        // Then: Should not be equal
        assertFalse(result)
    }

    @Test
    fun `ResolvedTelegramMedia equals handles both non-null minithumbnailBytes`() {
        // Given: Both with same bytes
        val bytes1 = byteArrayOf(1, 2, 3, 4)
        val bytes2 = byteArrayOf(1, 2, 3, 4)
        val media1 = ResolvedTelegramMedia(
            mediaFileId = 123,
            thumbFileId = 456,
            mimeType = "video/mp4",
            minithumbnailBytes = bytes1
        )
        val media2 = ResolvedTelegramMedia(
            mediaFileId = 123,
            thumbFileId = 456,
            mimeType = "video/mp4",
            minithumbnailBytes = bytes2
        )

        // When: Compare
        val result = media1 == media2

        // Then: Should be equal
        assertTrue(result)
    }
}
