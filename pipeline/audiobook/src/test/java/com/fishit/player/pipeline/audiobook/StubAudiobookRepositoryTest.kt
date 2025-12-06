package com.fishit.player.pipeline.audiobook

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for StubAudiobookRepository.
 *
 * Verifies that stub implementation behaves predictably and returns deterministic
 * empty/null results as specified in Phase 2 requirements.
 */
class StubAudiobookRepositoryTest {
    private lateinit var repository: AudiobookRepository

    @Before
    fun setup() {
        repository = StubAudiobookRepository()
    }

    @Test
    fun `getAllAudiobooks returns empty list`() =
        runTest {
            val result = repository.getAllAudiobooks()

            assertNotNull(result)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `getAudiobookById returns null`() =
        runTest {
            val result = repository.getAudiobookById("test-id")

            assertNull(result)
        }

    @Test
    fun `getChaptersForAudiobook returns empty list`() =
        runTest {
            val result = repository.getChaptersForAudiobook("test-audiobook-id")

            assertNotNull(result)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `searchAudiobooks returns empty list`() =
        runTest {
            val result = repository.searchAudiobooks("test query")

            assertNotNull(result)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `searchAudiobooks with empty query returns empty list`() =
        runTest {
            val result = repository.searchAudiobooks("")

            assertNotNull(result)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `multiple calls return consistent empty results`() =
        runTest {
            val result1 = repository.getAllAudiobooks()
            val result2 = repository.getAllAudiobooks()

            assertEquals(result1, result2)
            assertTrue(result1.isEmpty())
            assertTrue(result2.isEmpty())
        }
}
