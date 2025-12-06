package com.fishit.player.pipeline.xtream.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [XtreamLiveRepositoryStub].
 *
 * These tests verify that the stub implementation:
 * - Returns empty lists for all collection queries
 * - Returns null for all single-item queries
 * - Behaves deterministically
 */
class XtreamLiveRepositoryStubTest {
    private lateinit var repository: XtreamLiveRepositoryStub

    @Before
    fun setup() {
        repository = XtreamLiveRepositoryStub()
    }

    @Test
    fun `getChannels returns empty list`() =
        runTest {
            val result = repository.getChannels().first()
            assertTrue("Channels should be empty", result.isEmpty())
        }

    @Test
    fun `getChannels with categoryId returns empty list`() =
        runTest {
            val result = repository.getChannels(categoryId = 123).first()
            assertTrue("Channels should be empty", result.isEmpty())
        }

    @Test
    fun `searchChannels returns empty list`() =
        runTest {
            val result = repository.searchChannels("test query").first()
            assertTrue("Search results should be empty", result.isEmpty())
        }

    @Test
    fun `getChannelById returns null`() =
        runTest {
            val result = repository.getChannelById(channelId = 1)
            assertNull("Channel by ID should be null", result)
        }

    @Test
    fun `getCurrentAndNextEpg returns empty list`() =
        runTest {
            val result = repository.getCurrentAndNextEpg(channelId = 1).first()
            assertTrue("EPG should be empty", result.isEmpty())
        }

    @Test
    fun `getEpgByTimeRange returns empty list`() =
        runTest {
            val result =
                repository
                    .getEpgByTimeRange(
                        channelId = 1,
                        startTime = 1000,
                        endTime = 2000,
                    ).first()
            assertTrue("EPG should be empty", result.isEmpty())
        }

    @Test
    fun `getEpgAtTime returns empty map`() =
        runTest {
            val result =
                repository
                    .getEpgAtTime(
                        channelIds = listOf(1, 2, 3),
                        timestamp = 1000,
                    ).first()
            assertTrue("EPG map should be empty", result.isEmpty())
        }

    @Test
    fun `multiple calls return consistent empty results`() =
        runTest {
            val result1 = repository.getChannels().first()
            val result2 = repository.getChannels().first()

            assertEquals("Results should be consistently empty", result1, result2)
            assertTrue("Both results should be empty", result1.isEmpty() && result2.isEmpty())
        }
}
