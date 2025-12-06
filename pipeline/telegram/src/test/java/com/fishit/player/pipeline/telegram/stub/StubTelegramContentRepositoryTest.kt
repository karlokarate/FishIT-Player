package com.fishit.player.pipeline.telegram.stub

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for StubTelegramContentRepository.
 *
 * Phase 2 Task 3 (P2-T3) - validates stub behavior returns empty/mock data.
 */
class StubTelegramContentRepositoryTest {
    @Test
    fun `empty stub returns empty list for all media items`() =
        runTest {
            val repository = StubTelegramContentRepository()

            val result = repository.getAllMediaItems()

            assertTrue(result.isEmpty())
        }

    @Test
    fun `empty stub returns empty list for media by chat`() =
        runTest {
            val repository = StubTelegramContentRepository()

            val result = repository.getMediaItemsByChat(chatId = 12345)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `empty stub returns empty list for recent media`() =
        runTest {
            val repository = StubTelegramContentRepository()

            val result = repository.getRecentMediaItems()

            assertTrue(result.isEmpty())
        }

    @Test
    fun `empty stub returns empty list for search`() =
        runTest {
            val repository = StubTelegramContentRepository()

            val result = repository.searchMediaItems("test query")

            assertTrue(result.isEmpty())
        }

    @Test
    fun `empty stub returns empty list for series`() =
        runTest {
            val repository = StubTelegramContentRepository()

            val result = repository.getSeriesMediaItems("Test Series")

            assertTrue(result.isEmpty())
        }

    @Test
    fun `empty stub returns null for media by id`() =
        runTest {
            val repository = StubTelegramContentRepository()

            val result = repository.getMediaItemById(123)

            assertNull(result)
        }

    @Test
    fun `empty stub returns empty list for all chats`() =
        runTest {
            val repository = StubTelegramContentRepository()

            val result = repository.getAllChats()

            assertTrue(result.isEmpty())
        }

    @Test
    fun `mock data stub returns deterministic media items`() =
        runTest {
            val repository = StubTelegramContentRepository.withMockData()

            val result = repository.getAllMediaItems()

            assertEquals(2, result.size)
            assertEquals("Mock Video 1", result[0].title)
            assertEquals("Mock Video 2", result[1].title)
        }

    @Test
    fun `mock data stub filters by chat id`() =
        runTest {
            val repository = StubTelegramContentRepository.withMockData()

            val result = repository.getMediaItemsByChat(chatId = 12345)

            assertEquals(2, result.size)
            assertTrue(result.all { it.chatId == 12345L })
        }

    @Test
    fun `mock data stub returns media by id`() =
        runTest {
            val repository = StubTelegramContentRepository.withMockData()

            val result = repository.getMediaItemById(1)

            assertEquals("Mock Video 1", result?.title)
            assertEquals(1001, result?.fileId)
        }

    @Test
    fun `mock data stub returns null for non-existent id`() =
        runTest {
            val repository = StubTelegramContentRepository.withMockData()

            val result = repository.getMediaItemById(999)

            assertNull(result)
        }

    @Test
    fun `mock data stub returns chat summaries`() =
        runTest {
            val repository = StubTelegramContentRepository.withMockData()

            val result = repository.getAllChats()

            assertEquals(1, result.size)
            assertEquals("Mock Chat", result[0].title)
            assertEquals(12345L, result[0].chatId)
            assertEquals(2, result[0].mediaCount)
        }

    @Test
    fun `mock data stub respects pagination limit`() =
        runTest {
            val repository = StubTelegramContentRepository.withMockData()

            val result = repository.getAllMediaItems(limit = 1)

            assertEquals(1, result.size)
        }

    @Test
    fun `mock data stub respects pagination offset`() =
        runTest {
            val repository = StubTelegramContentRepository.withMockData()

            val result = repository.getAllMediaItems(offset = 1)

            assertEquals(1, result.size)
            assertEquals("Mock Video 2", result[0].title)
        }

    @Test
    fun `refresh does not throw exception`() =
        runTest {
            val repository = StubTelegramContentRepository()

            // Should not throw
            repository.refresh()
        }
}
