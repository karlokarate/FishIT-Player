package com.fishit.player.pipeline.telegram.model

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for TelegramRawMetadataExtensions.
 *
 * Verifies that TelegramMediaItem correctly converts to RawMediaMetadata
 * per MEDIA_NORMALIZATION_CONTRACT.md requirements.
 */
class TelegramRawMetadataExtensionsTest {

    @Test
    fun `toRawMediaMetadata uses title as first priority`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            mediaType = TelegramMediaType.VIDEO,
            title = "The Movie Title",
            episodeTitle = "Episode Name",
            caption = "Some caption",
            fileName = "movie.mkv"
        )

        val raw = item.toRawMediaMetadata()

        assertEquals("The Movie Title", raw.originalTitle)
    }

    @Test
    fun `toRawMediaMetadata falls back to episodeTitle when title is blank`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            mediaType = TelegramMediaType.VIDEO,
            title = "",
            episodeTitle = "Episode Name",
            caption = "Some caption",
            fileName = "movie.mkv"
        )

        val raw = item.toRawMediaMetadata()

        assertEquals("Episode Name", raw.originalTitle)
    }

    @Test
    fun `toRawMediaMetadata falls back to caption when title and episodeTitle are blank`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            mediaType = TelegramMediaType.VIDEO,
            title = "",
            episodeTitle = null,
            caption = "Some caption",
            fileName = "movie.mkv"
        )

        val raw = item.toRawMediaMetadata()

        assertEquals("Some caption", raw.originalTitle)
    }

    @Test
    fun `toRawMediaMetadata falls back to fileName when others are blank`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            mediaType = TelegramMediaType.VIDEO,
            title = "",
            caption = "",
            fileName = "Movie.2020.1080p.BluRay.x264-GROUP.mkv"
        )

        val raw = item.toRawMediaMetadata()

        // Critical: fileName passed through AS-IS, no cleaning
        assertEquals("Movie.2020.1080p.BluRay.x264-GROUP.mkv", raw.originalTitle)
    }

    @Test
    fun `toRawMediaMetadata uses fallback for empty item`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 789L,
            mediaType = TelegramMediaType.VIDEO
        )

        val raw = item.toRawMediaMetadata()

        assertEquals("Untitled Media 789", raw.originalTitle)
    }

    @Test
    fun `toRawMediaMetadata sets correct mediaType for series`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            mediaType = TelegramMediaType.VIDEO,
            title = "Breaking Bad S01E05",
            isSeries = true,
            seasonNumber = 1,
            episodeNumber = 5
        )

        val raw = item.toRawMediaMetadata()

        assertEquals(MediaType.SERIES_EPISODE, raw.mediaType)
        assertEquals(1, raw.season)
        assertEquals(5, raw.episode)
    }

    @Test
    fun `toRawMediaMetadata sets MOVIE for regular video`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            mediaType = TelegramMediaType.VIDEO,
            title = "Some Movie"
        )

        val raw = item.toRawMediaMetadata()

        assertEquals(MediaType.MOVIE, raw.mediaType)
    }

    @Test
    fun `toRawMediaMetadata sets MUSIC for audio`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            mediaType = TelegramMediaType.AUDIO,
            title = "Song Title"
        )

        val raw = item.toRawMediaMetadata()

        assertEquals(MediaType.MUSIC, raw.mediaType)
    }

    @Test
    fun `toRawMediaMetadata calculates durationMinutes from seconds`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            mediaType = TelegramMediaType.VIDEO,
            title = "Movie",
            durationSecs = 7200 // 2 hours
        )

        val raw = item.toRawMediaMetadata()

        assertEquals(120, raw.durationMinutes)
    }

    @Test
    fun `toRawMediaMetadata uses remoteId as sourceId when available`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            mediaType = TelegramMediaType.VIDEO,
            title = "Movie",
            remoteId = "stable-remote-id-xyz"
        )

        val raw = item.toRawMediaMetadata()

        assertEquals("stable-remote-id-xyz", raw.sourceId)
    }

    @Test
    fun `toRawMediaMetadata builds sourceId from chat and message when no remoteId`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            mediaType = TelegramMediaType.VIDEO,
            title = "Movie",
            remoteId = null
        )

        val raw = item.toRawMediaMetadata()

        assertEquals("msg:123:456", raw.sourceId)
    }

    @Test
    fun `toRawMediaMetadata always has TELEGRAM sourceType`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            title = "Test"
        )

        val raw = item.toRawMediaMetadata()

        assertEquals(SourceType.TELEGRAM, raw.sourceType)
    }

    @Test
    fun `toRawMediaMetadata leaves externalIds empty`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            title = "Test"
        )

        val raw = item.toRawMediaMetadata()

        // Per contract: Telegram doesn't provide TMDB/IMDB/TVDB
        assertNull(raw.externalIds.tmdbId)
        assertNull(raw.externalIds.imdbId)
        assertNull(raw.externalIds.tvdbId)
    }

    @Test
    fun `toRawMediaMetadata uses seriesName in sourceLabel when available`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            title = "Episode",
            seriesName = "Breaking Bad"
        )

        val raw = item.toRawMediaMetadata()

        assertEquals("Telegram: Breaking Bad", raw.sourceLabel)
    }

    @Test
    fun `toRawMediaMetadata falls back to chatId in sourceLabel`() {
        val item = TelegramMediaItem(
            chatId = 123456789L,
            messageId = 456L,
            title = "Movie"
        )

        val raw = item.toRawMediaMetadata()

        assertEquals("Telegram Chat: 123456789", raw.sourceLabel)
    }

    @Test
    fun `toRawMediaMetadata preserves year from source`() {
        val item = TelegramMediaItem(
            chatId = 123L,
            messageId = 456L,
            title = "Movie",
            year = 2020
        )

        val raw = item.toRawMediaMetadata()

        assertEquals(2020, raw.year)
    }
}
