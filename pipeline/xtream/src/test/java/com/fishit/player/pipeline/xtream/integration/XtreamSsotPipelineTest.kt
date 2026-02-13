package com.fishit.player.pipeline.xtream.integration

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.util.DurationParser
import com.fishit.player.core.model.util.EpochConverter
import com.fishit.player.core.model.util.RatingNormalizer
import com.fishit.player.infra.transport.xtream.XtreamEpisodeInfo
import com.fishit.player.infra.transport.xtream.XtreamEpisodeInfoBlock
import com.fishit.player.infra.transport.xtream.XtreamLiveStream
import com.fishit.player.infra.transport.xtream.XtreamMovieData
import com.fishit.player.infra.transport.xtream.XtreamSeriesInfo
import com.fishit.player.infra.transport.xtream.XtreamSeriesStream
import com.fishit.player.infra.transport.xtream.XtreamVodInfo
import com.fishit.player.infra.transport.xtream.XtreamVodInfoBlock
import com.fishit.player.infra.transport.xtream.XtreamVodStream
import com.fishit.player.pipeline.xtream.adapter.toPipelineItem
import com.fishit.player.pipeline.xtream.adapter.toEpisodes
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata
import com.fishit.player.pipeline.xtream.mapper.toRawMetadata
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end pipeline tests that validate transport DTO → pipeline DTO → RawMediaMetadata.
 *
 * These tests exercise the FULL chain including:
 * - XtreamPipelineAdapter extensions (toPipelineItem / toEpisodes)
 * - XtreamRawMetadataExtensions (toRawMetadata / toRawMediaMetadata)
 * - All referenced SSOT utilities: EpochConverter, RatingNormalizer, DurationParser, YearParser
 *
 * Unlike existing integration tests that manually construct pipeline DTOs (bypassing the adapter),
 * these tests start from actual transport DTOs to validate realistic data flow.
 */
class XtreamSsotPipelineTest {

    // =========================================================================
    // VOD: Transport → Pipeline → RawMediaMetadata
    // =========================================================================

