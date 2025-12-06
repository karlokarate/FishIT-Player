package com.fishit.player.pipeline.io

import com.fishit.player.pipeline.io.IoMediaItem
import com.fishit.player.pipeline.io.IoSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for StubIoContentRepository.
 *
 * These tests verify the stub implementation behaves deterministically
 * and satisfies the IoContentRepository interface contract.
 */
class StubIoContentRepositoryTest {
    private lateinit var repository: StubIoContentRepository

    @Before
    fun setup() {
        repository = StubIoContentRepository()
    }

    @Test
    fun `discoverAll returns fake items`() =
        runBlocking {
            val items = repository.discoverAll().first()

            assertTrue(items.isNotEmpty())
            assertTrue(items.all { it.id.isNotEmpty() })
            assertTrue(items.all { it.title.isNotEmpty() })
        }

    @Test
    fun `discoverAll returns same items on multiple calls`() =
        runBlocking {
            val items1 = repository.discoverAll().first()
            val items2 = repository.discoverAll().first()

            // Stub should be deterministic
            assertEquals(items1.size, items2.size)
            assertEquals(items1.map { it.id }, items2.map { it.id })
        }

    @Test
    fun `listItems returns empty for arbitrary source`() =
        runBlocking {
            val source = IoSource.LocalFile("/some/path")
            val items = repository.listItems(source, recursive = false).first()

            assertTrue(items.isEmpty())
        }

    @Test
    fun `search returns matching items`() =
        runBlocking {
            val allItems = repository.discoverAll().first()
            val searchQuery = allItems.first().title.take(5)

            val results = repository.search(searchQuery).first()

            assertTrue(results.isNotEmpty())
            assertTrue(results.all { it.title.contains(searchQuery, ignoreCase = true) })
        }

    @Test
    fun `search returns empty for non-matching query`() =
        runBlocking {
            val results = repository.search("nonexistent-query-xyz").first()

            assertTrue(results.isEmpty())
        }

    @Test
    fun `getItemById returns item if exists`() =
        runBlocking {
            val allItems = repository.discoverAll().first()
            val existingId = allItems.first().id

            val item = repository.getItemById(existingId)

            assertNotNull(item)
            assertEquals(existingId, item?.id)
        }

    @Test
    fun `getItemById returns null if not exists`() =
        runBlocking {
            val item = repository.getItemById("nonexistent-id")

            assertNull(item)
        }

    @Test
    fun `refresh does not throw`() =
        runBlocking {
            // Stub refresh is a no-op but should not crash
            repository.refresh()
        }
}
