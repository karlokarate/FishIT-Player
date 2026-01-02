package com.fishit.player.infra.transport.xtream

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for series episode URL building to verify VOD path usage.
 *
 * Per playback policy, series episodes should use:
 * - /movie/ or /vod/ path (file-based playback, like VOD)
 * - NOT /series/ path
 * - container_extension as SSOT
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

    @Test
    fun `series episode URL uses movie path for direct episodeId`() {
        // Given a URL builder with vodKind = "movie"
        val builder = XtreamUrlBuilder(config, resolvedPort = 8080, vodKind = "movie")

        // When building series episode URL with episodeId
        val url = builder.seriesEpisodeUrl(
            seriesId = 123,
            seasonNumber = 1,
            episodeNumber = 5,
            episodeId = 456,
            containerExtension = "mkv",
        )

        // Then URL uses /movie/ path, not /series/
        assertTrue("URL should contain /movie/", url.contains("/movie/"))
        assertFalse("URL should NOT contain /series/", url.contains("/series/"))
        assertTrue("URL should contain episodeId", url.contains("/456."))
        assertTrue("URL should use container extension", url.endsWith(".mkv"))
    }

    @Test
    fun `series episode URL uses vod path when vodKind is vod`() {
        // Given a URL builder with vodKind = "vod"
        val builder = XtreamUrlBuilder(config, resolvedPort = 8080, vodKind = "vod")

        // When building series episode URL with episodeId
        val url = builder.seriesEpisodeUrl(
            seriesId = 123,
            seasonNumber = 1,
            episodeNumber = 5,
            episodeId = 789,
            containerExtension = "mp4",
        )

        // Then URL uses /vod/ path, not /series/
        assertTrue("URL should contain /vod/", url.contains("/vod/"))
        assertFalse("URL should NOT contain /series/", url.contains("/series/"))
        assertTrue("URL should contain episodeId", url.contains("/789."))
    }

    @Test
    fun `series episode URL uses movie path for legacy format`() {
        // Given a URL builder with vodKind = "movie"
        val builder = XtreamUrlBuilder(config, resolvedPort = 8080, vodKind = "movie")

        // When building series episode URL without episodeId (legacy format)
        val url = builder.seriesEpisodeUrl(
            seriesId = 123,
            seasonNumber = 2,
            episodeNumber = 10,
            episodeId = null,
            containerExtension = "mkv",
        )

        // Then URL uses /movie/ path with seriesId/season/episode structure
        assertTrue("URL should contain /movie/", url.contains("/movie/"))
        assertFalse("URL should NOT contain /series/", url.contains("/series/"))
        assertTrue("URL should contain season/episode structure", url.contains("/123/2/10."))
        assertTrue("URL should use container extension", url.endsWith(".mkv"))
    }

    @Test
    fun `series episode URL respects container extension as SSOT`() {
        // Given a URL builder
        val builder = XtreamUrlBuilder(config, resolvedPort = 8080, vodKind = "movie")

        // When building URL with mkv container extension (even though config prefers mp4)
        val url = builder.seriesEpisodeUrl(
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
    fun `XtreamUrlBuilder uses VOD path for episode URLs`() {
        // Given a URL builder with vodKind = "movie"
        val builder = XtreamUrlBuilder(config, resolvedPort = 8080, vodKind = "movie")

        // When building series episode URL
        val url = builder.seriesEpisodeUrl(
            seriesId = 123,
            seasonNumber = 1,
            episodeNumber = 5,
            episodeId = 456,
            containerExtension = "mkv",
        )

        // Then URL uses /movie/ path via vodKind
        assertTrue("URL should contain /movie/", url.contains("/movie/"))
        assertFalse("URL should NOT contain /series/", url.contains("/series/"))
        
        // Verify the URL structure matches expected format
        val expectedPattern = "http://example.com:8080/movie/"
        assertTrue("URL should start with expected pattern", url.startsWith(expectedPattern))
    }

    @Test
    fun `series episode URL format matches VOD URL format`() {
        // Given a URL builder
        val builder = XtreamUrlBuilder(config, resolvedPort = 8080, vodKind = "movie")

        // When building VOD URL and series episode URL with same ID
        val vodUrl = builder.vodUrl(vodId = 456, containerExtension = "mkv")
        val seriesUrl = builder.seriesEpisodeUrl(
            seriesId = 123,
            seasonNumber = 1,
            episodeNumber = 5,
            episodeId = 456,
            containerExtension = "mkv",
        )

        // Then both should use /movie/ path and same format (except ID vs episodeId)
        val vodPattern = "/movie/testuser/testpass/456.mkv"
        val seriesPattern = "/movie/testuser/testpass/456.mkv"

        assertTrue("VOD URL should match expected pattern", vodUrl.contains(vodPattern))
        assertTrue("Series URL should match VOD pattern", seriesUrl.contains(seriesPattern))

        // Both should have same structure
        assertEquals(
            "Episode URL should have same structure as VOD URL",
            vodUrl.substringAfter("/movie/"),
            seriesUrl.substringAfter("/movie/"),
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

        val builder = XtreamUrlBuilder(specialConfig, resolvedPort = 8080, vodKind = "movie")

        // When building series episode URL
        val url = builder.seriesEpisodeUrl(
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
