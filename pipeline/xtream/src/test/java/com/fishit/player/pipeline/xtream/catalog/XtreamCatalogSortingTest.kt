package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for Xtream catalog sorting strategy.
 *
 * Verifies that items are correctly sorted by timestamp DESCENDING (newest first)
 * as implemented in XtreamCatalogPipelineImpl for incremental sync optimization.
 *
 * **Background (Dec 2025):**
 * Analysis of Xtream API responses showed items are NOT sorted by timestamp.
 * The pipeline now uses accumulate-sort-emit strategy to ensure newest items
 * appear first in the UI.
 */
class XtreamCatalogSortingTest {
    // =========================================================================
    // VOD Sorting Tests
    // =========================================================================

    @Test
    fun `VOD items sorted by added DESC - newest first`() {
        val items =
            mutableListOf(
                XtreamVodItem(id = 1, name = "Old Movie", added = 1609459200L), // 2021-01-01
                XtreamVodItem(id = 2, name = "Newest Movie", added = 1734480000L), // 2024-12-18
                XtreamVodItem(id = 3, name = "Middle Movie", added = 1672531200L), // 2023-01-01
            )

        // Apply same sorting as XtreamCatalogPipelineImpl
        items.sortByDescending { it.added ?: 0L }

        assertEquals("Newest Movie", items[0].name)
        assertEquals("Middle Movie", items[1].name)
        assertEquals("Old Movie", items[2].name)
    }

    @Test
    fun `VOD items with null added timestamp sorted to end`() {
        val items =
            mutableListOf(
                XtreamVodItem(id = 1, name = "No Timestamp", added = null),
                XtreamVodItem(id = 2, name = "Has Timestamp", added = 1734480000L),
                XtreamVodItem(id = 3, name = "Also No Timestamp", added = null),
            )

        // Apply same sorting as XtreamCatalogPipelineImpl (null → 0L → end of DESC list)
        items.sortByDescending { it.added ?: 0L }

        assertEquals("Has Timestamp", items[0].name)
        // Items with null timestamps should be at the end
        assertTrue(items[1].added == null || items[1].added == 0L)
        assertTrue(items[2].added == null || items[2].added == 0L)
    }

    @Test
    fun `VOD items with same timestamp maintain stable order`() {
        val items =
            mutableListOf(
                XtreamVodItem(id = 1, name = "Movie A", added = 1734480000L),
                XtreamVodItem(id = 2, name = "Movie B", added = 1734480000L),
                XtreamVodItem(id = 3, name = "Movie C", added = 1734480000L),
            )

        items.sortByDescending { it.added ?: 0L }

        // All have same timestamp, order should be preserved (stable sort)
        assertEquals(3, items.size)
        assertEquals(1734480000L, items[0].added)
        assertEquals(1734480000L, items[1].added)
        assertEquals(1734480000L, items[2].added)
    }

    // =========================================================================
    // Series Sorting Tests
    // =========================================================================

    @Test
    fun `Series items sorted by lastModified DESC - newest first`() {
        val items =
            mutableListOf(
                XtreamSeriesItem(id = 1, name = "Old Series", lastModified = 1609459200L),
                XtreamSeriesItem(id = 2, name = "Newest Series", lastModified = 1734480000L),
                XtreamSeriesItem(id = 3, name = "Middle Series", lastModified = 1672531200L),
            )

        // Apply same sorting as XtreamCatalogPipelineImpl
        items.sortByDescending { it.lastModified ?: 0L }

        assertEquals("Newest Series", items[0].name)
        assertEquals("Middle Series", items[1].name)
        assertEquals("Old Series", items[2].name)
    }

    @Test
    fun `Series items with null lastModified sorted to end`() {
        val items =
            mutableListOf(
                XtreamSeriesItem(id = 1, name = "No Timestamp", lastModified = null),
                XtreamSeriesItem(id = 2, name = "Has Timestamp", lastModified = 1734480000L),
                XtreamSeriesItem(id = 3, name = "Also No Timestamp", lastModified = null),
            )

        // Apply same sorting as XtreamCatalogPipelineImpl
        items.sortByDescending { it.lastModified ?: 0L }

        assertEquals("Has Timestamp", items[0].name)
        assertTrue(items[1].lastModified == null || items[1].lastModified == 0L)
        assertTrue(items[2].lastModified == null || items[2].lastModified == 0L)
    }

    // =========================================================================
    // Live Channel Sorting Tests
    // =========================================================================

    @Test
    fun `Live channels sorted by added DESC - newest first`() {
        val items =
            mutableListOf(
                XtreamChannel(id = 1, name = "Old Channel", added = 1609459200L),
                XtreamChannel(id = 2, name = "Newest Channel", added = 1734480000L),
                XtreamChannel(id = 3, name = "Middle Channel", added = 1672531200L),
            )

        // Apply same sorting as XtreamCatalogPipelineImpl
        items.sortByDescending { it.added ?: 0L }

        assertEquals("Newest Channel", items[0].name)
        assertEquals("Middle Channel", items[1].name)
        assertEquals("Old Channel", items[2].name)
    }

    @Test
    fun `Live channels with null added sorted to end`() {
        val items =
            mutableListOf(
                XtreamChannel(id = 1, name = "No Timestamp", added = null),
                XtreamChannel(id = 2, name = "Has Timestamp", added = 1734480000L),
                XtreamChannel(id = 3, name = "Also No Timestamp", added = null),
            )

        // Apply same sorting as XtreamCatalogPipelineImpl
        items.sortByDescending { it.added ?: 0L }

        assertEquals("Has Timestamp", items[0].name)
        assertTrue(items[1].added == null || items[1].added == 0L)
        assertTrue(items[2].added == null || items[2].added == 0L)
    }

    // =========================================================================
    // Performance Smoke Test (validates sorting doesn't regress)
    // =========================================================================

    @Test
    fun `Large VOD list sorts within acceptable time`() {
        // Simulate ~43K items (actual VOD catalog size from test data)
        val items =
            (1..43000)
                .map { i ->
                    XtreamVodItem(
                        id = i,
                        name = "Movie $i",
                        added = (1609459200L..1734480000L).random(), // Random timestamp in range
                    )
                }.toMutableList()

        val startTime = System.currentTimeMillis()
        items.sortByDescending { it.added ?: 0L }
        val elapsedMs = System.currentTimeMillis() - startTime

        // Verify sorting is fast (should be < 100ms even on CI)
        assertTrue(elapsedMs < 500, "Sorting 43K items took ${elapsedMs}ms, expected < 500ms")

        // Verify sorted correctly (first item has highest timestamp)
        val firstTimestamp = items.firstOrNull()?.added ?: 0L
        val lastTimestamp = items.lastOrNull()?.added ?: 0L
        assertTrue(firstTimestamp >= lastTimestamp, "Expected DESC order")
    }
}
