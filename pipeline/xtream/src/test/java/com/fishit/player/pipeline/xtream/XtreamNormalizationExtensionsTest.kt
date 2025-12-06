package com.fishit.player.pipeline.xtream

import com.fishit.player.core.model.SourceType
import com.fishit.player.pipeline.xtream.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for Xtream media normalization extensions.
 *
 * These tests verify that toRawMediaMetadata() correctly extracts
 * raw metadata fields without any cleaning or normalization.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Tests MUST verify correct RawMediaMetadata mapping
 * - Tests MUST NOT verify normalization or TMDB behavior
 */
class XtreamNormalizationExtensionsTest {

    @Test
    fun `XtreamVodItem toRawMediaMetadata extracts raw fields correctly`() {
        val vod = XtreamVodItem(
            id = 12345,
            name = "X-Men.2000.1080p.BluRay.x264-GROUP",
            streamIcon = "http://example.com/icon.jpg",
            categoryId = "movies",
            containerExtension = "mp4"
        )

        val raw = vod.toRawMediaMetadata()

        // Verify raw title is NOT cleaned - passed through as-is
        assertEquals("X-Men.2000.1080p.BluRay.x264-GROUP", raw.originalTitle)
        
        // Verify source identification
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals("Xtream VOD", raw.sourceLabel)
        assertEquals("xtream:vod:12345", raw.sourceId)
        
        // Verify stub limitations (these will be populated in Phase 3)
        assertNull(raw.year)
        assertNull(raw.season)
        assertNull(raw.episode)
        assertNull(raw.durationMinutes)
        assertNull(raw.externalIds.tmdbId)
        assertNull(raw.externalIds.imdbId)
        assertNull(raw.externalIds.tvdbId)
    }

    @Test
    fun `XtreamVodItem with minimal fields still produces valid RawMediaMetadata`() {
        val vod = XtreamVodItem(
            id = 999,
            name = "Simple Movie"
        )

        val raw = vod.toRawMediaMetadata()

        assertEquals("Simple Movie", raw.originalTitle)
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals("xtream:vod:999", raw.sourceId)
    }

    @Test
    fun `XtreamEpisode toRawMediaMetadata extracts season and episode correctly`() {
        val episode = XtreamEpisode(
            id = 54321,
            seriesId = 100,
            seasonNumber = 3,
            episodeNumber = 15,
            title = "The.Walking.Dead.S03E15.This.Sorrowful.Life.1080p.BluRay",
            containerExtension = "mkv",
            thumbnail = "http://example.com/thumb.jpg"
        )

        val raw = episode.toRawMediaMetadata()

        // Verify raw title is NOT cleaned - passed through as-is
        assertEquals("The.Walking.Dead.S03E15.This.Sorrowful.Life.1080p.BluRay", raw.originalTitle)
        
        // Verify season/episode numbers are extracted
        assertEquals(3, raw.season)
        assertEquals(15, raw.episode)
        
        // Verify source identification
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals("Xtream Episode", raw.sourceLabel)
        assertEquals("xtream:episode:54321", raw.sourceId)
        
        // Verify stub limitations
        assertNull(raw.year)
        assertNull(raw.durationMinutes)
    }

    @Test
    fun `XtreamEpisode with minimal fields still produces valid RawMediaMetadata`() {
        val episode = XtreamEpisode(
            id = 888,
            seriesId = 200,
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Pilot"
        )

        val raw = episode.toRawMediaMetadata()

        assertEquals("Pilot", raw.originalTitle)
        assertEquals(1, raw.season)
        assertEquals(1, raw.episode)
        assertEquals("xtream:episode:888", raw.sourceId)
    }

    @Test
    fun `XtreamSeriesItem toRawMediaMetadata extracts raw series metadata`() {
        val series = XtreamSeriesItem(
            id = 777,
            name = "Breaking.Bad.Complete.Series.1080p.BluRay",
            cover = "http://example.com/cover.jpg",
            categoryId = "drama"
        )

        val raw = series.toRawMediaMetadata()

        // Verify raw title is NOT cleaned - passed through as-is
        assertEquals("Breaking.Bad.Complete.Series.1080p.BluRay", raw.originalTitle)
        
        // Verify series items don't have season/episode
        assertNull(raw.season)
        assertNull(raw.episode)
        
        // Verify source identification
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals("Xtream Series", raw.sourceLabel)
        assertEquals("xtream:series:777", raw.sourceId)
    }

    @Test
    fun `XtreamChannel toRawMediaMetadata handles live channel metadata`() {
        val channel = XtreamChannel(
            id = 666,
            name = "HBO HD",
            streamIcon = "http://example.com/hbo.jpg",
            epgChannelId = "hbo.hd",
            tvArchive = 1,
            categoryId = "entertainment"
        )

        val raw = channel.toRawMediaMetadata()

        // Verify raw title
        assertEquals("HBO HD", raw.originalTitle)
        
        // Verify live channels don't have season/episode/year/duration
        assertNull(raw.year)
        assertNull(raw.season)
        assertNull(raw.episode)
        assertNull(raw.durationMinutes)
        
        // Verify source identification
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals("Xtream Live Channel", raw.sourceLabel)
        assertEquals("xtream:live:666", raw.sourceId)
    }

    @Test
    fun `toRawMediaMetadata does not clean or normalize titles`() {
        // Test with various "dirty" titles that should be passed through as-is
        val dirtyTitles = listOf(
            "Movie.Name.2020.1080p.BluRay.x264-GRP",
            "Show S01E05 PROPER 720p HDTV x264-XYZ",
            "Film.Title.EXTENDED.DIRECTORS.CUT.2019.2160p.WEB-DL",
            "[Release.Group].Anime.Title.E01.[1080p].mkv"
        )

        dirtyTitles.forEach { title ->
            val vod = XtreamVodItem(id = 1, name = title)
            val raw = vod.toRawMediaMetadata()
            
            // Verify title is EXACTLY as provided - NO cleaning
            assertEquals("Title should be passed through without cleaning", title, raw.originalTitle)
        }
    }

    @Test
    fun `toRawMediaMetadata creates stable sourceId for same input`() {
        val vod1 = XtreamVodItem(id = 123, name = "Movie A")
        val vod2 = XtreamVodItem(id = 123, name = "Movie A")
        
        val raw1 = vod1.toRawMediaMetadata()
        val raw2 = vod2.toRawMediaMetadata()
        
        // Same input should produce same sourceId (deterministic)
        assertEquals(raw1.sourceId, raw2.sourceId)
    }

    @Test
    fun `toRawMediaMetadata creates unique sourceId for different items`() {
        val vod1 = XtreamVodItem(id = 123, name = "Movie A")
        val vod2 = XtreamVodItem(id = 456, name = "Movie B")
        val episode = XtreamEpisode(
            id = 123,
            seriesId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Episode"
        )
        
        val raw1 = vod1.toRawMediaMetadata()
        val raw2 = vod2.toRawMediaMetadata()
        val raw3 = episode.toRawMediaMetadata()
        
        // Different items must have different sourceIds
        assert(raw1.sourceId != raw2.sourceId)
        assert(raw1.sourceId != raw3.sourceId)
        assert(raw2.sourceId != raw3.sourceId)
    }

    @Test
    fun `ExternalIds defaults are empty when not provided`() {
        val vod = XtreamVodItem(id = 1, name = "Test")
        val raw = vod.toRawMediaMetadata()
        
        val ids = raw.externalIds
        assertNull(ids.tmdbId)
        assertNull(ids.imdbId)
        assertNull(ids.tvdbId)
    }
}
