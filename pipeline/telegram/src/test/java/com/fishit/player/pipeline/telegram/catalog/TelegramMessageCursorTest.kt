package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.model.TelegramMediaType
import com.fishit.player.pipeline.telegram.tdlib.TelegramChatInfo
import com.fishit.player.pipeline.telegram.tdlib.TelegramClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [TelegramMessageCursor].
 *
 * Verifies:
 * - Pagination mechanics (fromMessageId cursor)
 * - Quota enforcement (maxMessages)
 * - Timestamp filtering (minMessageTimestampMs)
 * - End-of-history detection
 */
class TelegramMessageCursorTest {

    private lateinit var client: TelegramClient
    private val testChat = TelegramChatInfo(
        chatId = 123L,
        title = "Test Chat",
        type = "supergroup",
        photoPath = null,
    )

    @Before
    fun setup() {
        client = mockk(relaxed = true)
    }

    @Test
    fun `cursor returns empty list when no messages`() = runTest {
        coEvery { client.fetchMediaMessages(any(), any(), any()) } returns emptyList()

        val cursor = TelegramMessageCursor(
            client = client,
            chat = testChat,
            maxMessages = null,
            minMessageTimestampMs = null,
        )

        val batch = cursor.nextBatch()
        assertTrue(batch.isEmpty())
        assertFalse(cursor.hasNext())
    }

    @Test
    fun `cursor fetches multiple pages`() = runTest {
        val page1 = listOf(
            createMediaItem(messageId = 10),
            createMediaItem(messageId = 9),
        )
        val page2 = listOf(
            createMediaItem(messageId = 8),
            createMediaItem(messageId = 7),
        )

        coEvery { client.fetchMediaMessages(123L, any(), 0L) } returns page1
        coEvery { client.fetchMediaMessages(123L, any(), 9L) } returns page2
        coEvery { client.fetchMediaMessages(123L, any(), 7L) } returns emptyList()

        val cursor = TelegramMessageCursor(
            client = client,
            chat = testChat,
            maxMessages = null,
            minMessageTimestampMs = null,
            pageSize = 2,
        )

        val batch1 = cursor.nextBatch()
        assertEquals(2, batch1.size)
        assertEquals(10L, batch1[0].messageId)
        assertTrue(cursor.hasNext())

        val batch2 = cursor.nextBatch()
        assertEquals(2, batch2.size)
        assertEquals(8L, batch2[0].messageId)
        assertTrue(cursor.hasNext())

        val batch3 = cursor.nextBatch()
        assertTrue(batch3.isEmpty())
        assertFalse(cursor.hasNext())
    }

    @Test
    fun `cursor respects maxMessages quota`() = runTest {
        val page = listOf(
            createMediaItem(messageId = 10),
            createMediaItem(messageId = 9),
        )

        // When maxMessages=2 and pageSize=10, the cursor calculates min(quota, pageSize) = 2
        coEvery { client.fetchMediaMessages(123L, any(), 0L) } returns page

        val cursor = TelegramMessageCursor(
            client = client,
            chat = testChat,
            maxMessages = 2,
            minMessageTimestampMs = null,
            pageSize = 10,
        )

        val batch = cursor.nextBatch()
        assertEquals(2, batch.size)
        assertEquals(2L, cursor.scannedCount())

        // After reaching quota, should report no more
        assertFalse(cursor.hasNext())
    }

    @Test
    fun `cursor filters by timestamp`() = runTest {
        val now = System.currentTimeMillis()
        val twoDaysAgo = (now - 2 * 24 * 60 * 60 * 1000L) / 1000
        val fiveDaysAgo = (now - 5 * 24 * 60 * 60 * 1000L) / 1000

        val page = listOf(
            createMediaItem(messageId = 10, date = twoDaysAgo), // Recent - should pass
            createMediaItem(messageId = 9, date = fiveDaysAgo), // Old - should be filtered
        )

        coEvery { client.fetchMediaMessages(123L, any(), 0L) } returns page
        coEvery { client.fetchMediaMessages(123L, any(), 9L) } returns emptyList()

        val threeDaysAgoMs = now - 3 * 24 * 60 * 60 * 1000L

        val cursor = TelegramMessageCursor(
            client = client,
            chat = testChat,
            maxMessages = null,
            minMessageTimestampMs = threeDaysAgoMs,
        )

        val batch = cursor.nextBatch()
        assertEquals(1, batch.size)
        assertEquals(10L, batch[0].messageId) // Only the recent one passes
    }

    @Test
    fun `cursor tracks scanned count correctly`() = runTest {
        val page = listOf(
            createMediaItem(messageId = 10),
            createMediaItem(messageId = 9),
            createMediaItem(messageId = 8),
        )

        coEvery { client.fetchMediaMessages(123L, any(), 0L) } returns page
        coEvery { client.fetchMediaMessages(123L, any(), 8L) } returns emptyList()

        val cursor = TelegramMessageCursor(
            client = client,
            chat = testChat,
            maxMessages = null,
            minMessageTimestampMs = null,
        )

        cursor.nextBatch()
        assertEquals(3L, cursor.scannedCount())

        cursor.nextBatch() // Empty
        assertEquals(3L, cursor.scannedCount()) // Unchanged
    }

    // === Helper Functions ===

    private fun createMediaItem(
        messageId: Long,
        date: Long = System.currentTimeMillis() / 1000,
    ) = TelegramMediaItem(
        id = messageId,
        chatId = 123L,
        messageId = messageId,
        mediaType = TelegramMediaType.VIDEO,
        fileId = 100,
        fileUniqueId = "unique_$messageId",
        remoteId = "remote_$messageId",
        title = "Video $messageId",
        fileName = "video_$messageId.mp4",
        mimeType = "video/mp4",
        sizeBytes = 1024L,
        durationSecs = 60,
        date = date,
    )
}
