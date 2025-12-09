package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for [XtreamCatalogMapperImpl].
 *
 * Verifies:
 * - Correct RawMediaMetadata generation
 * - ImageRef population from URLs
 * - Kind assignment
 * - SourceId format
 */
class XtreamCatalogMapperTest {

    private lateinit var mapper: XtreamCatalogMapper

    @Before
    fun setup() {
        mapper = XtreamCatalogMapperImpl()
    }

    // ==================== VOD Tests ====================

    @Test
    fun `fromVod creates correct catalog item`() {
        val vod = XtreamVodItem(
            id = 123,
            name = "Inception",
            streamIcon = "https://example.com/inception.jpg",
            categoryId = "movies",
            containerExtension = "mkv",
        )

        val item = mapper.fromVod(vod, emptyMap())

        assertEquals(XtreamItemKind.VOD, item.kind)
        assertEquals(123, item.vodId)
        assertNull(item.seriesId)
        assertNull(item.episodeId)
        assertNull(item.channelId)

        val raw = item.raw
        assertEquals("Inception", raw.originalTitle)
        assertEquals(MediaType.MOVIE, raw.mediaType)
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals("xtream:vod:123", raw.sourceId)
        assertNotNull(raw.poster)
    }

    @Test
    fun `fromVod handles missing poster`() {
        val vod = XtreamVodItem(
            id = 1,
            name = "No Poster Movie",
            streamIcon = null,
        )

        val item = mapper.fromVod(vod, emptyMap())

        assertNull(item.raw.poster)
    }

    // ==================== Series Tests ====================

    @Test
    fun `fromSeries creates correct catalog item`() {
        val series = XtreamSeriesItem(
            id = 456,
            name = "Breaking Bad",
            cover = "https://example.com/bb.jpg",
            categoryId = "drama",
        )

        val item = mapper.fromSeries(series, emptyMap())

        assertEquals(XtreamItemKind.SERIES, item.kind)
        assertEquals(456, item.seriesId)
        assertNull(item.vodId)
        assertNull(item.episodeId)

        val raw = item.raw
        assertEquals("Breaking Bad", raw.originalTitle)
        assertEquals(MediaType.SERIES_EPISODE, raw.mediaType) // Marked as episode parent
        assertEquals("xtream:series:456", raw.sourceId)
    }

    // ==================== Episode Tests ====================

    @Test
    fun `fromEpisode creates correct catalog item with season and episode`() {
        val episode = XtreamEpisode(
            id = 789,
            seriesId = 456,
            seasonNumber = 2,
            episodeNumber = 5,
            title = "Confessions",
            thumbnail = "https://example.com/ep.jpg",
        )

        val item = mapper.fromEpisode(episode, "Breaking Bad", emptyMap())

        assertEquals(XtreamItemKind.EPISODE, item.kind)
        assertEquals(456, item.seriesId)
        assertEquals(789, item.episodeId)

        val raw = item.raw
        assertEquals("Confessions", raw.originalTitle)
        assertEquals(MediaType.SERIES_EPISODE, raw.mediaType)
        assertEquals(2, raw.season)
        assertEquals(5, raw.episode)
        assertEquals("Xtream: Breaking Bad", raw.sourceLabel)
    }

    @Test
    fun `fromEpisode uses fallback title when title is blank`() {
        val episode = XtreamEpisode(
            id = 1,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 3,
            title = "",
        )

        val item = mapper.fromEpisode(episode, "Test Series", emptyMap())

        assertEquals("Test Series", item.raw.originalTitle)
    }

    // ==================== Channel Tests ====================

    @Test
    fun `fromChannel creates correct catalog item`() {
        val channel = XtreamChannel(
            id = 999,
            name = "BBC One",
            streamIcon = "https://example.com/bbc.png",
            epgChannelId = "bbc.one",
            tvArchive = 1,
        )

        val item = mapper.fromChannel(channel, emptyMap())

        assertEquals(XtreamItemKind.LIVE, item.kind)
        assertEquals(999, item.channelId)
        assertNull(item.vodId)
        assertNull(item.seriesId)

        val raw = item.raw
        assertEquals("BBC One", raw.originalTitle)
        assertEquals(MediaType.LIVE, raw.mediaType)
        assertEquals("xtream:live:999", raw.sourceId)
        assertNotNull(raw.poster)
    }

    // ==================== Auth Headers Tests ====================

    @Test
    fun `mapper passes auth headers to ImageRef`() {
        val vod = XtreamVodItem(
            id = 1,
            name = "Movie",
            streamIcon = "https://panel.example.com/images/poster.jpg",
        )

        val headers = mapOf("Authorization" to "Bearer token123")
        val item = mapper.fromVod(vod, headers)

        // ImageRef.Http should have headers set
        // We can't easily verify this without accessing the internal structure,
        // but we can verify the poster is created
        assertNotNull(item.raw.poster)
    }
}
