package com.fishit.player.pipeline.xtream.repository.stub

import com.fishit.player.pipeline.xtream.repository.XtreamCatalogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [StubXtreamCatalogRepository].
 *
 * These tests verify that the stub implementation returns deterministic
 * empty/predictable results as expected for Phase 2.
 */
class StubXtreamCatalogRepositoryTest {
    private lateinit var repository: XtreamCatalogRepository

    @Before
    fun setup() {
        repository = StubXtreamCatalogRepository()
    }

    @Test
    fun `getVodItems returns empty list`() =
        runTest {
            val result = repository.getVodItems().first()
            assertTrue("VOD items should be empty", result.isEmpty())
        }

    @Test
    fun `getVodItems with category returns empty list`() =
        runTest {
            val result = repository.getVodItems(categoryId = "test-category").first()
            assertTrue("VOD items should be empty", result.isEmpty())
        }

    @Test
    fun `getVodById returns null`() =
        runTest {
            val result = repository.getVodById(vodId = 123).first()
            assertNull("VOD by ID should be null", result)
        }

    @Test
    fun `getSeriesItems returns empty list`() =
        runTest {
            val result = repository.getSeriesItems().first()
            assertTrue("Series items should be empty", result.isEmpty())
        }

    @Test
    fun `getSeriesById returns null`() =
        runTest {
            val result = repository.getSeriesById(seriesId = 456).first()
            assertNull("Series by ID should be null", result)
        }

    @Test
    fun `getEpisodes returns empty list`() =
        runTest {
            val result = repository.getEpisodes(seriesId = 789, seasonNumber = 1).first()
            assertTrue("Episodes should be empty", result.isEmpty())
        }

    @Test
    fun `search returns empty list`() =
        runTest {
            val result = repository.search(query = "test query").first()
            assertTrue("Search results should be empty", result.isEmpty())
        }

    @Test
    fun `refreshCatalog succeeds`() =
        runTest {
            val result = repository.refreshCatalog()
            assertTrue("Refresh should succeed", result.isSuccess)
            assertEquals("Refresh should return Unit", Unit, result.getOrNull())
        }
}
