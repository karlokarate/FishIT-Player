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
}