    @Test
    fun `VOD - epoch seconds converted to milliseconds through full chain`() {
        val transportDto = XtreamVodStream(
            streamId = 12345,
            name = "The Matrix",
            added = "1706745600", // 2024-02-01 00:00:00 UTC in seconds
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertEquals(1706745600_000L, pipelineDto.added, "Adapter must convert seconds to ms")

        val raw = pipelineDto.toRawMetadata()
        assertEquals(1706745600_000L, raw.addedTimestamp, "Mapper must pass through ms timestamp")
        assertEquals(1706745600_000L, raw.lastModifiedTimestamp, "lastModified must mirror added")
    }

    @Test
    fun `VOD - rating5Based fallback when rating is null`() {
        val transportDto = XtreamVodStream(
            streamId = 100,
            name = "Low-Budget Movie",
            rating = null,
            rating5Based = 3.75,
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertNull(pipelineDto.rating, "Adapter passes null rating through")
        assertEquals(3.75, pipelineDto.rating5Based, "Adapter passes rating5Based through")

        val raw = pipelineDto.toRawMetadata()
        assertEquals(7.5, raw.rating, "Mapper must normalize 3.75 * 2 = 7.5 via RatingNormalizer")
    }

    @Test
    fun `VOD - raw rating preferred over rating5Based`() {
        val transportDto = XtreamVodStream(
            streamId = 101,
            name = "Top Movie",
            rating = "8.2",
            rating5Based = 4.1,
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertEquals(8.2, pipelineDto.rating, "Adapter converts rating string to Double")
        assertEquals(4.1, pipelineDto.rating5Based)

        val raw = pipelineDto.toRawMetadata()
        assertEquals(8.2, raw.rating, "Mapper must prefer raw 0-10 rating over normalized 5-based")
    }

    @Test
    fun `VOD - invalid year filtered by YearParser`() {
        val transportDto = XtreamVodStream(
            streamId = 102,
            name = "Some Movie",
            year = "0",
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertEquals("0", pipelineDto.year, "Adapter passes year through unchanged")

        val raw = pipelineDto.toRawMetadata()
        assertNull(raw.year, "Mapper must filter '0' via YearParser.validate()")
    }

    @Test
    fun `VOD - valid year passes through`() {
        val transportDto = XtreamVodStream(
            streamId = 103,
            name = "New Movie",
            year = "2023",
        )

        val raw = transportDto.toPipelineItem().toRawMetadata()
        assertEquals(2023, raw.year)
    }

    @Test
    fun `VOD - duration parsed via DurationParser`() {
        val transportDto = XtreamVodStream(
            streamId = 104,
            name = "Long Movie",
            duration = "01:30:00",
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertEquals("01:30:00", pipelineDto.duration, "Adapter passes duration string through")

        val raw = pipelineDto.toRawMetadata()
        assertEquals(5_400_000L, raw.durationMs, "Mapper must parse HH:MM:SS via DurationParser")
    }

    @Test
    fun `VOD - duration in minutes format`() {
        val transportDto = XtreamVodStream(
            streamId = 105,
            name = "Short Film",
            duration = "90",
        )

        val raw = transportDto.toPipelineItem().toRawMetadata()
        assertEquals(
            DurationParser.parseToMs("90"),
            raw.durationMs,
            "Plain number → minutes → ms"
        )
    }

    @Test
    fun `VOD - isAdult flag converted from string`() {
        val adultDto = XtreamVodStream(
            streamId = 200,
            name = "Adult Content",
            isAdult = "1",
        )
        val normalDto = XtreamVodStream(
            streamId = 201,
            name = "Normal Content",
            isAdult = "0",
        )
        val nullDto = XtreamVodStream(
            streamId = 202,
            name = "Unknown Content",
            isAdult = null,
        )

        assertTrue(adultDto.toPipelineItem().isAdult, "'1' → true")
        assertEquals(false, normalDto.toPipelineItem().isAdult, "'0' → false")
        assertEquals(false, nullDto.toPipelineItem().isAdult, "null → false")

        // Verify it propagates to RawMediaMetadata
        assertTrue(adultDto.toPipelineItem().toRawMetadata().isAdult)
    }

    @Test
    fun `VOD - null added timestamp handled gracefully`() {
        val transportDto = XtreamVodStream(
            streamId = 300,
            name = "No Timestamp",
            added = null,
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertNull(pipelineDto.added)

        val raw = pipelineDto.toRawMetadata()
        assertNull(raw.addedTimestamp)
        assertNull(raw.lastModifiedTimestamp)
    }

    @Test
    fun `VOD - playbackHints include all expected fields`() {
        val transportDto = XtreamVodStream(
            streamId = 400,
            name = "Full VOD",
            containerExtension = "mkv",
            categoryId = "5",
            streamType = "movie",
        )

        val raw = transportDto.toPipelineItem().toRawMetadata()
        val hints = raw.playbackHints

        assertEquals(PlaybackHintKeys.Xtream.CONTENT_VOD, hints[PlaybackHintKeys.Xtream.CONTENT_TYPE])
        assertEquals("400", hints[PlaybackHintKeys.Xtream.VOD_ID])
        assertEquals("mkv", hints[PlaybackHintKeys.Xtream.CONTAINER_EXT])
        assertEquals("movie", hints[PlaybackHintKeys.Xtream.VOD_KIND])
    }

    @Test
    fun `VOD - sourceId uses XtreamIdCodec format`() {
        val transportDto = XtreamVodStream(
            streamId = 500,
            name = "Codec Test",
        )

        val raw = transportDto.toPipelineItem().toRawMetadata()
        assertEquals("xtream:vod:500", raw.sourceId)
        assertEquals(SourceType.XTREAM, raw.sourceType)
        assertEquals(MediaType.MOVIE, raw.mediaType)
    }

    @Test
    fun `VOD - both rating paths produce same result when identical`() {
        // When rating = "7.5" and rating5Based = 3.75, both represent the same value.
        // The mapper should use the raw rating (7.5), not re-normalize.
        val transportDto = XtreamVodStream(
            streamId = 600,
            name = "Consistent Rating",
            rating = "7.5",
            rating5Based = 3.75,
        )

        val raw = transportDto.toPipelineItem().toRawMetadata()
        assertEquals(7.5, raw.rating, "Should use raw rating, not normalize 3.75 again")
    }

    // =========================================================================
    // SERIES: Transport → Pipeline → RawMediaMetadata
    // =========================================================================

    @Test
    fun `Series - epoch lastModified converted to ms`() {
        val transportDto = XtreamSeriesStream(
            seriesId = 1000,
            name = "Breaking Bad",
            lastModified = "1706745600",
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertEquals(1706745600_000L, pipelineDto.lastModified, "Adapter converts lastModified seconds → ms")

        val raw = pipelineDto.toRawMetadata()
        assertEquals(1706745600_000L, raw.addedTimestamp, "Series uses lastModified as addedTimestamp")
        assertEquals(1706745600_000L, raw.lastModifiedTimestamp)
    }

    @Test
    fun `Series - rating resolved in adapter via RatingNormalizer`() {
        val withRawRating = XtreamSeriesStream(
            seriesId = 1001,
            name = "Series A",
            rating = "8.5",
            rating5Based = 4.25,
        )

        val pipelineDto = withRawRating.toPipelineItem()
        assertEquals(8.5, pipelineDto.rating, "Adapter resolves: prefer raw rating")

        val raw = pipelineDto.toRawMetadata()
        assertEquals(8.5, raw.rating)
    }

    @Test
    fun `Series - rating5Based fallback in adapter`() {
        val with5Based = XtreamSeriesStream(
            seriesId = 1002,
            name = "Series B",
            rating = null,
            rating5Based = 3.5,
        )

        val pipelineDto = with5Based.toPipelineItem()
        assertEquals(7.0, pipelineDto.rating, "Adapter normalizes: 3.5 * 2 = 7.0")

        val raw = pipelineDto.toRawMetadata()
        assertEquals(7.0, raw.rating)
    }

    @Test
    fun `Series - year from releaseDate when year field missing`() {
        val transportDto = XtreamSeriesStream(
            seriesId = 1003,
            name = "Gotham",
            year = null,
            releaseDate = "2014-09-21",
        )

        val pipelineDto = transportDto.toPipelineItem()
        // resolvedYear on transport DTO extracts "2014" from releaseDate
        assertEquals("2014", pipelineDto.year, "Adapter uses resolvedYear from XtreamSeriesStream")

        val raw = pipelineDto.toRawMetadata()
        assertEquals(2014, raw.year, "Mapper validates year via YearParser")
    }

    @Test
    fun `Series - year field preferred over releaseDate`() {
        val transportDto = XtreamSeriesStream(
            seriesId = 1004,
            name = "Show X",
            year = "2020",
            releaseDate = "2019-06-15",
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertEquals("2020", pipelineDto.year, "Year field takes priority over releaseDate")

        val raw = pipelineDto.toRawMetadata()
        assertEquals(2020, raw.year)
    }

    @Test
    fun `Series - invalid year filtered`() {
        val transportDto = XtreamSeriesStream(
            seriesId = 1005,
            name = "Bad Year Series",
            year = "N/A",
        )

        val raw = transportDto.toPipelineItem().toRawMetadata()
        assertNull(raw.year, "YearParser.validate('N/A') → null")
    }

    @Test
    fun `Series - episodeRunTime converted to ms via DurationParser`() {
        val transportDto = XtreamSeriesStream(
            seriesId = 1006,
            name = "Hour-Long Show",
            episodeRunTime = "45",
        )

        val raw = transportDto.toPipelineItem().toRawMetadata()
        assertEquals(
            DurationParser.minutesToMs(45),
            raw.durationMs,
            "episodeRunTime in minutes → minutesToMs"
        )
        assertEquals(2_700_000L, raw.durationMs)
    }

    @Test
    fun `Series - sourceId and mediaType correct`() {
        val transportDto = XtreamSeriesStream(
            seriesId = 1007,
            name = "Test Series",
        )

        val raw = transportDto.toPipelineItem().toRawMetadata()
        assertEquals("xtream:series:1007", raw.sourceId)
        assertEquals(MediaType.SERIES, raw.mediaType)
        assertEquals(SourceType.XTREAM, raw.sourceType)
    }

    @Test
    fun `Series - isAdult from string`() {
        val adultSeries = XtreamSeriesStream(
            seriesId = 1008,
            name = "Adult Series",
            isAdult = "1",
        )

        assertTrue(adultSeries.toPipelineItem().isAdult)
        assertTrue(adultSeries.toPipelineItem().toRawMetadata().isAdult)
    }

    @Test
    fun `Series - rich metadata passes through`() {
        val transportDto = XtreamSeriesStream(
            seriesId = 1009,
            name = "Rich Series",
            plot = "A great series about testing.",
            cast = "Actor A, Actor B",
            director = "Director X",
            genre = "Drama, Thriller",
            youtubeTrailer = "dQw4w9WgXcQ",
        )

        val raw = transportDto.toPipelineItem().toRawMetadata()
        assertEquals("A great series about testing.", raw.plot)
        assertEquals("Actor A, Actor B", raw.cast)
        assertEquals("Director X", raw.director)
        assertEquals("Drama, Thriller", raw.genres)
        assertEquals("dQw4w9WgXcQ", raw.trailer)
    }

    // =========================================================================
    // EPISODE: Transport → Pipeline → RawMediaMetadata
    // =========================================================================

    @Test
    fun `Episode - durationSecs preferred over duration string`() {
        val seriesInfo = XtreamSeriesInfo(
            episodes = mapOf(
                "1" to listOf(
                    XtreamEpisodeInfo(
                        id = 5001,
                        episodeNum = 1,
                        title = "Pilot",
                        containerExtension = "mp4",
                        added = "1706745600",
                        info = XtreamEpisodeInfoBlock(
                            durationSecs = 2700, // 45 minutes in seconds
                            duration = "45:00",  // 45 minutes as string (should be ignored)
                        ),
                    ),
                ),
            ),
        )

        val episodes = seriesInfo.toEpisodes(seriesId = 500, seriesName = "Test Series")
        assertEquals(1, episodes.size)

        val episode = episodes[0]
        assertEquals(2700, episode.durationSecs, "Adapter passes durationSecs through")
        assertEquals("45:00", episode.duration, "Adapter passes duration string through")

        val raw = episode.toRawMediaMetadata()
        // DurationParser.resolve(2700, "45:00") should prefer numeric → 2700 * 1000
        assertEquals(2_700_000L, raw.durationMs, "Must prefer durationSecs via DurationParser.resolve()")
    }

    @Test
    fun `Episode - falls back to duration string when durationSecs is null`() {
        val seriesInfo = XtreamSeriesInfo(
            episodes = mapOf(
                "1" to listOf(
                    XtreamEpisodeInfo(
                        id = 5002,
                        episodeNum = 2,
                        title = "Episode 2",
                        info = XtreamEpisodeInfoBlock(
                            durationSecs = null,
                            duration = "01:15:00", // 75 minutes
                        ),
                    ),
                ),
            ),
        )

        val episodes = seriesInfo.toEpisodes(seriesId = 500, seriesName = "Test")
        val raw = episodes[0].toRawMediaMetadata()
        assertEquals(4_500_000L, raw.durationMs, "Fallback to DurationParser.parseToMs(\"01:15:00\")")
    }

    @Test
    fun `Episode - epoch added converted to ms in adapter`() {
        val seriesInfo = XtreamSeriesInfo(
            episodes = mapOf(
                "1" to listOf(
                    XtreamEpisodeInfo(
                        id = 5003,
                        episodeNum = 3,
                        title = "Episode 3",
                        added = "1706745600",
                    ),
                ),
            ),
        )

        val episodes = seriesInfo.toEpisodes(seriesId = 500, seriesName = "Test")
        val episode = episodes[0]
        assertEquals(1706745600_000L, episode.added, "Adapter converts epoch seconds to ms")

        val raw = episode.toRawMediaMetadata()
        assertEquals(1706745600_000L, raw.addedTimestamp)
        assertEquals(1706745600_000L, raw.lastModifiedTimestamp)
    }

    @Test
    fun `Episode - season and episode numbers from API structure`() {
        val seriesInfo = XtreamSeriesInfo(
            episodes = mapOf(
                "3" to listOf(
                    XtreamEpisodeInfo(
                        id = 5010,
                        episodeNum = 7,
                        title = "The Fly",
                    ),
                ),
            ),
        )

        val episodes = seriesInfo.toEpisodes(seriesId = 500, seriesName = "Breaking Bad")
        val raw = episodes[0].toRawMediaMetadata()

        assertEquals(3, raw.season)
        assertEquals(7, raw.episode)
        assertEquals(MediaType.SERIES_EPISODE, raw.mediaType)
    }

    @Test
    fun `Episode - seriesName falls back when title is blank`() {
        val seriesInfo = XtreamSeriesInfo(
            episodes = mapOf(
                "1" to listOf(
                    XtreamEpisodeInfo(
                        id = 5011,
                        episodeNum = 1,
                        title = "",
                    ),
                ),
            ),
        )

        val episodes = seriesInfo.toEpisodes(seriesId = 500, seriesName = "My Series")
        val raw = episodes[0].toRawMediaMetadata()
        assertEquals("My Series", raw.originalTitle, "Blank title falls back to seriesName")
    }

    @Test
    fun `Episode - playbackHints include all critical fields`() {
        val seriesInfo = XtreamSeriesInfo(
            episodes = mapOf(
                "2" to listOf(
                    XtreamEpisodeInfo(
                        id = 5020,
                        episodeNum = 5,
                        title = "Test Episode",
                        containerExtension = "mkv",
                    ),
                ),
            ),
        )

        val episodes = seriesInfo.toEpisodes(seriesId = 600, seriesName = "Series X")
        val raw = episodes[0].toRawMediaMetadata(seriesKind = "series")
        val hints = raw.playbackHints

        assertEquals(PlaybackHintKeys.Xtream.CONTENT_SERIES, hints[PlaybackHintKeys.Xtream.CONTENT_TYPE])
        assertEquals("600", hints[PlaybackHintKeys.Xtream.SERIES_ID])
        assertEquals("2", hints[PlaybackHintKeys.Xtream.SEASON_NUMBER])
        assertEquals("5", hints[PlaybackHintKeys.Xtream.EPISODE_NUMBER])
        assertEquals("5020", hints[PlaybackHintKeys.Xtream.EPISODE_ID])
        assertEquals("mkv", hints[PlaybackHintKeys.Xtream.CONTAINER_EXT])
        assertEquals("series", hints[PlaybackHintKeys.Xtream.SERIES_KIND])
    }

    @Test
    fun `Episode - video and audio codec info propagated`() {
        val seriesInfo = XtreamSeriesInfo(
            episodes = mapOf(
                "1" to listOf(
                    XtreamEpisodeInfo(
                        id = 5030,
                        episodeNum = 1,
                        title = "HD Episode",
                        info = XtreamEpisodeInfoBlock(
                            video = com.fishit.player.infra.transport.xtream.XtreamVideoInfo(
                                codec = "hevc",
                                width = 1920,
                                height = 1080,
                            ),
                            audio = com.fishit.player.infra.transport.xtream.XtreamAudioInfo(
                                codec = "aac",
                                channels = 6,
                            ),
                            bitrate = 5000,
                        ),
                    ),
                ),
            ),
        )

        val episodes = seriesInfo.toEpisodes(seriesId = 700, seriesName = "Codec Series")
        val episode = episodes[0]
        assertEquals("hevc", episode.videoCodec)
        assertEquals(1920, episode.videoWidth)
        assertEquals(1080, episode.videoHeight)
        assertEquals("aac", episode.audioCodec)
        assertEquals(6, episode.audioChannels)
        assertEquals(5000, episode.bitrate)

        val raw = episode.toRawMediaMetadata()
        val hints = raw.playbackHints
        assertEquals("hevc", hints[PlaybackHintKeys.VIDEO_CODEC])
        assertEquals("1920", hints[PlaybackHintKeys.VIDEO_WIDTH])
        assertEquals("1080", hints[PlaybackHintKeys.VIDEO_HEIGHT])
        assertEquals("aac", hints[PlaybackHintKeys.AUDIO_CODEC])
        assertEquals("6", hints[PlaybackHintKeys.AUDIO_CHANNELS])
        assertEquals("5000", hints[PlaybackHintKeys.Xtream.BITRATE])
    }

    @Test
    fun `Episode - resolvedEpisodeId prefers episodeId over id`() {
        // XtreamEpisodeInfo has both id and episodeId fields. resolvedEpisodeId prefers episodeId.
        val seriesInfo = XtreamSeriesInfo(
            episodes = mapOf(
                "1" to listOf(
                    XtreamEpisodeInfo(
                        id = 100,
                        episodeId = 999, // KönigTV/XUI format
                        episodeNum = 1,
                        title = "Pilot",
                    ),
                ),
            ),
        )

        val episodes = seriesInfo.toEpisodes(seriesId = 800, seriesName = "Test")
        assertEquals(999, episodes[0].id, "resolvedEpisodeId should prefer episodeId")
    }

    // =========================================================================
    // LIVE: Transport → Pipeline → RawMediaMetadata
    // =========================================================================

    @Test
    fun `Live - epoch added converted to ms`() {
        val transportDto = XtreamLiveStream(
            streamId = 2000,
            name = "BBC One",
            added = "1706745600",
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertEquals(1706745600_000L, pipelineDto.added)

        val raw = pipelineDto.toRawMediaMetadata()
        assertEquals(1706745600_000L, raw.addedTimestamp)
        assertEquals(1706745600_000L, raw.lastModifiedTimestamp)
    }

    @Test
    fun `Live - isAdult flag and directSource`() {
        val transportDto = XtreamLiveStream(
            streamId = 2001,
            name = "Adult Channel",
            isAdult = "1",
            directSource = "http://example.com/live/stream.m3u8",
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertTrue(pipelineDto.isAdult)
        assertEquals("http://example.com/live/stream.m3u8", pipelineDto.directSource)

        val raw = pipelineDto.toRawMediaMetadata()
        assertTrue(raw.isAdult)
        assertEquals(
            "http://example.com/live/stream.m3u8",
            raw.playbackHints[PlaybackHintKeys.Xtream.DIRECT_SOURCE],
        )
    }

    @Test
    fun `Live - Unicode decorators cleaned from channel name`() {
        val transportDto = XtreamLiveStream(
            streamId = 2002,
            name = "▃ ▅ ▆ █ DE HEVC █ ▆ ▅ ▃",
        )

        val raw = transportDto.toPipelineItem().toRawMediaMetadata()
        assertEquals("DE HEVC", raw.originalTitle, "Unicode block decorators must be stripped")
    }

    @Test
    fun `Live - EPG and catchup fields propagated`() {
        val transportDto = XtreamLiveStream(
            streamId = 2003,
            name = "RTL HD",
            epgChannelId = "rtl.hd.de",
            tvArchive = 3,
            tvArchiveDuration = 7,
            categoryId = "10",
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertEquals("rtl.hd.de", pipelineDto.epgChannelId)
        assertEquals(3, pipelineDto.tvArchive)
        assertEquals(7, pipelineDto.tvArchiveDuration)

        val raw = pipelineDto.toRawMediaMetadata()
        assertEquals("rtl.hd.de", raw.epgChannelId)
        assertEquals(3, raw.tvArchive)
        assertEquals(7, raw.tvArchiveDuration)
        assertEquals(MediaType.LIVE, raw.mediaType)
        assertEquals("xtream:live:2003", raw.sourceId)
    }

    @Test
    fun `Live - playbackHints include streamType as liveKind`() {
        val transportDto = XtreamLiveStream(
            streamId = 2004,
            name = "Channel X",
            streamType = "live",
        )

        val raw = transportDto.toPipelineItem().toRawMediaMetadata()
        val hints = raw.playbackHints

        assertEquals(PlaybackHintKeys.Xtream.CONTENT_LIVE, hints[PlaybackHintKeys.Xtream.CONTENT_TYPE])
        assertEquals("2004", hints[PlaybackHintKeys.Xtream.STREAM_ID])
        assertEquals("live", hints[PlaybackHintKeys.Xtream.LIVE_KIND])
    }

    @Test
    fun `Live - resolvedId prefers streamId over id`() {
        val withStreamId = XtreamLiveStream(
            streamId = 3000,
            id = 9999,
            name = "Both IDs",
        )
        val withIdOnly = XtreamLiveStream(
            streamId = null,
            id = 5555,
            name = "ID Only",
        )

        assertEquals(3000, withStreamId.toPipelineItem().id)
        assertEquals(5555, withIdOnly.toPipelineItem().id)
    }

    // =========================================================================
    // VOD INFO (Detail): Transport DTO → RawMediaMetadata
    // =========================================================================

    @Test
    fun `VodInfo - durationSecs converted via DurationParser SSOT`() {
        val vodItem = XtreamVodStream(
            streamId = 4000,
            name = "Detail Movie",
            added = "1706745600",
        ).toPipelineItem()

        val vodInfo = XtreamVodInfo(
            info = XtreamVodInfoBlock(
                name = "Detail Movie - Extended",
                durationSecs = 7200, // 2 hours in seconds
            ),
            movieData = XtreamMovieData(
                streamId = 4000,
            ),
        )

        val raw = vodInfo.toRawMediaMetadata(vodItem)
        assertEquals(7_200_000L, raw.durationMs, "DurationParser.secondsToMs(7200)")
        assertEquals("Detail Movie - Extended", raw.originalTitle, "Info block title preferred")
    }

    @Test
    fun `VodInfo - rating resolved via RatingNormalizer SSOT`() {
        val vodItem = XtreamVodStream(
            streamId = 4001,
            name = "Rated Movie",
            rating = "6.0",
        ).toPipelineItem()

        val vodInfo = XtreamVodInfo(
            info = XtreamVodInfoBlock(
                rating = "8.5", // Detail API has better rating
                rating5Based = 4.25,
            ),
        )

        val raw = vodInfo.toRawMediaMetadata(vodItem)
        assertEquals(8.5, raw.rating, "RatingNormalizer.resolve prefers raw rating over 5-based")
    }

    @Test
    fun `VodInfo - rating5Based fallback when rating null`() {
        val vodItem = XtreamVodStream(
            streamId = 4002,
            name = "Fallback Rating Movie",
        ).toPipelineItem()

        val vodInfo = XtreamVodInfo(
            info = XtreamVodInfoBlock(
                rating = null,
                rating5Based = 3.0,
            ),
        )

        val raw = vodInfo.toRawMediaMetadata(vodItem)
        assertEquals(6.0, raw.rating, "RatingNormalizer: null raw → normalize5to10(3.0) = 6.0")
    }

    @Test
    fun `VodInfo - falls back to vodItem rating when info block has no rating`() {
        val vodItem = XtreamVodStream(
            streamId = 4003,
            name = "VodItem Rating Movie",
            rating = "7.0",
        ).toPipelineItem()

        val vodInfo = XtreamVodInfo(
            info = XtreamVodInfoBlock(
                rating = null,
                rating5Based = null,
            ),
        )

        val raw = vodInfo.toRawMediaMetadata(vodItem)
        assertEquals(7.0, raw.rating, "Falls back to vodItem.rating when info block has nothing")
    }

    @Test
    fun `VodInfo - movieData added as epoch seconds converted to ms`() {
        val vodItem = XtreamVodStream(
            streamId = 4004,
            name = "Timestamp Movie",
            added = "1706745600", // → 1706745600000 in pipeline DTO
        ).toPipelineItem()

        val vodInfo = XtreamVodInfo(
            movieData = XtreamMovieData(
                streamId = 4004,
                added = "1706832000", // Different timestamp than vodItem
            ),
        )

        val raw = vodInfo.toRawMediaMetadata(vodItem)
        assertEquals(
            EpochConverter.secondsToMs("1706832000"),
            raw.addedTimestamp,
            "movieData.added is epoch seconds → EpochConverter.secondsToMs"
        )
        assertEquals(1706832000_000L, raw.addedTimestamp)
    }

    @Test
    fun `VodInfo - falls back to vodItem added when movieData added is null`() {
        val vodItem = XtreamVodStream(
            streamId = 4005,
            name = "Fallback Timestamp",
            added = "1706745600",
        ).toPipelineItem()

        val vodInfo = XtreamVodInfo(
            movieData = XtreamMovieData(
                streamId = 4005,
                added = null,
            ),
        )

        val raw = vodInfo.toRawMediaMetadata(vodItem)
        assertEquals(1706745600_000L, raw.addedTimestamp, "Falls back to vodItem.added (already ms)")
    }

    @Test
    fun `VodInfo - rich metadata from info block`() {
        val vodItem = XtreamVodStream(streamId = 4006, name = "Rich").toPipelineItem()

        val vodInfo = XtreamVodInfo(
            info = XtreamVodInfoBlock(
                name = "Rich Movie",
                plot = "An amazing story about SSOT.",
                genre = "Sci-Fi, Action",
                director = "James Cameron",
                cast = "Arnold Schwarzenegger",
                releaseDate = "1984-10-26",
                tmdbId = "218",
                youtubeTrailer = "k64P4l2Wmeg",
            ),
        )

        val raw = vodInfo.toRawMediaMetadata(vodItem)
        assertEquals("Rich Movie", raw.originalTitle)
        assertEquals("An amazing story about SSOT.", raw.plot)
        assertEquals("Sci-Fi, Action", raw.genres)
        assertEquals("James Cameron", raw.director)
        assertEquals("Arnold Schwarzenegger", raw.cast)
        assertEquals("1984-10-26", raw.releaseDate)
        assertEquals("k64P4l2Wmeg", raw.trailer)
        assertNotNull(raw.externalIds.effectiveTmdbId)
    }

    // =========================================================================
    // CROSS-CUTTING: Verify SSOT consistency across all paths
    // =========================================================================

    @Test
    fun `Cross-cutting - same epoch value produces same ms across VOD, Series, Live, Episode`() {
        val epochSeconds = "1706745600"
        val expectedMs = 1706745600_000L

        // VOD
        val vod = XtreamVodStream(streamId = 9001, name = "V", added = epochSeconds)
            .toPipelineItem().toRawMetadata()
        assertEquals(expectedMs, vod.addedTimestamp, "VOD timestamp")

        // Series
        val series = XtreamSeriesStream(seriesId = 9002, name = "S", lastModified = epochSeconds)
            .toPipelineItem().toRawMetadata()
        assertEquals(expectedMs, series.addedTimestamp, "Series timestamp")

        // Live
        val live = XtreamLiveStream(streamId = 9003, name = "L", added = epochSeconds)
            .toPipelineItem().toRawMediaMetadata()
        assertEquals(expectedMs, live.addedTimestamp, "Live timestamp")

        // Episode
        val episodeInfo = XtreamSeriesInfo(
            episodes = mapOf(
                "1" to listOf(
                    XtreamEpisodeInfo(id = 9004, episodeNum = 1, title = "E", added = epochSeconds),
                ),
            ),
        )
        val ep = episodeInfo.toEpisodes(9000, "S")[0].toRawMediaMetadata()
        assertEquals(expectedMs, ep.addedTimestamp, "Episode timestamp")
    }

    @Test
    fun `Cross-cutting - null timestamps produce null across all paths`() {
        val vod = XtreamVodStream(streamId = 9010, name = "V").toPipelineItem().toRawMetadata()
        assertNull(vod.addedTimestamp, "VOD null timestamp")

        val series = XtreamSeriesStream(seriesId = 9011, name = "S")
            .toPipelineItem().toRawMetadata()
        assertNull(series.addedTimestamp, "Series null timestamp")

        val live = XtreamLiveStream(streamId = 9012, name = "L")
            .toPipelineItem().toRawMediaMetadata()
        assertNull(live.addedTimestamp, "Live null timestamp")
    }

    @Test
    fun `Cross-cutting - globalId empty across all paths (normalizer territory)`() {
        val vod = XtreamVodStream(streamId = 9020, name = "V").toPipelineItem().toRawMetadata()
        assertEquals("", vod.globalId)

        val series = XtreamSeriesStream(seriesId = 9021, name = "S").toPipelineItem().toRawMetadata()
        assertEquals("", series.globalId)

        val live = XtreamLiveStream(streamId = 9022, name = "L").toPipelineItem().toRawMediaMetadata()
        assertEquals("", live.globalId)
    }

    @Test
    fun `Cross-cutting - pipelineIdTag is XTREAM for all types`() {
        val vod = XtreamVodStream(streamId = 9030, name = "V").toPipelineItem().toRawMetadata()
        val series = XtreamSeriesStream(seriesId = 9031, name = "S").toPipelineItem().toRawMetadata()
        val live = XtreamLiveStream(streamId = 9032, name = "L").toPipelineItem().toRawMediaMetadata()

        assertEquals(com.fishit.player.core.model.PipelineIdTag.XTREAM, vod.pipelineIdTag)
        assertEquals(com.fishit.player.core.model.PipelineIdTag.XTREAM, series.pipelineIdTag)
        assertEquals(com.fishit.player.core.model.PipelineIdTag.XTREAM, live.pipelineIdTag)
    }

    // =========================================================================
    // EDGE CASES: Realistic bad data from real Xtream panels
    // =========================================================================

    @Test
    fun `Edge case - non-numeric added string handled gracefully`() {
        val transportDto = XtreamVodStream(
            streamId = 8001,
            name = "Bad Timestamp",
            added = "not-a-number",
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertNull(pipelineDto.added, "EpochConverter returns null for non-numeric")

        val raw = pipelineDto.toRawMetadata()
        assertNull(raw.addedTimestamp)
    }

    @Test
    fun `Edge case - empty string rating treated as null`() {
        val transportDto = XtreamVodStream(
            streamId = 8002,
            name = "Empty Rating",
            rating = "",
            rating5Based = 4.0,
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertNull(pipelineDto.rating, "Empty string toDoubleOrNull() → null")

        val raw = pipelineDto.toRawMetadata()
        assertEquals(8.0, raw.rating, "Fallback to normalized rating5Based: 4.0 * 2 = 8.0")
    }

    @Test
    fun `Edge case - negative seriesId handled`() {
        // Some panels return negative series IDs
        val transportDto = XtreamSeriesStream(
            seriesId = -42,
            name = "Negative ID Series",
        )

        val pipelineDto = transportDto.toPipelineItem()
        assertEquals(-42, pipelineDto.id)
        assertTrue(pipelineDto.isValidId, "Negative IDs are considered valid")

        val raw = pipelineDto.toRawMetadata()
        assertEquals("xtream:series:-42", raw.sourceId)
    }

    @Test
    fun `Edge case - VOD with all SSOT fields populated`() {
        // Comprehensive test: all SSOT utilities exercised in one flow
        val transportDto = XtreamVodStream(
            streamId = 9999,
            name = "The Complete Movie (2023)",
            added = "1672531200",       // 2023-01-01 00:00:00 UTC
            rating = null,
            rating5Based = 4.5,         // → 9.0
            year = "2023",
            duration = "02:15:30",      // 2h 15m 30s → 8130000 ms
            isAdult = "0",
            containerExtension = "mkv",
            categoryId = "3",
            streamType = "movie",
        )

        val raw = transportDto.toPipelineItem().toRawMetadata()

        // EpochConverter
        assertEquals(1672531200_000L, raw.addedTimestamp)
        // RatingNormalizer
        assertEquals(9.0, raw.rating)
        // YearParser
        assertEquals(2023, raw.year)
        // DurationParser
        assertEquals(8_130_000L, raw.durationMs)
        // isAdult
        assertEquals(false, raw.isAdult)
        // sourceId
        assertEquals("xtream:vod:9999", raw.sourceId)
        // playbackHints
        assertEquals("mkv", raw.playbackHints[PlaybackHintKeys.Xtream.CONTAINER_EXT])
        assertEquals("movie", raw.playbackHints[PlaybackHintKeys.Xtream.VOD_KIND])
    }
}
