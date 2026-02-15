package com.fishit.player.playback.xtream

import com.fishit.player.infra.transport.xtream.XtreamApiConfig
import com.fishit.player.infra.transport.xtream.XtreamUrlBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for Xtream SERIES episode playback URL construction.
 *
 * Critical requirements from problem statement:
 * 1. Series episodes MUST use /series/<user>/<pass>/<episodeId>.<ext> (NOT /movie/ or /vod/)
 * 2. Container extension must be preserved (NOT forced to m3u8/ts)
 * 3. episodeId is REQUIRED for playback
 * 4. Fallback to mkv if containerExtension is missing (consistent with transport layer)
 */
class XtreamSeriesPlaybackTest {
    companion object {
        private const val TEST_HOST = "konigtv.com"
        private const val TEST_PORT = 8080
        private const val TEST_USER = "testuser"
        private const val TEST_PASS = "testpass"
        private const val TEST_EPISODE_ID = 12345
        private const val TEST_SERIES_ID = 999
    }

    /**
     * Create a URL builder for testing.
     */
    private fun createUrlBuilder(): XtreamUrlBuilder {
        val config =
            XtreamApiConfig(
                host = TEST_HOST,
                port = TEST_PORT,
                scheme = "HTTP",
                username = TEST_USER,
                password = TEST_PASS,
            )
        val urlBuilder = XtreamUrlBuilder()
        urlBuilder.configure(config, TEST_PORT)
        return urlBuilder
    }

    // =========================================================================
    // Series Episode URL Construction Tests (Core Bug Fix)
    // =========================================================================

    @Test
    fun `series episode URL uses series path with episodeId and mp4 extension`() {
        // Given: A URL builder for series episodes
        val urlBuilder = createUrlBuilder()

        // When: Building a series episode URL with episodeId and mp4 container
        val url =
            urlBuilder.seriesEpisodeUrl(
                seriesId = TEST_SERIES_ID,
                seasonNumber = 1,
                episodeNumber = 1,
                episodeId = TEST_EPISODE_ID,
                containerExtension = "mp4",
            )

        // Then: URL must use /series/ path (NOT /movie/ or /vod/)
        assertTrue(
            "Series URL must use /series/ path",
            url.contains("/series/$TEST_USER/$TEST_PASS/$TEST_EPISODE_ID.mp4"),
        )
        assertFalse("Series URL must NOT use /movie/ path", url.contains("/movie/"))
        assertFalse("Series URL must NOT use /vod/ path", url.contains("/vod/"))
    }

    @Test
    fun `series episode URL uses series path with episodeId and mkv extension`() {
        // Given: A URL builder for series episodes
        val urlBuilder = createUrlBuilder()

        // When: Building a series episode URL with episodeId and mkv container
        val url =
            urlBuilder.seriesEpisodeUrl(
                seriesId = TEST_SERIES_ID,
                seasonNumber = 1,
                episodeNumber = 1,
                episodeId = TEST_EPISODE_ID,
                containerExtension = "mkv",
            )

        // Then: URL must preserve mkv extension (NOT forced to m3u8/ts)
        assertTrue(
            "Series URL must use /series/ path with mkv extension",
            url.contains("/series/$TEST_USER/$TEST_PASS/$TEST_EPISODE_ID.mkv"),
        )
        assertFalse("Series URL must NOT be forced to m3u8", url.endsWith(".m3u8"))
        assertFalse("Series URL must NOT be forced to ts", url.endsWith(".ts"))
    }

    @Test
    fun `series episode URL falls back to mkv when containerExtension is missing`() {
        // Given: A URL builder for series episodes
        val urlBuilder = createUrlBuilder()

        // When: Building a series episode URL WITHOUT containerExtension
        val url =
            urlBuilder.seriesEpisodeUrl(
                seriesId = TEST_SERIES_ID,
                seasonNumber = 1,
                episodeNumber = 1,
                episodeId = TEST_EPISODE_ID,
                containerExtension = null,
            )

        // Then: URL must fallback to mkv (NOT mp4 or m3u8)
        assertTrue(
            "Series URL must fallback to mkv when containerExtension is missing",
            url.contains("/series/$TEST_USER/$TEST_PASS/$TEST_EPISODE_ID.mkv"),
        )
        assertFalse("Series URL must NOT fallback to m3u8", url.endsWith(".m3u8"))
        assertFalse("Series URL must NOT fallback to mp4", url.endsWith(".mp4"))
    }

    @Test
    fun `series episode URL must NOT use movie path`() {
        // Given: A URL builder for series episodes
        val urlBuilder = createUrlBuilder()

        // When: Building a series episode URL
        val url =
            urlBuilder.seriesEpisodeUrl(
                seriesId = TEST_SERIES_ID,
                seasonNumber = 1,
                episodeNumber = 1,
                episodeId = TEST_EPISODE_ID,
                containerExtension = "mp4",
            )

        // Then: URL must NOT contain /movie/ or /vod/
        assertFalse(
            "Series URL must NOT use /movie/ path (legacy bug)",
            url.contains("/movie/"),
        )
        assertFalse(
            "Series URL must NOT use /vod/ path (legacy bug)",
            url.contains("/vod/"),
        )
    }

    @Test
    fun `series episode URL uses legacy path when episodeId is missing`() {
        // Given: A URL builder for series episodes
        val urlBuilder = createUrlBuilder()

        // When: Building a series episode URL WITHOUT episodeId
        val url =
            urlBuilder.seriesEpisodeUrl(
                seriesId = TEST_SERIES_ID,
                seasonNumber = 1,
                episodeNumber = 2,
                episodeId = null,
                containerExtension = "mp4",
            )

        // Then: URL should use legacy fallback path with /series/
        assertTrue(
            "Series URL should use legacy path with /series/",
            url.contains("/series/$TEST_USER/$TEST_PASS/$TEST_SERIES_ID/1/2.mp4"),
        )
    }

