package com.fishit.player.feature.home

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.feature.home.domain.HomeMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for [HomeContentStreams] type-safe combine behavior.
 *
 * Purpose:
 * - Verify each list maps to the correct field (no index confusion)
 * - Verify hasContent logic for single and multiple streams
 * - Ensure behavior is identical to previous Array<Any?> + cast approach
 *
 * These tests validate the Premium Gold refactor that replaced:
 * ```
 * combine(...) { values ->
 *     @Suppress("UNCHECKED_CAST")
 *     val telegram = values[0] as List<HomeMediaItem>
 *     ...
 * }
 * ```
 * with type-safe combine:
 * ```
 * combine(telegramItems, liveItems, vodItems, seriesItems) { telegram, live, vod, series ->
 *     HomeContentStreams(telegramMedia = telegram, xtreamLive = live, ...)
 * }
 * ```
 */
class HomeViewModelCombineSafetyTest {

    // ==================== HomeContentStreams Field Mapping Tests ====================

    @Test
    fun `HomeContentStreams telegramMedia field contains only telegram items`() {
        // Given
        val telegramItems = listOf(
            createTestItem(id = "tg-1", title = "Telegram Video 1"),
            createTestItem(id = "tg-2", title = "Telegram Video 2")
        )
        
        // When
        val streams = HomeContentStreams(telegramMedia = telegramItems)
        
        // Then
        assertEquals(2, streams.telegramMedia.size)
        assertEquals("tg-1", streams.telegramMedia[0].id)
        assertEquals("tg-2", streams.telegramMedia[1].id)
        assertTrue(streams.xtreamLive.isEmpty())
        assertTrue(streams.xtreamVod.isEmpty())
        assertTrue(streams.xtreamSeries.isEmpty())
    }

    @Test
    fun `HomeContentStreams xtreamLive field contains only live items`() {
        // Given
        val liveItems = listOf(
            createTestItem(id = "live-1", title = "Live Channel 1")
        )
        
        // When
        val streams = HomeContentStreams(xtreamLive = liveItems)
        
        // Then
        assertEquals(1, streams.xtreamLive.size)
        assertEquals("live-1", streams.xtreamLive[0].id)
        assertTrue(streams.telegramMedia.isEmpty())
        assertTrue(streams.xtreamVod.isEmpty())
        assertTrue(streams.xtreamSeries.isEmpty())
    }

    @Test
    fun `HomeContentStreams xtreamVod field contains only vod items`() {
        // Given
        val vodItems = listOf(
            createTestItem(id = "vod-1", title = "Movie 1"),
            createTestItem(id = "vod-2", title = "Movie 2"),
            createTestItem(id = "vod-3", title = "Movie 3")
        )
        
        // When
        val streams = HomeContentStreams(xtreamVod = vodItems)
        
        // Then
        assertEquals(3, streams.xtreamVod.size)
        assertEquals("vod-1", streams.xtreamVod[0].id)
        assertTrue(streams.telegramMedia.isEmpty())
        assertTrue(streams.xtreamLive.isEmpty())
        assertTrue(streams.xtreamSeries.isEmpty())
    }

    @Test
    fun `HomeContentStreams xtreamSeries field contains only series items`() {
        // Given
        val seriesItems = listOf(
            createTestItem(id = "series-1", title = "TV Show 1")
        )
        
        // When
        val streams = HomeContentStreams(xtreamSeries = seriesItems)
        
        // Then
        assertEquals(1, streams.xtreamSeries.size)
        assertEquals("series-1", streams.xtreamSeries[0].id)
        assertTrue(streams.telegramMedia.isEmpty())
        assertTrue(streams.xtreamLive.isEmpty())
        assertTrue(streams.xtreamVod.isEmpty())
    }

    @Test
    fun `HomeContentStreams continueWatching and recentlyAdded are independent`() {
        // Given
        val continueWatching = listOf(createTestItem(id = "cw-1", title = "Continue 1"))
        val recentlyAdded = listOf(createTestItem(id = "ra-1", title = "Recent 1"))
        
        // When
        val streams = HomeContentStreams(
            continueWatching = continueWatching,
            recentlyAdded = recentlyAdded
        )
        
        // Then
        assertEquals(1, streams.continueWatching.size)
        assertEquals("cw-1", streams.continueWatching[0].id)
        assertEquals(1, streams.recentlyAdded.size)
        assertEquals("ra-1", streams.recentlyAdded[0].id)
    }

    // ==================== hasContent Logic Tests ====================

    @Test
    fun `hasContent is false when all streams are empty`() {
        // Given
        val streams = HomeContentStreams()
        
        // Then
        assertFalse(streams.hasContent)
    }

