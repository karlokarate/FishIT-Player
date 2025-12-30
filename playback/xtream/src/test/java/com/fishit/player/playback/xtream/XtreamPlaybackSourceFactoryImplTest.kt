package com.fishit.player.playback.xtream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for XtreamPlaybackSourceFactoryImpl URI validation.
 *
 * Tests the isSafePrebuiltXtreamUri function to ensure it correctly identifies
 * and rejects credential-bearing URIs while allowing safe prebuilt URIs.
 */
class XtreamPlaybackSourceFactoryImplTest {

    /**
     * Helper to access the private isSafePrebuiltXtreamUri method via reflection.
     * This is needed since the method is private but we want to test it in isolation.
     */
    private fun testIsSafeUri(uri: String): Boolean {
        val factory = XtreamPlaybackSourceFactoryImpl(
            xtreamApiClient = object : com.fishit.player.infra.transport.xtream.XtreamApiClient {
                override val authState get() = TODO()
                override val connectionState get() = TODO()
                override val capabilities get() = null
                override suspend fun initialize(config: com.fishit.player.infra.transport.xtream.XtreamApiConfig, forceDiscovery: Boolean) = TODO()
                override suspend fun ping() = TODO()
                override fun close() = TODO()
                override suspend fun getServerInfo() = TODO()
                override suspend fun getUserInfo() = TODO()
                override suspend fun getLiveCategories() = TODO()
                override suspend fun getVodCategories() = TODO()
                override suspend fun getSeriesCategories() = TODO()
                override suspend fun getLiveStreams(categoryId: String?, limit: Int, offset: Int) = TODO()
                override suspend fun getVodStreams(categoryId: String?, limit: Int, offset: Int) = TODO()
                override suspend fun getSeries(categoryId: String?, limit: Int, offset: Int) = TODO()
                override suspend fun getVodInfo(vodId: Int) = TODO()
                override suspend fun getSeriesInfo(seriesId: Int) = TODO()
                override suspend fun getShortEpg(streamId: Int, limit: Int) = TODO()
                override suspend fun getFullEpg(streamId: Int) = TODO()
                override suspend fun prefetchEpg(streamIds: List<Int>, perStreamLimit: Int) = TODO()
                override fun buildLiveUrl(streamId: Int, extension: String?) = ""
                override fun buildVodUrl(vodId: Int, containerExtension: String?) = ""
                override fun buildSeriesEpisodeUrl(seriesId: Int, seasonNumber: Int, episodeNumber: Int, episodeId: Int?, containerExtension: String?) = ""
                override fun buildCatchupUrl(streamId: Int, start: Long, duration: Int) = null
                override suspend fun search(query: String, types: Set<com.fishit.player.infra.transport.xtream.XtreamContentType>, limit: Int) = TODO()
                override suspend fun rawApiCall(action: String, params: Map<String, String>) = TODO()
            }
        )

        // Use reflection to access private method
        val method = factory.javaClass.getDeclaredMethod("isSafePrebuiltXtreamUri", String::class.java)
        method.isAccessible = true
        return method.invoke(factory, uri) as Boolean
    }

    @Test
    fun `safe CDN URL with HTTPS should be allowed`() {
        assertTrue(testIsSafeUri("https://cdn.example.com/stream/123.m3u8"))
    }

    @Test
    fun `safe server URL with HTTP should be allowed`() {
        assertTrue(testIsSafeUri("http://server:8080/streams/abc.ts"))
    }

    @Test
    fun `safe simple path URL should be allowed`() {
        assertTrue(testIsSafeUri("http://example.com/video.mp4"))
    }

    @Test
    fun `URL with userinfo credentials should be blocked`() {
        assertFalse(testIsSafeUri("http://user:pass@server/live/123"))
    }

    @Test
    fun `Xtream live credentials path should be blocked`() {
        assertFalse(testIsSafeUri("http://server/live/user/pass/123.m3u8"))
    }

    @Test
    fun `Xtream movie credentials path should be blocked`() {
        assertFalse(testIsSafeUri("http://server/movie/user/pass/456.mkv"))
    }

    @Test
    fun `Xtream series credentials path should be blocked`() {
        assertFalse(testIsSafeUri("http://server/series/user/pass/789.mp4"))
    }

    @Test
    fun `Xtream VOD credentials path should be blocked`() {
        assertFalse(testIsSafeUri("http://server/vod/user/pass/999.avi"))
    }

    @Test
    fun `URL with username query param should be blocked`() {
        assertFalse(testIsSafeUri("http://server/stream.m3u8?username=user&password=pass"))
    }

    @Test
    fun `URL with password query param should be blocked`() {
        assertFalse(testIsSafeUri("http://server/stream.ts?password=secret"))
    }

