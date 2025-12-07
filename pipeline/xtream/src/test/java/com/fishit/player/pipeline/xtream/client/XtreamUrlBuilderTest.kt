package com.fishit.player.pipeline.xtream.client

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Unit Tests für XtreamUrlBuilder.
 *
 * Testet:
 * - Base URL Generation
 * - Player API URLs
 * - Playback URLs (Live/VOD/Series)
 * - M3U/XMLTV URLs
 * - Catchup URLs
 * - Credential Parsing
 */
class XtreamUrlBuilderTest {

    private val basicConfig =
            XtreamApiConfig(
                    host = "example.com",
                    port = 8080,
                    scheme = "HTTP",
                    username = "testuser",
                    password = "testpass",
            )

    private val httpsConfig =
            basicConfig.copy(
                    scheme = "HTTPS",
                    port = 443,
            )

    private val basePathConfig =
            basicConfig.copy(
                    basePath = "/panel",
            )

    // =========================================================================
    // Base URL Tests
    // =========================================================================

    @Test
    fun `baseUrl generates correct HTTP URL`() {
        val builder = XtreamUrlBuilder(basicConfig)
        assertEquals("http://example.com:8080", builder.baseUrl)
    }

    @Test
    fun `baseUrl generates correct HTTPS URL`() {
        val builder = XtreamUrlBuilder(httpsConfig)
        assertEquals("https://example.com:443", builder.baseUrl)
    }

    @Test
    fun `baseUrl includes basePath when present`() {
        val builder = XtreamUrlBuilder(basePathConfig)
        assertEquals("http://example.com:8080/panel", builder.baseUrl)
    }

    @Test
    fun `baseUrl normalizes basePath correctly`() {
        val configs =
                listOf(
                        basePathConfig.copy(basePath = "panel"),
                        basePathConfig.copy(basePath = "/panel"),
                        basePathConfig.copy(basePath = "/panel/"),
                        basePathConfig.copy(basePath = "panel/"),
                )

        configs.forEach { config ->
            val builder = XtreamUrlBuilder(config)
            assertEquals(
                    "http://example.com:8080/panel",
                    builder.baseUrl,
                    "Failed for basePath: ${config.basePath}"
            )
        }
    }

    // =========================================================================
    // Player API URL Tests
    // =========================================================================

    @Test
    fun `playerApiUrl generates correct URL for action`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.playerApiUrl("get_live_categories")

