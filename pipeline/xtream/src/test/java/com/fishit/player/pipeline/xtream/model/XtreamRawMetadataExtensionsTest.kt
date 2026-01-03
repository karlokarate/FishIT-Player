package com.fishit.player.pipeline.xtream.model

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for XtreamRawMetadataExtensions.
 *
 * Verifies that Xtream models correctly convert to RawMediaMetadata per
 * MEDIA_NORMALIZATION_CONTRACT.md requirements.
 */
class XtreamRawMetadataExtensionsTest {
    @Test
    fun `XtreamVodItem toRawMediaMetadata provides correct fields`() {
        val dtoTitle = "The Matrix"
        val vod =
            XtreamVodItem(
                id = 123,
                name = dtoTitle,
                streamIcon = "http://example.com/poster.jpg",
                categoryId = "1",
                containerExtension = "mkv",
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
        assertNull(raw.durationMs) // Not available in list API
    }

    @Test
    fun `XtreamSeriesItem toRawMediaMetadata provides correct fields`() {
        val dtoTitle = "Breaking Bad"
        val series =
            XtreamSeriesItem(
                id = 456,
                name = dtoTitle,
                cover = "http://example.com/cover.jpg",
                categoryId = "2",
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
        val episode =
            XtreamEpisode(
                id = 789,
                seriesId = 456,
                seasonNumber = 1,
                episodeNumber = 5,
                title = dtoTitle,
                containerExtension = "mp4",
            )

        val raw = episode.toRawMediaMetadata(seriesNameOverride = "Breaking Bad")

        assertEquals(dtoTitle, raw.originalTitle)
        assertEquals("", raw.globalId)
        assertEquals(MediaType.SERIES_EPISODE, raw.mediaType)
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals("xtream:episode:456:1:5", raw.sourceId)
        assertEquals("Xtream: Breaking Bad", raw.sourceLabel)
        assertEquals(1, raw.season)
        assertEquals(5, raw.episode)
    }

    @Test
    fun `XtreamEpisode toRawMediaMetadata uses fallback title when blank`() {
        val episode =
            XtreamEpisode(
                id = 790,
                seriesId = 456,
                seasonNumber = 2,
                episodeNumber = 3,
                title = "",
                containerExtension = "mp4",
            )

        val raw = episode.toRawMediaMetadata(seriesNameOverride = "Breaking Bad")

        assertEquals("", raw.globalId)
        assertEquals("Breaking Bad", raw.originalTitle) // Falls back to series name
    }

    @Test
    fun `XtreamChannel toRawMediaMetadata provides LIVE mediaType`() {
        val dtoTitle = "BBC One HD"
        val channel =
            XtreamChannel(
                id = 101,
                name = dtoTitle,
                streamIcon = "http://example.com/bbc.png",
                epgChannelId = "bbc.one.hd",
                tvArchive = 1,
                categoryId = "5",
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
        val episode =
            XtreamEpisode(
                id = 3,
                seriesId = 2,
                seasonNumber = 1,
                episodeNumber = 1,
                title = "Test",
            )
        val channel = XtreamChannel(id = 4, name = "Test")

        // Per contract: Xtream list APIs don't provide TMDB IDs
        assertNull(vod.toRawMediaMetadata().externalIds.tmdb)
        assertNull(series.toRawMediaMetadata().externalIds.tmdb)
        assertNull(episode.toRawMediaMetadata().externalIds.tmdb)
        assertNull(channel.toRawMediaMetadata().externalIds.tmdb)
    }

    // =======================================================================
    // CONTRACT TEST: GlobalId Isolation (MEDIA_NORMALIZATION_CONTRACT Section 2.1.1)
    // =======================================================================

    @Test
    fun `CONTRACT - all Xtream conversions leave globalId empty`() {
        // Per MEDIA_NORMALIZATION_CONTRACT.md Section 2.1.1:
        // Pipelines MUST leave globalId empty (""). Canonical identity is computed
        // centrally by :core:metadata-normalizer.

        val vod = XtreamVodItem(id = 1, name = "The Matrix")
        val series = XtreamSeriesItem(id = 2, name = "Breaking Bad", year = "2008")
        val episode =
            XtreamEpisode(
                id = 3,
                seriesId = 2,
                seasonNumber = 1,
                episodeNumber = 1,
                title = "Pilot",
            )
        val channel = XtreamChannel(id = 4, name = "BBC One HD")

        // ALL conversions must leave globalId empty - normalizer owns this field
        assertEquals("", vod.toRawMediaMetadata().globalId, "VOD globalId must be empty")
        assertEquals(
            "",
            series.toRawMediaMetadata().globalId,
            "Series globalId must be empty",
        )
        assertEquals(
            "",
            episode.toRawMediaMetadata().globalId,
            "Episode globalId must be empty",
        )
        assertEquals(
            "",
            channel.toRawMediaMetadata().globalId,
            "Channel globalId must be empty",
        )
    }
}
