package com.fishit.player.pipeline.telegram

import com.fishit.player.pipeline.telegram.repository.TelegramContentRepositoryStub
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for TelegramContentRepositoryStub.
 *
 * Validates that stub returns deterministic empty results.
 */
class TelegramContentRepositoryStubTest {
    private val repository = TelegramContentRepositoryStub()

    @Test
    fun `getChatsWithMedia returns empty list`() =
        runTest {
            val chats = repository.getChatsWithMedia().first()
            assertTrue(chats.isEmpty())
        }

    @Test
    fun `getMessagesFromChat returns empty list`() =
        runTest {
            val messages =
                repository
                    .getMessagesFromChat(
                        chatId = 123456789L,
                        offset = 0,
                        limit = 50,
                    ).first()
            assertTrue(messages.isEmpty())
        }

    @Test
    fun `getMediaFromChat returns empty list`() =
        runTest {
            val media =
                repository
                    .getMediaFromChat(
                        chatId = 123456789L,
                        offset = 0,
                        limit = 50,
                    ).first()
            assertTrue(media.isEmpty())
        }

    @Test
    fun `getAllMedia returns empty list`() =
        runTest {
            val media =
                repository
                    .getAllMedia(
                        offset = 0,
                        limit = 50,
                    ).first()
            assertTrue(media.isEmpty())
        }

    @Test
    fun `searchMedia returns empty list`() =
        runTest {
            val results =
                repository
                    .searchMedia(
                        query = "test",
                        offset = 0,
                        limit = 50,
                    ).first()
            assertTrue(results.isEmpty())
        }

    @Test
    fun `getSeriesMedia returns empty list`() =
        runTest {
            val episodes = repository.getSeriesMedia("test-series").first()
            assertTrue(episodes.isEmpty())
        }

    @Test
    fun `getMediaItem returns null`() =
        runTest {
            val item =
                repository.getMediaItem(
                    chatId = 123456789L,
                    messageId = 42L,
                )
            assertNull(item)
        }

    @Test
    fun `repository handles multiple calls consistently`() =
        runTest {
            // Call multiple times to ensure deterministic behavior
            val result1 = repository.getAllMedia().first()
            val result2 = repository.getAllMedia().first()
            val result3 = repository.getAllMedia().first()

            assertEquals(result1, result2)
            assertEquals(result2, result3)
            assertTrue(result1.isEmpty())
        }
}