    @Test
    fun `HTTPS URL with userinfo should be blocked`() {
        assertFalse(testIsSafeUri("https://user:pass@secure.example.com/video.m3u8"))
    }

    @Test
    fun `URL with @ in path (not userinfo) should be allowed`() {
        assertTrue(testIsSafeUri("http://server/streams/show@2024/episode.mp4"))
    }

    @Test
    fun `URL with port should be allowed`() {
        assertTrue(testIsSafeUri("http://server:8080/stream.m3u8"))
    }

    @Test
    fun `URL with complex path should be allowed`() {
        assertTrue(testIsSafeUri("http://cdn.example.com/media/streams/2024/12/video.mp4"))
    }

    @Test
    fun `URL with email-like pattern in path should be allowed`() {
        assertTrue(testIsSafeUri("http://server/contact@example.com/video.mp4"))
    }

    @Test
    fun `live path with single segment should be allowed`() {
        assertTrue(testIsSafeUri("http://server/live/123.m3u8"))
    }

    @Test
    fun `movie path with single segment should be allowed`() {
        assertTrue(testIsSafeUri("http://server/movie/456.mkv"))
    }

    @Test
    fun `URL with mixed case credentials path should be blocked`() {
        assertFalse(testIsSafeUri("http://server/Live/User/Pass/123.m3u8"))
    }

    @Test
    fun `URL with credentials in uppercase should be blocked`() {
        assertFalse(testIsSafeUri("http://server/LIVE/USER/PASS/123.m3u8"))
    }

    // =========================================================================
    // Container Extension Tests - Verifies VOD URL uses correct extension
    // =========================================================================

    /**
     * Test that buildVodUrl correctly passes the container extension.
     * This is the critical fix for Bug #1: UnrecognizedInputFormatException.
     */
    @Test
    fun `buildVodUrl should use containerExtension from playbackHints`() {
        var capturedExtension: String? = null
        
        val mockClient = object : com.fishit.player.infra.transport.xtream.XtreamApiClient {
            override val authState get() = TODO()
            override val connectionState get() = TODO()
            override val capabilities get() = com.fishit.player.infra.transport.xtream.XtreamCapabilities(
                baseUrl = "http://test.com:8080",
                serverInfo = null,
                userInfo = null,
                liveExtPrefs = listOf("m3u8"),
                vodExtPrefs = listOf("mp4"),
                seriesExtPrefs = listOf("mp4"),
                detectedServerType = null
            )
            override suspend fun initialize(config: com.fishit.player.infra.transport.xtream.XtreamApiConfig, forceDiscovery: Boolean) = TODO()
            override suspend fun ping() = TODO()
            override fun close() = TODO()
            override suspend fun getServerInfo() = TODO()
            override suspend fun getUserInfo() = TODO()
            override suspend fun getLiveCategories() = TODO()
            override suspend fun getVodCategories() = TODO()
            override suspend fun getSeriesCategories() = TODO()
            override suspend fun getLiveStreams(categoryId: String?, limit: Int, offset: Int) = TODO()
            override suspend fun getVodStreams(categoryId: String?, limit: Int, offset: Int) = TODO()
            override suspend fun getSeries(categoryId: String?, limit: Int, offset: Int) = TODO()
            override suspend fun getVodInfo(vodId: Int) = TODO()
            override suspend fun getSeriesInfo(seriesId: Int) = TODO()
            override suspend fun getShortEpg(streamId: Int, limit: Int) = TODO()
            override suspend fun getFullEpg(streamId: Int) = TODO()
            override suspend fun prefetchEpg(streamIds: List<Int>, perStreamLimit: Int) = TODO()
            override fun buildLiveUrl(streamId: Int, extension: String?) = ""
            override fun buildVodUrl(vodId: Int, containerExtension: String?): String {
                capturedExtension = containerExtension
                return "http://test.com:8080/movie/user/pass/$vodId.${containerExtension ?: "mp4"}"
            }
            override fun buildSeriesEpisodeUrl(seriesId: Int, seasonNumber: Int, episodeNumber: Int, episodeId: Int?, containerExtension: String?) = ""
            override fun buildCatchupUrl(streamId: Int, start: Long, duration: Int) = null
            override suspend fun search(query: String, types: Set<com.fishit.player.infra.transport.xtream.XtreamContentType>, limit: Int) = TODO()
            override suspend fun rawApiCall(action: String, params: Map<String, String>) = TODO()
        }

        val factory = XtreamPlaybackSourceFactoryImpl(mockClient)
        
        // Build context with containerExtension = "mkv" (as returned by get_vod_info)
        val context = com.fishit.player.core.playermodel.PlaybackContext(
            canonicalId = "test:movie:123",
            sourceType = com.fishit.player.core.model.SourceType.XTREAM,
            uri = null,
            title = "Test Movie",
            extras = mapOf(
                com.fishit.player.core.model.PlaybackHintKeys.Xtream.CONTENT_TYPE to "vod",
                com.fishit.player.core.model.PlaybackHintKeys.Xtream.VOD_ID to "787947",
                com.fishit.player.core.model.PlaybackHintKeys.Xtream.CONTAINER_EXT to "mkv"
            )
        )

        // Execute
        kotlinx.coroutines.runBlocking {
            val source = factory.createSource(context)
            
            // Verify the extension was passed to buildVodUrl
            org.junit.Assert.assertEquals("mkv", capturedExtension)
            
            // Verify the resulting URL ends with .mkv
            assertTrue("URL should end with .mkv but was: ${source.uri}", source.uri.endsWith(".mkv"))
        }
    }

