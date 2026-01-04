package com.fishit.player.infra.transport.telegram.imaging

import com.fishit.player.infra.transport.telegram.ResolvedTelegramMedia
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TelegramRemoteId
import com.fishit.player.infra.transport.telegram.TelegramRemoteResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for TelegramThumbDownloader.
 *
 * Verifies thumbnail download strategy using TDLib cache only.
 */
class TelegramThumbDownloaderTest {
    @Test
    fun `ensureThumbDownloaded returns path when already cached`() =
        runTest {
            // Given: Resolver returns media with thumbLocalPath
            val resolver = mockk<TelegramRemoteResolver>()
            val fileClient = mockk<TelegramFileClient>()
            val resolved =
                ResolvedTelegramMedia(
                    mediaFileId = 123,
                    thumbFileId = 456,
                    thumbLocalPath = "/path/to/thumb.jpg",
                )
            coEvery { resolver.resolveMedia(any()) } returns resolved

            val downloader = TelegramThumbDownloader(resolver, fileClient)

            // When: Ensure download
            val remoteId = TelegramRemoteId(chatId = 1L, messageId = 2L)
            val result = downloader.ensureThumbDownloaded(remoteId)

            // Then: Returns cached path, no download triggered
            assertEquals("/path/to/thumb.jpg", result)
            coVerify(exactly = 0) { fileClient.startDownload(any(), any(), any(), any()) }
        }

    @Test
    fun `ensureThumbDownloaded triggers download when not cached`() =
        runTest {
            // Given: Resolver returns media with thumbFileId but no local path
            val resolver = mockk<TelegramRemoteResolver>()
            val fileClient = mockk<TelegramFileClient>(relaxed = true)
            val resolved =
                ResolvedTelegramMedia(
                    mediaFileId = 123,
                    thumbFileId = 456,
                    thumbLocalPath = null,
                )
            coEvery { resolver.resolveMedia(any()) } returns resolved

            val downloader = TelegramThumbDownloader(resolver, fileClient)

            // When: Ensure download
            val remoteId = TelegramRemoteId(chatId = 1L, messageId = 2L)
            val result = downloader.ensureThumbDownloaded(remoteId)

            // Then: Returns null (download in progress), triggers LOW priority download
            assertNull(result)
            coVerify {
                fileClient.startDownload(
                    fileId = 456,
                    priority = 8, // LOW priority
                    offset = 0,
                    limit = 0,
                )
            }
        }

    @Test
    fun `ensureThumbDownloaded returns null when no thumbFileId`() =
        runTest {
            // Given: Resolver returns media without thumbFileId
            val resolver = mockk<TelegramRemoteResolver>()
            val fileClient = mockk<TelegramFileClient>()
            val resolved =
                ResolvedTelegramMedia(
                    mediaFileId = 123,
                    thumbFileId = null,
                    thumbLocalPath = null,
                )
            coEvery { resolver.resolveMedia(any()) } returns resolved

            val downloader = TelegramThumbDownloader(resolver, fileClient)

            // When: Ensure download
            val remoteId = TelegramRemoteId(chatId = 1L, messageId = 2L)
            val result = downloader.ensureThumbDownloaded(remoteId)

            // Then: Returns null, no download triggered
            assertNull(result)
            coVerify(exactly = 0) { fileClient.startDownload(any(), any(), any(), any()) }
        }
}
