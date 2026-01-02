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
 * Gold-grade tests for hardened Xtream playback behavior:
 * 1. Policy-driven format selection (allowed_output_formats is SSOT)
 * 2. Lazy session re-initialization (idempotent, thread-safe)
 * 3. Safe URI validation (no false positives on session-derived paths)
 * 4. Explicit error propagation (no masked failures)
 */
class XtreamPlaybackHardeningTest {
    // =========================================================================
    // Format Selection Tests (Policy-Driven, allowed_output_formats is SSOT)
    // =========================================================================

    @Test
    fun `format selection prioritizes m3u8 when available`() {
        // Given formats with all three options
        val formats = setOf("mp4", "ts", "m3u8")

        // When selecting (with HLS available)
        val selected = XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats, hlsAvailable = true)

        // Then m3u8 is chosen (highest priority)
        assertEquals("m3u8", selected)
    }

    @Test
    fun `format selection uses ts when m3u8 not available`() {
        // Given formats with ts and mp4 only (no m3u8)
        val formats = setOf("mp4", "ts")

        // When selecting (HLS availability doesn't matter since no m3u8)
        val selected = XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats, hlsAvailable = true)

        // Then ts is chosen (second priority)
        assertEquals("ts", selected)
    }

    @Test
    fun `format selection uses mp4 when explicitly allowed`() {
        // Given only mp4 in allowed formats
        val formats = setOf("mp4")

        // When selecting (HLS availability doesn't matter since no m3u8)
        val selected = XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats, hlsAvailable = true)

        // Then mp4 is used (policy-driven, not restricted)
        assertEquals("mp4", selected)
    }

    @Test
    fun `format selection chooses ts over mp4`() {
        // Given both ts and mp4
        val formats = setOf("ts", "mp4")

        // When selecting (HLS availability doesn't matter since no m3u8)
        val selected = XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats, hlsAvailable = true)

        // Then ts is preferred
        assertEquals("ts", selected)
    }

    @Test
    fun `format selection fails when no supported format`() {
        // Given unsupported formats only
        val formats = setOf("webm", "flv", "avi")

        // When selecting (HLS availability doesn't matter since no supported formats)
        val exception =
            assertThrows(PlaybackSourceException::class.java) {
                XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats, hlsAvailable = true)
            }

        // Then error message indicates no support
        assertTrue(exception.message!!.contains("No supported output format"))
        assertTrue(exception.message!!.contains("m3u8, ts, mp4"))
    }

    @Test
    fun `format selection fails when allowed formats empty`() {
        // Given empty formats
        val formats = emptySet<String>()

        // When selecting (HLS availability doesn't matter)
        val exception =
            assertThrows(PlaybackSourceException::class.java) {
                XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats, hlsAvailable = true)
            }

        // Then error message indicates empty list
        assertTrue(exception.message!!.contains("allowed_output_formats is empty"))
    }

    @Test
    fun `format selection for Cloudflare panels with m3u8 and ts`() {
        // Given typical Cloudflare panel formats (no mp4)
        val formats = setOf("m3u8", "ts")

        // When selecting (with HLS available)
        val selected = XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats, hlsAvailable = true)

        // Then m3u8 is selected
        assertEquals("m3u8", selected)
    }

    @Test
    fun `format selection handles provider with mp4 and ts but no m3u8`() {
        // Given provider that allows mp4 and ts (some providers do this)
        val formats = setOf("mp4", "ts")

        // When selecting (HLS availability doesn't matter since no m3u8)
        val selected = XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(formats, hlsAvailable = true)

        // Then ts is chosen (higher priority than mp4)
        assertEquals("ts", selected)
    }

    // =========================================================================
    // Lazy Re-Init Tests (Idempotent, Thread-Safe, Bounded)
    // =========================================================================

    @Test
    fun `lazy reinit succeeds when credentials available and valid`() =
        runTest {
            // Given a factory with null capabilities but valid stored credentials
            var initCalled = false
            val mockApiClient =
                createMockApiClient(
                    initialCapabilities = null,
                    onInitialize = {
                        initCalled = true
                        Result.success(
                            XtreamCapabilities(
                                cacheKey = "test|user",
                                baseUrl = "http://server:8080",
                                username = "user",
                            ),
                        )
                    },
                )

            val mockCredentialsStore =
                object : XtreamCredentialsStore {
                    override suspend fun read() =
                        XtreamStoredConfig(
                            scheme = "http",
                            host = "server",
                            port = 8080,
                            username = "user",
                            password = "pass",
                        )

                    override suspend fun write(config: XtreamStoredConfig) {}

                    override suspend fun clear() {}
                }

            val factory = XtreamPlaybackSourceFactoryImpl(mockApiClient, mockCredentialsStore)

            // When creating source with null session
            val context =
                PlaybackContext(
                    canonicalId = "test",
                    sourceType = SourceType.XTREAM,
                    uri = null,
                    extras =
                        mapOf(
                            "contentType" to "live",
                            "streamId" to "123",
                        ),
                )

            val source = factory.createSource(context)

            // Then lazy init is attempted and succeeds
            assertTrue(initCalled)
            assertNotNull(source)
        }

    @Test
    fun `lazy reinit fails gracefully when no credentials stored`() =
        runTest {
            // Given a factory with null capabilities and no stored credentials
            val mockApiClient = createMockApiClient(initialCapabilities = null)

            val mockCredentialsStore =
                object : XtreamCredentialsStore {
                    override suspend fun read() = null // No credentials

                    override suspend fun write(config: XtreamStoredConfig) {}

                    override suspend fun clear() {}
                }

            val factory = XtreamPlaybackSourceFactoryImpl(mockApiClient, mockCredentialsStore)

            // When creating source
            val context =
                PlaybackContext(
                    canonicalId = "test",
                    sourceType = SourceType.XTREAM,
                    uri = null,
                    extras =
                        mapOf(
                            "contentType" to "live",
                            "streamId" to "123",
                        ),
                )

            // Then explicit error with "not configured" message
            try {
                factory.createSource(context)
                fail("Expected PlaybackSourceException")
            } catch (e: PlaybackSourceException) {
                assertTrue(e.message!!.contains("not configured") || e.message!!.contains("log in"))
            }
        }

    @Test
    fun `lazy reinit provides actionable error when credentials invalid`() =
        runTest {
            // Given stored credentials that are invalid
            val mockApiClient =
                createMockApiClient(
                    initialCapabilities = null,
                    onInitialize = {
                        Result.failure(Exception("Invalid credentials"))
                    },
                )

            val mockCredentialsStore =
                object : XtreamCredentialsStore {
                    override suspend fun read() =
                        XtreamStoredConfig(
                            scheme = "http",
                            host = "server",
                            port = 8080,
                            username = "user",
                            password = "wrongpass",
                        )

                    override suspend fun write(config: XtreamStoredConfig) {}

                    override suspend fun clear() {}
                }

            val factory = XtreamPlaybackSourceFactoryImpl(mockApiClient, mockCredentialsStore)

            // When creating source
            val context =
                PlaybackContext(
                    canonicalId = "test",
                    sourceType = SourceType.XTREAM,
                    uri = null,
                    extras =
                        mapOf(
                            "contentType" to "live",
                            "streamId" to "123",
                        ),
                )

            // Then error message distinguishes invalid credentials
            try {
                factory.createSource(context)
                fail("Expected PlaybackSourceException")
            } catch (e: PlaybackSourceException) {
                assertTrue(
                    e.message!!.contains("invalid") ||
                        e.message!!.contains("unreachable") ||
                        e.message!!.contains("log in again"),
                )
            }
        }

    // =========================================================================
    // URI Safety Tests (No False Positives on Session-Derived Paths)
    // =========================================================================

    @Test
    fun `prebuilt URI validation does not block session-derived paths`() =
        runTest {
            // Given a factory with valid session and unsafe prebuilt URI
            val mockApiClient =
                createMockApiClient(
                    initialCapabilities =
                        XtreamCapabilities(
                            cacheKey = "test|user",
                            baseUrl = "http://server:8080",
                            username = "user",
                        ),
                )

            val mockCredentialsStore = createMockCredentialsStore()
            val factory = XtreamPlaybackSourceFactoryImpl(mockApiClient, mockCredentialsStore)

            // When creating source with unsafe prebuilt URI (should fallback to session-derived)
            val context =
                PlaybackContext(
                    canonicalId = "test",
                    sourceType = SourceType.XTREAM,
                    uri = "******server/live/123.m3u8", // Unsafe
                    extras =
                        mapOf(
                            "contentType" to "live",
                            "streamId" to "123",
                        ),
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
        onInitialize: (suspend (XtreamApiConfig) -> Result<XtreamCapabilities>)? = null,
    ): XtreamApiClient {
        var caps = initialCapabilities
        return object : XtreamApiClient {
            override val authState = MutableStateFlow<XtreamAuthState>(XtreamAuthState.Unknown)
            override val connectionState = MutableStateFlow<XtreamConnectionState>(XtreamConnectionState.Disconnected)
            override val capabilities get() = caps

            override suspend fun initialize(
                config: XtreamApiConfig,
                forceDiscovery: Boolean,
            ): Result<XtreamCapabilities> =
                if (onInitialize != null) {
                    val result = onInitialize(config)
                    if (result.isSuccess) {
                        caps = result.getOrNull()
                    }
                    result
                } else {
                    Result.failure(Exception("Not implemented"))
                }

            override suspend fun ping() = false

            override fun close() {}

            override suspend fun getServerInfo() = Result.failure<XtreamServerInfo>(Exception("Not implemented"))

            override suspend fun getPanelInfo() = null

            override suspend fun getUserInfo() = Result.failure<XtreamUserInfo>(Exception("Not implemented"))

            override suspend fun getLiveCategories() = emptyList<XtreamCategory>()

            override suspend fun getVodCategories() = emptyList<XtreamCategory>()

            override suspend fun getSeriesCategories() = emptyList<XtreamCategory>()

            override suspend fun getLiveStreams(
                categoryId: String?,
                limit: Int,
                offset: Int,
            ) = emptyList<XtreamLiveStream>()

            override suspend fun getVodStreams(
                categoryId: String?,
                limit: Int,
                offset: Int,
            ) = emptyList<XtreamVodStream>()

            override suspend fun getSeries(
                categoryId: String?,
                limit: Int,
                offset: Int,
            ) = emptyList<XtreamSeriesStream>()

            override suspend fun getVodInfo(vodId: Int) = null

            override suspend fun getSeriesInfo(seriesId: Int) = null

            override suspend fun getShortEpg(
                streamId: Int,
                limit: Int,
            ) = emptyList<XtreamEpgProgramme>()

            override suspend fun getFullEpg(streamId: Int) = emptyList<XtreamEpgProgramme>()

            override suspend fun prefetchEpg(
                streamIds: List<Int>,
                perStreamLimit: Int,
            ) {}

            override fun buildLiveUrl(
                streamId: Int,
                extension: String?,
            ) = "http://server/live/$streamId.${extension ?: "m3u8"}"

            override fun buildVodUrl(
                vodId: Int,
                containerExtension: String?,
            ) = "http://server/vod/$vodId.${containerExtension ?: "mp4"}"

            override fun buildSeriesEpisodeUrl(
                seriesId: Int,
                seasonNumber: Int,
                episodeNumber: Int,
                episodeId: Int?,
                containerExtension: String?,
            ) = if (episodeId != null && episodeId > 0) {
                "http://server/movie/$episodeId.${containerExtension ?: "mp4"}"
            } else {
                "http://server/movie/$seriesId/$seasonNumber/$episodeNumber.${containerExtension ?: "mp4"}"
            }

            override fun buildCatchupUrl(
                streamId: Int,
                start: Long,
                duration: Int,
            ) = null

            override suspend fun search(
                query: String,
                types: Set<XtreamContentType>,
                limit: Int,
            ) = XtreamSearchResults(emptyList(), emptyList(), emptyList())

            override suspend fun rawApiCall(
                action: String,
                params: Map<String, String>,
            ) = null
        }
    }

    private fun createMockCredentialsStore(): XtreamCredentialsStore =
        object : XtreamCredentialsStore {
            override suspend fun read() =
                XtreamStoredConfig(
                    scheme = "http",
                    host = "server",
                    port = 8080,
                    username = "user",
                    password = "pass",
                )

            override suspend fun write(config: XtreamStoredConfig) {}

            override suspend fun clear() {}
        }
}
