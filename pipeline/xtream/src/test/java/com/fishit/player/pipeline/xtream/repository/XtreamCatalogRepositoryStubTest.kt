package com.fishit.player.pipeline.xtream.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [XtreamCatalogRepositoryStub].
 *
 * These tests verify that the stub implementation:
 * - Returns empty lists for all collection queries
 * - Returns null for all single-item queries
 * - Behaves deterministically
 */
class XtreamCatalogRepositoryStubTest {
    private lateinit var repository: XtreamCatalogRepositoryStub

    @Before
    fun setup() {
        repository = XtreamCatalogRepositoryStub()
    }

    @Test
    fun `getVodItems returns empty list`() =
        runTest {
            val result = repository.getVodItems().first()
            assertTrue("VOD items should be empty", result.isEmpty())
        }

    @Test
    fun `getVodItems with categoryId returns empty list`() =
        runTest {
            val result = repository.getVodItems(categoryId = 123).first()
            assertTrue("VOD items should be empty", result.isEmpty())
        }

    @Test
    fun `searchVodItems returns empty list`() =
        runTest {
            val result = repository.searchVodItems("test query").first()
            assertTrue("Search results should be empty", result.isEmpty())
        }

    @Test
    fun `getVodById returns null`() =
        runTest {
            val result = repository.getVodById(vodId = 1)
            assertNull("VOD by ID should be null", result)
        }

    @Test
    fun `getSeries returns empty list`() =
        runTest {
            val result = repository.getSeries().first()
            assertTrue("Series should be empty", result.isEmpty())
        }

    @Test
    fun `getSeries with categoryId returns empty list`() =
        runTest {
            val result = repository.getSeries(categoryId = 456).first()
            assertTrue("Series should be empty", result.isEmpty())
        }

    @Test
    fun `searchSeries returns empty list`() =
        runTest {
            val result = repository.searchSeries("test query").first()
            assertTrue("Search results should be empty", result.isEmpty())
        }

    @Test
    fun `getSeriesById returns null`() =
        runTest {
            val result = repository.getSeriesById(seriesId = 1)
            assertNull("Series by ID should be null", result)
        }

    @Test
    fun `getEpisodes returns empty list`() =
        runTest {
            val result = repository.getEpisodes(seriesId = 1).first()
            assertTrue("Episodes should be empty", result.isEmpty())
        }

    @Test
    fun `getEpisodesBySeason returns empty list`() =
        runTest {
            val result = repository.getEpisodesBySeason(seriesId = 1, seasonNumber = 1).first()
            assertTrue("Episodes should be empty", result.isEmpty())
        }

    @Test
    fun `getEpisode returns null`() =
        runTest {
            val result = repository.getEpisode(seriesId = 1, seasonNumber = 1, episodeNumber = 1)
            assertNull("Episode should be null", result)
        }

    @Test
    fun `multiple calls return consistent empty results`() =
        runTest {
            val result1 = repository.getVodItems().first()
            val result2 = repository.getVodItems().first()

            assertEquals("Results should be consistently empty", result1, result2)
            assertTrue("Both results should be empty", result1.isEmpty() && result2.isEmpty())
        }
}
