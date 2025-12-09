package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.model.TelegramMediaType
import com.fishit.player.pipeline.telegram.tdlib.TelegramAuthState
import com.fishit.player.pipeline.telegram.tdlib.TelegramChatInfo
import com.fishit.player.pipeline.telegram.tdlib.TelegramClient
import com.fishit.player.pipeline.telegram.tdlib.TelegramConnectionState
import com.fishit.player.pipeline.telegram.tdlib.TelegramFileLocation
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [TelegramCatalogPipelineImpl].
 *
 * Verifies:
 * - Event-based scanning flow
 * - Pre-flight auth/connection checks
 * - Pagination via cursor
 * - Error handling
 */
class TelegramCatalogPipelineTest {

    private lateinit var client: TelegramClient
    private lateinit var pipeline: TelegramCatalogPipelineImpl

    private val authStateFlow = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Ready)
    private val connectionStateFlow = MutableStateFlow<TelegramConnectionState>(
        TelegramConnectionState.Connected,
    )

    @Before
    fun setup() {
        client = mockk(relaxed = true)
        every { client.authState } returns authStateFlow
        every { client.connectionState } returns connectionStateFlow

        pipeline = TelegramCatalogPipelineImpl(client)
    }

    @Test
    fun `scanCatalog emits ScanError when not authenticated`() = runTest {
        authStateFlow.value = TelegramAuthState.WaitingForPhone

        val events = pipeline.scanCatalog(TelegramCatalogConfig.DEFAULT).toList()

        assertEquals(1, events.size)
        val error = events.first() as TelegramCatalogEvent.ScanError
        assertEquals("unauthenticated", error.reason)
    }

    @Test
    fun `scanCatalog emits ScanError when not connected`() = runTest {
        connectionStateFlow.value = TelegramConnectionState.Disconnected

        val events = pipeline.scanCatalog(TelegramCatalogConfig.DEFAULT).toList()

        assertEquals(1, events.size)
        val error = events.first() as TelegramCatalogEvent.ScanError
        assertEquals("not_connected", error.reason)
    }

    @Test
    fun `scanCatalog emits ScanStarted and ScanCompleted for empty chat list`() = runTest {
        coEvery { client.getChats(any()) } returns emptyList()

        val events = pipeline.scanCatalog(TelegramCatalogConfig.DEFAULT).toList()

        assertEquals(2, events.size)
        assertTrue(events[0] is TelegramCatalogEvent.ScanStarted)
        assertTrue(events[1] is TelegramCatalogEvent.ScanCompleted)

        val started = events[0] as TelegramCatalogEvent.ScanStarted
        assertEquals(0, started.chatCount)

        val completed = events[1] as TelegramCatalogEvent.ScanCompleted
        assertEquals(0, completed.scannedChats)
        assertEquals(0L, completed.scannedMessages)
    }

    @Test
    fun `scanCatalog discovers items and emits progress`() = runTest {
        val testChat = TelegramChatInfo(
            chatId = 123L,
            title = "Test Chat",
            type = "supergroup",
            photoPath = null,
        )
        val testMedia = createTestMediaItem(messageId = 1L, chatId = 123L)

        coEvery { client.getChats(any()) } returns listOf(testChat)
        coEvery { client.fetchMediaMessages(123L, any(), 0L) } returns listOf(testMedia)
        coEvery { client.fetchMediaMessages(123L, any(), 1L) } returns emptyList()

        val events = pipeline.scanCatalog(TelegramCatalogConfig.DEFAULT).toList()

        // Should have: ScanStarted, ItemDiscovered, ScanProgress, ScanCompleted
        assertTrue(events.any { it is TelegramCatalogEvent.ScanStarted })
        assertTrue(events.any { it is TelegramCatalogEvent.ItemDiscovered })
        assertTrue(events.any { it is TelegramCatalogEvent.ScanProgress })
        assertTrue(events.any { it is TelegramCatalogEvent.ScanCompleted })

        val item = events.filterIsInstance<TelegramCatalogEvent.ItemDiscovered>().first().item
        assertEquals(123L, item.chatId)
        assertEquals(1L, item.messageId)
        assertEquals("Test Video", item.raw.originalTitle)
        assertEquals(SourceType.TELEGRAM, item.raw.sourceType)
    }

    @Test
    fun `scanCatalog respects chatIds filter in config`() = runTest {
        val chat1 = TelegramChatInfo(chatId = 1L, title = "Chat 1", type = "private", photoPath = null)
        val chat2 = TelegramChatInfo(chatId = 2L, title = "Chat 2", type = "private", photoPath = null)
        val chat3 = TelegramChatInfo(chatId = 3L, title = "Chat 3", type = "private", photoPath = null)

        coEvery { client.getChats(any()) } returns listOf(chat1, chat2, chat3)
        coEvery { client.fetchMediaMessages(any(), any(), any()) } returns emptyList()

        val config = TelegramCatalogConfig(chatIds = listOf(1L, 3L))
        val events = pipeline.scanCatalog(config).toList()

        val started = events.filterIsInstance<TelegramCatalogEvent.ScanStarted>().first()
        assertEquals(2, started.chatCount) // Only chats 1 and 3
    }

    @Test
    fun `quickScan config creates correct filters`() {
        val config = TelegramCatalogConfig.quickScan(maxPerChat = 50, recentDays = 7)

        assertEquals(50L, config.maxMessagesPerChat)
        assertTrue(config.minMessageTimestampMs!! > 0)

        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        assertTrue(config.minMessageTimestampMs!! >= sevenDaysAgo - 1000) // Allow 1 second tolerance
        assertTrue(config.minMessageTimestampMs!! <= sevenDaysAgo + 1000)
    }

    // === Helper Functions ===

    private fun createTestMediaItem(
        messageId: Long,
        chatId: Long,
        title: String = "Test Video",
    ) = TelegramMediaItem(
        id = messageId,
        chatId = chatId,
        messageId = messageId,
        mediaType = TelegramMediaType.VIDEO,
        fileId = 100,
        fileUniqueId = "unique_$messageId",
        remoteId = "remote_$messageId",
        title = title,
        fileName = "test_video.mp4",
        caption = null,
        mimeType = "video/mp4",
        sizeBytes = 1024L,
        durationSecs = 120,
        width = 1920,
        height = 1080,
        date = System.currentTimeMillis() / 1000,
    )
}