    @Test
    fun `buildVodUrl should fallback to mp4 when no extension provided`() {
        var capturedExtension: String? = "UNSET"
        
        val mockClient = object : com.fishit.player.infra.transport.xtream.XtreamApiClient {
            override val authState get() = TODO()
            override val connectionState get() = TODO()
            override val capabilities get() = com.fishit.player.infra.transport.xtream.XtreamCapabilities(
                baseUrl = "http://test.com:8080",
                serverInfo = null,
                userInfo = null,
                liveExtPrefs = listOf("m3u8"),
                vodExtPrefs = listOf("mp4"),
                seriesExtPrefs = listOf("mp4"),
                detectedServerType = null
            )
            override suspend fun initialize(config: com.fishit.player.infra.transport.xtream.XtreamApiConfig, forceDiscovery: Boolean) = TODO()
            override suspend fun ping() = TODO()
            override fun close() = TODO()
            override suspend fun getServerInfo() = TODO()
            override suspend fun getUserInfo() = TODO()
            override suspend fun getLiveCategories() = TODO()
            override suspend fun getVodCategories() = TODO()
            override suspend fun getSeriesCategories() = TODO()
            override suspend fun getLiveStreams(categoryId: String?, limit: Int, offset: Int) = TODO()
            override suspend fun getVodStreams(categoryId: String?, limit: Int, offset: Int) = TODO()
            override suspend fun getSeries(categoryId: String?, limit: Int, offset: Int) = TODO()
            override suspend fun getVodInfo(vodId: Int) = TODO()
            override suspend fun getSeriesInfo(seriesId: Int) = TODO()
            override suspend fun getShortEpg(streamId: Int, limit: Int) = TODO()
            override suspend fun getFullEpg(streamId: Int) = TODO()
            override suspend fun prefetchEpg(streamIds: List<Int>, perStreamLimit: Int) = TODO()
            override fun buildLiveUrl(streamId: Int, extension: String?) = ""
            override fun buildVodUrl(vodId: Int, containerExtension: String?): String {
                capturedExtension = containerExtension
                return "http://test.com:8080/movie/user/pass/$vodId.${containerExtension ?: "mp4"}"
            }
            override fun buildSeriesEpisodeUrl(seriesId: Int, seasonNumber: Int, episodeNumber: Int, episodeId: Int?, containerExtension: String?) = ""
            override fun buildCatchupUrl(streamId: Int, start: Long, duration: Int) = null
            override suspend fun search(query: String, types: Set<com.fishit.player.infra.transport.xtream.XtreamContentType>, limit: Int) = TODO()
            override suspend fun rawApiCall(action: String, params: Map<String, String>) = TODO()
        }

        val factory = XtreamPlaybackSourceFactoryImpl(mockClient)
        
        // Build context WITHOUT containerExtension - should fallback to mp4
        val context = com.fishit.player.core.playermodel.PlaybackContext(
            canonicalId = "test:movie:123",
            sourceType = com.fishit.player.core.model.SourceType.XTREAM,
            uri = null,
            title = "Test Movie",
            extras = mapOf(
                com.fishit.player.core.model.PlaybackHintKeys.Xtream.CONTENT_TYPE to "vod",
                com.fishit.player.core.model.PlaybackHintKeys.Xtream.VOD_ID to "787947"
                // NO CONTAINER_EXT - should fallback
            )
        )

        // Execute
        kotlinx.coroutines.runBlocking {
            val source = factory.createSource(context)
            
            // Verify null was passed to buildVodUrl (client handles fallback)
            org.junit.Assert.assertNull("Extension should be null for fallback", capturedExtension)
            
            // Verify the resulting URL ends with .mp4 (default fallback)
            assertTrue("URL should end with .mp4 but was: ${source.uri}", source.uri.endsWith(".mp4"))
        }
    }
}