        assertTrue(url.contains("http://example.com:8080/player_api.php"))
        assertTrue(url.contains("action=get_live_categories"))
        assertTrue(url.contains("username=testuser"))
        assertTrue(url.contains("password=testpass"))
    }

    @Test
    fun `playerApiUrl includes extra params`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.playerApiUrl("get_live_streams", mapOf("category_id" to "5"))

        assertTrue(url.contains("category_id=5"))
    }

    @Test
    fun `playerApiUrl without action generates server info URL`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.playerApiUrl(null)

        assertTrue(url.contains("player_api.php"))
        assertTrue(!url.contains("action="))
        assertTrue(url.contains("username=testuser"))
    }

    @Test
    fun `playerApiUrl includes basePath segments`() {
        val builder = XtreamUrlBuilder(basePathConfig)
        val url = builder.playerApiUrl("get_live_categories")

        assertTrue(url.contains("/panel/player_api.php"))
    }

    // =========================================================================
    // Live URL Tests
    // =========================================================================

    @Test
    fun `liveUrl generates correct URL`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.liveUrl(101, "m3u8")

        assertEquals("http://example.com:8080/live/testuser/testpass/101.m3u8", url)
    }

    @Test
    fun `liveUrl normalizes hls extension`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.liveUrl(101, "hls")

        assertTrue(url.endsWith(".m3u8"))
    }

    @Test
    fun `liveUrl uses default extension from config`() {
        val config = basicConfig.copy(liveExtPrefs = listOf("ts", "m3u8"))
        val builder = XtreamUrlBuilder(config)
        val url = builder.liveUrl(101)

        assertTrue(url.endsWith(".ts"))
    }

    // =========================================================================
    // VOD URL Tests
    // =========================================================================

    @Test
    fun `vodUrl generates correct URL`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.vodUrl(1001, "mp4")

        assertEquals("http://example.com:8080/vod/testuser/testpass/1001.mp4", url)
    }

    @Test
    fun `vodUrl uses updated vodKind`() {
        val builder = XtreamUrlBuilder(basicConfig)
        builder.updateVodKind("movie")
        val url = builder.vodUrl(1001, "mkv")

        assertEquals("http://example.com:8080/movie/testuser/testpass/1001.mkv", url)
    }

    @Test
    fun `vodUrl sanitizes extension`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.vodUrl(1001, "INVALID_EXTENSION!")

        assertTrue(url.endsWith(".mp4")) // Falls back to mp4
    }

    // =========================================================================
    // Series Episode URL Tests
    // =========================================================================

    @Test
    fun `seriesEpisodeUrl with episodeId generates direct URL`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url =
                builder.seriesEpisodeUrl(
                        seriesId = 500,
                        seasonNumber = 1,
                        episodeNumber = 1,
                        episodeId = 1001,
                        containerExtension = "mkv",
                )

        assertEquals("http://example.com:8080/series/testuser/testpass/1001.mkv", url)
    }

    @Test
    fun `seriesEpisodeUrl without episodeId generates legacy URL`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url =
                builder.seriesEpisodeUrl(
                        seriesId = 500,
                        seasonNumber = 2,
                        episodeNumber = 5,
                        episodeId = null,
                        containerExtension = "mp4",
                )

        assertEquals("http://example.com:8080/series/testuser/testpass/500/2/5.mp4", url)
    }

    @Test
    fun `seriesEpisodeUrl treats zero episodeId as null`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url =
                builder.seriesEpisodeUrl(
                        seriesId = 500,
                        seasonNumber = 1,
                        episodeNumber = 3,
                        episodeId = 0,
                        containerExtension = "mp4",
                )

        assertTrue(url.contains("/500/1/3.mp4"))
    }

    // =========================================================================
    // M3U / XMLTV URL Tests
    // =========================================================================

    @Test
    fun `m3uUrl generates correct URL`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.m3uUrl()

        assertTrue(url.contains("get.php"))
        assertTrue(url.contains("username=testuser"))
        assertTrue(url.contains("password=testpass"))
        assertTrue(url.contains("type=m3u_plus"))
    }

    @Test
    fun `m3uUrl with output format`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.m3uUrl(type = "m3u", output = "ts")

        assertTrue(url.contains("type=m3u"))
        assertTrue(url.contains("output=ts"))
    }

    @Test
    fun `xmltvUrl generates correct URL`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.xmltvUrl()

        assertTrue(url.contains("xmltv.php"))
        assertTrue(url.contains("username=testuser"))
        assertTrue(url.contains("password=testpass"))
    }

    // =========================================================================
    // Catchup URL Tests
    // =========================================================================

    @Test
    fun `catchupUrl generates standard format`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.catchupUrl(101, 1704067200, 60)

        assertTrue(url.contains("/streaming/timeshift.php"))
        assertTrue(url.contains("stream=101"))
        assertTrue(url.contains("start=1704067200"))
        assertTrue(url.contains("duration=60"))
    }

    @Test
    fun `catchupUrlAlt generates alternative format`() {
        val builder = XtreamUrlBuilder(basicConfig)
        val url = builder.catchupUrlAlt(101, "2024-01-01:20-00", 60, "ts")

        assertTrue(url.contains("/timeshift/"))
        assertTrue(url.contains("/testuser/"))
        assertTrue(url.contains("/testpass/"))
        assertTrue(url.contains("/60/"))
        assertTrue(url.contains("/101.ts"))
    }

    // =========================================================================
    // Credential Parsing Tests
    // =========================================================================

    @Test
    fun `parseCredentials extracts from get_php URL`() {
        val url = "http://example.com:8080/get.php?username=myuser&password=mypass&type=m3u_plus"
        val config = XtreamUrlBuilder.parseCredentials(url)

        assertNotNull(config)
        assertEquals("example.com", config.host)
        assertEquals(8080, config.port)
        assertEquals("HTTP", config.scheme)
        assertEquals("myuser", config.username)
        assertEquals("mypass", config.password)
        assertNull(config.basePath)
    }

    @Test
    fun `parseCredentials extracts from player_api URL`() {
        val url =
                "https://example.com/player_api.php?username=user1&password=pass1&action=get_live_streams"
        val config = XtreamUrlBuilder.parseCredentials(url)

        assertNotNull(config)
        assertEquals("example.com", config.host)
        assertEquals("HTTPS", config.scheme)
        assertEquals("user1", config.username)
        assertEquals("pass1", config.password)
    }

    @Test
    fun `parseCredentials extracts basePath`() {
        val url = "http://example.com:8080/panel/iptv/get.php?username=u&password=p"
        val config = XtreamUrlBuilder.parseCredentials(url)

        assertNotNull(config)
        assertEquals("/panel/iptv", config.basePath)
    }

    @Test
    fun `parseCredentials returns null for invalid URL`() {
        assertNull(XtreamUrlBuilder.parseCredentials("not a url"))
        assertNull(
                XtreamUrlBuilder.parseCredentials("http://example.com/get.php")
        ) // No credentials
        assertNull(
                XtreamUrlBuilder.parseCredentials("http://example.com/get.php?username=&password=")
        ) // Empty credentials
    }

    @Test
    fun `parsePlayUrl extracts from live URL`() {
        val url = "http://example.com:8080/live/myuser/mypass/101.m3u8"
        val config = XtreamUrlBuilder.parsePlayUrl(url)

        assertNotNull(config)
        assertEquals("example.com", config.host)
        assertEquals(8080, config.port)
        assertEquals("myuser", config.username)
        assertEquals("mypass", config.password)
    }

    @Test
    fun `parsePlayUrl extracts from vod URL`() {
        val url = "http://example.com/vod/user/pass/1001.mp4"
        val config = XtreamUrlBuilder.parsePlayUrl(url)

        assertNotNull(config)
        assertEquals("user", config.username)
        assertEquals("pass", config.password)
    }

    @Test
    fun `parsePlayUrl extracts basePath from play URL`() {
        val url = "http://example.com/panel/live/user/pass/101.ts"
        val config = XtreamUrlBuilder.parsePlayUrl(url)

        assertNotNull(config)
        assertEquals("/panel", config.basePath)
    }

    @Test
    fun `parsePlayUrl returns null for invalid URLs`() {
        assertNull(XtreamUrlBuilder.parsePlayUrl("http://example.com/something.m3u8"))
        assertNull(
                XtreamUrlBuilder.parsePlayUrl("http://example.com/live/user/101.ts")
        ) // Missing password
    }

    // =========================================================================
    // Credential Redaction Tests
    // =========================================================================

    @Test
    fun `redactCredentials masks query parameters`() {
        val url = "http://example.com/player_api.php?username=secret&password=hunter2&action=test"
        val redacted = url.redactCredentials()

        assertTrue(!redacted.contains("secret"))
        assertTrue(!redacted.contains("hunter2"))
        assertTrue(redacted.contains("username=***"))
        assertTrue(redacted.contains("password=***"))
    }

    @Test
    fun `redactCredentials masks path segments`() {
        val url = "http://example.com/live/myuser/mypass/101.m3u8"
        val redacted = url.redactCredentials()

        assertTrue(!redacted.contains("myuser"))
        assertTrue(!redacted.contains("mypass"))
        assertTrue(redacted.contains("/live/***/****/"))
    }

    // =========================================================================
    // Port Update Tests
    // =========================================================================

    @Test
    fun `updateResolvedPort changes URL port`() {
        val builder = XtreamUrlBuilder(basicConfig)
        assertEquals("http://example.com:8080", builder.baseUrl)

        builder.updateResolvedPort(9090)
        assertEquals("http://example.com:9090", builder.baseUrl)
    }

    // =========================================================================
    // KönigTV Integration Test
    // =========================================================================

    @Test
    fun `parseCredentials extracts from KonigTV get_php URL`() {
        // Real test URL from user
        val url =
                "http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts"
        val config = XtreamUrlBuilder.parseCredentials(url)

        assertNotNull(config, "parseCredentials should not return null for KönigTV URL")
        assertEquals("konigtv.com", config.host)
        assertEquals(8080, config.port)
        assertEquals("HTTP", config.scheme)
        assertEquals("Christoph10", config.username)
        assertEquals("JQ2rKsQ744", config.password)
        assertNull(config.basePath)
    }

    @Test
    fun `KonigTV config generates correct player API URLs`() {
        val url =
                "http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts"
        val config = XtreamUrlBuilder.parseCredentials(url)

        assertNotNull(config)

        val builder = XtreamUrlBuilder(config)

        // Server info URL
        val serverInfoUrl = builder.playerApiUrl(null)
        assertTrue(serverInfoUrl.contains("http://konigtv.com:8080/player_api.php"))
        assertTrue(serverInfoUrl.contains("username=Christoph10"))
        assertTrue(serverInfoUrl.contains("password=JQ2rKsQ744"))
        assertTrue(!serverInfoUrl.contains("action="))

        // Live categories URL
        val liveCatsUrl = builder.playerApiUrl("get_live_categories")
        assertTrue(liveCatsUrl.contains("action=get_live_categories"))
        assertTrue(liveCatsUrl.contains("username=Christoph10"))

        // VOD categories URL
        val vodCatsUrl = builder.playerApiUrl("get_vod_categories")
        assertTrue(vodCatsUrl.contains("action=get_vod_categories"))

        // Live streams with category
        val liveStreamsUrl = builder.playerApiUrl("get_live_streams", mapOf("category_id" to "5"))
        assertTrue(liveStreamsUrl.contains("action=get_live_streams"))
        assertTrue(liveStreamsUrl.contains("category_id=5"))
    }

    @Test
    fun `KonigTV config generates correct playback URLs`() {
        val url =
                "http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts"
        val config = XtreamUrlBuilder.parseCredentials(url)

        assertNotNull(config)

        val builder = XtreamUrlBuilder(config)

        // Live URL
        val liveUrl = builder.liveUrl(12345, "m3u8")
        assertEquals("http://konigtv.com:8080/live/Christoph10/JQ2rKsQ744/12345.m3u8", liveUrl)

        // Live URL with ts
        val liveTsUrl = builder.liveUrl(12345, "ts")
        assertEquals("http://konigtv.com:8080/live/Christoph10/JQ2rKsQ744/12345.ts", liveTsUrl)

        // VOD URL
        val vodUrl = builder.vodUrl(67890, "mkv")
        assertEquals("http://konigtv.com:8080/vod/Christoph10/JQ2rKsQ744/67890.mkv", vodUrl)

        // Series episode URL with episodeId
        val seriesUrl = builder.seriesEpisodeUrl(100, 1, 1, 55555, "mp4")
        assertEquals("http://konigtv.com:8080/series/Christoph10/JQ2rKsQ744/55555.mp4", seriesUrl)
    }

    @Test
    fun `KonigTV config generates correct M3U URL`() {
        val url =
                "http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts"
        val config = XtreamUrlBuilder.parseCredentials(url)

        assertNotNull(config)

        val builder = XtreamUrlBuilder(config)

        val m3uUrl = builder.m3uUrl("m3u_plus", "ts")
        assertTrue(m3uUrl.contains("http://konigtv.com:8080/get.php"))
        assertTrue(m3uUrl.contains("username=Christoph10"))
        assertTrue(m3uUrl.contains("password=JQ2rKsQ744"))
        assertTrue(m3uUrl.contains("type=m3u_plus"))
        assertTrue(m3uUrl.contains("output=ts"))
    }

    @Test
    fun `KonigTV series episode URL uses episode_id for playback`() {
        // KönigTV requires episode_id (not series/season/episode path) for playback
        val url =
                "http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts"
        val config = XtreamUrlBuilder.parseCredentials(url)

        assertNotNull(config)

        val builder = XtreamUrlBuilder(config)

        // When episodeId is provided, use direct path
        val episodeUrl =
                builder.seriesEpisodeUrl(
                        seriesId = 1234,
                        seasonNumber = 1,
                        episodeNumber = 5,
                        episodeId = 98765, // The episode_id from API
                        containerExtension = "mp4"
                )

        // Should use episodeId, NOT seriesId/season/episode path
        assertEquals("http://konigtv.com:8080/series/Christoph10/JQ2rKsQ744/98765.mp4", episodeUrl)
        assertTrue(!episodeUrl.contains("/1234/1/5."))
    }

    @Test
    fun `KonigTV xmltv URL is generated correctly`() {
        val url =
                "http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts"
        val config = XtreamUrlBuilder.parseCredentials(url)

        assertNotNull(config)

        val builder = XtreamUrlBuilder(config)

        val xmltvUrl = builder.xmltvUrl()
        assertTrue(xmltvUrl.contains("http://konigtv.com:8080/xmltv.php"))
        assertTrue(xmltvUrl.contains("username=Christoph10"))
        assertTrue(xmltvUrl.contains("password=JQ2rKsQ744"))
    }
}
