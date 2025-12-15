package com.fishit.player.pipeline.xtream.model

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for XtreamRawMetadataExtensions.
 *
 * Verifies that Xtream models correctly convert to RawMediaMetadata
 * per MEDIA_NORMALIZATION_CONTRACT.md requirements.
 */
class XtreamRawMetadataExtensionsTest {

    @Test
    fun `XtreamVodItem toRawMediaMetadata provides correct fields`() {
        val dtoTitle = "The Matrix"
        val vod = XtreamVodItem(
            id = 123,
            name = dtoTitle,
            streamIcon = "http://example.com/poster.jpg",
            categoryId = "1",
            containerExtension = "mkv"
        )

        val raw = vod.toRawMediaMetadata()

        assertEquals(dtoTitle, raw.originalTitle)
        assertEquals("", raw.globalId)
        assertEquals(MediaType.MOVIE, raw.mediaType)
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals("xtream:vod:123", raw.sourceId)
        assertEquals("Xtream VOD", raw.sourceLabel)
        assertNull(raw.year) // Not available in list
        assertNull(raw.season)
        assertNull(raw.episode)
        assertNull(raw.durationMinutes)
    }

    @Test
    fun `XtreamSeriesItem toRawMediaMetadata provides correct fields`() {
        val dtoTitle = "Breaking Bad"
        val series = XtreamSeriesItem(
            id = 456,
            name = dtoTitle,
            cover = "http://example.com/cover.jpg",
            categoryId = "2"
        )

        val raw = series.toRawMediaMetadata()

        assertEquals(dtoTitle, raw.originalTitle)
        assertEquals("", raw.globalId)
        assertEquals(MediaType.SERIES, raw.mediaType)
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals("xtream:series:456", raw.sourceId)
        assertEquals("Xtream Series", raw.sourceLabel)
    }

    @Test
    fun `XtreamEpisode toRawMediaMetadata includes season and episode`() {
        val dtoTitle = "Gray Matter"
        val episode = XtreamEpisode(
            id = 789,
            seriesId = 456,
            seasonNumber = 1,
            episodeNumber = 5,
            title = dtoTitle,
            containerExtension = "mp4"
        )

        val raw = episode.toRawMediaMetadata(seriesNameOverride = "Breaking Bad")

        assertEquals(dtoTitle, raw.originalTitle)
        assertEquals("", raw.globalId)
        assertEquals(MediaType.SERIES_EPISODE, raw.mediaType)
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals("xtream:episode:789", raw.sourceId)
        assertEquals("Xtream: Breaking Bad", raw.sourceLabel)
        assertEquals(1, raw.season)
        assertEquals(5, raw.episode)
    }

    @Test
    fun `XtreamEpisode toRawMediaMetadata uses fallback title when blank`() {
        val episode = XtreamEpisode(
            id = 790,
            seriesId = 456,
            seasonNumber = 2,
            episodeNumber = 3,
            title = "",
            containerExtension = "mp4"
        )

        val raw = episode.toRawMediaMetadata(seriesNameOverride = "Breaking Bad")

        assertEquals("", raw.globalId)
        assertEquals("Breaking Bad", raw.originalTitle) // Falls back to series name
    }

    @Test
    fun `XtreamChannel toRawMediaMetadata provides LIVE mediaType`() {
        val dtoTitle = "BBC One HD"
        val channel = XtreamChannel(
            id = 101,
            name = dtoTitle,
            streamIcon = "http://example.com/bbc.png",
            epgChannelId = "bbc.one.hd",
            tvArchive = 1,
            categoryId = "5"
        )

        val raw = channel.toRawMediaMetadata()

        assertEquals(dtoTitle, raw.originalTitle)
        assertEquals("", raw.globalId)
        assertEquals(MediaType.LIVE, raw.mediaType)
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals("xtream:live:101", raw.sourceId)
        assertEquals("Xtream Live", raw.sourceLabel)
    }

    @Test
    fun `all Xtream conversions leave externalIds empty`() {
        val vod = XtreamVodItem(id = 1, name = "Test")
        val series = XtreamSeriesItem(id = 2, name = "Test")
        val episode = XtreamEpisode(id = 3, seriesId = 2, seasonNumber = 1, episodeNumber = 1, title = "Test")
        val channel = XtreamChannel(id = 4, name = "Test")

        // Per contract: Xtream list APIs don't provide TMDB IDs
        assertNull(vod.toRawMediaMetadata().externalIds.tmdbId)
        assertNull(series.toRawMediaMetadata().externalIds.tmdbId)
        assertNull(episode.toRawMediaMetadata().externalIds.tmdbId)
        assertNull(channel.toRawMediaMetadata().externalIds.tmdbId)
    }
}
