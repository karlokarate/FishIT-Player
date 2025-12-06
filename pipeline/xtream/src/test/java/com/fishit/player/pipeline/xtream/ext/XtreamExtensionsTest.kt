package com.fishit.player.pipeline.xtream.ext

import com.fishit.player.core.model.PlaybackType
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for Xtream extension functions.
 *
 * These tests verify that conversion from Xtream models to PlaybackContext
 * works correctly and produces expected output.
 */
class XtreamExtensionsTest {
    @Test
    fun `XtreamVodItem toPlaybackContext creates valid VOD context`() {
        val vodItem =
            XtreamVodItem(
                id = 123,
                name = "Test Movie",
                streamUrl = "http://example.com/stream.m3u8",
                posterUrl = "http://example.com/poster.jpg",
                description = "Test description",
                rating = "PG-13",
                year = 2024,
                durationSeconds = 7200,
                categoryId = 10,
            )

        val context = vodItem.toPlaybackContext()

        assertEquals("Type should be VOD", PlaybackType.VOD, context.type)
        assertEquals("URI should match stream URL", "http://example.com/stream.m3u8", context.uri)
        assertEquals("Title should match name", "Test Movie", context.title)
        assertEquals("Content ID should be formatted correctly", "xtream-vod-123", context.contentId)
        assertEquals("Poster URL should match", "http://example.com/poster.jpg", context.posterUrl)
        assertEquals("VOD ID should be set", 123L, context.vodId)
    }

    @Test
    fun `XtreamEpisode toPlaybackContext creates valid SERIES context`() {
        val episode =
            XtreamEpisode(
                id = 456,
                seriesId = 789,
                seasonNumber = 2,
                episodeNumber = 5,
                title = "Test Episode",
                streamUrl = "http://example.com/episode.m3u8",
                posterUrl = "http://example.com/episode-thumb.jpg",
                description = "Episode description",
                airDate = "2024-01-15",
                durationSeconds = 3600,
            )

        val context = episode.toPlaybackContext()

        assertEquals("Type should be SERIES", PlaybackType.SERIES, context.type)
        assertEquals("URI should match stream URL", "http://example.com/episode.m3u8", context.uri)
        assertEquals("Title should match", "Test Episode", context.title)
        assertEquals("Content ID should be formatted correctly", "xtream-episode-456", context.contentId)
        assertEquals("Poster URL should match", "http://example.com/episode-thumb.jpg", context.posterUrl)
        assertEquals("Series ID should be set", 789L, context.seriesId)
        assertEquals("Season should be set", 2, context.season)
        assertEquals("Episode should be set", 5, context.episode)
    }

    @Test
    fun `XtreamChannel toPlaybackContext creates valid LIVE context`() {
        val channel =
            XtreamChannel(
                id = 321,
                name = "Test Channel",
                streamUrl = "http://example.com/live.m3u8",
                logoUrl = "http://example.com/logo.png",
                epgChannelId = "test-epg-id",
                categoryId = 20,
                streamType = "hls",
            )

        val context = channel.toPlaybackContext()

        assertEquals("Type should be LIVE", PlaybackType.LIVE, context.type)
        assertEquals("URI should match stream URL", "http://example.com/live.m3u8", context.uri)
        assertEquals("Title should match name", "Test Channel", context.title)
        assertEquals("Content ID should be formatted correctly", "xtream-live-321", context.contentId)
        assertEquals("Poster URL should match logo URL", "http://example.com/logo.png", context.posterUrl)
        assertEquals("Live channel ID should be set", 321L, context.liveChannelId)
    }

    @Test
    fun `toPlaybackContext handles null optional fields gracefully`() {
        val vodItem =
            XtreamVodItem(
                id = 999,
                name = "Minimal VOD",
                streamUrl = "http://example.com/minimal.m3u8",
                posterUrl = null,
                description = null,
                rating = null,
                year = null,
                durationSeconds = null,
                categoryId = null,
            )

        val context = vodItem.toPlaybackContext()

        assertNotNull("Context should be created", context)
        assertEquals("Type should be VOD", PlaybackType.VOD, context.type)
        assertEquals("URI should be set", "http://example.com/minimal.m3u8", context.uri)
        assertEquals("VOD ID should be set", 999L, context.vodId)
    }
}