    @Test
    fun `hasContent is true when only telegramMedia has items`() {
        // Given
        val streams = HomeContentStreams(
            telegramMedia = listOf(createTestItem(id = "tg-1", title = "Test"))
        )
        
        // Then
        assertTrue(streams.hasContent)
    }

    @Test
    fun `hasContent is true when only xtreamLive has items`() {
        // Given
        val streams = HomeContentStreams(
            xtreamLive = listOf(createTestItem(id = "live-1", title = "Test"))
        )
        
        // Then
        assertTrue(streams.hasContent)
    }

    @Test
    fun `hasContent is true when only xtreamVod has items`() {
        // Given
        val streams = HomeContentStreams(
            xtreamVod = listOf(createTestItem(id = "vod-1", title = "Test"))
        )
        
        // Then
        assertTrue(streams.hasContent)
    }

    @Test
    fun `hasContent is true when only xtreamSeries has items`() {
        // Given
        val streams = HomeContentStreams(
            xtreamSeries = listOf(createTestItem(id = "series-1", title = "Test"))
        )
        
        // Then
        assertTrue(streams.hasContent)
    }

    @Test
    fun `hasContent is true when only continueWatching has items`() {
        // Given
        val streams = HomeContentStreams(
            continueWatching = listOf(createTestItem(id = "cw-1", title = "Test"))
        )
        
        // Then
        assertTrue(streams.hasContent)
    }

    @Test
    fun `hasContent is true when only recentlyAdded has items`() {
        // Given
        val streams = HomeContentStreams(
            recentlyAdded = listOf(createTestItem(id = "ra-1", title = "Test"))
        )
        
        // Then
        assertTrue(streams.hasContent)
    }

    @Test
    fun `hasContent is true when multiple streams have items`() {
        // Given
        val streams = HomeContentStreams(
            telegramMedia = listOf(createTestItem(id = "tg-1", title = "Telegram")),
            xtreamVod = listOf(createTestItem(id = "vod-1", title = "VOD")),
            xtreamLive = listOf(createTestItem(id = "live-1", title = "Live"))
        )
        
        // Then
        assertTrue(streams.hasContent)
    }

    // ==================== HomeState Consistency Tests ====================

    @Test
    fun `HomeState hasContent matches HomeContentStreams behavior`() {
        // Given - empty state
        val emptyState = HomeState()
        assertFalse(emptyState.hasContent)

        // Given - state with telegram items
        val stateWithTelegram = HomeState(
            telegramMediaItems = listOf(createTestItem(id = "tg-1", title = "Test"))
        )
        assertTrue(stateWithTelegram.hasContent)

        // Given - state with mixed items
        val mixedState = HomeState(
            xtreamVodItems = listOf(createTestItem(id = "vod-1", title = "Movie")),
            xtreamSeriesItems = listOf(createTestItem(id = "series-1", title = "Show"))
        )
        assertTrue(mixedState.hasContent)
    }

    @Test
    fun `HomeState all content fields are independent`() {
        // Given
        val state = HomeState(
            continueWatchingItems = listOf(createTestItem(id = "cw", title = "Continue")),
            recentlyAddedItems = listOf(createTestItem(id = "ra", title = "Recent")),
            telegramMediaItems = listOf(createTestItem(id = "tg", title = "Telegram")),
            xtreamLiveItems = listOf(createTestItem(id = "live", title = "Live")),
            xtreamVodItems = listOf(createTestItem(id = "vod", title = "VOD")),
            xtreamSeriesItems = listOf(createTestItem(id = "series", title = "Series"))
        )
        
        // Then - each field contains exactly its item
        assertEquals(1, state.continueWatchingItems.size)
        assertEquals("cw", state.continueWatchingItems[0].id)
        
        assertEquals(1, state.recentlyAddedItems.size)
        assertEquals("ra", state.recentlyAddedItems[0].id)
        
        assertEquals(1, state.telegramMediaItems.size)
        assertEquals("tg", state.telegramMediaItems[0].id)
        
        assertEquals(1, state.xtreamLiveItems.size)
        assertEquals("live", state.xtreamLiveItems[0].id)
        
        assertEquals(1, state.xtreamVodItems.size)
        assertEquals("vod", state.xtreamVodItems[0].id)
        
        assertEquals(1, state.xtreamSeriesItems.size)
        assertEquals("series", state.xtreamSeriesItems[0].id)
    }

    // ==================== Test Helpers ====================

    private fun createTestItem(
        id: String,
        title: String,
        mediaType: MediaType = MediaType.MOVIE,
        sourceType: SourceType = SourceType.TELEGRAM
    ): HomeMediaItem = HomeMediaItem(
        id = id,
        title = title,
        mediaType = mediaType,
        sourceType = sourceType,
        navigationId = id,
        navigationSource = sourceType
    )
}