    @Test
    fun `VOD URL continues to use movie path`() {
        // Given: A URL builder for VOD content
        val urlBuilder = createUrlBuilder()

        // When: Building a VOD URL
        val url = urlBuilder.vodUrl(vodId = 54321, containerExtension = "mp4")

        // Then: VOD URL should use /movie/ or /vod/ path (NOT /series/)
        assertFalse("VOD URL must NOT use /series/ path", url.contains("/series/"))
        // Should contain either /movie/ or /vod/ (the exact one depends on config)
        assertTrue(
            "VOD URL should use /movie/ or /vod/ path",
            url.contains("/movie/") || url.contains("/vod/"),
        )
    }

    @Test
    fun `series episode URL rejects m3u8 extension`() {
        // Given: A URL builder for series episodes
        val urlBuilder = createUrlBuilder()

        // When/Then: Building a series episode URL with m3u8 extension should fail
        try {
            urlBuilder.seriesEpisodeUrl(
                seriesId = TEST_SERIES_ID,
                seasonNumber = 1,
                episodeNumber = 1,
                episodeId = TEST_EPISODE_ID,
                containerExtension = "m3u8",
            )
            fail("Should have thrown IllegalArgumentException for m3u8 extension")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Error message should mention m3u8 is not allowed for series",
                e.message!!.contains("m3u8") && e.message!!.contains("streaming formats"),
            )
        }
    }

    @Test
    fun `series episode URL rejects ts extension`() {
        // Given: A URL builder for series episodes
        val urlBuilder = createUrlBuilder()

        // When/Then: Building a series episode URL with ts extension should fail
        try {
            urlBuilder.seriesEpisodeUrl(
                seriesId = TEST_SERIES_ID,
                seasonNumber = 1,
                episodeNumber = 1,
                episodeId = TEST_EPISODE_ID,
                containerExtension = "ts",
            )
            fail("Should have thrown IllegalArgumentException for ts extension")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Error message should mention ts is not allowed for series",
                e.message!!.contains("ts") && e.message!!.contains("streaming formats"),
            )
        }
    }

    @Test
    fun `series episode URL rejects invalid extension`() {
        // Given: A URL builder for series episodes
        val urlBuilder = createUrlBuilder()

        // When/Then: Building a series episode URL with invalid extension should fail
        try {
            urlBuilder.seriesEpisodeUrl(
                seriesId = TEST_SERIES_ID,
                seasonNumber = 1,
                episodeNumber = 1,
                episodeId = TEST_EPISODE_ID,
                containerExtension = "xyz",
            )
            fail("Should have thrown IllegalArgumentException for invalid extension")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Error message should list valid formats",
                e.message!!.contains("Valid formats") && e.message!!.contains("mp4"),
            )
        }
    }

    // =========================================================================
    // Custom seriesKind Parameter Tests (Fix for provider-specific aliases)
    // =========================================================================

    @Test
    fun `series episode URL uses custom seriesKind parameter`() {
        // Given: A URL builder for series episodes
        val urlBuilder = createUrlBuilder()

        // When: Building a series episode URL with custom seriesKind "episodes"
        val url =
            urlBuilder.seriesEpisodeUrl(
                seriesId = TEST_SERIES_ID,
                seasonNumber = 1,
                episodeNumber = 1,
                episodeId = TEST_EPISODE_ID,
                containerExtension = "mp4",
                seriesKind = "episodes",
            )

        // Then: URL must use /episodes/ path (NOT default /series/)
        assertTrue(
            "Series URL must use custom /episodes/ path",
            url.contains("/episodes/$TEST_USER/$TEST_PASS/$TEST_EPISODE_ID.mp4"),
        )
        assertFalse("Series URL must NOT use default /series/ path", url.contains("/series/"))
    }

    @Test
    fun `series episode URL defaults to series when seriesKind is null`() {
        // Given: A URL builder for series episodes
        val urlBuilder = createUrlBuilder()

        // When: Building a series episode URL without seriesKind (null)
        val url =
            urlBuilder.seriesEpisodeUrl(
                seriesId = TEST_SERIES_ID,
                seasonNumber = 1,
                episodeNumber = 1,
                episodeId = TEST_EPISODE_ID,
                containerExtension = "mp4",
                seriesKind = null,
            )

        // Then: URL must use default /series/ path
        assertTrue(
            "Series URL must use default /series/ path when seriesKind is null",
            url.contains("/series/$TEST_USER/$TEST_PASS/$TEST_EPISODE_ID.mp4"),
        )
    }

    @Test
    fun `series episode URL supports provider-specific aliases`() {
        // Given: A URL builder for series episodes
        val urlBuilder = createUrlBuilder()

        // Test multiple provider-specific aliases that might be used
        val aliases = listOf("series", "episodes", "show", "tvshow")

        aliases.forEach { alias ->
            // When: Building URL with provider-specific alias
            val url =
                urlBuilder.seriesEpisodeUrl(
                    seriesId = TEST_SERIES_ID,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    episodeId = TEST_EPISODE_ID,
                    containerExtension = "mp4",
                    seriesKind = alias,
                )

            // Then: URL must use the specified alias
            assertTrue(
                "Series URL must use custom /$alias/ path",
                url.contains("/$alias/$TEST_USER/$TEST_PASS/$TEST_EPISODE_ID.mp4"),
            )
        }
    }
}
