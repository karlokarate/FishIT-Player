package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.core.model.SourceType
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [XtreamCatalogPipelineImpl].
 *
 * Verifies:
 * - Event-based scanning flow
 * - Phase ordering (VOD → Series → Episodes → Live)
 * - Config-based filtering
 * - Error handling
 */
class XtreamCatalogPipelineTest {

    private lateinit var source: XtreamCatalogSource
    private lateinit var mapper: XtreamCatalogMapper
    private lateinit var pipeline: XtreamCatalogPipelineImpl

    @Before
    fun setup() {
        source = mockk(relaxed = true)
        mapper = XtreamCatalogMapperImpl()
        pipeline = XtreamCatalogPipelineImpl(source, mapper)

        // Default: empty sources
        coEvery { source.loadVodItems() } returns emptyList()
        coEvery { source.loadSeriesItems() } returns emptyList()
        coEvery { source.loadEpisodes() } returns emptyList()
        coEvery { source.loadLiveChannels() } returns emptyList()
    }

    @Test
    fun `scanCatalog emits ScanStarted and ScanCompleted for empty source`() = runTest {
        val events = pipeline.scanCatalog(XtreamCatalogConfig.DEFAULT).toList()

        // Should have ScanStarted and ScanCompleted
        assertEquals(2, events.size)
        assertTrue(events[0] is XtreamCatalogEvent.ScanStarted)
        assertTrue(events[1] is XtreamCatalogEvent.ScanCompleted)

        val completed = events[1] as XtreamCatalogEvent.ScanCompleted
        assertEquals(0, completed.vodCount)
        assertEquals(0, completed.seriesCount)
        assertEquals(0, completed.episodeCount)
        assertEquals(0, completed.liveCount)
    }

    @Test
    fun `scanCatalog discovers VOD items`() = runTest {
        val vodItems = listOf(
            XtreamVodItem(id = 1, name = "Movie 1", streamIcon = "http://example.com/poster1.jpg"),
            XtreamVodItem(id = 2, name = "Movie 2", streamIcon = "http://example.com/poster2.jpg"),
        )
        coEvery { source.loadVodItems() } returns vodItems

        val events = pipeline.scanCatalog(XtreamCatalogConfig.VOD_ONLY).toList()

        val discovered = events.filterIsInstance<XtreamCatalogEvent.ItemDiscovered>()
        assertEquals(2, discovered.size)

        // Verify first item
        val item1 = discovered[0].item
        assertEquals(XtreamItemKind.VOD, item1.kind)
        assertEquals(1, item1.vodId)
        assertEquals("Movie 1", item1.raw.originalTitle)
        assertEquals(SourceType.XTREAM, item1.raw.sourceType)

        // Verify completion
        val completed = events.filterIsInstance<XtreamCatalogEvent.ScanCompleted>().first()
        assertEquals(2, completed.vodCount)
        assertEquals(0, completed.seriesCount)
    }

    @Test
    fun `scanCatalog discovers series and episodes`() = runTest {
        val series = listOf(
            XtreamSeriesItem(id = 10, name = "Breaking Bad", cover = "http://example.com/bb.jpg"),
        )
        val episodes = listOf(
            XtreamEpisode(id = 100, seriesId = 10, seasonNumber = 1, episodeNumber = 1, title = "Pilot"),
            XtreamEpisode(id = 101, seriesId = 10, seasonNumber = 1, episodeNumber = 2, title = "Cat's in the Bag"),
        )

        coEvery { source.loadSeriesItems() } returns series
        coEvery { source.loadEpisodes() } returns episodes

        val events = pipeline.scanCatalog(XtreamCatalogConfig.SERIES_ONLY).toList()

        val discovered = events.filterIsInstance<XtreamCatalogEvent.ItemDiscovered>()
        assertEquals(3, discovered.size) // 1 series + 2 episodes

        // Check series
        val seriesItem = discovered.first { it.item.kind == XtreamItemKind.SERIES }.item
        assertEquals(10, seriesItem.seriesId)
        assertEquals("Breaking Bad", seriesItem.raw.originalTitle)

        // Check episodes
        val episodeItems = discovered.filter { it.item.kind == XtreamItemKind.EPISODE }
        assertEquals(2, episodeItems.size)

        val completed = events.filterIsInstance<XtreamCatalogEvent.ScanCompleted>().first()
        assertEquals(1, completed.seriesCount)
        assertEquals(2, completed.episodeCount)
    }

    @Test
    fun `scanCatalog discovers live channels`() = runTest {
        val channels = listOf(
            XtreamChannel(id = 1, name = "BBC One", streamIcon = "http://example.com/bbc.png"),
            XtreamChannel(id = 2, name = "CNN", streamIcon = "http://example.com/cnn.png"),
            XtreamChannel(id = 3, name = "ESPN", streamIcon = "http://example.com/espn.png"),
        )
        coEvery { source.loadLiveChannels() } returns channels

        val events = pipeline.scanCatalog(XtreamCatalogConfig.LIVE_ONLY).toList()

        val discovered = events.filterIsInstance<XtreamCatalogEvent.ItemDiscovered>()
        assertEquals(3, discovered.size)

        discovered.forEach { event ->
            assertEquals(XtreamItemKind.LIVE, event.item.kind)
        }

        val completed = events.filterIsInstance<XtreamCatalogEvent.ScanCompleted>().first()
        assertEquals(3, completed.liveCount)
    }

    @Test
    fun `scanCatalog respects config filters`() = runTest {
        coEvery { source.loadVodItems() } returns listOf(
            XtreamVodItem(id = 1, name = "Movie"),
        )
        coEvery { source.loadLiveChannels() } returns listOf(
            XtreamChannel(id = 1, name = "Channel"),
        )

        // VOD only config - should not include live
        val events = pipeline.scanCatalog(XtreamCatalogConfig.VOD_ONLY).toList()

        val discovered = events.filterIsInstance<XtreamCatalogEvent.ItemDiscovered>()
        assertEquals(1, discovered.size)
        assertEquals(XtreamItemKind.VOD, discovered[0].item.kind)
    }

    @Test
    fun `scanCatalog continues after partial source failure`() = runTest {
        coEvery { source.loadVodItems() } throws XtreamCatalogSourceException("VOD failed")
        coEvery { source.loadSeriesItems() } returns listOf(
            XtreamSeriesItem(id = 1, name = "Series"),
        )

        val events = pipeline.scanCatalog(
            XtreamCatalogConfig(
                includeVod = true,
                includeSeries = true,
                includeEpisodes = false,
                includeLive = false,
            ),
        ).toList()

        // Should still discover series despite VOD failure
        val discovered = events.filterIsInstance<XtreamCatalogEvent.ItemDiscovered>()
        assertEquals(1, discovered.size)
        assertEquals(XtreamItemKind.SERIES, discovered[0].item.kind)

        // Should complete successfully
        assertTrue(events.any { it is XtreamCatalogEvent.ScanCompleted })
    }

    @Test
    fun `scanCatalog emits ScanError on total failure`() = runTest {
        coEvery { source.loadVodItems() } throws RuntimeException("Network error")

        val events = pipeline.scanCatalog(XtreamCatalogConfig.VOD_ONLY).toList()

        val error = events.filterIsInstance<XtreamCatalogEvent.ScanError>().firstOrNull()
        assertTrue(error != null)
        assertEquals("unexpected_error", error!!.reason)
    }
}
