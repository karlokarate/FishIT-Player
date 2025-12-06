package com.fishit.player.pipeline.telegram

import com.fishit.player.pipeline.telegram.download.TelegramDownloadManager
import com.fishit.player.pipeline.telegram.download.TelegramDownloadManagerStub
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Unit tests for TelegramDownloadManagerStub.
 */
class TelegramDownloadManagerStubTest {
    private val manager = TelegramDownloadManagerStub()

    @Test
    fun `downloadFile returns false`() =
        runTest {
            val result = manager.downloadFile(fileId = 1001, priority = 0)
            assertFalse(result)
        }

    @Test
    fun `getDownloadStatus returns NOT_DOWNLOADED`() =
        runTest {
            val status = manager.getDownloadStatus(fileId = 1001)
            assertEquals(TelegramDownloadManager.DownloadStatus.NOT_DOWNLOADED, status)
        }

    @Test
    fun `getLocalPath returns null`() =
        runTest {
            val path = manager.getLocalPath(fileId = 1001)
            assertNull(path)
        }

    @Test
    fun `getTotalDownloadSize returns zero`() =
        runTest {
            val size = manager.getTotalDownloadSize()
            assertEquals(0L, size)
        }

    @Test
    fun `cancelDownload does not throw`() =
        runTest {
            // Should not throw exception
            manager.cancelDownload(fileId = 1001)
        }

    @Test
    fun `deleteDownload does not throw`() =
        runTest {
            // Should not throw exception
            manager.deleteDownload(fileId = 1001)
        }
}
