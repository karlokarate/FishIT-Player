package com.fishit.player.pipeline.xtream.client

import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit Tests für DefaultXtreamApiClient.
 *
 * Testet:
 * - Server Info & Auth
 * - Kategorien-Abruf (Live/VOD/Series)
 * - Stream-Listen mit Category-ID-Fallback
 * - Detail-Endpoints
 * - EPG-Abruf
 * - Playback-URL-Generierung
 * - Suche
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultXtreamApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DefaultXtreamApiClient
    private lateinit var http: OkHttpClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        http =
                OkHttpClient.Builder()
                        .connectTimeout(1, TimeUnit.SECONDS)
                        .readTimeout(1, TimeUnit.SECONDS)
                        .build()

        client = DefaultXtreamApiClient(http)
    }

    @After
    fun teardown() {
        server.shutdown()
        client.close()
    }

    private fun config(port: Int = server.port): XtreamApiConfig =
            XtreamApiConfig(
                    host = server.hostName,
                    port = port,
                    scheme = "HTTP",
                    username = "testuser",
                    password = "testpass",
            )

    // =========================================================================
    // Server Info Tests
    // =========================================================================

    @Test
    fun `getServerInfo returns parsed server info`() = runTest {
        val json =
                """
        {
            "user_info": {
                "username": "testuser",
                "password": "testpass",
                "status": "Active",
                "exp_date": "1735689600",
                "is_trial": "0",
                "active_cons": "1",
                "max_connections": "2",
                "allowed_output_formats": ["m3u8", "ts"]
            },
            "server_info": {
                "url": "example.com",
                "port": "8080",
                "https_port": "443",
                "server_protocol": "http",
                "rtmp_port": "1935",
                "timezone": "Europe/Berlin",
                "timestamp_now": 1704067200,
                "time_now": "2024-01-01 00:00:00"
            }
        }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(json).setResponseCode(200))

        // Initialize first
        server.enqueue(MockResponse().setBody(json).setResponseCode(200)) // Discovery probe
        server.enqueue(MockResponse().setBody(json).setResponseCode(200)) // Auth check

        val initResult = client.initialize(config())
        assertTrue(initResult.isSuccess)

        // Reset queue
        server.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = client.getServerInfo()
        assertTrue(result.isSuccess)

        val serverInfo = result.getOrThrow()
        assertNotNull(serverInfo.userInfo)
        assertEquals("testuser", serverInfo.userInfo?.username)
        assertEquals("Active", serverInfo.userInfo?.status)
    }

    // =========================================================================
    // Categories Tests
    // =========================================================================

    @Test
    fun `getLiveCategories returns parsed categories`() = runTest {
        val categoriesJson =
                """
        [
            {"category_id": "1", "category_name": "Sports", "parent_id": 0},
            {"category_id": "2", "category_name": "News", "parent_id": 0},
            {"category_id": "3", "category_name": "Football", "parent_id": 1}
        ]
        """.trimIndent()

        // Initialize
        setupInitialization()

        server.enqueue(MockResponse().setBody(categoriesJson).setResponseCode(200))

        val categories = client.getLiveCategories()
        assertEquals(3, categories.size)
        assertEquals("Sports", categories[0].categoryName)
        assertEquals("1", categories[0].categoryId)
        assertEquals(1, categories[2].parentId)
    }

    @Test
    fun `getVodCategories tries aliases until success`() = runTest {
        val categoriesJson =
                """
        [
            {"category_id": "10", "category_name": "Action"},
            {"category_id": "11", "category_name": "Comedy"}
        ]
        """.trimIndent()

        setupInitialization()

        // First alias "vod" fails
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200))
        // Second alias "movie" succeeds
        server.enqueue(MockResponse().setBody(categoriesJson).setResponseCode(200))

        val categories = client.getVodCategories()
        assertEquals(2, categories.size)
        assertEquals("Action", categories[0].categoryName)
    }

    // =========================================================================
    // Stream Lists Tests
    // =========================================================================

    @Test
    fun `getLiveStreams returns parsed streams`() = runTest {
        val streamsJson =
                """
        [
            {
                "num": 1,
                "name": "BBC One",
                "stream_id": 101,
                "stream_icon": "http://logo.png",
                "epg_channel_id": "bbc1.uk",
                "tv_archive": 1,
                "tv_archive_duration": 7,
                "category_id": "1",
                "is_adult": "0"
            },
            {
                "num": 2,
                "name": "CNN",
                "stream_id": 102,
                "stream_icon": "http://cnn.png",
                "epg_channel_id": "cnn.us",
                "tv_archive": 0,
                "category_id": "2"
            }
        ]
        """.trimIndent()

        setupInitialization()
        server.enqueue(MockResponse().setBody(streamsJson).setResponseCode(200))

        val streams = client.getLiveStreams()
        assertEquals(2, streams.size)

        val bbc = streams[0]
        assertEquals("BBC One", bbc.name)
        assertEquals(101, bbc.streamId)
        assertEquals("bbc1.uk", bbc.epgChannelId)
        assertEquals(1, bbc.tvArchive)
        assertEquals(7, bbc.tvArchiveDuration)
    }

    @Test
    fun `getLiveStreams with category fallback`() = runTest {
        val streamsJson =
                """
        [{"num": 1, "name": "Test", "stream_id": 1}]
        """.trimIndent()

        setupInitialization()

        // category_id=* fails
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200))
        // category_id=0 succeeds
        server.enqueue(MockResponse().setBody(streamsJson).setResponseCode(200))

        val streams = client.getLiveStreams()
        assertEquals(1, streams.size)
    }

    @Test
    fun `getVodStreams extracts all ID field variants`() = runTest {
        val streamsJson =
                """
        [
            {"num": 1, "name": "Movie A", "vod_id": 1001, "stream_icon": "a.png"},
            {"num": 2, "name": "Movie B", "movie_id": 1002, "poster_path": "b.png"},
            {"num": 3, "name": "Movie C", "id": 1003, "cover": "c.png"}
        ]
        """.trimIndent()

        setupInitialization()
        server.enqueue(MockResponse().setBody(streamsJson).setResponseCode(200))

        val streams = client.getVodStreams()
        assertEquals(3, streams.size)

        assertEquals(1001, streams[0].vodId)
        assertEquals(1002, streams[1].movieId)
        assertEquals(1003, streams[2].id)
    }

    @Test
    fun `getSeries returns parsed series`() = runTest {
        val seriesJson =
                """
        [
            {
                "num": 1,
                "name": "Breaking Bad",
                "series_id": 500,
                "cover": "bb.png",
                "plot": "A chemistry teacher...",
                "cast": "Bryan Cranston",
                "rating": "9.5",
                "year": "2008",
                "genre": "Drama, Crime"
            }
        ]
        """.trimIndent()

        setupInitialization()
        server.enqueue(MockResponse().setBody(seriesJson).setResponseCode(200))

        val series = client.getSeries()
        assertEquals(1, series.size)

        val bb = series[0]
        assertEquals("Breaking Bad", bb.name)
        assertEquals(500, bb.seriesId)
        assertEquals("2008", bb.year)
        assertEquals("Drama, Crime", bb.genre)
    }

    // =========================================================================
    // Detail Endpoints Tests
    // =========================================================================

    @Test
    fun `getVodInfo returns parsed VOD info`() = runTest {
        val infoJson =
                """
        {
            "info": {
                "movie_image": "poster.jpg",
                "plot": "An epic story",
                "genre": "Action",
                "releasedate": "2023-12-01",
                "rating": "8.5",
                "duration": "02:15:30"
            },
            "movie_data": {
                "stream_id": 1001,
                "container_extension": "mkv"
            }
        }
        """.trimIndent()

        setupInitialization()
        server.enqueue(MockResponse().setBody(infoJson).setResponseCode(200))

        val info = client.getVodInfo(1001)
        assertNotNull(info)
        assertEquals("An epic story", info.info?.plot)
        assertEquals("Action", info.info?.genre)
        assertEquals("mkv", info.movieData?.containerExtension)
    }

    @Test
    fun `getSeriesInfo returns parsed series info with episodes`() = runTest {
        val infoJson =
                """
        {
            "info": {
                "name": "Breaking Bad",
                "cover": "bb.png",
                "plot": "A chemistry teacher turns to crime",
                "cast": "Bryan Cranston, Aaron Paul",
                "genre": "Drama",
                "rating": "9.5"
            },
            "episodes": {
                "1": [
                    {
                        "id": "1001",
                        "episode_num": 1,
                        "title": "Pilot",
                        "container_extension": "mkv",
                        "info": {"duration": "58:00"}
                    },
                    {
                        "id": "1002",
                        "episode_num": 2,
                        "title": "Cat's in the Bag",
                        "container_extension": "mkv"
                    }
                ]
            }
        }
        """.trimIndent()

        setupInitialization()
        server.enqueue(MockResponse().setBody(infoJson).setResponseCode(200))

        val info = client.getSeriesInfo(500)
        assertNotNull(info)
        assertEquals("Breaking Bad", info.info?.name)
        assertNotNull(info.episodes)
        assertTrue(info.episodes!!.containsKey("1"))
        assertEquals(2, info.episodes!!["1"]?.size)
    }

    // =========================================================================
    // EPG Tests
    // =========================================================================

    @Test
    fun `getShortEpg returns parsed programmes`() = runTest {
        val epgJson =
                """
        {
            "epg_listings": [
                {
                    "id": "123",
                    "title": "News at 9",
                    "start": "2024-01-01 21:00:00",
                    "start_timestamp": 1704142800,
                    "end": "2024-01-01 21:30:00",
                    "end_timestamp": 1704144600,
                    "description": "Latest news"
                },
                {
                    "id": "124",
                    "title": "Weather",
                    "start": "2024-01-01 21:30:00",
                    "start_timestamp": 1704144600,
                    "end": "2024-01-01 21:35:00",
                    "end_timestamp": 1704144900
                }
            ]
        }
        """.trimIndent()

        setupInitialization()
        server.enqueue(MockResponse().setBody(epgJson).setResponseCode(200))

        val programmes = client.getShortEpg(101, 10)
        assertEquals(2, programmes.size)
        assertEquals("News at 9", programmes[0].title)
        assertEquals("Latest news", programmes[0].description)
        assertEquals(1704142800, programmes[0].startTimestamp)
    }

    @Test
    fun `getShortEpg handles direct array response`() = runTest {
        val epgJson =
                """
        [
            {"id": "1", "title": "Show A", "start_timestamp": 1000},
            {"id": "2", "title": "Show B", "start_timestamp": 2000}
        ]
        """.trimIndent()

        setupInitialization()
        server.enqueue(MockResponse().setBody(epgJson).setResponseCode(200))

        val programmes = client.getShortEpg(101)
        assertEquals(2, programmes.size)
    }

    // =========================================================================
    // Playback URL Tests
    // =========================================================================

    @Test
    fun `buildLiveUrl generates correct URL`() = runTest {
        setupInitialization()

        val url = client.buildLiveUrl(101, "m3u8")
        assertTrue(url.contains("/live/"))
        assertTrue(url.contains("/testuser/"))
        assertTrue(url.contains("/testpass/"))
        assertTrue(url.contains("/101.m3u8"))
    }

    @Test
    fun `buildVodUrl generates correct URL`() = runTest {
        setupInitialization()

        val url = client.buildVodUrl(1001, "mkv")
        assertTrue(url.contains("/vod/") || url.contains("/movie/"))
        assertTrue(url.contains("/1001.mkv"))
    }

    @Test
    fun `buildSeriesEpisodeUrl with episodeId generates direct URL`() = runTest {
        setupInitialization()

        val url =
                client.buildSeriesEpisodeUrl(
                        seriesId = 500,
                        seasonNumber = 1,
                        episodeNumber = 1,
                        episodeId = 1001,
                        containerExtension = "mkv",
                )
        assertTrue(url.contains("/series/"))
        assertTrue(url.contains("/1001.mkv"))
    }

    @Test
    fun `buildSeriesEpisodeUrl without episodeId generates legacy URL`() = runTest {
        setupInitialization()

        val url =
                client.buildSeriesEpisodeUrl(
                        seriesId = 500,
                        seasonNumber = 2,
                        episodeNumber = 5,
                        episodeId = null,
                        containerExtension = "mp4",
                )
        assertTrue(url.contains("/series/"))
        assertTrue(url.contains("/500/2/5.mp4") || url.contains("/500/"))
    }

    // =========================================================================
    // Search Tests
    // =========================================================================

    @Test
    fun `search filters results by query`() = runTest {
        val liveJson = """[{"name": "BBC News", "stream_id": 1}, {"name": "CNN", "stream_id": 2}]"""
        val vodJson = """[{"name": "Batman", "vod_id": 10}, {"name": "Superman", "vod_id": 11}]"""
        val seriesJson = """[{"name": "Breaking Bad", "series_id": 100}]"""

        setupInitialization()

        // Search for "bat"
        server.enqueue(MockResponse().setBody(liveJson).setResponseCode(200))
        server.enqueue(MockResponse().setBody(vodJson).setResponseCode(200))
        server.enqueue(MockResponse().setBody(seriesJson).setResponseCode(200))

        val results =
                client.search(
                        "bat",
                        setOf(
                                XtreamContentType.LIVE,
                                XtreamContentType.VOD,
                                XtreamContentType.SERIES
                        )
                )

        assertEquals(0, results.live.size) // No live match
        assertEquals(1, results.vod.size) // Batman matches
        assertEquals(0, results.series.size) // No series match

        assertEquals("Batman", results.vod[0].name)
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun setupInitialization() {
        val serverInfoJson =
                """
        {
            "user_info": {"username": "testuser", "status": "Active", "exp_date": "9999999999"},
            "server_info": {"url": "${server.hostName}", "port": "${server.port}"}
        }
        """.trimIndent()

        // Queue responses for initialization
        server.enqueue(MockResponse().setBody(serverInfoJson).setResponseCode(200)) // Initial probe
        server.enqueue(
                MockResponse().setBody(serverInfoJson).setResponseCode(200)
        ) // Auth validation

        runTest { client.initialize(config()) }
    }
}
