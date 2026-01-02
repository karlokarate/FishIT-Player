package com.fishit.player.feature.home

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.home.domain.HomeMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.test.runTest

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

    // ==================== HomeContentPartial Tests ====================

    @Test
    fun `HomeContentPartial contains all 4 fields correctly mapped`() {
        // Given
        val continueWatching = listOf(createTestItem(id = "cw-1", title = "Continue 1"))
        val recentlyAdded = listOf(createTestItem(id = "ra-1", title = "Recent 1"))
        val telegram = listOf(createTestItem(id = "tg-1", title = "Telegram 1"))
        val live = listOf(createTestItem(id = "live-1", title = "Live 1"))
        
        // When
        val partial = HomeContentPartial(
            continueWatching = continueWatching,
            recentlyAdded = recentlyAdded,
            telegramMedia = telegram,
            xtreamLive = live
        )
        
        // Then
        assertEquals(1, partial.continueWatching.size)
        assertEquals("cw-1", partial.continueWatching[0].id)
        assertEquals(1, partial.recentlyAdded.size)
        assertEquals("ra-1", partial.recentlyAdded[0].id)
        assertEquals(1, partial.telegramMedia.size)
        assertEquals("tg-1", partial.telegramMedia[0].id)
        assertEquals(1, partial.xtreamLive.size)
        assertEquals("live-1", partial.xtreamLive[0].id)
    }

    @Test
    fun `HomeContentStreams preserves HomeContentPartial fields correctly`() {
        // Given
        val partial = HomeContentPartial(
            continueWatching = listOf(createTestItem(id = "cw", title = "Continue")),
            recentlyAdded = listOf(createTestItem(id = "ra", title = "Recent")),
            telegramMedia = listOf(createTestItem(id = "tg", title = "Telegram")),
            xtreamLive = listOf(createTestItem(id = "live", title = "Live"))
        )
        val vod = listOf(createTestItem(id = "vod", title = "VOD"))
        val series = listOf(createTestItem(id = "series", title = "Series"))
        
        // When - Simulating stage 2 combine
        val streams = HomeContentStreams(
            continueWatching = partial.continueWatching,
            recentlyAdded = partial.recentlyAdded,
            telegramMedia = partial.telegramMedia,
            xtreamLive = partial.xtreamLive,
            xtreamVod = vod,
            xtreamSeries = series
        )
        
        // Then - All 6 fields are correctly populated
        assertEquals("cw", streams.continueWatching[0].id)
        assertEquals("ra", streams.recentlyAdded[0].id)
        assertEquals("tg", streams.telegramMedia[0].id)
        assertEquals("live", streams.xtreamLive[0].id)
        assertEquals("vod", streams.xtreamVod[0].id)
        assertEquals("series", streams.xtreamSeries[0].id)
    }

    // ==================== 6-Stream Integration Test ====================

    @Test
    fun `full 6-stream combine produces correct HomeContentStreams`() = runTest {
        // Given - 6 independent flows
        val continueWatchingFlow = flowOf(listOf(
            createTestItem(id = "cw-1", title = "Continue 1"),
            createTestItem(id = "cw-2", title = "Continue 2")
        ))
        val recentlyAddedFlow = flowOf(listOf(
            createTestItem(id = "ra-1", title = "Recent 1")
        ))
        val telegramFlow = flowOf(listOf(
            createTestItem(id = "tg-1", title = "Telegram 1"),
            createTestItem(id = "tg-2", title = "Telegram 2"),
            createTestItem(id = "tg-3", title = "Telegram 3")
        ))
        val liveFlow = flowOf(listOf(
            createTestItem(id = "live-1", title = "Live 1")
        ))
        val vodFlow = flowOf(listOf(
            createTestItem(id = "vod-1", title = "VOD 1"),
            createTestItem(id = "vod-2", title = "VOD 2")
        ))
        val seriesFlow = flowOf(listOf(
            createTestItem(id = "series-1", title = "Series 1")
        ))
        
        // When - Stage 1: 4-way combine into partial
        val partialFlow = combine(
            continueWatchingFlow,
            recentlyAddedFlow,
            telegramFlow,
            liveFlow
        ) { continueWatching, recentlyAdded, telegram, live ->
            HomeContentPartial(
                continueWatching = continueWatching,
                recentlyAdded = recentlyAdded,
                telegramMedia = telegram,
                xtreamLive = live
            )
        }
        
        // When - Stage 2: 3-way combine into streams
        val streamsFlow = combine(
            partialFlow,
            vodFlow,
            seriesFlow
        ) { partial, vod, series ->
            HomeContentStreams(
                continueWatching = partial.continueWatching,
                recentlyAdded = partial.recentlyAdded,
                telegramMedia = partial.telegramMedia,
                xtreamLive = partial.xtreamLive,
                xtreamVod = vod,
                xtreamSeries = series
            )
        }
        
        // Then - Collect and verify
        val result = streamsFlow.first()
        
        // Verify counts
        assertEquals(2, result.continueWatching.size)
        assertEquals(1, result.recentlyAdded.size)
        assertEquals(3, result.telegramMedia.size)
        assertEquals(1, result.xtreamLive.size)
        assertEquals(2, result.xtreamVod.size)
        assertEquals(1, result.xtreamSeries.size)
        
        // Verify IDs are correctly mapped (no index confusion)
        assertEquals("cw-1", result.continueWatching[0].id)
        assertEquals("cw-2", result.continueWatching[1].id)
        assertEquals("ra-1", result.recentlyAdded[0].id)
        assertEquals("tg-1", result.telegramMedia[0].id)
        assertEquals("tg-2", result.telegramMedia[1].id)
        assertEquals("tg-3", result.telegramMedia[2].id)
        assertEquals("live-1", result.xtreamLive[0].id)
        assertEquals("vod-1", result.xtreamVod[0].id)
        assertEquals("vod-2", result.xtreamVod[1].id)
        assertEquals("series-1", result.xtreamSeries[0].id)
        
        // Verify hasContent
        assertTrue(result.hasContent)
    }

    @Test
    fun `6-stream combine with all empty streams produces empty HomeContentStreams`() = runTest {
        // Given - All empty flows
        val emptyFlow = flowOf(emptyList<HomeMediaItem>())
        
        // When - Stage 1
        val partialFlow = combine(
            emptyFlow, emptyFlow, emptyFlow, emptyFlow
        ) { cw, ra, tg, live ->
            HomeContentPartial(
                continueWatching = cw,
                recentlyAdded = ra,
                telegramMedia = tg,
                xtreamLive = live
            )
        }
        
        // When - Stage 2
        val streamsFlow = combine(
            partialFlow, emptyFlow, emptyFlow
        ) { partial, vod, series ->
            HomeContentStreams(
                continueWatching = partial.continueWatching,
                recentlyAdded = partial.recentlyAdded,
                telegramMedia = partial.telegramMedia,
                xtreamLive = partial.xtreamLive,
                xtreamVod = vod,
                xtreamSeries = series
            )
        }
        
        // Then
        val result = streamsFlow.first()
        assertFalse(result.hasContent)
        assertTrue(result.continueWatching.isEmpty())
        assertTrue(result.recentlyAdded.isEmpty())
        assertTrue(result.telegramMedia.isEmpty())
        assertTrue(result.xtreamLive.isEmpty())
        assertTrue(result.xtreamVod.isEmpty())
        assertTrue(result.xtreamSeries.isEmpty())
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
