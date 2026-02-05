package com.fishit.player.infra.transport.xtream

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for series episode URL building to verify /series/ path usage.
 *
 * Per Xtream API specification and playback policy:
 * - Series episodes MUST use /series/ path (NOT /movie/ or /vod/)
 * - container_extension as SSOT
 * - Fallback to mkv if containerExtension is missing
 */
class XtreamSeriesEpisodeUrlTest {
    private val config =
        XtreamApiConfig(
            host = "example.com",
            port = 8080,
            scheme = "http",
            username = "testuser",
            password = "testpass",
            vodExtPrefs = listOf("mp4"),
            seriesExtPrefs = listOf("mkv"),
        )

    /**
     * Helper to create a configured XtreamUrlBuilder.
     * Uses the new configure() + updateVodKind() API pattern.
     */
    private fun createBuilder(
        cfg: XtreamApiConfig = config,
        resolvedPort: Int = 8080,
        vodKind: String = "vod",
    ): XtreamUrlBuilder =
        XtreamUrlBuilder().apply {
            configure(cfg, resolvedPort)
            updateVodKind(vodKind)
        }

    @Test
    fun `series episode URL uses series path for direct episodeId`() {
        // Given a URL builder with vodKind = "movie"
        val builder = createBuilder(vodKind = "movie")

        // When building series episode URL with episodeId
        val url =
            builder.seriesEpisodeUrl(
                seriesId = 123,
                seasonNumber = 1,
                episodeNumber = 5,
                episodeId = 456,
                containerExtension = "mkv",
            )

        // Then URL uses /series/ path, not /movie/
        assertTrue("URL should contain /series/", url.contains("/series/"))
        assertFalse("URL should NOT contain /movie/", url.contains("/movie/"))
        assertTrue("URL should contain episodeId", url.contains("/456."))
        assertTrue("URL should use container extension", url.endsWith(".mkv"))
    }

    @Test
    fun `series episode URL always uses series path regardless of vodKind`() {
        // Given a URL builder with vodKind = "vod"
        val builder = createBuilder(vodKind = "vod")

        // When building series episode URL with episodeId
        val url =
            builder.seriesEpisodeUrl(
                seriesId = 123,
                seasonNumber = 1,
                episodeNumber = 5,
                episodeId = 789,
                containerExtension = "mp4",
            )

        // Then URL uses /series/ path, NOT /vod/
        assertTrue("URL should contain /series/", url.contains("/series/"))
        assertFalse("URL should NOT contain /vod/", url.contains("/vod/"))
        assertTrue("URL should contain episodeId", url.contains("/789."))
    }

    @Test
    fun `series episode URL uses series path for legacy format`() {
        // Given a URL builder with vodKind = "movie"
        val builder = createBuilder(vodKind = "movie")

        // When building series episode URL without episodeId (legacy format)
        val url =
            builder.seriesEpisodeUrl(
                seriesId = 123,
                seasonNumber = 2,
                episodeNumber = 10,
                episodeId = null,
                containerExtension = "mkv",
            )

        // Then URL uses /series/ path with seriesId/season/episode structure
        assertTrue("URL should contain /series/", url.contains("/series/"))
        assertFalse("URL should NOT contain /movie/", url.contains("/movie/"))
        assertTrue("URL should contain season/episode structure", url.contains("/123/2/10."))
        assertTrue("URL should use container extension", url.endsWith(".mkv"))
    }

    @Test
    fun `series episode URL respects container extension as SSOT`() {
        // Given a URL builder
        val builder = createBuilder(vodKind = "movie")

        // When building URL with mkv container extension (even though config prefers mp4)
        val url =
            builder.seriesEpisodeUrl(
                seriesId = 123,
                seasonNumber = 1,
                episodeNumber = 5,
                episodeId = 456,
                containerExtension = "mkv",
            )

        // Then URL uses the provided container extension, not config default
        assertTrue("URL should use mkv extension", url.endsWith(".mkv"))
        assertFalse("URL should NOT use mp4 from config", url.endsWith(".mp4"))
    }

    @Test
    fun `XtreamUrlBuilder uses series path for episode URLs`() {
        // Given a URL builder with vodKind = "movie"
        val builder = createBuilder(vodKind = "movie")

        // When building series episode URL
        val url =
            builder.seriesEpisodeUrl(
                seriesId = 123,
                seasonNumber = 1,
                episodeNumber = 5,
                episodeId = 456,
                containerExtension = "mkv",
            )

        // Then URL uses /series/ path, NOT /movie/
        assertTrue("URL should contain /series/", url.contains("/series/"))
        assertFalse("URL should NOT contain /movie/", url.contains("/movie/"))

        // Verify the URL structure matches expected format
        val expectedPattern = "http://example.com:8080/series/"
        assertTrue("URL should start with expected pattern", url.startsWith(expectedPattern))
    }

    @Test
    fun `series episode URL format differs from VOD URL format`() {
        // Given a URL builder
        val builder = createBuilder(vodKind = "movie")

        // When building VOD URL and series episode URL with same ID
        val vodUrl = builder.vodUrl(vodId = 456, containerExtension = "mkv")
        val seriesUrl =
            builder.seriesEpisodeUrl(
                seriesId = 123,
                seasonNumber = 1,
                episodeNumber = 5,
                episodeId = 456,
                containerExtension = "mkv",
            )

        // Then VOD uses /movie/ path and series uses /series/ path
        val vodPattern = "/movie/testuser/testpass/456.mkv"
        val seriesPattern = "/series/testuser/testpass/456.mkv"

        assertTrue("VOD URL should match expected pattern", vodUrl.contains(vodPattern))
        assertTrue("Series URL should match expected pattern", seriesUrl.contains(seriesPattern))

        // URLs have different paths
        assertFalse(
            "Episode URL should NOT have same path as VOD URL",
            vodUrl == seriesUrl,
        )
    }

    @Test
    fun `series episode URL uses correct credentials encoding`() {
        // Given a config with special characters in credentials
        val specialConfig =
            XtreamApiConfig(
                host = "example.com",
                port = 8080,
                scheme = "http",
                username = "user@test",
                password = "pass+word",
            )

        val builder = createBuilder(cfg = specialConfig, vodKind = "movie")

        // When building series episode URL
        val url =
            builder.seriesEpisodeUrl(
                seriesId = 123,
                seasonNumber = 1,
                episodeNumber = 5,
                episodeId = 456,
                containerExtension = "mkv",
            )

        // Then credentials should be URL-encoded
        assertTrue("Username should be encoded", url.contains("user%40test"))
        assertTrue("Password should be encoded", url.contains("pass%2Bword"))
    }
}
