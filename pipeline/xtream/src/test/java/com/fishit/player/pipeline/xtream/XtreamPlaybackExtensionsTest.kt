package com.fishit.player.pipeline.xtream.model

import com.fishit.player.core.model.PlaybackType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for Xtream model to PlaybackContext conversion extensions.
 */
class XtreamPlaybackExtensionsTest {
    @Test
    fun `XtreamVodItem toPlaybackContext creates correct context`() {
        val vodItem =
            XtreamVodItem(
                id = 123,
                name = "Test Movie",
                streamIcon = "http://example.com/poster.jpg",
                categoryId = "movies",
                containerExtension = "mp4",
            )

        val context =
            vodItem.toPlaybackContext(
                uri = "http://example.com/stream.mp4",
                profileId = 999L,
                startPositionMs = 5000L,
            )

        assertEquals("Type should be VOD", PlaybackType.VOD, context.type)
        assertEquals("URI should match", "http://example.com/stream.mp4", context.uri)
        assertEquals("Title should match", "Test Movie", context.title)
        assertEquals("Poster should match", "http://example.com/poster.jpg", context.posterUrl)
        assertEquals("Content ID should match", "123", context.contentId)
        assertEquals("Profile ID should match", 999L, context.profileId)
        assertEquals("Start position should match", 5000L, context.startPositionMs)
        assertEquals("Category ID extra should match", "movies", context.extras["categoryId"])
        assertEquals(
            "Container extension extra should match",
            "mp4",
            context.extras["containerExtension"],
        )
    }

    @Test
    fun `XtreamEpisode toPlaybackContext creates correct context`() {
        val episode =
            XtreamEpisode(
                id = 456,
                seriesId = 789,
                seasonNumber = 2,
                episodeNumber = 5,
                title = "Test Episode",
                containerExtension = "mkv",
                thumbnail = "http://example.com/thumb.jpg",
            )

        val context =
            episode.toPlaybackContext(
                uri = "http://example.com/episode.mkv",
                profileId = 888L,
                startPositionMs = 10000L,
            )

        assertEquals("Type should be SERIES", PlaybackType.SERIES, context.type)
        assertEquals("URI should match", "http://example.com/episode.mkv", context.uri)
        assertEquals("Title should match", "Test Episode", context.title)
        assertEquals("Poster should match", "http://example.com/thumb.jpg", context.posterUrl)
        assertEquals("Content ID should match", "456", context.contentId)
        assertEquals("Series ID should match", "789", context.seriesId)
        assertEquals("Season number should match", 2, context.seasonNumber)
        assertEquals("Episode number should match", 5, context.episodeNumber)
        assertEquals("Profile ID should match", 888L, context.profileId)
        assertEquals("Start position should match", 10000L, context.startPositionMs)
    }

    @Test
    fun `XtreamChannel toPlaybackContext creates correct context`() {
        val channel =
            XtreamChannel(
                id = 101,
                name = "Test Channel",
                streamIcon = "http://example.com/logo.png",
                epgChannelId = "epg-101",
                tvArchive = 1,
                categoryId = "entertainment",
            )

        val context =
            channel.toPlaybackContext(
                uri = "http://example.com/channel.m3u8",
                profileId = 777L,
            )

        assertEquals("Type should be LIVE", PlaybackType.LIVE, context.type)
        assertEquals("URI should match", "http://example.com/channel.m3u8", context.uri)
        assertEquals("Title should match", "Test Channel", context.title)
        assertEquals("Poster should match", "http://example.com/logo.png", context.posterUrl)
        assertEquals("Content ID should match", "101", context.contentId)
        assertEquals("Profile ID should match", 777L, context.profileId)
        assertEquals("Start position should be 0", 0L, context.startPositionMs)
        assertEquals("EPG channel ID extra should match", "epg-101", context.extras["epgChannelId"])
        assertEquals("TV archive extra should match", "1", context.extras["tvArchive"])
        assertEquals(
            "Category ID extra should match",
            "entertainment",
            context.extras["categoryId"],
        )
    }
}
