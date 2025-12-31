package com.fishit.player.playback.xtream

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.infra.transport.xtream.*
import com.fishit.player.playback.domain.PlaybackSourceException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for hardened Xtream playback behavior addressing:
 * 1. Lazy session re-initialization
 * 2. Strict output format selection (Cloudflare-safe)
 * 3. Safe URI validation not blocking session-derived paths
 * 4. Explicit error propagation (no demo stream fallback)
 */
class XtreamPlaybackHardeningTest {

    @Test
    fun `format selection prioritizes m3u8 over ts over mp4`() {
        // Given formats with all three options
        val formats = setOf("mp4", "ts", "m3u8")
        
        // When selecting
        val selected = XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats)
        
        // Then m3u8 is chosen
        assertEquals("m3u8", selected)
    }

    @Test
    fun `format selection uses ts when m3u8 not available`() {
        // Given formats with only ts and mp4
        val formats = setOf("mp4", "ts")
        
        // When selecting
        val selected = XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats)
        
        // Then ts is chosen (mp4 is not in priority list)
        assertEquals("ts", selected)
    }

    @Test
    fun `format selection uses mp4 only if it is the ONLY format`() {
        // Given only mp4
        val formats = setOf("mp4")
        
        // When selecting
        val selected = XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats)
        
        // Then mp4 is used as last resort
        assertEquals("mp4", selected)
    }

    @Test
    fun `format selection fails when no supported format and mp4 not alone`() {
        // Given unsupported formats only
        val formats = setOf("webm", "flv")
        
        // When selecting
        val exception = assertThrows(PlaybackSourceException::class.java) {
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats)
        }
        
        // Then error message indicates no support
        assertTrue(exception.message!!.contains("No supported output format"))
    }

    @Test
    fun `format selection for Cloudflare panels uses m3u8`() {
        // Given typical Cloudflare panel formats
        val formats = setOf("m3u8", "ts")
        
        // When selecting
        val selected = XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats)
        
        // Then m3u8 is selected (Cloudflare-safe)
        assertEquals("m3u8", selected)
    }

    @Test
    fun `lazy reinit succeeds when credentials available and valid`() = runTest {
        // Given a factory with null capabilities but valid stored credentials
        var initCalled = false
        val mockApiClient = createMockApiClient(
            initialCapabilities = null,
            onInitialize = { 
                initCalled = true
                Result.success(XtreamCapabilities(
                    baseUrl = "http://server:8080",
                    vodKind = "vod",
                    liveKind = "live",
                    allowedOutputFormats = setOf("m3u8", "ts")
                ))
            }
        )
        
        val mockCredentialsStore = object : XtreamCredentialsStore {
            override suspend fun read() = XtreamStoredConfig(
                scheme = "http",
                host = "server",
                port = 8080,
                username = "user",
                password = "pass"
            )
            override suspend fun write(config: XtreamStoredConfig) {}
            override suspend fun clear() {}
        }
        
        val factory = XtreamPlaybackSourceFactoryImpl(mockApiClient, mockCredentialsStore)
        
        // When creating source with null session
        val context = PlaybackContext(
            canonicalId = "test",
            sourceType = SourceType.XTREAM,
            uri = null,
            extras = mapOf(
                "contentType" to "live",
                "streamId" to "123"
            )
        )
        
        val source = factory.createSource(context)
        
        // Then lazy init is attempted and succeeds
        assertTrue(initCalled)
        assertNotNull(source)
    }

    @Test
    fun `lazy reinit fails gracefully when no credentials stored`() = runTest {
        // Given a factory with null capabilities and no stored credentials
        val mockApiClient = createMockApiClient(initialCapabilities = null)
        
        val mockCredentialsStore = object : XtreamCredentialsStore {
            override suspend fun read() = null // No credentials
            override suspend fun write(config: XtreamStoredConfig) {}
            override suspend fun clear() {}
        }
        
        val factory = XtreamPlaybackSourceFactoryImpl(mockApiClient, mockCredentialsStore)
        
        // When creating source
        val context = PlaybackContext(
            canonicalId = "test",
            sourceType = SourceType.XTREAM,
            uri = null,
            extras = mapOf(
                "contentType" to "live",
                "streamId" to "123"
            )
        )
        
        // Then explicit error with actionable message
        val exception = assertThrows(PlaybackSourceException::class.java) {
            runTest { factory.createSource(context) }
        }
        
        assertTrue(exception.message!!.contains("log in to your Xtream account"))
    }

    @Test
    fun `prebuilt URI validation does not block session-derived paths`() = runTest {
        // Given a factory with valid session and unsafe prebuilt URI
        val mockApiClient = createMockApiClient(
            initialCapabilities = XtreamCapabilities(
                baseUrl = "http://server:8080",
                vodKind = "vod",
                liveKind = "live",
                allowedOutputFormats = setOf("m3u8")
            )
        )
        
        val mockCredentialsStore = createMockCredentialsStore()
        val factory = XtreamPlaybackSourceFactoryImpl(mockApiClient, mockCredentialsStore)
        
        // When creating source with unsafe prebuilt URI (should fallback to session-derived)
        val context = PlaybackContext(
            canonicalId = "test",
            sourceType = SourceType.XTREAM,
            uri = "http://user:pass@server/live/123.m3u8", // Unsafe
            extras = mapOf(
                "contentType" to "live",
                "streamId" to "123"
            )
        )
        
        val source = factory.createSource(context)
        
        // Then source is created using session-derived path (not rejected)
        assertNotNull(source)
        // Should not use the unsafe prebuilt URI
        assertFalse(source.uri.contains("user:pass"))
    }

    // Helper to create mock API client
    private fun createMockApiClient(
        initialCapabilities: XtreamCapabilities? = null,
        onInitialize: (suspend (XtreamApiConfig) -> Result<XtreamCapabilities>)? = null
    ): XtreamApiClient {
        var caps = initialCapabilities
        return object : XtreamApiClient {
            override val authState = MutableStateFlow<XtreamAuthState>(XtreamAuthState.Unknown)
            override val connectionState = MutableStateFlow<XtreamConnectionState>(XtreamConnectionState.Disconnected)
            override val capabilities get() = caps
            
            override suspend fun initialize(config: XtreamApiConfig, forceDiscovery: Boolean): Result<XtreamCapabilities> {
                return if (onInitialize != null) {
                    val result = onInitialize(config)
                    if (result.isSuccess) {
                        caps = result.getOrNull()
                    }
                    result
                } else {
                    Result.failure(Exception("Not implemented"))
                }
            }
            
            override suspend fun ping() = false
            override fun close() {}
            override suspend fun getServerInfo() = Result.failure<XtreamServerInfo>(Exception("Not implemented"))
            override suspend fun getPanelInfo() = null
            override suspend fun getUserInfo() = Result.failure<XtreamUserInfo>(Exception("Not implemented"))
            override suspend fun getLiveCategories() = emptyList<XtreamCategory>()
            override suspend fun getVodCategories() = emptyList<XtreamCategory>()
            override suspend fun getSeriesCategories() = emptyList<XtreamCategory>()
            override suspend fun getLiveStreams(categoryId: String?, limit: Int, offset: Int) = emptyList<XtreamLiveStream>()
            override suspend fun getVodStreams(categoryId: String?, limit: Int, offset: Int) = emptyList<XtreamVodStream>()
            override suspend fun getSeries(categoryId: String?, limit: Int, offset: Int) = emptyList<XtreamSeriesStream>()
            override suspend fun getVodInfo(vodId: Int) = null
            override suspend fun getSeriesInfo(seriesId: Int) = null
            override suspend fun getShortEpg(streamId: Int, limit: Int) = emptyList<XtreamEpgProgramme>()
            override suspend fun getFullEpg(streamId: Int) = emptyList<XtreamEpgProgramme>()
            override suspend fun prefetchEpg(streamIds: List<Int>, perStreamLimit: Int) {}
            override fun buildLiveUrl(streamId: Int, extension: String?) = "http://server/live/$streamId.${extension ?: "m3u8"}"
            override fun buildVodUrl(vodId: Int, containerExtension: String?) = "http://server/vod/$vodId.${containerExtension ?: "mp4"}"
            override fun buildSeriesEpisodeUrl(seriesId: Int, seasonNumber: Int, episodeNumber: Int, episodeId: Int?, containerExtension: String?) = 
                "http://server/series/$seriesId/$seasonNumber/$episodeNumber.${containerExtension ?: "mp4"}"
            override fun buildCatchupUrl(streamId: Int, start: Long, duration: Int) = null
            override suspend fun search(query: String, types: Set<XtreamContentType>, limit: Int) = 
                XtreamSearchResults(emptyList(), emptyList(), emptyList())
            override suspend fun rawApiCall(action: String, params: Map<String, String>) = null
        }
    }

    private fun createMockCredentialsStore(): XtreamCredentialsStore {
        return object : XtreamCredentialsStore {
            override suspend fun read() = XtreamStoredConfig(
                scheme = "http",
                host = "server",
                port = 8080,
                username = "user",
                password = "pass"
            )
            override suspend fun write(config: XtreamStoredConfig) {}
            override suspend fun clear() {}
        }
    }
}
