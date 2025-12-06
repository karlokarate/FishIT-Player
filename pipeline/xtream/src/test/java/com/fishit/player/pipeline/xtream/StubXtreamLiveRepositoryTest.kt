package com.fishit.player.pipeline.xtream.repository.stub

import com.fishit.player.pipeline.xtream.repository.XtreamLiveRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [StubXtreamLiveRepository].
 *
 * These tests verify that the stub implementation returns deterministic
 * empty/predictable results as expected for Phase 2.
 */
class StubXtreamLiveRepositoryTest {
    private lateinit var repository: XtreamLiveRepository

    @Before
    fun setup() {
        repository = StubXtreamLiveRepository()
    }

    @Test
    fun `getChannels returns empty list`() =
        runTest {
            val result = repository.getChannels().first()
            assertTrue("Channels should be empty", result.isEmpty())
        }

    @Test
    fun `getChannels with category returns empty list`() =
        runTest {
            val result = repository.getChannels(categoryId = "test-category").first()
            assertTrue("Channels should be empty", result.isEmpty())
        }

    @Test
    fun `getChannelById returns null`() =
        runTest {
            val result = repository.getChannelById(channelId = 123).first()
            assertNull("Channel by ID should be null", result)
        }

    @Test
    fun `getEpgForChannel returns empty list`() =
        runTest {
            val result =
                repository
                    .getEpgForChannel(
                        epgChannelId = "epg-123",
                        startTime = 0L,
                        endTime = 1000L,
                    ).first()
            assertTrue("EPG entries should be empty", result.isEmpty())
        }

    @Test
    fun `getCurrentEpg returns null`() =
        runTest {
            val result = repository.getCurrentEpg(epgChannelId = "epg-456").first()
            assertNull("Current EPG should be null", result)
        }

    @Test
    fun `searchChannels returns empty list`() =
        runTest {
            val result = repository.searchChannels(query = "test query").first()
            assertTrue("Search results should be empty", result.isEmpty())
        }

    @Test
    fun `refreshLiveData succeeds`() =
        runTest {
            val result = repository.refreshLiveData()
            assertTrue("Refresh should succeed", result.isSuccess)
            assertEquals("Refresh should return Unit", Unit, result.getOrNull())
        }
}
