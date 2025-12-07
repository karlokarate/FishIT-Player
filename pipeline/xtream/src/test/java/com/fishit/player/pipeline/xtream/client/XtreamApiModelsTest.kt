package com.fishit.player.pipeline.xtream.client

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for XtreamApiModels - specifically testing field resolution logic for panel
 * compatibility (KönigTV, XUI.ONE, etc.)
 */
class XtreamApiModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // =========================================================================
    // XtreamEpisodeInfo - episode_id Resolution
    // =========================================================================

    @Test
    fun `XtreamEpisodeInfo resolvedEpisodeId prefers episode_id over id`() {
        // KönigTV-style response with episode_id
        val jsonStr =
                """
        {
            "episode_id": 98765,
            "id": 11111,
            "episode_num": 5,
            "title": "Test Episode"
        }
        """.trimIndent()

        val episode = json.decodeFromString<XtreamEpisodeInfo>(jsonStr)

        // Should prefer episode_id
        assertEquals(98765, episode.resolvedEpisodeId)
        assertEquals(98765, episode.episodeId)
        assertEquals(11111, episode.id)
    }

    @Test
    fun `XtreamEpisodeInfo resolvedEpisodeId falls back to id when episode_id missing`() {
        // Classic Xtream-UI style with only id
        val jsonStr =
                """
        {
            "id": 11111,
            "episode_num": 5,
            "title": "Test Episode"
        }
        """.trimIndent()

        val episode = json.decodeFromString<XtreamEpisodeInfo>(jsonStr)

        // Should fall back to id
        assertEquals(11111, episode.resolvedEpisodeId)
        assertNull(episode.episodeId)
    }

    @Test
    fun `XtreamEpisodeInfo handles missing both IDs`() {
        val jsonStr =
                """
        {
            "episode_num": 5,
            "title": "Test Episode"
        }
        """.trimIndent()

        val episode = json.decodeFromString<XtreamEpisodeInfo>(jsonStr)

        // Both null
        assertNull(episode.resolvedEpisodeId)
        assertNull(episode.episodeId)
        assertNull(episode.id)
    }

    // =========================================================================
    // XtreamLiveStream - ID Resolution
    // =========================================================================

    @Test
    fun `XtreamLiveStream resolvedId prefers stream_id`() {
        val jsonStr =
                """
        {
            "stream_id": 12345,
            "id": 99999,
            "name": "Test Channel"
        }
        """.trimIndent()

        val stream = json.decodeFromString<XtreamLiveStream>(jsonStr)

        assertEquals(12345, stream.resolvedId)
    }

    @Test
    fun `XtreamLiveStream resolvedId falls back to id`() {
        val jsonStr =
                """
        {
            "id": 99999,
            "name": "Test Channel"
        }
        """.trimIndent()

        val stream = json.decodeFromString<XtreamLiveStream>(jsonStr)

        assertEquals(99999, stream.resolvedId)
    }

    // =========================================================================
    // XtreamVodStream - ID Resolution
    // =========================================================================

    @Test
    fun `XtreamVodStream resolvedId prefers vod_id`() {
        val jsonStr =
                """
        {
            "vod_id": 67890,
            "movie_id": 11111,
            "stream_id": 22222,
            "id": 33333,
            "name": "Test Movie"
        }
        """.trimIndent()

        val stream = json.decodeFromString<XtreamVodStream>(jsonStr)

        assertEquals(67890, stream.resolvedId)
    }

    @Test
    fun `XtreamVodStream resolvedId tries movie_id when vod_id missing`() {
        val jsonStr =
                """
        {
            "movie_id": 11111,
            "stream_id": 22222,
            "name": "Test Movie"
        }
        """.trimIndent()

        val stream = json.decodeFromString<XtreamVodStream>(jsonStr)

        assertEquals(11111, stream.resolvedId)
    }

    @Test
    fun `XtreamVodStream resolvedId tries stream_id when movie_id missing`() {
        // KönigTV-style: some VODs only have stream_id
        val jsonStr =
                """
        {
            "stream_id": 22222,
            "name": "Test Movie"
        }
        """.trimIndent()

        val stream = json.decodeFromString<XtreamVodStream>(jsonStr)

        assertEquals(22222, stream.resolvedId)
    }

    // =========================================================================
    // XtreamSeriesStream - ID Resolution
    // =========================================================================

    @Test
    fun `XtreamSeriesStream resolvedId prefers series_id`() {
        val jsonStr =
                """
        {
            "series_id": 54321,
            "id": 99999,
            "name": "Test Series"
        }
        """.trimIndent()

        val stream = json.decodeFromString<XtreamSeriesStream>(jsonStr)

        assertEquals(54321, stream.resolvedId)
    }

    // =========================================================================
    // XtreamEpgProgramme - Timestamp Resolution
    // =========================================================================

    @Test
    fun `XtreamEpgProgramme startEpoch prefers start_timestamp`() {
        val jsonStr =
                """
        {
            "start_timestamp": 1733580000,
            "start": "1733500000",
            "title": "Test Program"
        }
        """.trimIndent()

        val epg = json.decodeFromString<XtreamEpgProgramme>(jsonStr)

        assertEquals(1733580000L, epg.startEpoch)
    }

    @Test
    fun `XtreamEpgProgramme startEpoch falls back to start string`() {
        val jsonStr =
                """
        {
            "start": "1733500000",
            "title": "Test Program"
        }
        """.trimIndent()

        val epg = json.decodeFromString<XtreamEpgProgramme>(jsonStr)

        assertEquals(1733500000L, epg.startEpoch)
    }

    @Test
    fun `XtreamEpgProgramme endEpoch handles stop_timestamp variant`() {
        // Some panels use stop_timestamp instead of end_timestamp
        val jsonStr =
                """
        {
            "stop_timestamp": 1733590000,
            "title": "Test Program"
        }
        """.trimIndent()

        val epg = json.decodeFromString<XtreamEpgProgramme>(jsonStr)

        assertEquals(1733590000L, epg.endEpoch)
    }

    // =========================================================================
    // XtreamVodInfoBlock - Poster Resolution
    // =========================================================================

    @Test
    fun `XtreamVodInfoBlock contains all common poster field variants`() {
        val jsonStr =
                """
        {
            "movie_image": "https://example.com/movie.jpg",
            "poster_path": "https://example.com/poster.jpg",
            "cover": "https://example.com/cover.jpg",
            "cover_big": "https://example.com/cover_big.jpg"
        }
        """.trimIndent()

        val info = json.decodeFromString<XtreamVodInfoBlock>(jsonStr)

        assertNotNull(info.movieImage)
        assertNotNull(info.posterPath)
        assertNotNull(info.cover)
        assertNotNull(info.coverBig)
    }

    // =========================================================================
    // XtreamCategory - String ID Handling
    // =========================================================================

    @Test
    fun `XtreamCategory handles category_id as string`() {
        val jsonStr =
                """
        {
            "category_id": "123",
            "category_name": "Movies"
        }
        """.trimIndent()

        val category = json.decodeFromString<XtreamCategory>(jsonStr)

        assertEquals("123", category.id)
        assertEquals("Movies", category.name)
    }

    @Test
    fun `XtreamUserInfo fromRaw parses status correctly`() {
        val raw =
                XtreamUserInfoRaw(
                        username = "testuser",
                        status = "Active",
                        expDate = "1735689600",
                        maxConnections = "2",
                        activeCons = "1"
                )

        val info = XtreamUserInfo.fromRaw(raw)

        assertEquals("testuser", info.username)
        assertEquals(XtreamUserInfo.UserStatus.ACTIVE, info.status)
        assertEquals(1735689600L, info.expDateEpoch)
        assertEquals(2, info.maxConnections)
        assertEquals(1, info.activeConnections)
    }
}
